package br.com.ctcore.benefit;

import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.api.page.PageResponse;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.plan.Plans;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RF-BEN-01: tabela de benefícios. Permissão e alcance são os de Planos (BENEFITS tem o mesmo perfil de acesso): Coordenador em todas as
 * unidades, Administrador nas dele (alcance = unidade da matrícula). A situação "vencido" é calculada na leitura, no fuso da unidade.
 */
@Service
public class Benefits {
    public record Ref(UUID id, String name) {}

    public record Row(UUID id, Ref student, Ref unit, Ref modality, String type, long value, String reason, String decidedBy, String grantedBy,
            LocalDate validUntil, String status, boolean high, long monthlyValueCents) {}

    public record Filter(String q, String type, UUID modalityId, UUID unitId, String status, Boolean high) {}

    /** Situação calculada: encerrado, vencido (validade anterior a hoje no fuso da unidade) ou ativo. */
    public static final String STATUS_SQL = "case when b.status = 'ENDED' then 'ENDED' when b.valid_until is not null "
            + "and b.valid_until < (now() at time zone u.timezone)::date then 'EXPIRED' else 'ACTIVE' end";
    /** RN-BEN-06: bolsa acima de 50% ou desconto acima de metade do valor do plano. Usa o alias p (plano da modalidade). */
    public static final String HIGH_SQL = "((b.type = 'PERCENT' and b.value > 50) or (b.type = 'AMOUNT' and b.value * 2 > p.value_cents))";
    public static final String FROM_SQL = "benefit b join enrollment_modality em on em.id = b.enrollment_modality_id join plan p on p.id = em.plan_id "
            + "join unit u on u.id = b.unit_id";
    private static final String FULL_FROM = FROM_SQL + " join student s on s.id = b.student_id join modality m on m.id = em.modality_id "
            + "join account a on a.id = b.granted_by_account_id";
    private static final String SELECT = "select b.id, s.id, s.name, u.id, u.name, m.id, m.name, b.type, b.value, b.reason, b.decided_by, a.name, b.valid_until, "
            + STATUS_SQL + ", " + HIGH_SQL + ", p.value_cents";

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Plans plans;

    public Benefits(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Plans plans) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.plans = plans;
    }

    /** RN-BEN-07: valor do plano menos o benefício, nunca negativo; bolsa de 100% dá zero. */
    public static long monthlyValueCents(String type, long value, long planCents) {
        long discount = "PERCENT".equals(type) ? (planCents * value + 50) / 100 : value;
        return Math.max(0, planCents - discount);
    }

    public PageResponse<Row> list(AccountPrincipal who, Filter f, PageRequest page) {
        plans.requireAccess(who);
        String type = enumValue("type", f.type(), Set.of("PERCENT", "AMOUNT"));
        String status = enumValue("status", f.status(), Set.of("ACTIVE", "EXPIRED", "ENDED"));
        Set<UUID> reach = plans.reach(who, f.unitId());
        if (reach != null && reach.isEmpty()) return PageResponse.of(List.of(), page, 0);

        StringBuilder where = new StringBuilder("b.organization_id = :org");
        Map<String, Object> params = new HashMap<>();
        params.put("org", who.organizationId());
        if (f.q() != null && !f.q().isBlank()) {
            where.append(" and s.name_key like :q escape '\\'");
            params.put("q", "%" + Modalities.normalize(f.q()).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (type != null) {
            where.append(" and b.type = :type");
            params.put("type", type);
        }
        if (f.modalityId() != null) {
            where.append(" and em.modality_id = :modality");
            params.put("modality", f.modalityId());
        }
        if (reach != null) {
            where.append(" and b.unit_id in (:reach)");
            params.put("reach", reach);
        }
        if (status != null) {
            where.append(" and ").append(STATUS_SQL).append(" = :status");
            params.put("status", status);
        }
        if (Boolean.TRUE.equals(f.high())) where.append(" and ").append(HIGH_SQL);
        return transactions.execute(() -> {
            long total = jdbc.sql("select count(*) from " + FULL_FROM + " where " + where).params(params).query(Long.class).single();
            List<Row> rows = jdbc.sql(SELECT + " from " + FULL_FROM + " where " + where + " order by s.name_key, m.name, b.id limit :limit offset :offset")
                    .params(params).param("limit", page.size()).param("offset", page.offset()).query(Benefits::mapRow).list();
            return PageResponse.of(rows, page, total);
        });
    }

    /** Uma linha pelo id (dentro de uma transação da organização); vazio se não existe nela. */
    Optional<Row> find(UUID organizationId, UUID id) {
        return jdbc.sql(SELECT + " from " + FULL_FROM + " where b.organization_id = :org and b.id = :id").param("org", organizationId).param("id", id)
                .query(Benefits::mapRow).optional();
    }

    /** O benefício com status ACTIVE da modalidade da matrícula (o vencido não encerrado também ocupa o lugar). */
    Optional<Row> findActive(UUID organizationId, UUID modalityRowId) {
        return jdbc.sql(SELECT + " from " + FULL_FROM + " where b.organization_id = :org and b.enrollment_modality_id = :row and b.status = 'ACTIVE'")
                .param("org", organizationId).param("row", modalityRowId).query(Benefits::mapRow).optional();
    }

    private static Row mapRow(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Row(rs.getObject(1, UUID.class), new Ref(rs.getObject(2, UUID.class), rs.getString(3)),
                new Ref(rs.getObject(4, UUID.class), rs.getString(5)), new Ref(rs.getObject(6, UUID.class), rs.getString(7)),
                rs.getString(8), rs.getLong(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getObject(13, LocalDate.class),
                rs.getString(14), rs.getBoolean(15), monthlyValueCents(rs.getString(8), rs.getLong(9), rs.getLong(16)));
    }

    private static String enumValue(String field, String raw, Set<String> allowed) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.strip().toUpperCase(Locale.ROOT);
        if (!allowed.contains(v)) throw new ApiValidationException(field, "valor inválido");
        return v;
    }
}

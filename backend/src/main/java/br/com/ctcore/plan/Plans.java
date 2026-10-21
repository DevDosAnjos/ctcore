package br.com.ctcore.plan;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.api.page.PageResponse;
import br.com.ctcore.benefit.Benefits;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.modality.Modalities;
import java.util.List;
import java.util.Locale;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-PLA-01: resumo de planos e benefícios. Organização, perfil e alcance vêm da sessão: o Coordenador vê todas as unidades;
 * o Administrador, as dele. Sem controle de aulas feitas (RN-PLA-06).
 */
@Service
public class Plans {
    public record PlanCounts(int active, int inactive, int studentsUsing, Long minValueCents, Long maxValueCents) {}

    public record BenefitSummary(int active, int high) {}

    public record Summary(PlanCounts plans, BenefitSummary benefits) {}

    public record Ref(UUID id, String name) {}

    public record Filter(String q, UUID modalityId, UUID unitId, String status) {}

    public record Row(UUID id, Ref unit, Ref modality, String name, long valueCents, Integer weeklyFrequency, boolean validOtherUnits,
            int studentsUsing, String status) {}

    /** Alcance da consulta: unitIds nulo = todas as unidades da organização. */
    record Scope(UUID organizationId, Set<UUID> unitIds) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;

    public Plans(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
    }

    public void requireAccess(AccountPrincipal who) {
        if (who.role() == null || permissions.effective(who).get(Permission.PLANS) == Access.NONE) {
            throw new AccessDeniedException("Permission denied");
        }
    }

    public Summary summary(AccountPrincipal who, UUID unitId) {
        requireAccess(who);
        Scope scope = new Scope(who.organizationId(), reach(who, unitId));
        PlanCounts plans = scope.unitIds() != null && scope.unitIds().isEmpty() ? new PlanCounts(0, 0, 0, null, null) : counts(scope);
        return new Summary(plans, benefits(scope));
    }

    /** Alcance: nulo = todas as unidades; o Administrador fica nas dele (fora do alcance, vazio). */
    public Set<UUID> reach(AccountPrincipal who, UUID unitId) {
        if (who.role() == Role.ADMINISTRATOR) {
            Set<UUID> own = who.units() == null ? Set.of() : who.units();
            return unitId == null ? own : own.contains(unitId) ? Set.of(unitId) : Set.of();
        }
        return unitId == null ? null : Set.of(unitId);
    }

    /** RF-PLA-02: tabela de planos, por modalidade e nome. */
    public PageResponse<Row> list(AccountPrincipal who, Filter f, PageRequest page) {
        requireAccess(who);
        String st = f.status() == null || f.status().isBlank() ? null : f.status().strip().toUpperCase(Locale.ROOT);
        if (st != null && !Set.of("ACTIVE", "INACTIVE").contains(st)) throw new ApiValidationException("status", "valor inválido");
        Set<UUID> reach = reach(who, f.unitId());
        if (reach != null && reach.isEmpty()) return PageResponse.of(List.of(), page, 0);

        StringBuilder where = new StringBuilder("p.organization_id = :org");
        Map<String, Object> params = new HashMap<>();
        params.put("org", who.organizationId());
        if (f.q() != null && !f.q().isBlank()) {
            where.append(" and p.name_key like :q escape '\\'");
            params.put("q", "%" + Modalities.normalize(f.q()).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (f.modalityId() != null) {
            where.append(" and p.modality_id = :modality");
            params.put("modality", f.modalityId());
        }
        if (reach != null) {
            where.append(" and p.unit_id in (:reach)");
            params.put("reach", reach);
        }
        if (st != null) {
            where.append(" and p.status = :status");
            params.put("status", st);
        }
        return transactions.execute(() -> {
            long total = jdbc.sql("select count(*) from plan p where " + where).params(params).query(Long.class).single();
            List<Row> rows = jdbc.sql("select p.id, u.id, u.name, m.id, m.name, p.name, p.value_cents, p.weekly_frequency, p.valid_other_units, p.status "
                    + "from plan p join unit u on u.id = p.unit_id join modality m on m.id = p.modality_id where " + where
                    + " order by m.name, p.name_key, p.id limit :limit offset :offset")
                    .params(params).param("limit", page.size()).param("offset", page.offset())
                    .query((rs, n) -> new Row(rs.getObject(1, UUID.class), new Ref(rs.getObject(2, UUID.class), rs.getString(3)),
                            new Ref(rs.getObject(4, UUID.class), rs.getString(5)), rs.getString(6), rs.getLong(7), (Integer) rs.getObject(8),
                            rs.getBoolean(9), studentsUsing(new Scope(who.organizationId(), Set.of(rs.getObject(2, UUID.class)))), rs.getString(10)))
                    .list();
            return PageResponse.of(rows, page, total);
        });
    }

    /** RF-PLA-03: um plano por id; outra organização ou fora do alcance do Administrador é 404. */
    public Row get(AccountPrincipal who, UUID id) {
        requireAccess(who);
        return transactions.execute(() -> {
            Row r = jdbc.sql("select p.id, u.id, u.name, m.id, m.name, p.name, p.value_cents, p.weekly_frequency, p.valid_other_units, p.status "
                    + "from plan p join unit u on u.id = p.unit_id join modality m on m.id = p.modality_id where p.organization_id = :org and p.id = :id")
                    .param("org", who.organizationId()).param("id", id)
                    .query((rs, n) -> new Row(rs.getObject(1, UUID.class), new Ref(rs.getObject(2, UUID.class), rs.getString(3)),
                            new Ref(rs.getObject(4, UUID.class), rs.getString(5)), rs.getString(6), rs.getLong(7), (Integer) rs.getObject(8),
                            rs.getBoolean(9), studentsUsing(new Scope(who.organizationId(), Set.of(rs.getObject(2, UUID.class)))), rs.getString(10)))
                    .optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            Set<UUID> reach = reach(who, r.unit().id());
            if (reach != null && reach.isEmpty()) throw new ApiStatusException(ErrorCode.NOT_FOUND);
            return r;
        });
    }

    /** Ponto único (RN-PLA-04): a Sprint 07 troca por contagem real de alunos no plano. */
    int studentsAffected(UUID planId) {
        return 0;
    }

    /** Ponto único (RN-PLA-05): a cobrança troca por reajuste real das cobranças não pagas e pagas futuras. Hoje não altera nada. */
    void applyRepricing(UUID planId, long oldCents, long newCents, boolean adjustUnpaid, boolean chargePaidDifference) {
    }

    private PlanCounts counts(Scope scope) {
        Map<String, Object> params = new HashMap<>();
        params.put("org", scope.organizationId());
        String filter = "";
        if (scope.unitIds() != null) {
            filter = " and unit_id in (:units)";
            params.put("units", scope.unitIds());
        }
        String sql = "select count(*) filter (where status = 'ACTIVE'), count(*) filter (where status = 'INACTIVE'), "
                + "min(value_cents) filter (where status = 'ACTIVE'), max(value_cents) filter (where status = 'ACTIVE') "
                + "from plan where organization_id = :org" + filter;
        return transactions.execute(() -> jdbc.sql(sql).params(params).query((rs, n) -> new PlanCounts(rs.getInt(1), rs.getInt(2),
                studentsUsing(scope), (Long) rs.getObject(3), (Long) rs.getObject(4))).single());
    }

    /** Ponto único: a Sprint 07 (matrícula) troca por contagem real. */
    int studentsUsing(Scope scope) {
        return 0;
    }

    /** Benefícios ativos não vencidos e, entre eles, os altos (RN-BEN-06), no alcance da consulta. */
    BenefitSummary benefits(Scope scope) {
        if (scope.unitIds() != null && scope.unitIds().isEmpty()) return new BenefitSummary(0, 0);
        Map<String, Object> params = new HashMap<>();
        params.put("org", scope.organizationId());
        String filter = "";
        if (scope.unitIds() != null) {
            filter = " and b.unit_id in (:units)";
            params.put("units", scope.unitIds());
        }
        String sql = "select count(*), count(*) filter (where " + Benefits.HIGH_SQL + ") from " + Benefits.FROM_SQL
                + " where b.organization_id = :org and " + Benefits.STATUS_SQL + " = 'ACTIVE'" + filter;
        return transactions.execute(() -> jdbc.sql(sql).params(params).query((rs, n) -> new BenefitSummary(rs.getInt(1), rs.getInt(2))).single());
    }
}

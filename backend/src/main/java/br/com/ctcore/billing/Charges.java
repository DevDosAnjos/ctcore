package br.com.ctcore.billing;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import br.com.ctcore.student.Students;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-COB-01: lista de cobranças. A situação (Pendente, Vence hoje, Em atraso, Paga com atraso) é calculada em SQL pela data do fuso da unidade da cobrança, nunca
 * gravada (D1, D2); o valor devolvido é sempre o gravado, sem juros nem multa (RN-COB-07). Só leitura, sem auditoria.
 */
@Service
public class Charges {
    public record StudentRef(UUID id, String name, String photoUrl) {}

    public record Named(UUID id, String name) {}

    public record PaymentRef(String method, OffsetDateTime paidAt) {}

    public record Row(UUID id, StudentRef student, Named unit, String type, Named modality, LocalDate referenceMonth, String description, long amountCents,
            LocalDate dueOn, String status, PaymentRef payment) {}

    public record Counts(long overdue, long dueToday, long inReview, long paidMonth) {}

    public record Totals(long count, long amountCents) {}

    public record ChargeList(List<Row> items, int page, int size, long total, Counts counts, Totals totals) {}

    /** R5: filtros; {@code from} e {@code to} chegam como texto para a data inválida virar 400 com o nome do campo. */
    public record Filter(String q, UUID unitId, String quick, String from, String to, String status, String type, UUID modalityId, UUID classId, UUID studentId) {
        public Filter(String q, UUID unitId, String quick, String from, String to, String status, String type, UUID modalityId, UUID classId) {
            this(q, unitId, quick, from, to, status, type, modalityId, classId, null);
        }
    }

    private static final Set<String> QUICK = Set.of("OVERDUE", "DUE_TODAY", "IN_REVIEW", "PAID_MONTH");
    private static final Set<String> STATUSES = Set.of("PENDING", "DUE_TODAY", "OVERDUE", "IN_REVIEW", "PAID", "PAID_LATE", "CANCELED", "RENEGOTIATED", "REFUNDED");
    private static final Set<String> TYPES = Set.of("MONTHLY", "ENROLLMENT_FEE", "ONE_OFF", "INSTALLMENT", "READJUSTMENT_DIFF");
    private static final Map<String, String> SORTS = Map.of("dueOn", "c.due_on", "amount", "c.amount_cents", "student", "s.name_key");
    public static final Set<String> SORT_FIELDS = SORTS.keySet();

    /** A data de hoje no fuso da unidade de alias {@code unit}; o parâmetro {@code :now} é o instante atual. */
    public static String today(String unit) {
        return "(cast(:now as timestamptz) at time zone " + unit + ".timezone)::date";
    }

    /** RN-COB-07: aberta e passada do vencimento mais a tolerância (sem juros nem multa). Aliases da cobrança e da unidade dela. */
    public static String overdue(String charge, String unit) {
        return "(" + charge + ".status = 'OPEN' and " + charge + ".due_on + " + charge + ".tolerance_days < " + today(unit) + ")";
    }

    /** Situação calculada (R2). Aliases {@code c} (cobrança), {@code u} (unidade dela) e {@code p} (pagamento confirmado). */
    static final String SITUATION = "(case when c.status = 'OPEN' then case when " + overdue("c", "u") + " then 'OVERDUE' when c.due_on = " + today("u")
            + " then 'DUE_TODAY' else 'PENDING' end when c.status = 'PAID' then case when (p.paid_at at time zone u.timezone)::date > c.due_on + c.tolerance_days "
            + "then 'PAID_LATE' else 'PAID' end else c.status end)";

    /** D3: paga e com o pagamento no mês corrente do fuso da unidade. */
    private static final String PAID_MONTH = "(c.status = 'PAID' and to_char(p.paid_at at time zone u.timezone, 'YYYYMM') = to_char(" + today("u") + ", 'YYYYMM'))";

    static final String FROM = " from charge c join unit u on u.organization_id = c.organization_id and u.id = c.unit_id "
            + "join student s on s.organization_id = c.organization_id and s.id = c.student_id "
            + "left join payment p on p.organization_id = c.organization_id and p.charge_id = c.id and p.status = 'CONFIRMED' "
            + "left join enrollment_modality em on em.organization_id = c.organization_id and em.id = c.enrollment_modality_id "
            + "left join modality md on md.id = em.modality_id ";

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final Students students;
    private final Clock clock;

    public Charges(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, Students students, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.students = students;
        this.clock = clock;
    }

    public ChargeList list(AccountPrincipal who, Filter f, PageRequest page) {
        if (who == null || who.role() == null || permissions.effective(who).get(Permission.COLLECTIONS) == Access.NONE) throw new AccessDeniedException("Permission denied");
        if (f.quick() != null && !QUICK.contains(f.quick())) throw new ApiValidationException("quick", "inválido");
        if (f.status() != null && !STATUSES.contains(f.status())) throw new ApiValidationException("status", "inválido");
        if (f.type() != null && !TYPES.contains(f.type())) throw new ApiValidationException("type", "inválido");
        LocalDate from = date("from", f.from()), to = date("to", f.to());
        if (from != null && to != null && from.isAfter(to)) throw new ApiValidationException("from", "depois de to");
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            if (f.unitId() != null) exists("unit", org, f.unitId());
            if (f.modalityId() != null) {
                jdbc.sql("select 1 from organization_modality where organization_id = :org and modality_id = :id").param("org", org).param("id", f.modalityId())
                        .query(Integer.class).optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            }
            if (f.classId() != null) exists("training_class", org, f.classId());
            if (f.studentId() != null) exists("student", org, f.studentId());
            boolean admin = who.role() == Role.ADMINISTRATOR;
            Set<UUID> reach = who.units() == null ? Set.of() : who.units();
            if (admin && (reach.isEmpty() || f.unitId() != null && !reach.contains(f.unitId()))) {
                return new ChargeList(List.of(), page.page(), page.size(), 0, new Counts(0, 0, 0, 0), new Totals(0, 0));
            }
            Map<String, Object> params = new HashMap<>();
            params.put("org", org);
            params.put("now", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            StringBuilder scope = new StringBuilder("c.organization_id = :org");
            if (admin) {
                scope.append(" and c.unit_id in (:reach)");
                params.put("reach", reach);
            }
            if (f.unitId() != null) {
                scope.append(" and c.unit_id = :unit");
                params.put("unit", f.unitId());
            }
            // D4: as contagens dos atalhos valem só para o alcance e a unidade.
            Counts counts = jdbc.sql("select count(*) filter (where " + SITUATION + " = 'OVERDUE'), count(*) filter (where " + SITUATION + " = 'DUE_TODAY'), "
                    + "count(*) filter (where c.status = 'IN_REVIEW'), count(*) filter (where " + PAID_MONTH + ")" + FROM + "where " + scope).params(params)
                    .query((rs, n) -> new Counts(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4))).single();

            StringBuilder where = new StringBuilder(scope);
            if (f.q() != null && !f.q().isBlank()) {
                where.append(" and s.name_key like :q escape '\\'");
                params.put("q", "%" + Modalities.normalize(f.q()).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            }
            if (f.quick() != null) where.append(" and ").append(quick(f.quick()));
            if (from != null) {
                where.append(" and c.due_on >= :from");
                params.put("from", from);
            }
            if (to != null) {
                where.append(" and c.due_on <= :to");
                params.put("to", to);
            }
            if (f.status() != null) {
                where.append(" and ").append(SITUATION).append(" = :status");
                params.put("status", f.status());
            }
            if (f.type() != null) {
                where.append(" and c.type = :type");
                params.put("type", f.type());
            }
            if (f.modalityId() != null) {
                where.append(" and em.modality_id = :modality");
                params.put("modality", f.modalityId());
            }
            if (f.studentId() != null) {
                where.append(" and c.student_id = :student");
                params.put("student", f.studentId());
            }
            if (f.classId() != null) {
                where.append(" and (em.primary_class_id = :class or exists (select 1 from enrollment_modality_class o where o.organization_id = em.organization_id "
                        + "and o.modality_row_id = em.id and o.class_id = :class))");
                params.put("class", f.classId());
            }

            Totals totals = jdbc.sql("select count(*), coalesce(sum(c.amount_cents), 0)" + FROM + "where " + where).params(params)
                    .query((rs, n) -> new Totals(rs.getLong(1), rs.getLong(2))).single();
            var sort = page.sort() == null ? new PageRequest.Sort("dueOn", true) : page.sort();
            params.put("limit", page.size());
            params.put("offset", page.offset());
            List<Row> rows = jdbc.sql("select " + COLUMNS + FROM + "where " + where + " order by " + SORTS.get(sort.field()) + (sort.desc() ? " desc" : " asc")
                    + ", c.id limit :limit offset :offset").params(params).query(this::map).list();
            return new ChargeList(rows, page.page(), page.size(), totals.count(), counts, totals);
        });
    }

    static final String COLUMNS = "c.id, s.id, s.name, s.photo, u.id, u.name, c.type, md.id, md.name, c.reference_month, c.description, c.amount_cents, c.due_on, "
            + SITUATION + ", p.method, p.paid_at";

    private Row map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        UUID modality = rs.getObject(8, UUID.class);
        String method = rs.getString(15);
        return new Row(rs.getObject(1, UUID.class), new StudentRef(rs.getObject(2, UUID.class), rs.getString(3), students.photoUrl(rs.getString(4))),
                new Named(rs.getObject(5, UUID.class), rs.getString(6)), rs.getString(7), modality == null ? null : new Named(modality, rs.getString(9)),
                rs.getObject(10, LocalDate.class), rs.getString(11), rs.getLong(12), rs.getObject(13, LocalDate.class), rs.getString(14),
                method == null ? null : new PaymentRef(method, rs.getObject(16, OffsetDateTime.class)));
    }

    /** A linha da lista de uma cobrança (situação calculada hoje); chamar dentro da transação da organização. */
    Row one(UUID org, UUID id) {
        return jdbc.sql("select " + COLUMNS + FROM + "where c.organization_id = :org and c.id = :id").param("org", org).param("id", id)
                .param("now", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)).query(this::map).single();
    }

    public record OpenCharge(UUID id, LocalDate referenceMonth, LocalDate dueOn, long amountCents, String status) {}

    /** RN-MAT-17: cobranças em aberto da modalidade, com a situação calculada; chamar dentro da transação da organização. */
    public List<OpenCharge> openOfModality(UUID org, UUID modalityRow) {
        return jdbc.sql("select c.id, c.reference_month, c.due_on, c.amount_cents, " + SITUATION + FROM + "where c.organization_id = :org and c.enrollment_modality_id = :row "
                + "and c.status = 'OPEN' order by c.due_on, c.reference_month, c.id").param("org", org).param("row", modalityRow).param("now", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .query((rs, n) -> new OpenCharge(rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class), rs.getObject(3, LocalDate.class), rs.getLong(4), rs.getString(5))).list();
    }

    private static String quick(String quick) {
        return switch (quick) {
            case "OVERDUE", "DUE_TODAY" -> SITUATION + " = '" + quick + "'";
            case "IN_REVIEW" -> "c.status = 'IN_REVIEW'";
            default -> PAID_MONTH;
        };
    }

    private static LocalDate date(String field, String value) {
        if (value == null) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ApiValidationException(field, "data inválida");
        }
    }

    private void exists(String table, UUID org, UUID id) {
        jdbc.sql("select 1 from " + table + " where organization_id = :org and id = :id").param("org", org).param("id", id).query(Integer.class).optional()
                .orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
    }
}

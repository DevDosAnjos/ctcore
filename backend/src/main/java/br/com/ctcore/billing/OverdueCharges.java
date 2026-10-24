package br.com.ctcore.billing;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import br.com.ctcore.student.Students;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-COB-06: a lista de cobranças em atraso (mesma regra de "Em atraso" da lista, RN-COB-07, sem juros) e o envio manual de lembrete por e-mail. Só quem tem
 * cobranças completo na unidade (D5, igual a cancelar e renegociar). O envio trava as linhas ({@code for update}), é um pedido só (404 para tudo se alguma
 * cobrança estiver fora do alcance) e devolve o resultado de cada cobrança.
 */
@Service
public class OverdueCharges {
    public record LastReminder(OffsetDateTime at, String channel) {}

    public record Payer(String name, String phone) {}

    public record Item(UUID id, Charges.StudentRef student, Charges.Named unit, String type, LocalDate referenceMonth, String description, long amountCents,
            LocalDate dueOn, int daysLate, Integer daysToBlock, LastReminder lastReminder, Payer payer) {}

    public record OverdueList(List<Item> items, int page, int size, long total) {}

    public record Filter(String q, UUID unitId) {}

    public record ReminderRequest(List<UUID> chargeIds) {}

    public record Outcome(UUID chargeId, String result) {}

    public record SendResults(List<Outcome> results) {}

    static final int MAX_IDS = 100;
    static final Duration MANUAL_INTERVAL = Duration.ofHours(24);
    /** Mais dias de atraso é vencimento mais antigo: a direção de {@code daysLate} é invertida na ordenação. */
    public static final Set<String> SORT_FIELDS = Set.of("daysLate", "amount", "student");

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final Students students;
    private final ReminderSender sender;
    private final AuditService audit;
    private final Clock clock;

    public OverdueCharges(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, Students students, ReminderSender sender,
            AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.students = students;
        this.sender = sender;
        this.audit = audit;
        this.clock = clock;
    }

    private void requireCollections(AccountPrincipal who) {
        if (who == null || who.role() == null || permissions.effective(who).get(Permission.COLLECTIONS) == Access.NONE) throw new AccessDeniedException("Permission denied");
    }

    public OverdueList list(AccountPrincipal who, Filter f, PageRequest page) {
        requireCollections(who);
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            if (f.unitId() != null) {
                jdbc.sql("select 1 from unit where organization_id = :org and id = :id").param("org", org).param("id", f.unitId()).query(Integer.class).optional()
                        .orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            }
            boolean admin = who.role() == Role.ADMINISTRATOR;
            Set<UUID> reach = who.units() == null ? Set.of() : who.units();
            if (admin && (reach.isEmpty() || f.unitId() != null && !reach.contains(f.unitId()))) return new OverdueList(List.of(), page.page(), page.size(), 0);
            Map<String, Object> params = new HashMap<>();
            params.put("org", org);
            params.put("now", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            StringBuilder where = new StringBuilder("c.organization_id = :org and " + Charges.overdue("c", "u"));
            if (admin) {
                where.append(" and c.unit_id in (:reach)");
                params.put("reach", reach);
            }
            if (f.unitId() != null) {
                where.append(" and c.unit_id = :unit");
                params.put("unit", f.unitId());
            }
            if (f.q() != null && !f.q().isBlank()) {
                where.append(" and s.name_key like :q escape '\\'");
                params.put("q", "%" + Modalities.normalize(f.q()).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            }
            String from = " from charge c join unit u on u.organization_id = c.organization_id and u.id = c.unit_id "
                    + "join student s on s.organization_id = c.organization_id and s.id = c.student_id "
                    + "left join guardian g on g.organization_id = c.organization_id and g.id = c.payer_guardian_id "
                    + "join unit_settings us on us.organization_id = c.organization_id and us.unit_id = c.unit_id "
                    + "join organization_settings os on os.organization_id = c.organization_id "
                    + "left join lateral (select r.created_at, r.channel from charge_reminder r where r.organization_id = c.organization_id and r.charge_id = c.id "
                    + "and r.status = 'SENT' order by r.created_at desc, r.id limit 1) lr on true where " + where;
            long total = jdbc.sql("select count(*)" + from).params(params).query(Long.class).single();
            var sort = page.sort() == null ? new PageRequest.Sort("daysLate", true) : page.sort();
            String order = switch (sort.field()) {
                case "amount" -> "c.amount_cents" + (sort.desc() ? " desc" : " asc");
                case "student" -> "s.name_key" + (sort.desc() ? " desc" : " asc");
                default -> "c.due_on" + (sort.desc() ? " asc" : " desc");
            };
            params.put("limit", page.size());
            params.put("offset", page.offset());
            List<Item> items = jdbc.sql("select c.id, s.id, s.name, s.photo, u.id, u.name, c.type, c.reference_month, c.description, c.amount_cents, c.due_on, "
                    + Charges.today("u") + " - c.due_on, case when c.type in ('MONTHLY', 'INSTALLMENT', 'READJUSTMENT_DIFF') and us.payment_account_reference is not null "
                    + "then greatest(0, c.due_on + os.deactivation_days - " + Charges.today("u") + ") end, lr.created_at, lr.channel, coalesce(g.name, s.name), "
                    + "case when c.payer_guardian_id is null then s.phone else g.phone end" + from + " order by " + order + ", c.id limit :limit offset :offset")
                    .params(params).query((rs, n) -> new Item(rs.getObject(1, UUID.class), new Charges.StudentRef(rs.getObject(2, UUID.class), rs.getString(3),
                            students.photoUrl(rs.getString(4))), new Charges.Named(rs.getObject(5, UUID.class), rs.getString(6)), rs.getString(7),
                            rs.getObject(8, LocalDate.class), rs.getString(9), rs.getLong(10), rs.getObject(11, LocalDate.class), rs.getInt(12),
                            rs.getObject(13) == null ? null : rs.getInt(13), rs.getObject(14) == null ? null : new LastReminder(rs.getObject(14, OffsetDateTime.class),
                            rs.getString(15)), new Payer(rs.getString(16), rs.getString(17)))).list();
            return new OverdueList(items, page.page(), page.size(), total);
        });
    }

    private record Locked(UUID id, UUID unit, String status, LocalDate dueOn, LocalDate today, boolean recent) {}

    /** R9: um pedido, tudo travado antes; 404 para o pedido inteiro se alguma cobrança não existe ou está fora do alcance. */
    public SendResults send(AccountPrincipal who, List<UUID> chargeIds, String ip) {
        requireCollections(who);
        if (chargeIds == null || chargeIds.isEmpty() || chargeIds.size() > MAX_IDS || chargeIds.contains(null) || new HashSet<>(chargeIds).size() != chargeIds.size()) {
            throw new ApiValidationException("chargeIds", "de 1 a " + MAX_IDS + " cobranças, sem repetir");
        }
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            Map<UUID, Locked> found = new HashMap<>();
            jdbc.sql("select c.id, c.unit_id, c.status, c.due_on, " + Charges.today("u") + ", exists (select 1 from charge_reminder r where r.charge_id = c.id "
                    + "and r.kind = 'MANUAL' and r.status = 'SENT' and r.created_at > :since) from charge c join unit u on u.organization_id = c.organization_id "
                    + "and u.id = c.unit_id where c.organization_id = :org and c.id in (:ids) order by c.id for update of c").param("org", org).param("ids", chargeIds)
                    .param("now", now).param("since", now.minus(MANUAL_INTERVAL))
                    .query((rs, n) -> new Locked(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getObject(4, LocalDate.class),
                            rs.getObject(5, LocalDate.class), rs.getBoolean(6))).list().forEach(l -> found.put(l.id(), l));
            if (found.size() != chargeIds.size()) throw new ApiStatusException(ErrorCode.NOT_FOUND);
            for (Locked l : found.values()) {
                if (permissions.grant(who, Permission.COLLECTIONS, l.unit()) != Access.FULL) throw new ApiStatusException(ErrorCode.NOT_FOUND);
            }
            List<Outcome> results = new ArrayList<>();
            for (UUID id : chargeIds) {
                Locked l = found.get(id);
                if (!"OPEN".equals(l.status())) {
                    results.add(new Outcome(id, "NOT_OPEN"));
                } else if (l.recent()) {
                    results.add(new Outcome(id, "RECENT"));
                } else {
                    var result = sender.send(org, id, "MANUAL", null, ReminderSender.template(l.today(), l.dueOn()), who.accountId(), null);
                    audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), "charge.reminder_sent", "charge", id.toString(),
                            l.unit(), Map.of(), Map.of("channel", "EMAIL", "result", result.name()), null, ip));
                    results.add(new Outcome(id, result.name()));
                }
            }
            return new SendResults(results);
        });
    }
}

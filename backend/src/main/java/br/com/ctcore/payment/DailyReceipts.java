package br.com.ctcore.payment;

import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** RF-COB-08, RN-COB-12: o que a unidade recebeu em um dia (fuso da unidade), por meio e por quem lançou; sem abertura nem fechamento de caixa. */
@Service
public class DailyReceipts {
    public record Person(UUID id, String name) {}

    public record Line(UUID id, OffsetDateTime paidAt, Person student, String reference, String method, Person registeredBy, long amountCents, int receiptNumber) {}

    public record ByMethod(String method, int count, long totalCents) {}

    public record ByRegistrar(Person registeredBy, int count, long totalCents) {}

    public record Day(LocalDate date, long totalCents, int count, List<ByMethod> byMethod, List<ByRegistrar> byRegistrar, List<Line> payments) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final Clock clock;

    public DailyReceipts(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.clock = clock;
    }

    public Day day(AccountPrincipal who, UUID unitId, String date) {
        if (permissions.effective(who).get(Permission.COLLECTIONS) == Access.NONE) throw new AccessDeniedException("Permission denied");
        LocalDate wanted;
        try {
            wanted = date == null || date.isBlank() ? null : LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new ApiValidationException("date", "use AAAA-MM-DD");
        }
        if (permissions.grant(who, Permission.COLLECTIONS, unitId) == Access.NONE) {
            LocalDate day = wanted == null ? LocalDate.now(clock) : wanted;
            return new Day(day, 0, 0, List.of(), List.of(), List.of());
        }
        return transactions.execute(() -> {
            String zone = jdbc.sql("select timezone from unit where id = :unit").param("unit", unitId).query(String.class).single();
            LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(zone)));
            LocalDate day = wanted == null ? today : wanted;
            if (day.isAfter(today)) throw new ApiValidationException("date", "não pode ser futura");
            List<Line> lines = jdbc.sql("select p.id, p.paid_at, s.id, s.name, "
                    + "case when c.type = 'MONTHLY' and c.reference_month is not null "
                    + "then m.name || ' · ' || (array['jan','fev','mar','abr','mai','jun','jul','ago','set','out','nov','dez'])[extract(month from c.reference_month)::int] "
                    + "|| '/' || extract(year from c.reference_month)::int else coalesce(c.description, '') end, "
                    + "p.method, a.id, coalesce(nullif(btrim(a.name), ''), a.email), p.amount_cents, r.number "
                    + "from payment p join charge c on c.id = p.charge_id and c.organization_id = p.organization_id "
                    + "join student s on s.id = p.student_id and s.organization_id = p.organization_id "
                    + "join receipt r on r.payment_id = p.id left join enrollment_modality em on em.id = c.enrollment_modality_id "
                    + "left join modality m on m.id = em.modality_id left join account a on a.id = p.registered_by_account_id "
                    + "where p.organization_id = :org and p.unit_id = :unit and p.status = 'CONFIRMED' and (p.paid_at at time zone :zone)::date = :day "
                    + "order by p.paid_at desc, p.id desc")
                    .param("org", who.organizationId()).param("unit", unitId).param("zone", zone).param("day", day)
                    .query((rs, n) -> new Line(rs.getObject(1, UUID.class), rs.getObject(2, OffsetDateTime.class),
                            new Person(rs.getObject(3, UUID.class), rs.getString(4)), rs.getString(5), rs.getString(6),
                            rs.getObject(7, UUID.class) == null ? null : new Person(rs.getObject(7, UUID.class), rs.getString(8)), rs.getLong(9), rs.getInt(10)))
                    .list();
            Map<String, long[]> methods = new LinkedHashMap<>();
            Map<Person, long[]> registrars = new LinkedHashMap<>();
            long total = 0;
            for (Line l : lines) {
                total += l.amountCents();
                add(methods.computeIfAbsent(l.method(), k -> new long[2]), l);
                add(registrars.computeIfAbsent(Objects.requireNonNullElse(l.registeredBy(), new Person(null, null)), k -> new long[2]), l);
            }
            List<ByMethod> byMethod = methods.entrySet().stream().map(e -> new ByMethod(e.getKey(), (int) e.getValue()[0], e.getValue()[1])).toList();
            List<ByRegistrar> byRegistrar = registrars.entrySet().stream()
                    .map(e -> new ByRegistrar(e.getKey().id() == null ? null : e.getKey(), (int) e.getValue()[0], e.getValue()[1])).toList();
            return new Day(day, total, lines.size(), byMethod, byRegistrar, lines);
        });
    }

    private static void add(long[] sum, Line l) {
        sum[0]++;
        sum[1] += l.amountCents();
    }
}

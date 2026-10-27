package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RF-POR-05, RN-POR-01, RN-POR-02, RN-POR-04, RN-POR-05, RN-POR-08, RN-COB-03, RN-COB-16, RN-INA-08: aba Financeiro do portal. Cobranças em aberto (com a composição
 * guardada na cobrança) do aluno escolhido ou de toda a família, e o histórico por ano com meio, data de pagamento e recibo. Só leitura; vale também no portal
 * reduzido. Aluno que a conta não paga vem com listas vazias (D6).
 */
@Service
public class PortalFinance {
    public enum Scope { STUDENT, FAMILY }

    public record Open(UUID id, UUID studentId, String studentName, String type, String description, String referenceLabel, LocalDate referenceMonth, LocalDate dueOn,
            String situation, long amountCents, long planCents, long benefitCents, boolean canPay, PortalHome.Ref modality, PortalHome.Ref unit) {}

    public record Receipt(int number, String code) {}

    public record History(UUID id, String referenceLabel, LocalDate dueOn, long amountCents, String status, OffsetDateTime paidAt, String method, Receipt receipt) {}

    public record View(boolean reduced, List<PortalHome.Student> students, UUID selectedStudentId, Scope scope, int year, List<Integer> years, long familyOpenCents,
            List<Open> open, List<History> history) {}

    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;
    private static final String DEFAULT_ZONE = "America/Sao_Paulo";
    private static final String ZONE = "select u.timezone from student s join unit u on u.organization_id = s.organization_id and u.id = s.registered_unit_id "
            + "where s.organization_id = :org and s.id = :sid";
    private static final String FINAL = "c.status in ('PAID', 'REFUNDED', 'CANCELED', 'RENEGOTIATED')";

    /** Igual à referência do recibo (receipt_pdf_data): mensalidade "Modalidade · mês/ano"; senão a descrição. Alias {@code c} e {@code md}. */
    private static final String REFERENCE = "(case when c.type = 'MONTHLY' and c.reference_month is not null then md.name || ' · ' || (array['jan','fev','mar','abr','mai','jun',"
            + "'jul','ago','set','out','nov','dez'])[extract(month from c.reference_month)::int] || '/' || extract(year from c.reference_month)::int "
            + "else coalesce(c.description, '') end)";
    private static final String JOIN = " left join enrollment_modality em on em.organization_id = c.organization_id and em.id = c.enrollment_modality_id "
            + "left join modality md on md.id = em.modality_id ";

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final PortalStudents portal;
    private final PortalTerms terms;
    private final PortalShell shell;
    private final PortalHome home;
    private final Clock clock;

    public PortalFinance(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, PortalStudents portal, PortalTerms terms, PortalShell shell, PortalHome home,
            Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.portal = portal;
        this.terms = terms;
        this.shell = shell;
        this.home = home;
        this.clock = clock;
    }

    /** Quem chama já conferiu o aceite do termo. {@code studentId} nulo: o primeiro cartão; {@code year} nulo: o ano de hoje no fuso da unidade de cadastro. */
    public View view(AccountPrincipal who, UUID studentId, Scope scope, Integer year) {
        terms.requirePortal(who);
        if (year != null && (year < MIN_YEAR || year > MAX_YEAR)) throw new ApiValidationException("year", "ano inválido");
        Scope chosen = scope == null ? Scope.STUDENT : scope;
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            List<PortalStudents.Card> cards = portal.cards(who);
            if (cards.isEmpty() && studentId == null) {
                return new View(false, List.of(), null, chosen, year != null ? year : now.atZoneSameInstant(ZoneId.of(DEFAULT_ZONE)).getYear(), List.of(), 0, List.of(), List.of());
            }
            UUID selected = studentId != null ? studentId : cards.get(0).id();
            if (cards.stream().noneMatch(c -> c.id().equals(selected))) throw new ApiStatusException(ErrorCode.NOT_FOUND);

            Map<UUID, PortalTerms.Linked> linked = terms.candidates(who, null, false).stream().collect(Collectors.toMap(PortalTerms.Linked::id, Function.identity()));
            List<UUID> payable = cards.stream().map(PortalStudents.Card::id).filter(id -> terms.canAccept(linked.get(id))).toList();
            Map<UUID, Long> openCents = home.openCents(org, payable);
            List<PortalHome.Student> students = cards.stream().map(c -> new PortalHome.Student(c.id(), c.name(), c.photoUrl(), c.self(), openCents.getOrDefault(c.id(), 0L))).toList();
            long family = openCents.values().stream().mapToLong(Long::longValue).sum();

            String zone = jdbc.sql(ZONE).param("org", org).param("sid", selected).query(String.class).optional().orElse(DEFAULT_ZONE);
            int shown = year != null ? year : now.atZoneSameInstant(ZoneId.of(zone)).getYear();
            boolean pays = payable.contains(selected);
            List<UUID> ofOpen = chosen == Scope.FAMILY ? payable : pays ? List.of(selected) : List.of();
            List<Open> open = ofOpen.isEmpty() ? List.of() : open(org, ofOpen, now);
            boolean withHistory = chosen == Scope.STUDENT && pays;
            return new View(shell.reduced(who), students, selected, chosen, shown, withHistory ? years(org, selected) : List.of(), family, open,
                    withHistory ? history(org, selected, shown) : List.of());
        });
    }

    // R1: vencidas primeiro, depois por vencimento; a composição é a guardada na cobrança.
    private List<Open> open(UUID org, List<UUID> students, OffsetDateTime now) {
        return jdbc.sql("select c.id, c.student_id, s.name, c.type, c.description, " + REFERENCE + ", c.reference_month, c.due_on, " + PortalHome.SITUATION
                + ", c.amount_cents, c.plan_cents, c.benefit_cents, md.id, md.name, u.id, u.name "
                + "from charge c join unit u on u.organization_id = c.organization_id and u.id = c.unit_id "
                + "join student s on s.organization_id = c.organization_id and s.id = c.student_id" + JOIN
                + "where c.organization_id = :org and c.student_id in (:ids) and c.status in ('OPEN', 'IN_REVIEW')")
                .param("org", org).param("ids", students).param("now", now)
                .query((rs, n) -> new Open(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getObject(7, LocalDate.class), rs.getObject(8, LocalDate.class), rs.getString(9), rs.getLong(10), rs.getLong(11), rs.getLong(12),
                        !"IN_REVIEW".equals(rs.getString(9)), ref(rs.getObject(13, UUID.class), rs.getString(14)), ref(rs.getObject(15, UUID.class), rs.getString(16))))
                .list().stream().sorted(Comparator.comparing((Open c) -> !c.situation().equals("OVERDUE")).thenComparing(Open::dueOn).thenComparing(Open::id)).toList();
    }

    // D1: só situações finais, para a cobrança nunca aparecer nas duas listas. Meio e data vêm do pagamento (o confirmado primeiro); recibo só da paga.
    private List<History> history(UUID org, UUID student, int year) {
        return jdbc.sql("select c.id, " + REFERENCE + ", c.due_on, c.amount_cents, c.status, p.paid_at, p.method, r.number, r.code from charge c" + JOIN
                + "left join lateral (select x.id, x.paid_at, x.method from payment x where x.organization_id = c.organization_id and x.charge_id = c.id "
                + "order by (x.status = 'CONFIRMED') desc, x.paid_at desc limit 1) p on c.status in ('PAID', 'REFUNDED') "
                + "left join receipt r on r.organization_id = c.organization_id and r.payment_id = p.id and c.status = 'PAID' "
                + "where c.organization_id = :org and c.student_id = :sid and " + FINAL + " and extract(year from c.due_on) = :year order by c.due_on desc, c.id")
                .param("org", org).param("sid", student).param("year", year)
                .query((rs, n) -> new History(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, LocalDate.class), rs.getLong(4), rs.getString(5),
                        rs.getObject(6, OffsetDateTime.class), rs.getString(7), rs.getString(9) == null ? null : new Receipt(rs.getInt(8), rs.getString(9)))).list();
    }

    private List<Integer> years(UUID org, UUID student) {
        return jdbc.sql("select distinct extract(year from c.due_on)::int y from charge c where c.organization_id = :org and c.student_id = :sid and " + FINAL + " order by y desc")
                .param("org", org).param("sid", student).query(Integer.class).list();
    }

    private static PortalHome.Ref ref(UUID id, String name) {
        return id == null ? null : new PortalHome.Ref(id, name);
    }
}

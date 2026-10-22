package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationContext;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.organization.OrganizationBase;
import br.com.ctcore.permission.Permissions;
import br.com.ctcore.student.EnrollmentFixtures;
import br.com.ctcore.student.Students;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-COB-01, R1 a R9: a lista de cobranças com situação calculada, alcance, filtros, contagens, totais e ordenação. Hoje é 2026-10-15 (America/Sao_Paulo). */
class ChargesIT extends OrganizationBase {
    @Autowired JdbcClient jdbc;
    @Autowired OrganizationTransactions transactions;
    @Autowired Permissions permissions;
    @Autowired Students students;
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        BillingFixtures.cleanAll(adminJdbc());
    }

    private Charges charges() {
        return new Charges(jdbc, transactions, permissions, students, Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC));
    }

    private Charges.ChargeList list(AccountPrincipal who, Charges.Filter f, PageRequest page) {
        try (var ignored = OrganizationContext.open(who)) {
            return charges().list(who, f, page);
        }
    }

    private Charges.ChargeList list(AccountPrincipal who, Charges.Filter f) {
        return list(who, f, PageRequest.of(null, null, null, Charges.SORT_FIELDS));
    }

    private static Charges.Filter none() {
        return new Charges.Filter(null, null, null, null, null, null, null, null, null);
    }

    private Charges.Filter with(String field, Object value) {
        Map<String, Object> m = new HashMap<>();
        m.put(field, value);
        return new Charges.Filter((String) m.get("q"), (UUID) m.get("unit"), (String) m.get("quick"), (String) m.get("from"), (String) m.get("to"),
                (String) m.get("status"), (String) m.get("type"), (UUID) m.get("modality"), (UUID) m.get("class"));
    }

    private UUID student(Tenant t, UUID unit, String name) {
        return BillingFixtures.student(adminJdbc(), t.orgId(), unit, name, "1990-01-01");
    }

    private UUID unit(Tenant t, String zone) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active, timezone) values (?, ?, 'Outra', ?, true, ?)", id, t.orgId(),
                "u" + id.toString().substring(0, 12), zone);
        return id;
    }

    /** Cobrança avulsa com vencimento, tolerância e situação gravada; o pagamento, quando há, é confirmado em {@code paidAt} (UTC). */
    private UUID charge(Tenant t, UUID unit, UUID student, String description, String type, String due, int tolerance, long amount, String status, String paidAt, UUID row) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into charge (id, organization_id, unit_id, student_id, type, enrollment_modality_id, description, due_on, tolerance_days, amount_cents, "
                + "plan_cents, status) values (?, ?, ?, ?, ?, ?, ?, ?::date, ?, ?, ?, ?)", id, t.orgId(), unit, student, type, row, description, due, tolerance, amount, amount, status);
        if (paidAt != null) {
            adminJdbc().update("insert into payment (id, organization_id, charge_id, student_id, unit_id, amount_cents, method, origin, paid_at) "
                    + "values (?, ?, ?, ?, ?, ?, 'PIX_MP', 'PORTAL', ?::timestamptz)", UUID.randomUUID(), t.orgId(), id, student, unit, amount, paidAt);
        }
        return id;
    }

    private UUID open(Tenant t, UUID student, String description, String due, int tolerance) {
        return charge(t, t.unitId(), student, description, "ONE_OFF", due, tolerance, 10000, "OPEN", null, null);
    }

    private Map<String, String> statuses(Charges.ChargeList l) {
        Map<String, String> out = new HashMap<>();
        l.items().forEach(r -> out.put(r.description(), r.status()));
        return out;
    }

    private List<String> descriptions(Charges.ChargeList l) {
        return l.items().stream().map(Charges.Row::description).toList();
    }

    @Test
    @DisplayName("CA1, CA2, CA3: a linha traz os dados e a situação sai do vencimento, da tolerância, do pagamento e da data da unidade")
    void situations() {
        Tenant t = tenant();
        var c = who(t, Role.COORDINATOR);
        UUID s = student(t, t.unitId(), "Ana");
        open(t, s, "ontem", "2026-10-14", 0);
        open(t, s, "hoje", "2026-10-15", 0);
        open(t, s, "amanha", "2026-10-16", 0);
        open(t, s, "tol-dentro", "2026-10-14", 3);
        open(t, s, "tol-fim", "2026-10-12", 3);
        open(t, s, "tol-passou", "2026-10-11", 3);
        charge(t, t.unitId(), s, "em-dia", "ONE_OFF", "2026-10-10", 0, 5000, "PAID", "2026-10-10T15:00:00Z", null);
        charge(t, t.unitId(), s, "atrasada", "ONE_OFF", "2026-10-10", 0, 5000, "PAID", "2026-10-12T15:00:00Z", null);
        charge(t, t.unitId(), s, "na-tolerancia", "ONE_OFF", "2026-10-10", 3, 5000, "PAID", "2026-10-13T15:00:00Z", null);
        charge(t, t.unitId(), s, "conferencia", "ONE_OFF", "2026-10-10", 0, 5000, "IN_REVIEW", null, null);
        charge(t, t.unitId(), s, "cancelada", "ONE_OFF", "2026-10-10", 0, 5000, "CANCELED", null, null);
        charge(t, t.unitId(), s, "renegociada", "ONE_OFF", "2026-10-10", 0, 5000, "RENEGOTIATED", null, null);
        charge(t, t.unitId(), s, "estornada", "ONE_OFF", "2026-10-10", 0, 5000, "REFUNDED", null, null);

        var l = list(c, none(), PageRequest.of(null, 50, null, Charges.SORT_FIELDS));

        assertThat(statuses(l)).containsEntry("ontem", "OVERDUE").containsEntry("hoje", "DUE_TODAY").containsEntry("amanha", "PENDING")
                .containsEntry("tol-dentro", "PENDING").containsEntry("tol-fim", "PENDING").containsEntry("tol-passou", "OVERDUE")
                .containsEntry("em-dia", "PAID").containsEntry("atrasada", "PAID_LATE").containsEntry("na-tolerancia", "PAID")
                .containsEntry("conferencia", "IN_REVIEW").containsEntry("cancelada", "CANCELED").containsEntry("renegociada", "RENEGOTIATED")
                .containsEntry("estornada", "REFUNDED");
        var paid = l.items().stream().filter(r -> r.description().equals("em-dia")).findFirst().orElseThrow();
        assertThat(paid.student().name()).isEqualTo("Ana");
        assertThat(paid.student().photoUrl()).isNull();
        assertThat(paid.unit().id()).isEqualTo(t.unitId());
        assertThat(paid.modality()).isNull();
        assertThat(paid.amountCents()).isEqualTo(5000);
        assertThat(paid.payment().method()).isEqualTo("PIX_MP");
        assertThat(paid.payment().paidAt()).isNotNull();
        assertThat(l.items().stream().filter(r -> r.description().equals("ontem")).findFirst().orElseThrow().payment()).isNull();
    }

    @Test
    @DisplayName("CA1: mensalidade paga traz modalidade e mês de referência")
    void monthlyRow() {
        Tenant t = tenant();
        UUID s = student(t, t.unitId(), "Bia");
        var enrolled = EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), s, t.unitId(), "ACTIVE", 0, null);
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into charge (id, organization_id, unit_id, student_id, type, enrollment_modality_id, reference_month, due_on, plan_cents, amount_cents, status) "
                + "values (?, ?, ?, ?, 'MONTHLY', ?, date '2026-10-01', date '2026-10-10', 12000, 12000, 'OPEN')", id, t.orgId(), t.unitId(), s, enrolled.row());

        var row = list(who(t, Role.COORDINATOR), none()).items().get(0);

        assertThat(row.type()).isEqualTo("MONTHLY");
        assertThat(row.modality().name()).isNotBlank();
        assertThat(row.referenceMonth().toString()).isEqualTo("2026-10-01");
        assertThat(row.status()).isEqualTo("OVERDUE");
    }

    @Test
    @DisplayName("CA3: o hoje é o do fuso da unidade; a mesma data vence hoje em São Paulo e já está em atraso em Auckland")
    void unitTimezone() {
        Tenant t = tenant();
        UUID auckland = unit(t, "Pacific/Auckland");
        UUID s = student(t, t.unitId(), "Caio");
        charge(t, t.unitId(), s, "sp", "ONE_OFF", "2026-10-15", 0, 100, "OPEN", null, null);
        charge(t, auckland, s, "nz", "ONE_OFF", "2026-10-15", 0, 100, "OPEN", null, null);

        assertThat(statuses(list(who(t, Role.COORDINATOR), none()))).containsEntry("sp", "DUE_TODAY").containsEntry("nz", "OVERDUE");
    }

    @Test
    @DisplayName("CA11: cobrança vencida há 40 dias devolve o valor gravado, sem juros nem multa, e o total soma o mesmo valor")
    void noInterest() throws Exception {
        Tenant t = tenant();
        UUID s = student(t, t.unitId(), "Dani");
        open(t, s, "velha", "2026-09-05", 0);
        adminJdbc().update("update charge set amount_cents = 12000, plan_cents = 12000 where organization_id = ?", t.orgId());

        var l = list(who(t, Role.COORDINATOR), none());

        assertThat(l.items().get(0).amountCents()).isEqualTo(12000);
        assertThat(l.totals()).isEqualTo(new Charges.Totals(1, 12000));
        assertThat(json.writeValueAsString(l.items().get(0))).doesNotContain("interest", "fine", "juros", "multa");
    }

    @Test
    @DisplayName("CA4: o Administrador vê só as unidades dele; unitId de fora dá lista vazia; sem unidades, vazia; o Coordenador vê todas")
    void reach() {
        Tenant t = tenant();
        UUID b = unit(t, "America/Sao_Paulo");
        UUID s = student(t, t.unitId(), "Eva");
        charge(t, t.unitId(), s, "a", "ONE_OFF", "2026-10-20", 0, 100, "OPEN", null, null);
        charge(t, b, s, "b", "ONE_OFF", "2026-10-20", 0, 100, "OPEN", null, null);
        var admin = who(t, Role.ADMINISTRATOR);

        assertThat(descriptions(list(admin, none()))).containsExactly("a");
        assertThat(list(admin, with("unit", b)).items()).isEmpty();
        assertThat(list(admin, with("unit", b)).counts()).isEqualTo(new Charges.Counts(0, 0, 0, 0));
        assertThat(list(admin, with("unit", t.unitId())).items()).hasSize(1);
        var nobody = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of());
        assertThat(list(nobody, none()).total()).isZero();
        assertThat(descriptions(list(who(t, Role.COORDINATOR), none()))).containsExactlyInAnyOrder("a", "b");
        assertThat(descriptions(list(who(t, Role.COORDINATOR), with("unit", b)))).containsExactly("b");
    }

    @Test
    @DisplayName("CA7, CA8: atalhos e filtros por período, situação, tipo, modalidade, turma, busca e combinações; sem modalidade não passa pelo filtro de modalidade")
    void filters() {
        Tenant t = tenant();
        var c = who(t, Role.COORDINATOR);
        UUID ana = student(t, t.unitId(), "Ana Souza"), bia = student(t, t.unitId(), "Bia Lima");
        var e = EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), ana, t.unitId(), "ACTIVE", 0, null);
        UUID modality = adminJdbc().queryForObject("select modality_id from enrollment_modality where id = ?", UUID.class, e.row());
        UUID other = EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), bia, t.unitId(), "ACTIVE", 1, null).klass();
        adminJdbc().update("insert into enrollment_modality_class (organization_id, modality_row_id, class_id) values (?, ?, ?)", t.orgId(), e.row(), other);
        charge(t, t.unitId(), ana, "mensal", "MONTHLY", "2026-10-10", 0, 10000, "OPEN", null, e.row());
        charge(t, t.unitId(), ana, "pago-mes", "ONE_OFF", "2026-10-03", 0, 5000, "PAID", "2026-10-03T12:00:00Z", null);
        charge(t, t.unitId(), ana, "pago-tarde-mes", "ONE_OFF", "2026-09-20", 0, 5000, "PAID", "2026-10-02T12:00:00Z", null);
        charge(t, t.unitId(), bia, "pago-antes", "ONE_OFF", "2026-09-20", 0, 5000, "PAID", "2026-09-21T12:00:00Z", null);
        charge(t, t.unitId(), bia, "estornado", "ONE_OFF", "2026-10-01", 0, 5000, "REFUNDED", null, null);
        charge(t, t.unitId(), bia, "revisao", "ONE_OFF", "2026-10-30", 0, 5000, "IN_REVIEW", null, null);
        charge(t, t.unitId(), bia, "hoje", "ENROLLMENT_FEE", "2026-10-15", 0, 5000, "OPEN", null, null);

        assertThat(descriptions(list(c, with("quick", "OVERDUE")))).containsExactly("mensal");
        assertThat(descriptions(list(c, with("quick", "DUE_TODAY")))).containsExactly("hoje");
        assertThat(descriptions(list(c, with("quick", "IN_REVIEW")))).containsExactly("revisao");
        assertThat(descriptions(list(c, with("quick", "PAID_MONTH")))).containsExactlyInAnyOrder("pago-mes", "pago-tarde-mes");
        assertThat(descriptions(list(c, new Charges.Filter(null, null, null, "2026-10-10", "2026-10-15", null, null, null, null)))).containsExactlyInAnyOrder("mensal", "hoje");
        assertThat(descriptions(list(c, with("status", "PAID_LATE")))).containsExactlyInAnyOrder("pago-tarde-mes", "pago-antes");
        assertThat(descriptions(list(c, with("type", "ENROLLMENT_FEE")))).containsExactly("hoje");
        assertThat(descriptions(list(c, with("modality", modality)))).containsExactly("mensal");
        assertThat(descriptions(list(c, with("class", e.klass())))).containsExactly("mensal");
        assertThat(descriptions(list(c, with("class", other)))).containsExactly("mensal");
        assertThat(descriptions(list(c, with("q", "BIA lim")))).hasSize(4);
        assertThat(descriptions(list(c, with("q", "  ")))).hasSize(7);
        assertThat(list(c, with("q", "%")).items()).isEmpty();
        assertThat(descriptions(list(c, new Charges.Filter("ana", null, "PAID_MONTH", null, "2026-10-02", "PAID_LATE", "ONE_OFF", null, null)))).containsExactly("pago-tarde-mes");
    }

    @Test
    @DisplayName("CA9: modalidade, turma ou unidade inexistente ou de outra organização dá 404; valores inválidos dão 400")
    void validation() {
        Tenant t = tenant(), other = tenant();
        var c = who(t, Role.COORDINATOR);
        UUID s = student(other, other.unitId(), "Fora");
        var enrolled = EnrollmentFixtures.enroll(adminJdbc(), other.orgId(), s, other.unitId(), "ACTIVE", 0, null);
        UUID foreignModality = adminJdbc().queryForObject("select modality_id from enrollment_modality where id = ?", UUID.class, enrolled.row());

        assertThatThrownBy(() -> list(c, with("modality", foreignModality))).isInstanceOf(ApiStatusException.class);
        assertThatThrownBy(() -> list(c, with("modality", UUID.randomUUID()))).isInstanceOf(ApiStatusException.class);
        assertThatThrownBy(() -> list(c, with("class", enrolled.klass()))).isInstanceOf(ApiStatusException.class);
        assertThatThrownBy(() -> list(c, with("unit", other.unitId()))).isInstanceOf(ApiStatusException.class);
        for (Charges.Filter bad : List.of(with("status", "X"), with("type", "X"), with("quick", "X"), with("from", "2026-13-01"), with("to", "ontem"),
                new Charges.Filter(null, null, null, "2026-10-20", "2026-10-10", null, null, null, null))) {
            assertThatThrownBy(() -> list(c, bad)).isInstanceOf(ApiValidationException.class);
        }
        assertThatThrownBy(() -> PageRequest.of(null, 30, null, Charges.SORT_FIELDS)).isInstanceOf(ApiValidationException.class);
        assertThatThrownBy(() -> PageRequest.of(null, null, "x,asc", Charges.SORT_FIELDS)).isInstanceOf(ApiValidationException.class);
    }

    @Test
    @DisplayName("CA10: totais somam tudo o que está filtrado em qualquer página; as contagens ignoram os outros filtros e mudam com a unidade")
    void countsAndTotals() {
        Tenant t = tenant();
        UUID b = unit(t, "America/Sao_Paulo");
        var c = who(t, Role.COORDINATOR);
        UUID s = student(t, t.unitId(), "Gil");
        charge(t, t.unitId(), s, "1", "ONE_OFF", "2026-10-01", 0, 10000, "OPEN", null, null);
        charge(t, t.unitId(), s, "2", "ONE_OFF", "2026-10-02", 0, 15000, "OPEN", null, null);
        charge(t, t.unitId(), s, "3", "ONE_OFF", "2026-10-03", 0, 5000, "OPEN", null, null);
        charge(t, t.unitId(), s, "4", "MONTHLY", "2026-10-15", 0, 700, "OPEN", null, null);
        charge(t, t.unitId(), s, "5", "ONE_OFF", "2026-10-20", 0, 800, "IN_REVIEW", null, null);
        charge(t, b, s, "6", "ONE_OFF", "2026-10-01", 0, 900, "OPEN", null, null);

        var page2 = list(c, new Charges.Filter(null, t.unitId(), null, null, null, "OVERDUE", "ONE_OFF", null, null), PageRequest.of(2, 25, null, Charges.SORT_FIELDS));
        assertThat(page2.totals()).isEqualTo(new Charges.Totals(3, 30000));
        assertThat(page2.items()).isEmpty();
        assertThat(page2.total()).isEqualTo(3);
        assertThat(page2.counts()).isEqualTo(new Charges.Counts(3, 1, 1, 0));
        assertThat(list(c, none()).counts()).isEqualTo(new Charges.Counts(4, 1, 1, 0));
    }

    @Test
    @DisplayName("CA6: ordem por vencimento decrescente e por valor ou aluno, com desempate por id; segunda página")
    void sorting() {
        Tenant t = tenant();
        var c = who(t, Role.COORDINATOR);
        UUID zed = student(t, t.unitId(), "Zed"), abel = student(t, t.unitId(), "Abel");
        charge(t, t.unitId(), zed, "z", "ONE_OFF", "2026-10-01", 0, 100, "OPEN", null, null);
        charge(t, t.unitId(), abel, "a", "ONE_OFF", "2026-10-03", 0, 300, "OPEN", null, null);
        charge(t, t.unitId(), abel, "b", "ONE_OFF", "2026-10-02", 0, 200, "OPEN", null, null);
        for (int i = 0; i < 25; i++) charge(t, t.unitId(), zed, "x" + i, "ONE_OFF", "2026-09-01", 0, 50, "OPEN", null, null);

        assertThat(descriptions(list(c, none())).subList(0, 3)).containsExactly("a", "b", "z");
        assertThat(descriptions(list(c, none(), PageRequest.of(null, 50, "amount,asc", Charges.SORT_FIELDS))).get(25)).isEqualTo("z");
        assertThat(descriptions(list(c, none(), PageRequest.of(null, null, "student,asc", Charges.SORT_FIELDS))).subList(0, 2)).containsExactlyInAnyOrder("a", "b");
        var second = list(c, none(), PageRequest.of(2, 25, null, Charges.SORT_FIELDS));
        assertThat(second.items()).hasSize(3);
        assertThat(second.total()).isEqualTo(28);
    }

    @Test
    @DisplayName("CA5, CA9 pela rota: Professor, Aluno e Responsável recebem 403, anônimo 401; Coordenador 200; erros 400")
    void route() throws Exception {
        Tenant t = tenant();
        UUID s = student(t, t.unitId(), "Hugo");
        charge(t, t.unitId(), s, "r", "ONE_OFF", "2026-10-20", 0, 100, "OPEN", null, null);

        assertThat(call(MockMvcRequestBuilders.get("/api/v1/charges"), null).getResponse().getStatus()).isEqualTo(401);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(call(MockMvcRequestBuilders.get("/api/v1/charges"), who(t, role)).getResponse().getStatus()).as(role.name()).isEqualTo(403);
        }
        var ok = call(MockMvcRequestBuilders.get("/api/v1/charges?size=25&sort=amount,asc"), who(t, Role.COORDINATOR)).getResponse();
        assertThat(ok.getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(ok.getContentAsString());
        assertThat(body.get("totals").get("count").asInt()).isEqualTo(1);
        assertThat(body.get("items").get(0).has("payment")).isTrue();
        for (String bad : new String[] {"size=30", "sort=x,asc", "status=X", "from=2026-13-01", "from=2026-10-20&to=2026-10-10"}) {
            assertThat(call(MockMvcRequestBuilders.get("/api/v1/charges?" + bad), who(t, Role.COORDINATOR)).getResponse().getStatus()).as(bad).isEqualTo(400);
        }
        assertThat(call(MockMvcRequestBuilders.get("/api/v1/charges?classId=" + UUID.randomUUID()), who(t, Role.COORDINATOR)).getResponse().getStatus()).isEqualTo(404);
    }
}

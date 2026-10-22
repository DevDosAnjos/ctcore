package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import br.com.ctcore.identity.AccountMailer;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-MAT-01 (R1 a R10): prévia, conclusão atômica, graduação de origem, comprovante, convite, perfis, isolamento e auditoria, com banco real. */
class EnrollmentConclusionIT extends OrganizationBase {
    @Autowired ObjectMapper json;
    @MockitoSpyBean AccountMailer mailer;

    record Offer(UUID unit, UUID modality, UUID klass, UUID plan) {}

    @AfterEach
    void clean() {
        Mockito.reset(mailer);
        for (String table : new String[] {"receipt", "payment", "receipt_counter", "charge", "student_graduation", "enrollment_start", "benefit", "enrollment_modality_class", "enrollment_modality", "enrollment",
                "student_guardian", "guardian", "student", "plan", "graduation_level", "training_class_schedule", "training_class", "teacher_unit",
                "teacher_modality", "teacher", "unit_modality", "organization_modality"}) {
            adminJdbc().update("delete from " + table);
        }
        adminJdbc().update("delete from email_message where to_email like '%.example'");
        String mine = "(select id from account where email like '%.example')";
        adminJdbc().update("delete from account_invite where account_id in " + mine);
        adminJdbc().update("delete from account_session where account_id in " + mine);
        adminJdbc().update("delete from account_link_unit where link_id in (select id from account_link where account_id in " + mine + ")");
        adminJdbc().update("delete from account_link where account_id in " + mine);
        adminJdbc().update("delete from account where email like '%.example'");
    }

    private AccountPrincipal as(Tenant t, Role role, UUID... units) {
        AccountPrincipal p = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), role, Set.of(units));
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", p.accountId(), p.accountId() + "@gestao.example");
        return p;
    }

    private AccountPrincipal coordinator(Tenant t) {
        return as(t, Role.COORDINATOR, t.unitId());
    }

    private UUID teacher(Tenant t) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into teacher (id, organization_id, name, name_key) values (?, ?, 'Professor', 'professor')", id, t.orgId());
        return id;
    }

    /** Modalidade `index` oferecida na unidade, com turma e plano (R$ 200,00) ativos; unidade com vencimentos 5 e 10. */
    private Offer offer(Tenant t, UUID unit, int index) {
        UUID m = adminJdbc().queryForList("select id from modality order by name", UUID.class).get(index);
        adminJdbc().update("insert into organization_modality (organization_id, modality_id, active) values (?, ?, true) on conflict do nothing", t.orgId(), m);
        adminJdbc().update("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (?, ?, ?, true) on conflict do nothing", t.orgId(), unit, m);
        adminJdbc().update("update unit_settings set due_days = '{5,10}' where unit_id = ?", unit);
        UUID klass = UUID.randomUUID(), plan = UUID.randomUUID();
        adminJdbc().update("insert into training_class (id, organization_id, unit_id, name, name_key, modality_id, head_teacher_id, status) values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                klass, t.orgId(), unit, "T" + klass, "t" + klass, m, teacher(t));
        adminJdbc().update("insert into plan (id, organization_id, unit_id, modality_id, name, name_key, value_cents, status) values (?, ?, ?, ?, ?, ?, 20000, 'ACTIVE')",
                plan, t.orgId(), unit, m, "Plano " + plan, Modalities.normalize("Plano " + plan));
        return new Offer(unit, m, klass, plan);
    }

    private UUID otherUnit(Tenant t) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'Outra', ?)", id, t.orgId(), "u" + id.toString().substring(0, 12));
        adminJdbc().update("insert into unit_settings (organization_id, unit_id, due_days) values (?, ?, '{5,10}') on conflict do nothing", t.orgId(), id);
        return id;
    }

    private UUID level(Tenant t, Offer o, boolean requireProof) {
        adminJdbc().update("update organization_modality set graduation_enabled = true, require_origin_proof = ? where organization_id = ? and modality_id = ?",
                requireProof, t.orgId(), o.modality());
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into graduation_level (id, organization_id, modality_id, position, name, color, max_degree, kids) values (?, ?, ?, 0, 'Branca', '#FFFFFF', 4, false)",
                id, t.orgId(), o.modality());
        return id;
    }

    private UUID student(Tenant t, String email, LocalDate birth) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, email, postal_code, street, number, district, city, state) "
                + "values (?, ?, ?, 'Aluno Fictício', 'aluno ficticio', ?, ?, '01001000', 'Rua', '1', 'Centro', 'Cidade', 'SP')", id, t.orgId(), t.unitId(), birth, email);
        return id;
    }

    private UUID adult(Tenant t) {
        return student(t, UUID.randomUUID() + "@aluno.example", LocalDate.of(1990, 1, 1));
    }

    private ObjectNode modality(Offer o, Integer dueDay) {
        ObjectNode n = json.createObjectNode().put("unitId", o.unit().toString()).put("modalityId", o.modality().toString())
                .put("primaryClassId", o.klass().toString()).put("planId", o.plan().toString());
        if (dueDay != null) n.put("dueDay", dueDay);
        n.putArray("otherClassIds");
        return n;
    }

    private ObjectNode body(ObjectNode... modalities) {
        ObjectNode b = json.createObjectNode();
        ArrayNode array = b.putArray("modalities");
        for (ObjectNode m : modalities) array.add(m);
        b.putArray("units");
        return b;
    }

    private ObjectNode unitAsk(ObjectNode body, UUID unit) {
        return ((ArrayNode) body.get("units")).addObject().put("unitId", unit.toString());
    }

    private MockHttpServletResponse post(UUID student, String path, JsonNode body, AccountPrincipal who) throws Exception {
        var request = MockMvcRequestBuilders.post("/api/v1/students/" + student + "/enrollment/" + path).contentType("application/json").content(body.toString());
        return call(request, who).getResponse();
    }

    private JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return json.readTree(r.getContentAsString());
    }

    private void rejected(MockHttpServletResponse r, int status, String field) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        assertThat(r.getContentAsString()).contains(field);
    }

    private int count(String sql, Object... args) {
        return adminJdbc().queryForObject(sql, Integer.class, args);
    }

    private int nothingWritten() {
        return count("select (select count(*) from enrollment) + (select count(*) from enrollment_modality) + (select count(*) from benefit) "
                + "+ (select count(*) from enrollment_start) + (select count(*) from student_graduation) + (select count(*) from charge)");
    }

    private int audits(Tenant t, String action) {
        return count("select count(*) from audit_log where organization_id = ? and action = ?", t.orgId(), action);
    }

    private LocalDate today(UUID unit) {
        return LocalDate.now(ZoneId.of(adminJdbc().queryForObject("select timezone from unit where id = ?", String.class, unit)));
    }

    private MockHttpServletResponse proof(UUID student, byte[] content, AccountPrincipal who) throws Exception {
        var request = MockMvcRequestBuilders.multipart("/api/v1/students/" + student + "/enrollment/origin-proof")
                .file(new MockMultipartFile("file", "comprovante.bin", "application/octet-stream", content));
        return call(request, who).getResponse();
    }

    private static byte[] pdf() {
        return "%PDF-1.7 fictício".getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    @DisplayName("CA1: a prévia dá mensalidade de R$ 180,00 com 10%, primeira cobrança hoje e não grava nada")
    void preview() throws Exception {
        var t = tenant();
        var o = offer(t, t.unitId(), 0);
        var s = adult(t);
        var b = body(modality(o, 10));
        ((ObjectNode) b.get("modalities").get(0)).putObject("benefit").put("type", "PERCENT").put("value", 10).put("reason", "Bolsa").put("decidedBy", "Mestre");
        var r = ok(post(s, "preview", b, coordinator(t)), 200);
        assertThat(r.at("/modalities/0/planCents").asLong()).isEqualTo(20000);
        assertThat(r.at("/modalities/0/monthlyCents").asLong()).isEqualTo(18000);
        assertThat(r.at("/modalities/0/discountCents").asLong()).isEqualTo(2000);
        assertThat(r.at("/units/0/firstDueOn").asText()).isEqualTo(today(t.unitId()).toString());
        assertThat(r.at("/units/0/toleranceDays").asInt()).isEqualTo(5);
        assertThat(r.at("/units/0/firstChargeCents").asLong()).isEqualTo(18000);
        assertThat(nothingWritten()).isZero();
        assertThat(audits(t, "student.enrolled")).isZero();
    }

    @Test
    @DisplayName("CA2, CA3: taxa 5000 na primeira vez, 0 na unidade sem taxa, de novo no retorno; isenção exige motivo, só o motivo vai para o início e a auditoria")
    void fee() throws Exception {
        var t = tenant();
        var o = offer(t, t.unitId(), 0);
        var c = coordinator(t);
        var s = adult(t);
        assertThat(ok(post(s, "preview", body(modality(o, 10)), c), 200).at("/units/0/feeCents").asLong()).isZero(); // unidade sem taxa
        adminJdbc().update("update unit_settings set enrollment_fee_enabled = true, enrollment_fee_cents = 5000 where unit_id = ?", t.unitId());
        assertThat(ok(post(s, "preview", body(modality(o, 10)), c), 200).at("/units/0/feeCents").asLong()).isEqualTo(5000);
        var exempt = body(modality(o, 10));
        unitAsk(exempt, t.unitId()).put("feeExempt", true);
        rejected(post(s, "preview", exempt, c), 422, "feeExemptReason");
        ((ObjectNode) exempt.get("units").get(0)).put("feeExemptReason", "bolsa da turma");
        var r = ok(post(s, "conclude", exempt, c), 201);
        assertThat(r.at("/summary/units/0/feeCents").asLong()).isZero();
        assertThat(r.at("/summary/units/0/feeExemptReason").asText()).isEqualTo("bolsa da turma");
        assertThat(count("select count(*) from enrollment_start where fee_cents = 0 and fee_exempt_reason = 'bolsa da turma'")).isEqualTo(1);
        assertThat(audits(t, "enrollment.fee_exempted")).isEqualTo(1);
        // ex-aluno: a taxa volta (RN-MAT-19); aluno ativo com modalidade nova na mesma unidade não paga de novo
        adminJdbc().update("update enrollment_modality set status = 'ENDED', ended_at = now()");
        assertThat(ok(post(s, "preview", body(modality(o, 10)), c), 200).at("/units/0/feeCents").asLong()).isEqualTo(5000);
        var s2 = adult(t);
        var o2 = offer(t, t.unitId(), 1);
        ok(post(s2, "conclude", body(modality(o, 10)), c), 201);
        assertThat(ok(post(s2, "preview", body(modality(o2, 10)), c), 200).at("/units/0/feeCents").asLong()).isZero();
        // unidade sem taxa: isenção recusada
        adminJdbc().update("update unit_settings set enrollment_fee_enabled = false where unit_id = ?", t.unitId());
        rejected(post(adult(t), "preview", exempt, c), 422, "feeExemptReason");
    }

    @Test
    @DisplayName("CA4: segunda cobrança sugerida e limites de 15 a 60 dias")
    void secondDue() throws Exception {
        var t = tenant();
        var o = offer(t, t.unitId(), 0);
        var c = coordinator(t);
        var s = adult(t);
        LocalDate today = today(t.unitId());
        var r = ok(post(s, "preview", body(modality(o, 10)), c), 200);
        LocalDate suggested = LocalDate.parse(r.at("/units/0/suggestedSecondDueOn").asText());
        assertThat(suggested.getDayOfMonth()).isEqualTo(10);
        assertThat(suggested).isAfterOrEqualTo(today.plusDays(15)).isBefore(today.plusDays(46));
        for (int days : new int[] {14, 61}) {
            var b = body(modality(o, 10));
            unitAsk(b, t.unitId()).put("secondDueOn", today.plusDays(days).toString());
            rejected(post(s, "preview", b, c), 422, "secondDueOn");
        }
        var b = body(modality(o, 10));
        unitAsk(b, t.unitId()).put("secondDueOn", today.plusDays(30).toString());
        assertThat(ok(post(s, "preview", b, c), 200).at("/units/0/secondDueOn").asText()).isEqualTo(today.plusDays(30).toString());
    }

    @Test
    @DisplayName("CA5, CA16, CA17: plano de outra unidade desfaz tudo; repetir dá 409; ids de outra organização dão 404")
    void atomic() throws Exception {
        var t = tenant();
        var u2 = otherUnit(t);
        var o = offer(t, t.unitId(), 0);
        var o2 = offer(t, u2, 1);
        var c = as(t, Role.COORDINATOR, t.unitId(), u2);
        var s = adult(t);
        var bad = new Offer(u2, o2.modality(), o2.klass(), o.plan());
        rejected(post(s, "conclude", body(modality(o, 10), modality(bad, 10)), c), 422, "planId");
        assertThat(nothingWritten()).isZero();

        var good = body(modality(o, 10));
        ok(post(s, "conclude", good, c), 201);
        int rows = count("select count(*) from enrollment_modality");
        assertThat(post(s, "conclude", good, c).getStatus()).isEqualTo(409);
        assertThat(count("select count(*) from enrollment_modality")).isEqualTo(rows);
        assertThat(count("select count(*) from enrollment_start")).isEqualTo(1);

        var other = tenant();
        var foreign = offer(other, other.unitId(), 0);
        var s2 = adult(t);
        assertThat(post(s2, "conclude", body(modality(foreign, 10)), coordinator(t)).getStatus()).isEqualTo(404);
        assertThat(post(s2, "conclude", body(modality(foreign, 10)), as(t, Role.ADMINISTRATOR, t.unitId())).getStatus()).isEqualTo(404);
        assertThat(post(adult(other), "conclude", body(modality(o, 10)), coordinator(t)).getStatus()).isEqualTo(404);
        assertThat(post(adult(other), "preview", body(modality(o, 10)), coordinator(t)).getStatus()).isEqualTo(404);
        assertThat(count("select count(*) from enrollment where student_id = ?", s2)).isZero();
    }

    @Test
    @DisplayName("CA6, CA13, CA15: conclui com benefício, aluno Ativo, termo pendente, início da matrícula e auditoria sem dados pessoais")
    void conclude() throws Exception {
        var t = tenant();
        var o = offer(t, t.unitId(), 0);
        var c = coordinator(t);
        var s = adult(t);
        var b = body(modality(o, 10));
        ((ObjectNode) b.get("modalities").get(0)).putObject("benefit").put("type", "PERCENT").put("value", 10).put("reason", "Bolsa").put("decidedBy", "Mestre");
        var r = ok(post(s, "conclude", b, c), 201);
        assertThat(r.at("/enrollment/status").asText()).isEqualTo("ACTIVE");
        assertThat(r.get("termPending").asBoolean()).isTrue();
        assertThat(r.at("/summary/modalities/0/monthlyCents").asLong()).isEqualTo(18000);
        assertThat(count("select count(*) from benefit where status = 'ACTIVE' and student_id = ?", s)).isEqualTo(1);
        assertThat(count("select count(*) from enrollment_start")).isEqualTo(1);
        assertThat(audits(t, "student.enrolled")).isEqualTo(1);
        assertThat(audits(t, "student.modality_added")).isEqualTo(1);
        assertThat(audits(t, "benefit.granted")).isEqualTo(1);
        String email = adminJdbc().queryForObject("select email from student where id = ?", String.class, s);
        assertThat(count("select count(*) from audit_log where organization_id = ? and (after_data::text like ? or after_data::text like '%origin-proof%')", t.orgId(),
                "%" + email + "%")).isZero();
        assertThat(count("select count(*) from audit_log where organization_id = ? and after_data::text like '%cpf%'", t.orgId())).isZero();
    }

    @Test
    @DisplayName("CA7: ex-aluno reabre a mesma matrícula")
    void reopen() throws Exception {
        var t = tenant();
        var o = offer(t, t.unitId(), 0);
        var o2 = offer(t, t.unitId(), 1);
        var c = coordinator(t);
        var s = adult(t);
        ok(post(s, "conclude", body(modality(o, 10)), c), 201);
        UUID enrollment = adminJdbc().queryForObject("select id from enrollment where student_id = ?", UUID.class, s);
        adminJdbc().update("update enrollment_modality set status = 'ENDED', ended_at = now()");
        var r = ok(post(s, "conclude", body(modality(o2, 10)), c), 201);
        assertThat(r.at("/enrollment/id").asText()).isEqualTo(enrollment.toString());
        assertThat(r.at("/enrollment/reopenedAt").isNull()).isFalse();
        assertThat(count("select count(*) from enrollment")).isEqualTo(1);
        assertThat(audits(t, "student.enrollment_reopened")).isEqualTo(1);
    }

    @Test
    @DisplayName("CA8: 9 ativas + 2 novas dá 422 modalities; dia de vencimento fora dos permitidos ou diferente do atual dá 422")
    void limits() throws Exception {
        var t = tenant();
        var c = coordinator(t);
        var s = adult(t);
        for (int i = 0; i < 9; i++) EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), s, t.unitId(), "ACTIVE", i, null);
        var a = offer(t, t.unitId(), 9);
        var b = offer(t, t.unitId(), 10);
        rejected(post(s, "conclude", body(modality(a, 5), modality(b, 5)), c), 422, "modalities");
        int rows = count("select count(*) from enrollment_modality");
        rejected(post(s, "conclude", body(modality(a, 10)), c), 422, "dueDay"); // as ativas pagam no dia 5
        var fresh = adult(t);
        rejected(post(fresh, "conclude", body(modality(a, 7)), c), 422, "dueDay");
        assertThat(count("select count(*) from enrollment_modality")).isEqualTo(rows);
        ok(post(s, "conclude", body(modality(a, null)), c), 201); // herda o dia 5
        assertThat(post(s, "conclude", body(modality(a, null)), c).getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("CA9, CA10, CA11: graduação de origem com e sem exigência de comprovante, validações e comprovante")
    void origin() throws Exception {
        var t = tenant();
        var o = offer(t, t.unitId(), 0);
        UUID level = level(t, o, true);
        var c = coordinator(t);
        var s = adult(t);
        var withOrigin = body(modality(o, 10));
        ObjectNode origin = ((ObjectNode) withOrigin.get("modalities").get(0)).putObject("origin");
        origin.put("levelId", level.toString()).put("degree", 2).put("achievedOn", today(t.unitId()).minusDays(30).toString());
        rejected(post(s, "conclude", withOrigin, c), 422, "origin.proofKey");
        assertThat(nothingWritten()).isZero();

        // comprovante: tipo e tamanho; chave de outro aluno ou de outra organização
        rejected(proof(s, "GIF89a".getBytes(StandardCharsets.ISO_8859_1), c), 422, "file");
        rejected(proof(s, new byte[5 * 1024 * 1024 + 1], c), 422, "file");
        String key = ok(proof(s, pdf(), c), 201).get("proofKey").asText();
        String otherStudentKey = ok(proof(adult(t), pdf(), c), 201).get("proofKey").asText();
        origin.put("proofKey", otherStudentKey);
        rejected(post(s, "conclude", withOrigin, c), 422, "origin.proofKey");
        var other = tenant();
        origin.put("proofKey", ok(proof(adult(other), pdf(), coordinator(other)), 201).get("proofKey").asText());
        rejected(post(s, "conclude", withOrigin, c), 422, "origin.proofKey");
        assertThat(nothingWritten()).isZero();

        origin.put("proofKey", key);
        ok(post(s, "conclude", withOrigin, c), 201);
        assertThat(count("select count(*) from student_graduation where origin and has_proof and degree = 2 and student_id = ?", s)).isEqualTo(1);
        assertThat(audits(t, "student.origin_graduation_registered")).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where organization_id = ? and after_data::text like '%origin-proof%'", t.orgId())).isZero();
    }

    @Test
    @DisplayName("CA10: sem exigência o comprovante é opcional; nível de outra modalidade, grau alto, data futura e graduação desligada dão 422")
    void originRules() throws Exception {
        var t = tenant();
        var o = offer(t, t.unitId(), 0);
        var o2 = offer(t, t.unitId(), 1);
        UUID level = level(t, o, false);
        UUID levelOther = level(t, o2, false);
        var c = coordinator(t);
        var s = adult(t);
        var b = body(modality(o, 10));
        ObjectNode origin = ((ObjectNode) b.get("modalities").get(0)).putObject("origin");
        origin.put("levelId", levelOther.toString()).put("degree", 1);
        rejected(post(s, "conclude", b, c), 422, "origin.levelId");
        origin.put("levelId", level.toString()).put("degree", 5);
        rejected(post(s, "conclude", b, c), 422, "origin.degree");
        origin.put("degree", 1).put("achievedOn", today(t.unitId()).plusDays(1).toString());
        rejected(post(s, "conclude", b, c), 422, "origin.achievedOn");
        origin.putNull("achievedOn");
        origin.put("levelId", UUID.randomUUID().toString());
        assertThat(post(s, "conclude", b, c).getStatus()).isEqualTo(404);
        adminJdbc().update("update organization_modality set graduation_enabled = false where modality_id = ?", o.modality());
        origin.put("levelId", level.toString());
        rejected(post(s, "conclude", b, c), 422, "origin");
        assertThat(nothingWritten()).isZero();
        adminJdbc().update("update organization_modality set graduation_enabled = true where modality_id = ?", o.modality());
        ok(post(s, "conclude", b, c), 201);
        assertThat(count("select count(*) from student_graduation where origin and not has_proof and proof_file is null")).isEqualTo(1);
        assertThat(count("select count(*) from email_message where template like '%GRADUATION%'")).isZero();
    }

    @Test
    @DisplayName("CA12: adulto recebe convite; falha de e-mail não desfaz; menor e conta ativa não recebem")
    void invite() throws Exception {
        var t = tenant();
        var o = offer(t, t.unitId(), 0);
        var o2 = offer(t, t.unitId(), 1);
        var c = coordinator(t);
        var adult = adult(t);
        String email = adminJdbc().queryForObject("select email from student where id = ?", String.class, adult);
        var r = ok(post(adult, "conclude", body(modality(o, 10)), c), 201);
        assertThat(r.get("inviteStatus").asText()).isEqualTo("SENT");
        assertThat(count("select count(*) from email_message where template = 'INVITE' and to_email = ?", email)).isEqualTo(1);
        assertThat(count("select count(*) from account_invite i join account a on a.id = i.account_id where a.email = ?", email)).isEqualTo(1);

        // ex-aluno com conta ativa: sem convite novo
        adminJdbc().update("update account_link set status = 'ACTIVE' where account_id = (select id from account where email = ?)", email);
        adminJdbc().update("update enrollment_modality set status = 'ENDED', ended_at = now()");
        assertThat(ok(post(adult, "conclude", body(modality(o2, 10)), c), 201).get("inviteStatus").asText()).isEqualTo("EXISTING_ACCOUNT");
        assertThat(count("select count(*) from email_message where template = 'INVITE' and to_email = ?", email)).isEqualTo(1);

        // falha no envio
        doThrow(new IllegalStateException("smtp fora")).when(mailer).invite(any(), any());
        var failing = adult(t);
        var f = ok(post(failing, "conclude", body(modality(o, 10)), c), 201);
        assertThat(f.get("inviteStatus").asText()).isEqualTo("FAILED");
        assertThat(f.at("/enrollment/status").asText()).isEqualTo("ACTIVE");
        assertThat(count("select count(*) from enrollment where student_id = ?", failing)).isEqualTo(1);
        Mockito.reset(mailer);

        // menor: nenhum convite
        int before = count("select count(*) from email_message where template = 'INVITE'");
        var minor = student(t, null, LocalDate.now().minusYears(10));
        br.com.ctcore.billing.BillingFixtures.guardian(adminJdbc(), t.orgId(), minor, true);
        assertThat(ok(post(minor, "conclude", body(modality(o, 10)), c), 201).get("inviteStatus").asText()).isEqualTo("MINOR");
        assertThat(count("select count(*) from email_message where template = 'INVITE'")).isEqualTo(before);
    }

    @Test
    @DisplayName("CA14: Professor e Aluno 403, Administrador fora do alcance 403 sem gravar, sem sessão 401")
    void profiles() throws Exception {
        var t = tenant();
        var u2 = otherUnit(t);
        var o = offer(t, t.unitId(), 0);
        var o2 = offer(t, u2, 1);
        var s = adult(t);
        var b = body(modality(o, 10));
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            var who = as(t, role, t.unitId());
            assertThat(post(s, "preview", b, who).getStatus()).isEqualTo(403);
            assertThat(post(s, "conclude", b, who).getStatus()).isEqualTo(403);
            assertThat(proof(s, pdf(), who).getStatus()).isEqualTo(403);
        }
        var admin = as(t, Role.ADMINISTRATOR, t.unitId());
        assertThat(post(s, "preview", body(modality(o, 10), modality(o2, 10)), admin).getStatus()).isEqualTo(403);
        assertThat(post(s, "conclude", body(modality(o, 10), modality(o2, 10)), admin).getStatus()).isEqualTo(403);
        assertThat(nothingWritten()).isZero();
        assertThat(post(s, "preview", b, null).getStatus()).isEqualTo(401);
        assertThat(post(s, "conclude", b, null).getStatus()).isEqualTo(401);
        assertThat(proof(s, pdf(), null).getStatus()).isEqualTo(401);
        ok(post(s, "conclude", b, admin), 201);
    }

    @Test
    @DisplayName("RF-MAT-02 CA6: a conclusão aceita turma fora da faixa etária e acima da capacidade")
    void warningsDoNotBlock() throws Exception {
        var t = tenant();
        var o = offer(t, t.unitId(), 0);
        UUID range = UUID.randomUUID();
        adminJdbc().update("insert into age_range (id, organization_id, name, name_key, min_age, max_age) values (?, ?, 'Faixa Infantil T2', 'faixa infantil t2', 4, 12)", range, t.orgId());
        adminJdbc().update("update training_class set age_range_id = ?, capacity = 1 where id = ?", range, o.klass());
        try {
            var first = adult(t);
            ok(post(first, "conclude", body(modality(o, 10)), coordinator(t)), 201);
            ok(post(adult(t), "conclude", body(modality(o, 10)), coordinator(t)), 201); // adulto na turma infantil, já cheia
            assertThat(count("select count(*) from enrollment_modality where primary_class_id = ?", o.klass())).isEqualTo(2);
        } finally {
            adminJdbc().update("update training_class set age_range_id = null where id = ?", o.klass());
            adminJdbc().update("delete from age_range where id = ?", range);
        }
    }

    private ObjectNode benefit(ObjectNode modality, String type, int value) {
        modality.putObject("benefit").put("type", type).put("value", value).put("reason", "Bolsa").put("decidedBy", "Mestre");
        return modality;
    }

    @Test
    @DisplayName("CA18, CA19, CA23: a conclusão cria a mensalidade cheia e a taxa, devolve charges e audita sem dados pessoais; isenção e bolsa de 100% não cobram")
    void firstCharges() throws Exception {
        var t = tenant();
        var o = offer(t, t.unitId(), 0);
        var o2 = offer(t, t.unitId(), 1);
        var c = coordinator(t);
        adminJdbc().update("update unit_settings set enrollment_fee_enabled = true, enrollment_fee_cents = 5000 where unit_id = ?", t.unitId());
        var s = adult(t);
        var r = ok(post(s, "conclude", body(benefit(modality(o, 10), "PERCENT", 25), modality(o2, 10)), c), 201);
        LocalDate today = today(t.unitId());
        assertThat(r.get("charges")).hasSize(3);
        assertThat(r.at("/charges/0/type").asText()).isEqualTo("MONTHLY");
        assertThat(r.at("/charges/0/amountCents").asLong()).isEqualTo(15000);
        assertThat(r.at("/charges/0/dueOn").asText()).isEqualTo(today.toString());
        assertThat(r.at("/charges/0/referenceMonth").asText()).isEqualTo(today.withDayOfMonth(1).toString());
        assertThat(r.at("/charges/0/toleranceDays").asInt()).isEqualTo(5);
        assertThat(r.at("/charges/1/amountCents").asLong()).isEqualTo(20000);
        assertThat(r.at("/charges/2/type").asText()).isEqualTo("ENROLLMENT_FEE");
        assertThat(r.at("/charges/2/amountCents").asLong()).isEqualTo(5000);
        assertThat(count("select count(*) from charge where student_id = ? and status = 'OPEN' and origin = 'ENROLLMENT'", s)).isEqualTo(3);
        assertThat(count("select count(*) from charge c join enrollment_start e on e.id = c.enrollment_start_id where c.type = 'ENROLLMENT_FEE'")).isEqualTo(1);
        assertThat(audits(t, "charge.created")).isEqualTo(3);
        assertThat(count("select count(*) from audit_log where organization_id = ? and action = 'charge.created' and (after_data::text like '%cpf%' or after_data::text like '%@%')",
                t.orgId())).isZero();

        // isenção: nenhuma taxa; bolsa de 100%: nenhuma mensalidade
        var exempt = body(benefit(modality(o, 10), "PERCENT", 100));
        unitAsk(exempt, t.unitId()).put("feeExempt", true).put("feeExemptReason", "bolsa da turma");
        var r2 = ok(post(adult(t), "conclude", exempt, c), 201);
        assertThat(r2.get("charges")).isEmpty();
    }

    @Test
    @DisplayName("CA23: erro ao criar a taxa desfaz a matrícula e a mensalidade")
    void feeFailureUndoesEverything() throws Exception {
        var t = tenant();
        var o = offer(t, t.unitId(), 0);
        adminJdbc().update("update unit_settings set enrollment_fee_enabled = true, enrollment_fee_cents = 5000 where unit_id = ?", t.unitId());
        adminJdbc().execute("create function test_fee_fails() returns trigger language plpgsql as 'begin raise exception ''taxa forçada''; end'");
        adminJdbc().execute("create trigger test_fee_fails before insert on charge for each row when (new.type = 'ENROLLMENT_FEE') execute function test_fee_fails()");
        try {
            assertThat(post(adult(t), "conclude", body(modality(o, 10)), coordinator(t)).getStatus()).isEqualTo(500);
            assertThat(nothingWritten()).isZero();
        } finally {
            adminJdbc().execute("drop trigger test_fee_fails on charge");
            adminJdbc().execute("drop function test_fee_fails()");
        }
    }

    @Test
    @DisplayName("CA21: taxa já cobrada (qualquer situação) ou isenta não se repete; ex-aluno paga de novo; taxa ligada depois cobra de quem só teve modalidade antes")
    void feeHandled() throws Exception {
        var t = tenant();
        var o = offer(t, t.unitId(), 0);
        var o2 = offer(t, t.unitId(), 1);
        var o3 = offer(t, t.unitId(), 2);
        var c = coordinator(t);
        var s = adult(t);
        ok(post(s, "conclude", body(modality(o, 10)), c), 201); // unidade sem taxa: nada tratado
        adminJdbc().update("update unit_settings set enrollment_fee_enabled = true, enrollment_fee_cents = 5000 where unit_id = ?", t.unitId());
        assertThat(ok(post(s, "preview", body(modality(o2, 10)), c), 200).at("/units/0/feeCents").asLong()).isEqualTo(5000);
        var r = ok(post(s, "conclude", body(modality(o2, 10)), c), 201);
        assertThat(r.get("charges")).hasSize(2); // mensalidade e taxa
        adminJdbc().update("update charge set status = 'CANCELED' where type = 'ENROLLMENT_FEE'");
        assertThat(ok(post(s, "preview", body(modality(o3, 10)), c), 200).at("/units/0/feeCents").asLong()).isZero(); // cancelada também conta
        adminJdbc().update("update enrollment_modality set status = 'ENDED', ended_at = now()");
        assertThat(ok(post(s, "preview", body(modality(o3, 10)), c), 200).at("/units/0/feeCents").asLong()).isEqualTo(5000); // retorno
    }

    @Test
    @DisplayName("CA22: openDebt soma as cobranças em aberto do aluno nas unidades do pedido e só elas")
    void openDebt() throws Exception {
        var t = tenant();
        var u2 = otherUnit(t);
        var o = offer(t, t.unitId(), 0);
        var c = coordinator(t);
        var s = adult(t);
        assertThat(ok(post(s, "preview", body(modality(o, 10)), c), 200).at("/openDebt/count").asInt()).isZero();
        var row = EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), s, t.unitId(), "ENDED", 3, null).row();
        var other = EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), s, u2, "ENDED", 4, null).row();
        var billing = new Object[][] {{t.unitId(), row, "2026-01-01", 20000, "OPEN"}, {t.unitId(), row, "2026-02-01", 5000, "OPEN"},
                {t.unitId(), row, "2026-03-01", 9900, "PAID"}, {t.unitId(), row, "2026-04-01", 9900, "CANCELED"}, {u2, other, "2026-01-01", 7000, "OPEN"}};
        for (Object[] b : billing) {
            br.com.ctcore.billing.BillingFixtures.charge(adminJdbc(), t.orgId(), (UUID) b[0], s, (UUID) b[1], LocalDate.parse((String) b[2]), null, ((Integer) b[3]).longValue(),
                    (String) b[4]);
        }
        var debt = ok(post(s, "preview", body(modality(o, 10)), c), 200).get("openDebt");
        assertThat(debt.get("count").asInt()).isEqualTo(2);
        assertThat(debt.get("totalCents").asLong()).isEqualTo(25000);
        assertThat(ok(post(s, "preview", body(modality(o, 10)), as(t, Role.ADMINISTRATOR, t.unitId())), 200).at("/openDebt/count").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("T1: as tabelas novas têm RLS forçada e o papel de execução não apaga linhas delas")
    void isolation() {
        for (String table : new String[] {"student_graduation", "enrollment_start"}) {
            assertThat(adminJdbc().queryForObject("select relrowsecurity and relforcerowsecurity from pg_class where relname = ? and relnamespace = 'public'::regnamespace", Boolean.class, table)).isTrue();
            assertThat(count("select count(*) from information_schema.role_table_grants where table_name = ? and privilege_type = 'DELETE' and grantee <> current_user",
                    table)).isZero();
        }
    }
}

package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RN-MAT-13 (D5): confirmação de modalidade nova no portal, com plano e valor, isolamento entre contas e organizações, termo pendente e auditoria. */
class ModalityConfirmationsIT extends PortalTermBase {
    private MockHttpServletResponse confirm(br.com.ctcore.identity.AccountPrincipal who, UUID row) throws Exception {
        return call(MockMvcRequestBuilders.post("/api/v1/portal/modality-confirmations/" + row), who).getResponse();
    }

    private int confirmations() {
        return adminJdbc().queryForObject("select count(*) from modality_confirmation", Integer.class);
    }

    @Test
    @DisplayName("CA17, CA18, CA20: lista só a modalidade ativa criada depois do aceite e ainda não confirmada; confirmar grava plano e valor, não altera nada e repetir não grava de novo")
    void listAndConfirm() throws Exception {
        Tenant t = tenant();
        publish(t, "ENROLLMENT", 1, true);
        UUID acc = account("aluno@portal.example");
        UUID s = enrolledAdult(t, "Aluno Um", acc); // modalidade criada antes do aceite
        var who = portal(t, acc, Role.STUDENT);
        assertThat(post(who, s, "{\"version\":1}").getStatus()).isEqualTo(201);
        assertThat(body(get(who, "/modality-confirmations"))).isEmpty();

        var added = EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), s, t.unitId(), "ACTIVE", 1, null);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), s, t.unitId(), "ENDED", 2, null);
        var list = body(get(who, "/modality-confirmations"));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("modalityRowId").asText()).isEqualTo(added.row().toString());
        assertThat(list.get(0).get("studentId").asText()).isEqualTo(s.toString());
        assertThat(list.get(0).get("planValueCents").asLong()).isEqualTo(15000);
        assertThat(list.get(0).get("planId").asText()).isEqualTo(added.plan().toString());
        assertThat(list.get(0).has("unit")).isTrue();

        String before = adminJdbc().queryForObject("select plan_id::text || status from enrollment_modality where id = ?", String.class, added.row());
        var first = confirm(who, added.row());
        assertThat(first.getStatus()).isEqualTo(201);
        assertThat(body(first).get("planValueCents").asLong()).isEqualTo(15000);
        assertThat(adminJdbc().queryForObject("select plan_value_cents from modality_confirmation where enrollment_modality_id = ?", Long.class, added.row())).isEqualTo(15000);
        assertThat(adminJdbc().queryForObject("select ip from modality_confirmation", String.class)).isNotBlank();
        assertThat(adminJdbc().queryForObject("select plan_id::text || status from enrollment_modality where id = ?", String.class, added.row())).isEqualTo(before);
        assertThat(audits("modality.confirmed", s)).isEqualTo(1);
        assertThat(body(get(who, "/modality-confirmations"))).isEmpty();

        assertThat(confirm(who, added.row()).getStatus()).isEqualTo(200);
        assertThat(confirmations()).isEqualTo(1);
        assertThat(audits("modality.confirmed", s)).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from charge", Integer.class)).isZero();
    }

    @Test
    @DisplayName("CA19: modalidade de aluno de outra conta ou organização, 404; de aluno que a conta não aceita, 403; modalidade já anterior ao aceite, 404; com termo pendente, 403 TERM_PENDING")
    void isolation() throws Exception {
        Tenant a = tenant(), b = tenant();
        publish(a, "ENROLLMENT", 1, true);
        UUID mine = account("minha@portal.example"), theirs = account("alheia@portal.example"), fin = account("financeiro@portal.example"), other = account("outro@portal.example");
        UUID own = enrolledAdult(a, "Meu Aluno", mine), foreign = enrolledAdult(a, "Aluno Alheio", theirs), inB = enrolledAdult(b, "Aluno da B", account("b@portal.example"));
        UUID kid = student(a, "Filho", "2015-01-01", null);
        EnrollmentFixtures.enroll(adminJdbc(), a.orgId(), kid, a.unitId(), "ACTIVE", 0, null);
        guardian(a, fin, "52998224725", true, kid);
        guardian(a, other, "11144477735", false, kid);
        var me = portal(a, mine, Role.STUDENT);
        var financial = portal(a, fin, Role.GUARDIAN);
        var notFinancial = portal(a, other, Role.GUARDIAN);
        UUID oldRow = adminJdbc().queryForObject("select id from enrollment_modality where student_id = ?", UUID.class, own);

        var pending = confirm(me, oldRow);
        assertThat(pending.getStatus()).isEqualTo(403);
        assertThat(body(pending).get("code").asText()).isEqualTo("TERM_PENDING");
        assertThat(get(me, "/modality-confirmations").getStatus()).isEqualTo(403);

        assertThat(post(me, own, "{\"version\":1}").getStatus()).isEqualTo(201);
        assertThat(post(financial, kid, "{\"version\":1}").getStatus()).isEqualTo(201);
        assertThat(confirm(me, oldRow).getStatus()).isEqualTo(404);
        UUID foreignRow = EnrollmentFixtures.enroll(adminJdbc(), a.orgId(), foreign, a.unitId(), "ACTIVE", 1, null).row();
        UUID rowInB = EnrollmentFixtures.enroll(adminJdbc(), b.orgId(), inB, b.unitId(), "ACTIVE", 1, null).row();
        UUID kidRow = EnrollmentFixtures.enroll(adminJdbc(), a.orgId(), kid, a.unitId(), "ACTIVE", 1, null).row();
        assertThat(confirm(me, foreignRow).getStatus()).isEqualTo(404);
        assertThat(confirm(me, rowInB).getStatus()).isEqualTo(404);
        assertThat(confirm(me, UUID.randomUUID()).getStatus()).isEqualTo(404);
        assertThat(confirm(notFinancial, kidRow).getStatus()).isEqualTo(403);
        assertThat(body(get(notFinancial, "/modality-confirmations"))).isEmpty();
        assertThat(body(get(me, "/modality-confirmations"))).isEmpty();
        assertThat(confirmations()).isZero();
        assertThat(confirm(financial, kidRow).getStatus()).isEqualTo(201);
        assertThat(confirmations()).isEqualTo(1);
    }

    @Test
    @DisplayName("CA14: gestão recebe 403 e sem sessão 401 nas duas rotas")
    void profiles() throws Exception {
        Tenant t = tenant();
        for (Role role : new Role[] {Role.COORDINATOR, Role.ADMINISTRATOR, Role.TEACHER}) {
            assertThat(get(who(t, role), "/modality-confirmations").getStatus()).as(role.name()).isEqualTo(403);
            assertThat(confirm(who(t, role), UUID.randomUUID()).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(get(null, "/modality-confirmations").getStatus()).isEqualTo(401);
        assertThat(confirm(null, UUID.randomUUID()).getStatus()).isEqualTo(401);
    }
}

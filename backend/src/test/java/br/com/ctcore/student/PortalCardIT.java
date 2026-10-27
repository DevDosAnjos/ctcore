package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RF-POR-03, RN-POR-01, RN-POR-04, RN-POR-09, RN-INA-08: carteirinha do portal (selo, modalidades, faixa, fuso, acesso), com banco real. */
class PortalCardIT extends PortalTermBase {
    @AfterEach
    void cleanCard() {
        adminJdbc().update("delete from student_graduation");
        adminJdbc().update("delete from graduation_level");
    }

    private EnrollmentFixtures.Enrolled enroll(Tenant t, UUID student, String status, int kind) {
        var e = EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), student, t.unitId(), status, kind, null);
        UUID by = adminJdbc().queryForList("select id from account where email = 'autor@portal.example'", UUID.class).stream().findFirst().orElseGet(() -> account("autor@portal.example"));
        adminJdbc().update("insert into term_acceptance (id, organization_id, student_id, kind, event, version, account_id, ip) values (?, ?, ?, 'ENROLLMENT', 'ACCEPTED', 1, ?, '127.0.0.1') on conflict do nothing",
                UUID.randomUUID(), t.orgId(), student, by);
        return e;
    }

    private JsonNode card(Tenant t, UUID account, Role role, String query) throws Exception {
        var r = get(portal(t, account, role), "/card" + query);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return body(r);
    }

    private UUID level(Tenant t, UUID modality, String name) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into graduation_level (id, organization_id, modality_id, position, name, color, secondary_color, max_degree, kids) values (?, ?, ?, 0, ?, '#112233', '#445566', 4, false)",
                id, t.orgId(), modality, name);
        return id;
    }

    @Test
    @DisplayName("CA1, CA2, CA7: duas modalidades ativas, uma com faixa e outra sem; selo ACTIVE; marca e aluno; nada de presença, CPF, telefone nem e-mail")
    void active() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        var jiu = enroll(t, s, "ACTIVE", 0);
        var judo = enroll(t, s, "ACTIVE", 1);
        UUID jm = adminJdbc().queryForObject("select modality_id from enrollment_modality where id = ?", UUID.class, jiu.row());
        UUID dm = adminJdbc().queryForObject("select modality_id from enrollment_modality where id = ?", UUID.class, judo.row());
        UUID blue = level(t, jm, "Azul");
        adminJdbc().update("insert into student_graduation (id, organization_id, student_id, modality_id, level_id, degree, achieved_on, origin, has_proof, registered_by_account_id) "
                + "values (?, ?, ?, ?, ?, 2, now()::date, false, false, ?)", UUID.randomUUID(), t.orgId(), s, jm, blue, acc);

        JsonNode c = card(t, acc, Role.STUDENT, "");
        assertThat(c.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(c.get("student").get("name").asText()).isEqualTo("Aluno Um");
        assertThat(c.get("organization").get("name").asText()).isNotBlank();
        assertThat(c.get("modalities")).hasSize(2);
        for (JsonNode m : c.get("modalities")) {
            assertThat(m.get("unit").get("id").asText()).isEqualTo(t.unitId().toString());
            if (m.get("modality").get("id").asText().equals(jm.toString())) {
                assertThat(m.get("belt").get("levelName").asText()).isEqualTo("Azul");
                assertThat(m.get("belt").get("degree").asInt()).isEqualTo(2);
                assertThat(m.get("belt").get("color").asText()).isEqualTo("#112233");
            } else {
                assertThat(m.get("modality").get("id").asText()).isEqualTo(dm.toString());
                assertThat(m.get("belt").isNull()).isTrue();
            }
        }
        String all = get(portal(t, acc, Role.STUDENT), "/card").getContentAsString().toLowerCase();
        assertThat(all).doesNotContain("attendance", "frequency", "presen", "progress", "cpf", "phone", "email");
    }

    @Test
    @DisplayName("CA2, CA6: ex-aluno recebe 200 com INACTIVE e sem modalidades; today é a data do fuso da unidade, não a da JVM")
    void inactiveAndZone() throws Exception {
        Tenant t = tenant();
        UUID acc = account("ex@portal.example");
        UUID s = student(t, "Ex Aluno", "1990-01-01", acc);
        enroll(t, s, "ENDED", 0);
        adminJdbc().update("update unit set timezone = 'Pacific/Kiritimati' where id = ?", t.unitId());

        JsonNode c = card(t, acc, Role.STUDENT, "");
        assertThat(c.get("status").asText()).isEqualTo("INACTIVE");
        assertThat(c.get("modalities")).isEmpty();
        assertThat(c.get("timezone").asText()).isEqualTo("Pacific/Kiritimati");
        LocalDate zoneDate = adminJdbc().queryForObject("select (now() at time zone 'Pacific/Kiritimati')::date", LocalDate.class);
        assertThat(LocalDate.parse(c.get("today").asText())).isEqualTo(zoneDate);
    }

    @Test
    @DisplayName("CA3: responsável com dois filhos vê os dois (e Você), padrão o primeiro cartão; o não pagador também vê")
    void family() throws Exception {
        Tenant t = tenant();
        UUID acc = account("responsavel@portal.example");
        UUID a = student(t, "Filho A", "2015-01-01", null), b = student(t, "Filho B", "2016-01-01", null);
        guardian(t, acc, "52998224725", false, a, b);
        enroll(t, a, "ACTIVE", 0);
        enroll(t, b, "ACTIVE", 1);

        JsonNode first = card(t, acc, Role.GUARDIAN, "");
        assertThat(first.get("students")).hasSize(2);
        assertThat(first.get("student").get("id").asText()).isEqualTo(a.toString());
        assertThat(card(t, acc, Role.GUARDIAN, "?studentId=" + b).get("student").get("id").asText()).isEqualTo(b.toString());

        UUID self = account("treina@portal.example");
        UUID mine = student(t, "Responsável Que Treina", "1985-01-01", self);
        guardian(t, self, "39053344705", true, student(t, "Filho D", "2015-01-01", null));
        enroll(t, mine, "ACTIVE", 0);
        JsonNode both = card(t, self, Role.GUARDIAN, "");
        assertThat(both.get("students").get(0).get("self").asBoolean()).isTrue();
        assertThat(both.get("student").get("id").asText()).isEqualTo(mine.toString());
    }

    @Test
    @DisplayName("CA4, CA5: studentId de outra conta ou organização é 404; gestão 403; sem sessão 401; termo pendente 403")
    void access() throws Exception {
        Tenant a = tenant(), b = tenant();
        UUID x = account("x@portal.example"), y = account("y@portal.example");
        UUID sx = student(a, "Aluno X", "1990-01-01", x), sy = student(a, "Aluno Y", "1990-01-01", y), sb = student(b, "Aluno B", "1990-01-01", account("b@portal.example"));
        enroll(a, sx, "ACTIVE", 0);
        enroll(a, sy, "ACTIVE", 0);
        enroll(b, sb, "ACTIVE", 0);
        var other = get(portal(a, x, Role.STUDENT), "/card?studentId=" + sy);
        var foreign = get(portal(a, x, Role.STUDENT), "/card?studentId=" + sb);
        assertThat(other.getStatus()).isEqualTo(404);
        assertThat(foreign.getStatus()).isEqualTo(404);
        assertThat(other.getContentAsString()).doesNotContain("Aluno Y");
        assertThat(foreign.getContentAsString()).doesNotContain("Aluno B");
        assertThat(card(a, x, Role.STUDENT, "").get("student").get("id").asText()).isEqualTo(sx.toString());

        for (Role role : new Role[] {Role.ADMINISTRATOR, Role.COORDINATOR, Role.TEACHER}) {
            var r = get(portal(a, UUID.randomUUID(), role), "/card");
            assertThat(r.getStatus()).isEqualTo(403);
            assertThat(body(r).get("code").asText()).isEqualTo("FORBIDDEN");
        }
        assertThat(get(null, "/card").getStatus()).isEqualTo(401);

        adminJdbc().update("delete from term_acceptance where student_id = ?", sx);
        publish(a, "ENROLLMENT", 1, true);
        var blocked = get(portal(a, x, Role.STUDENT), "/card");
        assertThat(blocked.getStatus()).isEqualTo(403);
        assertThat(body(blocked).get("code").asText()).isEqualTo("TERM_PENDING");
    }
}

package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-POR-03, RN-POR-01, RN-LGP-03: rotas do aceite do termo, portão do portal e isolamento entre contas, organizações e perfis. */
class PortalTermControllerIT extends PortalTermBase {
    @Test
    @DisplayName("CA3: aluno de outra conta ou de outra organização responde 404 nas três rotas de aluno")
    void notLinked() throws Exception {
        Tenant a = tenant(), b = tenant();
        publish(a, "ENROLLMENT", 1, true);
        UUID mine = account("minha@portal.example"), theirs = account("alheia@portal.example");
        UUID other = enrolledAdult(a, "Aluno Alheio", theirs);
        UUID inB = enrolledAdult(b, "Aluno da B", account("b@portal.example"));
        var who = portal(a, mine, Role.STUDENT);

        for (UUID id : new UUID[] {other, inB}) {
            assertThat(post(who, id, "{\"version\":1}").getStatus()).isEqualTo(404);
            assertThat(get(who, "/students/" + id + "/image-use").getStatus()).isEqualTo(404);
            assertThat(put(who, id, "{\"granted\":false}").getStatus()).isEqualTo(404);
        }
        assertThat(count("true")).isZero();
    }

    @Test
    @DisplayName("CA12, CA15: com termo pendente GET /portal/students responde 403 TERM_PENDING e as rotas do aceite funcionam; depois do aceite, 200")
    void gate() throws Exception {
        Tenant t = tenant();
        publish(t, "ENROLLMENT", 1, true);
        UUID acc = account("aluno@portal.example");
        UUID s = enrolledAdult(t, "Aluno Um", acc);
        var who = portal(t, acc, Role.STUDENT);

        var blocked = get(who, "/students");
        assertThat(blocked.getStatus()).isEqualTo(403);
        assertThat(body(blocked).get("code").asText()).isEqualTo("TERM_PENDING");
        var pending = body(get(who, "/term"));
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).fieldNames()).toIterable().containsExactlyInAnyOrder("id", "name", "term", "imageUse");
        assertThat(pending.get(0).get("name").asText()).isEqualTo("Aluno Um");
        assertThat(pending.get(0).get("term").get("version").asInt()).isEqualTo(1);
        assertThat(pending.get(0).get("term").get("body").asText()).contains("ENROLLMENT");
        assertThat(pending.get(0).get("imageUse").get("granted").asBoolean()).isFalse();
        assertThat(get(who, "/students/" + s + "/image-use").getStatus()).isEqualTo(200);

        assertThat(post(who, s, "{\"version\":1}").getStatus()).isEqualTo(201);
        assertThat(get(who, "/students").getStatus()).isEqualTo(200);
        assertThat(body(get(who, "/term"))).isEmpty();
    }

    @Test
    @DisplayName("CA13: responsável com dois filhos fica bloqueado até aceitar o segundo; o não financeiro do único filho pendente não é bloqueado")
    void guardianGate() throws Exception {
        Tenant t = tenant();
        publish(t, "ENROLLMENT", 1, true);
        UUID fin = account("financeiro@portal.example"), other = account("outro@portal.example");
        UUID a = student(t, "Filho A", "2015-01-01", null), b = student(t, "Filho B", "2016-01-01", null), c = student(t, "Filho C", "2017-01-01", null);
        for (UUID id : new UUID[] {a, b, c}) EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), id, t.unitId(), "ACTIVE", 0, null);
        guardian(t, fin, "52998224725", true, a, b);
        guardian(t, other, "11144477735", false, c);
        var who = portal(t, fin, Role.GUARDIAN);

        assertThat(get(who, "/students").getStatus()).isEqualTo(403);
        assertThat(post(who, a, "{\"version\":1}").getStatus()).isEqualTo(201);
        assertThat(get(who, "/students").getStatus()).isEqualTo(403);
        assertThat(body(get(who, "/term"))).hasSize(1);
        assertThat(post(who, b, "{\"version\":1}").getStatus()).isEqualTo(201);
        assertThat(get(who, "/students").getStatus()).isEqualTo(200);

        assertThat(get(portal(t, other, Role.GUARDIAN), "/students").getStatus()).isEqualTo(200);
        assertThat(body(get(portal(t, other, Role.GUARDIAN), "/term"))).isEmpty();
    }

    @Test
    @DisplayName("CA14: gestão e Professor recebem 403 e sem sessão 401 em todas as rotas novas")
    void profiles() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = enrolledAdult(t, "Aluno Um", acc);
        for (Role role : new Role[] {Role.COORDINATOR, Role.ADMINISTRATOR, Role.TEACHER}) {
            var who = portal(t, acc, role);
            assertThat(get(who, "/term").getStatus()).as(role.name()).isEqualTo(403);
            assertThat(post(who, s, "{\"version\":1}").getStatus()).as(role.name()).isEqualTo(403);
            assertThat(get(who, "/students/" + s + "/image-use").getStatus()).as(role.name()).isEqualTo(403);
            assertThat(put(who, s, "{\"granted\":false}").getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(get(null, "/term").getStatus()).isEqualTo(401);
        assertThat(post(null, s, "{\"version\":1}").getStatus()).isEqualTo(401);
        assertThat(get(null, "/students/" + s + "/image-use").getStatus()).isEqualTo(401);
        assertThat(put(null, s, "{\"granted\":false}").getStatus()).isEqualTo(401);
        assertThat(count("true")).isZero();
    }
}

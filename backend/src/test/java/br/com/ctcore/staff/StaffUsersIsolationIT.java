package br.com.ctcore.staff;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** CA3 / CA17 / CA18: isolamento entre organizações e entre perfis em cada rota de /users, e auditoria sem dado proibido. */
class StaffUsersIsolationIT extends StaffBase {
    private MockHttpServletRequestBuilder[] routes(UUID link, UUID unit) {
        return new MockHttpServletRequestBuilder[] {resend(link), units(link, unit), deactivate(link)};
    }

    @Test
    @DisplayName("CA17: alvo de outra organização responde 404 em resend, units e delete, e nada muda; lista e convite não vazam entre organizações")
    void otherOrganization() throws Exception {
        var a = tenant();
        var b = tenant();
        var target = member(a, Role.ADMINISTRATOR, "Alvo", "PENDING", a.unitId());
        var intruder = coordinator(b);
        member(b, Role.ADMINISTRATOR, "Dele", "ACTIVE", b.unitId());
        for (var request : routes(target.link(), b.unitId())) assertThat(call(request, intruder).getStatus()).isEqualTo(404);
        assertThat(status(target.link())).isEqualTo("PENDING");
        assertThat(unitsOf(target.link())).containsExactly(a.unitId());
        assertThat(names(body(call(list(""), intruder)))).containsExactly("Dele");
        assertThat(call(invite(inviteBody("x-" + b.orgId() + "@synthetic.example", "X", a.unitId())), intruder).getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("CA17: Administrador mexendo em Administrador e Coordenador mexendo em Professor recebem 404 em cada rota, e nada muda")
    void otherProfiles() throws Exception {
        var t = tenant();
        var coordinator = coordinator(t);
        var admin = member(t, Role.ADMINISTRATOR, "Ana Admin", "ACTIVE", t.unitId());
        var otherAdmin = member(t, Role.ADMINISTRATOR, "Outro", "PENDING", t.unitId());
        var teacher = member(t, Role.TEACHER, "Prof", "PENDING", t.unitId());
        for (var request : routes(otherAdmin.link(), t.unitId())) assertThat(call(request, admin).getStatus()).isEqualTo(404);
        for (var request : routes(teacher.link(), t.unitId())) assertThat(call(request, coordinator).getStatus()).isEqualTo(404);
        assertThat(status(otherAdmin.link())).isEqualTo("PENDING");
        assertThat(status(teacher.link())).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("CA3: sem sessão, 401; Professor, Aluno e Responsável, 403 em todas as rotas")
    void anonymousAndOtherProfiles() throws Exception {
        var t = tenant();
        var target = member(t, Role.ADMINISTRATOR, "Alvo", "PENDING", t.unitId());
        var all = new MockHttpServletRequestBuilder[] {list(""), invite(inviteBody("a@synthetic.example", "A", t.unitId())),
                resend(target.link()), units(target.link(), t.unitId()), deactivate(target.link()), appoint(t.unitId())};
        for (var request : all) assertThat(call(request, null).getStatus()).isEqualTo(401);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            var who = member(t, role, "X " + role, "ACTIVE", t.unitId());
            for (var request : new MockHttpServletRequestBuilder[] {list(""), invite(inviteBody("a@synthetic.example", "A", t.unitId())),
                    resend(target.link()), units(target.link(), t.unitId()), deactivate(target.link()), appoint(t.unitId())}) {
                assertThat(call(request, who).getStatus()).as(role.name()).isEqualTo(403);
            }
        }
        assertThat(status(target.link())).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("CA18: cada escrita grava auditoria com o ator e sem e-mail, código, senha nem token")
    void auditOfEveryWrite() throws Exception {
        var t = tenant();
        var b = addUnit(t, "B", true);
        var coordinator = coordinator(t);
        var email = "audit-" + UUID.randomUUID() + "@synthetic.example";
        var link = UUID.fromString(body(call(invite(inviteBody(email, "Auditado", t.unitId())), coordinator)).path("linkId").asText());
        assertThat(call(resend(link), coordinator).getStatus()).isEqualTo(204);
        assertThat(call(units(link, t.unitId(), b), coordinator).getStatus()).isEqualTo(200);
        assertThat(call(appoint(t.unitId()), coordinator).getStatus()).isEqualTo(200);
        assertThat(call(deactivate(link), coordinator).getStatus()).isEqualTo(204);
        for (String action : new String[] {"invite.created", "invite.resent", "link.units_changed", "link.coordinator_appointed", "invite.canceled"}) {
            var rows = audit(t, action);
            assertThat(rows).as(action).isNotEmpty();
            for (var row : rows) {
                assertThat(row.get("actor_id")).isEqualTo(coordinator.account());
                assertThat(row.toString().toLowerCase()).doesNotContain(email.toLowerCase(), "code", "password", "token", "senha");
            }
        }
    }
}

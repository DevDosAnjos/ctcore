package br.com.ctcore.communication;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** RF-COM-01 (#81, CA6, R8): acesso por perfil e isolamento entre organizações nas duas rotas. */
class AnnouncementsRouteIT extends AnnouncementBase {
    @Test
    @DisplayName("CA6: Aluno, Responsável e anônimo não leem nem cancelam (403, 403, 401) e nada muda")
    void profiles() throws Exception {
        Tenant t = tenant();
        AccountPrincipal coordinator = account(t, Role.COORDINATOR);
        UUID id = scheduledToUnit(t, coordinator, "Agendado");
        for (Role role : new Role[] {Role.STUDENT, Role.GUARDIAN}) {
            AccountPrincipal who = account(t, role);
            assertThat(send(HttpMethod.GET, "/announcements", who).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(send(HttpMethod.DELETE, "/announcements/" + id, who).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(send(HttpMethod.GET, "/announcements", null).getStatus()).isEqualTo(401);
        assertThat(send(HttpMethod.DELETE, "/announcements/" + id, null).getStatus()).isEqualTo(401);
        assertThat(count("select count(*) from announcement where id = ?", id)).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where organization_id = ? and action = 'announcement.cancel'", t.orgId())).isZero();
    }

    @Test
    @DisplayName("CA6: gestão da organização B não vê nem cancela o comunicado da A: lista sem as linhas e DELETE com 404; id desconhecido também é 404")
    void organizations() throws Exception {
        Tenant a = tenant(), b = tenant();
        AccountPrincipal coordinatorA = account(a, Role.COORDINATOR), coordinatorB = account(b, Role.COORDINATOR);
        UUID id = scheduledToUnit(a, coordinatorA, "Da A");
        scheduledToUnit(b, coordinatorB, "Da B");
        assertThat(titles("", coordinatorB)).containsExactly("Da B");
        assertThat(titles("?unitId=" + b.unitId(), coordinatorB)).containsExactly("Da B");
        assertThat(send(HttpMethod.GET, "/announcements?unitId=" + a.unitId(), coordinatorB).getStatus()).isEqualTo(404);
        assertThat(send(HttpMethod.DELETE, "/announcements/" + id, coordinatorB).getStatus()).isEqualTo(404);
        assertThat(send(HttpMethod.DELETE, "/announcements/" + UUID.randomUUID(), coordinatorB).getStatus()).isEqualTo(404);
        assertThat(count("select count(*) from announcement where id = ?", id)).isEqualTo(1);
        assertThat(titles("", coordinatorA)).containsExactly("Da A");
    }

    @Test
    @DisplayName("Professor de outra organização e Administrador sem unidade em comum não cancelam; só a gestão com alcance cancela")
    void profilesOnCancel() throws Exception {
        Tenant a = tenant(), b = tenant();
        AccountPrincipal coordinatorA = account(a, Role.COORDINATOR);
        UUID id = scheduledToUnit(a, coordinatorA, "Da A");
        assertThat(send(HttpMethod.DELETE, "/announcements/" + id, account(b, Role.TEACHER)).getStatus()).isEqualTo(404);
        assertThat(send(HttpMethod.DELETE, "/announcements/" + id, account(b, Role.ADMINISTRATOR)).getStatus()).isEqualTo(404);
        assertThat(send(HttpMethod.DELETE, "/announcements/" + id, accountIn(a, Role.ADMINISTRATOR, a.unitId())).getStatus()).isEqualTo(204);
    }
}

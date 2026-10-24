package br.com.ctcore.communication;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** RF-COM-02 (#82, R1, CA1): os destinos que cada perfil pode escolher. */
class AnnouncementTargetsIT extends AnnouncementBase {
    private List<String> names(JsonNode array) {
        List<String> result = new ArrayList<>();
        array.forEach(n -> result.add(n.get("name").asText()));
        return result;
    }

    @Test
    @DisplayName("CA1: Coordenador vê tudo e organization; Administrador só a unidade dele; Professor só as duas turmas (titular e auxiliar), sem unidades nem modalidades")
    void byProfile() throws Exception {
        Tenant t = tenant();
        UUID second = secondUnit(t);
        AccountPrincipal teacherAccount = accountIn(t, Role.TEACHER, t.unitId(), second);
        UUID teacher = teacher(t, teacherAccount), other = teacher(t, null);
        adminJdbc().update("update unit_settings set teacher_permissions = '{false,true,true,true,false}' where unit_id = ?", second);
        klassIn(t, t.unitId(), "A Titular", teacher, "ACTIVE");
        UUID assisted = klassIn(t, t.unitId(), "B Auxiliar", other, "ACTIVE");
        adminJdbc().update("insert into training_class_assistant (organization_id, class_id, teacher_id) values (?, ?, ?)", t.orgId(), assisted, teacher);
        klassIn(t, t.unitId(), "C De outro", other, "ACTIVE");
        klassIn(t, t.unitId(), "D Desativada", teacher, "INACTIVE");
        klassIn(t, second, "E Outra unidade", teacher, "ACTIVE");

        JsonNode coordinator = ok(send(HttpMethod.GET, "/announcements/targets", account(t, Role.COORDINATOR)), 200);
        assertThat(coordinator.get("organization").asBoolean()).isTrue();
        assertThat(names(coordinator.get("units"))).containsExactly("Segunda", "Unit");
        assertThat(names(coordinator.get("classes"))).containsExactly("A Titular", "B Auxiliar", "C De outro", "E Outra unidade");
        assertThat(coordinator.get("modalities")).hasSize(2);
        assertThat(coordinator.get("classes").get(0).has("groupLink")).isTrue();

        JsonNode admin = ok(send(HttpMethod.GET, "/announcements/targets", accountIn(t, Role.ADMINISTRATOR, t.unitId())), 200);
        assertThat(admin.get("organization").asBoolean()).isFalse();
        assertThat(names(admin.get("units"))).containsExactly("Unit");
        assertThat(names(admin.get("classes"))).containsExactly("A Titular", "B Auxiliar", "C De outro");
        assertThat(admin.get("modalities")).hasSize(1);

        JsonNode professor = ok(send(HttpMethod.GET, "/announcements/targets", teacherAccount), 200);
        assertThat(professor.get("organization").asBoolean()).isFalse();
        assertThat(professor.get("units")).isEmpty();
        assertThat(professor.get("modalities")).isEmpty();
        assertThat(names(professor.get("classes"))).containsExactly("A Titular", "B Auxiliar");
    }

    @Test
    @DisplayName("CA1: Professor sem a permissão na unidade não vê a turma dela; sem a permissão em nenhuma unidade, 403; Aluno, Responsável e anônimo: 403 e 401")
    void permissions() throws Exception {
        Tenant t = tenant();
        AccountPrincipal teacherAccount = accountIn(t, Role.TEACHER, t.unitId());
        UUID teacher = teacher(t, teacherAccount);
        adminJdbc().update("update unit_settings set teacher_permissions = '{false,true,true,true,false}' where unit_id = ?", t.unitId());
        klassIn(t, t.unitId(), "Turma", teacher, "ACTIVE");
        assertThat(send(HttpMethod.GET, "/announcements/targets", teacherAccount).getStatus()).isEqualTo(403);
        UUID second = secondUnit(t);
        adminJdbc().update("update unit_settings set teacher_permissions = '{true,true,true,true,false}' where unit_id = ?", second);
        AccountPrincipal both = new AccountPrincipal(teacherAccount.accountId(), teacherAccount.sessionId(), teacherAccount.activeLinkId(), t.orgId(), Role.TEACHER,
                java.util.Set.of(t.unitId(), second));
        assertThat(ok(send(HttpMethod.GET, "/announcements/targets", both), 200).get("classes")).isEmpty();
        for (Role role : new Role[] {Role.STUDENT, Role.GUARDIAN}) {
            assertThat(send(HttpMethod.GET, "/announcements/targets", account(t, role)).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(send(HttpMethod.GET, "/announcements/targets", null).getStatus()).isEqualTo(401);
    }
}

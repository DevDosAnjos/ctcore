package br.com.ctcore.communication;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** RN-COM-04, RN-COM-12 (#83, R8, CA10): o texto inicial de cada tipo vem do servidor, o da organização ou o da plataforma. */
class AnnouncementTemplatesIT extends AnnouncementBase {
    @AfterEach
    void cleanTexts() {
        adminJdbc().update("delete from text_model");
    }

    @Test
    @DisplayName("CA10: o tipo com texto próprio vem com ele e os demais com o da plataforma; outra organização não vê; Professor lê; Aluno 403; anônimo 401")
    void templates() throws Exception {
        Tenant t = tenant();
        Tenant other = tenant();
        AccountPrincipal coordinator = account(t, Role.COORDINATOR);
        adminJdbc().update("insert into text_model (id, organization_id, key, subject, body, updated_by_account_id) values (?, ?, 'ANNOUNCEMENT_GENERAL', 'Meu aviso', 'Meu texto de {unidade}', ?)",
                UUID.randomUUID(), t.orgId(), coordinator.accountId());

        JsonNode r = ok(send(HttpMethod.GET, "/announcements/templates", coordinator), 200).get("templates");
        assertThat(r).hasSize(5);
        assertThat(r.get("GENERAL").get("title").asText()).isEqualTo("Meu aviso");
        assertThat(r.get("GENERAL").get("body").asText()).isEqualTo("Meu texto de {unidade}");
        assertThat(r.get("CANCELED_CLASS").get("title").asText()).isEqualTo("Aula cancelada");
        assertThat(r.get("CANCELED_CLASS").get("body").asText()).contains("{turma}", "{data}", "{horario}");
        assertThat(r.get("NO_CLASS_DAY").get("body").asText()).contains("{periodo}", "{motivo}");

        JsonNode teacher = ok(send(HttpMethod.GET, "/announcements/templates", accountIn(t, Role.TEACHER, t.unitId())), 200);
        assertThat(teacher.get("templates").get("GENERAL").get("title").asText()).isEqualTo("Meu aviso");
        JsonNode foreign = ok(send(HttpMethod.GET, "/announcements/templates", account(other, Role.COORDINATOR)), 200);
        assertThat(foreign.get("templates").get("GENERAL").get("title").asText()).isEqualTo("Aviso geral");
        for (Role role : new Role[] {Role.STUDENT, Role.GUARDIAN}) {
            assertThat(send(HttpMethod.GET, "/announcements/templates", account(t, role)).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(send(HttpMethod.GET, "/announcements/templates", null).getStatus()).isEqualTo(401);
    }
}

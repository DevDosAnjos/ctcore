package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RN-ALU-10: marcar e limpar e-mail com problema, auditoria e envio bloqueado, com banco real. */
class EmailProblemsIT extends OrganizationBase {
    @Autowired ObjectMapper json;
    @Autowired EmailProblems problems;

    @AfterEach
    void clean() {
        adminJdbc().update("delete from email_message where to_email like '%.example'");
        adminJdbc().update("delete from student_guardian");
        adminJdbc().update("delete from guardian");
        adminJdbc().update("delete from student");
        String mine = "(select id from account where email like '%.example')";
        adminJdbc().update("delete from account_invite where account_id in " + mine);
        adminJdbc().update("delete from account_session where account_id in " + mine);
        adminJdbc().update("delete from account_link_unit where link_id in (select id from account_link where account_id in " + mine + ")");
        adminJdbc().update("delete from account_link where account_id in " + mine);
        adminJdbc().update("delete from account where email like '%.example'");
    }

    private JsonNode send(HttpMethod m, String path, JsonNode body, AccountPrincipal who) throws Exception {
        var r = call(MockMvcRequestBuilders.request(m, "/api/v1/students" + path).contentType("application/json").content(body.toString()), who).getResponse();
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(m == HttpMethod.POST && path.isEmpty() ? 201 : 200);
        return json.readTree(r.getContentAsString());
    }

    private JsonNode guardian() {
        return json.createObjectNode().put("name", "Resp Um").put("cpf", "52998224725").put("email", "resp@ficticia.example")
                .put("phone", "11987654321").put("reason", "Ligar");
    }

    @Test
    @DisplayName("CA19: marcar põe emailProblem no aluno e no responsável, audita e bloqueia o e-mail neutro; limpar volta ao normal")
    void marksAndClears() throws Exception {
        Tenant t = tenant();
        AccountPrincipal who = who(t, Role.COORDINATOR);
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", who.accountId(), "gestao@ficticia.example");
        var body = json.createObjectNode().put("registeredUnitId", t.unitId().toString()).put("name", "Aluno Adulto").put("birthDate", "1990-01-01")
                .put("postalCode", "01001000").put("street", "Rua").put("number", "1").put("district", "Centro").put("city", "São Paulo").put("state", "SP")
                .put("cpf", "39053344705").put("email", "aluno@ficticia.example").put("phone", "11912345678");
        String id = send(HttpMethod.POST, "", body, who).get("id").asText();
        send(HttpMethod.POST, "/" + id + "/guardians", guardian(), who);
        String g = adminJdbc().queryForObject("select guardian_id::text from student_guardian where student_id = ?::uuid", String.class, id);

        assertThat(problems.mark(t.orgId(), "ALUNO@ficticia.example")).isEqualTo(1);
        assertThat(problems.mark(t.orgId(), "aluno@ficticia.example")).isZero();
        assertThat(problems.mark(t.orgId(), "resp@ficticia.example")).isEqualTo(1);
        assertThat(problems.mark(tenant().orgId(), "resp@ficticia.example")).isZero();
        JsonNode detail = send(HttpMethod.GET, "/" + id, json.createObjectNode(), who);
        assertThat(detail.get("emailProblem").asBoolean()).isTrue();
        assertThat(detail.get("guardians").get(0).get("emailProblem").asBoolean()).isTrue();
        send(HttpMethod.POST, "/" + id + "/guardians/" + g + "/unlink", json.createObjectNode().put("reason", "Motivo"), who);
        assertThat(adminJdbc().queryForObject("select count(*) from email_message where template = 'GUARDIAN_UNLINKED'", Long.class)).isZero();
        assertThat(adminJdbc().queryForObject("select count(*) from audit_log where organization_id = ? and action = 'student.email_problem_marked'", Long.class, t.orgId()))
                .isEqualTo(2);

        assertThat(problems.clear(t.orgId(), "aluno@ficticia.example")).isEqualTo(1);
        assertThat(problems.clear(t.orgId(), "resp@ficticia.example")).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from audit_log where organization_id = ? and action = 'student.email_problem_cleared'", Long.class, t.orgId()))
                .isEqualTo(2);
        assertThat(send(HttpMethod.GET, "/" + id, json.createObjectNode(), who).get("emailProblem").asBoolean()).isFalse();
        send(HttpMethod.POST, "/" + id + "/guardians", guardian(), who);
        send(HttpMethod.POST, "/" + id + "/guardians/" + g + "/unlink", json.createObjectNode().put("reason", "Outra vez"), who);
        assertThat(adminJdbc().queryForObject("select count(*) from email_message where template = 'GUARDIAN_UNLINKED'", Long.class)).isEqualTo(1);
    }
}

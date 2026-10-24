package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.IdentityException;
import br.com.ctcore.identity.InviteRules.Inviter;
import br.com.ctcore.identity.InviteService;
import br.com.ctcore.identity.Role;
import br.com.ctcore.identity.ScopedAudit;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.mail.EmailService;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * R28, S8, S3: a data de hoje do cadastro, da edição, do convite e do aviso de maioridade é a do fuso da unidade de cadastro, não a do UTC.
 * Organização com duas unidades (Brasília e Tóquio); relógio em 02h30 UTC de 15/06/2026 = 23h30 de 14/06 em Brasília = 11h30 de 15/06 em Tóquio.
 */
class StudentDatesIT extends OrganizationBase {
    static final String BIRTH = "2008-06-15"; // faz 18 anos em 15/06/2026
    @Autowired ObjectMapper json;
    @Autowired JdbcClient jdbc;
    @Autowired OrganizationTransactions transactions;
    @Autowired br.com.ctcore.notification.Notifications notifications;
    @Autowired EmailService emails;
    @Autowired ScopedAudit audit;
    @Autowired InviteService invites;
    @MockitoSpyBean Clock clock;

    Tenant t;
    UUID tokyo;

    @BeforeEach
    void data() {
        t = tenant();
        tokyo = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, timezone) values (?, ?, 'Tokyo', ?, 'Asia/Tokyo')", tokyo, t.orgId(),
                "t" + tokyo.toString().substring(0, 12));
        doReturn(Instant.parse("2026-06-15T02:30:00Z")).when(clock).instant();
    }

    @AfterEach
    void clean() {
        adminJdbc().update("delete from student_guardian");
        adminJdbc().update("delete from guardian");
        adminJdbc().update("delete from student");
        String mine = "(select id from account where email like '%.example')";
        adminJdbc().update("delete from account_invite where account_id in " + mine);
        adminJdbc().update("delete from account_link_unit where link_id in (select id from account_link where account_id in " + mine + ")");
        adminJdbc().update("delete from account_link where account_id in " + mine);
        adminJdbc().update("delete from email_message where to_email like '%.example'");
        adminJdbc().update("delete from account where email like '%.example'");
    }

    private AccountPrincipal coordinator() {
        return who(t, Role.COORDINATOR);
    }

    private ObjectNode body(UUID unit) {
        return json.createObjectNode().put("name", "Aluno Dezoito").put("birthDate", BIRTH).put("postalCode", "01001-000").put("street", "Rua Fictícia")
                .put("number", "10").put("district", "Centro").put("city", "São Paulo").put("state", "SP").put("cpf", "52998224725")
                .put("email", "aluno@ficticia.example").put("phone", "(11) 91234-5678").put("registeredUnitId", unit.toString());
    }

    private MockHttpServletResponse send(HttpMethod method, String path, JsonNode body) throws Exception {
        return call(MockMvcRequestBuilders.request(method, "/api/v1/students" + path).contentType("application/json").content(body.toString()),
                coordinator()).getResponse();
    }

    private UUID student(UUID unit, String name, String birth, String createdAt) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, postal_code, street, number, district, city, state, created_at) "
                + "values (?, ?, ?, ?, ?, ?::date, '01001000', 'Rua', '1', 'Bairro', 'Cidade', 'SP', ?::timestamptz)", id, t.orgId(), unit, name, name.toLowerCase(), birth, createdAt);
        return id;
    }

    @Test
    @DisplayName("CA37: cadastro — às 23h30 de Brasília quem faz 18 anos amanhã ainda é menor (sem responsável: recusado); na unidade de Tóquio já é adulto")
    void registration() throws Exception {
        assertThat(send(HttpMethod.POST, "", body(t.unitId())).getStatus()).isEqualTo(400);
        var r = send(HttpMethod.POST, "", body(tokyo));
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        assertThat(json.readTree(r.getContentAsString()).get("minor").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("CA37: edição — a idade usa o fuso da unidade de cadastro do aluno, não do usuário nem do UTC")
    void edit() throws Exception {
        UUID brasilia = student(t.unitId(), "Aluno Brasilia", "1990-05-20", "2026-01-01T00:00:00Z");
        UUID tokyoStudent = student(tokyo, "Aluno Toquio", "1990-05-20", "2026-01-01T00:00:00Z");
        ObjectNode in = body(t.unitId()).put("name", "Aluno Brasilia");
        in.remove("registeredUnitId");
        assertThat(send(HttpMethod.PUT, "/" + brasilia, in).getStatus()).isEqualTo(400);
        in.put("name", "Aluno Toquio");
        var r = send(HttpMethod.PUT, "/" + tokyoStudent, in);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
    }

    @Test
    @DisplayName("CA37: convite — aluno que faz 18 anos amanhã não recebe conta pela unidade de Brasília, e recebe pela de Tóquio")
    void invite() {
        var by = Inviter.system();
        assertThatThrownBy(() -> invites.invite(by, t.orgId(), "brasilia@ficticia.example", "Aluno", Role.STUDENT, Set.of(t.unitId()), LocalDate.parse(BIRTH)))
                .isInstanceOf(IdentityException.class);
        assertThatCode(() -> invites.invite(by, t.orgId(), "toquio@ficticia.example", "Aluno", Role.STUDENT, Set.of(tokyo), LocalDate.parse(BIRTH)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CA37: aviso de maioridade — só o aluno cadastrado na unidade de Tóquio é avisado às 23h30 de Brasília")
    void majorityNotice() {
        UUID brasilia = student(t.unitId(), "Aluno Brasilia", BIRTH, "2026-01-01T00:00:00Z");
        UUID tokyoStudent = student(tokyo, "Aluno Toquio", BIRTH, "2026-01-01T00:00:00Z");
        var job = new MajorityNoticeJob(transactions, jdbc, emails, audit, clock, notifications);
        assertThat(job.runOnce()).isEqualTo(1);
        assertThat(adminJdbc().queryForList("select id from student where majority_notice_at is not null", UUID.class)).containsExactly(tokyoStudent)
                .doesNotContain(brasilia);
    }
}

package br.com.ctcore.student;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Dados e chamadas comuns dos testes de aceite do termo no portal (#85), com banco real. */
abstract class PortalTermBase extends OrganizationBase {
    static final String TEXT = "Texto do termo de teste, com mais de vinte caracteres.";

    @Autowired ObjectMapper json;

    @AfterEach
    void cleanPortalTerm() {
        adminJdbc().update("delete from modality_confirmation");
        adminJdbc().update("delete from term_acceptance");
        adminJdbc().update("delete from terms_version");
        EnrollmentFixtures.clean(adminJdbc());
        String mine = "(select id from account where email like '%.example')";
        adminJdbc().update("delete from account_invite where account_id in " + mine);
        adminJdbc().update("delete from account_session where account_id in " + mine);
        adminJdbc().update("delete from account_link_unit where link_id in (select id from account_link where account_id in " + mine + ")");
        adminJdbc().update("delete from account_link where account_id in " + mine);
        adminJdbc().update("delete from account where email like '%.example'");
    }

    UUID account(String email) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", id, email);
        return id;
    }

    UUID student(Tenant t, String name, String birth, UUID account) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, postal_code, street, number, district, city, state, account_id) "
                + "values (?, ?, ?, ?, ?, ?::date, '01001000', 'Rua', '1', 'Bairro', 'Cidade', 'SP', ?)", id, t.orgId(), t.unitId(), name, name.toLowerCase(), birth, account);
        return id;
    }

    /** Aluno adulto com conta e matrícula ativa. */
    UUID enrolledAdult(Tenant t, String name, UUID account) {
        UUID id = student(t, name, "1990-01-01", account);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), id, t.unitId(), "ACTIVE", 0, null);
        return id;
    }

    void guardian(Tenant t, UUID account, String cpf, boolean financial, UUID... students) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into guardian (id, organization_id, account_id, name, cpf, phone) values (?, ?, ?, 'Responsável', ?, '11987654321')", id, t.orgId(), account, cpf);
        for (UUID s : students) {
            adminJdbc().update("insert into student_guardian (organization_id, student_id, guardian_id, financial) values (?, ?, ?, ?)", t.orgId(), s, id, financial);
        }
    }

    /** Versão publicada do termo ({@code ENROLLMENT} ou {@code IMAGE_USE}), direto no banco. */
    void publish(Tenant t, String kind, int version, boolean reaccept) {
        UUID by = adminJdbc().queryForList("select id from account limit 1", UUID.class).stream().findFirst().orElseGet(() -> account("publica@portal.example"));
        adminJdbc().update("insert into terms_version (id, organization_id, kind, version, body, reaccept, published_by_account_id) values (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), t.orgId(), kind, version, TEXT + " v" + version + " " + kind, reaccept, by);
    }

    AccountPrincipal portal(Tenant t, UUID account, Role role) {
        return new AccountPrincipal(account, UUID.randomUUID(), UUID.randomUUID(), t.orgId(), role, Set.of());
    }

    MockHttpServletResponse post(AccountPrincipal who, UUID student, String body) throws Exception {
        return call(MockMvcRequestBuilders.post("/api/v1/portal/students/" + student + "/term-acceptance").contentType("application/json").content(body), who).getResponse();
    }

    MockHttpServletResponse get(AccountPrincipal who, String path) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/portal" + path), who).getResponse();
    }

    MockHttpServletResponse put(AccountPrincipal who, UUID student, String body) throws Exception {
        return call(MockMvcRequestBuilders.put("/api/v1/portal/students/" + student + "/image-use").contentType("application/json").content(body), who).getResponse();
    }

    JsonNode body(MockHttpServletResponse response) throws Exception {
        return json.readTree(response.getContentAsString());
    }

    int count(String where, Object... args) {
        return adminJdbc().queryForObject("select count(*) from term_acceptance where " + where, Integer.class, args);
    }

    int audits(String action, UUID student) {
        return adminJdbc().queryForObject("select count(*) from audit_log where action = ? and entity_id = ?", Integer.class, action, student.toString());
    }
}

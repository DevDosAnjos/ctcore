package br.com.ctcore.teacher;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-PRO-04 / RN-PRO-05: inativar professor, com banco real. */
class TeacherDeactivationIT extends OrganizationBase {
    @Autowired ObjectMapper json;
    private final List<String> emails = new ArrayList<>();

    @AfterEach
    void clean() {
        adminJdbc().update("delete from teacher_unit");
        adminJdbc().update("delete from teacher");
        for (String email : emails) {
            adminJdbc().update("delete from account_session where account_id in (select id from account where email = ?)", email);
            adminJdbc().update("delete from account_invite where account_id in (select id from account where email = ?) "
                    + "or sent_by in (select id from account where email = ?)", email, email);
            adminJdbc().update("delete from account_link_unit where link_id in (select l.id from account_link l join account a on a.id = l.account_id where a.email = ?)", email);
            adminJdbc().update("delete from account_link where account_id in (select id from account where email = ?)", email);
            adminJdbc().update("delete from account where email = ?", email);
        }
        emails.clear();
    }

    private AccountPrincipal principal(Tenant t, Role role, Set<UUID> units) {
        UUID account = UUID.randomUUID();
        String email = "ator-" + account + "@synthetic.example";
        emails.add(email);
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Ator', 'ACTIVE')", account, email);
        return new AccountPrincipal(account, UUID.randomUUID(), UUID.randomUUID(), t.orgId(), role, units);
    }

    @Override
    protected AccountPrincipal who(Tenant t, Role role) {
        return principal(t, role, Set.of(t.unitId()));
    }

    private String email() {
        String e = "prof-" + UUID.randomUUID() + "@synthetic.example";
        emails.add(e);
        return e;
    }

    private UUID unit(Tenant t, String name) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active) values (?, ?, ?, ?, true)", id, t.orgId(), name,
                "u" + id.toString().substring(0, 12));
        return id;
    }

    private UUID teacher(Tenant t, String email, String status, UUID... units) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into teacher (id, organization_id, name, name_key, email, phone, status) values (?, ?, 'Ana', ?, ?, '11999990000', ?)",
                id, t.orgId(), Modalities.normalize("Ana"), email, status);
        for (UUID unit : units) {
            adminJdbc().update("insert into teacher_unit (organization_id, teacher_id, unit_id) values (?, ?, ?)", t.orgId(), id, unit);
        }
        return id;
    }

    private MockHttpServletResponse preview(UUID id, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/teachers/" + id + "/deactivation"), who).getResponse();
    }

    private MockHttpServletResponse deactivate(UUID id, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.post("/api/v1/teachers/" + id + "/deactivate"), who).getResponse();
    }

    private MockHttpServletResponse grant(UUID id, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.post("/api/v1/teachers/" + id + "/access"), who).getResponse();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private String status(UUID id) {
        return adminJdbc().queryForObject("select status from teacher where id = ?", String.class, id);
    }

    private String linkStatus(String email) {
        return adminJdbc().queryForObject("select l.status from account_link l join account a on a.id = l.account_id where a.email = ? and l.role = 'TEACHER'",
                String.class, email);
    }

    private List<String> audit(Tenant t, String action) {
        return adminJdbc().queryForList("select coalesce(before_data::text, '') || coalesce(after_data::text, '') from audit_log "
                + "where organization_id = ? and action = ?", String.class, t.orgId(), action);
    }

    @Test
    @DisplayName("CA1, CA2: pré-visualização não altera nada; inativar dá 200 INACTIVE, a lista filtra e repetir dá 409")
    void deactivates() throws Exception {
        var t = tenant();
        UUID id = teacher(t, email(), "ACTIVE", t.unitId());
        var c = who(t, Role.COORDINATOR);
        var p = preview(id, c);
        assertThat(p.getStatus()).isEqualTo(200);
        assertThat(body(p).get("headClasses")).isEmpty();
        assertThat(body(p).get("canDeactivate").asBoolean()).isTrue();
        assertThat(status(id)).isEqualTo("ACTIVE");
        var r = deactivate(id, c);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        assertThat(body(r).get("status").asText()).isEqualTo("INACTIVE");
        assertThat(status(id)).isEqualTo("INACTIVE");
        var list = call(MockMvcRequestBuilders.get("/api/v1/teachers?status=INACTIVE"), c).getResponse();
        assertThat(body(list).get("items")).hasSize(1);
        assertThat(deactivate(id, c).getStatus()).isEqualTo(409);
        assertThat(body(preview(id, c)).get("canDeactivate").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("CA3, CA7: acesso ACTIVE encerra o vínculo e a sessão, conta e account_id ficam; auditoria sem contato e com fim de vínculo")
    void endsActiveLink() throws Exception {
        var t = tenant();
        String email = email();
        UUID id = teacher(t, email, "ACTIVE", t.unitId());
        var c = who(t, Role.COORDINATOR);
        assertThat(grant(id, c).getStatus()).isEqualTo(200);
        UUID account = adminJdbc().queryForObject("select id from account where email = ?", UUID.class, email);
        adminJdbc().update("update account_link set status = 'ACTIVE' where account_id = ?", account);
        UUID link = adminJdbc().queryForObject("select id from account_link where account_id = ?", UUID.class, account);
        adminJdbc().update("insert into account_session (account_id, token_hash, active_link_id, expires_at) values (?, ?, ?, now() + interval '1 hour')",
                account, "h-" + UUID.randomUUID(), link);
        var r = deactivate(id, c);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        assertThat(body(r).get("access").asText()).isEqualTo("ENDED");
        assertThat(linkStatus(email)).isEqualTo("ENDED");
        assertThat(adminJdbc().queryForObject("select count(*) from account_session where account_id = ?", Integer.class, account)).isZero();
        assertThat(adminJdbc().queryForObject("select count(*) from account where id = ?", Integer.class, account)).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select account_id from teacher where id = ?", UUID.class, id)).isEqualTo(account);
        var log = audit(t, "teacher.deactivated");
        assertThat(log).hasSize(1);
        assertThat(log.get(0)).contains("ACTIVE", "INACTIVE").doesNotContain(email).doesNotContain("11999990000");
        assertThat(audit(t, "link.ended")).hasSize(1);
    }

    @Test
    @DisplayName("CA3: acesso PENDING cancela o convite e encerra o vínculo (Administrador); sem conta só muda a situação")
    void endsPendingLink() throws Exception {
        var t = tenant();
        String email = email();
        UUID id = teacher(t, email, "ACTIVE", t.unitId());
        assertThat(grant(id, who(t, Role.COORDINATOR)).getStatus()).isEqualTo(200);
        var r = deactivate(id, who(t, Role.ADMINISTRATOR));
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        assertThat(linkStatus(email)).isEqualTo("ENDED");
        assertThat(adminJdbc().queryForObject("select count(*) from account_invite i join account a on a.id = i.account_id where a.email = ? "
                + "and i.used_at is null and i.canceled_at is null", Integer.class, email)).isZero();
        assertThat(audit(t, "invite.canceled")).hasSize(1);

        UUID coordinatorCase = teacher(t, email(), "ACTIVE", t.unitId());
        String email2 = adminJdbc().queryForObject("select email from teacher where id = ?", String.class, coordinatorCase);
        var c = who(t, Role.COORDINATOR);
        assertThat(grant(coordinatorCase, c).getStatus()).isEqualTo(200);
        assertThat(deactivate(coordinatorCase, c).getStatus()).isEqualTo(200);
        assertThat(linkStatus(email2)).isEqualTo("ENDED");

        UUID bare = teacher(t, null, "ACTIVE", t.unitId());
        assertThat(deactivate(bare, c).getStatus()).isEqualTo(200);
        assertThat(status(bare)).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("CA4: Administrador só inativa quem tem todas as unidades dele; senão canDeactivate false e 403 sem alterar; Coordenador inativa")
    void administratorReach() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul");
        UUID id = teacher(t, email(), "ACTIVE", t.unitId(), second);
        var admin = who(t, Role.ADMINISTRATOR);
        assertThat(body(preview(id, admin)).get("canDeactivate").asBoolean()).isFalse();
        assertThat(deactivate(id, admin).getStatus()).isEqualTo(403);
        assertThat(status(id)).isEqualTo("ACTIVE");
        var both = principal(t, Role.ADMINISTRATOR, Set.of(t.unitId(), second));
        assertThat(body(preview(id, both)).get("canDeactivate").asBoolean()).isTrue();
        assertThat(deactivate(id, both).getStatus()).isEqualTo(200);
        UUID other = teacher(t, email(), "ACTIVE", t.unitId(), second);
        assertThat(deactivate(other, who(t, Role.COORDINATOR)).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("CA5, CA6: Professor, Aluno e Responsável 403; anônimo 401; Administrador sem alcance e outra organização 404; RLS com ctcore_app")
    void permissionsAndIsolation() throws Exception {
        var a = tenant();
        var b = tenant();
        UUID second = unit(a, "Zona Sul");
        UUID id = teacher(a, email(), "ACTIVE", a.unitId());
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(preview(id, who(a, role)).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(deactivate(id, who(a, role)).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(preview(id, null).getStatus()).isEqualTo(401);
        assertThat(deactivate(id, null).getStatus()).isEqualTo(401);
        var far = principal(a, Role.ADMINISTRATOR, Set.of(second));
        assertThat(preview(id, far).getStatus()).isEqualTo(404);
        assertThat(deactivate(id, far).getStatus()).isEqualTo(404);
        var cb = who(b, Role.COORDINATOR);
        assertThat(preview(id, cb).getStatus()).isEqualTo(404);
        assertThat(deactivate(id, cb).getStatus()).isEqualTo(404);
        assertThat(status(id)).isEqualTo("ACTIVE");

        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); var sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '" + b.orgId() + "', true)");
            assertThat(sql.executeUpdate("update teacher set status = 'INACTIVE' where id = '" + id + "'")).isZero();
            app.rollback();
        }
        assertThat(status(id)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("CA8: professor inativo pode ser editado e não recebe convite (409)")
    void inactiveRules() throws Exception {
        var t = tenant();
        UUID id = teacher(t, email(), "ACTIVE", t.unitId());
        var c = who(t, Role.COORDINATOR);
        assertThat(deactivate(id, c).getStatus()).isEqualTo(200);
        assertThat(grant(id, c).getStatus()).isEqualTo(409);
        var put = call(MockMvcRequestBuilders.put("/api/v1/teachers/" + id).contentType("application/json")
                .content("{\"name\":\"Ana Maria\",\"unitIds\":[\"" + t.unitId() + "\"],\"modalities\":[],\"directoryConsent\":false}"), c).getResponse();
        assertThat(put.getStatus()).as(put.getContentAsString()).isEqualTo(200);
        assertThat(status(id)).isEqualTo("INACTIVE");
    }
}

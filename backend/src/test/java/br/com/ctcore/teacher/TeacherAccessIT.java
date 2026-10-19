package br.com.ctcore.teacher;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** RF-PRO-02 / RN-PRO-02: dar acesso e reenviar o convite do professor, com banco real. */
class TeacherAccessIT extends OrganizationBase {
    @Autowired ObjectMapper json;
    private final List<String> emails = new ArrayList<>();

    @AfterEach
    void clean() {
        adminJdbc().update("delete from teacher_unit");
        adminJdbc().update("delete from teacher");
        for (String email : emails) {
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

    private UUID unit(Tenant t, String name, boolean active) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active) values (?, ?, ?, ?, ?)", id, t.orgId(), name,
                "u" + id.toString().substring(0, 12), active);
        return id;
    }

    private UUID teacher(Tenant t, String email, String status, UUID... units) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into teacher (id, organization_id, name, name_key, email, status) values (?, ?, 'Ana', ?, ?, ?)",
                id, t.orgId(), Modalities.normalize("Ana"), email, status);
        for (UUID unit : units) {
            adminJdbc().update("insert into teacher_unit (organization_id, teacher_id, unit_id) values (?, ?, ?)", t.orgId(), id, unit);
        }
        return id;
    }

    private MockHttpServletResponse grant(UUID teacher, String body, AccountPrincipal who) throws Exception {
        var request = MockMvcRequestBuilders.post("/api/v1/teachers/" + teacher + "/access");
        if (body != null) request.contentType("application/json").content(body);
        return call(request, who).getResponse();
    }

    private MockHttpServletResponse resend(UUID teacher, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.post("/api/v1/teachers/" + teacher + "/access/resend"), who).getResponse();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private static String units(UUID... ids) {
        return "{\"unitIds\":[" + String.join(",", java.util.Arrays.stream(ids).map(u -> "\"" + u + "\"").toList()) + "]}";
    }

    private List<String> audit(Tenant t, String action) {
        return adminJdbc().queryForList("select coalesce(after_data::text, '') || coalesce(before_data::text, '') from audit_log "
                + "where organization_id = ? and action = ?", String.class, t.orgId(), action);
    }

    private int pending(Tenant t, String email) {
        return adminJdbc().queryForObject("select count(*) from account_link l join account a on a.id = l.account_id "
                + "where l.organization_id = ? and a.email = ? and l.role = 'TEACHER' and l.status = 'PENDING'", Integer.class, t.orgId(), email);
    }

    @Test
    @DisplayName("CA4, CA5: Coordenador dá acesso (sem unitIds usa as unidades do professor): vínculo PENDING, conta ligada, convite, access PENDING e auditoria sem código")
    void grants() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul", true);
        String email = email();
        UUID id = teacher(t, email, "ACTIVE", t.unitId(), second);
        var r = grant(id, null, who(t, Role.COORDINATOR));
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        assertThat(body(r).get("access").asText()).isEqualTo("PENDING");
        assertThat(pending(t, email)).isEqualTo(1);
        assertThat(adminJdbc().queryForList("select unit_id from account_link_unit u join account_link l on l.id = u.link_id join account a on a.id = l.account_id "
                + "where a.email = ?", UUID.class, email)).containsExactlyInAnyOrder(t.unitId(), second);
        assertThat(adminJdbc().queryForObject("select account_id from teacher where id = ?", UUID.class, id)).isNotNull();
        assertThat(adminJdbc().queryForObject("select count(*) from account_invite i join account a on a.id = i.account_id where a.email = ? "
                + "and i.used_at is null and i.canceled_at is null", Integer.class, email)).isEqualTo(1);
        var log = audit(t, "teacher.access_granted");
        assertThat(log).hasSize(1);
        assertThat(log.toString()).doesNotContain(email).doesNotContainIgnoringCase("code");
    }

    @Test
    @DisplayName("CA4: e-mail de conta que já é aluno da organização reaproveita a conta; os dois vínculos coexistem")
    void reusesStudentAccount() throws Exception {
        var t = tenant();
        String email = email();
        adminJdbc().update("insert into account (email, name, status) values (?, 'Aluna', 'ACTIVE')", email);
        adminJdbc().update("insert into account_link (account_id, organization_id, role, status) select id, ?, 'STUDENT', 'ACTIVE' from account where email = ?",
                t.orgId(), email);
        UUID id = teacher(t, email, "ACTIVE", t.unitId());
        assertThat(grant(id, null, who(t, Role.COORDINATOR)).getStatus()).isEqualTo(200);
        assertThat(adminJdbc().queryForObject("select count(*) from account where email = ?", Integer.class, email)).isEqualTo(1);
        assertThat(adminJdbc().queryForList("select l.role from account_link l join account a on a.id = l.account_id where a.email = ?", String.class, email))
                .containsExactlyInAnyOrder("STUDENT", "TEACHER");
    }

    @Test
    @DisplayName("CA5: Administrador marca só unidade dele; unidade fora do alcance 403; fora do professor ou inativa 400; sem unitIds usa as dele; nada é criado nas recusas")
    void units() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul", true);
        UUID closed = unit(t, "Fechada", false);
        UUID elsewhere = unit(t, "Sem professor", true);
        String email = email();
        UUID id = teacher(t, email, "ACTIVE", t.unitId(), second, closed);
        var admin = who(t, Role.ADMINISTRATOR);
        assertThat(grant(id, units(second), admin).getStatus()).isEqualTo(403);
        var coordinator = who(t, Role.COORDINATOR);
        assertThat(grant(id, units(elsewhere), coordinator).getStatus()).isEqualTo(400);
        assertThat(grant(id, units(closed), coordinator).getStatus()).isEqualTo(400);
        assertThat(pending(t, email)).isZero();
        var ok = grant(id, null, admin);
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(adminJdbc().queryForList("select unit_id from account_link_unit u join account_link l on l.id = u.link_id join account a on a.id = l.account_id "
                + "where a.email = ?", UUID.class, email)).containsExactly(t.unitId());
    }

    @Test
    @DisplayName("CA6: sem e-mail 400; inativo 409; acesso PENDING ou ACTIVE 409; conta ligada a outro cadastro 409 ALREADY_LINKED; vínculo de Professor de #37 não é ligado")
    void refusals() throws Exception {
        var t = tenant();
        var c = who(t, Role.COORDINATOR);
        assertThat(grant(teacher(t, null, "ACTIVE", t.unitId()), null, c).getStatus()).isEqualTo(400);
        assertThat(grant(teacher(t, email(), "INACTIVE", t.unitId()), null, c).getStatus()).isEqualTo(409);

        String taken = email();
        UUID first = teacher(t, taken, "ACTIVE", t.unitId());
        assertThat(grant(first, null, c).getStatus()).isEqualTo(200);
        assertThat(grant(first, null, c).getStatus()).isEqualTo(409);
        adminJdbc().update("update account_link set status = 'ACTIVE' where account_id = (select id from account where email = ?)", taken);
        assertThat(grant(first, null, c).getStatus()).isEqualTo(409);
        var other = grant(teacher(t, taken, "ACTIVE", t.unitId()), null, c);
        assertThat(other.getStatus()).isEqualTo(409);
        assertThat(body(other).get("code").asText()).isEqualTo("ALREADY_LINKED");

        String staff = email();
        adminJdbc().update("insert into account (email, name, status) values (?, 'Equipe', 'ACTIVE')", staff);
        adminJdbc().update("insert into account_link (account_id, organization_id, role, status) select id, ?, 'TEACHER', 'ACTIVE' from account where email = ?",
                t.orgId(), staff);
        var linked = grant(teacher(t, staff, "ACTIVE", t.unitId()), null, c);
        assertThat(linked.getStatus()).isEqualTo(409);
        assertThat(body(linked).get("code").asText()).isEqualTo("ALREADY_LINKED");
    }

    @Test
    @DisplayName("CA6: organização no limite de usuários de gestão recebe STAFF_LIMIT_REACHED e nada é criado")
    void limit() throws Exception {
        var t = tenant();
        adminJdbc().update("with a as (insert into account (id, email, name, status) select gen_random_uuid(), 'f' || g || '-' || ?::text || '@synthetic.example', "
                + "'F' || g, 'ACTIVE' from generate_series(1, 200) g returning id) "
                + "insert into account_link (account_id, organization_id, role, status) select id, ?, 'TEACHER', 'ACTIVE' from a", t.orgId().toString(), t.orgId());
        String email = email();
        UUID id = teacher(t, email, "ACTIVE", t.unitId());
        var r = grant(id, null, who(t, Role.COORDINATOR));
        assertThat(r.getStatus()).isEqualTo(409);
        assertThat(body(r).get("code").asText()).isEqualTo("STAFF_LIMIT_REACHED");
        assertThat(pending(t, email)).isZero();
        adminJdbc().update("delete from account_link where organization_id = ?", t.orgId());
        adminJdbc().update("delete from account where email like ?", "f%-" + t.orgId() + "@synthetic.example");
    }

    @Test
    @DisplayName("CA7: reenviar com PENDING invalida o convite antigo e audita; NONE e ACTIVE 409; Administrador sem alcance 404")
    void resends() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul", true);
        String email = email();
        UUID id = teacher(t, email, "ACTIVE", t.unitId());
        var c = who(t, Role.COORDINATOR);
        assertThat(resend(id, c).getStatus()).isEqualTo(409);
        assertThat(grant(id, null, c).getStatus()).isEqualTo(200);
        assertThat(resend(id, c).getStatus()).isEqualTo(204);
        assertThat(adminJdbc().queryForObject("select count(*) from account_invite i join account a on a.id = i.account_id where a.email = ? "
                + "and i.canceled_at is null", Integer.class, email)).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from account_invite i join account a on a.id = i.account_id where a.email = ? "
                + "and i.canceled_at is not null", Integer.class, email)).isEqualTo(1);
        assertThat(audit(t, "teacher.invite_resent")).hasSize(1);
        var farAdmin = principal(t, Role.ADMINISTRATOR, Set.of(second));
        assertThat(resend(id, farAdmin).getStatus()).isEqualTo(404);
        adminJdbc().update("update account_link set status = 'ACTIVE' where account_id = (select id from account where email = ?)", email);
        assertThat(resend(id, c).getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("CA2, CA6: Professor, Aluno e Responsável 403; anônimo 401; outra organização e Administrador sem alcance 404, sem criar nada")
    void permissionsAndIsolation() throws Exception {
        var a = tenant();
        var b = tenant();
        UUID second = unit(a, "Zona Sul", true);
        String email = email();
        UUID id = teacher(a, email, "ACTIVE", a.unitId());
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(grant(id, null, who(a, role)).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(resend(id, who(a, role)).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(grant(id, null, null).getStatus()).isEqualTo(401);
        assertThat(resend(id, null).getStatus()).isEqualTo(401);
        assertThat(grant(id, null, who(b, Role.COORDINATOR)).getStatus()).isEqualTo(404);
        assertThat(resend(id, who(b, Role.COORDINATOR)).getStatus()).isEqualTo(404);
        var far = principal(a, Role.ADMINISTRATOR, Set.of(second));
        assertThat(grant(id, null, far).getStatus()).isEqualTo(404);
        assertThat(pending(a, email)).isZero();
        assertThat(adminJdbc().queryForObject("select account_id from teacher where id = ?", UUID.class, id)).isNull();
    }
}

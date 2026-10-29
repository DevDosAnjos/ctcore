package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RF-MAT-04: lista da gestão, Aprovar e Recusar, alcance por unidade, perfis, isolamento, e-mail ao aluno e portão do termo, com banco real. */
class PortalRequestControllerIT extends PortalRequestBase {
    private record World(Tenant t, UUID unitB, UUID acc, UUID student, Setup m, AccountPrincipal who, String openId) {}

    private World world() throws Exception {
        Tenant t = tenant();
        UUID unitB = secondUnit(t);
        UUID acc = account("aluno-" + UUID.randomUUID() + "@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        Setup m = setup(t, s, 0);
        accepted(t, s, acc);
        var who = portal(t, acc, Role.STUDENT);
        String id = body(request(who, s, classChange(m))).get("id").asText();
        return new World(t, unitB, acc, s, m, who, id);
    }

    @Test
    @DisplayName("CA6: Administrador vê só as unidades do seu alcance, Coordenador todas; filtros status, kind e unitId; Professor e portal 403; outra organização nunca aparece (404)")
    void listing() throws Exception {
        World w = world();
        UUID accB = account("aluno-b@portal.example");
        UUID sb = student(w.t(), "Aluno B", "1990-01-01", accB);
        Setup mb = setup(w.t(), sb, w.unitB(), 1);
        accepted(w.t(), sb, accB);
        var whoB = portal(w.t(), accB, Role.STUDENT);
        assertThat(request(whoB, sb, planChange(mb)).getStatus()).isEqualTo(201);
        assertThat(request(whoB, sb, closure(mb, "OTHER")).getStatus()).isEqualTo(201);

        var adminA = who(w.t(), Role.ADMINISTRATOR);
        var adminB = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), w.t().orgId(), Role.ADMINISTRATOR, java.util.Set.of(w.unitB()));
        var coordinator = who(w.t(), Role.COORDINATOR);

        var forA = body(manage("GET", "", adminA, null));
        assertThat(forA.get("items")).hasSize(1);
        assertThat(forA.get("items").get(0).get("kind").asText()).isEqualTo("CLASS_CHANGE");
        assertThat(forA.get("items").get(0).get("studentName").asText()).isEqualTo("Aluno Um");
        assertThat(forA.get("total").asInt()).isEqualTo(1);
        assertThat(body(manage("GET", "", adminB, null)).get("items")).hasSize(2);
        var all = body(manage("GET", "", coordinator, null));
        assertThat(all.get("items")).hasSize(3);
        assertThat(all.get("items").findValuesAsText("id").get(0)).isEqualTo(w.openId());
        assertThat(body(manage("GET", "?kind=CLOSURE", coordinator, null)).get("items")).hasSize(1);
        assertThat(body(manage("GET", "?unitId=" + w.unitB(), coordinator, null)).get("items")).hasSize(2);
        assertThat(body(manage("GET", "?unitId=" + w.unitB(), adminA, null)).get("items")).isEmpty();
        assertThat(body(manage("GET", "?status=REFUSED", coordinator, null)).get("items")).isEmpty();
        assertThat(manage("GET", "?status=XX", coordinator, null).getStatus()).isEqualTo(400);
        assertThat(manage("GET", "?kind=XX", coordinator, null).getStatus()).isEqualTo(400);

        assertThat(manage("GET", "", who(w.t(), Role.TEACHER), null).getStatus()).isEqualTo(403);
        assertThat(manage("GET", "/" + w.openId(), who(w.t(), Role.TEACHER), null).getStatus()).isEqualTo(403);
        assertThat(manage("GET", "/" + w.openId(), adminA, null).getStatus()).isEqualTo(200);
        assertThat(manage("GET", "/" + w.openId(), adminB, null).getStatus()).isEqualTo(404);
        assertThat(manage("GET", "/" + w.openId(), who(tenant(), Role.COORDINATOR), null).getStatus()).isEqualTo(404);
        assertThat(body(manage("GET", "", who(tenant(), Role.COORDINATOR), null)).get("items")).isEmpty();
    }

    @Test
    @DisplayName("CA7, CA15: Aprovar marca o pedido, não altera a matrícula, manda e-mail à conta que pediu e repetir dá 409 REQUEST_NOT_OPEN")
    void approve() throws Exception {
        World w = world();
        var admin = manager(w.t(), Role.ADMINISTRATOR, w.t().unitId());
        String before = adminJdbc().queryForObject("select primary_class_id::text || plan_id::text from enrollment_modality where id = ?", String.class, w.m().row());

        var ok = manage("POST", "/" + w.openId() + "/approve", admin, "{\"message\":\"Pode vir na terça\"}");
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(body(ok).get("status").asText()).isEqualTo("APPROVED");
        assertThat(body(ok).get("response").asText()).isEqualTo("Pode vir na terça");
        assertThat(adminJdbc().queryForObject("select primary_class_id::text || plan_id::text from enrollment_modality where id = ?", String.class, w.m().row())).isEqualTo(before);
        assertThat(adminJdbc().queryForObject("select decided_by_account_id from portal_request where id = ?", UUID.class, UUID.fromString(w.openId()))).isEqualTo(admin.accountId());
        assertThat(auditsOf("portal_request.approved", w.openId())).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from email_message where template = 'PORTAL_REQUEST' and to_email = (select email from account where id = ?)",
                Integer.class, w.acc())).isEqualTo(1);

        var again = manage("POST", "/" + w.openId() + "/approve", admin, null);
        assertThat(again.getStatus()).isEqualTo(409);
        assertThat(body(again).get("code").asText()).isEqualTo("REQUEST_NOT_OPEN");
        assertThat(manage("POST", "/" + w.openId() + "/refuse", admin, "{\"message\":\"Não dá\"}").getStatus()).isEqualTo(409);
        assertThat(auditsOf("portal_request.approved", w.openId())).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from email_message where template = 'PORTAL_REQUEST'", Integer.class)).isEqualTo(1);

        String closure = body(request(w.who(), w.student(), closure(w.m(), "OTHER"))).get("id").asText();
        assertThat(manage("POST", "/" + closure + "/approve", admin, null).getStatus()).isEqualTo(400);
        assertThat(requests("id = ? and status = 'OPEN'", UUID.fromString(closure))).isEqualTo(1);
    }

    @Test
    @DisplayName("CA8: Recusar exige mensagem de 3 a 500 caracteres; com mensagem, Recusado e e-mail com ela; recusar o encerramento libera novo pedido")
    void refuse() throws Exception {
        World w = world();
        var admin = manager(w.t(), Role.ADMINISTRATOR, w.t().unitId());
        assertThat(manage("POST", "/" + w.openId() + "/refuse", admin, null).getStatus()).isEqualTo(400);
        assertThat(manage("POST", "/" + w.openId() + "/refuse", admin, "{\"message\":\"ab\"}").getStatus()).isEqualTo(400);
        assertThat(manage("POST", "/" + w.openId() + "/refuse", admin, "{\"message\":\"" + "x".repeat(501) + "\"}").getStatus()).isEqualTo(400);
        assertThat(requests("id = ? and status = 'OPEN'", UUID.fromString(w.openId()))).isEqualTo(1);
        assertThat(manage("POST", "/" + w.openId() + "/refuse", admin, "{\"message\":\"Turma cheia\"}").getStatus()).isEqualTo(200);
        assertThat(requests("id = ? and status = 'REFUSED'", UUID.fromString(w.openId()))).isEqualTo(1);
        assertThat(auditsOf("portal_request.refused", w.openId())).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from audit_log where action = 'portal_request.refused' and after_data::text like '%Turma cheia%'", Integer.class)).isZero();
        assertThat(adminJdbc().queryForObject("select count(*) from email_message where template = 'PORTAL_REQUEST' and to_email = (select email from account where id = ?)",
                Integer.class, w.acc())).isEqualTo(1);

        String closure = body(request(w.who(), w.student(), closure(w.m(), "OTHER"))).get("id").asText();
        var options = body(get(w.who(), "/students/" + w.student() + "/request-options"));
        assertThat(options.get("modalities").get(0).get("closureRequestedAt").isNull()).isFalse();
        assertThat(manage("POST", "/" + closure + "/refuse", admin, "{\"message\":\"Vamos conversar\"}").getStatus()).isEqualTo(200);
        options = body(get(w.who(), "/students/" + w.student() + "/request-options"));
        assertThat(options.get("modalities").get(0).get("closureRequestedAt").isNull()).isTrue();
        assertThat(request(w.who(), w.student(), closure(w.m(), "FINANCIAL")).getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("CA6, CA17: Administrador fora do alcance não decide (404); gestão nas rotas do portal e portal nas da gestão, 403; sem sessão, 401; termo pendente, 403 TERM_PENDING")
    void profiles() throws Exception {
        World w = world();
        var outsider = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), w.t().orgId(), Role.ADMINISTRATOR, java.util.Set.of(w.unitB()));
        assertThat(manage("POST", "/" + w.openId() + "/approve", outsider, null).getStatus()).isEqualTo(404);
        assertThat(manage("POST", "/" + w.openId() + "/refuse", outsider, "{\"message\":\"Não\"}").getStatus()).isEqualTo(404);
        assertThat(manage("POST", "/" + w.openId() + "/approve", who(w.t(), Role.TEACHER), null).getStatus()).isEqualTo(403);
        assertThat(manage("POST", "/" + w.openId() + "/approve", w.who(), null).getStatus()).isEqualTo(403);
        assertThat(manage("GET", "", w.who(), null).getStatus()).isEqualTo(403);
        assertThat(manage("GET", "", null, null).getStatus()).isEqualTo(401);
        assertThat(requests("id = ? and status = 'OPEN'", UUID.fromString(w.openId()))).isEqualTo(1);

        for (Role role : new Role[] {Role.ADMINISTRATOR, Role.COORDINATOR, Role.TEACHER}) {
            var staff = who(w.t(), role);
            assertThat(request(staff, w.student(), classChange(w.m())).getStatus()).isEqualTo(403);
            assertThat(get(staff, "/students/" + w.student() + "/requests").getStatus()).isEqualTo(403);
            assertThat(get(staff, "/students/" + w.student() + "/request-options").getStatus()).isEqualTo(403);
        }
        assertThat(request(null, w.student(), classChange(w.m())).getStatus()).isEqualTo(401);

        publish(w.t(), "ENROLLMENT", 2, true);
        var pending = request(w.who(), w.student(), planChange(w.m()));
        assertThat(pending.getStatus()).isEqualTo(403);
        assertThat(body(pending).get("code").asText()).isEqualTo("TERM_PENDING");
        assertThat(get(w.who(), "/students/" + w.student() + "/requests").getStatus()).isEqualTo(403);
        assertThat(get(w.who(), "/students/" + w.student() + "/request-options").getStatus()).isEqualTo(403);
    }
}

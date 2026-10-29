package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-MAT-04 (RN-MAT-16): criação do pedido no portal, validação por tipo, quem pode pedir, duplicata e corrida, auditoria e avisos à gestão. */
class PortalRequestsIT extends PortalRequestBase {
    @Test
    @DisplayName("CA1, CA16: troca de turma cria pedido aberto, não altera turma, plano nem cobrança e audita sem o texto livre")
    void classChange() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        Setup m = setup(t, s, 0);
        accepted(t, s, acc);
        var who = portal(t, acc, Role.STUDENT);

        var r = request(who, s, classChange(m));
        assertThat(r.getStatus()).isEqualTo(201);
        var created = body(r);
        assertThat(created.get("status").asText()).isEqualTo("OPEN");
        assertThat(created.get("kind").asText()).isEqualTo("CLASS_CHANGE");
        assertThat(created.get("targetClassId").asText()).isEqualTo(m.otherClass().toString());
        assertThat(adminJdbc().queryForObject("select primary_class_id::text || plan_id::text || coalesce(next_plan_id::text, '') from enrollment_modality where id = ?", String.class, m.row()))
                .isEqualTo(m.klass().toString() + m.plan());
        assertThat(adminJdbc().queryForObject("select count(*) from charge", Integer.class)).isZero();
        assertThat(requests("student_id = ? and status = 'OPEN'", s)).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select ip from portal_request", String.class)).isNotBlank();

        String id = created.get("id").asText();
        assertThat(auditsOf("portal_request.created", id)).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select after_data::text from audit_log where action = 'portal_request.created' and entity_id = ?", String.class, id))
                .contains("CLASS_CHANGE").doesNotContain("horário da noite");
    }

    @Test
    @DisplayName("CA2: planos, nova modalidade e encerramento são aceitos; motivo fora da lista, turma de outra modalidade, plano igual ou modalidade já ativa dão 400 e nada é gravado")
    void kinds() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        Setup m = setup(t, s, 0);
        Setup other = setup(t, student(t, "Outro Aluno", "1990-01-01", null), 1);
        accepted(t, s, acc);
        var who = portal(t, acc, Role.STUDENT);

        assertThat(request(who, s, planChange(m)).getStatus()).isEqualTo(201);
        UUID newModality = EnrollmentFixtures.modality(adminJdbc(), t.orgId(), 2);
        adminJdbc().update("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (?, ?, ?, true) on conflict do nothing", t.orgId(), t.unitId(), newModality);
        var added = request(who, s, "{\"kind\":\"NEW_MODALITY\",\"targetUnitId\":\"" + t.unitId() + "\",\"targetModalityId\":\"" + newModality + "\"}");
        assertThat(added.getStatus()).isEqualTo(201);
        assertThat(body(added).get("targetModalityId").asText()).isEqualTo(newModality.toString());
        assertThat(request(who, s, closure(m, "FINANCIAL")).getStatus()).isEqualTo(201);
        assertThat(requests("student_id = ?", s)).isEqualTo(3);

        int before = requests("true");
        var badReason = request(who, s, closure(m, "ABC"));
        assertThat(badReason.getStatus()).isEqualTo(400);
        assertThat(body(badReason).get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(badReason.getContentAsString()).contains("reason");
        assertThat(request(who, s, "{\"kind\":\"CLOSURE\",\"modalityRowId\":\"" + m.row() + "\"}").getStatus()).isEqualTo(400);
        assertThat(request(who, s, "{\"kind\":\"CLASS_CHANGE\",\"modalityRowId\":\"" + m.row() + "\",\"targetClassId\":\"" + other.otherClass() + "\"}").getStatus()).isEqualTo(400);
        assertThat(request(who, s, "{\"kind\":\"CLASS_CHANGE\",\"modalityRowId\":\"" + m.row() + "\",\"targetClassId\":\"" + m.klass() + "\"}").getStatus()).isEqualTo(400);
        assertThat(request(who, s, "{\"kind\":\"PLAN_CHANGE\",\"modalityRowId\":\"" + m.row() + "\",\"targetPlanId\":\"" + m.plan() + "\"}").getStatus()).isEqualTo(400);
        assertThat(request(who, s, "{\"kind\":\"NEW_MODALITY\",\"targetUnitId\":\"" + t.unitId() + "\",\"targetModalityId\":\"" + m.modality() + "\"}").getStatus()).isEqualTo(400);
        assertThat(request(who, s, "{\"kind\":\"CLOSURE\",\"modalityRowId\":\"" + other.row() + "\",\"reason\":\"OTHER\"}").getStatus()).isEqualTo(400);
        assertThat(request(who, s, "{\"kind\":\"OUTRO\"}").getStatus()).isEqualTo(400);
        assertThat(request(who, s, planChange(m).replace("}", ",\"note\":\"" + "x".repeat(501) + "\"}")).getStatus()).isEqualTo(400);
        assertThat(requests("true")).isEqualTo(before);
    }

    @Test
    @DisplayName("CA3: no menor, o responsável financeiro pede (201), o não financeiro recebe 403 e o aluno de outra conta ou organização 404")
    void whoCanAsk() throws Exception {
        Tenant a = tenant(), b = tenant();
        UUID kid = student(a, "Filho", "2015-01-01", null);
        Setup m = setup(a, kid, 0);
        UUID fin = account("financeiro@portal.example"), other = account("outro@portal.example"), stranger = account("estranho@portal.example");
        guardian(a, fin, "52998224725", true, kid);
        guardian(a, other, "11144477735", false, kid);
        accepted(a, kid, fin);
        accepted(a, kid, other);
        UUID foreign = student(b, "Aluno B", "1990-01-01", stranger);
        Setup mb = setup(b, foreign, 0);
        accepted(b, foreign, stranger);

        assertThat(request(portal(a, fin, Role.GUARDIAN), kid, classChange(m)).getStatus()).isEqualTo(201);
        assertThat(request(portal(a, other, Role.GUARDIAN), kid, planChange(m)).getStatus()).isEqualTo(403);
        assertThat(get(portal(a, other, Role.GUARDIAN), "/students/" + kid + "/requests").getStatus()).isEqualTo(200);
        assertThat(request(portal(a, fin, Role.GUARDIAN), foreign, classChange(mb)).getStatus()).isEqualTo(404);
        assertThat(request(portal(a, stranger, Role.STUDENT), kid, classChange(m)).getStatus()).isEqualTo(404);
        assertThat(request(portal(b, stranger, Role.STUDENT), kid, classChange(m)).getStatus()).isEqualTo(404);
        assertThat(get(portal(a, stranger, Role.GUARDIAN), "/students/" + kid + "/requests").getStatus()).isEqualTo(404);
        assertThat(requests("true")).isEqualTo(1);
    }

    @Test
    @DisplayName("CA4, CA5: pedido igual aberto responde 409 e fica uma só linha (inclusive em paralelo); tipo diferente passa; com encerramento aberto, turma e plano dão 409")
    void duplicates() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        Setup m = setup(t, s, 0);
        accepted(t, s, acc);
        var who = portal(t, acc, Role.STUDENT);

        assertThat(request(who, s, classChange(m)).getStatus()).isEqualTo(201);
        var again = request(who, s, classChange(m));
        assertThat(again.getStatus()).isEqualTo(409);
        assertThat(body(again).get("code").asText()).isEqualTo("REQUEST_ALREADY_OPEN");
        assertThat(requests("kind = 'CLASS_CHANGE'")).isEqualTo(1);
        assertThat(request(who, s, planChange(m)).getStatus()).isEqualTo(201);

        var pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) calls.add(() -> request(who, s, closure(m, "OTHER")).getStatus());
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(calls)) statuses.add(f.get());
            assertThat(statuses.stream().filter(c -> c == 201).count()).isEqualTo(1);
            assertThat(statuses.stream().filter(c -> c != 201)).containsOnly(409);
        } finally {
            pool.shutdown();
        }
        assertThat(requests("kind = 'CLOSURE' and status = 'OPEN'")).isEqualTo(1);

        adminJdbc().update("update portal_request set status = 'REFUSED', decided_at = now() where kind in ('CLASS_CHANGE', 'PLAN_CHANGE')");
        assertThat(request(who, s, classChange(m)).getStatus()).isEqualTo(409);
        assertThat(request(who, s, planChange(m)).getStatus()).isEqualTo(409);
        assertThat(requests("status = 'OPEN'")).isEqualTo(1);
    }

    @Test
    @DisplayName("CA14: troca de turma avisa os Administradores da unidade; encerramento avisa a Coordenação (com e-mail) e os Administradores; sem nome nem texto")
    void notifications() throws Exception {
        Tenant t = tenant();
        UUID unitB = secondUnit(t);
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Sigiloso", "1990-01-01", acc);
        Setup m = setup(t, s, 0);
        accepted(t, s, acc);
        var adminA = manager(t, Role.ADMINISTRATOR, t.unitId());
        var adminB = manager(t, Role.ADMINISTRATOR, unitB);
        var coordinator = manager(t, Role.COORDINATOR, t.unitId());
        var who = portal(t, acc, Role.STUDENT);

        var created = request(who, s, classChange(m));
        assertThat(created.getStatus()).isEqualTo(201);
        String id = body(created).get("id").asText();
        assertThat(notices(adminA, "PORTAL_REQUEST")).isEqualTo(1);
        assertThat(notices(adminB, "PORTAL_REQUEST")).isZero();
        assertThat(notices(coordinator, "PORTAL_REQUEST")).isZero();
        assertThat(notices(coordinator, "CLOSURE_REQUEST")).isZero();
        assertThat(adminJdbc().queryForObject("select params->>'kind' from notification where type = 'PORTAL_REQUEST'", String.class)).isEqualTo("CLASS_CHANGE");
        assertThat(adminJdbc().queryForObject("select ref_id::text from notification where type = 'PORTAL_REQUEST'", String.class)).isEqualTo(id);

        assertThat(request(who, s, closure(m, "INJURY")).getStatus()).isEqualTo(201);
        assertThat(notices(coordinator, "CLOSURE_REQUEST")).isEqualTo(1);
        assertThat(notices(adminA, "PORTAL_REQUEST")).isEqualTo(2);
        assertThat(notices(adminA, "CLOSURE_REQUEST")).isZero();
        assertThat(adminJdbc().queryForObject("select count(*) from email_message where template = 'NOTIFICATION_URGENT' and to_email = (select email from account where id = ?)",
                Integer.class, coordinator.accountId())).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from notification where params::text like '%Sigiloso%' or params::text like '%horário%'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Opções e lista do aluno: modalidades ativas com turmas e planos válidos, ofertas que ele ainda não tem e pedidos do mais novo ao mais antigo")
    void optionsAndList() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        Setup m = setup(t, s, 0);
        UUID newModality = EnrollmentFixtures.modality(adminJdbc(), t.orgId(), 3);
        adminJdbc().update("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (?, ?, ?, true) on conflict do nothing", t.orgId(), t.unitId(), newModality);
        accepted(t, s, acc);
        var who = portal(t, acc, Role.STUDENT);
        assertThat(request(who, s, classChange(m)).getStatus()).isEqualTo(201);
        assertThat(request(who, s, closure(m, "OTHER")).getStatus()).isEqualTo(201);

        var options = body(get(who, "/students/" + s + "/request-options"));
        var row = options.get("modalities").get(0);
        assertThat(row.get("modalityRowId").asText()).isEqualTo(m.row().toString());
        assertThat(row.get("classes")).hasSize(1);
        assertThat(row.get("classes").get(0).get("id").asText()).isEqualTo(m.otherClass().toString());
        assertThat(row.get("plans").get(0).get("id").asText()).isEqualTo(m.otherPlan().toString());
        assertThat(row.get("closureRequestedAt").isNull()).isFalse();
        assertThat(options.get("offers").findValuesAsText("modalityId")).contains(newModality.toString()).doesNotContain(m.modality().toString());

        var list = body(get(who, "/students/" + s + "/requests"));
        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("kind").asText()).isEqualTo("CLOSURE");
        assertThat(call(MockMvcRequestBuilders.get("/api/v1/portal/students/" + s + "/requests"), null).getResponse().getStatus()).isEqualTo(401);
    }

    private int notices(AccountPrincipal who, String type) {
        return adminJdbc().queryForObject("select count(*) from notification where link_id = ? and type = ?", Integer.class, who.activeLinkId(), type);
    }
}

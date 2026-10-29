package br.com.ctcore.student;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Dados e chamadas comuns dos testes dos pedidos do portal (#86), com banco real. */
abstract class PortalRequestBase extends PortalTermBase {
    /** Modalidade ativa do aluno com uma turma e um plano alternativos da mesma unidade e modalidade. */
    record Setup(UUID student, UUID row, UUID modality, UUID klass, UUID plan, UUID otherClass, UUID otherPlan) {}

    @AfterEach
    void cleanRequests() {
        adminJdbc().update("delete from portal_request");
        adminJdbc().update("delete from notification");
        adminJdbc().update("delete from email_message where to_email like '%.example'");
    }

    /** Termo publicado e aceito: o portão do termo deixa passar. */
    void accepted(Tenant t, UUID student, UUID account) {
        if (adminJdbc().queryForObject("select count(*) from terms_version where organization_id = ? and kind = 'ENROLLMENT'", Integer.class, t.orgId()) == 0) {
            publish(t, "ENROLLMENT", 1, false);
        }
        adminJdbc().update("insert into term_acceptance (id, organization_id, student_id, kind, event, version, account_id, ip) values (?, ?, ?, 'ENROLLMENT', 'ACCEPTED', 1, ?, '127.0.0.1') on conflict do nothing",
                UUID.randomUUID(), t.orgId(), student, account);
    }

    Setup setup(Tenant t, UUID student, int kind) {
        return setup(t, student, t.unitId(), kind);
    }

    Setup setup(Tenant t, UUID student, UUID unit, int kind) {
        var db = adminJdbc();
        UUID modality = EnrollmentFixtures.modality(db, t.orgId(), kind);
        UUID teacher = EnrollmentFixtures.teacher(db, t.orgId(), null);
        UUID klass = EnrollmentFixtures.klass(db, t.orgId(), unit, modality, teacher);
        var e = EnrollmentFixtures.enroll(db, t.orgId(), student, unit, "ACTIVE", modality, klass, null, teacher);
        UUID otherClass = EnrollmentFixtures.klass(db, t.orgId(), unit, modality, teacher);
        UUID otherPlan = UUID.randomUUID();
        db.update("insert into plan (id, organization_id, unit_id, modality_id, name, name_key, value_cents, status) values (?, ?, ?, ?, ?, ?, 20000, 'ACTIVE')",
                otherPlan, t.orgId(), unit, modality, "P" + otherPlan, "p" + otherPlan);
        return new Setup(student, e.row(), modality, klass, e.plan(), otherClass, otherPlan);
    }

    UUID secondUnit(Tenant t) {
        UUID unit = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'Unidade B', ?)", unit, t.orgId(), "b-" + unit);
        return unit;
    }

    /** Pessoa da gestão com conta e vínculo reais (destinatária de notificação). */
    AccountPrincipal manager(Tenant t, Role role, UUID... units) {
        UUID account = UUID.randomUUID(), link = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Gestão Sintética', 'ACTIVE')", account, "gestao-" + account + "@gestao.example");
        adminJdbc().update("insert into account_link (id, account_id, organization_id, role, status) values (?, ?, ?, ?, 'ACTIVE')", link, account, t.orgId(), role.name());
        for (UUID unit : units) adminJdbc().update("insert into account_link_unit (link_id, organization_id, unit_id) values (?, ?, ?)", link, t.orgId(), unit);
        return new AccountPrincipal(account, UUID.randomUUID(), link, t.orgId(), role, Set.of(units));
    }

    MockHttpServletResponse request(AccountPrincipal who, UUID student, String body) throws Exception {
        return call(MockMvcRequestBuilders.post("/api/v1/portal/students/" + student + "/requests").contentType("application/json").content(body), who).getResponse();
    }

    String classChange(Setup s) {
        return "{\"kind\":\"CLASS_CHANGE\",\"modalityRowId\":\"" + s.row() + "\",\"targetClassId\":\"" + s.otherClass() + "\",\"note\":\"Prefiro o horário da noite\"}";
    }

    String planChange(Setup s) {
        return "{\"kind\":\"PLAN_CHANGE\",\"modalityRowId\":\"" + s.row() + "\",\"targetPlanId\":\"" + s.otherPlan() + "\"}";
    }

    String closure(Setup s, String reason) {
        return "{\"kind\":\"CLOSURE\",\"modalityRowId\":\"" + s.row() + "\",\"reason\":\"" + reason + "\"}";
    }

    MockHttpServletResponse manage(String method, String path, AccountPrincipal who, String body) throws Exception {
        var builder = switch (method) {
            case "POST" -> MockMvcRequestBuilders.post("/api/v1/portal-requests" + path).contentType("application/json").content(body == null ? "{}" : body);
            default -> MockMvcRequestBuilders.get("/api/v1/portal-requests" + path);
        };
        return call(builder, who).getResponse();
    }

    int requests(String where, Object... args) {
        return adminJdbc().queryForObject("select count(*) from portal_request where " + where, Integer.class, args);
    }

    int auditsOf(String action, String entityId) {
        return adminJdbc().queryForObject("select count(*) from audit_log where action = ? and entity_id = ?", Integer.class, action, entityId);
    }
}

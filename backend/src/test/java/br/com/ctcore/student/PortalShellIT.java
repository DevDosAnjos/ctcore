package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RF-POR-01, RN-POR-08, RN-INA-08, RN-INA-09, RN-POR-01: moldura do portal (marca, contador do sino, portal reduzido), com banco real. */
class PortalShellIT extends PortalTermBase {
    @AfterEach
    void cleanShell() {
        adminJdbc().update("delete from announcement_read");
        adminJdbc().update("delete from announcement_class");
        adminJdbc().update("delete from announcement");
    }

    /** Matrícula na modalidade 0 da unidade, com o aceite do termo já dado (o portão do termo deixa passar). */
    private EnrollmentFixtures.Enrolled enroll(Tenant t, UUID student, String status) {
        var e = EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), student, t.unitId(), status, 0, null);
        UUID by = adminJdbc().queryForList("select id from account where email = 'autor@portal.example'", UUID.class).stream().findFirst().orElseGet(() -> account("autor@portal.example"));
        adminJdbc().update("insert into term_acceptance (id, organization_id, student_id, kind, event, version, account_id, ip) values (?, ?, ?, 'ENROLLMENT', 'ACCEPTED', 1, ?, '127.0.0.1') on conflict do nothing",
                UUID.randomUUID(), t.orgId(), student, by);
        return e;
    }

    private UUID announcement(Tenant t, String kind, UUID unit, UUID klass, UUID modality, boolean sent) {
        UUID id = UUID.randomUUID();
        UUID author = adminJdbc().queryForList("select id from account where email = 'autor@portal.example'", UUID.class).stream().findFirst().orElseGet(() -> account("autor@portal.example"));
        adminJdbc().update("insert into announcement (id, organization_id, unit_id, target_kind, class_id, modality_id, type, title, body, scheduled_for, sent_at, recipient_count, author_account_id) "
                + "values (?, ?, ?, ?, ?, ?, 'GENERAL', 'Aviso', 'Corpo fictício', ?::timestamptz, ?::timestamptz, ?, ?)", id, t.orgId(), unit, kind, klass, modality,
                sent ? null : "2099-01-01T12:00:00Z", sent ? "2026-01-01T12:00:00Z" : null, sent ? 3 : null, author);
        return id;
    }

    private void read(Tenant t, UUID announcement, UUID student, UUID account) {
        adminJdbc().update("insert into announcement_read (organization_id, announcement_id, student_id, account_id) values (?, ?, ?, ?)", t.orgId(), announcement, student, account);
    }

    private int unread(Tenant t, UUID account, Role role) throws Exception {
        var r = get(portal(t, account, role), "/shell");
        assertThat(r.getStatus()).isEqualTo(200);
        return body(r).get("unreadAnnouncements").asInt();
    }

    @Test
    @DisplayName("CA4: conta só os comunicados enviados que alcançam o aluno e que ele não leu; o da organização inteira conta, o agendado e o de outra turma não")
    void unreadCount() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        var e = enroll(t, s, "ACTIVE");
        UUID otherClass = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), t.unitId(), EnrollmentFixtures.modality(adminJdbc(), t.orgId(), 0), e.teacher());

        UUID mine = announcement(t, "CLASS", t.unitId(), e.klass(), null, true);
        UUID alsoRead = announcement(t, "CLASS", t.unitId(), e.klass(), null, true);
        announcement(t, "CLASS", t.unitId(), e.klass(), null, false);
        announcement(t, "CLASS", t.unitId(), otherClass, null, true);
        read(t, alsoRead, s, acc);
        assertThat(unread(t, acc, Role.STUDENT)).isEqualTo(1);

        announcement(t, "ORGANIZATION", null, null, null, true);
        announcement(t, "UNIT", t.unitId(), null, null, true);
        assertThat(unread(t, acc, Role.STUDENT)).isEqualTo(3);
        read(t, mine, s, acc);
        assertThat(unread(t, acc, Role.STUDENT)).isEqualTo(2);
    }

    @Test
    @DisplayName("CA4: comunicado de modalidade e de várias turmas alcança o aluno ativo")
    void modalityAndClasses() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        var e = enroll(t, s, "ACTIVE");
        UUID modality = adminJdbc().queryForObject("select modality_id from enrollment_modality where id = ?", UUID.class, e.row());
        announcement(t, "MODALITY", t.unitId(), null, modality, true);
        UUID many = announcement(t, "CLASSES", t.unitId(), null, null, true);
        adminJdbc().update("insert into announcement_class (organization_id, announcement_id, class_id) values (?, ?, ?)", t.orgId(), many, e.klass());
        assertThat(unread(t, acc, Role.STUDENT)).isEqualTo(2);
    }

    @Test
    @DisplayName("CA5: responsável com dois filhos: o aviso conta uma vez; lido por um só ainda conta, lido pelos dois não")
    void twoChildren() throws Exception {
        Tenant t = tenant();
        UUID acc = account("responsavel@portal.example");
        UUID a = student(t, "Filho A", "2015-01-01", null), b = student(t, "Filho B", "2016-01-01", null);
        guardian(t, acc, "52998224725", true, a, b);
        enroll(t, a, "ACTIVE");
        enroll(t, b, "ACTIVE");
        UUID ann = announcement(t, "ORGANIZATION", null, null, null, true);

        assertThat(unread(t, acc, Role.GUARDIAN)).isEqualTo(1);
        read(t, ann, a, acc);
        assertThat(unread(t, acc, Role.GUARDIAN)).isEqualTo(1);
        read(t, ann, b, acc);
        assertThat(unread(t, acc, Role.GUARDIAN)).isZero();
    }

    @Test
    @DisplayName("CA7, CA8: reduzido quando todas as modalidades acabaram (contador 0); responsável com um filho ativo e conta sem matrícula não são reduzidos")
    void reduced() throws Exception {
        Tenant t = tenant();
        UUID former = account("ex@portal.example");
        UUID s = student(t, "Ex Aluno", "1990-01-01", former);
        enroll(t, s, "ENDED");
        announcement(t, "ORGANIZATION", null, null, null, true);
        var r = body(get(portal(t, former, Role.STUDENT), "/shell"));
        assertThat(r.get("reduced").asBoolean()).isTrue();
        assertThat(r.get("unreadAnnouncements").asInt()).isZero();
        assertThat(r.get("organization").get("name").asText()).startsWith("Synthetic");
        assertThat(r.get("organization").get("logoUrl").isNull()).isTrue();

        UUID parent = account("pai@portal.example");
        UUID active = student(t, "Filho Ativo", "2015-01-01", null), ended = student(t, "Filho Inativo", "2016-01-01", null);
        guardian(t, parent, "52998224725", true, active, ended);
        enroll(t, active, "ACTIVE");
        enroll(t, ended, "ENDED");
        assertThat(body(get(portal(t, parent, Role.GUARDIAN), "/shell")).get("reduced").asBoolean()).isFalse();

        UUID fresh = account("novo@portal.example");
        student(t, "Sem Matrícula", "1990-01-01", fresh);
        assertThat(body(get(portal(t, fresh, Role.STUDENT), "/shell")).get("reduced").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("CA13: unit traz nome, endereço e WhatsApp da unidade da modalidade ativa; campos nulos ficam nulos; reduzido e sem matrícula, unit nulo; nada de outra organização")
    void unit() throws Exception {
        Tenant t = tenant(), other = tenant();
        adminJdbc().update("update unit set address = 'Rua Fictícia, 10', whatsapp = '+55 11 90000-0000' where id = ?", t.unitId());
        adminJdbc().update("update unit set address = 'Rua Outra, 99', whatsapp = '+55 11 91111-1111' where id = ?", other.unitId());
        UUID acc = account("aluno@portal.example");
        enroll(t, student(t, "Aluno Um", "1990-01-01", acc), "ACTIVE");
        var u = body(get(portal(t, acc, Role.STUDENT), "/shell")).get("unit");
        assertThat(u.get("name").asText()).isEqualTo(adminJdbc().queryForObject("select name from unit where id = ?", String.class, t.unitId()));
        assertThat(u.get("address").asText()).isEqualTo("Rua Fictícia, 10");
        assertThat(u.get("whatsapp").asText()).isEqualTo("+55 11 90000-0000");

        adminJdbc().update("update unit set address = null, whatsapp = null where id = ?", t.unitId());
        var empty = body(get(portal(t, acc, Role.STUDENT), "/shell")).get("unit");
        assertThat(empty.get("address").isNull()).isTrue();
        assertThat(empty.get("whatsapp").isNull()).isTrue();

        UUID former = account("ex@portal.example");
        enroll(t, student(t, "Ex Aluno", "1990-01-01", former), "ENDED");
        assertThat(body(get(portal(t, former, Role.STUDENT), "/shell")).get("unit").isNull()).isTrue();
        UUID fresh = account("novo@portal.example");
        student(t, "Sem Matrícula", "1990-01-01", fresh);
        assertThat(body(get(portal(t, fresh, Role.STUDENT), "/shell")).get("unit").isNull()).isTrue();
    }

    @Test
    @DisplayName("CA10: nada de outra organização nem de outra conta entra na resposta")
    void isolation() throws Exception {
        Tenant a = tenant(), b = tenant();
        UUID x = account("x@portal.example"), y = account("y@portal.example");
        UUID sx = student(a, "Aluno X", "1990-01-01", x);
        enroll(a, sx, "ACTIVE");
        UUID sy = student(a, "Aluno Y", "1990-01-01", y);
        enroll(a, sy, "ACTIVE");
        UUID sb = student(b, "Aluno B", "1990-01-01", account("b@portal.example"));
        enroll(b, sb, "ACTIVE");
        announcement(b, "ORGANIZATION", null, null, null, true);
        announcement(b, "ORGANIZATION", null, null, null, true);
        UUID ownAnn = announcement(a, "ORGANIZATION", null, null, null, true);
        read(a, ownAnn, sy, y);

        var forX = body(get(portal(a, x, Role.STUDENT), "/shell"));
        assertThat(forX.get("unreadAnnouncements").asInt()).isEqualTo(1);
        assertThat(forX.get("organization").get("name").asText()).isEqualTo(adminJdbc().queryForObject("select name from organization where id = ?", String.class, a.orgId()));
        assertThat(unread(a, y, Role.STUDENT)).isZero();
        assertThat(unread(b, x, Role.STUDENT)).isZero();
    }

    @Test
    @DisplayName("CA11: gestão e Professor 403 FORBIDDEN, sem sessão 401, termo pendente 403 TERM_PENDING")
    void permissions() throws Exception {
        Tenant t = tenant();
        for (Role role : new Role[] {Role.ADMINISTRATOR, Role.COORDINATOR, Role.TEACHER}) {
            var r = get(portal(t, UUID.randomUUID(), role), "/shell");
            assertThat(r.getStatus()).isEqualTo(403);
            assertThat(body(r).get("code").asText()).isEqualTo("FORBIDDEN");
        }
        assertThat(get(null, "/shell").getStatus()).isEqualTo(401);

        publish(t, "ENROLLMENT", 1, true);
        UUID acc = account("aluno@portal.example");
        enrolledAdult(t, "Aluno Um", acc);
        var blocked = get(portal(t, acc, Role.STUDENT), "/shell");
        assertThat(blocked.getStatus()).isEqualTo(403);
        assertThat(body(blocked).get("code").asText()).isEqualTo("TERM_PENDING");
    }
}

package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationContext;
import br.com.ctcore.storage.FileStorage;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-POR-06, RN-POR-01, RN-POR-02, RN-POR-06, RN-POR-08, RN-COM-08, RN-INA-08: aba Avisos do portal (lista, abrir, marcar todos), com banco real. */
class PortalAnnouncementsIT extends PortalTermBase {
    @Autowired FileStorage storage;

    @AfterEach
    void cleanAnnouncements() {
        for (String table : new String[] {"announcement_dismissal", "announcement_read", "announcement_class", "announcement"}) adminJdbc().update("delete from " + table);
    }

    private UUID author() {
        return adminJdbc().queryForList("select id from account where email = 'autor@portal.example'", UUID.class).stream().findFirst()
                .orElseGet(() -> {
                    UUID id = account("autor@portal.example");
                    adminJdbc().update("update account set name = 'Prof. Autora' where id = ?", id);
                    return id;
                });
    }

    private EnrollmentFixtures.Enrolled enroll(Tenant t, UUID student, String status, int kind) {
        var e = EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), student, t.unitId(), status, kind, null);
        adminJdbc().update("insert into term_acceptance (id, organization_id, student_id, kind, event, version, account_id, ip) values (?, ?, ?, 'ENROLLMENT', 'ACCEPTED', 1, ?, '127.0.0.1') on conflict do nothing",
                UUID.randomUUID(), t.orgId(), student, author());
        return e;
    }

    private UUID modalityOf(UUID row) {
        return adminJdbc().queryForObject("select modality_id from enrollment_modality where id = ?", UUID.class, row);
    }

    private UUID notice(Tenant t, String kind, UUID unit, UUID klass, UUID modality, String sentAt, LocalDate pinnedUntil, String image) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into announcement (id, organization_id, unit_id, target_kind, class_id, modality_id, type, title, body, scheduled_for, sent_at, recipient_count, "
                + "author_account_id, pinned_until, image_name) values (?, ?, ?, ?, ?, ?, 'GENERAL', ?, 'Corpo <b>fictício</b>', ?::timestamptz, ?::timestamptz, ?, ?, ?, ?)",
                id, t.orgId(), unit, kind, klass, modality, "Aviso " + id, sentAt == null ? "2099-01-01T12:00:00Z" : null, sentAt, sentAt == null ? null : 3, author(), pinnedUntil, image);
        return id;
    }

    private UUID notice(Tenant t, String kind, UUID unit, UUID klass, UUID modality, String sentAt) {
        return notice(t, kind, unit, klass, modality, sentAt, null, null);
    }

    private JsonNode list(Tenant t, UUID account, Role role, String query) throws Exception {
        var r = get(portal(t, account, role), "/announcements" + query);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return body(r);
    }

    private MockHttpServletResponse postTo(AccountPrincipal who, String path) throws Exception {
        return call(MockMvcRequestBuilders.post("/api/v1/portal/announcements" + path), who).getResponse();
    }

    private JsonNode open(Tenant t, UUID account, Role role, UUID id, UUID student) throws Exception {
        var r = postTo(portal(t, account, role), "/" + id + "/open" + (student == null ? "" : "?studentId=" + student));
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return body(r);
    }

    private List<String> ids(JsonNode view) {
        List<String> out = new ArrayList<>();
        view.get("items").forEach(n -> out.add(n.get("id").asText()));
        return out;
    }

    private int count(String table) {
        return adminJdbc().queryForObject("select count(*) from " + table, Integer.class);
    }

    private int audits(String action, Object entity) {
        return adminJdbc().queryForObject("select count(*) from audit_log where action = ? and entity_id = ?", Integer.class, action, entity.toString());
    }

    private int bell(Tenant t, UUID account, Role role) throws Exception {
        return body(get(portal(t, account, role), "/shell")).get("unreadAnnouncements").asInt();
    }

    @Test
    @DisplayName("CA1: só os enviados que alcançam o aluno (organização, unidade, modalidade, turma, turmas), com destino, autor e data; agendado, turma alheia e outra unidade ficam de fora")
    void reach() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        var e = enroll(t, s, "ACTIVE", 0);
        UUID modality = modalityOf(e.row());
        UUID foreign = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), t.unitId(), modality, e.teacher());
        UUID otherUnit = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active) values (?, ?, 'Outra', ?, true)", otherUnit, t.orgId(), "o" + otherUnit.toString().substring(0, 12));

        UUID org = notice(t, "ORGANIZATION", null, null, null, "2026-01-05T12:00:00Z");
        UUID unit = notice(t, "UNIT", t.unitId(), null, null, "2026-01-04T12:00:00Z");
        UUID mod = notice(t, "MODALITY", null, null, modality, "2026-01-03T12:00:00Z");
        UUID klass = notice(t, "CLASS", t.unitId(), e.klass(), null, "2026-01-02T12:00:00Z");
        UUID many = notice(t, "CLASSES", t.unitId(), null, null, "2026-01-01T12:00:00Z");
        adminJdbc().update("insert into announcement_class (organization_id, announcement_id, class_id) values (?, ?, ?), (?, ?, ?)", t.orgId(), many, e.klass(), t.orgId(), many, foreign);
        notice(t, "ORGANIZATION", null, null, null, null);
        notice(t, "CLASS", t.unitId(), foreign, null, "2026-01-06T12:00:00Z");
        notice(t, "UNIT", otherUnit, null, null, "2026-01-06T12:00:00Z");

        JsonNode view = list(t, acc, Role.STUDENT, "");
        assertThat(ids(view)).containsExactly(org.toString(), unit.toString(), mod.toString(), klass.toString(), many.toString());
        assertThat(view.get("total").asInt()).isEqualTo(5);
        JsonNode first = view.get("items").get(0);
        assertThat(first.get("destination").get("kind").asText()).isEqualTo("ORGANIZATION");
        assertThat(first.get("destination").get("names")).isEmpty();
        assertThat(first.get("author").asText()).isEqualTo("Prof. Autora");
        assertThat(first.get("sentAt").asText()).startsWith("2026-01-05");
        assertThat(first.get("unread").asBoolean()).isTrue();
        assertThat(view.get("items").get(1).get("destination").get("names").get(0).asText()).isEqualTo("Unit");
        assertThat(view.get("items").get(3).get("destination").get("names")).hasSize(1);
        assertThat(view.get("items").get(4).get("destination").get("kind").asText()).isEqualTo("CLASSES");
        assertThat(view.get("items").get(4).get("destination").get("names")).hasSize(2);
        assertThat(view.get("unreadCount").asInt()).isEqualTo(5);
        assertThat(view.get("students")).hasSize(1);
        assertThat(view.toString().toLowerCase()).doesNotContain("attendance", "frequency", "progress", "phone", "email");
    }

    @Test
    @DisplayName("CA2: fixado no topo, fixado vencido (fuso da unidade) volta à ordem por data; size limita a 50 e pagina com o total")
    void orderAndPaging() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        enroll(t, s, "ACTIVE", 0);
        LocalDate today = adminJdbc().queryForObject("select (now() at time zone 'America/Sao_Paulo')::date", LocalDate.class);
        UUID oldPinned = notice(t, "ORGANIZATION", null, null, null, "2026-01-01T12:00:00Z", today, null);
        UUID newer = notice(t, "ORGANIZATION", null, null, null, "2026-02-01T12:00:00Z");
        UUID newest = notice(t, "ORGANIZATION", null, null, null, "2026-03-01T12:00:00Z");
        assertThat(ids(list(t, acc, Role.STUDENT, ""))).containsExactly(oldPinned.toString(), newest.toString(), newer.toString());
        assertThat(list(t, acc, Role.STUDENT, "").get("items").get(0).get("pinned").asBoolean()).isTrue();

        adminJdbc().update("update unit set timezone = 'Pacific/Kiritimati' where id = ?", t.unitId());
        LocalDate kiritimati = adminJdbc().queryForObject("select (now() at time zone 'Pacific/Kiritimati')::date", LocalDate.class);
        adminJdbc().update("update announcement set pinned_until = ? where id = ?", kiritimati.minusDays(1), oldPinned);
        JsonNode expired = list(t, acc, Role.STUDENT, "");
        assertThat(ids(expired)).containsExactly(newest.toString(), newer.toString(), oldPinned.toString());
        assertThat(expired.get("items").get(2).get("pinned").asBoolean()).isFalse();

        JsonNode second = list(t, acc, Role.STUDENT, "?size=2&page=2");
        assertThat(ids(second)).containsExactly(oldPinned.toString());
        assertThat(second.get("total").asInt()).isEqualTo(3);
        assertThat(list(t, acc, Role.STUDENT, "?size=2").get("items")).hasSize(2);
        assertThat(list(t, acc, Role.STUDENT, "?size=500").get("size").asInt()).isEqualTo(50);
        assertThat(list(t, acc, Role.STUDENT, "").get("size").asInt()).isEqualTo(20);
    }

    @Test
    @DisplayName("CA3: unread por aluno, filtro UNREAD e MODALITY, 400 para filtro e modalidade inválidos, unreadCount fixo")
    void filters() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        var jiu = enroll(t, s, "ACTIVE", 0);
        UUID jm = modalityOf(jiu.row());
        UUID dm = EnrollmentFixtures.modality(adminJdbc(), t.orgId(), 1);
        UUID judoClass = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), t.unitId(), dm, jiu.teacher());

        UUID read = notice(t, "ORGANIZATION", null, null, null, "2026-01-03T12:00:00Z");
        UUID unread = notice(t, "CLASS", t.unitId(), jiu.klass(), null, "2026-01-02T12:00:00Z");
        UUID byModality = notice(t, "MODALITY", null, null, jm, "2026-01-01T12:00:00Z");
        adminJdbc().update("insert into announcement_read (organization_id, announcement_id, student_id, account_id) values (?, ?, ?, ?)", t.orgId(), read, s, acc);
        notice(t, "CLASS", t.unitId(), judoClass, null, "2026-01-04T12:00:00Z");

        JsonNode all = list(t, acc, Role.STUDENT, "");
        assertThat(all.get("items").get(0).get("unread").asBoolean()).isFalse();
        assertThat(all.get("items").get(1).get("unread").asBoolean()).isTrue();
        assertThat(ids(list(t, acc, Role.STUDENT, "?filter=UNREAD"))).containsExactly(unread.toString(), byModality.toString());
        JsonNode mod = list(t, acc, Role.STUDENT, "?filter=MODALITY&modalityId=" + jm);
        assertThat(ids(mod)).containsExactly(read.toString(), unread.toString(), byModality.toString());
        assertThat(mod.get("unreadCount").asInt()).isEqualTo(2);
        assertThat(mod.get("modalities")).hasSize(1);
        for (String bad : new String[] {"?filter=XYZ", "?filter=MODALITY", "?filter=MODALITY&modalityId=" + dm, "?filter=MODALITY&modalityId=" + UUID.randomUUID()}) {
            assertThat(get(portal(t, acc, Role.STUDENT), "/announcements" + bad).getStatus()).as(bad).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("CA4, CA5, CA10: abrir traz o texto e a imagem, grava uma leitura do aluno; repetir não duplica nem audita; dois responsáveis contam uma; sem título no registro")
    void open() throws Exception {
        Tenant t = tenant();
        UUID a1 = account("mae@portal.example"), a2 = account("pai@portal.example");
        UUID oa = account("outro@portal.example");
        UUID minor = student(t, "Menor", "2015-01-01", null), other = student(t, "Outro", "1990-01-01", oa);
        guardian(t, a1, "52998224725", true, minor);
        guardian(t, a2, "11144477735", false, minor);
        enroll(t, minor, "ACTIVE", 0);
        enroll(t, other, "ACTIVE", 0);
        String image = "aviso-" + UUID.randomUUID() + ".png";
        try (var ignored = OrganizationContext.open(portal(t, a1, Role.GUARDIAN))) {
            storage.put(image, new byte[] {1, 2, 3}, "image/png");
        }
        UUID id = notice(t, "ORGANIZATION", null, null, null, "2026-01-01T12:00:00Z", null, image);

        JsonNode detail = open(t, a1, Role.GUARDIAN, id, null);
        assertThat(detail.get("body").asText()).isEqualTo("Corpo <b>fictício</b>");
        assertThat(detail.get("imageUrl").asText()).contains(image);
        assertThat(detail.get("unread").asBoolean()).isFalse();
        var opened = adminJdbc().queryForMap("select student_id, account_id, opened_at from announcement_read where announcement_id = ?", id);
        assertThat(opened.get("student_id")).isEqualTo(minor);
        assertThat(opened.get("account_id")).isEqualTo(a1);

        open(t, a1, Role.GUARDIAN, id, null);
        open(t, a2, Role.GUARDIAN, id, null);
        assertThat(count("announcement_read")).isEqualTo(1);
        assertThat(adminJdbc().queryForMap("select opened_at from announcement_read").get("opened_at")).isEqualTo(opened.get("opened_at"));
        assertThat(audits("portal_announcement.opened", id)).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select after_data::text from audit_log where action = 'portal_announcement.opened' and entity_id = ?", String.class, id.toString())).doesNotContain("Aviso", "fictício");
        assertThat(adminJdbc().queryForObject("select count(*) from announcement_read where announcement_id = ?", Integer.class, id)).isEqualTo(1);

        open(t, oa, Role.STUDENT, id, null);
        assertThat(count("announcement_read")).isEqualTo(2);

        // Arquivo ausente: o aviso abre sem imagem.
        UUID noFile = notice(t, "ORGANIZATION", null, null, null, "2026-01-02T12:00:00Z", null, "ausente.png");
        assertThat(open(t, a1, Role.GUARDIAN, noFile, null).get("imageUrl").isNull()).isTrue();
    }

    @Test
    @DisplayName("CA6: agendado, de outra organização, que não alcança o aluno e aluno de outra conta dão o mesmo 404 e nada é gravado")
    void notFound() throws Exception {
        Tenant a = tenant(), b = tenant();
        UUID x = account("x@portal.example"), y = account("y@portal.example");
        UUID sx = student(a, "Aluno X", "1990-01-01", x), sy = student(a, "Aluno Y", "1990-01-01", y), sb = student(b, "Aluno B", "1990-01-01", account("b@portal.example"));
        var ex = enroll(a, sx, "ACTIVE", 0);
        enroll(a, sy, "ACTIVE", 0);
        enroll(b, sb, "ACTIVE", 0);
        UUID foreign = EnrollmentFixtures.klass(adminJdbc(), a.orgId(), a.unitId(), modalityOf(ex.row()), ex.teacher());
        UUID scheduled = notice(a, "ORGANIZATION", null, null, null, null);
        UUID elsewhere = notice(b, "ORGANIZATION", null, null, null, "2026-01-01T12:00:00Z");
        UUID noReach = notice(a, "CLASS", a.unitId(), foreign, null, "2026-01-01T12:00:00Z");
        UUID mine = notice(a, "ORGANIZATION", null, null, null, "2026-01-01T12:00:00Z");

        List<String> paths = List.of("/" + scheduled + "/open", "/" + elsewhere + "/open", "/" + noReach + "/open", "/" + UUID.randomUUID() + "/open", "/" + mine + "/open?studentId=" + sy,
                "/" + mine + "/open?studentId=" + sb);
        for (String path : paths) {
            var r = postTo(portal(a, x, Role.STUDENT), path);
            assertThat(r.getStatus()).as(path).isEqualTo(404);
            assertThat(body(r).get("code").asText()).isEqualTo("NOT_FOUND");
        }
        assertThat(get(portal(a, x, Role.STUDENT), "/announcements?studentId=" + sy).getStatus()).isEqualTo(404);
        assertThat(count("announcement_read")).isZero();
        assertThat(ids(list(a, x, Role.STUDENT, ""))).containsExactly(mine.toString());
    }

    @Test
    @DisplayName("CA7, CA8, CA10: marcar todos limpa lista, sino e Início sem gravar leitura, vale para todos os filhos, repetir dá 0 e aviso novo volta a contar")
    void readAll() throws Exception {
        Tenant t = tenant();
        UUID acc = account("responsavel@portal.example");
        UUID first = student(t, "Filho A", "2015-01-01", null), second = student(t, "Filho B", "2016-01-01", null);
        guardian(t, acc, "52998224725", true, first, second);
        var ea = enroll(t, first, "ACTIVE", 0);
        var eb = enroll(t, second, "ACTIVE", 1);
        UUID onlySecond = notice(t, "MODALITY", null, null, modalityOf(eb.row()), "2026-01-01T12:00:00Z");
        UUID both = notice(t, "ORGANIZATION", null, null, null, "2026-01-02T12:00:00Z");
        assertThat(bell(t, acc, Role.GUARDIAN)).isEqualTo(2);

        var done = postTo(portal(t, acc, Role.GUARDIAN), "/read-all");
        assertThat(done.getStatus()).isEqualTo(200);
        assertThat(body(done).get("dismissed").asInt()).isEqualTo(2);
        assertThat(count("announcement_read")).isZero();
        assertThat(count("announcement_dismissal")).isEqualTo(2);
        for (UUID student : new UUID[] {first, second}) {
            JsonNode view = list(t, acc, Role.GUARDIAN, "?studentId=" + student);
            assertThat(view.get("unreadCount").asInt()).isZero();
            view.get("items").forEach(n -> assertThat(n.get("unread").asBoolean()).isFalse());
        }
        assertThat(bell(t, acc, Role.GUARDIAN)).isZero();
        JsonNode home = body(get(portal(t, acc, Role.GUARDIAN), "/home?studentId=" + second));
        assertThat(home.get("announcements").get("unreadCount").asInt()).isZero();
        assertThat(body(postTo(portal(t, acc, Role.GUARDIAN), "/read-all")).get("dismissed").asInt()).isZero();
        assertThat(audits("portal_announcement.read_all", acc)).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select after_data::text from audit_log where action = 'portal_announcement.read_all' and entity_id = ?", String.class, acc.toString())).doesNotContain("Aviso");

        notice(t, "ORGANIZATION", null, null, null, "2026-01-03T12:00:00Z");
        assertThat(bell(t, acc, Role.GUARDIAN)).isEqualTo(1);

        // CA8: abrir depois grava a leitura do filho escolhido e a lista segue sem destacá-lo.
        open(t, acc, Role.GUARDIAN, onlySecond, second);
        assertThat(adminJdbc().queryForObject("select student_id from announcement_read where announcement_id = ?", UUID.class, onlySecond)).isEqualTo(second);
        assertThat(count("announcement_read")).isEqualTo(1);
        assertThat(both).isNotNull();
        assertThat(ea).isNotNull();
    }

    @Test
    @DisplayName("CA9: gestão e Professor 403, sem sessão 401, termo pendente 403 TERM_PENDING; outra organização não vê nem limpa nada")
    void access() throws Exception {
        Tenant a = tenant(), b = tenant();
        UUID x = account("x@portal.example"), y = account("y@portal.example");
        enroll(a, student(a, "Aluno X", "1990-01-01", x), "ACTIVE", 0);
        enroll(b, student(b, "Aluno Y", "1990-01-01", y), "ACTIVE", 0);
        UUID id = notice(a, "ORGANIZATION", null, null, null, "2026-01-01T12:00:00Z");

        for (Role role : new Role[] {Role.ADMINISTRATOR, Role.COORDINATOR, Role.TEACHER}) {
            AccountPrincipal who = portal(a, UUID.randomUUID(), role);
            assertThat(get(who, "/announcements").getStatus()).isEqualTo(403);
            assertThat(postTo(who, "/" + id + "/open").getStatus()).isEqualTo(403);
            assertThat(postTo(who, "/read-all").getStatus()).isEqualTo(403);
        }
        assertThat(get(null, "/announcements").getStatus()).isEqualTo(401);
        assertThat(postTo(null, "/read-all").getStatus()).isEqualTo(401);

        assertThat(postTo(portal(b, y, Role.STUDENT), "/" + id + "/open").getStatus()).isEqualTo(404);
        assertThat(body(postTo(portal(b, y, Role.STUDENT), "/read-all")).get("dismissed").asInt()).isZero();
        assertThat(list(b, y, Role.STUDENT, "").get("items")).isEmpty();
        assertThat(count("announcement_dismissal")).isZero();

        // RLS: a organização B não enxerga a marca da A.
        postTo(portal(a, x, Role.STUDENT), "/read-all");
        assertThat(count("announcement_dismissal")).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from announcement_dismissal where organization_id = ?", Integer.class, b.orgId())).isZero();

        publishTerm(a, x);
        var blocked = get(portal(a, x, Role.STUDENT), "/announcements");
        assertThat(blocked.getStatus()).isEqualTo(403);
        assertThat(body(blocked).get("code").asText()).isEqualTo("TERM_PENDING");
        assertThat(postTo(portal(a, x, Role.STUDENT), "/read-all").getStatus()).isEqualTo(403);
    }

    private void publishTerm(Tenant t, UUID account) {
        adminJdbc().update("delete from term_acceptance where student_id in (select id from student where account_id = ?)", account);
        publish(t, "ENROLLMENT", 1, true);
    }

    @Test
    @DisplayName("CA11: portal reduzido responde 200 com reduced e lista vazia; abrir é 404 e marcar todos dá 0 sem gravar")
    void reduced() throws Exception {
        Tenant t = tenant();
        UUID acc = account("ex@portal.example");
        UUID s = student(t, "Ex Aluno", "1990-01-01", acc);
        enroll(t, s, "ENDED", 0);
        UUID id = notice(t, "ORGANIZATION", null, null, null, "2026-01-01T12:00:00Z");
        JsonNode view = list(t, acc, Role.STUDENT, "");
        assertThat(view.get("reduced").asBoolean()).isTrue();
        assertThat(view.get("items")).isEmpty();
        assertThat(postTo(portal(t, acc, Role.STUDENT), "/" + id + "/open").getStatus()).isEqualTo(404);
        assertThat(body(postTo(portal(t, acc, Role.STUDENT), "/read-all")).get("dismissed").asInt()).isZero();
        assertThat(count("announcement_read") + count("announcement_dismissal")).isZero();
    }
}

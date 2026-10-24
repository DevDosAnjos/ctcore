package br.com.ctcore.communication;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** RF-COM-01 (#81): lista, filtros, escopo por perfil, leituras e cancelamento. */
class AnnouncementsIT extends AnnouncementBase {
    @Test
    @DisplayName("CA1: ordem da data mais recente para a mais antiga, destino nomeado, autor, situação, recipients e reads; o agendado traz recipients nulo e reads 0")
    void list() throws Exception {
        Tenant t = tenant();
        AccountPrincipal coordinator = account(t, Role.COORDINATOR);
        UUID teacher = teacher(t, null);
        UUID klass = klass(t, "Turma Sintetica", teacher);
        announcement(t, coordinator, "CLASS", t.unitId(), klass, "CANCELED_CLASS", "Antigo", null, "2026-10-01T15:00:00Z");
        UUID recent = sentToUnit(t, coordinator, "Recente", "2026-10-05T15:00:00Z");
        scheduledToUnit(t, coordinator, "Futuro");
        UUID student = student(t, "1990-01-01");
        adminJdbc().update("insert into announcement_read (organization_id, announcement_id, student_id, account_id) values (?, ?, ?, ?)", t.orgId(), recent, student, coordinator.accountId());

        JsonNode body = ok(send(HttpMethod.GET, "/announcements", coordinator), 200);
        assertThat(body.get("total").asInt()).isEqualTo(3);
        JsonNode items = body.get("items");
        assertThat(items.get(0).get("title").asText()).isEqualTo("Futuro");
        assertThat(items.get(0).get("status").asText()).isEqualTo("SCHEDULED");
        assertThat(items.get(0).get("recipients").isNull()).isTrue();
        assertThat(items.get(0).get("reads").asInt()).isZero();
        assertThat(items.get(1).get("status").asText()).isEqualTo("SENT");
        assertThat(items.get(1).get("recipients").asInt()).isEqualTo(7);
        assertThat(items.get(1).get("reads").asInt()).isEqualTo(1);
        assertThat(items.get(1).get("author").asText()).isEqualTo("Autora Sintética");
        assertThat(items.get(1).get("target").get("kind").asText()).isEqualTo("UNIT");
        assertThat(items.get(1).get("target").get("name").asText()).isEqualTo("Unit");
        assertThat(items.get(2).get("target").get("kind").asText()).isEqualTo("CLASS");
        assertThat(items.get(2).get("target").get("name").asText()).isEqualTo("Turma Sintetica");
        assertThat(items.get(2).get("type").asText()).isEqualTo("CANCELED_CLASS");
        assertThat(titles("?sort=title,asc", coordinator)).containsExactly("Antigo", "Futuro", "Recente");
        assertThat(titles("?sort=type,asc&size=25", coordinator).get(0)).isEqualTo("Antigo");
        assertThat(send(HttpMethod.GET, "/announcements?sort=body,asc", coordinator).getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("CA2: filtros por tipo, turma e período no fuso da unidade (inclusive nos extremos); to antes de from e tipo inválido dão 400")
    void filters() throws Exception {
        Tenant t = tenant();
        AccountPrincipal coordinator = account(t, Role.COORDINATOR);
        UUID klass = klass(t, "Turma Filtro", teacher(t, null));
        // 2026-10-10T02:00Z é 09/10 23:00 em São Paulo; 2026-10-10T03:00Z é 10/10 00:00.
        announcement(t, coordinator, "CLASS", t.unitId(), klass, "CANCELED_CLASS", "A", null, "2026-10-10T02:00:00Z");
        sentToUnit(t, coordinator, "B", "2026-10-10T03:00:00Z");
        sentToUnit(t, coordinator, "C", "2026-10-11T02:59:00Z");
        sentToUnit(t, coordinator, "D", "2026-10-11T03:00:00Z");

        assertThat(titles("?type=CANCELED_CLASS", coordinator)).containsExactly("A");
        assertThat(titles("?classId=" + klass, coordinator)).containsExactly("A");
        assertThat(titles("?from=2026-10-10&to=2026-10-10", coordinator)).containsExactly("C", "B");
        assertThat(titles("?from=2026-10-09&to=2026-10-09", coordinator)).containsExactly("A");
        assertThat(titles("?from=2026-10-11", coordinator)).containsExactly("D");
        assertThat(titles("?to=2026-10-09", coordinator)).containsExactly("A");
        assertThat(titles("?type=GENERAL&from=2026-10-10&to=2026-10-10", coordinator)).containsExactly("C", "B");
        assertThat(titles("?type=GENERAL&classId=" + klass, coordinator)).isEmpty();
        assertThat(send(HttpMethod.GET, "/announcements?from=2026-10-11&to=2026-10-10", coordinator).getStatus()).isEqualTo(400);
        assertThat(send(HttpMethod.GET, "/announcements?from=ontem", coordinator).getStatus()).isEqualTo(400);
        assertThat(send(HttpMethod.GET, "/announcements?type=OTHER", coordinator).getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("CA3: unitId de outra organização dá 404; Administrador com unidade fora do alcance recebe lista vazia; unitId filtra")
    void unitFilter() throws Exception {
        Tenant t = tenant(), other = tenant();
        AccountPrincipal coordinator = account(t, Role.COORDINATOR);
        UUID second = secondUnit(t);
        sentToUnit(t, coordinator, "Na primeira", "2026-10-05T15:00:00Z");
        announcement(t, coordinator, "UNIT", second, null, "GENERAL", "Na segunda", null, "2026-10-05T15:00:00Z");
        assertThat(send(HttpMethod.GET, "/announcements?unitId=" + other.unitId(), coordinator).getStatus()).isEqualTo(404);
        assertThat(titles("?unitId=" + second, coordinator)).containsExactly("Na segunda");
        AccountPrincipal admin = accountIn(t, Role.ADMINISTRATOR, t.unitId());
        assertThat(titles("?unitId=" + second, admin)).isEmpty();
        assertThat(titles("?unitId=" + t.unitId(), admin)).containsExactly("Na primeira");
        assertThat(titles("", accountIn(t, Role.ADMINISTRATOR))).isEmpty();
    }

    @Test
    @DisplayName("CA4: o Coordenador vê tudo; o Administrador só as unidades dele e nunca o de todas as unidades")
    void administratorReach() throws Exception {
        Tenant t = tenant();
        AccountPrincipal coordinator = account(t, Role.COORDINATOR);
        UUID second = secondUnit(t);
        sentToUnit(t, coordinator, "Unidade 1", "2026-10-05T15:00:00Z");
        announcement(t, coordinator, "UNIT", second, null, "GENERAL", "Unidade 2", null, "2026-10-04T15:00:00Z");
        announcement(t, coordinator, "ORGANIZATION", null, null, "GENERAL", "Todas", null, "2026-10-03T15:00:00Z");
        assertThat(titles("", coordinator)).containsExactly("Unidade 1", "Unidade 2", "Todas");
        assertThat(ok(send(HttpMethod.GET, "/announcements", coordinator), 200).get("items").get(2).get("target").get("name").isNull()).isTrue();
        assertThat(titles("", accountIn(t, Role.ADMINISTRATOR, t.unitId()))).containsExactly("Unidade 1");
        assertThat(titles("", accountIn(t, Role.ADMINISTRATOR, second))).containsExactly("Unidade 2");
    }

    @Test
    @DisplayName("CA5: o Professor vê o da turma dele (titular ou auxiliar) e o que enviou, não o da turma de outro; sem a permissão ligada na unidade, 403")
    void teacherScope() throws Exception {
        Tenant t = tenant();
        AccountPrincipal coordinator = account(t, Role.COORDINATOR);
        AccountPrincipal teacher = account(t, Role.TEACHER);
        UUID me = teacher(t, teacher), another = teacher(t, null);
        UUID mine = klass(t, "Minha Turma", me), theirs = klass(t, "Turma Alheia", another), assisted = klass(t, "Turma Auxiliar", another);
        adminJdbc().update("insert into training_class_assistant (organization_id, class_id, teacher_id) values (?, ?, ?)", t.orgId(), assisted, me);
        announcement(t, coordinator, "CLASS", t.unitId(), mine, "GENERAL", "Da minha turma", null, "2026-10-05T15:00:00Z");
        announcement(t, coordinator, "CLASS", t.unitId(), theirs, "GENERAL", "Da turma alheia", null, "2026-10-05T14:00:00Z");
        announcement(t, coordinator, "CLASS", t.unitId(), assisted, "GENERAL", "Da turma auxiliar", null, "2026-10-05T13:00:00Z");
        sentToUnit(t, teacher, "Enviado por mim", "2026-10-05T12:00:00Z");
        sentToUnit(t, coordinator, "Da unidade", "2026-10-05T11:00:00Z");
        assertThat(titles("", teacher)).containsExactly("Da minha turma", "Da turma auxiliar", "Enviado por mim");
        assertThat(titles("?classId=" + theirs, teacher)).isEmpty();
        adminJdbc().update("update unit_settings set teacher_permissions = '{false,true,true,true,false}' where unit_id = ?", t.unitId());
        assertThat(send(HttpMethod.GET, "/announcements", teacher).getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("CA7: reads conta alunos distintos; dois responsáveis do mesmo menor contam uma vez e a unicidade impede a segunda leitura")
    void reads() throws Exception {
        Tenant t = tenant();
        AccountPrincipal coordinator = account(t, Role.COORDINATOR);
        UUID id = sentToUnit(t, coordinator, "Lido", "2026-10-05T15:00:00Z");
        UUID minor = student(t, "2018-01-01"), adult = student(t, "1990-01-01");
        UUID g1 = BillingFixtures.guardian(adminJdbc(), t.orgId(), minor, true), g2 = BillingFixtures.guardian(adminJdbc(), t.orgId(), minor, false);
        UUID a1 = adminJdbc().queryForObject("select account_id from guardian where id = ?", UUID.class, g1);
        UUID a2 = adminJdbc().queryForObject("select account_id from guardian where id = ?", UUID.class, g2);
        String read = "insert into announcement_read (organization_id, announcement_id, student_id, account_id) values (?, ?, ?, ?)";
        adminJdbc().update(read, t.orgId(), id, minor, a1);
        try {
            adminJdbc().update(read, t.orgId(), id, minor, a2);
        } catch (org.springframework.dao.DuplicateKeyException expected) {
            // a leitura do segundo responsável não cria linha
        }
        adminJdbc().update(read, t.orgId(), id, adult, coordinator.accountId());
        assertThat(ok(send(HttpMethod.GET, "/announcements", coordinator), 200).get("items").get(0).get("reads").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("CA8: o Administrador cancela o agendado: 204, some da lista, auditoria com título e destino, nada mais muda; repetir dá 404")
    void cancel() throws Exception {
        Tenant t = tenant();
        AccountPrincipal admin = accountIn(t, Role.ADMINISTRATOR, t.unitId());
        UUID id = scheduledToUnit(t, admin, "Cancelar este");
        UUID kept = scheduledToUnit(t, admin, "Manter este");
        assertThat(send(HttpMethod.DELETE, "/announcements/" + id, admin).getStatus()).isEqualTo(204);
        assertThat(titles("", admin)).containsExactly("Manter este");
        assertThat(count("select count(*) from announcement where id = ?", kept)).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where organization_id = ? and action = 'announcement.cancel'", t.orgId())).isEqualTo(1);
        String before = adminJdbc().queryForObject("select before_data::text from audit_log where organization_id = ? and action = 'announcement.cancel'", String.class, t.orgId());
        assertThat(before).contains("Cancelar este").contains("UNIT:Unit");
        assertThat(send(HttpMethod.DELETE, "/announcements/" + id, admin).getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("CA9: o comunicado enviado não é cancelado: 409 ANNOUNCEMENT_ALREADY_SENT e a linha continua igual")
    void alreadySent() throws Exception {
        Tenant t = tenant();
        AccountPrincipal coordinator = account(t, Role.COORDINATOR);
        UUID id = sentToUnit(t, coordinator, "Enviado", "2026-10-05T15:00:00Z");
        var response = send(HttpMethod.DELETE, "/announcements/" + id, coordinator);
        assertThat(ok(response, 409).get("code").asText()).isEqualTo("ANNOUNCEMENT_ALREADY_SENT");
        assertThat(count("select count(*) from announcement where id = ? and sent_at is not null", id)).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where organization_id = ? and action = 'announcement.cancel'", t.orgId())).isZero();
    }

    @Test
    @DisplayName("CA10: o Professor cancela só o agendado dele; o de outro autor (mesmo da turma dele) é 403; Administrador de outra unidade e Professor sem permissão: 403")
    void cancelScope() throws Exception {
        Tenant t = tenant();
        AccountPrincipal coordinator = account(t, Role.COORDINATOR);
        AccountPrincipal teacher = account(t, Role.TEACHER);
        UUID klass = klass(t, "Turma do Prof", teacher(t, teacher));
        UUID others = announcement(t, coordinator, "CLASS", t.unitId(), klass, "GENERAL", "De outro", "2099-01-01T12:00:00Z", null);
        UUID own = announcement(t, teacher, "CLASS", t.unitId(), klass, "GENERAL", "Meu", "2099-01-01T12:00:00Z", null);
        UUID all = announcement(t, coordinator, "ORGANIZATION", null, null, "GENERAL", "Todas", "2099-01-01T12:00:00Z", null);
        assertThat(send(HttpMethod.DELETE, "/announcements/" + others, teacher).getStatus()).isEqualTo(403);
        assertThat(send(HttpMethod.DELETE, "/announcements/" + all, accountIn(t, Role.ADMINISTRATOR, t.unitId())).getStatus()).isEqualTo(403);
        assertThat(send(HttpMethod.DELETE, "/announcements/" + others, accountIn(t, Role.ADMINISTRATOR, secondUnit(t))).getStatus()).isEqualTo(403);
        adminJdbc().update("update unit_settings set teacher_permissions = '{false,true,true,true,false}' where unit_id = ?", t.unitId());
        assertThat(send(HttpMethod.DELETE, "/announcements/" + own, teacher).getStatus()).isEqualTo(403);
        adminJdbc().update("update unit_settings set teacher_permissions = '{true,true,true,true,false}' where unit_id = ?", t.unitId());
        assertThat(send(HttpMethod.DELETE, "/announcements/" + own, teacher).getStatus()).isEqualTo(204);
        assertThat(send(HttpMethod.DELETE, "/announcements/" + all, coordinator).getStatus()).isEqualTo(204);
        assertThat(count("select count(*) from announcement")).isEqualTo(1);
    }

    @Test
    @DisplayName("#82 CA21: comunicado CLASSES traz o nome das duas turmas, o filtro por qualquer uma o encontra e o Professor titular de uma delas o vê; o de outra turma não")
    void classes() throws Exception {
        Tenant t = tenant();
        AccountPrincipal coordinator = account(t, Role.COORDINATOR);
        AccountPrincipal first = account(t, Role.TEACHER), stranger = account(t, Role.TEACHER);
        UUID one = klass(t, "Turma Um", teacher(t, first)), two = klass(t, "Turma Dois", teacher(t, null));
        teacher(t, stranger);
        UUID id = announcement(t, coordinator, "CLASSES", t.unitId(), null, "NO_CLASS_DAY", "Sem aula", null, "2026-10-05T15:00:00Z");
        for (UUID k : new UUID[] {one, two}) {
            adminJdbc().update("insert into announcement_class (organization_id, announcement_id, class_id) values (?, ?, ?)", t.orgId(), id, k);
        }
        JsonNode item = ok(send(HttpMethod.GET, "/announcements", coordinator), 200).get("items").get(0);
        assertThat(item.get("target").get("kind").asText()).isEqualTo("CLASSES");
        assertThat(item.get("target").get("name").asText()).isEqualTo("Turma Dois, Turma Um");
        assertThat(item.get("target").get("id").isNull()).isTrue();
        assertThat(titles("?classId=" + one, coordinator)).containsExactly("Sem aula");
        assertThat(titles("?classId=" + two, coordinator)).containsExactly("Sem aula");
        assertThat(titles("?classId=" + UUID.randomUUID(), coordinator)).isEmpty();
        assertThat(titles("", first)).containsExactly("Sem aula");
        assertThat(titles("", stranger)).isEmpty();
    }
}

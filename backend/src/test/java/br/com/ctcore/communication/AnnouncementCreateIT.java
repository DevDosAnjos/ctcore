package br.com.ctcore.communication;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.integration.Outbox;
import com.fasterxml.jackson.databind.JsonNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-COM-02 (#82, R3, R4, R7 a R10): criar e enviar o comunicado, validações, permissão por destino, limite do dia e imagem. */
class AnnouncementCreateIT extends AnnouncementBase {
    @Autowired Outbox outbox;

    @AfterEach
    void clearOutbox() {
        outbox.clear();
    }

    private static Map<String, Object> draft(UUID klass, String... channels) {
        Map<String, Object> d = new HashMap<>();
        d.put("targetKind", "CLASS");
        d.put("classId", klass);
        d.put("type", "GENERAL");
        d.put("title", "Aviso importante");
        d.put("body", "Texto do aviso.");
        d.put("channels", List.of(channels));
        return d;
    }

    private UUID optOut(Tenant t, String email) {
        UUID account = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", account, email);
        adminJdbc().update("insert into account_link (account_id, organization_id, role, status, announcement_email) values (?, ?, 'STUDENT', 'ACTIVE', false)", account, t.orgId());
        return account;
    }

    private byte[] png(int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("CA6, CA15: Administrador envia agora para a turma: 201 SENT, contagem gravada, e-mail por endereço (pulado a quem desligou), PORTAL no canal, texto simples escapado e auditoria sem o corpo")
    void sendNow() throws Exception {
        Tenant t = tenant();
        AccountPrincipal admin = accountIn(t, Role.ADMINISTRATOR, t.unitId());
        UUID teacher = teacher(t, null), klass = klass(t, "Turma Envio", teacher);
        enrolled(t, klass, teacher, "1990-01-01", "envio-1@example.test", "ACTIVE");
        enrolled(t, klass, teacher, "1991-01-01", "gen-desligou@example.com", "ACTIVE");
        optOut(t, "gen-desligou@example.com");
        UUID minor = enrolled(t, klass, teacher, "2015-01-01", "menor@example.test", "ACTIVE");
        br.com.ctcore.billing.BillingFixtures.guardian(adminJdbc(), t.orgId(), minor, true);
        Map<String, Object> d = draft(klass, "EMAIL");
        String text = "**Atenção** <b> & aviso\n- item\nVeja [o site](https://exemplo.test/x).";
        d.put("body", text);
        d.put("title", "Reunião de pais");

        JsonNode created = ok(post("/announcements", d, admin), 201);
        assertThat(created.get("status").asText()).isEqualTo("SENT");
        assertThat(created.get("recipients").asInt()).isEqualTo(3);
        UUID id = UUID.fromString(created.get("id").asText());
        assertThat(count("select count(*) from announcement where id = ? and sent_at is not null and recipient_count = 3 and body = ?", id, text)).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select array_to_string(channels, ',') from announcement where id = ?", String.class, id)).isEqualTo("PORTAL,EMAIL");
        assertThat(count("select count(*) from email_message where organization_id = ? and template = 'ANNOUNCEMENT'", t.orgId())).isEqualTo(3);
        assertThat(count("select count(*) from email_message where organization_id = ? and template = 'ANNOUNCEMENT' and status = 'SKIPPED' and last_error = 'OPT_OUT'", t.orgId())).isEqualTo(1);
        var sent = outbox.list().stream().filter(m -> m.recipient().equals("envio-1@example.test")).findFirst().orElseThrow().content();
        assertThat(sent).contains("Atenção", "Reunião de pais", "o site (https://exemplo.test/x)", "&lt;b&gt;", "&amp;").doesNotContain("**");
        assertThat(count("select count(*) from audit_log where organization_id = ? and action = 'announcement.create' and after_data::text not like '%Texto%' "
                + "and after_data::text not like '%exemplo.test%' and after_data::text like '%Reunião de pais%'", t.orgId())).isEqualTo(1);
        assertThat(titles("", admin)).containsExactly("Reunião de pais");
    }

    @Test
    @DisplayName("CA7: sendAt futuro agenda (201 SCHEDULED, sem destinatários nem e-mail, Agendado na lista); sendAt no passado ou inválido: 400 no campo")
    void schedule() throws Exception {
        Tenant t = tenant();
        AccountPrincipal admin = accountIn(t, Role.ADMINISTRATOR, t.unitId());
        UUID teacher = teacher(t, null), klass = klass(t, "Turma Agenda", teacher);
        enrolled(t, klass, teacher, "1990-01-01", "agenda-1@example.test", "ACTIVE");
        Map<String, Object> d = draft(klass, "EMAIL");
        d.put("sendAt", "2099-05-01T12:00:00-03:00");
        JsonNode created = ok(post("/announcements", d, admin), 201);
        assertThat(created.get("status").asText()).isEqualTo("SCHEDULED");
        assertThat(created.get("recipients").isNull()).isTrue();
        assertThat(count("select count(*) from email_message where organization_id = ?", t.orgId())).isZero();
        assertThat(adminJdbc().queryForObject("select scheduled_for = '2099-05-01T15:00:00Z'::timestamptz and sent_at is null from announcement", Boolean.class)).isTrue();
        assertThat(ok(send(org.springframework.http.HttpMethod.GET, "/announcements", admin), 200).get("items").get(0).get("status").asText()).isEqualTo("SCHEDULED");
        for (String bad : new String[] {"2020-01-01T12:00:00Z", "amanhã", "2099-05-01"}) {
            d.put("sendAt", bad);
            var r = post("/announcements", d, admin);
            assertThat(r.getStatus()).as(bad).isEqualTo(400);
            assertThat(r.getContentAsString()).contains("sendAt");
        }
        assertThat(count("select count(*) from announcement")).isEqualTo(1);
    }

    @Test
    @DisplayName("CA10: título, texto, tipo, canal, \"fixar até\", link e imagem inválidos dão 400 com o campo e nada é gravado; \"fixar até\" de hoje vale")
    void validation() throws Exception {
        Tenant t = tenant();
        AccountPrincipal admin = accountIn(t, Role.ADMINISTRATOR, t.unitId());
        UUID teacher = teacher(t, null), klass = klass(t, "Turma Validação", teacher);
        Map<String, Map<String, Object>> cases = new java.util.LinkedHashMap<>();
        cases.put("title", Map.of("title", ""));
        cases.put("title ", Map.of("title", "x".repeat(121)));
        cases.put("body", Map.of("body", "  "));
        cases.put("body ", Map.of("body", "x".repeat(2001)));
        cases.put("type", Map.of("type", "OUTRO"));
        cases.put("channels", Map.of("channels", List.of("PUSH")));
        cases.put("pinnedUntil", Map.of("pinnedUntil", "2020-01-01"));
        cases.put("pinnedUntil ", Map.of("pinnedUntil", "ontem"));
        cases.put("body  ", Map.of("body", "[x](javascript:alert(1))"));
        cases.put("imageName", Map.of("imageName", "announcement-" + UUID.randomUUID() + ".png"));
        cases.put("imageName ", Map.of("imageName", "logo.png"));
        cases.put("targetKind", Map.of("targetKind", "GALAXY"));
        for (var c : cases.entrySet()) {
            Map<String, Object> d = draft(klass);
            d.putAll(c.getValue());
            var r = post("/announcements", d, admin);
            assertThat(r.getStatus()).as(c.getKey() + c.getValue()).isEqualTo(400);
            assertThat(r.getContentAsString()).as(c.getKey()).contains(c.getKey().strip());
        }
        assertThat(count("select count(*) from announcement")).isZero();
        Map<String, Object> today = draft(klass);
        today.put("pinnedUntil", java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString());
        ok(post("/announcements", today, admin), 201);
        assertThat(adminJdbc().queryForObject("select pinned_until from announcement", java.sql.Date.class)).isNotNull();
    }

    @Test
    @DisplayName("CA11: Professor envia só para turma sua com a permissão ligada; Administrador não envia para ORGANIZATION nem fora do alcance; Coordenador envia para ORGANIZATION (sem unidade)")
    void permissions() throws Exception {
        Tenant t = tenant();
        UUID second = secondUnit(t);
        AccountPrincipal teacherAccount = accountIn(t, Role.TEACHER, t.unitId());
        UUID mine = teacher(t, teacherAccount), other = teacher(t, null);
        UUID own = klass(t, "Turma Minha", mine), foreign = klass(t, "Turma Alheia", other);
        UUID modality = modality(t);

        assertThat(post("/announcements", draft(foreign), teacherAccount).getStatus()).isEqualTo(403);
        for (Map<String, Object> bad : List.of(Map.<String, Object>of("targetKind", "UNIT", "unitId", t.unitId()),
                Map.<String, Object>of("targetKind", "MODALITY", "unitId", t.unitId(), "modalityId", modality), Map.<String, Object>of("targetKind", "ORGANIZATION"))) {
            Map<String, Object> d = draft(own);
            d.remove("classId");
            d.putAll(bad);
            assertThat(post("/announcements", d, teacherAccount).getStatus()).as(bad.toString()).isEqualTo(403);
        }
        ok(post("/announcements", draft(own), teacherAccount), 201);
        adminJdbc().update("update unit_settings set teacher_permissions = '{false,true,true,true,false}' where unit_id = ?", t.unitId());
        assertThat(post("/announcements", draft(own), teacherAccount).getStatus()).isEqualTo(403);

        AccountPrincipal admin = accountIn(t, Role.ADMINISTRATOR, t.unitId());
        assertThat(post("/announcements", Map.of("targetKind", "ORGANIZATION", "type", "GENERAL", "title", "T", "body", "B"), admin).getStatus()).isEqualTo(403);
        assertThat(post("/announcements", Map.of("targetKind", "UNIT", "unitId", second, "type", "GENERAL", "title", "T", "body", "B"), admin).getStatus()).isEqualTo(403);
        ok(post("/announcements", Map.of("targetKind", "UNIT", "unitId", t.unitId(), "type", "GENERAL", "title", "T", "body", "B"), admin), 201);
        ok(post("/announcements", Map.of("targetKind", "MODALITY", "unitId", t.unitId(), "modalityId", modality, "type", "GENERAL", "title", "T", "body", "B"), admin), 201);

        AccountPrincipal coordinator = account(t, Role.COORDINATOR);
        ok(post("/announcements", Map.of("targetKind", "ORGANIZATION", "type", "GENERAL", "title", "Todos", "body", "B"), coordinator), 201);
        assertThat(count("select count(*) from announcement where title = 'Todos' and unit_id is null and target_kind = 'ORGANIZATION'")).isEqualTo(1);
    }

    @Test
    @DisplayName("CA13: o 21º comunicado do dia da unidade é 409 e nada é gravado; outro dia, outra unidade e o limite próprio de ORGANIZATION passam; o dia é o do fuso da unidade")
    void dailyLimit() throws Exception {
        Tenant t = tenant();
        UUID second = secondUnit(t);
        AccountPrincipal coordinator = account(t, Role.COORDINATOR);
        // 10 às 12h e 10 às 22h de Brasília (01h UTC do dia seguinte): todos no dia local 10/03.
        for (int i = 0; i < 10; i++) {
            announcement(t, coordinator, "UNIT", t.unitId(), null, "GENERAL", "Dia " + i, "2099-03-10T15:00:00Z", null);
            announcement(t, coordinator, "UNIT", t.unitId(), null, "GENERAL", "Noite " + i, "2099-03-11T01:00:00Z", null);
        }
        Map<String, Object> unit = new HashMap<>(Map.of("targetKind", "UNIT", "unitId", t.unitId(), "type", "GENERAL", "title", "21", "body", "B"));
        unit.put("sendAt", "2099-03-10T21:00:00-03:00");
        var refused = post("/announcements", unit, coordinator);
        assertThat(refused.getStatus()).isEqualTo(409);
        assertThat(refused.getContentAsString()).contains("ANNOUNCEMENT_DAILY_LIMIT");
        assertThat(count("select count(*) from announcement")).isEqualTo(20);
        unit.put("sendAt", "2099-03-11T09:00:00-03:00");
        ok(post("/announcements", unit, coordinator), 201);
        unit.put("sendAt", "2099-03-10T21:00:00-03:00");
        unit.put("unitId", second);
        ok(post("/announcements", unit, coordinator), 201);

        for (int i = 0; i < 20; i++) announcement(t, coordinator, "ORGANIZATION", null, null, "GENERAL", "Org " + i, "2099-04-10T15:00:00Z", null);
        Map<String, Object> org = new HashMap<>(Map.of("targetKind", "ORGANIZATION", "type", "GENERAL", "title", "Org 21", "body", "B", "sendAt", "2099-04-10T15:00:00Z"));
        assertThat(post("/announcements", org, coordinator).getStatus()).isEqualTo(409);
        unit.put("unitId", t.unitId());
        unit.put("sendAt", "2099-04-10T15:00:00Z");
        ok(post("/announcements", unit, coordinator), 201);
    }

    @Test
    @DisplayName("CA14: PNG válido guarda a imagem (name e url) e o nome vale no comunicado; texto que não é imagem, vazio e acima de 5 MB: 400 no campo file; Professor sem permissão: 403; imagem da outra organização não vale")
    void image() throws Exception {
        Tenant a = tenant(), b = tenant();
        AccountPrincipal coordinator = account(a, Role.COORDINATOR);
        UUID teacher = teacher(a, null), klass = klass(a, "Turma Imagem", teacher);
        var upload = MockMvcRequestBuilders.multipart("/api/v1/announcements/image").file(new MockMultipartFile("file", "banner.png", "image/png", png(40, 20)));
        JsonNode image = ok(call(upload, coordinator).getResponse(), 200);
        assertThat(image.get("name").asText()).startsWith("announcement-").endsWith(".png");
        assertThat(image.get("url").asText()).startsWith("http");
        Map<String, Object> d = draft(klass);
        d.put("imageName", image.get("name").asText());
        ok(post("/announcements", d, coordinator), 201);
        assertThat(adminJdbc().queryForObject("select image_name from announcement", String.class)).isEqualTo(image.get("name").asText());
        assertThat(count("select count(*) from audit_log where organization_id = ? and action = 'announcement.image.upload'", a.orgId())).isEqualTo(1);

        for (byte[] bad : new byte[][] {"não é imagem".getBytes(), new byte[0], new byte[5 * 1024 * 1024 + 1]}) {
            var r = call(MockMvcRequestBuilders.multipart("/api/v1/announcements/image").file(new MockMultipartFile("file", "x.png", "image/png", bad)), coordinator).getResponse();
            assertThat(r.getStatus()).isEqualTo(400);
            assertThat(r.getContentAsString()).contains("file");
        }
        assertThat(call(MockMvcRequestBuilders.multipart("/api/v1/announcements/image"), coordinator).getResponse().getStatus()).isEqualTo(400);

        AccountPrincipal teacherAccount = accountIn(a, Role.TEACHER, a.unitId());
        adminJdbc().update("update unit_settings set teacher_permissions = '{false,true,true,true,false}' where unit_id = ?", a.unitId());
        assertThat(call(MockMvcRequestBuilders.multipart("/api/v1/announcements/image").file(new MockMultipartFile("file", "banner.png", "image/png", png(40, 20))),
                teacherAccount).getResponse().getStatus()).isEqualTo(403);

        AccountPrincipal other = account(b, Role.COORDINATOR);
        Map<String, Object> foreign = new HashMap<>(Map.of("targetKind", "UNIT", "unitId", b.unitId(), "type", "GENERAL", "title", "T", "body", "B", "imageName", image.get("name").asText()));
        var r = post("/announcements", foreign, other);
        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(r.getContentAsString()).contains("imageName");
    }
}

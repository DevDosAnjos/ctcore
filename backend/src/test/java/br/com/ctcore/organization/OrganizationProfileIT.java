package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.Scopes;
import br.com.ctcore.storage.FileStorage;
import br.com.ctcore.storage.S3FileStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrganizationProfileIT extends OrganizationBase {
    @Autowired ObjectMapper json;
    @Autowired S3FileStorage storage;

    JsonNode ok(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Tenant t, Role role) throws Exception {
        var response = call(request, who(t, role)).getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return json.readTree(response.getContentAsString());
    }

    JsonNode invalid(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Tenant t, String field) throws Exception {
        var response = call(request, who(t, Role.COORDINATOR)).getResponse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("VALIDATION_ERROR").contains("\"" + field + "\"");
        return json.readTree(response.getContentAsString());
    }

    List<Map<String, Object>> audit(Tenant t, String action) {
        return adminJdbc().queryForList("select actor_profile, actor_id, before_data::text as b, after_data::text as a, entity_id from audit_log "
                + "where organization_id = ? and action = ? order by occurred_at", t.orgId(), action);
    }


    String logoColumn(Tenant t) {
        return adminJdbc().queryForObject("select logo_url from organization where id = ?", String.class, t.orgId());
    }

    boolean exists(Tenant t, String file) {
        try (var ignored = Scopes.open(t.orgId(), null)) {
            storage.readUrl(file);
            return true;
        } catch (FileStorage.NotFound e) {
            return false;
        }
    }

    @Test
    @DisplayName("CA1 / CA13: Coordenador e Administrador leem; a resposta tem exatamente as chaves do contrato e logo nula sem logo")
    void read() throws Exception {
        var a = tenant();
        for (Role role : new Role[] {Role.COORDINATOR, Role.ADMINISTRATOR}) {
            var view = ok(get(), a, role);
            assertThat(view.fieldNames()).toIterable().containsExactlyInAnyOrder("name", "color", "holderDocument", "logoUrl");
            assertThat(view.path("name").asText()).isEqualTo("Synthetic " + a.orgId());
            assertThat(view.path("logoUrl").isNull()).isTrue();
        }
    }

    @Test
    @DisplayName("CA3 / CA6 / CA7: PATCH salva nome, cor e titular (só dígitos) e a leitura devolve; vazio salva nulo")
    void update() throws Exception {
        var a = tenant();
        var view = ok(patch("{\"name\":\"  Dojo Alfa \",\"color\":\"#1A56DB\",\"holderDocument\":\"11.222.333/0001-81\"}"), a, Role.COORDINATOR);
        assertThat(view.path("name").asText()).isEqualTo("Dojo Alfa");
        assertThat(view.path("color").asText()).isEqualTo("#1a56db");
        assertThat(view.path("holderDocument").asText()).isEqualTo("11222333000181");
        assertThat(ok(get(), a, Role.ADMINISTRATOR)).isEqualTo(view);
        var cleared = ok(patch("{\"name\":\"Dojo Alfa\",\"color\":\"\",\"holderDocument\":null}"), a, Role.COORDINATOR);
        assertThat(cleared.path("color").isNull()).isTrue();
        assertThat(cleared.path("holderDocument").isNull()).isTrue();
        assertThat(ok(patch("{\"name\":\"Dojo\",\"holderDocument\":\"529.982.247-25\"}"), a, Role.COORDINATOR).path("holderDocument").asText())
                .isEqualTo("52998224725");
    }

    @Test
    @DisplayName("CA5 / CA6 / CA7: valores inválidos dão erro com o campo apontado e nada é salvo")
    void validation() throws Exception {
        var a = tenant();
        var before = row(a);
        String longName = "x".repeat(121);
        invalid(patch("{\"name\":\"\"}"), a, "name");
        invalid(patch("{\"name\":\"   \"}"), a, "name");
        invalid(patch("{\"name\":\"" + longName + "\"}"), a, "name");
        invalid(patch("{\"name\":\"ok\",\"holderDocument\":\"529.982.247-26\"}"), a, "holderDocument");
        invalid(patch("{\"name\":\"ok\",\"holderDocument\":\"abc\"}"), a, "holderDocument");
        for (String color : new String[] {"#ffeb3b", "azul", "#fff", "1a56db"}) {
            invalid(patch("{\"name\":\"ok\",\"color\":\"" + color + "\"}"), a, "color");
        }
        assertThat(ok(patch("{\"name\":\"" + "x".repeat(120) + "\"}"), a, Role.COORDINATOR).path("name").asText()).hasSize(120);
        assertThat(row(a)).isNotEqualTo(before);
        var current = row(a);
        invalid(patch("{\"name\":\"\",\"color\":\"#1a56db\"}"), a, "name");
        assertThat(row(a)).isEqualTo(current);
    }

    @Test
    @DisplayName("T2: o banco também recusa cor e titular fora do formato")
    void databaseChecks() {
        var a = tenant();
        assertThatThrownBy(() -> adminJdbc().update("update organization set color = 'red' where id = ?", a.orgId())).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> adminJdbc().update("update organization set color = '#ABCDEF' where id = ?", a.orgId())).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> adminJdbc().update("update organization set holder_document = '123' where id = ?", a.orgId())).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("CA8 / CA11: PNG, JPEG e WebP de 512 x 512; a URL assinada baixa a imagem; a troca apaga o arquivo antigo")
    void logoReplace() throws Exception {
        var a = tenant();
        byte[] png = LogoImageTest.image("png", 512, 512);
        var first = ok(putLogo(png, "qualquer.txt"), a, Role.COORDINATOR);
        String firstFile = logoColumn(a);
        assertThat(firstFile).matches("logo-[0-9a-f-]{36}\\.png");
        String url = first.path("logoUrl").asText();
        assertThat(url).contains("/org/" + a.orgId() + "/" + firstFile).contains("X-Amz-Signature=");
        var download = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(png);

        ok(putLogo(LogoImageTest.image("jpg", 512, 512), "a.png"), a, Role.COORDINATOR);
        String secondFile = logoColumn(a);
        assertThat(secondFile).endsWith(".jpg");
        assertThat(exists(a, firstFile)).isFalse();
        assertThat(exists(a, secondFile)).isTrue();

        ok(putLogo(LogoImageTest.webp("L", 512, 512), "a.png"), a, Role.COORDINATOR);
        assertThat(logoColumn(a)).endsWith(".webp");
        assertThat(exists(a, secondFile)).isFalse();
        assertThat(logoColumn(a)).doesNotContain("http").doesNotContain("/");
    }

    @Test
    @DisplayName("CA9: texto .png, 511 x 512, 1024 x 1024, GIF, SVG, vazio e acima de 5 MB dão erro e a logo anterior permanece")
    void logoInvalid() throws Exception {
        var a = tenant();
        ok(putLogo(LogoImageTest.image("png", 512, 512), "a.png"), a, Role.COORDINATOR);
        String current = logoColumn(a);
        byte[][] bad = {"not an image".getBytes(), LogoImageTest.image("png", 511, 512), LogoImageTest.image("png", 1024, 1024),
            LogoImageTest.image("gif", 512, 512), "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(), new byte[0]};
        for (byte[] content : bad) invalid(putLogo(content, "logo.png"), a, "file");
        var huge = call(putLogo(new byte[5 * 1024 * 1024 + 1024], "big.png"), who(a, Role.COORDINATOR)).getResponse();
        assertThat(huge.getStatus()).isEqualTo(400);
        assertThat(logoColumn(a)).isEqualTo(current);
        assertThat(exists(a, current)).isTrue();
    }

    @Test
    @DisplayName("CA10: DELETE remove a logo e o arquivo; repetir não dá erro")
    void logoRemove() throws Exception {
        var a = tenant();
        ok(putLogo(LogoImageTest.image("png", 512, 512), "a.png"), a, Role.COORDINATOR);
        String file = logoColumn(a);
        assertThat(ok(deleteLogo(), a, Role.COORDINATOR).path("logoUrl").isNull()).isTrue();
        assertThat(logoColumn(a)).isNull();
        assertThat(exists(a, file)).isFalse();
        assertThat(ok(deleteLogo(), a, Role.COORDINATOR).path("logoUrl").isNull()).isTrue();
        assertThat(audit(a, "organization.logo.remove")).hasSize(1);
    }

    @Test
    @DisplayName("CA12: auditoria com antes e depois só dos campos alterados; sem mudança ou recusado não grava; sem URL assinada nem titular completo")
    void auditTrail() throws Exception {
        var a = tenant();
        var coordinator = who(a, Role.COORDINATOR);
        assertThat(call(patch("{\"name\":\"Novo\",\"color\":\"#1a56db\",\"holderDocument\":\"52998224725\"}"), coordinator).getResponse().getStatus()).isEqualTo(200);
        var rows = audit(a, "organization.update");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("actor_profile")).isEqualTo("COORDINATOR");
        assertThat(rows.get(0).get("actor_id")).isEqualTo(coordinator.accountId());
        assertThat(rows.get(0).get("entity_id")).isEqualTo(a.orgId().toString());
        assertThat(rows.get(0).get("a").toString()).contains("Novo", "#1a56db").doesNotContain("52998224725");
        assertThat(rows.get(0).get("b").toString()).contains("Synthetic");

        call(patch("{\"name\":\"Novo\",\"color\":\"#1a56db\",\"holderDocument\":\"52998224725\"}"), coordinator);
        call(patch("{\"name\":\"\"}"), coordinator);
        call(patch("{\"name\":\"Hack\"}"), who(a, Role.ADMINISTRATOR));
        call(patch("{\"name\":\"Só nome\",\"color\":\"#1a56db\",\"holderDocument\":\"52998224725\"}"), coordinator);
        rows = audit(a, "organization.update");
        assertThat(rows).hasSize(2);
        assertThat(rows).anySatisfy(r -> assertThat(r.get("a").toString()).contains("Só nome").doesNotContain("color"));

        call(putLogo(LogoImageTest.image("png", 512, 512), "a.png"), coordinator);
        call(putLogo(LogoImageTest.image("png", 512, 512), "a.png"), coordinator);
        assertThat(audit(a, "organization.logo.update")).hasSize(2);
        assertThat(audit(a, "organization.logo.update").toString()).doesNotContain("X-Amz").doesNotContain("http");
    }

    @Test
    @DisplayName("Isolamento: logo e dados de A não aparecem para B; a pasta do armazenamento é da própria organização")
    void logoIsolated() throws Exception {
        var a = tenant();
        var b = tenant();
        ok(putLogo(LogoImageTest.image("png", 512, 512), "a.png"), a, Role.COORDINATOR);
        assertThat(ok(get(), b, Role.COORDINATOR).path("logoUrl").isNull()).isTrue();
        assertThat(logoColumn(b)).isNull();
        assertThat(exists(b, logoColumn(a))).isFalse();
    }
}

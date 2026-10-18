package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** RF-ORG-04: leitura, lista e alteração das configurações da unidade, com o banco real. */
class UnitSettingsIT extends UnitBase {
    @Autowired Permissions permissions;

    private AccountPrincipal coordinator(Tenant t) {
        return who(t, Role.COORDINATOR);
    }

    private JsonNode get(Tenant t, UUID unit, AccountPrincipal who) throws Exception {
        var response = call(read(unit), who).getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return body(response);
    }

    @Test
    @DisplayName("CA1 / CA12: leitura traz os campos do contrato, com os padrões, sem a referência da conta e sem chaves a mais")
    void reads() throws Exception {
        var a = tenant();
        var unit = get(a, a.unitId(), coordinator(a));
        assertThat(fieldNames(unit)).containsExactlyInAnyOrder("id", "name", "shortName", "postalCode", "address", "city", "latitude", "longitude",
                "phone", "email", "whatsapp", "cnpj", "active", "settings", "version", "lastChange");
        var s = unit.path("settings");
        assertThat(fieldNames(s)).containsExactlyInAnyOrder("dueDays", "enrollmentFeeEnabled", "enrollmentFeeCents", "feesCents", "pixKey",
                "paymentAccount", "reminderDays", "teacherPermissions", "directoryEnabled", "trialLinkEnabled", "selfRegistrationLinkEnabled");
        assertThat(s.path("dueDays")).hasSize(4);
        assertThat(fieldNames(s.path("feesCents"))).containsExactlyInAnyOrder("cash", "pix", "bankSlip", "creditCard", "debitCard",
                "automaticDebit", "manualPix", "terminalCard", "transfer");
        assertThat(fieldNames(s.path("teacherPermissions"))).containsExactlyInAnyOrder("announcements", "agendaEdit", "studentContacts", "trialClasses");
        assertThat(s.path("paymentAccount").asText()).isEqualTo("NOT_CONNECTED");
        adminJdbc().update("update unit_settings set payment_account_reference = 'conta-secreta-123' where unit_id = ?", a.unitId());
        var response = call(read(a.unitId()), coordinator(a)).getResponse();
        assertThat(body(response).path("settings").path("paymentAccount").asText()).isEqualTo("CONNECTED");
        assertThat(response.getContentAsString()).doesNotContain("conta-secreta-123");
        assertThat(call(put(a.unitId(), payload()), coordinator(a)).getResponse().getStatus()).isEqualTo(200);
        assertThat(adminJdbc().queryForObject("select payment_account_reference from unit_settings where unit_id = ?", String.class, a.unitId()))
                .isEqualTo("conta-secreta-123");
    }

    private static List<String> fieldNames(JsonNode node) {
        var names = new java.util.ArrayList<String>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    @DisplayName("CA3: Coordenador vê as 3 unidades; Administrador só a sua; Professor 403")
    void mineLists() throws Exception {
        var a = tenant();
        UUID second = addUnit(a, "Segunda", "segunda-" + a.orgId(), "Rio");
        addUnit(a, "Terceira", "terceira-" + a.orgId(), null);
        var all = body(call(mine(), coordinator(a)).getResponse());
        assertThat(all).hasSize(3);
        assertThat(all.get(0).fieldNames().next()).isNotNull();
        var admin = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), a.orgId(), Role.ADMINISTRATOR, Set.of(second));
        var own = body(call(mine(), admin).getResponse());
        assertThat(own).hasSize(1);
        assertThat(own.get(0).path("id").asText()).isEqualTo(second.toString());
        assertThat(call(mine(), who(a, Role.TEACHER)).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("CA4 / CA6 / CA9 / CA10 / CA11 / CA13 / CA14: alteração válida volta nova na leitura; organizationId e shortName do corpo são ignorados")
    void updates() throws Exception {
        var a = tenant();
        var b = tenant();
        String shortBefore = unitRow(a.unitId()).get("short_name").toString();
        ObjectNode body = payload();
        body.put("name", "Academia Nova").put("phone", "(21) 99999-8888").put("cnpj", "11.222.333/0001-81")
                .put("organizationId", b.orgId().toString()).put("shortName", "invadido");
        ObjectNode s = (ObjectNode) body.path("settings");
        s.putArray("dueDays").add(15).add(5).add(1);
        s.put("enrollmentFeeEnabled", true).put("enrollmentFeeCents", 15000).put("pixKey", "Ana@Exemplo.com");
        ((ObjectNode) s.path("feesCents")).put("pix", 199).put("creditCard", 450);
        s.putArray("reminderDays").add(-3).add(0).add(3).add(10);
        ((ObjectNode) s.path("teacherPermissions")).put("announcements", false).put("agendaEdit", true).put("studentContacts", false)
                .put("trialClasses", true);
        s.put("directoryEnabled", true).put("trialLinkEnabled", false).put("selfRegistrationLinkEnabled", false);
        var response = call(put(a.unitId(), body), coordinator(a)).getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        var read = get(a, a.unitId(), coordinator(a));
        assertThat(read).isEqualTo(body(response));
        assertThat(read.path("name").asText()).isEqualTo("Academia Nova");
        assertThat(read.path("phone").asText()).isEqualTo("21999998888");
        assertThat(read.path("cnpj").asText()).isEqualTo("11222333000181");
        assertThat(read.path("shortName").asText()).isEqualTo(shortBefore);
        var rs = read.path("settings");
        assertThat(rs.path("dueDays").toString()).isEqualTo("[1,5,15]");
        assertThat(rs.path("enrollmentFeeCents").asInt()).isEqualTo(15000);
        assertThat(rs.path("pixKey").asText()).isEqualTo("ana@exemplo.com");
        assertThat(rs.path("feesCents").path("pix").asInt()).isEqualTo(199);
        assertThat(rs.path("feesCents").path("creditCard").asInt()).isEqualTo(450);
        assertThat(rs.path("reminderDays").toString()).isEqualTo("[-3,0,3,10]");
        assertThat(rs.path("directoryEnabled").asBoolean()).isTrue();
        assertThat(rs.path("trialLinkEnabled").asBoolean()).isFalse();
        assertThat(adminJdbc().queryForObject("select organization_id from unit where id = ?", UUID.class, a.unitId())).isEqualTo(a.orgId());
        assertThat(adminJdbc().queryForObject("select teacher_permissions::text from unit_settings where unit_id = ?", String.class, a.unitId()))
                .isEqualTo("{f,t,f,t}");
        // CA14: a tabela de permissões do Professor reflete a mudança.
        var teacher = who(a, Role.TEACHER);
        assertThat(permissions.grant(teacher, Permission.ANNOUNCEMENTS, a.unitId())).isEqualTo(Access.NONE);
        assertThat(permissions.grant(teacher, Permission.AGENDA_EDIT, a.unitId())).isNotEqualTo(Access.NONE);
        assertThat(permissions.grant(teacher, Permission.STUDENT_CONTACTS, a.unitId())).isEqualTo(Access.NONE);
        assertThat(permissions.grant(teacher, Permission.TRIAL_CLASSES, a.unitId())).isNotEqualTo(Access.NONE);
        // CA9: taxa de matrícula desligada grava 0.
        ObjectNode off = payload();
        ((ObjectNode) off.path("settings")).put("enrollmentFeeEnabled", false).put("enrollmentFeeCents", 500);
        assertThat(call(put(a.unitId(), off), coordinator(a)).getResponse().getStatus()).isEqualTo(200);
        assertThat(adminJdbc().queryForObject("select enrollment_fee_cents from unit_settings where unit_id = ?", Long.class, a.unitId())).isZero();
        // CA11: chave em branco apaga.
        assertThat(adminJdbc().queryForObject("select pix_key from unit_settings where unit_id = ?", String.class, a.unitId())).isNull();
        // CA13: lembretes vazios.
        ObjectNode none = payload();
        ((ObjectNode) none.path("settings")).putArray("reminderDays");
        assertThat(call(put(a.unitId(), none), coordinator(a)).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("CA6 / CA8 / CA10: corpo inválido responde 400 com o campo e nada é salvo nem auditado")
    void invalid() throws Exception {
        var a = tenant();
        String before = unitSnapshot(a);
        for (var change : List.<Map.Entry<String, java.util.function.Consumer<ObjectNode>>>of(
                Map.entry("name", n -> n.put("name", " ")),
                Map.entry("email", n -> n.put("email", "ruim")),
                Map.entry("cnpj", n -> n.put("cnpj", "11.222.333/0001-82")),
                Map.entry("longitude", n -> n.put("latitude", 1.0)),
                Map.entry("settings.dueDays", n -> ((ObjectNode) n.path("settings")).putArray("dueDays").add(0)),
                Map.entry("settings.feesCents.pix", n -> ((ObjectNode) n.path("settings").path("feesCents")).put("pix", -1)),
                Map.entry("settings.pixKey", n -> ((ObjectNode) n.path("settings")).put("pixKey", "abc")))) {
            ObjectNode body = payload();
            change.getValue().accept(body);
            var response = call(put(a.unitId(), body), coordinator(a)).getResponse();
            assertThat(response.getStatus()).as(change.getKey()).isEqualTo(400);
            assertThat(response.getContentAsString()).contains("VALIDATION_ERROR").contains("\"field\":\"" + change.getKey() + "\"");
        }
        assertThat(unitSnapshot(a)).isEqualTo(before);
        assertThat(audit(a, "unit.update")).isEmpty();
    }

    private String unitSnapshot(Tenant t) {
        return adminJdbc().queryForList("select u.*, s.* from unit u join unit_settings s on s.unit_id = u.id where u.id = ?", t.unitId()).toString();
    }

    @Test
    @DisplayName("CA7: CEP novo sem coordenadas usa a porta; CEP desconhecido deixa vazio; coordenadas enviadas valem; CEP igual mantém as atuais")
    void coordinates() throws Exception {
        var a = tenant();
        ObjectNode known = payload();
        known.put("postalCode", "01001-000");
        assertThat(call(put(a.unitId(), known), coordinator(a)).getResponse().getStatus()).isEqualTo(200);
        var row = adminJdbc().queryForMap("select postal_code, latitude::float8 as lat, longitude::float8 as lon from unit where id = ?", a.unitId());
        assertThat(row.get("postal_code")).isEqualTo("01001000");
        assertThat((double) row.get("lat")).isEqualTo(-23.5505);
        assertThat((double) row.get("lon")).isEqualTo(-46.6333);
        // CEP igual e sem coordenadas: mantém.
        assertThat(call(put(a.unitId(), known), coordinator(a)).getResponse().getStatus()).isEqualTo(200);
        assertThat((double) adminJdbc().queryForObject("select latitude::float8 from unit where id = ?", Double.class, a.unitId())).isEqualTo(-23.5505);
        // Coordenadas enviadas valem.
        ObjectNode manual = payload();
        manual.put("postalCode", "01001-000").put("latitude", -1.5).put("longitude", 2.5);
        assertThat(call(put(a.unitId(), manual), coordinator(a)).getResponse().getStatus()).isEqualTo(200);
        assertThat((double) adminJdbc().queryForObject("select longitude::float8 from unit where id = ?", Double.class, a.unitId())).isEqualTo(2.5);
        // CEP desconhecido: vazio, sem erro.
        ObjectNode unknown = payload();
        unknown.put("postalCode", "99999999");
        assertThat(call(put(a.unitId(), unknown), coordinator(a)).getResponse().getStatus()).isEqualTo(200);
        assertThat(adminJdbc().queryForObject("select latitude from unit where id = ?", Object.class, a.unitId())).isNull();
    }

    @Test
    @DisplayName("CA15: auditoria unit.update só com os campos mudados; Pix e CNPJ sem valor; PUT idêntico não grava")
    void audited() throws Exception {
        var a = tenant();
        assertThat(call(put(a.unitId(), payload()), coordinator(a)).getResponse().getStatus()).isEqualTo(200); // baseline: o nome muda
        ObjectNode body = payload();
        body.put("phone", "21999998888");
        ((ObjectNode) body.path("settings")).putArray("dueDays").add(7);
        assertThat(call(put(a.unitId(), body), coordinator(a)).getResponse().getStatus()).isEqualTo(200);
        var rows = audit(a, "unit.update");
        assertThat(rows).hasSize(2);
        var row = rows.stream().filter(r -> r.get("a").toString().contains("21999998888")).findFirst().orElseThrow();
        assertThat(row.get("actor_profile")).isEqualTo("COORDINATOR");
        assertThat(row.get("a").toString()).contains("dueDays").doesNotContain("name");
        assertThat(row.get("b").toString().replace(" ", "")).contains("dueDays").contains("[1,5,10,15]").doesNotContain("21999998888");
        assertThat(call(put(a.unitId(), body), coordinator(a)).getResponse().getStatus()).isEqualTo(200);
        assertThat(audit(a, "unit.update")).hasSize(2);
        ObjectNode secret = payload();
        secret.put("cnpj", "11.222.333/0001-81");
        ((ObjectNode) secret.path("settings")).put("pixKey", "529.982.247-25");
        ((ObjectNode) secret.path("settings")).putArray("dueDays").add(7);
        assertThat(call(put(a.unitId(), secret), coordinator(a)).getResponse().getStatus()).isEqualTo(200);
        var all = audit(a, "unit.update").toString();
        assertThat(all).contains("cnpj").contains("pixKey").doesNotContain("11222333000181").doesNotContain("52998224725");
    }

    @Test
    @DisplayName("CA15: falha ao auditar desfaz a alteração (tudo ou nada)")
    void allOrNothing() throws Exception {
        var a = tenant();
        String before = unitSnapshot(a);
        var db = adminJdbc();
        db.execute("create function fail_audit_unit() returns trigger language plpgsql as $$ begin raise exception 'audit down'; end $$");
        db.execute("create trigger fail_audit_unit before insert on audit_log for each row execute function fail_audit_unit()");
        try {
            ObjectNode body = payload();
            body.put("name", "Outro nome");
            ((ObjectNode) body.path("settings")).putArray("dueDays").add(9);
            assertThat(call(put(a.unitId(), body), coordinator(a)).getResponse().getStatus()).isEqualTo(500);
        } finally {
            db.execute("drop trigger fail_audit_unit on audit_log");
            db.execute("drop function fail_audit_unit()");
        }
        assertThat(unitSnapshot(a)).isEqualTo(before);
    }

    @Test
    @DisplayName("T1: o banco recusa CNPJ fora do padrão de 14 dígitos")
    void databaseCheck() {
        var a = tenant();
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> adminJdbc().update("update unit set cnpj = '11.222.333/0001-81' where id = ?", a.unitId()));
        assertThat(adminJdbc().update("update unit set cnpj = '11222333000181' where id = ?", a.unitId())).isEqualTo(1);
    }
}

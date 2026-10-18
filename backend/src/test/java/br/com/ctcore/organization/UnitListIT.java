package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.Scopes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UnitListIT extends UnitBase {
    @Autowired UnitShortNames shortNames;

    @Test
    @DisplayName("CA1 / CA2 / CA3: lista as unidades com os campos do contrato, só Administradores ativos da unidade e 0 aluno")
    void listUnits() throws Exception {
        var a = tenant();
        var second = addUnit(a, "Zeta", "zeta-" + a.orgId(), "Niterói");
        addAdministrator(a, "Ana Admin", "ACTIVE", a.unitId(), second);
        addAdministrator(a, "Bruno Admin", "ACTIVE", a.unitId());
        addAdministrator(a, "Carla Encerrada", "ENDED", a.unitId());
        addAdministrator(a, "Dani Pendente", "PENDING", a.unitId());
        var response = call(list(), who(a, Role.COORDINATOR)).getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        var units = body(response);
        assertThat(units).hasSize(2);
        var first = units.get(0);
        assertThat(first.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "name", "shortName", "shortNameAdjusted", "city",
                "activeStudents", "administrators", "active");
        assertThat(first.path("name").asText()).isEqualTo("Unit");
        assertThat(first.path("active").asBoolean()).isTrue();
        assertThat(first.path("shortNameAdjusted").asBoolean()).isFalse();
        assertThat(first.path("administrators").findValuesAsText("name")).containsExactly("Ana Admin", "Bruno Admin");
        assertThat(units.get(1).path("city").asText()).isEqualTo("Niterói");
        assertThat(units.get(1).path("administrators").findValuesAsText("name")).containsExactly("Ana Admin");
        assertThat(units.findValues("activeStudents")).allMatch(n -> n.asInt() == 0);
        var empty = tenant();
        assertThat(body(call(list(), who(empty, Role.COORDINATOR)).getResponse()).get(0).path("administrators")).isEmpty();
    }

    @Test
    @DisplayName("CA8 / CA9 / CA12: ajusta uma vez, grava a auditoria com antes e depois e recusa o segundo ajuste sem mudar nada")
    void adjustOnce() throws Exception {
        var a = tenant();
        var coordinator = who(a, Role.COORDINATOR);
        var response = call(adjust(a.unitId(), "novo-nome-" + a.orgId()), coordinator).getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        var view = body(response);
        assertThat(view.path("shortName").asText()).isEqualTo("novo-nome-" + a.orgId());
        assertThat(view.path("shortNameAdjusted").asBoolean()).isTrue();
        var rows = audit(a, "unit.short_name.update");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("actor_profile")).isEqualTo("COORDINATOR");
        assertThat(rows.get(0).get("entity_id")).isEqualTo(a.unitId().toString());
        assertThat(rows.get(0).get("b").toString()).contains(a.orgId().toString()).doesNotContain("novo-nome");
        assertThat(rows.get(0).get("a").toString()).contains("novo-nome-" + a.orgId());
        assertThat(rows.get(0).toString().toLowerCase()).doesNotContain("password", "token", "senha");
        var before = unitRow(a.unitId());
        assertThat(call(adjust(a.unitId(), "outro-" + a.orgId()), coordinator).getResponse().getStatus()).isEqualTo(409);
        assertThat(unitRow(a.unitId())).isEqualTo(before);
        assertThat(audit(a, "unit.short_name.update")).hasSize(1);
    }

    @Test
    @DisplayName("CA10: nome de outra unidade, nome antigo (da organização ou de outra) e formato inválido são recusados e nada muda")
    void conflictsAndFormat() throws Exception {
        var a = tenant();
        var b = tenant();
        var coordinator = who(a, Role.COORDINATOR);
        var other = addUnit(a, "Outra", "outra-" + a.orgId(), null);
        var otherOfB = addUnit(b, "Outra B", "outra-b-" + b.orgId(), null);
        // outra unidade (da mesma organização e de outra) usa o nome atual
        assertThat(call(adjust(a.unitId(), "outra-" + a.orgId()), coordinator).getResponse().getStatus()).isEqualTo(409);
        assertThat(call(adjust(a.unitId(), "outra-b-" + b.orgId()), coordinator).getResponse().getStatus()).isEqualTo(409);
        // o próprio nome atual não conta como ajuste
        assertThat(call(adjust(a.unitId(), a.orgId().toString()), coordinator).getResponse().getStatus()).isEqualTo(409);
        // nome antigo de outra organização
        assertThat(call(adjust(otherOfB, "b-novo-" + b.orgId()), who(b, Role.COORDINATOR)).getResponse().getStatus()).isEqualTo(200);
        assertThat(call(adjust(a.unitId(), "outra-b-" + b.orgId()), coordinator).getResponse().getStatus()).isEqualTo(409);
        // nome antigo da própria organização
        assertThat(call(adjust(other, "o-novo-" + a.orgId()), coordinator).getResponse().getStatus()).isEqualTo(200);
        assertThat(call(adjust(a.unitId(), "outra-" + a.orgId()), coordinator).getResponse().getStatus()).isEqualTo(409);
        assertThat(unitRow(a.unitId()).get("short_name_adjusted")).isEqualTo(false);
        for (String bad : new String[] {null, "", "Maiuscula", "com espaco", "x".repeat(61)}) {
            var response = call(adjust(a.unitId(), bad), coordinator).getResponse();
            assertThat(response.getStatus()).as(String.valueOf(bad)).isEqualTo(400);
            assertThat(response.getContentAsString()).contains("VALIDATION_ERROR").contains("shortName");
        }
        assertThat(unitRow(a.unitId()).get("short_name")).isEqualTo(a.orgId().toString());
    }

    @Test
    @DisplayName("CA11: nome antigo resolve para o novo; o novo resolve para si; desconhecido devolve vazio")
    void resolve() throws Exception {
        var a = tenant();
        String old = a.orgId().toString();
        String next = "novo-" + old;
        assertThat(call(adjust(a.unitId(), next), who(a, Role.COORDINATOR)).getResponse().getStatus()).isEqualTo(200);
        try (var ignored = Scopes.open(a.orgId(), null)) {
            assertThat(shortNames.resolve(old)).contains(next);
            assertThat(shortNames.resolve(next)).contains(next);
            assertThat(shortNames.resolve("desconhecido-" + old)).isEmpty();
            assertThat(shortNames.resolve(null)).isEmpty();
        }
    }
}

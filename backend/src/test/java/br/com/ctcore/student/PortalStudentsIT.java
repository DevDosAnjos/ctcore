package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RN-RSP-03, RN-RSP-04: cartões do portal, com banco real. */
class PortalStudentsIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        adminJdbc().update("delete from student_guardian");
        adminJdbc().update("delete from guardian");
        adminJdbc().update("delete from student");
        String mine = "(select id from account where email like '%.example')";
        adminJdbc().update("delete from account_invite where account_id in " + mine);
        adminJdbc().update("delete from account_session where account_id in " + mine);
        adminJdbc().update("delete from account_link_unit where link_id in (select id from account_link where account_id in " + mine + ")");
        adminJdbc().update("delete from account_link where account_id in " + mine);
        adminJdbc().update("delete from account where email like '%.example'");
    }

    private UUID account(String email) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", id, email);
        return id;
    }

    private UUID student(Tenant t, UUID unit, String name, String birth, UUID account) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, postal_code, street, number, district, city, state, account_id) "
                + "values (?, ?, ?, ?, ?, ?::date, '01001000', 'Rua', '1', 'Bairro', 'Cidade', 'SP', ?)", id, t.orgId(), unit, name, name.toLowerCase(), birth, account);
        return id;
    }

    private UUID guardian(Tenant t, UUID account, String cpf, UUID... students) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into guardian (id, organization_id, account_id, name, cpf, phone) values (?, ?, ?, 'Responsável', ?, '11987654321')", id, t.orgId(), account, cpf);
        for (UUID s : students) {
            adminJdbc().update("insert into student_guardian (organization_id, student_id, guardian_id, financial) values (?, ?, ?, true)", t.orgId(), s, id);
        }
        return id;
    }

    private AccountPrincipal portal(Tenant t, UUID account, Role role) {
        return new AccountPrincipal(account, UUID.randomUUID(), UUID.randomUUID(), t.orgId(), role, Set.of());
    }

    private org.springframework.mock.web.MockHttpServletResponse get(AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/portal/students"), who).getResponse();
    }

    @Test
    @DisplayName("RN-LGP-03: outra organização: o responsável só vê os alunos da organização do vínculo, e quem não tem aluno nela recebe lista vazia")
    void otherOrganization() throws Exception {
        Tenant a = tenant();
        Tenant b = tenant();
        UUID acc = account("responsavel@portal.example"), alone = account("sozinho@portal.example");
        UUID inA = student(a, a.unitId(), "Filho da A", "2015-01-01", null), inB = student(b, b.unitId(), "Filho da B", "2016-01-01", null);
        guardian(a, acc, "52998224725", inA);
        guardian(b, acc, "11144477735", inB);

        var cardsA = json.readTree(get(portal(a, acc, Role.GUARDIAN)).getContentAsString());
        assertThat(cardsA).hasSize(1);
        assertThat(cardsA.get(0).get("name").asText()).isEqualTo("Filho da A");
        var cardsB = json.readTree(get(portal(b, acc, Role.GUARDIAN)).getContentAsString());
        assertThat(cardsB).hasSize(1);
        assertThat(cardsB.get(0).get("name").asText()).isEqualTo("Filho da B");
        var none = get(portal(a, alone, Role.GUARDIAN));
        assertThat(none.getStatus()).isEqualTo(200);
        assertThat(json.readTree(none.getContentAsString())).isEmpty();
    }

    @Test
    @DisplayName("RN-LGP-03: CA17: três cartões (dois filhos em unidades diferentes e o próprio com self), só id, nome, foto e self; outro responsável não vê; gestão 403; sem sessão 401")
    void cards() throws Exception {
        Tenant t = tenant();
        UUID other = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'Outra', ?)", other, t.orgId(), "u" + other.toString().substring(0, 12));
        UUID mine = account("mae@portal.example"), stranger = account("outra@portal.example");
        UUID a = student(t, t.unitId(), "Filho Bento", "2015-01-01", null), b = student(t, other, "Filho Ana", "2017-01-01", null);
        UUID self = student(t, t.unitId(), "Mãe Aluna", "1985-01-01", mine);
        UUID unrelated = student(t, t.unitId(), "Outro Menor", "2016-01-01", null);
        guardian(t, mine, "52998224725", a, b);
        guardian(t, stranger, "11144477735", unrelated);

        var response = get(portal(t, mine, Role.GUARDIAN));
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode cards = json.readTree(response.getContentAsString());
        assertThat(cards).hasSize(3);
        assertThat(cards.get(0).get("id").asText()).isEqualTo(self.toString());
        assertThat(cards.get(0).get("self").asBoolean()).isTrue();
        assertThat(cards.get(1).get("name").asText()).isEqualTo("Filho Ana");
        assertThat(cards.get(2).get("name").asText()).isEqualTo("Filho Bento");
        assertThat(cards.get(1).get("self").asBoolean()).isFalse();
        for (JsonNode c : cards) assertThat(c.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "name", "photoUrl", "self");
        assertThat(json.readTree(get(portal(t, mine, Role.STUDENT)).getContentAsString())).hasSize(3);

        JsonNode theirs = json.readTree(get(portal(t, stranger, Role.GUARDIAN)).getContentAsString());
        assertThat(theirs).hasSize(1);
        assertThat(theirs.get(0).get("id").asText()).isEqualTo(unrelated.toString());
        assertThat(json.readTree(get(portal(t, account("nada@portal.example"), Role.GUARDIAN)).getContentAsString())).isEmpty();
        assertThat(json.readTree(get(portal(tenant(), mine, Role.GUARDIAN)).getContentAsString())).isEmpty();

        for (Role role : new Role[]{Role.COORDINATOR, Role.ADMINISTRATOR, Role.TEACHER}) {
            assertThat(get(portal(t, mine, role)).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(get(null).getStatus()).isEqualTo(401);
    }
}

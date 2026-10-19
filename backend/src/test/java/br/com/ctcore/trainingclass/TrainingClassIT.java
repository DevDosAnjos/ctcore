package br.com.ctcore.trainingclass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.modality.ModalityUsage;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import br.com.ctcore.organization.UnitDeactivationBlockers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-TUR-01: lista de turmas, filtros, regras da tabela, perfis, alcance, isolamento (API e RLS) e contagens reais, com banco real. */
class TrainingClassIT extends OrganizationBase {
    @Autowired ObjectMapper json;
    @Autowired OrganizationTransactions transactions;
    @Autowired ModalityUsage usage;
    @Autowired UnitDeactivationBlockers blockers;

    @AfterEach
    void clean() {
        adminJdbc().update("delete from training_class_assistant");
        adminJdbc().update("delete from training_class_schedule");
        adminJdbc().update("delete from lesson");
        adminJdbc().update("delete from training_class");
        adminJdbc().update("delete from teacher_unit");
        adminJdbc().update("delete from teacher");
    }

    private MockHttpServletResponse get(String query, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/classes" + query), who).getResponse();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private static List<String> names(JsonNode items) {
        return StreamSupport.stream(items.spliterator(), false).map(i -> i.get("name").asText()).toList();
    }

    private UUID modality(String name) {
        return adminJdbc().queryForObject("select id from modality where name = ?", UUID.class, name);
    }

    private UUID unit(Tenant t, String name) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, ?, ?)", id, t.orgId(), name, "u" + id.toString().substring(0, 12));
        return id;
    }

    private UUID teacher(Tenant t, String name, UUID unit) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into teacher (id, organization_id, name, name_key) values (?, ?, ?, ?)", id, t.orgId(), name, Modalities.normalize(name));
        adminJdbc().update("insert into teacher_unit (organization_id, teacher_id, unit_id) values (?, ?, ?)", t.orgId(), id, unit);
        return id;
    }

    /** A organização nasce com as faixas padrão (Infantil, Juvenil, Adulto). */
    private UUID ageRange(Tenant t, String name) {
        return adminJdbc().queryForObject("select id from age_range where organization_id = ? and name = ?", UUID.class, t.orgId(), name);
    }

    private UUID klass(Tenant t, UUID unit, String name, UUID modality, UUID ageRange, Integer capacity, UUID head, String status) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into training_class (id, organization_id, unit_id, name, name_key, modality_id, age_range_id, capacity, head_teacher_id, status) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, t.orgId(), unit, name, Modalities.normalize(name), modality, ageRange, capacity, head, status);
        return id;
    }

    private void slot(Tenant t, UUID klass, int weekday, String from, String to) {
        adminJdbc().update("insert into training_class_schedule (organization_id, class_id, weekday, starts_at, ends_at) values (?, ?, ?, ?::time, ?::time)",
                t.orgId(), klass, weekday, from, to);
    }

    private void assistant(Tenant t, UUID klass, UUID teacher) {
        adminJdbc().update("insert into training_class_assistant (organization_id, class_id, teacher_id) values (?, ?, ?)", t.orgId(), klass, teacher);
    }

    @Test
    @DisplayName("CA1: Coordenador lista ordenado por nome com unidade, modalidade, faixa, horários ordenados, titular, auxiliares e envelope")
    void list() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul");
        UUID ana = teacher(t, "Ana", t.unitId());
        UUID beto = teacher(t, "Beto", t.unitId());
        UUID adult = ageRange(t, "Adulto");
        UUID zulu = klass(t, t.unitId(), "Zulu", modality("Judô"), null, null, ana, "ACTIVE");
        UUID alfa = klass(t, second, "Alfa", modality("Jiu-Jitsu"), adult, 30, ana, "ACTIVE");
        slot(t, alfa, 3, "19:00", "20:00");
        slot(t, alfa, 1, "18:00", "19:30");
        assistant(t, alfa, beto);
        var c = who(t, Role.COORDINATOR);
        var r = get("", c);
        assertThat(r.getStatus()).isEqualTo(200);
        var data = body(r);
        assertThat(data.get("page").asInt()).isEqualTo(1);
        assertThat(data.get("size").asInt()).isEqualTo(25);
        assertThat(data.get("total").asInt()).isEqualTo(2);
        assertThat(names(data.get("items"))).containsExactly("Alfa", "Zulu");
        var first = data.get("items").get(0);
        assertThat(first.get("unit").get("name").asText()).isEqualTo("Zona Sul");
        assertThat(first.get("modality").get("name").asText()).isEqualTo("Jiu-Jitsu");
        assertThat(first.get("ageRange").get("name").asText()).isEqualTo("Adulto");
        assertThat(first.get("ageRange").get("maxAge").isNull()).isTrue();
        assertThat(first.get("schedule").findValuesAsText("startsAt")).containsExactly("18:00", "19:00");
        assertThat(first.get("schedule").findValuesAsText("endsAt")).containsExactly("19:30", "20:00");
        assertThat(first.get("schedule").findValues("weekday").stream().map(JsonNode::asInt)).containsExactly(1, 3);
        assertThat(first.get("head").get("name").asText()).isEqualTo("Ana");
        assertThat(first.get("assistants").findValuesAsText("name")).containsExactly("Beto");
        assertThat(first.get("activeStudents").asInt()).isZero();
        assertThat(first.get("capacity").asInt()).isEqualTo(30);
        assertThat(first.get("status").asText()).isEqualTo("ACTIVE");
        var last = data.get("items").get(1);
        assertThat(last.get("ageRange").isNull()).isTrue();
        assertThat(last.get("capacity").isNull()).isTrue();
        assertThat(last.get("schedule")).isEmpty();
        assertThat(body(get("?sort=name,desc", c)).get("items").get(0).get("id").asText()).isEqualTo(zulu.toString());
        assertThat(body(get("?sort=name", c)).get("items").get(0).get("id").asText()).isEqualTo(alfa.toString());
        for (String bad : new String[] {"?size=10", "?page=0", "?sort=email"}) {
            assertThat(get(bad, c).getStatus()).as(bad).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("CA2: filtros q, modalidade, professor (titular e auxiliar), faixa, dia, unidade e situação, sozinhos e combinados; valores inválidos 400")
    void filters() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul");
        UUID ana = teacher(t, "Ana", t.unitId());
        UUID beto = teacher(t, "Beto", t.unitId());
        UUID kids = ageRange(t, "Infantil");
        UUID jj = modality("Jiu-Jitsu");
        UUID adv = klass(t, t.unitId(), "Avançado 100%", jj, null, null, ana, "ACTIVE");
        UUID kid = klass(t, second, "Pequenos", modality("Judô"), kids, 20, beto, "INACTIVE");
        slot(t, adv, 2, "19:00", "20:00");
        slot(t, kid, 5, "10:00", "11:00");
        assistant(t, adv, beto);
        var c = who(t, Role.COORDINATOR);
        assertThat(names(body(get("?q=avancado", c)).get("items"))).containsExactly("Avançado 100%");
        assertThat(names(body(get("?q=AVANÇADO", c)).get("items"))).containsExactly("Avançado 100%");
        assertThat(names(body(get("?q=%", c)).get("items"))).containsExactly("Avançado 100%");
        assertThat(body(get("?q=_", c)).get("total").asInt()).isZero();
        assertThat(names(body(get("?modalityId=" + jj, c)).get("items"))).containsExactly("Avançado 100%");
        assertThat(names(body(get("?teacherId=" + ana, c)).get("items"))).containsExactly("Avançado 100%");
        assertThat(names(body(get("?teacherId=" + beto, c)).get("items"))).containsExactly("Avançado 100%", "Pequenos");
        assertThat(names(body(get("?ageRangeId=" + kids, c)).get("items"))).containsExactly("Pequenos");
        assertThat(names(body(get("?weekday=2", c)).get("items"))).containsExactly("Avançado 100%");
        assertThat(names(body(get("?weekday=5", c)).get("items"))).containsExactly("Pequenos");
        assertThat(body(get("?weekday=7", c)).get("total").asInt()).isZero();
        assertThat(names(body(get("?unitId=" + second, c)).get("items"))).containsExactly("Pequenos");
        assertThat(names(body(get("?status=INACTIVE", c)).get("items"))).containsExactly("Pequenos");
        assertThat(names(body(get("?status=active", c)).get("items"))).containsExactly("Avançado 100%");
        assertThat(body(get("?teacherId=" + beto + "&weekday=5&status=INACTIVE&unitId=" + second + "&ageRangeId=" + kids, c)).get("total").asInt()).isEqualTo(1);
        assertThat(body(get("?teacherId=" + beto + "&weekday=2&status=INACTIVE", c)).get("total").asInt()).isZero();
        assertThat(get("?weekday=8", c).getStatus()).isEqualTo(400);
        assertThat(get("?weekday=0", c).getStatus()).isEqualTo(400);
        assertThat(get("?status=XYZ", c).getStatus()).isEqualTo(400);
        assertThat(kid).isNotNull();
    }

    @Test
    @DisplayName("CA3: overCapacity só com mais alunos que a capacidade; a tabela recusa capacidade 0 e 201")
    void capacity() {
        assertThat(TrainingClasses.overCapacity(31, 30)).isTrue();
        assertThat(TrainingClasses.overCapacity(30, 30)).isFalse();
        assertThat(TrainingClasses.overCapacity(31, null)).isFalse();
        var t = tenant();
        UUID head = teacher(t, "Ana", t.unitId());
        UUID jj = modality("Jiu-Jitsu");
        assertThatThrownBy(() -> klass(t, t.unitId(), "Zero", jj, null, 0, head, "ACTIVE")).hasMessageContaining("check");
        assertThatThrownBy(() -> klass(t, t.unitId(), "Muitos", jj, null, 201, head, "ACTIVE")).hasMessageContaining("check");
        klass(t, t.unitId(), "Cheia", jj, null, 200, head, "ACTIVE");
        klass(t, t.unitId(), "Uma", jj, null, 1, head, "ACTIVE");
    }

    @Test
    @DisplayName("CA4: nenhuma tabela, coluna, campo da resposta ou texto da tela menciona presença ou frequência")
    void noAttendance() throws Exception {
        var t = tenant();
        klass(t, t.unitId(), "Turma", modality("Judô"), null, null, teacher(t, "Ana", t.unitId()), "ACTIVE");
        var names = adminJdbc().queryForList("select table_name || '.' || coalesce(column_name, '') from information_schema.columns "
                + "where table_schema = 'public' and table_name like 'training_class%'", String.class);
        assertThat(names).isNotEmpty().noneMatch(n -> n.matches("(?i).*(presen|attend|frequen|check_?in).*"));
        assertThat(adminJdbc().queryForList("select table_name from information_schema.tables where table_schema = 'public'", String.class))
                .noneMatch(n -> n.matches("(?i).*(presen|attend|frequen).*"));
        assertThat(get("", who(t, Role.COORDINATOR)).getContentAsString()).doesNotContainIgnoringCase("presen").doesNotContainIgnoringCase("attend")
                .doesNotContainIgnoringCase("frequen").doesNotContain("groupLink");
    }

    @Test
    @DisplayName("CA5: nome único entre ativas na unidade (sem caixa nem acento); término depois do início; um horário por dia; titular obrigatório")
    void tableRules() {
        var t = tenant();
        UUID second = unit(t, "Zona Sul");
        UUID head = teacher(t, "Ana", t.unitId());
        UUID jj = modality("Jiu-Jitsu");
        UUID first = klass(t, t.unitId(), "Avançado", jj, null, null, head, "ACTIVE");
        assertThatThrownBy(() -> klass(t, t.unitId(), "AVANCADO", jj, null, null, head, "ACTIVE")).hasMessageContaining("training_class_unit_name");
        klass(t, second, "Avançado", jj, null, null, head, "ACTIVE");
        klass(t, t.unitId(), "Avançado 2", jj, null, null, head, "ACTIVE");
        adminJdbc().update("update training_class set status = 'INACTIVE' where id = ?", first);
        klass(t, t.unitId(), "Avançado", jj, null, null, head, "ACTIVE");
        klass(t, t.unitId(), "Avançado", jj, null, null, head, "INACTIVE");
        slot(t, first, 1, "19:00", "20:00");
        assertThatThrownBy(() -> slot(t, first, 1, "20:00", "21:00")).hasMessageContaining("training_class_schedule_pkey");
        assertThatThrownBy(() -> slot(t, first, 2, "19:00", "19:00")).hasMessageContaining("check");
        assertThatThrownBy(() -> slot(t, first, 3, "20:00", "19:00")).hasMessageContaining("check");
        assertThatThrownBy(() -> slot(t, first, 8, "19:00", "20:00")).hasMessageContaining("check");
        assertThatThrownBy(() -> klass(t, t.unitId(), "Sem titular", jj, null, null, null, "ACTIVE")).hasMessageContaining("head_teacher_id");
    }

    @Test
    @DisplayName("CA6: Administrador só vê turmas das unidades dele; unitId alheio vazio; demais perfis 403; anônimo 401")
    void permissions() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul");
        UUID head = teacher(t, "Ana", t.unitId());
        klass(t, t.unitId(), "Minha", modality("Judô"), null, null, head, "ACTIVE");
        klass(t, second, "Alheia", modality("Judô"), null, null, head, "ACTIVE");
        var admin = who(t, Role.ADMINISTRATOR);
        assertThat(names(body(get("", admin)).get("items"))).containsExactly("Minha");
        assertThat(body(get("?unitId=" + second, admin)).get("items")).isEmpty();
        assertThat(body(get("?unitId=" + t.unitId(), admin)).get("total").asInt()).isEqualTo(1);
        assertThat(body(get("", who(t, Role.COORDINATOR))).get("total").asInt()).isEqualTo(2);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(get("", who(t, role)).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(get("", null).getStatus()).isEqualTo(401);
        var noUnits = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of());
        assertThat(body(get("", noUnits)).get("total").asInt()).isZero();
    }

    @Test
    @DisplayName("CA7: cada organização só vê as suas turmas de mesmo nome; RLS com ctcore_app nas três tabelas")
    void isolation() throws Exception {
        var a = tenant();
        var b = tenant();
        UUID ca = klass(a, a.unitId(), "Avançado", modality("Judô"), null, null, teacher(a, "Ana", a.unitId()), "ACTIVE");
        UUID headB = teacher(b, "Ana", b.unitId());
        UUID cb = klass(b, b.unitId(), "Avançado", modality("Judô"), null, null, headB, "ACTIVE");
        slot(a, ca, 1, "19:00", "20:00");
        slot(b, cb, 1, "19:00", "20:00");
        assistant(a, ca, teacher(a, "Caio", a.unitId()));
        assistant(b, cb, teacher(b, "Beto", b.unitId()));
        var items = body(get("", who(a, Role.COORDINATOR))).get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("id").asText()).isEqualTo(ca.toString());
        assertThat(body(get("?unitId=" + b.unitId(), who(a, Role.COORDINATOR))).get("items")).isEmpty();
        assertThat(body(get("?teacherId=" + headB, who(a, Role.COORDINATOR))).get("items")).isEmpty();

        for (String table : new String[] {"training_class", "training_class_schedule", "training_class_assistant"}) {
            try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
                app.setAutoCommit(false);
                sql.execute("select set_config('app.organization_id', '', true)");
                try (var rows = sql.executeQuery("select 1 from " + table)) {
                    assertThat(rows.next()).as(table).isFalse();
                }
                sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
                try (var rows = sql.executeQuery("select count(distinct organization_id), min(organization_id::text) from " + table)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).as(table).isEqualTo(1);
                    assertThat(rows.getString(2)).isEqualTo(a.orgId().toString());
                }
                String insert = switch (table) {
                    case "training_class" -> "insert into training_class (id, organization_id, unit_id, name, name_key, modality_id, head_teacher_id) values (gen_random_uuid(), '"
                            + b.orgId() + "', '" + b.unitId() + "', 'X1', 'x1', '" + modality("Judô") + "', '" + headB + "')";
                    case "training_class_schedule" -> "insert into training_class_schedule (organization_id, class_id, weekday, starts_at, ends_at) values ('"
                            + b.orgId() + "', '" + cb + "', 2, '19:00', '20:00')";
                    default -> "insert into training_class_assistant (organization_id, class_id, teacher_id) values ('" + b.orgId() + "', '" + cb + "', '" + headB + "')";
                };
                assertThatThrownBy(() -> sql.executeUpdate(insert)).as(table).hasMessageContaining("row-level security");
                app.rollback();
            }
        }
    }

    @Test
    @DisplayName("CA8: turma ativa conta para titular e auxiliar, aparece no painel e na prévia de inativação e bloqueia modalidade e unidade; desativada não conta")
    void realCounts() throws Exception {
        var t = tenant();
        UUID head = teacher(t, "Ana", t.unitId());
        UUID aux = teacher(t, "Beto", t.unitId());
        UUID judo = modality("Judô");
        UUID klass = klass(t, t.unitId(), "Judô Adulto", judo, null, null, head, "ACTIVE");
        assistant(t, klass, aux);
        var c = who(t, Role.COORDINATOR);
        var teachers = body(call(MockMvcRequestBuilders.get("/api/v1/teachers"), c).getResponse()).get("items");
        assertThat(teachers.findValues("classCount").stream().map(JsonNode::asInt)).containsExactly(1, 1);
        var panel = body(call(MockMvcRequestBuilders.get("/api/v1/teachers/" + head), c).getResponse());
        assertThat(panel.get("classes").findValuesAsText("name")).containsExactly("Judô Adulto");
        assertThat(panel.get("classes").get(0).get("role").asText()).isEqualTo("HEAD");
        var preview = body(call(MockMvcRequestBuilders.get("/api/v1/teachers/" + head + "/deactivation"), c).getResponse());
        assertThat(preview.get("headClasses").findValuesAsText("name")).containsExactly("Judô Adulto");
        assertThat(transactions.provision(t.orgId(), () -> usage.activeClasses(judo))).isEqualTo(1);
        assertThat(transactions.provision(t.orgId(), () -> blockers.activeClasses(t.unitId()))).isEqualTo(1);

        adminJdbc().update("update training_class set status = 'INACTIVE' where id = ?", klass);
        teachers = body(call(MockMvcRequestBuilders.get("/api/v1/teachers"), c).getResponse()).get("items");
        assertThat(teachers.findValues("classCount").stream().map(JsonNode::asInt)).containsExactly(0, 0);
        assertThat(body(call(MockMvcRequestBuilders.get("/api/v1/teachers/" + head), c).getResponse()).get("classes")).isEmpty();
        assertThat(transactions.provision(t.orgId(), () -> usage.activeClasses(judo))).isZero();
        assertThat(transactions.provision(t.orgId(), () -> blockers.activeClasses(t.unitId()))).isZero();
    }
}

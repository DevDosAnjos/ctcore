package br.com.ctcore.communication;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-COM-02 (#82, R15, CA12, CA27): acesso por perfil e isolamento entre organizações nas quatro rotas novas. */
class AnnouncementCreateRouteIT extends AnnouncementBase {
    private static Map<String, Object> toUnit(UUID unit) {
        return Map.of("targetKind", "UNIT", "unitId", unit, "type", "GENERAL", "title", "Aviso", "body", "Texto", "channels", List.of());
    }

    private MockHttpServletResponse upload(AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.multipart("/api/v1/announcements/image").file(new MockMultipartFile("file", "x.png", "image/png", new byte[] {1})), who).getResponse();
    }

    @Test
    @DisplayName("CA12: Aluno e Responsável recebem 403 e o anônimo 401 nas quatro rotas, e nada é gravado")
    void profiles() throws Exception {
        Tenant t = tenant();
        for (AccountPrincipal who : new AccountPrincipal[] {account(t, Role.STUDENT), account(t, Role.GUARDIAN), null}) {
            int expected = who == null ? 401 : 403;
            String name = who == null ? "anônimo" : who.role().name();
            assertThat(send(HttpMethod.GET, "/announcements/targets", who).getStatus()).as(name).isEqualTo(expected);
            assertThat(post("/announcements/preview", toUnit(t.unitId()), who).getStatus()).as(name).isEqualTo(expected);
            assertThat(post("/announcements", toUnit(t.unitId()), who).getStatus()).as(name).isEqualTo(expected);
            assertThat(upload(who).getStatus()).as(name).isEqualTo(expected);
        }
        assertThat(count("select count(*) from announcement")).isZero();
        assertThat(count("select count(*) from audit_log where organization_id = ? and action like 'announcement.%'", t.orgId())).isZero();
    }

    @Test
    @DisplayName("CA12: gestão da organização B não escolhe nem vê o destino da A: prévia e criação dão 404 (turma, unidade, modalidade), os destinos de B não trazem os da A e nada é gravado")
    void organizations() throws Exception {
        Tenant a = tenant(), b = tenant();
        AccountPrincipal coordinatorB = account(b, Role.COORDINATOR);
        UUID klassA = klass(a, "Turma da A", teacher(a, null)), modalityA = modality(a);
        UUID klassB = klass(b, "Turma da B", teacher(b, null));
        List<Map<String, Object>> foreign = List.of(
                Map.of("targetKind", "CLASS", "classId", klassA, "type", "GENERAL", "title", "T", "body", "B"),
                Map.of("targetKind", "UNIT", "unitId", a.unitId(), "type", "GENERAL", "title", "T", "body", "B"),
                Map.of("targetKind", "MODALITY", "unitId", a.unitId(), "modalityId", modalityA, "type", "GENERAL", "title", "T", "body", "B"),
                Map.of("targetKind", "CLASS", "classId", UUID.randomUUID(), "type", "GENERAL", "title", "T", "body", "B"));
        for (Map<String, Object> d : foreign) {
            assertThat(post("/announcements/preview", d, coordinatorB).getStatus()).as("preview " + d).isEqualTo(404);
            assertThat(post("/announcements", d, coordinatorB).getStatus()).as("create " + d).isEqualTo(404);
            assertThat(post("/announcements", d, accountIn(b, Role.TEACHER, b.unitId())).getStatus()).as("teacher " + d).isEqualTo(404);
            assertThat(post("/announcements", d, accountIn(b, Role.ADMINISTRATOR, b.unitId())).getStatus()).as("admin " + d).isEqualTo(404);
        }
        String targets = send(HttpMethod.GET, "/announcements/targets", coordinatorB).getContentAsString();
        assertThat(targets).contains(klassB.toString(), b.unitId().toString()).doesNotContain(klassA.toString(), a.unitId().toString());
        assertThat(count("select count(*) from announcement")).isZero();
    }

    @Test
    @DisplayName("CA12: consulta direta ao banco com o papel da organização B não vê o comunicado nem as turmas (announcement_class) da A")
    void rls() throws Exception {
        Tenant a = tenant(), b = tenant();
        AccountPrincipal coordinatorA = account(a, Role.COORDINATOR);
        UUID klass = klass(a, "Turma RLS", teacher(a, null));
        UUID id = announcement(a, coordinatorA, "CLASSES", a.unitId(), null, "NO_CLASS_DAY", "Sem aula", "2099-01-01T12:00:00Z", null);
        adminJdbc().update("insert into announcement_class (organization_id, announcement_id, class_id) values (?, ?, ?)", a.orgId(), id, klass);
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '" + b.orgId() + "', true)");
            for (String table : new String[] {"announcement", "announcement_class"}) {
                var rs = sql.executeQuery("select count(*) from " + table);
                rs.next();
                assertThat(rs.getInt(1)).as(table).isZero();
            }
            sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
            var rs = sql.executeQuery("select count(*) from announcement_class");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
            app.rollback();
        }
    }
}

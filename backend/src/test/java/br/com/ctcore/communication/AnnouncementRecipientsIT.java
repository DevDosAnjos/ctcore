package br.com.ctcore.communication;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.student.EnrollmentFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RF-COM-02 (#82, R2, R5, R6): a prévia conta pessoas distintas, e-mails e o WhatsApp, sem gravar nada. */
class AnnouncementRecipientsIT extends AnnouncementBase {
    private JsonNode preview(AccountPrincipal who, Map<String, Object> body) throws Exception {
        return ok(post("/announcements/preview", body, who), 200);
    }

    private static Map<String, Object> toClass(UUID klass, String... channels) {
        return Map.of("targetKind", "CLASS", "classId", klass, "channels", List.of(channels));
    }

    private UUID optOut(Tenant t, String email) {
        UUID account = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", account, email);
        adminJdbc().update("insert into account_link (account_id, organization_id, role, status, announcement_email) values (?, ?, 'STUDENT', 'ACTIVE', false)", account, t.orgId());
        return account;
    }

    @Test
    @DisplayName("CA2: turma com adulto, menor com dois responsáveis, adulto sem e-mail e aluno encerrado: 4 pessoas e 3 e-mails; o menor e o encerrado não contam; nada é gravado")
    void people() throws Exception {
        Tenant t = tenant();
        AccountPrincipal coordinator = account(t, Role.COORDINATOR);
        UUID teacher = teacher(t, null), klass = klass(t, "Turma Prévia", teacher);
        enrolled(t, klass, teacher, "1990-01-01", "adulto-1@example.test", "ACTIVE");
        UUID minor = enrolled(t, klass, teacher, "2015-01-01", "menor@example.test", "ACTIVE");
        BillingFixtures.guardian(adminJdbc(), t.orgId(), minor, true);
        BillingFixtures.guardian(adminJdbc(), t.orgId(), minor, false);
        enrolled(t, klass, teacher, "1991-01-01", null, "ACTIVE");
        enrolled(t, klass, teacher, "1992-01-01", "encerrado@example.test", "ENDED");

        JsonNode p = preview(coordinator, toClass(klass, "EMAIL"));
        assertThat(p.get("recipients").asInt()).isEqualTo(4);
        assertThat(p.get("portal").asInt()).isEqualTo(4);
        assertThat(p.get("email").asInt()).isEqualTo(3);
        assertThat(p.get("channels")).extracting(JsonNode::asText).containsExactly("PORTAL", "EMAIL");
        assertThat(p.get("whatsapp").isNull()).isTrue();
        assertThat(count("select count(*) from announcement")).isZero();
        assertThat(count("select count(*) from email_message where organization_id = ?", t.orgId())).isZero();
        assertThat(count("select count(*) from audit_log where organization_id = ? and action like 'announcement%'", t.orgId())).isZero();
    }

    @Test
    @DisplayName("CA3: o responsável de dois filhos conta uma vez; quem desligou os avisos conta nas pessoas mas não nos e-mails; \"Com problema\" também não recebe e-mail")
    void onceAndOptOut() throws Exception {
        Tenant t = tenant();
        AccountPrincipal coordinator = account(t, Role.COORDINATOR);
        UUID teacher = teacher(t, null), klass = klass(t, "Turma Dupla", teacher);
        UUID first = enrolled(t, klass, teacher, "2015-01-01", null, "ACTIVE"), second = enrolled(t, klass, teacher, "2016-01-01", null, "ACTIVE");
        UUID guardian = BillingFixtures.guardian(adminJdbc(), t.orgId(), first, true);
        adminJdbc().update("insert into student_guardian (organization_id, student_id, guardian_id, financial) values (?, ?, ?, false)", t.orgId(), second, guardian);
        enrolled(t, klass, teacher, "1990-01-01", "gen-desligou@example.com", "ACTIVE");
        optOut(t, "gen-desligou@example.com");
        UUID problem = enrolled(t, klass, teacher, "1991-01-01", "problema@example.test", "ACTIVE");
        adminJdbc().update("update student set email_problem_at = now() where id = ?", problem);

        JsonNode p = preview(coordinator, toClass(klass, "EMAIL"));
        assertThat(p.get("recipients").asInt()).isEqualTo(3);
        assertThat(p.get("email").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("CA4: WhatsApp na turma com link é GROUP; sem link, CONVERSATION; sem o canal, nulo; PUSH e canal desconhecido são 400")
    void whatsapp() throws Exception {
        Tenant t = tenant();
        AccountPrincipal coordinator = account(t, Role.COORDINATOR);
        UUID teacher = teacher(t, null), withLink = klass(t, "Com link", teacher), without = klass(t, "Sem link", teacher);
        adminJdbc().update("update training_class set group_link = 'https://chat.example.test/grupo' where id = ?", withLink);

        JsonNode group = preview(coordinator, toClass(withLink, "WHATSAPP")).get("whatsapp");
        assertThat(group.get("mode").asText()).isEqualTo("GROUP");
        assertThat(group.get("groupLink").asText()).isEqualTo("https://chat.example.test/grupo");
        assertThat(preview(coordinator, toClass(without, "WHATSAPP")).get("whatsapp").get("mode").asText()).isEqualTo("CONVERSATION");
        assertThat(preview(coordinator, Map.of("targetKind", "UNIT", "unitId", t.unitId(), "channels", List.of("WHATSAPP"))).get("whatsapp").get("mode").asText())
                .isEqualTo("CONVERSATION");
        assertThat(preview(coordinator, toClass(withLink)).get("whatsapp").isNull()).isTrue();
        for (String bad : new String[] {"PUSH", "SMS"}) {
            var r = post("/announcements/preview", toClass(withLink, bad), coordinator);
            assertThat(r.getStatus()).as(bad).isEqualTo(400);
            assertThat(r.getContentAsString()).contains("channels");
        }
    }

    @Test
    @DisplayName("CA5: modalidade na unidade, unidade e toda a organização trazem só as matrículas ativas do recorte; o outro horário também entra na turma")
    void scopes() throws Exception {
        Tenant t = tenant();
        AccountPrincipal coordinator = account(t, Role.COORDINATOR);
        UUID unit2 = secondUnit(t), teacher = teacher(t, null);
        UUID jiu = modality(t), other = adminJdbc().queryForObject("select id from modality where name <> 'Jiu-Jitsu' order by name limit 1", UUID.class);
        adminJdbc().update("insert into organization_modality (organization_id, modality_id, active) values (?, ?, true) on conflict do nothing", t.orgId(), other);
        adminJdbc().update("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (?, ?, ?, true) on conflict do nothing", t.orgId(), t.unitId(), other);
        UUID classA = klass(t, "Turma A", teacher), classB = klass(t, "Turma B", teacher), classC = klassIn(t, unit2, "Turma C", teacher, "ACTIVE");
        adminJdbc().update("update training_class set modality_id = ? where id = ?", other, classB);
        for (Object[] row : new Object[][] {{classA, t.unitId(), jiu}, {classB, t.unitId(), other}, {classC, unit2, jiu}}) {
            UUID student = student(t, "1990-01-01");
            EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), student, (UUID) row[1], "ACTIVE", (UUID) row[2], (UUID) row[0], null, teacher);
        }
        UUID extra = student(t, "1990-01-01");
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), extra, t.unitId(), "ACTIVE", other, classB, classA, teacher);

        assertThat(preview(coordinator, Map.of("targetKind", "MODALITY", "unitId", t.unitId(), "modalityId", jiu, "channels", List.of())).get("recipients").asInt()).isEqualTo(1);
        assertThat(preview(coordinator, Map.of("targetKind", "UNIT", "unitId", t.unitId(), "channels", List.of())).get("recipients").asInt()).isEqualTo(3);
        assertThat(preview(coordinator, Map.of("targetKind", "ORGANIZATION", "channels", List.of())).get("recipients").asInt()).isEqualTo(4);
        assertThat(preview(coordinator, toClass(classA)).get("recipients").asInt()).isEqualTo(2);
    }
}

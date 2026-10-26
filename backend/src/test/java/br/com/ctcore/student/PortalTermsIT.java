package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-MAT-11 a RN-MAT-14, RN-LGP-01: regras do aceite do termo e do uso de imagem, com banco real. */
class PortalTermsIT extends PortalTermBase {
    @Test
    @DisplayName("CA1, CA5, CA7, CA20: aceite grava conta, versão e IP, com uso de imagem na caixa; repetir devolve 200 sem nova linha; auditoria sem o texto")
    void accept() throws Exception {
        Tenant t = tenant();
        publish(t, "ENROLLMENT", 1, true);
        publish(t, "IMAGE_USE", 1, true);
        UUID acc = account("aluno@portal.example"), acc2 = account("outro@portal.example");
        UUID s = enrolledAdult(t, "Aluno Um", acc), noImage = enrolledAdult(t, "Aluno Dois", acc2);
        var who = portal(t, acc, Role.STUDENT);

        assertThat(body(get(who, "/term")).get(0).get("id").asText()).isEqualTo(s.toString());
        var first = post(who, s, "{\"version\":1,\"imageUse\":true}");
        assertThat(first.getStatus()).isEqualTo(201);
        assertThat(body(first).get("imageUse").asBoolean()).isTrue();
        var row = adminJdbc().queryForMap("select account_id, version, ip, accepted_at from term_acceptance where student_id = ? and kind = 'ENROLLMENT'", s);
        assertThat(row.get("account_id")).isEqualTo(acc);
        assertThat(row.get("version")).isEqualTo(1);
        assertThat(row.get("ip").toString()).isNotBlank();
        assertThat(row.get("accepted_at")).isNotNull();
        assertThat(count("student_id = ? and kind = 'IMAGE_USE' and event = 'ACCEPTED' and version = 1", s)).isEqualTo(1);
        assertThat(body(get(who, "/term"))).isEmpty();

        assertThat(post(who, s, "{\"version\":1,\"imageUse\":true}").getStatus()).isEqualTo(200);
        assertThat(count("student_id = ?", s)).isEqualTo(2);
        assertThat(audits("term.accepted", s)).isEqualTo(1);
        assertThat(audits("image-use.granted", s)).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select coalesce(string_agg(after_data::text, ''), '') from audit_log where entity_id = ?", String.class, s.toString()))
                .doesNotContain("Texto do termo");

        assertThat(post(portal(t, acc2, Role.STUDENT), noImage, "{\"version\":1,\"imageUse\":false}").getStatus()).isEqualTo(201);
        assertThat(count("student_id = ? and kind = 'IMAGE_USE'", noImage)).isZero();
        assertThat(body(get(portal(t, acc2, Role.STUDENT), "/students/" + noImage + "/image-use")).get("granted").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("CA5: duas chamadas simultâneas gravam uma linha só")
    void concurrent() throws Exception {
        Tenant t = tenant();
        publish(t, "ENROLLMENT", 1, true);
        UUID acc = account("corrida@portal.example");
        UUID s = enrolledAdult(t, "Aluno Corrida", acc);
        var who = portal(t, acc, Role.STUDENT);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> call = () -> post(who, s, "{\"version\":1}").getStatus();
            Future<Integer> a = pool.submit(call), b = pool.submit(call);
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 201);
        } finally {
            pool.shutdown();
        }
        assertThat(count("student_id = ? and kind = 'ENROLLMENT'", s)).isEqualTo(1);
    }

    @Test
    @DisplayName("CA2: menor com dois responsáveis: o financeiro aceita (201), o não financeiro recebe 403 e nada é gravado")
    void guardians() throws Exception {
        Tenant t = tenant();
        publish(t, "ENROLLMENT", 1, true);
        UUID fin = account("financeiro@portal.example"), other = account("outro@portal.example");
        UUID child = student(t, "Filho Menor", "2015-01-01", null);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), child, t.unitId(), "ACTIVE", 0, null);
        guardian(t, fin, "52998224725", true, child);
        guardian(t, other, "11144477735", false, child);

        assertThat(post(portal(t, other, Role.GUARDIAN), child, "{\"version\":1}").getStatus()).isEqualTo(403);
        assertThat(count("student_id = ?", child)).isZero();
        assertThat(post(portal(t, fin, Role.GUARDIAN), child, "{\"version\":1}").getStatus()).isEqualTo(201);
        assertThat(count("student_id = ? and account_id = ?", child, fin)).isEqualTo(1);
    }

    @Test
    @DisplayName("CA4, CA6: versão antiga 409, sem versão 400, aluno sem matrícula 200; nada é gravado")
    void rejected() throws Exception {
        Tenant t = tenant();
        publish(t, "ENROLLMENT", 1, true);
        publish(t, "ENROLLMENT", 2, true);
        UUID acc = account("aluno@portal.example"), acc2 = account("semmatricula@portal.example");
        UUID s = enrolledAdult(t, "Aluno Um", acc), none = student(t, "Sem Matricula", "1990-01-01", acc2);
        var who = portal(t, acc, Role.STUDENT);

        var old = post(who, s, "{\"version\":1}");
        assertThat(old.getStatus()).isEqualTo(409);
        assertThat(body(old).get("code").asText()).isEqualTo("TERMS_VERSION_CONFLICT");
        var missing = post(who, s, "{}");
        assertThat(missing.getStatus()).isEqualTo(400);
        assertThat(body(missing).get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(post(portal(t, acc2, Role.STUDENT), none, "{\"version\":2}").getStatus()).isEqualTo(200);
        assertThat(count("true")).isZero();
        assertThat(body(get(portal(t, acc2, Role.STUDENT), "/term"))).isEmpty();
    }

    @Test
    @DisplayName("CA10, CA11: reaceite só quando a versão nova pede; aluno matriculado depois da v3 sem aceite está pendente e aceita a v3")
    void reaccept() throws Exception {
        Tenant t = tenant();
        publish(t, "ENROLLMENT", 1, true);
        UUID acc = account("aluno@portal.example"), late = account("tarde@portal.example");
        UUID s = enrolledAdult(t, "Aluno Um", acc);
        var who = portal(t, acc, Role.STUDENT);
        assertThat(post(who, s, "{\"version\":1}").getStatus()).isEqualTo(201);

        publish(t, "ENROLLMENT", 2, false);
        assertThat(body(get(who, "/term"))).isEmpty();
        publish(t, "ENROLLMENT", 3, true);
        var pending = body(get(who, "/term"));
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).get("term").get("version").asInt()).isEqualTo(3);
        assertThat(post(who, s, "{\"version\":3}").getStatus()).isEqualTo(201);
        assertThat(body(get(who, "/term"))).isEmpty();

        publish(t, "ENROLLMENT", 4, false);
        UUID s2 = enrolledAdult(t, "Aluno Tarde", late);
        assertThat(body(get(portal(t, late, Role.STUDENT), "/term"))).hasSize(1);
        assertThat(post(portal(t, late, Role.STUDENT), s2, "{\"version\":4}").getStatus()).isEqualTo(201);
        assertThat(count("student_id = ? and version = 4", s2)).isEqualTo(1);
    }

    @Test
    @DisplayName("CA8, CA9, CA20: revogar e conceder uso de imagem são registros novos; versão antiga 409; nova versão com reaccept volta a falso")
    void imageUse() throws Exception {
        Tenant t = tenant();
        publish(t, "IMAGE_USE", 1, true);
        UUID acc = account("aluno@portal.example");
        UUID s = enrolledAdult(t, "Aluno Um", acc);
        AccountPrincipal who = portal(t, acc, Role.STUDENT);

        assertThat(body(put(who, s, "{\"granted\":true,\"version\":1}")).get("granted").asBoolean()).isTrue();
        assertThat(put(who, s, "{\"granted\":true,\"version\":1}").getStatus()).isEqualTo(200);
        assertThat(count("student_id = ? and kind = 'IMAGE_USE'", s)).isEqualTo(1);
        var revoked = put(who, s, "{\"granted\":false}");
        assertThat(revoked.getStatus()).isEqualTo(200);
        assertThat(body(revoked).get("granted").asBoolean()).isFalse();
        assertThat(body(get(who, "/students/" + s + "/image-use")).get("granted").asBoolean()).isFalse();
        assertThat(count("student_id = ? and event = 'REVOKED'", s)).isEqualTo(1);
        assertThat(count("student_id = ? and event = 'ACCEPTED'", s)).isEqualTo(1);
        assertThat(audits("image-use.revoked", s)).isEqualTo(1);
        assertThat(put(who, s, "{\"granted\":false}").getStatus()).isEqualTo(200);
        assertThat(count("student_id = ?", s)).isEqualTo(2);
        assertThat(put(who, s, "{\"granted\":true,\"version\":1}").getStatus()).isEqualTo(200);
        assertThat(count("student_id = ? and event = 'ACCEPTED'", s)).isEqualTo(2);
        assertThat(put(who, s, "{}").getStatus()).isEqualTo(400);

        publish(t, "IMAGE_USE", 2, true);
        var after = body(get(who, "/students/" + s + "/image-use"));
        assertThat(after.get("granted").asBoolean()).isFalse();
        assertThat(after.get("version").asInt()).isEqualTo(2);
        assertThat(after.get("body").asText()).contains("v2");
        assertThat(put(who, s, "{\"granted\":true,\"version\":1}").getStatus()).isEqualTo(409);
        assertThat(put(who, s, "{\"granted\":true,\"version\":2}").getStatus()).isEqualTo(200);
        assertThat(body(get(who, "/students/" + s + "/image-use")).get("granted").asBoolean()).isTrue();
    }
}

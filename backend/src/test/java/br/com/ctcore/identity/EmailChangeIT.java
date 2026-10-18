package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.integration.Outbox;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

@Import({AuthBase.Config.class, AuthBase.OrgProbe.class})
class EmailChangeIT extends AuthBase {
    static final String URL = "/api/v1/auth/email-change";

    String codeIn() {
        List<Outbox.Message> mails = outbox.list();
        Matcher m = Pattern.compile("confirmar-email/(\\S+)").matcher(mails.get(mails.size() - 1).content());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    int request(Cookie s, String newEmail) throws Exception {
        return post(URL, Map.of("newEmail", newEmail, "password", PASSWORD), s).andReturn().getResponse().getStatus();
    }

    int confirm(Cookie s, String code) throws Exception {
        return post(URL + "/" + code + "/confirm", null, s).andReturn().getResponse().getStatus();
    }

    String email(UUID id) {
        return admin.queryForObject("select email from account where id = ?", String.class, id);
    }

    @Test
    @DisplayName("RN-ACE-15: CA3, CA7: pedido pendente por 24 horas, e-mail só muda na confirmação, aviso ao antigo, link de uso único")
    void requestAndConfirm() throws Exception {
        UUID org = org();
        UUID ana = account("ana@x.com");
        link(ana, org, "ADMINISTRATOR", "ACTIVE");
        Cookie s = sessionOf("ana@x.com");

        assertThat(request(s, "novo@x.com")).isEqualTo(202);
        assertThat(email(ana)).isEqualTo("ana@x.com");
        assertThat(count("select count(*) from account_email_change where account_id = ? and used_at is null", ana)).isEqualTo(1);
        assertThat(count("select count(*) from account_email_change where expires_at - created_at = interval '24 hours'")).isEqualTo(1);
        assertThat(outbox.list()).hasSize(1);
        assertThat(outbox.list().get(0).recipient()).isEqualTo("novo@x.com");
        String code = codeIn();
        assertThat(admin.queryForObject("select code_hash from account_email_change", String.class)).isNotEqualTo(code);

        assertThat(confirm(s, code)).isEqualTo(204);
        assertThat(email(ana)).isEqualTo("novo@x.com");
        assertThat(login("novo@x.com", PASSWORD).getResponse().getStatus()).isEqualTo(200);
        assertThat(login("ana@x.com", PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(outbox.list().stream().anyMatch(m -> m.recipient().equals("ana@x.com"))).isTrue();
        assertThat(shape(post(URL + "/" + code + "/confirm", null, s).andReturn())).startsWith("410|EMAIL_CHANGE_INVALID");
    }

    @Test
    @DisplayName("RN-ACE-15: CA4: senha errada ou código ausente das duas etapas não cria nem envia nada")
    void needsPasswordAndCode() throws Exception {
        UUID org = org();
        UUID ana = account("ana@x.com");
        link(ana, org, "ADMINISTRATOR", "ACTIVE");
        Cookie s = sessionOf("ana@x.com");
        assertThat(shape(post(URL, Map.of("newEmail", "novo@x.com", "password", "Errada123"), s).andReturn())).startsWith("401|INVALID_CREDENTIALS");

        byte[] secret = enableTwoFactor(ana, "ana@x.com");
        s = sessionOf("ana@x.com");
        assertThat(shape(post(URL, Map.of("newEmail", "novo@x.com", "password", PASSWORD), s).andReturn())).startsWith("401|INVALID_TWO_FACTOR_CODE");
        assertThat(shape(post(URL, Map.of("newEmail", "novo@x.com", "password", PASSWORD, "code", "000000"), s).andReturn()))
                .startsWith("401|INVALID_TWO_FACTOR_CODE");
        assertThat(count("select count(*) from account_email_change")).isZero();
        assertThat(outbox.list()).isEmpty();

        assertThat(post(URL, Map.of("newEmail", "novo@x.com", "password", PASSWORD, "code", nextCode(secret)), s).andReturn()
                .getResponse().getStatus()).isEqualTo(202);
    }

    @Test
    @DisplayName("RN-ACE-15: CA5: novo pedido cancela o anterior; no máximo 3 por hora")
    void cancelsPreviousAndLimits() throws Exception {
        link(account("ana@x.com"), org(), "ADMINISTRATOR", "ACTIVE");
        Cookie s = sessionOf("ana@x.com");
        assertThat(request(s, "a1@x.com")).isEqualTo(202);
        String first = codeIn();
        assertThat(request(s, "a2@x.com")).isEqualTo(202);
        String second = codeIn();
        assertThat(confirm(s, first)).isEqualTo(410);
        assertThat(request(s, "a3@x.com")).isEqualTo(202);
        assertThat(shape(post(URL, Map.of("newEmail", "a4@x.com", "password", PASSWORD), s).andReturn())).startsWith("429|TOO_MANY_REQUESTS");
        assertThat(confirm(s, second)).isEqualTo(410);

        clock.now = clock.now.plus(Duration.ofMinutes(61));
        s = sessionOf("ana@x.com");
        assertThat(request(s, "a4@x.com")).isEqualTo(202);
    }

    @Test
    @DisplayName("RN-ACE-15, RN-ACE-11: CA6: endereço de outra conta responde igual e nada sai; igual ao atual ou mal formado é 400")
    void takenAddress() throws Exception {
        UUID org = org();
        link(account("ana@x.com"), org, "ADMINISTRATOR", "ACTIVE");
        link(account("bia@x.com"), org, "TEACHER", "ACTIVE");
        Cookie s = sessionOf("ana@x.com");
        MvcResult taken = post(URL, Map.of("newEmail", "BIA@x.com", "password", PASSWORD), s).andReturn();
        MvcResult free = post(URL, Map.of("newEmail", "livre@x.com", "password", PASSWORD), s).andReturn();
        assertThat(taken.getResponse().getStatus()).isEqualTo(202);
        assertThat(taken.getResponse().getContentAsString()).isEqualTo(free.getResponse().getContentAsString());
        assertThat(count("select count(*) from account_email_change where new_email ilike 'bia@x.com'")).isZero();
        assertThat(outbox.list()).hasSize(1);

        assertThat(shape(post(URL, Map.of("newEmail", "ANA@x.com", "password", PASSWORD), s).andReturn())).startsWith("400|VALIDATION_ERROR");
        assertThat(shape(post(URL, Map.of("newEmail", "sem-arroba", "password", PASSWORD), s).andReturn())).startsWith("400|VALIDATION_ERROR");
    }

    @Test
    @DisplayName("RN-ACE-15: CA8: vencido, cancelado, de outra conta ou endereço tomado antes dão 410 e o e-mail não muda")
    void invalidLinks() throws Exception {
        UUID org = org();
        UUID ana = account("ana@x.com");
        link(ana, org, "ADMINISTRATOR", "ACTIVE");
        UUID bia = account("bia@x.com");
        link(bia, org, "TEACHER", "ACTIVE");
        Cookie s = sessionOf("ana@x.com"), sb = sessionOf("bia@x.com");

        assertThat(request(s, "novo@x.com")).isEqualTo(202);
        String code = codeIn();
        assertThat(confirm(sb, code)).isEqualTo(410);
        assertThat(email(bia)).isEqualTo("bia@x.com");
        assertThat(post(URL + "/" + code + "/confirm", null, null).andReturn().getResponse().getStatus()).isEqualTo(401);

        admin.update("insert into account (email, name, password_hash, status) values ('novo@x.com', 'N', ?, 'ACTIVE')", passwordHash);
        assertThat(confirm(s, code)).isEqualTo(410);
        assertThat(email(ana)).isEqualTo("ana@x.com");
        admin.update("delete from account where email = 'novo@x.com'");

        clock.now = clock.now.plus(Duration.ofHours(24)).plusSeconds(1);
        s = sessionOf("ana@x.com");
        assertThat(confirm(s, code)).isEqualTo(410);
        assertThat(email(ana)).isEqualTo("ana@x.com");
        assertThat(confirm(s, "inexistente")).isEqualTo(410);
    }

    @Test
    @DisplayName("RF-ACE-04: CA13: pedido e troca são auditados por organização, sem o endereço novo, senha nem código")
    void audited() throws Exception {
        UUID org = org();
        link(account("ana@x.com"), org, "ADMINISTRATOR", "ACTIVE");
        Cookie s = sessionOf("ana@x.com");
        assertThat(request(s, "novo@x.com")).isEqualTo(202);
        String code = codeIn();
        assertThat(confirm(s, code)).isEqualTo(204);
        assertThat(count("select count(*) from audit_log where organization_id = ? and action = 'account.email_change_requested' and ip is not null", org)).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where organization_id = ? and action = 'account.email_changed' and ip is not null", org)).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where organization_id = ? and (audit_log::text ilike '%novo@x.com%' "
                + "or audit_log::text like '%' || ? || '%' or audit_log::text like '%' || ? || '%')", org, code, PASSWORD)).isZero();
    }
}

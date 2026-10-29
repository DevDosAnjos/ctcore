package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.EmailChangeService;
import br.com.ctcore.identity.IdentityException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** RN-ALU-11, RN-ACE-15, D3: o e-mail de contato do cadastro acompanha a confirmação do e-mail da conta, com banco real. */
class ContactEmailSyncIT extends PortalTermBase {
    @Autowired EmailChangeService emailChanges;

    private String request(UUID account, String newEmail) throws Exception {
        String code = "codigo-" + UUID.randomUUID();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        adminJdbc().update("insert into account_email_change (account_id, new_email, code_hash, expires_at) values (?, ?, ?, now() + interval '1 day')", account, newEmail, hash);
        return code;
    }

    private void link(Tenant t, UUID account, String role) {
        adminJdbc().update("insert into account_link (id, account_id, organization_id, role, status) values (?, ?, ?, ?, 'ACTIVE')", UUID.randomUUID(), account, t.orgId(), role);
    }

    private String studentEmail(UUID id) {
        return adminJdbc().queryForObject("select email from student where id = ?", String.class, id);
    }

    private List<String> audit(String action, UUID... entities) {
        return adminJdbc().queryForList("select actor_type || '|' || coalesce(actor_profile, '') || '|' || coalesce(after_data::text, '') from audit_log where action = ? and entity_id = any (?::text[])", String.class, action,
                java.util.Arrays.stream(entities).map(UUID::toString).toArray(String[]::new));
    }

    @Test
    @DisplayName("CA6: confirmado o link, o e-mail do aluno com a conta e o marcador do responsável acompanham, em todas as organizações; antes, nada muda")
    void confirmSyncs() throws Exception {
        Tenant a = tenant(), b = tenant();
        UUID x = account("x@portal.example"), g = account("g@portal.example");
        UUID sa = student(a, "Aluno A", "1990-01-01", x), sb = student(b, "Aluno B", "1990-01-01", x);
        UUID minor = student(a, "Filho Menor", "2015-05-05", null);
        guardian(a, g, "11144477735", true, minor);
        link(a, x, "STUDENT");
        link(b, x, "STUDENT");
        link(a, g, "GUARDIAN");
        adminJdbc().update("update student set email = 'velho@portal.example', email_problem_at = now() where id in (?, ?)", sa, sb);
        adminJdbc().update("update student set email = 'pai@portal.example' where id = ?", minor);
        adminJdbc().update("update guardian set email_problem_at = now() where account_id = ?", g);

        String code = request(x, "novo@portal.example");
        assertThat(studentEmail(sa)).isEqualTo("velho@portal.example");
        assertThat(audit("student.updated", sa, sb)).isEmpty();
        emailChanges.confirm(x, code, "127.0.0.1");
        assertThat(studentEmail(sa)).isEqualTo("novo@portal.example");
        assertThat(studentEmail(sb)).isEqualTo("novo@portal.example");
        assertThat(adminJdbc().queryForObject("select count(*) from student where email_problem_at is not null", Integer.class)).isZero();
        assertThat(studentEmail(minor)).isEqualTo("pai@portal.example");
        assertThat(audit("student.updated", sa, sb)).hasSize(2).allMatch(r -> r.startsWith("PORTAL|STUDENT|") && r.contains("alterado") && !r.contains("novo@"));

        String code2 = request(g, "responsavel@portal.example");
        emailChanges.confirm(g, code2, "127.0.0.1");
        assertThat(adminJdbc().queryForObject("select email_problem_at from guardian where account_id = ?", Object.class, g)).isNull();
        UUID gid = adminJdbc().queryForObject("select id from guardian where account_id = ?", UUID.class, g);
        assertThat(audit("guardian.updated", gid)).hasSize(1).allMatch(r -> r.startsWith("PORTAL|GUARDIAN|"));
        assertThat(studentEmail(minor)).isEqualTo("pai@portal.example");
    }

    @Test
    @DisplayName("CA6: link inválido ou já usado não muda nada")
    void invalidLink() throws Exception {
        Tenant a = tenant();
        UUID x = account("x@portal.example");
        UUID sa = student(a, "Aluno A", "1990-01-01", x);
        link(a, x, "STUDENT");
        adminJdbc().update("update student set email = 'velho@portal.example' where id = ?", sa);
        String code = request(x, "novo@portal.example");
        org.junit.jupiter.api.Assertions.assertThrows(IdentityException.class, () -> emailChanges.confirm(x, "inexistente", "127.0.0.1"));
        assertThat(studentEmail(sa)).isEqualTo("velho@portal.example");
        emailChanges.confirm(x, code, "127.0.0.1");
        adminJdbc().update("update student set email = 'outro@portal.example' where id = ?", sa);
        org.junit.jupiter.api.Assertions.assertThrows(IdentityException.class, () -> emailChanges.confirm(x, code, "127.0.0.1"));
        assertThat(studentEmail(sa)).isEqualTo("outro@portal.example");
    }

    @Test
    @DisplayName("CA6: falha numa organização não desfaz a confirmação nem as demais")
    void isolatedFailure() throws Exception {
        Tenant a = tenant(), b = tenant();
        UUID x = account("x@portal.example");
        UUID sa = student(a, "Aluno A", "1990-01-01", x), sb = student(b, "Aluno B", "1990-01-01", x);
        link(a, x, "STUDENT");
        link(b, x, "STUDENT");
        adminJdbc().update("update student set email = 'velho@portal.example'");
        adminJdbc().update("update account set email = 'x@portal.example' where id = ?", x);
        // gatilho que recusa a gravação do e-mail novo só na organização A
        adminJdbc().execute("create or replace function sync_fail() returns trigger language plpgsql as $$ begin if new.organization_id = '" + a.orgId()
                + "' and new.email = 'novo@portal.example' then raise exception 'falha de teste'; end if; return new; end $$");
        adminJdbc().execute("create trigger sync_fail before update on student for each row execute function sync_fail()");
        try {
            emailChanges.confirm(x, request(x, "novo@portal.example"), "127.0.0.1");
        } finally {
            adminJdbc().execute("drop trigger sync_fail on student");
            adminJdbc().execute("drop function sync_fail()");
        }
        assertThat(adminJdbc().queryForObject("select email from account where id = ?", String.class, x)).isEqualTo("novo@portal.example");
        assertThat(studentEmail(sa)).isEqualTo("velho@portal.example");
        assertThat(studentEmail(sb)).isEqualTo("novo@portal.example");
    }
}

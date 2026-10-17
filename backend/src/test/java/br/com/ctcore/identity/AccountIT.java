package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.db.PostgresIT;
import br.com.ctcore.identity.LoginAttempts.Result;
import br.com.ctcore.identity.LoginAttempts.Status;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;

class AccountIT extends PostgresIT {
    JdbcTemplate admin = adminJdbc();
    @Autowired AccountTransactions accountTransactions;
    @Autowired JdbcClient jdbc;

    UUID orgA = UUID.randomUUID(), orgB = UUID.randomUUID();
    UUID unitA = UUID.randomUUID();
    UUID ana = UUID.randomUUID(), bia = UUID.randomUUID();
    UUID anaTeacherA = UUID.randomUUID(), anaStudentB = UUID.randomUUID(), anaAdminA = UUID.randomUUID(), biaAdminB = UUID.randomUUID();

    static String hash(String code) { return "hash-" + code; }

    @BeforeEach
    void data() {
        for (UUID org : new UUID[]{orgA, orgB}) {
            admin.update("insert into organization (id, name) values (?, 'Org')", org);
        }
        admin.update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'U', ?)", unitA, orgA, "u-" + unitA);
        admin.update("insert into account (id, email, status) values (?, 'Ana@x.com', 'ACTIVE'), (?, 'bia@x.com', 'ACTIVE')", ana, bia);
        link(anaTeacherA, ana, orgA, "TEACHER");
        link(anaStudentB, ana, orgB, "STUDENT");
        link(anaAdminA, ana, orgA, "ADMINISTRATOR");
        link(biaAdminB, bia, orgB, "ADMINISTRATOR");
        admin.update("insert into account_link_unit (link_id, organization_id, unit_id) values (?, ?, ?)", anaTeacherA, orgA, unitA);
        invite(anaStudentB, ana, orgB, "codeB");
        invite(anaAdminA, ana, orgA, "codeA");
    }

    @AfterEach
    void clean() {
        for (String table : new String[]{"login_attempt", "account_session", "password_reset", "account_invite",
                "account_link_unit", "account_link", "account"}) {
            admin.update("delete from " + table);
        }
        admin.update("delete from unit where id = ?", unitA);
        admin.update("delete from organization where id in (?, ?)", orgA, orgB);
    }

    void link(UUID id, UUID account, UUID org, String role) {
        admin.update("insert into account_link (id, account_id, organization_id, role, status) values (?, ?, ?, ?, 'ACTIVE')",
                id, account, org, role);
    }

    void invite(UUID link, UUID account, UUID org, String code) {
        admin.update("insert into account_invite (account_id, link_id, organization_id, code_hash, expires_at) values (?, ?, ?, ?, now() + interval '7 days')",
                account, link, org, hash(code));
    }

    /** Runs a query as the runtime role (RLS applies) with the given scopes; returns the first column as text. */
    List<String> asApp(String org, String account, String code, String sql) throws Exception {
        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime");
             Statement s = c.createStatement()) {
            c.setAutoCommit(false);
            try (var set = c.prepareStatement("select set_config('app.organization_id', ?, true), "
                    + "set_config('app.account_id', ?, true), set_config('app.invite_code_hash', ?, true)")) {
                set.setString(1, org);
                set.setString(2, account);
                set.setString(3, code);
                set.execute();
            }
            List<String> out = new ArrayList<>();
            try (var rows = s.executeQuery(sql)) {
                while (rows.next()) out.add(rows.getString(1));
            }
            c.rollback();
            return out;
        }
    }

    @Test
    @DisplayName("CA1: e-mail único sem diferenciar maiúsculas; uma conta com vários vínculos")
    void oneAccountPerEmailManyLinks() {
        assertThatThrownBy(() -> admin.update("insert into account (email, status) values ('ana@X.com', 'ACTIVE')"))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(admin.queryForObject("select count(*) from account_link where account_id = ?", Integer.class, ana)).isEqualTo(3);
        assertThat(admin.queryForObject("select count(*) from account where lower(email) = 'ana@x.com'", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> link(UUID.randomUUID(), ana, orgA, "TEACHER")).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("CA16: RLS - escopo de organização só vê vínculos e convites da própria")
    void organizationScope() throws Exception {
        assertThat(asApp(orgB.toString(), "", "", "select id::text from account_link"))
                .containsExactlyInAnyOrder(anaStudentB.toString(), biaAdminB.toString());
        assertThat(asApp(orgB.toString(), "", "", "select code_hash from account_invite")).containsExactly(hash("codeB"));
        assertThat(asApp(orgB.toString(), "", "", "select link_id::text from account_link_unit")).isEmpty();
        assertThat(asApp(orgA.toString(), "", "", "select link_id::text from account_link_unit")).containsExactly(anaTeacherA.toString());
        assertThat(asApp("", "", "", "select id::text from account_link")).isEmpty();
        assertThat(asApp("", "", "", "select id::text from account_invite")).isEmpty();
    }

    @Test
    @DisplayName("CA16: RLS - escopo de conta só vê o que é da conta, em qualquer organização")
    void accountScope() throws Exception {
        assertThat(asApp("", bia.toString(), "", "select id::text from account_link")).containsExactly(biaAdminB.toString());
        assertThat(asApp("", ana.toString(), "", "select id::text from account_link"))
                .containsExactlyInAnyOrder(anaTeacherA.toString(), anaStudentB.toString(), anaAdminA.toString());
        assertThat(asApp("", bia.toString(), "", "select id::text from account_invite")).isEmpty();
        assertThat(asApp("", ana.toString(), "", "select link_id::text from account_link_unit")).containsExactly(anaTeacherA.toString());
        assertThat(asApp("", bia.toString(), "", "select link_id::text from account_link_unit")).isEmpty();
    }

    @Test
    @DisplayName("CA16: RLS - escopo do código só traz o convite e o vínculo daquele código")
    void inviteCodeScope() throws Exception {
        assertThat(asApp("", "", hash("codeB"), "select code_hash from account_invite")).containsExactly(hash("codeB"));
        assertThat(asApp("", "", hash("codeB"), "select id::text from account_link")).containsExactly(anaStudentB.toString());
        assertThat(asApp("", "", hash("nope"), "select code_hash from account_invite")).isEmpty();
        assertThat(asApp("", "", hash("nope"), "select id::text from account_link")).isEmpty();
    }

    @Test
    @DisplayName("CA16: RLS - inserir vínculo de outra organização ou de outra conta é recusado")
    void writesAreScoped() {
        assertThatThrownBy(() -> asApp(orgA.toString(), "", "", "insert into account_link (account_id, organization_id, role, status) "
                + "values ('" + bia + "', '" + orgB + "', 'TEACHER', 'ACTIVE') returning id::text"))
                .hasMessageContaining("row-level security");
        assertThatThrownBy(() -> asApp("", "", hash("codeB"), "insert into account_link (account_id, organization_id, role, status) "
                + "values ('" + bia + "', '" + orgB + "', 'TEACHER', 'ACTIVE') returning id::text"))
                .hasMessageContaining("row-level security");
    }

    @Test
    @DisplayName("AccountTransactions: abre o escopo da conta ou do código e recusa trocar dentro da transação")
    void accountTransactions() {
        assertThat(accountTransactions.forAccount(ana, () ->
                jdbc.sql("select count(*) from account_link").query(Long.class).single())).isEqualTo(3L);
        assertThat(accountTransactions.forInvite(hash("codeB"), () ->
                jdbc.sql("select count(*) from account_invite").query(Long.class).single())).isEqualTo(1L);
        assertThat(accountTransactions.forAccount(bia, () ->
                jdbc.sql("select count(*) from account_link").query(Long.class).single())).isEqualTo(1L);
        assertThatThrownBy(() -> accountTransactions.forAccount(ana, () -> accountTransactions.forAccount(bia, () -> 1)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> accountTransactions.forAccount(ana, () -> accountTransactions.forInvite(hash("codeB"), () -> 1)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> accountTransactions.forInvite(" ", () -> 1)).isInstanceOf(AccessDeniedException.class);
        assertThat(accountTransactions.forAccount(ana, () -> accountTransactions.forAccount(ana, () -> 7))).isEqualTo(7);
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-17T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    @DisplayName("CA9: 6ª tentativa trava por 15 minutos, volta depois, vale para e-mail inexistente e tudo fica registrado")
    void lockoutByEmail() {
        var clock = new MutableClock();
        var attempts = new LoginAttempts(jdbc, clock, 5, Duration.ofMinutes(15), 20);
        for (String email : new String[]{"Ana@x.com", "ghost@x.com"}) {
            for (int i = 0; i < 5; i++) {
                assertThat(attempts.check(email, "10.0.0." + i)).isEqualTo(Status.OPEN);
                attempts.record(email, "10.0.0." + i, Result.WRONG_PASSWORD);
                clock.now = clock.now.plusSeconds(10);
            }
            assertThat(attempts.check(email.toUpperCase(), "10.9.9.9")).isEqualTo(Status.EMAIL_LOCKED);
            attempts.record(email, "10.9.9.9", Result.LOCKED);
            clock.now = clock.now.plus(Duration.ofMinutes(14));
            assertThat(attempts.check(email, "10.9.9.9")).isEqualTo(Status.EMAIL_LOCKED);
            clock.now = clock.now.plus(Duration.ofMinutes(2));
            assertThat(attempts.check(email, "10.9.9.9")).isEqualTo(Status.OPEN);
            attempts.record(email, "10.9.9.9", Result.WRONG_PASSWORD);
            assertThat(attempts.check(email, "10.9.9.9")).isEqualTo(Status.OPEN);
        }
        assertThat(admin.queryForObject("select count(*) from login_attempt where email = 'ana@x.com'", Integer.class)).isEqualTo(7);
        assertThat(admin.queryForObject("select count(*) from login_attempt where result = 'LOCKED'", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("CA9: acerto no meio zera a contagem; desafio recusado não conta como senha errada")
    void successAndChallengeBreakNothing() {
        var attempts = new LoginAttempts(jdbc, new MutableClock(), 5, Duration.ofMinutes(15), 20);
        for (int i = 0; i < 4; i++) attempts.record("c@x.com", "10.1.1.1", Result.WRONG_PASSWORD);
        attempts.record("c@x.com", "10.1.1.1", Result.CHALLENGE_FAILED);
        assertThat(attempts.check("c@x.com", "10.1.1.1")).isEqualTo(Status.OPEN);
        attempts.record("c@x.com", "10.1.1.1", Result.SUCCESS);
        attempts.record("c@x.com", "10.1.1.1", Result.WRONG_PASSWORD);
        assertThat(attempts.check("c@x.com", "10.1.1.1")).isEqualTo(Status.OPEN);
    }

    @Test
    @DisplayName("CA9 / D4: 20 falhas do mesmo IP em e-mails diferentes dão limite por IP até a janela esvaziar")
    void limitByIp() {
        var clock = new MutableClock();
        var attempts = new LoginAttempts(jdbc, clock, 5, Duration.ofMinutes(15), 20);
        for (int i = 0; i < 20; i++) {
            assertThat(attempts.check("u" + i + "@x.com", "10.2.2.2")).isEqualTo(Status.OPEN);
            attempts.record("u" + i + "@x.com", "10.2.2.2", Result.WRONG_PASSWORD);
        }
        assertThat(attempts.check("novo@x.com", "10.2.2.2")).isEqualTo(Status.IP_LIMITED);
        assertThat(attempts.check("novo@x.com", "10.3.3.3")).isEqualTo(Status.OPEN);
        clock.now = clock.now.plus(Duration.ofMinutes(16));
        assertThat(attempts.check("novo@x.com", "10.2.2.2")).isEqualTo(Status.OPEN);
    }
}

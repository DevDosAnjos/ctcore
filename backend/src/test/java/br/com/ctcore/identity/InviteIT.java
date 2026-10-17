package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.db.PostgresIT;
import br.com.ctcore.identity.IdentityException.Code;
import br.com.ctcore.identity.InviteRules.Inviter;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

class InviteIT extends PostgresIT {
    JdbcTemplate admin = adminJdbc();
    @Autowired JdbcClient jdbc;
    @Autowired AccountTransactions accountTx;
    @Autowired br.com.ctcore.isolation.OrganizationTransactions orgTx;
    @Autowired ScopedAudit audit;
    @Autowired PasswordPolicy policy;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountService accounts;
    @Autowired LinkService links;
    @Autowired SessionService sessions;

    AccountIT.MutableClock clock = new AccountIT.MutableClock();
    List<String> mails = new ArrayList<>();
    InviteService invites;
    UUID org = UUID.randomUUID(), org2 = UUID.randomUUID(), unit = UUID.randomUUID(), unit2 = UUID.randomUUID();
    UUID coordinatorAccount = UUID.randomUUID();
    Inviter coordinator, administrator;

    @BeforeEach
    void data() {
        AccountMailer mailer = new AccountMailer() {
            public void invite(String to, String code) { mails.add(to); }
            public void passwordReset(String to, String code) {}
            public void passwordChanged(String to) {}
        };
        invites = new InviteService(jdbc, orgTx, accountTx, mailer, audit, policy, encoder,
                new LoginAttempts(jdbc, clock, 5, Duration.ofMinutes(15), 20), clock);
        admin.update("insert into organization (id, name) values (?, 'Org'), (?, 'Org2')", org, org2);
        admin.update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'U', ?), (?, ?, 'U2', ?)",
                unit, org, "u-" + unit, unit2, org, "u-" + unit2);
        admin.update("insert into account (id, email, status) values (?, 'coord@x.com', 'ACTIVE')", coordinatorAccount);
        coordinator = new Inviter(coordinatorAccount, Role.COORDINATOR, Set.of());
        administrator = new Inviter(coordinatorAccount, Role.ADMINISTRATOR, Set.of(unit));
    }

    @AfterEach
    void clean() {
        for (String t : new String[]{"login_attempt", "account_session", "password_reset", "account_invite", "account_link_unit", "account_link"}) {
            admin.update("delete from " + t);
        }
        admin.update("delete from account");
        admin.execute("alter table audit_log disable trigger user");
        admin.update("delete from audit_log where organization_id in (?, ?)", org, org2);
        admin.execute("alter table audit_log enable trigger user");
        admin.update("delete from unit where organization_id = ?", org);
        admin.update("delete from organization where id in (?, ?)", org, org2);
    }

    String one(String sql, Object... args) {
        return admin.queryForObject(sql, String.class, args);
    }

    long audited(String action) {
        return admin.queryForObject("select count(*) from audit_log where action = ? and organization_id = ?", Long.class, action, org);
    }

    @Test
    @DisplayName("CA3, CA6, CA17: convite novo cria conta e vínculo pendentes, guarda só o hash, aceita uma vez e audita")
    void inviteAcceptOnce() {
        var issued = invites.invite(coordinator, org, "Novo@x.com", "Novo", Role.ADMINISTRATOR, Set.of(), null);
        assertThat(mails).containsExactly("Novo@x.com");
        assertThat(one("select status from account where lower(email) = 'novo@x.com'")).isEqualTo("INVITED");
        assertThat(one("select status from account_link where id = ?", issued.linkId())).isEqualTo("PENDING");
        assertThat(admin.queryForObject("select count(*) from account_invite where code_hash = ?", Long.class, issued.code())).isZero();
        assertThat(one("select code_hash from account_invite where id = ?", issued.inviteId())).isEqualTo(Codes.hash(issued.code()));

        assertThatThrownBy(() -> invites.accept(issued.code(), "abcdefg1", "1.1.1.1"))
                .isInstanceOfSatisfying(IdentityException.class, e -> assertThat(e.code()).isEqualTo(Code.PASSWORD_WEAK));
        assertThatThrownBy(() -> invites.accept(issued.code(), "Password1", "1.1.1.1"))
                .isInstanceOfSatisfying(IdentityException.class, e -> assertThat(e.code()).isEqualTo(Code.PASSWORD_LEAKED));
        assertThat(one("select status from account_link where id = ?", issued.linkId())).isEqualTo("PENDING");

        var accepted = invites.accept(issued.code(), "Abcdefg1", "1.1.1.1");
        assertThat(accepted.accountId()).isEqualTo(issued.accountId());
        assertThat(one("select status from account_link where id = ?", issued.linkId())).isEqualTo("ACTIVE");
        assertThat(one("select status from account where id = ?", issued.accountId())).isEqualTo("ACTIVE");
        assertThat(one("select password_hash from account where id = ?", issued.accountId())).startsWith("$argon2id$");
        assertThatThrownBy(() -> invites.accept(issued.code(), "Abcdefg1", "1.1.1.1"))
                .isInstanceOfSatisfying(IdentityException.class, e -> assertThat(e.detail()).isEqualTo("USED"));

        assertThat(audited("invite.created")).isEqualTo(1);
        assertThat(audited("invite.accepted")).isEqualTo(1);
        assertThat(audited("link.created")).isEqualTo(1);
        assertThat(admin.queryForObject("select count(*) from audit_log where organization_id = ? and (after_data::text ~* '(senha|password|token|code)' "
                + "or before_data::text ~* '(senha|password|token|code)')", Long.class, org)).isZero();
    }

    @Test
    @DisplayName("CA6: desconhecido, cancelado pelo reenvio, vencido no 8º dia; 3 reenvios por dia e o 4º recusado")
    void invalidStatesAndResendLimit() {
        assertThatThrownBy(() -> invites.accept("nope", "Abcdefg1", "1.1.1.1"))
                .isInstanceOfSatisfying(IdentityException.class, e -> {
                    assertThat(e.code()).isEqualTo(Code.INVITE_INVALID);
                    assertThat(e.detail()).isEqualTo("UNKNOWN");
                });
        var first = invites.invite(coordinator, org, "a@x.com", "A", Role.ADMINISTRATOR, Set.of(), null);
        var second = invites.resend(coordinator, org, first.linkId());
        assertThatThrownBy(() -> invites.accept(first.code(), "Abcdefg1", "1.1.1.1"))
                .isInstanceOfSatisfying(IdentityException.class, e -> assertThat(e.detail()).isEqualTo("CANCELED"));
        invites.resend(coordinator, org, first.linkId());
        invites.resend(coordinator, org, first.linkId());
        assertThatThrownBy(() -> invites.resend(coordinator, org, first.linkId()))
                .isInstanceOfSatisfying(IdentityException.class, e -> assertThat(e.code()).isEqualTo(Code.TOO_MANY_REQUESTS));
        assertThat(audited("invite.resent")).isEqualTo(3);

        var third = invites.invite(coordinator, org, "b@x.com", "B", Role.ADMINISTRATOR, Set.of(), null);
        clock.now = clock.now.plus(Duration.ofDays(7)).plusSeconds(1);
        assertThatThrownBy(() -> invites.accept(third.code(), "Abcdefg1", "1.1.1.1"))
                .isInstanceOfSatisfying(IdentityException.class, e -> assertThat(e.detail()).isEqualTo("EXPIRED"));
        assertThat(second.code()).isNotEqualTo(first.code());
        // no dia seguinte o limite zera
        clock.now = clock.now.plus(Duration.ofDays(1));
        invites.resend(coordinator, org, third.linkId());
    }

    @Test
    @DisplayName("CA3, CA4: convite fora da cascata é negado; aluno menor não ganha conta; maior sim, pelo sistema")
    void cascadeAndAdulthood() {
        assertThatThrownBy(() -> invites.invite(coordinator, org, "t@x.com", "T", Role.TEACHER, Set.of(unit), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> invites.invite(administrator, org, "t@x.com", "T", Role.TEACHER, Set.of(unit2), null))
                .isInstanceOf(AccessDeniedException.class);
        invites.invite(administrator, org, "t@x.com", "T", Role.TEACHER, Set.of(unit), null);
        assertThat(admin.queryForObject("select count(*) from account_link_unit where unit_id = ?", Long.class, unit)).isEqualTo(1);

        LocalDate today = LocalDate.of(2026, 10, 17);
        assertThatThrownBy(() -> invites.invite(Inviter.system(), org, "kid@x.com", "Kid", Role.STUDENT, Set.of(), today.minusYears(18).plusDays(1)))
                .isInstanceOfSatisfying(IdentityException.class, e -> assertThat(e.code()).isEqualTo(Code.MINOR_HAS_NO_ACCOUNT));
        assertThat(admin.queryForObject("select count(*) from account where lower(email) = 'kid@x.com'", Long.class)).isZero();
        invites.invite(Inviter.system(), org, "adult@x.com", "Adult", Role.STUDENT, Set.of(), today.minusYears(18));
        invites.invite(Inviter.system(), org, "resp@x.com", "Resp", Role.GUARDIAN, Set.of(), null);
        assertThat(admin.queryForObject("select count(*) from account_invite where sent_by is null", Long.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("CA5: e-mail com conta recebe só novo vínculo; senha errada não ativa e conta como falha; certa ativa")
    void existingAccount() {
        UUID id = accounts.provision("Ana@x.com", "Ana", null, "Abcdefg1");
        assertThat(accounts.provision("ana@X.com", "Outra", null, "Zzzzzzz9")).isEqualTo(id);
        assertThat(encoder.matches("Abcdefg1", one("select password_hash from account where id = ?", id))).isTrue();

        var issued = invites.invite(Inviter.system(), org, "ana@x.com", "Ana", Role.STUDENT, Set.of(), LocalDate.of(1990, 1, 1));
        assertThat(issued.accountId()).isEqualTo(id);
        assertThat(admin.queryForObject("select count(*) from account where lower(email) = 'ana@x.com'", Long.class)).isEqualTo(1);
        assertThatThrownBy(() -> invites.accept(issued.code(), "Errada123", "2.2.2.2"))
                .isInstanceOfSatisfying(IdentityException.class, e -> assertThat(e.code()).isEqualTo(Code.INVALID_CREDENTIALS));
        assertThat(one("select status from account_link where id = ?", issued.linkId())).isEqualTo("PENDING");
        assertThat(admin.queryForObject("select count(*) from login_attempt where result = 'WRONG_PASSWORD' and email = 'ana@x.com'", Long.class)).isEqualTo(1);
        invites.accept(issued.code(), "Abcdefg1", "2.2.2.2");
        assertThat(one("select status from account_link where id = ?", issued.linkId())).isEqualTo("ACTIVE");

        assertThatThrownBy(() -> accounts.provision("x@x.com", "X", null, "fraca"))
                .isInstanceOfSatisfying(IdentityException.class, e -> assertThat(e.code()).isEqualTo(Code.PASSWORD_WEAK));
        assertThat(accounts.find("nobody@x.com")).isEmpty();
    }

    @Test
    @DisplayName("CA17: cancelar convite, encerrar vínculo e desativar conta auditam; desativar grava uma linha por organização")
    void cancelEndDeactivateAudit() {
        var staff = new AuditActor(AuditActor.Type.ACCOUNT, coordinatorAccount, "COORDINATOR");
        var pending = invites.invite(coordinator, org, "c@x.com", "C", Role.ADMINISTRATOR, Set.of(), null);
        invites.cancel(coordinator, org, pending.linkId());
        assertThat(audited("invite.canceled")).isEqualTo(1);
        assertThatThrownBy(() -> invites.accept(pending.code(), "Abcdefg1", "1.1.1.1"))
                .isInstanceOfSatisfying(IdentityException.class, e -> assertThat(e.detail()).isEqualTo("CANCELED"));
        assertThatThrownBy(() -> invites.cancel(coordinator, org, pending.linkId())).isInstanceOf(AccessDeniedException.class);

        UUID person = accounts.provision("p@x.com", "P", null, "Abcdefg1");
        UUID l1 = activeLink(person, org, "TEACHER"), l2 = activeLink(person, org2, "STUDENT");
        links.end(staff, org, l1);
        assertThat(one("select status from account_link where id = ?", l1)).isEqualTo("ENDED");
        assertThat(audited("link.ended")).isEqualTo(1);
        assertThatThrownBy(() -> links.end(staff, org, l2)).isInstanceOf(AccessDeniedException.class);

        UUID l3 = activeLink(person, org, "STUDENT");
        accounts.deactivate(person, staff);
        assertThat(one("select status from account where id = ?", person)).isEqualTo("DEACTIVATED");
        assertThat(audited("account.deactivated")).isEqualTo(1);
        assertThat(admin.queryForObject("select count(*) from audit_log where action = 'account.deactivated' and organization_id = ?", Long.class, org2)).isEqualTo(1);
        assertThat(l3).isNotNull();
        assertThatThrownBy(() -> invites.invite(Inviter.system(), org, "p@x.com", "P", Role.GUARDIAN, Set.of(), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("CA14, CA17: remover o último vínculo de gestão e desativar encerram todas as sessões; remover outro vínculo de gestão não")
    void sessionsEndWithManagementLinkOrAccount() {
        var staff = new AuditActor(AuditActor.Type.ACCOUNT, coordinatorAccount, "COORDINATOR");
        UUID person = accounts.provision("m@x.com", "M", null, "Abcdefg1");
        UUID admin1 = activeLink(person, org, "ADMINISTRATOR"), admin2 = activeLink(person, org2, "TEACHER");
        activeLink(person, org2, "STUDENT");
        sessions.create(person, "1.1.1.1", "a");
        sessions.create(person, "1.1.1.1", "b");
        links.end(staff, org, admin1);
        assertThat(admin.queryForObject("select count(*) from account_session", Long.class)).isEqualTo(2);
        links.end(staff, org2, admin2);
        assertThat(admin.queryForObject("select count(*) from account_session", Long.class)).isZero();
        assertThat(admin.queryForObject("select count(*) from audit_log where action = 'account.sessions_ended'", Long.class)).isEqualTo(1);

        sessions.create(person, "1.1.1.1", "c");
        accounts.deactivate(person, staff);
        assertThat(admin.queryForObject("select count(*) from account_session", Long.class)).isZero();
        assertThat(admin.queryForObject("select count(*) from audit_log where action = 'account.sessions_ended'", Long.class)).isEqualTo(2);
    }

    UUID activeLink(UUID account, UUID organization, String role) {
        return admin.queryForObject("insert into account_link (account_id, organization_id, role, status) values (?, ?, ?, 'ACTIVE') returning id",
                UUID.class, account, organization, role);
    }
}

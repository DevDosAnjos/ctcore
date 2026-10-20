package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.IdentityException;
import br.com.ctcore.identity.InviteRules.Inviter;
import br.com.ctcore.identity.InviteService;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.mail.EmailService;
import br.com.ctcore.mail.EmailTemplate;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RN-RSP-01 a 06: responsáveis do aluno (no máximo dois, exatamente um financeiro, menor nunca sem responsável), cada um com conta e
 * convite próprios. Convite e e-mail só saem depois do commit (a transação desfaz tudo se algo falha).
 */
@Service
public class StudentGuardians {
    /** Corpo de um responsável; {@code financial} e {@code reason} só valem em ligar e trocar. */
    public record Input(String name, String cpf, String email, String phone, Boolean financial, String reason) {}

    record Clean(String name, String cpf, String email, String phone, boolean financial) {}

    /** E-mail de convite a enviar depois do commit. */
    public record Mail(String email, String code) {}

    private record Existing(UUID id, String cpf, UUID accountId) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Students students;
    private final InviteService invites;
    private final AuditService audit;
    private final EmailService email;
    private final Clock clock;

    public StudentGuardians(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Students students, InviteService invites,
            AuditService audit, EmailService email, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.students = students;
        this.invites = invites;
        this.audit = audit;
        this.email = email;
        this.clock = clock;
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name());
    }

    private static Inviter inviter(AccountPrincipal who) {
        return new Inviter(who.accountId(), who.role(), who.units());
    }

    static Clean clean(Input in, boolean financial) {
        if (in == null) throw new ApiValidationException("guardians", "responsável inválido");
        String name = in.name() == null ? "" : in.name().strip();
        if (name.length() < 2 || name.length() > 80) throw new ApiValidationException("guardians.name", "de 2 a 80 caracteres");
        String cpf = StudentRegistration.cpf("guardians.cpf", in.cpf());
        if (cpf == null) throw new ApiValidationException("guardians.cpf", "obrigatório");
        String mail = br.com.ctcore.organization.NewUnit.email("guardians.email", in.email() == null || in.email().isBlank() ? null : in.email().strip());
        if (mail == null) throw new ApiValidationException("guardians.email", "obrigatório");
        String phone = br.com.ctcore.organization.NewUnit.phone("guardians.phone", in.phone());
        if (phone == null) throw new ApiValidationException("guardians.phone", "obrigatório");
        return new Clean(name, cpf, mail, phone, financial);
    }

    /** R4: menor exige ao menos um; no máximo dois; com um só ele é o financeiro, com dois exatamente um. */
    static List<Clean> prepare(boolean adult, List<Input> in) {
        List<Input> list = in == null ? List.of() : in;
        if (list.size() > 2) throw new ApiValidationException("guardians", "no máximo dois responsáveis");
        if (list.isEmpty() && !adult) throw new ApiValidationException("guardians", "aluno menor de 18 anos precisa de ao menos um responsável");
        long financial = list.stream().filter(g -> g != null && Boolean.TRUE.equals(g.financial())).count();
        if (list.size() == 2 && financial != 1) throw new ApiValidationException("guardians", "informe exatamente um responsável financeiro");
        List<Clean> out = new ArrayList<>();
        for (Input g : list) out.add(clean(g, list.size() == 1 || (g != null && Boolean.TRUE.equals(g.financial()))));
        if (out.size() == 2 && (out.get(0).cpf().equals(out.get(1).cpf()) || out.get(0).email().equalsIgnoreCase(out.get(1).email()))) {
            throw new ApiValidationException("guardians", "os dois responsáveis precisam ser pessoas diferentes");
        }
        return out;
    }

    static String reason(String raw) {
        String r = raw == null ? "" : raw.strip();
        if (r.length() < 3 || r.length() > 200) throw new ApiValidationException("reason", "de 3 a 200 caracteres");
        return r;
    }

    public void send(List<Mail> mails) {
        for (Mail m : mails) {
            if (m.code() != null) invites.sendInvite(m.email(), m.code());
        }
    }

    /** Dentro da transação da organização, com a linha do aluno já travada. Liga o responsável e devolve o e-mail de convite, se houver. */
    Mail attach(AccountPrincipal who, UUID studentId, Clean g, String reason, String ip) {
        UUID org = who.organizationId();
        if (count(org, studentId) >= 2) throw new ApiStatusException(ErrorCode.GUARDIAN_LIMIT);
        try {
            Existing byEmail = jdbc.sql("select g.id, g.cpf, g.account_id from guardian g join account a on a.id = g.account_id "
                    + "where g.organization_id = :org and lower(a.email) = lower(:e)").param("org", org).param("e", g.email())
                    .query((rs, n) -> new Existing(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class))).optional().orElse(null);
            UUID byCpf = jdbc.sql("select id from guardian where organization_id = :org and cpf = :cpf").param("org", org).param("cpf", g.cpf())
                    .query(UUID.class).optional().orElse(null);
            if ((byEmail != null && !byEmail.cpf().equals(g.cpf())) || (byCpf != null && (byEmail == null || !byCpf.equals(byEmail.id())))) {
                throw new ApiStatusException(ErrorCode.GUARDIAN_CONFLICT);
            }
            UUID guardianId;
            InviteService.Issued issued = null;
            if (byEmail != null) {
                guardianId = byEmail.id();
                if (jdbc.sql("select count(*) from student_guardian where student_id = :s and guardian_id = :g").param("s", studentId)
                        .param("g", guardianId).query(Integer.class).single() > 0) {
                    throw new ApiStatusException(ErrorCode.CONFLICT);
                }
                String status = jdbc.sql("select status from account_link where account_id = :a and organization_id = :org and role = 'GUARDIAN'")
                        .param("a", byEmail.accountId()).param("org", org).query(String.class).optional().orElse("NONE");
                if (!status.equals("ACTIVE") && !status.equals("PENDING")) {
                    issued = invites.inviteGuardian(inviter(who), org, g.email(), g.name());
                }
            } else {
                issued = invites.inviteGuardian(inviter(who), org, g.email(), g.name());
                guardianId = UUID.randomUUID();
                jdbc.sql("insert into guardian (id, organization_id, account_id, name, cpf, phone) values (:id, :org, :a, :n, :cpf, :p)")
                        .param("id", guardianId).param("org", org).param("a", issued.accountId()).param("n", g.name()).param("cpf", g.cpf())
                        .param("p", g.phone()).update();
            }
            if (g.financial()) {
                jdbc.sql("update student_guardian set financial = false where student_id = :s").param("s", studentId).update();
            }
            jdbc.sql("insert into student_guardian (organization_id, student_id, guardian_id, financial) values (:org, :s, :g, :f)")
                    .param("org", org).param("s", studentId).param("g", guardianId).param("f", g.financial()).update();
            audit.record(new AuditEntry(actor(who), "student.guardian_linked", "student", studentId.toString(), null, null,
                    Map.of("guardianId", guardianId.toString(), "financial", g.financial()), reason, ip));
            return issued == null || issued.code() == null ? null : new Mail(g.email(), issued.code());
        } catch (DuplicateKeyException e) {
            throw new ApiStatusException(ErrorCode.GUARDIAN_CONFLICT);
        }
    }

    private int count(UUID org, UUID studentId) {
        return jdbc.sql("select count(*) from student_guardian where organization_id = :org and student_id = :s").param("org", org)
                .param("s", studentId).query(Integer.class).single();
    }

    /** Trava a linha do aluno (serializa ligações concorrentes) e confere o alcance de quem chama; fora dele, 404. */
    private Students.Detail lock(AccountPrincipal who, UUID studentId) {
        Students.Detail d = students.detail(who, studentId);
        jdbc.sql("select id from student where organization_id = :org and id = :id for update").param("org", who.organizationId())
                .param("id", studentId).query(UUID.class).single();
        return d;
    }

    public Students.Detail link(AccountPrincipal who, UUID studentId, Input body, String ip) {
        students.requireAccess(who);
        String reason = reason(body == null ? null : body.reason());
        List<Mail> mails = new ArrayList<>();
        Students.Detail detail = transactions.execute(() -> {
            lock(who, studentId);
            int existing = count(who.organizationId(), studentId);
            Clean g = clean(body, existing == 0 || Boolean.TRUE.equals(body.financial()));
            Mail mail = attach(who, studentId, g, reason, ip);
            if (mail != null) mails.add(mail);
            return students.detail(who, studentId);
        });
        send(mails);
        return detail;
    }

    public Students.Detail setFinancial(AccountPrincipal who, UUID studentId, UUID guardianId, Boolean financial, String ip) {
        students.requireAccess(who);
        if (!Boolean.TRUE.equals(financial)) throw new ApiValidationException("financial", "só é possível indicar quem passa a ser o financeiro");
        return transactions.execute(() -> {
            lock(who, studentId);
            requireLinked(who, studentId, guardianId);
            boolean already = jdbc.sql("select financial from student_guardian where student_id = :s and guardian_id = :g").param("s", studentId)
                    .param("g", guardianId).query(Boolean.class).single();
            if (!already) {
                jdbc.sql("update student_guardian set financial = false where student_id = :s").param("s", studentId).update();
                jdbc.sql("update student_guardian set financial = true where student_id = :s and guardian_id = :g").param("s", studentId)
                        .param("g", guardianId).update();
                audit.record(new AuditEntry(actor(who), "student.guardian_financial_changed", "student", studentId.toString(), null, null,
                        Map.of("guardianId", guardianId.toString()), null, ip));
            }
            return students.detail(who, studentId);
        });
    }

    private void requireLinked(AccountPrincipal who, UUID studentId, UUID guardianId) {
        if (jdbc.sql("select count(*) from student_guardian where organization_id = :org and student_id = :s and guardian_id = :g")
                .param("org", who.organizationId()).param("s", studentId).param("g", guardianId).query(Integer.class).single() == 0) {
            throw new ApiStatusException(ErrorCode.NOT_FOUND);
        }
    }

    public Students.Detail unlink(AccountPrincipal who, UUID studentId, UUID guardianId, String rawReason, String ip) {
        students.requireAccess(who);
        String reason = reason(rawReason);
        String[] notify = new String[1];
        Students.Detail detail = transactions.execute(() -> {
            Students.Detail d = lock(who, studentId);
            requireLinked(who, studentId, guardianId);
            int total = count(who.organizationId(), studentId);
            if (d.minor() && total <= 1) throw new ApiStatusException(ErrorCode.MINOR_NEEDS_GUARDIAN);
            boolean financial = jdbc.sql("select financial from student_guardian where student_id = :s and guardian_id = :g").param("s", studentId)
                    .param("g", guardianId).query(Boolean.class).single();
            jdbc.sql("select unlink_student_guardian(:s, :g)").param("s", studentId).param("g", guardianId).query(Integer.class).single();
            if (financial) {
                jdbc.sql("update student_guardian set financial = true where student_id = :s").param("s", studentId).update();
            }
            var guardian = jdbc.sql("select g.account_id, a.email, g.email_problem_at is not null from guardian g join account a on a.id = g.account_id "
                    + "where g.organization_id = :org and g.id = :g").param("org", who.organizationId()).param("g", guardianId)
                    .query((rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getString(2), rs.getBoolean(3)}).single();
            UUID accountId = (UUID) guardian[0];
            if (!(Boolean) guardian[2]) notify[0] = (String) guardian[1];
            int left = jdbc.sql("select count(*) from student_guardian where organization_id = :org and guardian_id = :g").param("org", who.organizationId())
                    .param("g", guardianId).query(Integer.class).single();
            if (left == 0) endAccess(who.organizationId(), accountId);
            audit.record(new AuditEntry(actor(who), "student.guardian_unlinked", "student", studentId.toString(), null, null,
                    Map.of("guardianId", guardianId.toString()), reason, ip));
            return students.detail(who, studentId);
        });
        if (notify[0] != null) email.send(notify[0], EmailTemplate.GUARDIAN_UNLINKED);
        return detail;
    }

    /** RN-RSP-05: sem aluno nenhum na organização, o vínculo GUARDIAN termina e o convite pendente é cancelado. */
    private void endAccess(UUID org, UUID accountId) {
        var now = java.time.OffsetDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC);
        jdbc.sql("update account_invite set canceled_at = :now where organization_id = :org and used_at is null and canceled_at is null "
                + "and link_id in (select id from account_link where account_id = :a and organization_id = :org and role = 'GUARDIAN')")
                .param("now", now).param("org", org).param("a", accountId).update();
        jdbc.sql("update account_link set status = 'ENDED', ends_on = :d where account_id = :a and organization_id = :org and role = 'GUARDIAN' "
                + "and status in ('PENDING', 'ACTIVE')").param("d", LocalDate.now(clock)).param("a", accountId).param("org", org).update();
    }

    public void resendInvite(AccountPrincipal who, UUID studentId, UUID guardianId, String ip) {
        students.requireAccess(who);
        UUID accountId = transactions.execute(() -> {
            lock(who, studentId);
            requireLinked(who, studentId, guardianId);
            UUID account = jdbc.sql("select account_id from guardian where organization_id = :org and id = :g").param("org", who.organizationId())
                    .param("g", guardianId).query(UUID.class).single();
            String status = jdbc.sql("select status from account_link where account_id = :a and organization_id = :org and role = 'GUARDIAN'")
                    .param("a", account).param("org", who.organizationId()).query(String.class).optional().orElse("NONE");
            if (status.equals("ACTIVE")) throw new IdentityException(IdentityException.Code.ALREADY_LINKED, null);
            return account;
        });
        invites.resendGuardian(inviter(who), who.organizationId(), accountId);
        transactions.execute(() -> {
            audit.record(new AuditEntry(actor(who), "student.guardian_invite_resent", "student", studentId.toString(), null, null,
                    Map.of("guardianId", guardianId.toString()), null, ip));
            return null;
        });
    }
}

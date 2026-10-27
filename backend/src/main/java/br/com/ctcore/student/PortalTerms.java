package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.InviteRules;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.terms.Terms;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RN-MAT-11 a RN-MAT-14, RN-POR-03, RN-LGP-01: aceite do termo de matrícula e uso de imagem no portal. Quem aceita é o próprio aluno adulto ou o
 * responsável financeiro do menor (um só ponto: {@link #canAccept}). Os registros só são inseridos; o estado atual é o último deles.
 */
@Service
public class PortalTerms {
    public record Text(int version, String body) {}

    public record ImageUse(int version, String body, boolean granted) {}

    public record Pending(UUID id, String name, Text term, ImageUse imageUse) {}

    public record Acceptance(UUID studentId, int version, boolean imageUse) {}

    /** {@code created}: o servidor gravou algo (201); senão a chamada só repetiu o que já estava feito (200). */
    public record Outcome<T>(boolean created, T body) {}

    /** Aluno ligado à conta da sessão. */
    record Linked(UUID id, String name, LocalDate birth, String zone, boolean self, boolean financial, boolean enrolled) {
        boolean minor(LocalDate today) {
            return !InviteRules.adult(birth, today);
        }
    }

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Terms terms;
    private final AuditService audit;
    private final Clock clock;

    public PortalTerms(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Terms terms, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.terms = terms;
        this.audit = audit;
        this.clock = clock;
    }

    /** RN-POR-03: erro 403 {@code TERM_PENDING} se a conta tem aluno com termo pendente que ela mesma deve aceitar. */
    public void requireAccepted(AccountPrincipal who) {
        requirePortal(who);
        boolean pending = transactions.execute(() -> candidates(who, null, false).stream().anyMatch(c -> canAccept(c) && c.enrolled() && pending(who.organizationId(), c.id())));
        if (pending) throw new ApiStatusException(ErrorCode.TERM_PENDING);
    }

    /** Alunos com termo pendente que a conta aceita, com o texto vigente do termo e do uso de imagem. */
    public List<Pending> pending(AccountPrincipal who) {
        requirePortal(who);
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            Terms.Version term = terms.current(org, Terms.Kind.ENROLLMENT);
            return candidates(who, null, false).stream().filter(c -> canAccept(c) && c.enrolled() && pending(org, c.id()))
                    .map(c -> new Pending(c.id(), c.name(), new Text(term.version(), term.body()), imageUse(org, c.id()))).toList();
        });
    }

    public Outcome<Acceptance> accept(AccountPrincipal who, UUID studentId, Integer version, Boolean imageUse, String ip) {
        requirePortal(who);
        if (version == null) throw new ApiValidationException("version", "obrigatório");
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            Linked student = acceptable(who, studentId, true);
            Terms.Version current = terms.current(org, Terms.Kind.ENROLLMENT);
            boolean granted = imageUse(org, studentId).granted();
            if (!student.enrolled()) return new Outcome<>(false, new Acceptance(studentId, current.version(), granted));
            if (version != current.version()) throw new ApiStatusException(ErrorCode.TERMS_VERSION_CONFLICT);
            if (!pending(org, studentId)) return new Outcome<>(false, new Acceptance(studentId, current.version(), granted));
            insert(who, studentId, "ENROLLMENT", "ACCEPTED", current.version(), ip, "term.accepted");
            if (Boolean.TRUE.equals(imageUse) && !granted) {
                insert(who, studentId, "IMAGE_USE", "ACCEPTED", terms.current(org, Terms.Kind.IMAGE_USE).version(), ip, "image-use.granted");
                granted = true;
            }
            return new Outcome<>(true, new Acceptance(studentId, current.version(), granted));
        });
    }

    public ImageUse imageUse(AccountPrincipal who, UUID studentId) {
        requirePortal(who);
        return transactions.execute(() -> {
            acceptable(who, studentId, false);
            return imageUse(who.organizationId(), studentId);
        });
    }

    /** Concede (na versão vigente) ou revoga o uso de imagem; cada mudança é um registro novo, e repetir o estado atual não grava. */
    public ImageUse setImageUse(AccountPrincipal who, UUID studentId, Boolean granted, Integer version, String ip) {
        requirePortal(who);
        if (granted == null) throw new ApiValidationException("granted", "obrigatório");
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            acceptable(who, studentId, true);
            int current = terms.current(org, Terms.Kind.IMAGE_USE).version();
            if (granted && version != null && version != current) throw new ApiStatusException(ErrorCode.TERMS_VERSION_CONFLICT);
            if (imageUse(org, studentId).granted() != granted) {
                insert(who, studentId, "IMAGE_USE", granted ? "ACCEPTED" : "REVOKED", current, ip, granted ? "image-use.granted" : "image-use.revoked");
            }
            return imageUse(org, studentId);
        });
    }

    // ---- regra de quem aceita (reusada pela confirmação de modalidade) ----

    void requirePortal(AccountPrincipal who) {
        if (who == null || (who.role() != Role.GUARDIAN && who.role() != Role.STUDENT)) throw new AccessDeniedException("Portal session required");
    }

    /** RN-COB-13: quem paga é quem aceita o termo (adulto com conta ou responsável financeiro do menor). Dentro da transação: 404 se não ligado, 403 se sem esse direito. */
    public void requirePayer(AccountPrincipal who, UUID studentId) {
        requirePortal(who);
        acceptable(who, studentId, false);
    }

    boolean canAccept(Linked c) {
        return c.minor(LocalDate.ofInstant(clock.instant(), ZoneId.of(c.zone()))) ? c.financial() : c.self();
    }

    /** Aluno ligado à conta que ela pode aceitar: 404 se não ligado (ou de outra organização), 403 se ligado e sem esse direito. Dentro da transação. */
    Linked acceptable(AccountPrincipal who, UUID studentId, boolean lock) {
        Linked student = candidates(who, studentId, lock).stream().findFirst().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
        if (!canAccept(student)) throw new AccessDeniedException("Permission denied");
        return student;
    }

    /** Termo pendente: sem aceite, ou última versão aceita menor que a maior publicada com reaccept (D2). Quem chama confere a matrícula. */
    boolean pending(UUID org, UUID studentId) {
        int last = jdbc.sql("select coalesce(max(version), -1) from term_acceptance where organization_id = :org and student_id = :id and kind = 'ENROLLMENT' and event = 'ACCEPTED'")
                .param("org", org).param("id", studentId).query(Integer.class).single();
        return last < requiredVersion(org, "ENROLLMENT");
    }

    private int requiredVersion(UUID org, String kind) {
        return jdbc.sql("select coalesce(max(version), 0) from terms_version where organization_id = :org and kind = :kind and reaccept")
                .param("org", org).param("kind", kind).query(Integer.class).single();
    }

    private ImageUse imageUse(UUID org, UUID studentId) {
        Terms.Version current = terms.current(org, Terms.Kind.IMAGE_USE);
        boolean granted = jdbc.sql("select event = 'ACCEPTED' and version >= :required from term_acceptance where organization_id = :org and student_id = :id and kind = 'IMAGE_USE' "
                + "order by accepted_at desc, id limit 1").param("required", requiredVersion(org, "IMAGE_USE")).param("org", org).param("id", studentId)
                .query(Boolean.class).optional().orElse(false);
        return new ImageUse(current.version(), current.body(), granted);
    }

    List<Linked> candidates(AccountPrincipal who, UUID only, boolean lock) {
        String link = "from student_guardian sg join guardian g on g.organization_id = sg.organization_id and g.id = sg.guardian_id "
                + "where sg.organization_id = s.organization_id and sg.student_id = s.id and g.account_id = :acc";
        return jdbc.sql("select s.id, s.name, s.birth_date, u.timezone, coalesce(s.account_id = :acc, false), exists (select 1 " + link + " and sg.financial), "
                + "exists (select 1 from enrollment e where e.organization_id = s.organization_id and e.student_id = s.id) "
                + "from student s join unit u on u.organization_id = s.organization_id and u.id = s.registered_unit_id "
                + "where s.organization_id = :org and (s.account_id = :acc or exists (select 1 " + link + "))" + (only == null ? "" : " and s.id = :only")
                + " order by s.name, s.id" + (lock ? " for update of s" : ""))
                .param("org", who.organizationId()).param("acc", who.accountId()).param("only", only)
                .query((rs, n) -> new Linked(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, LocalDate.class), rs.getString(4), rs.getBoolean(5),
                        rs.getBoolean(6), rs.getBoolean(7))).list();
    }

    private void insert(AccountPrincipal who, UUID studentId, String kind, String event, int version, String ip, String action) {
        jdbc.sql("insert into term_acceptance (id, organization_id, student_id, kind, event, version, account_id, ip) values (:id, :org, :student, :kind, :event, :version, :acc, :ip)")
                .param("id", UUID.randomUUID()).param("org", who.organizationId()).param("student", studentId).param("kind", kind).param("event", event)
                .param("version", version).param("acc", who.accountId()).param("ip", ip).update();
        var after = new LinkedHashMap<String, Object>();
        after.put("studentId", studentId);
        after.put("version", version);
        audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), action, "term_acceptance", studentId.toString(),
                null, Map.of(), after, null, ip));
    }
}

package br.com.ctcore.billing;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RN-COB-11: cobrança avulsa de uma unidade para um aluno. Idempotente pelo {@code requestId}, que vira o id da cobrança (RNF-12).
 * É do tipo {@code ONE_OFF}: sem mês, modalidade ou plano, fica fora de toda regra de mensalidade.
 */
@Service
public class OneOffCharges {
    static final long MAX_CENTS = 10_000_000L;
    static final int MAX_DESCRIPTION = 80;

    public record Input(UUID requestId, UUID unitId, String description, Long amountCents, String dueOn) {}

    /** {@code created} falso quando é a repetição de um pedido já atendido (resposta 200). */
    public record Result(Charges.Row row, boolean created) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final AuditService audit;
    private final Charges charges;
    private final Clock clock;

    public OneOffCharges(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, AuditService audit, Charges charges, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.audit = audit;
        this.charges = charges;
        this.clock = clock;
    }

    private record Student(boolean minor, UUID guardian) {}

    private record Stored(UUID student, UUID unit, String description, long amount, LocalDate dueOn, String type) {}

    public Result create(AccountPrincipal who, UUID studentId, Input in, String ip) {
        if (permissions.effective(who).get(Permission.COLLECTIONS) == Access.NONE) throw new AccessDeniedException("Permission denied");
        if (in == null || in.unitId() == null) throw new ApiValidationException("unitId", "obrigatório");
        String description = in.description() == null ? "" : in.description().strip();
        if (description.isEmpty() || description.length() > MAX_DESCRIPTION) throw new ApiValidationException("description", "de 1 a " + MAX_DESCRIPTION + " caracteres");
        if (in.amountCents() == null || in.amountCents() < 1 || in.amountCents() > MAX_CENTS) throw new ApiValidationException("amountCents", "de 1 a " + MAX_CENTS);
        LocalDate dueOn = parse(in.dueOn());
        UUID org = who.organizationId(), unit = in.unitId();
        return transactions.execute(() -> {
            if (permissions.grant(who, Permission.COLLECTIONS, unit) != Access.FULL) throw new ApiStatusException(ErrorCode.NOT_FOUND);
            LocalDate today = todayOf(unit);
            Student student = jdbc.sql("select (birth_date + interval '18 years')::date > cast(:today as date), "
                    + "(select guardian_id from student_guardian where student_id = s.id and financial limit 1) from student s where s.id = :id and s.organization_id = :org")
                    .param("today", today).param("id", studentId).param("org", org)
                    .query((rs, n) -> new Student(rs.getBoolean(1), rs.getObject(2, UUID.class))).optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            if (dueOn.isBefore(today)) throw new ApiValidationException("dueOn", "não pode ser anterior a hoje");
            boolean enrolled = jdbc.sql("select exists (select 1 from enrollment_modality where student_id = :student and unit_id = :unit and organization_id = :org)")
                    .param("student", studentId).param("unit", unit).param("org", org).query(Boolean.class).single();
            if (!enrolled || student.minor() && student.guardian() == null) throw new ApiStatusException(ErrorCode.UNPROCESSABLE);
            UUID id = in.requestId() == null ? UUID.randomUUID() : in.requestId();
            int inserted = jdbc.sql("insert into charge (id, organization_id, unit_id, student_id, payer_guardian_id, type, description, due_on, amount_cents, origin, created_by_account_id) "
                    + "values (:id, :org, :unit, :student, :payer, 'ONE_OFF', :description, :due, :amount, 'MANUAL', :by) on conflict (id) do nothing")
                    .param("id", id).param("org", org).param("unit", unit).param("student", studentId).param("payer", student.minor() ? student.guardian() : null)
                    .param("description", description).param("due", dueOn).param("amount", in.amountCents()).param("by", who.accountId()).update();
            if (inserted == 0) return repeated(org, id, studentId, unit, description, in.amountCents(), dueOn);
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("type", "ONE_OFF");
            after.put("origin", "MANUAL");
            after.put("studentId", studentId.toString());
            after.put("description", description);
            after.put("amountCents", in.amountCents());
            after.put("dueOn", dueOn.toString());
            audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), "charge.created", "charge", id.toString(), unit, Map.of(),
                    after, null, ip));
            return new Result(charges.one(org, id), true);
        });
    }

    /** Mesmo id: só é repetição se tudo bate; outro dado, ou id de outra organização, é conflito. */
    private Result repeated(UUID org, UUID id, UUID student, UUID unit, String description, long amount, LocalDate dueOn) {
        Stored s = jdbc.sql("select student_id, unit_id, description, amount_cents, due_on, type from charge where organization_id = :org and id = :id")
                .param("org", org).param("id", id)
                .query((rs, n) -> new Stored(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getLong(4), rs.getObject(5, LocalDate.class), rs.getString(6)))
                .optional().orElseThrow(() -> new ApiStatusException(ErrorCode.CONFLICT));
        if (!s.equals(new Stored(student, unit, description, amount, dueOn, "ONE_OFF"))) throw new ApiStatusException(ErrorCode.CONFLICT);
        return new Result(charges.one(org, id), false);
    }

    private static LocalDate parse(String value) {
        if (value == null) throw new ApiValidationException("dueOn", "obrigatório");
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ApiValidationException("dueOn", "use AAAA-MM-DD");
        }
    }

    private LocalDate todayOf(UUID unit) {
        String zone = jdbc.sql("select timezone from unit where id = :unit").param("unit", unit).query(String.class).optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
        return LocalDate.now(clock.withZone(ZoneId.of(zone)));
    }
}

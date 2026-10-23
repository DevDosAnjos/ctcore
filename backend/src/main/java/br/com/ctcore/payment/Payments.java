package br.com.ctcore.payment;

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
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** RN-COB-12, RN-MAT-06: pagamento no balcão do valor total da cobrança em aberto, com recibo de número sequencial por unidade (RN-COB-16). */
@Service
public class Payments {
    static final Set<String> METHODS = Set.of("CASH", "PIX_KEY", "CARD_MACHINE", "TRANSFER");
    static final int MAX_OBSERVATION = 200;

    public record Input(String method, OffsetDateTime paidAt, String observation, Long amountCents, Boolean sendReceipt) {}

    public record Receipt(int number, String code) {}

    public record Registered(UUID id, UUID chargeId, long amountCents, String method, OffsetDateTime paidAt, UUID registeredBy, String observation,
            Receipt receipt, String receiptEmail) {}

    private record Done(Registered registered, String email, String reference) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final AuditService audit;
    private final Clock clock;
    private final ReceiptMailer mailer;

    public Payments(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, AuditService audit, Clock clock, ReceiptMailer mailer) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.audit = audit;
        this.clock = clock;
        this.mailer = mailer;
    }

    public Registered register(AccountPrincipal who, UUID chargeId, Input in, String ip) {
        if (permissions.effective(who).get(Permission.COLLECTIONS) == Access.NONE) throw new AccessDeniedException("Permission denied");
        if (in.method() == null || !METHODS.contains(in.method())) throw new ApiValidationException("method", "valor inválido");
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (in.paidAt() != null && in.paidAt().isAfter(now)) throw new ApiValidationException("paidAt", "não pode ser futuro");
        String observation = in.observation() == null || in.observation().isBlank() ? null : in.observation().strip();
        if (observation != null && observation.length() > MAX_OBSERVATION) throw new ApiValidationException("observation", "até 200 caracteres");
        OffsetDateTime paidAt = in.paidAt() == null ? now : in.paidAt();
        boolean send = Boolean.TRUE.equals(in.sendReceipt());
        Done done = transactions.execute(() -> {
            // A trava da linha faz a segunda chamada esperar e ver a cobrança já paga (RNF-12).
            var charge = jdbc.sql("select unit_id, student_id, amount_cents, status from charge where id = :id and organization_id = :org for update")
                    .param("id", chargeId).param("org", who.organizationId())
                    .query((rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getLong(3), rs.getString(4)})
                    .optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            UUID unit = (UUID) charge[0];
            if (permissions.grant(who, Permission.COLLECTIONS, unit) != Access.FULL) throw new ApiStatusException(ErrorCode.NOT_FOUND);
            if (!"OPEN".equals(charge[3])) throw new ApiStatusException(ErrorCode.CHARGE_NOT_PAYABLE);
            long amount = (Long) charge[2];
            if (in.amountCents() != null && in.amountCents() != amount) throw new ApiStatusException(ErrorCode.UNPROCESSABLE);

            UUID paymentId = UUID.randomUUID();
            jdbc.sql("insert into payment (id, organization_id, charge_id, student_id, unit_id, amount_cents, method, origin, paid_at, registered_by_account_id, observation) "
                    + "values (:id, :org, :charge, :student, :unit, :amount, :method, 'MANAGEMENT', :paidAt, :by, :obs)")
                    .param("id", paymentId).param("org", who.organizationId()).param("charge", chargeId).param("student", charge[1]).param("unit", unit)
                    .param("amount", amount).param("method", in.method()).param("paidAt", paidAt).param("by", who.accountId()).param("obs", observation).update();
            // A linha do contador trava até o fim da transação: números consecutivos, sem repetir e sem lacuna se algo falhar depois.
            int number = jdbc.sql("insert into receipt_counter (unit_id, organization_id, last_number) values (:unit, :org, 1) "
                    + "on conflict (unit_id) do update set last_number = receipt_counter.last_number + 1 returning last_number")
                    .param("unit", unit).param("org", who.organizationId()).query(Integer.class).single();
            String code = ReceiptCodes.next();
            jdbc.sql("insert into receipt (id, organization_id, payment_id, unit_id, number, code) values (:id, :org, :payment, :unit, :number, :code)")
                    .param("id", UUID.randomUUID()).param("org", who.organizationId()).param("payment", paymentId).param("unit", unit)
                    .param("number", number).param("code", code).update();
            jdbc.sql("update charge set status = 'PAID' where id = :id").param("id", chargeId).update();
            audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), "payment.registered", "payment",
                    paymentId.toString(), unit, Map.of(), Map.of("chargeId", chargeId.toString(), "amountCents", amount, "method", in.method(), "receiptNumber", number, "receiptRequested", send),
                    null, ip));
            // O endereço vem do cadastro (nunca do cliente); o envio só sai depois do commit (D7).
            String to = !send ? null : jdbc.sql("select coalesce(ga.email, s.email) from charge c join student s on s.id = c.student_id "
                    + "left join guardian g on g.id = c.payer_guardian_id left join account ga on ga.id = g.account_id where c.id = :id")
                    .param("id", chargeId).query(String.class).optional().orElse(null);
            String reference = !send ? null : jdbc.sql("select reference from public_receipt(:code)").param("code", code).query(String.class).single();
            return new Done(new Registered(paymentId, chargeId, amount, in.method(), paidAt, who.accountId(), observation, new Receipt(number, code), "NOT_REQUESTED"), to, reference);
        });
        Registered r = done.registered();
        if (!send) return r;
        String status = mailer.send(done.email(), r.receipt().code(), done.reference(), r.receipt().number());
        return new Registered(r.id(), r.chargeId(), r.amountCents(), r.method(), r.paidAt(), r.registeredBy(), r.observation(), r.receipt(), status);
    }
}

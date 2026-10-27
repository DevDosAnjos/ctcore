package br.com.ctcore.payment;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationContext;
import br.com.ctcore.isolation.OrganizationPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import br.com.ctcore.storage.FileStorage;
import br.com.ctcore.student.PortalTerms;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** RN-COB-16: monta o PDF do recibo pelo código. Pelo link público o CPF sai mascarado; com sessão e acesso a cobranças da unidade do recibo, completo. */
@Component
class ReceiptFiles {
    private record Row(UUID organization, ReceiptPdf.Data data) {}

    private final JdbcClient jdbc;
    private final FileStorage storage;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final PortalTerms terms;

    ReceiptFiles(@Lazy JdbcClient jdbc, @Lazy FileStorage storage, @Lazy OrganizationTransactions transactions, Permissions permissions, PortalTerms terms) {
        this.terms = terms;
        this.jdbc = jdbc;
        this.storage = storage;
        this.transactions = transactions;
        this.permissions = permissions;
    }

    /** Sem sessão: CPF mascarado. */
    ResponseEntity<byte[]> publicPdf(String code) throws IOException {
        return pdf(row(code), false);
    }

    /** Com sessão: CPF completo. Outra organização ou unidade fora do alcance dá o mesmo 404 do código desconhecido. */
    ResponseEntity<byte[]> staffPdf(AccountPrincipal who, String code) throws IOException {
        if (permissions.effective(who).get(Permission.COLLECTIONS) == Access.NONE) throw new AccessDeniedException("Permission denied");
        Row row = row(code);
        if (!row.organization().equals(who.organizationId())) throw new ApiStatusException(ErrorCode.NOT_FOUND);
        UUID unit = transactions.execute(() -> jdbc.sql("select unit_id from receipt where code = :code and organization_id = :org").param("code", code)
                .param("org", who.organizationId()).query(UUID.class).optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND)));
        if (permissions.grant(who, Permission.COLLECTIONS, unit) == Access.NONE) throw new ApiStatusException(ErrorCode.NOT_FOUND);
        return pdf(row, true);
    }

    /** Portal (RF-POR-05): CPF mascarado, só de recibo de aluno que a conta paga. Qualquer outra coisa dá o mesmo 404. */
    ResponseEntity<byte[]> portalPdf(AccountPrincipal who, String code) throws IOException {
        Row row = row(code);
        if (!row.organization().equals(who.organizationId())) throw new ApiStatusException(ErrorCode.NOT_FOUND);
        boolean pays = transactions.execute(() -> jdbc.sql("select c.student_id from receipt r join payment p on p.organization_id = r.organization_id and p.id = r.payment_id "
                + "join charge c on c.organization_id = p.organization_id and c.id = p.charge_id where r.code = :code and r.organization_id = :org")
                .param("code", code).param("org", who.organizationId()).query(UUID.class).optional().map(student -> terms.pays(who, student)).orElse(false));
        if (!pays) throw new ApiStatusException(ErrorCode.NOT_FOUND);
        return pdf(row, false);
    }

    private Row row(String code) {
        return jdbc.sql("select organization_id, logo, unit_name, cnpj, address, timezone, number, code, amount_cents, fee_passed_cents, method, paid_at, "
                + "student_name, payer_name, payer_cpf, reference from receipt_pdf_data(:code)").param("code", code)
                .query((rs, n) -> new Row(rs.getObject(1, UUID.class), new ReceiptPdf.Data(rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getInt(7), rs.getString(8), rs.getLong(9), rs.getLong(10), rs.getString(11), rs.getObject(12, OffsetDateTime.class), rs.getString(13),
                        rs.getString(14), rs.getString(15), rs.getString(16))))
                .optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
    }

    private ResponseEntity<byte[]> pdf(Row row, boolean fullCpf) throws IOException {
        byte[] pdf = ReceiptPdf.render(row.data(), logo(row.organization(), row.data().logo()), fullCpf);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff").header("Content-Disposition", "inline; filename=\"recibo-" + row.data().number() + ".pdf\"").body(pdf);
    }

    /** A logo é lida no contexto da organização do recibo, fechado no fim (nada vaza para a próxima requisição). Sem logo ou com falha, o PDF sai sem ela. */
    private byte[] logo(UUID organization, String name) {
        if (name == null) return null;
        try (var ignored = OrganizationContext.open(new OrganizationPrincipal() {
            @Override public UUID organizationId() { return organization; }
            @Override public UUID unitId() { return null; }
            @Override public Set<UUID> authorizedUnitIds() { return Set.of(); }
        })) {
            return storage.get(name);
        } catch (RuntimeException e) {
            return null;
        }
    }
}

package br.com.ctcore.organization;

import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import br.com.ctcore.storage.FileStorage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** RF-ORG-01: dados da organização do vínculo ativo. Leitura para Coordenador e Administrador; escrita só para o Coordenador (FULL). */
@Service
public class OrganizationProfile {
    private static final Logger LOG = LoggerFactory.getLogger(OrganizationProfile.class);
    static final int MAX_NAME = 120;

    /** Resposta de GET e das escritas; {@code logoUrl} é a URL temporária assinada, nunca o caminho no armazenamento. */
    public record OrganizationView(String name, String color, String holderDocument, String logoUrl) {}

    private record Row(String name, String color, String holder, String logo) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final AuditService audit;
    private final FileStorage storage;

    public OrganizationProfile(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions,
            AuditService audit, FileStorage storage) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.audit = audit;
        this.storage = storage;
    }

    public OrganizationView read(AccountPrincipal who) {
        if (permissions.grant(who, Permission.ORGANIZATION_SETTINGS, null) == Access.NONE) throw new AccessDeniedException("Permission denied");
        return view(transactions.execute(() -> row(who.organizationId())));
    }

    public OrganizationView update(AccountPrincipal who, String name, String color, String holderDocument, String ip) {
        requireWrite(who);
        String newName = name == null ? "" : name.trim();
        if (newName.isEmpty()) throw new ApiValidationException("name", "obrigatório");
        if (newName.length() > MAX_NAME) throw new ApiValidationException("name", "no máximo 120 caracteres");
        String newColor = field("color", () -> BrandColor.normalize(color));
        String newHolder = field("holderDocument", () -> HolderDocument.normalize(holderDocument));
        Row after = transactions.execute(() -> {
            Row before = row(who.organizationId());
            Row next = new Row(newName, newColor, newHolder, before.logo());
            if (!next.equals(before)) {
                jdbc.sql("update organization set name = :name, color = :color, holder_document = :holder where id = :id")
                        .param("name", newName).param("color", newColor).param("holder", newHolder)
                        .param("id", who.organizationId()).update();
                audit.record(entry(who, "organization.update", ip, auditValues(before), auditValues(next)));
            }
            return next;
        });
        return view(after);
    }

    public OrganizationView replaceLogo(AccountPrincipal who, byte[] content, String ip) {
        requireWrite(who);
        LogoImage image = field("file", () -> LogoImage.parse(content));
        String name = "logo-" + UUID.randomUUID() + "." + image.extension();
        storage.put(name, image.bytes(), image.contentType());
        Row before;
        try {
            before = transactions.execute(() -> setLogo(who, name, ip));
        } catch (RuntimeException e) {
            discard(name);
            throw e;
        }
        if (before.logo() != null) discard(before.logo());
        return read(who);
    }

    public OrganizationView removeLogo(AccountPrincipal who, String ip) {
        requireWrite(who);
        Row before = transactions.execute(() -> setLogo(who, null, ip));
        if (before.logo() != null) discard(before.logo());
        return read(who);
    }

    /** Dentro da transação: troca a coluna e audita; devolve a linha anterior. */
    private Row setLogo(AccountPrincipal who, String name, String ip) {
        Row before = row(who.organizationId());
        if (java.util.Objects.equals(before.logo(), name)) return before;
        jdbc.sql("update organization set logo_url = :logo where id = :id").param("logo", name).param("id", who.organizationId()).update();
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("logo", before.logo());
        Map<String, Object> next = new LinkedHashMap<>();
        next.put("logo", name);
        audit.record(entry(who, name == null ? "organization.logo.remove" : "organization.logo.update", ip, old, next));
        return before;
    }

    // Arquivo antigo (ou novo, se a transação falhou): falha ao apagar vai para o log e não desfaz a troca.
    private void discard(String name) {
        try {
            storage.delete(name);
        } catch (RuntimeException e) {
            LOG.warn("Could not delete organization file {}: {}", name, e.getClass().getSimpleName());
        }
    }

    private void requireWrite(AccountPrincipal who) {
        if (permissions.grant(who, Permission.ORGANIZATION_SETTINGS, null) != Access.FULL) throw new AccessDeniedException("Permission denied");
    }

    private Row row(UUID organizationId) {
        return jdbc.sql("select name, color, holder_document, logo_url from organization where id = :id").param("id", organizationId)
                .query((rs, i) -> new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4))).single();
    }

    private OrganizationView view(Row row) {
        String url = null;
        if (row.logo() != null) {
            try {
                url = storage.readUrl(row.logo());
            } catch (FileStorage.NotFound e) {
                LOG.warn("Organization logo file is missing from storage");
            }
        }
        return new OrganizationView(row.name(), row.color(), row.holder(), url);
    }

    private static <T> T field(String field, Supplier<T> parse) {
        try {
            return parse.get();
        } catch (IllegalArgumentException e) {
            throw new ApiValidationException(field, e.getMessage());
        }
    }

    // O titular entra na auditoria mascarado (só os dois últimos dígitos) e a logo, só pelo nome do arquivo.
    private static Map<String, Object> auditValues(Row r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", r.name());
        m.put("color", r.color());
        m.put("holderDocument", r.holder() == null ? null : "*".repeat(r.holder().length() - 2) + r.holder().substring(r.holder().length() - 2));
        return m;
    }

    private static AuditEntry entry(AccountPrincipal who, String action, String ip, Map<String, Object> before, Map<String, Object> after) {
        return new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), action, "organization",
                who.organizationId().toString(), null, before, after, null, ip);
    }
}

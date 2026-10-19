package br.com.ctcore.modality;

import br.com.ctcore.api.error.ApiError;
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
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** RF-MOD-01 (RN-MOD-01..04, RN-MOD-06): catálogo global, ativação por organização e pedidos de modalidade nova. */
@Service
public class Modalities {
    static final int MIN_NAME = 2;
    static final int MAX_NAME = 60;
    static final int MAX_NOTE = 300;

    public record Item(UUID id, String name, boolean active, boolean canManage) {}

    public record Request(UUID id, String name, String note, String status, OffsetDateTime createdAt) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final AuditService audit;
    private final ModalityUsage usage;

    public Modalities(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, AuditService audit,
            ModalityUsage usage) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.audit = audit;
        this.usage = usage;
    }

    /** Sem acento e sem diferença de maiúsculas, para comparar nomes. */
    static String normalize(String name) {
        return Normalizer.normalize(name.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    public List<Item> list(AccountPrincipal who) {
        boolean manage = access(who) == Access.FULL;
        return transactions.execute(() -> jdbc.sql("select m.id, m.name, coalesce(o.active, false) from modality m "
                + "left join organization_modality o on o.modality_id = m.id and o.organization_id = :org order by m.name")
                .param("org", who.organizationId())
                .query((rs, i) -> new Item(rs.getObject(1, UUID.class), rs.getString(2), rs.getBoolean(3), manage)).list());
    }

    public Item setActive(AccountPrincipal who, UUID id, Boolean active, String ip) {
        requireFull(who);
        if (active == null) throw new ApiValidationException("active", "obrigatório");
        return transactions.execute(() -> {
            String name = jdbc.sql("select name from modality where id = :id").param("id", id).query(String.class).optional()
                    .orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            boolean current = jdbc.sql("select active from organization_modality where organization_id = :org and modality_id = :id")
                    .param("org", who.organizationId()).param("id", id).query(Boolean.class).optional().orElse(false);
            if (current != active) {
                if (!active) refuseIfInUse(id);
                jdbc.sql("insert into organization_modality (organization_id, modality_id, active) values (:org, :id, :active) "
                        + "on conflict (organization_id, modality_id) do update set active = :active, updated_at = now()")
                        .param("org", who.organizationId()).param("id", id).param("active", active).update();
                audit.record(new AuditEntry(actor(who), active ? "modality.activated" : "modality.deactivated", "modality", id.toString(), null,
                        Map.of("active", current), Map.of("active", active), null, ip));
            }
            return new Item(id, name, active, true);
        });
    }

    public List<Request> requests(AccountPrincipal who) {
        access(who);
        return transactions.execute(() -> jdbc.sql("select id, name, note, status, created_at from modality_request "
                + "where organization_id = :org order by created_at desc, id").param("org", who.organizationId())
                .query((rs, i) -> new Request(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getObject(5, OffsetDateTime.class))).list());
    }

    public Request request(AccountPrincipal who, String name, String note, String ip) {
        requireFull(who);
        String newName = name == null ? "" : name.strip();
        if (newName.length() < MIN_NAME || newName.length() > MAX_NAME) throw new ApiValidationException("name", "de 2 a 60 caracteres");
        String newNote = note == null || note.isBlank() ? null : note.strip();
        if (newNote != null && newNote.length() > MAX_NOTE) throw new ApiValidationException("note", "no máximo 300 caracteres");
        return transactions.execute(() -> {
            String key = normalize(newName);
            for (String existing : jdbc.sql("select name from modality").query(String.class).list()) {
                if (normalize(existing).equals(key)) throw new ApiStatusException(ErrorCode.ALREADY_IN_CATALOG);
            }
            UUID id = UUID.randomUUID();
            try {
                jdbc.sql("insert into modality_request (id, organization_id, requested_by, name, note) values (:id, :org, :by, :name, :note)")
                        .param("id", id).param("org", who.organizationId()).param("by", who.accountId()).param("name", newName)
                        .param("note", newNote).update();
            } catch (DuplicateKeyException e) {
                throw new ApiStatusException(ErrorCode.REQUEST_ALREADY_OPEN);
            }
            audit.record(new AuditEntry(actor(who), "modality.requested", "modality_request", id.toString(), null, Map.of(),
                    Map.of("name", newName), null, ip));
            return jdbc.sql("select id, name, note, status, created_at from modality_request where id = :id").param("id", id)
                    .query((rs, i) -> new Request(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getObject(5, OffsetDateTime.class))).single();
        });
    }

    private void refuseIfInUse(UUID id) {
        int classes = usage.activeClasses(id);
        int students = usage.activeStudents(id);
        if (classes == 0 && students == 0) return;
        throw new ApiStatusException(ErrorCode.MODALITY_IN_USE, List.of(
                new ApiError.Field("activeClasses", String.valueOf(classes)), new ApiError.Field("activeStudents", String.valueOf(students))));
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name());
    }

    private Access access(AccountPrincipal who) {
        Access access = permissions.grant(who, Permission.ORGANIZATION_SETTINGS, null);
        if (access == Access.NONE) throw new AccessDeniedException("Permission denied");
        return access;
    }

    private void requireFull(AccountPrincipal who) {
        if (access(who) != Access.FULL) throw new AccessDeniedException("Permission denied");
    }
}

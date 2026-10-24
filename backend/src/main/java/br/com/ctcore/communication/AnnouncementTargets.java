package br.com.ctcore.communication;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** RN-COM-01: os destinos que cada perfil escolhe (R1) e a regra única de "quem pode enviar para este destino" (R4), usada por prévia e criação. */
@Service
public class AnnouncementTargets {
    public record UnitOption(UUID id, String name) {}

    public record ClassOption(UUID id, String name, UUID unitId, UUID modalityId, String groupLink) {}

    public record ModalityOption(UUID id, String name, UUID unitId) {}

    public record Targets(List<UnitOption> units, List<ClassOption> classes, List<ModalityOption> modalities, boolean organization) {}

    /** Destino já conferido: {@code unitId} é o da turma, da unidade ou nulo (organização); {@code classIds} só no destino CLASSES (dia sem aula). */
    public record Destination(String kind, UUID classId, UUID modalityId, UUID unitId, List<UUID> classIds) {}

    /** Turmas em que a conta é titular ou auxiliar (parâmetros :org e :acc). */
    static final String TEACHER_CLASSES = "select k.id from training_class k join teacher t on t.organization_id = k.organization_id and t.account_id = :acc "
            + "where k.organization_id = :org and (k.head_teacher_id = t.id or exists (select 1 from training_class_assistant s "
            + "where s.organization_id = k.organization_id and s.class_id = k.id and s.teacher_id = t.id))";

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;

    public AnnouncementTargets(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
    }

    /** 403 para quem não tem a permissão "enviar comunicado" em nenhuma unidade (Aluno, Responsável, Professor sem a permissão). */
    void requireAnnouncements(AccountPrincipal who) {
        if (who == null || who.role() == null || permissions.effective(who).get(Permission.ANNOUNCEMENTS) == Access.NONE) throw new AccessDeniedException("Permission denied");
    }

    public Targets targets(AccountPrincipal who) {
        requireAnnouncements(who);
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            if (who.role() == Role.TEACHER) {
                List<ClassOption> classes = jdbc.sql("select c.id, c.name, c.unit_id, c.modality_id, c.group_link from training_class c "
                        + "where c.organization_id = :org and c.status = 'ACTIVE' and c.id in (" + TEACHER_CLASSES + ") order by lower(c.name), c.id")
                        .param("org", org).param("acc", who.accountId()).query((rs, n) -> new ClassOption(rs.getObject(1, UUID.class), rs.getString(2),
                                rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getString(5))).list().stream()
                        .filter(c -> permissions.grant(who, Permission.ANNOUNCEMENTS, c.unitId()) != Access.NONE).toList();
                return new Targets(List.of(), classes, List.of(), false);
            }
            boolean all = who.role() == Role.COORDINATOR;
            Set<UUID> reach = who.units() == null ? Set.of() : who.units();
            if (!all && reach.isEmpty()) return new Targets(List.of(), List.of(), List.of(), false);
            Map<String, Object> params = all ? Map.of("org", org) : Map.of("org", org, "reach", reach);
            String in = all ? "" : " and %s in (:reach)";
            var units = jdbc.sql("select u.id, u.name from unit u where u.organization_id = :org and u.active" + in.formatted("u.id") + " order by lower(u.name), u.id")
                    .params(params).query((rs, n) -> new UnitOption(rs.getObject(1, UUID.class), rs.getString(2))).list();
            var classes = jdbc.sql("select c.id, c.name, c.unit_id, c.modality_id, c.group_link from training_class c join unit u on u.organization_id = c.organization_id "
                    + "and u.id = c.unit_id where c.organization_id = :org and c.status = 'ACTIVE' and u.active" + in.formatted("c.unit_id") + " order by lower(c.name), c.id")
                    .params(params).query((rs, n) -> new ClassOption(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
                            rs.getObject(4, UUID.class), rs.getString(5))).list();
            var modalities = jdbc.sql("select m.id, m.name, um.unit_id from unit_modality um join modality m on m.id = um.modality_id join unit u "
                    + "on u.organization_id = um.organization_id and u.id = um.unit_id where um.organization_id = :org and um.offered and u.active"
                    + in.formatted("um.unit_id") + " order by lower(m.name), um.unit_id")
                    .params(params).query((rs, n) -> new ModalityOption(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class))).list();
            return new Targets(units, classes, modalities, all);
        });
    }

    private record Klass(UUID unit, String status) {}

    /**
     * R4, dentro de uma transação da organização: 400 no campo do destino que falta, 404 se não existe na organização, 403 se o perfil não pode,
     * 400 {@code classId} se a turma está desativada. O alcance vem do principal, nunca do corpo.
     */
    Destination resolve(AccountPrincipal who, String kind, UUID classId, UUID modalityId, UUID unitId) {
        UUID org = who.organizationId();
        if (kind == null) throw new ApiValidationException("targetKind", "obrigatório");
        switch (kind) {
            case "CLASS" -> {
                if (classId == null) throw new ApiValidationException("classId", "obrigatório");
                Klass k = jdbc.sql("select unit_id, status from training_class where organization_id = :org and id = :id").param("org", org).param("id", classId)
                        .query((rs, n) -> new Klass(rs.getObject(1, UUID.class), rs.getString(2))).optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
                boolean own = who.role() != Role.TEACHER || jdbc.sql("select 1 from training_class c where c.id = :cls and c.id in (" + TEACHER_CLASSES + ")")
                        .param("cls", classId).param("org", org).param("acc", who.accountId()).query(Integer.class).optional().isPresent();
                if (!own || permissions.grant(who, Permission.ANNOUNCEMENTS, k.unit()) == Access.NONE) throw new AccessDeniedException("Permission denied");
                if (!"ACTIVE".equals(k.status())) throw new ApiValidationException("classId", "turma desativada");
                return new Destination(kind, classId, null, k.unit(), null);
            }
            case "MODALITY", "UNIT" -> {
                if (unitId == null) throw new ApiValidationException("unitId", "obrigatório");
                if ("MODALITY".equals(kind) && modalityId == null) throw new ApiValidationException("modalityId", "obrigatório");
                jdbc.sql("select 1 from unit where organization_id = :org and id = :id").param("org", org).param("id", unitId).query(Integer.class).optional()
                        .orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
                if ("MODALITY".equals(kind)) {
                    jdbc.sql("select 1 from unit_modality where organization_id = :org and unit_id = :u and modality_id = :m and offered").param("org", org).param("u", unitId)
                            .param("m", modalityId).query(Integer.class).optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
                }
                if (who.role() == Role.TEACHER || permissions.grant(who, Permission.ANNOUNCEMENTS, unitId) == Access.NONE) throw new AccessDeniedException("Permission denied");
                return new Destination(kind, null, "MODALITY".equals(kind) ? modalityId : null, unitId, null);
            }
            case "ORGANIZATION" -> {
                if (who.role() != Role.COORDINATOR) throw new AccessDeniedException("Permission denied");
                return new Destination(kind, null, null, null, null);
            }
            default -> throw new ApiValidationException("targetKind", "destino inválido");
        }
    }
}

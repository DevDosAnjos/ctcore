package br.com.ctcore.communication;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-COM-01: a lista de comunicados da gestão e o cancelamento do agendado. Quem vê (D2): Coordenador tudo; Administrador as unidades dele (sem "todas as
 * unidades"); Professor o que enviou e o destinado às suas turmas. O comunicado enviado não se apaga nem se edita (RN-COM-06). Só contagens de destinatários
 * e leituras na resposta, nunca dados de pessoa.
 */
@Service
public class Announcements {
    public record Target(String kind, UUID id, String name) {}

    public record Item(UUID id, OffsetDateTime date, Target target, String title, String type, String author, String status, Integer recipients, long reads) {}

    public record AnnouncementList(List<Item> items, int page, int size, long total) {}

    public record Filter(String from, String to, UUID classId, String type, UUID unitId) {}

    public static final Set<String> SORT_FIELDS = Set.of("date", "title", "type");
    static final Set<String> TYPES = Set.of("CANCELED_CLASS", "TEACHER_CHANGE", "SCHEDULE_CHANGE", "NO_CLASS_DAY", "GENERAL");
    /** S1: o "todas as unidades" não tem unidade; vale o fuso padrão da V3. */
    private static final String ZONE = "coalesce(u.timezone, 'America/Sao_Paulo')";
    private static final String JOINS = " from announcement a left join unit u on u.organization_id = a.organization_id and u.id = a.unit_id "
            + "left join training_class c on c.organization_id = a.organization_id and c.id = a.class_id left join modality m on m.id = a.modality_id "
            + "join account au on au.id = a.author_account_id where ";

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final AuditService audit;

    public Announcements(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, AuditService audit) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.audit = audit;
    }

    private static LocalDate date(String field, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException e) {
            throw new ApiValidationException(field, "use AAAA-MM-DD");
        }
    }

    private void requireAnnouncements(AccountPrincipal who) {
        if (who == null || who.role() == null || permissions.effective(who).get(Permission.ANNOUNCEMENTS) == Access.NONE) throw new AccessDeniedException("Permission denied");
    }

    public AnnouncementList list(AccountPrincipal who, Filter f, PageRequest page) {
        requireAnnouncements(who);
        LocalDate from = date("from", f.from()), to = date("to", f.to());
        if (from != null && to != null && to.isBefore(from)) throw new ApiValidationException("to", "não pode ser anterior a from");
        if (f.type() != null && !TYPES.contains(f.type())) throw new ApiValidationException("type", "tipo de comunicado inválido");
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            if (f.unitId() != null) {
                jdbc.sql("select 1 from unit where organization_id = :org and id = :id").param("org", org).param("id", f.unitId()).query(Integer.class).optional()
                        .orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            }
            Map<String, Object> params = new HashMap<>();
            params.put("org", org);
            StringBuilder where = new StringBuilder("a.organization_id = :org");
            if (who.role() == Role.ADMINISTRATOR) {
                Set<UUID> reach = who.units() == null ? Set.of() : who.units();
                if (reach.isEmpty() || f.unitId() != null && !reach.contains(f.unitId())) return new AnnouncementList(List.of(), page.page(), page.size(), 0);
                where.append(" and a.unit_id in (:reach)");
                params.put("reach", reach);
            } else if (who.role() == Role.TEACHER) {
                where.append(" and (a.author_account_id = :acc or a.class_id in (select k.id from training_class k join teacher t on t.organization_id = k.organization_id "
                        + "and t.account_id = :acc where k.organization_id = :org and (k.head_teacher_id = t.id or exists (select 1 from training_class_assistant s "
                        + "where s.organization_id = k.organization_id and s.class_id = k.id and s.teacher_id = t.id))))");
                params.put("acc", who.accountId());
            }
            if (f.unitId() != null) {
                where.append(" and a.unit_id = :unit");
                params.put("unit", f.unitId());
            }
            if (f.classId() != null) {
                where.append(" and a.class_id = :class");
                params.put("class", f.classId());
            }
            if (f.type() != null) {
                where.append(" and a.type = :type");
                params.put("type", f.type());
            }
            if (from != null) {
                where.append(" and (coalesce(a.sent_at, a.scheduled_for) at time zone " + ZONE + ")::date >= :from");
                params.put("from", from);
            }
            if (to != null) {
                where.append(" and (coalesce(a.sent_at, a.scheduled_for) at time zone " + ZONE + ")::date <= :to");
                params.put("to", to);
            }
            String from_ = JOINS + where;
            long total = jdbc.sql("select count(*)" + from_).params(params).query(Long.class).single();
            var sort = page.sort() == null ? new PageRequest.Sort("date", true) : page.sort();
            String direction = sort.desc() ? " desc" : " asc";
            String order = switch (sort.field()) {
                case "title" -> "lower(a.title)" + direction;
                case "type" -> "a.type" + direction;
                default -> "coalesce(a.sent_at, a.scheduled_for)" + direction;
            };
            params.put("limit", page.size());
            params.put("offset", page.offset());
            // reads em subconsulta: um join duplicaria linhas na paginação.
            List<Item> items = jdbc.sql("select a.id, coalesce(a.sent_at, a.scheduled_for), a.target_kind, coalesce(c.id, m.id, u.id), coalesce(c.name, m.name, u.name), a.title, a.type, "
                    + "coalesce(nullif(btrim(au.name), ''), au.email), a.sent_at is null, a.recipient_count, "
                    + "(select count(*) from announcement_read r where r.announcement_id = a.id and r.organization_id = a.organization_id)" + from_
                    + " order by " + order + ", a.id limit :limit offset :offset").params(params)
                    .query((rs, n) -> new Item(rs.getObject(1, UUID.class), rs.getObject(2, OffsetDateTime.class),
                            new Target(rs.getString(3), rs.getObject(4, UUID.class), rs.getString(5)), rs.getString(6), rs.getString(7), rs.getString(8),
                            rs.getBoolean(9) ? "SCHEDULED" : "SENT", rs.getObject(10) == null ? null : rs.getInt(10), rs.getLong(11))).list();
            return new AnnouncementList(items, page.page(), page.size(), total);
        });
    }

    private record Found(UUID unit, UUID author, boolean sent, String title, String kind, String targetName) {}

    /** R5: cancela o agendado (apaga a linha, D9). Sem trava (o papel não tem UPDATE): o {@code sent_at is null} do delete decide a corrida com o envio. */
    public void cancel(AccountPrincipal who, UUID id, String ip) {
        requireAnnouncements(who);
        transactions.execute(() -> {
            UUID org = who.organizationId();
            Found found = jdbc.sql("select a.unit_id, a.author_account_id, a.sent_at is not null, a.title, a.target_kind, coalesce(c.name, m.name, u.name) "
                    + "from announcement a left join unit u on u.organization_id = a.organization_id and u.id = a.unit_id "
                    + "left join training_class c on c.organization_id = a.organization_id and c.id = a.class_id left join modality m on m.id = a.modality_id "
                    + "where a.organization_id = :org and a.id = :id").param("org", org).param("id", id)
                    .query((rs, n) -> new Found(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getBoolean(3), rs.getString(4), rs.getString(5), rs.getString(6)))
                    .optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            if (permissions.grant(who, Permission.ANNOUNCEMENTS, found.unit()) == Access.NONE
                    || who.role() == Role.TEACHER && !who.accountId().equals(found.author())) {
                throw new AccessDeniedException("Permission denied");
            }
            if (found.sent()) throw new ApiStatusException(ErrorCode.ANNOUNCEMENT_ALREADY_SENT);
            if (jdbc.sql("delete from announcement where organization_id = :org and id = :id and sent_at is null").param("org", org).param("id", id).update() != 1) {
                throw new ApiStatusException(ErrorCode.ANNOUNCEMENT_ALREADY_SENT);
            }
            Map<String, Object> before = new HashMap<>();
            before.put("title", found.title());
            before.put("target", found.kind() + (found.targetName() == null ? "" : ":" + found.targetName()));
            audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), "announcement.cancel", "announcement", id.toString(),
                    found.unit(), before, Map.of(), null, ip));
            return null;
        });
    }
}

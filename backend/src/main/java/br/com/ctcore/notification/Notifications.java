package br.com.ctcore.notification;

import br.com.ctcore.api.error.ApiError;
import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.mail.EmailService;
import br.com.ctcore.mail.EmailTemplate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-NOT-01 (RN-NOT-01..04): notificações por pessoa (vínculo). {@link #publish} decide quem recebe no servidor, grava uma linha por pessoa sem
 * repetir (mesmo tipo e objeto) e manda e-mail na hora só nos tipos urgentes; nunca desfaz nem derruba o fato que a gerou.
 * O texto de tela é do front (pelo tipo e {@code params}); aqui só vão os valores mínimos, nunca CPF, e-mail ou telefone.
 */
@Service
public class Notifications {
    private static final Logger LOG = LoggerFactory.getLogger(Notifications.class);
    private static final Locale PT_BR = Locale.of("pt", "BR");
    /** Hoje e os 6 dias anteriores. */
    static final int WINDOW_DAYS = 7;

    public record Item(UUID id, String type, String group, Instant createdAt, Instant readAt, String refType, UUID refId, Map<String, Object> params) {}

    public record Listing(List<Item> items, long unreadCount) {}

    public record Preferences(boolean receiveAll, boolean canReceiveAll) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final AuditService audit;
    private final EmailService email;
    private final MessageSource messages;
    private final ObjectMapper json;
    private final Clock clock;

    public Notifications(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, AuditService audit, EmailService email, MessageSource messages,
            ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.audit = audit;
        this.email = email;
        this.messages = messages;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Chamar depois do fato (fora da transação dele). {@code unitId} é a unidade do fato (obrigatória nos tipos de unidade); {@code refId} nulo
     * não deduplica. Falha é registrada sem dado e engolida (R4).
     */
    public void publish(UUID organizationId, UUID unitId, NotificationType type, String refType, UUID refId, Map<String, Object> params) {
        try {
            String text = json.writeValueAsString(params == null ? Map.of() : params);
            OffsetDateTime now = OffsetDateTime.now(clock);
            List<String> urgentTo = transactions.provision(organizationId, () -> {
                List<UUID> links = jdbc.sql(recipientsSql(type)).param("org", organizationId).param("unit", unitId, java.sql.Types.OTHER)
                        .param("type", type.name()).param("refType", refType).param("refId", refId, java.sql.Types.OTHER).param("params", text)
                        .param("now", now).query(UUID.class).list();
                if (!type.urgent() || links.isEmpty()) return List.<String>of();
                return jdbc.sql("select a.email from account_link l join account a on a.id = l.account_id where l.organization_id = :org and l.id in (:ids)")
                        .param("org", organizationId).param("ids", links).query(String.class).list();
            });
            if (!urgentTo.isEmpty()) {
                String subject = messages.getMessage("notification." + type + ".subject", null, PT_BR);
                String body = messages.getMessage("notification." + type + ".body", null, PT_BR);
                for (String to : urgentTo) email.sendText(organizationId, to, EmailTemplate.NOTIFICATION_URGENT, subject, body);
            }
        } catch (JsonProcessingException | RuntimeException e) {
            LOG.warn("notification not recorded type={} cause={}", type, e.getClass().getSimpleName());
        }
    }

    /** R2: Administrador com acesso à unidade (tipo de unidade) ou Coordenador (da organização; ou "receber tudo" no de unidade). Só vínculo ativo. */
    private static String recipientsSql(NotificationType type) {
        String who = type.audience() == NotificationType.Audience.COORDINATORS ? "l.role = 'COORDINATOR'"
                : "(l.role = 'ADMINISTRATOR' and exists (select 1 from account_link_unit u where u.link_id = l.id and u.unit_id = :unit)) "
                        + "or (l.role = 'COORDINATOR' and coalesce((select p.receive_all from notification_preference p "
                        + "where p.organization_id = l.organization_id and p.link_id = l.id), false))";
        return "insert into notification (id, organization_id, link_id, unit_id, type, ref_type, ref_id, params, created_at) "
                + "select gen_random_uuid(), :org, l.id, cast(:unit as uuid), :type, :refType, cast(:refId as uuid), cast(:params as jsonb), :now "
                + "from account_link l where l.organization_id = :org and l.status = 'ACTIVE' and (" + who + ") on conflict do nothing returning link_id";
    }

    /** R6: janela de 7 dias no fuso da unidade (D8); as não lidas contam de qualquer data. */
    public Listing list(AccountPrincipal who, String filter) {
        requireManagement(who);
        boolean unreadOnly = switch (filter == null ? "all" : filter) {
            case "all" -> false;
            case "unread" -> true;
            default -> throw new ApiValidationException("filter", "all ou unread");
        };
        Instant now = clock.instant();
        return transactions.execute(() -> {
            // ponytail: unit não guarda a data de criação; "a unidade mais antiga" (D8) vira a primeira por nome. Troca se a unit ganhar created_at.
            var rows = jdbc.sql("select n.id, n.type, n.created_at, n.read_at, n.ref_type, n.ref_id, n.params::text, "
                    + "coalesce((select u.timezone from unit u where u.organization_id = n.organization_id and u.id = n.unit_id), "
                    + "(select u.timezone from unit u where u.organization_id = n.organization_id order by u.short_name limit 1), 'America/Sao_Paulo') "
                    + "from notification n where n.organization_id = :org and n.link_id = :link and n.created_at >= :since "
                    + (unreadOnly ? "and n.read_at is null " : "") + "order by n.created_at desc, n.id")
                    .param("org", who.organizationId()).param("link", who.activeLinkId())
                    .param("since", now.minusSeconds((WINDOW_DAYS + 1) * 86400L).atOffset(ZoneOffset.UTC))
                    .query((rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, OffsetDateTime.class).toInstant(),
                            rs.getObject(4, OffsetDateTime.class), rs.getString(5), rs.getObject(6, UUID.class), rs.getString(7), rs.getString(8)})
                    .list();
            List<Item> items = new ArrayList<>();
            for (Object[] r : rows) {
                Instant created = (Instant) r[2];
                String group = group(created, ZoneId.of((String) r[7]), now);
                if (group == null) continue;
                OffsetDateTime read = (OffsetDateTime) r[3];
                items.add(new Item((UUID) r[0], (String) r[1], group, created, read == null ? null : read.toInstant(), (String) r[4], (UUID) r[5], params((String) r[6])));
            }
            return new Listing(items, unread(who));
        });
    }

    /** Grupo da notificação ({@code TODAY} ou {@code WEEK}) pela data local da unidade; fora da janela, {@code null}. */
    static String group(Instant createdAt, ZoneId zone, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, zone);
        LocalDate day = LocalDate.ofInstant(createdAt, zone);
        if (!day.isBefore(today)) return "TODAY";
        return day.isBefore(today.minusDays(WINDOW_DAYS - 1)) ? null : "WEEK";
    }

    private Map<String, Object> params(String text) {
        try {
            return json.readValue(text, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    public long unreadCount(AccountPrincipal who) {
        requireManagement(who);
        return transactions.execute(() -> unread(who));
    }

    private long unread(AccountPrincipal who) {
        return jdbc.sql("select count(*) from notification where organization_id = :org and link_id = :link and read_at is null "
                + "and created_at > :expiry").param("org", who.organizationId()).param("link", who.activeLinkId())
                .param("expiry", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(NotificationRetentionJob.RETENTION_DAYS)).query(Long.class).single();
    }

    /** R8: marca todas as do vínculo da sessão; devolve quantas mudaram. */
    public int readAll(AccountPrincipal who, String ip) {
        requireManagement(who);
        return transactions.execute(() -> {
            int changed = jdbc.sql("update notification set read_at = :now where organization_id = :org and link_id = :link and read_at is null")
                    .param("now", OffsetDateTime.now(clock)).param("org", who.organizationId()).param("link", who.activeLinkId()).update();
            audit.record(new AuditEntry(actor(who), "notification.read_all", "notification", who.activeLinkId().toString(), null, null,
                    Map.of("changed", changed), null, ip));
            return changed;
        });
    }

    public Preferences preferences(AccountPrincipal who) {
        requireManagement(who);
        return transactions.execute(() -> new Preferences(receiveAll(who), who.role() == Role.COORDINATOR));
    }

    private boolean receiveAll(AccountPrincipal who) {
        return jdbc.sql("select receive_all from notification_preference where organization_id = :org and link_id = :link")
                .param("org", who.organizationId()).param("link", who.activeLinkId()).query(Boolean.class).optional().orElse(false);
    }

    /** R9: só o Coordenador; {@code value} que não seja booleano é 422. */
    public Preferences setReceiveAll(AccountPrincipal who, Object value, String ip) {
        requireManagement(who);
        if (who.role() != Role.COORDINATOR) throw new AccessDeniedException("Permission denied");
        if (!(value instanceof Boolean on)) {
            throw new ApiStatusException(ErrorCode.UNPROCESSABLE, List.of(new ApiError.Field("receiveAll", "deve ser verdadeiro ou falso")));
        }
        return transactions.execute(() -> {
            boolean before = receiveAll(who);
            jdbc.sql("insert into notification_preference (organization_id, link_id, receive_all, updated_at) values (:org, :link, :on, :now) "
                    + "on conflict (organization_id, link_id) do update set receive_all = excluded.receive_all, updated_at = excluded.updated_at")
                    .param("org", who.organizationId()).param("link", who.activeLinkId()).param("on", on).param("now", OffsetDateTime.now(clock)).update();
            audit.record(new AuditEntry(actor(who), "notification.preferences_updated", "notification_preference", who.activeLinkId().toString(), null,
                    Map.of("receiveAll", before), Map.of("receiveAll", on), null, ip));
            return new Preferences(on, true);
        });
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name());
    }

    private static void requireManagement(AccountPrincipal who) {
        if (who == null || who.organizationId() == null || who.role() == null || !who.role().management()) throw new AccessDeniedException("Permission denied");
    }
}

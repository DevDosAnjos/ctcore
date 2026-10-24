package br.com.ctcore.communication;

import br.com.ctcore.api.error.ApiError;
import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.communication.AnnouncementTargets.Destination;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.organization.LogoImage;
import br.com.ctcore.storage.FileStorage;
import java.sql.Types;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** RF-COM-02: prévia, criação (enviar agora ou agendar) e imagem do comunicado. Permissão e destino em {@link AnnouncementTargets}; texto em {@link AnnouncementText}. */
@Service
public class AnnouncementWrite {
    /** Entrada de {@code preview} e de {@code POST /announcements}; datas e horas chegam como texto para o erro apontar o campo. */
    public record Draft(String targetKind, UUID classId, UUID modalityId, UUID unitId, String type, String title, String body, List<String> channels,
            String imageName, String pinnedUntil, String sendAt, UUID noClassDayId) {}

    public record Whatsapp(String mode, String groupLink) {}

    public record Preview(int recipients, int portal, int email, Whatsapp whatsapp, List<String> channels) {}

    public record Created(UUID id, String status, Integer recipients) {}

    public record Image(String name, String url) {}

    /** RN-COM-07 (D5). */
    static final int DAILY_LIMIT = 20;
    private static final java.util.Locale PT_BR = java.util.Locale.forLanguageTag("pt-BR");
    private static final String DEFAULT_ZONE = "America/Sao_Paulo";
    private static final String IMAGE_PREFIX = "announcement-";

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final AnnouncementTargets targets;
    private final AnnouncementRecipients recipients;
    private final AnnouncementSender sender;
    private final AuditService audit;
    private final FileStorage storage;
    private final Clock clock;
    private final MessageSource messages;

    public AnnouncementWrite(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, AnnouncementTargets targets, AnnouncementRecipients recipients,
            AnnouncementSender sender, AuditService audit, FileStorage storage, Clock clock, MessageSource messages) {
        this.messages = messages;
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.targets = targets;
        this.recipients = recipients;
        this.sender = sender;
        this.audit = audit;
        this.storage = storage;
        this.clock = clock;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /** R3/D4: PORTAL sempre, depois EMAIL e WHATSAPP na ordem fixa; PUSH e qualquer outro valor são recusados. */
    static List<String> channels(List<String> given) {
        List<String> in = given == null ? List.of() : given;
        for (String c : in) {
            if (!List.of("PORTAL", "EMAIL", "WHATSAPP").contains(c)) throw new ApiValidationException("channels", "canal inválido: " + c);
        }
        List<String> result = new ArrayList<>(List.of("PORTAL"));
        if (in.contains("EMAIL")) result.add("EMAIL");
        if (in.contains("WHATSAPP")) result.add("WHATSAPP");
        return result;
    }

    private String zoneOf(UUID org, UUID unit) {
        if (unit == null) return DEFAULT_ZONE;
        return jdbc.sql("select timezone from unit where organization_id = :org and id = :id").param("org", org).param("id", unit).query(String.class).optional().orElse(DEFAULT_ZONE);
    }

    /** R2: nada é gravado. */
    public Preview preview(AccountPrincipal who, Draft d) {
        targets.requireAnnouncements(who);
        List<String> channels = channels(d.channels());
        return transactions.execute(() -> {
            Destination dest = targets.resolve(who, d.targetKind(), d.classId(), d.modalityId(), d.unitId());
            var found = recipients.find(who.organizationId(), dest, now());
            Whatsapp whatsapp = null;
            if (channels.contains("WHATSAPP")) {
                String link = dest.classId() == null ? null : jdbc.sql("select group_link from training_class where organization_id = :org and id = :id")
                        .param("org", who.organizationId()).param("id", dest.classId()).query(String.class).optional().orElse(null);
                whatsapp = link == null || link.isBlank() ? new Whatsapp("CONVERSATION", null) : new Whatsapp("GROUP", link);
            }
            return new Preview(found.recipients(), found.recipients(), found.emails(), whatsapp, channels);
        });
    }

    /** R3, R4, R7, R8: valida, confere o destino, respeita o limite do dia, grava e, sem {@code sendAt}, envia na hora. */
    public Created create(AccountPrincipal who, Draft d, String ip) {
        targets.requireAnnouncements(who);
        String type = d.type();
        if (type == null || !Announcements.TYPES.contains(type)) throw new ApiValidationException("type", "tipo de comunicado inválido");
        String title = d.title() == null ? "" : d.title().strip();
        if (title.isEmpty() || title.length() > 120) throw new ApiValidationException("title", "de 1 a 120 caracteres");
        String body = AnnouncementText.validate(d.body());
        List<String> channels = channels(d.channels());
        OffsetDateTime now = now();
        OffsetDateTime sendAt = null;
        if (d.sendAt() != null && !d.sendAt().isBlank()) {
            try {
                sendAt = OffsetDateTime.parse(d.sendAt());
            } catch (DateTimeException e) {
                throw new ApiValidationException("sendAt", "use data e hora, como 2026-11-03T19:00:00-03:00");
            }
            if (!sendAt.isAfter(now)) throw new ApiValidationException("sendAt", "precisa ser no futuro");
        }
        LocalDate pinned = null;
        if (d.pinnedUntil() != null && !d.pinnedUntil().isBlank()) {
            try {
                pinned = LocalDate.parse(d.pinnedUntil());
            } catch (DateTimeException e) {
                throw new ApiValidationException("pinnedUntil", "use AAAA-MM-DD");
            }
        }
        if (d.noClassDayId() != null && !"NO_CLASS_DAY".equals(type)) throw new ApiValidationException("type", "o comunicado ligado a um dia sem aula é do tipo NO_CLASS_DAY");
        LocalDate pinnedUntil = pinned;
        OffsetDateTime at = sendAt;
        return transactions.execute(() -> {
            Destination dest = targets.resolve(who, d.targetKind(), d.classId(), d.modalityId(), d.unitId());
            if (d.imageName() != null && !d.imageName().isBlank()) checkImage(d.imageName());
            if (pinnedUntil != null && pinnedUntil.isBefore(now.atZoneSameInstant(ZoneId.of(zoneOf(who.organizationId(), dest.unitId()))).toLocalDate())) {
                throw new ApiValidationException("pinnedUntil", "não pode ser anterior a hoje");
            }
            if (d.noClassDayId() != null) checkDay(who.organizationId(), d.noClassDayId(), dest.unitId());
            Created created = insert(who.organizationId(), who.accountId(), dest, type, title, body, channels, blankToNull(d.imageName()), pinnedUntil, at, now, d.noClassDayId());
            if (d.noClassDayId() != null) linkDay(who.organizationId(), d.noClassDayId(), at);
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("title", title);
            after.put("type", type);
            after.put("target", dest.kind());
            after.put("channels", String.join(",", channels));
            after.put("status", created.status());
            audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), "announcement.create", "announcement",
                    created.id().toString(), dest.unitId(), Map.of(), after, null, ip));
            return created.status().equals("SENT") ? new Created(created.id(), created.status(), sender.dispatch(who.organizationId(), created.id(), now)) : created;
        });
    }

    /** R11/CA20: dia ainda não avisado, da unidade do destino; o dia passa a "Agendado" (a tarefa ou o envio na hora o põe em "Enviado"). */
    private void checkDay(UUID org, UUID day, UUID unit) {
        var row =jdbc.sql("select unit_id, notice_status from no_class_day where organization_id = :org and id = :id").param("org", org).param("id", day)
                .query((rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getString(2)}).optional()
                .orElseThrow(() -> new ApiValidationException("noClassDayId", "dia sem aula não encontrado"));
        if (!"Ainda não avisado".equals(row[1]) || !row[0].equals(unit)) {
            throw new ApiStatusException(ErrorCode.CONFLICT, List.of(new ApiError.Field("noClassDayId", "o dia já foi avisado ou é de outra unidade")));
        }
    }

    private void linkDay(UUID org, UUID day, OffsetDateTime sendAt) {
        jdbc.sql("update no_class_day set notice_mode = :mode, notice_at = :at, notice_status = :status where organization_id = :org and id = :id")
                .param("mode", sendAt == null ? "NOW" : "SCHEDULED").param("at", sendAt, Types.TIMESTAMP_WITH_TIMEZONE)
                .param("status", sendAt == null ? "Ainda não avisado" : "Agendado").param("org", org).param("id", day).update();
    }

    /** Dados do aviso de um dia sem aula (RN-AGE-10); {@code mode} é NONE, NOW ou SCHEDULED. */
    public record DayNotice(UUID dayId, UUID unitId, LocalDate start, LocalDate end, String reason, List<UUID> classIds, String mode, java.time.Instant sendAt) {}

    private static String period(LocalDate start, LocalDate end) {
        var f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return start.equals(end) ? start.format(f) : start.format(f) + " a " + end.format(f);
    }

    /**
     * R11, na transação de quem chama: mantém um único comunicado do dia. Enviado fica como está; agendado é refeito com os dados novos (ou apagado, se o
     * modo é NONE); NOW envia na hora e o remetente põe o dia em "Enviado".
     */
    public void noClassDayNotice(UUID org, UUID author, DayNotice n) {
        Boolean sent = jdbc.sql("select sent_at is not null from announcement where organization_id = :org and no_class_day_id = :day").param("org", org).param("day", n.dayId())
                .query(Boolean.class).optional().orElse(null);
        if (Boolean.TRUE.equals(sent)) return;
        if (sent != null) jdbc.sql("delete from announcement where organization_id = :org and no_class_day_id = :day").param("org", org).param("day", n.dayId()).update();
        if ("NONE".equals(n.mode())) return;
        boolean classes = !n.classIds().isEmpty();
        Destination dest = new Destination(classes ? "CLASSES" : "UNIT", null, null, n.unitId(), classes ? n.classIds() : null);
        String when = period(n.start(), n.end());
        OffsetDateTime now = now();
        OffsetDateTime at = "SCHEDULED".equals(n.mode()) ? n.sendAt().atOffset(ZoneOffset.UTC) : null;
        Created created = insert(org, author, dest, "NO_CLASS_DAY", messages.getMessage("announcement.noClassDay.title", null, PT_BR),
                messages.getMessage("announcement.noClassDay.body", new Object[] {when, n.reason()}, PT_BR), List.of("PORTAL", "EMAIL"), null, null, at, now, n.dayId());
        if (at == null) sender.dispatch(org, created.id(), now);
    }

    /** R11: dia desfeito. O comunicado ainda agendado sai; se já foi enviado e o pedido marcou avisar, sai "Aviso geral" de aulas de volta para o mesmo destino. */
    public void noClassDayRemoved(UUID org, UUID author, UUID dayId, boolean notify) {
        var old = jdbc.sql("select id, sent_at is not null, target_kind, class_id, modality_id, unit_id, array(select class_id from announcement_class c "
                + "where c.organization_id = a.organization_id and c.announcement_id = a.id) from announcement a where a.organization_id = :org and a.no_class_day_id = :day")
                .param("org", org).param("day", dayId).query((rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getBoolean(2), rs.getString(3),
                        rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getObject(6, UUID.class), rs.getArray(7).getArray()}).optional().orElse(null);
        if (old == null) return;
        if (!(Boolean) old[1]) {
            jdbc.sql("delete from announcement where organization_id = :org and id = :id").param("org", org).param("id", old[0]).update();
            return;
        }
        if (!notify) return;
        List<UUID> classes = List.of((UUID[]) old[6]);
        Destination dest = new Destination((String) old[2], (UUID) old[3], (UUID) old[4], (UUID) old[5], "CLASSES".equals(old[2]) ? classes : null);
        OffsetDateTime now = now();
        Created created = insert(org, author, dest, "GENERAL", messages.getMessage("announcement.classesBack.title", null, PT_BR),
                messages.getMessage("announcement.classesBack.body", null, PT_BR), List.of("PORTAL", "EMAIL"), null, null, null, now, null);
        sender.dispatch(org, created.id(), now);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /**
     * Grava a linha (agendada para {@code sendAt} ou, sem ele, para agora: o envio logo a seguir a reivindica) com o limite do dia (R8). O envio na hora
     * fica a cargo de quem chama, depois da auditoria.
     */
    Created insert(UUID org, UUID author, Destination dest, String type, String title, String body, List<String> channels, String image, LocalDate pinnedUntil,
            OffsetDateTime sendAt, OffsetDateTime now, UUID noClassDayId) {
        OffsetDateTime when = sendAt != null ? sendAt : now;
        String zone = zoneOf(org, dest.unitId());
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:key, 0))").param("key", org + ":" + (dest.unitId() == null ? "ORGANIZATION" : dest.unitId())).query().singleRow();
        long used = jdbc.sql("select count(*) from announcement a where a.organization_id = :org and a.unit_id is not distinct from cast(:unit as uuid) "
                + "and (coalesce(a.sent_at, a.scheduled_for) at time zone :zone)::date = :day")
                .param("org", org).param("unit", dest.unitId(), Types.OTHER).param("zone", zone).param("day", when.atZoneSameInstant(ZoneId.of(zone)).toLocalDate())
                .query(Long.class).single();
        if (used >= DAILY_LIMIT) throw new ApiStatusException(ErrorCode.ANNOUNCEMENT_DAILY_LIMIT);
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into announcement (id, organization_id, unit_id, target_kind, class_id, modality_id, type, title, body, channels, scheduled_for, author_account_id, "
                + "image_name, pinned_until, no_class_day_id) values (:id, :org, :unit, :kind, :class, :mod, :type, :title, :body, cast(:channels as text[]), :when, :author, "
                + ":image, :pinned, :day)")
                .param("id", id).param("org", org).param("unit", dest.unitId(), Types.OTHER).param("kind", dest.kind()).param("class", dest.classId(), Types.OTHER)
                .param("mod", dest.modalityId(), Types.OTHER).param("type", type).param("title", title).param("body", body)
                .param("channels", "{" + String.join(",", channels) + "}").param("when", when).param("author", author).param("image", image, Types.VARCHAR)
                .param("pinned", pinnedUntil, Types.DATE).param("day", noClassDayId, Types.OTHER).update();
        if (dest.classIds() != null) {
            for (UUID classId : dest.classIds()) {
                jdbc.sql("insert into announcement_class (organization_id, announcement_id, class_id) values (:org, :id, :class)").param("org", org).param("id", id)
                        .param("class", classId).update();
            }
        }
        return new Created(id, sendAt == null ? "SENT" : "SCHEDULED", null);
    }

    /** O nome precisa ser de uma imagem de comunicado guardada nesta organização (a pasta é a do contexto). */
    private void checkImage(String name) {
        try {
            if (!name.startsWith(IMAGE_PREFIX)) throw new IllegalArgumentException("name");
            storage.readUrl(name);
        } catch (IllegalArgumentException | FileStorage.NotFound e) {
            throw new ApiValidationException("imageName", "imagem não encontrada");
        }
    }

    /** R9: mesma validação da logo (formato pelos bytes, até 5 MB), sem exigir 512 x 512; guarda no armazenamento da organização. */
    public Image uploadImage(AccountPrincipal who, byte[] content, String ip) {
        targets.requireAnnouncements(who);
        LogoImage image;
        try {
            image = LogoImage.parse(content, false);
        } catch (IllegalArgumentException e) {
            throw new ApiValidationException("file", e.getMessage());
        }
        String name = IMAGE_PREFIX + UUID.randomUUID() + "." + image.extension();
        storage.put(name, image.bytes(), image.contentType());
        transactions.execute(() -> {
            audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), "announcement.image.upload", "announcement", name,
                    null, Map.of(), Map.of("image", name), null, ip));
            return null;
        });
        return new Image(name, storage.readUrl(name));
    }
}

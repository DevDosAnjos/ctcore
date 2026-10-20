package br.com.ctcore.agenda;

import br.com.ctcore.agenda.NoClassDays.Item;
import br.com.ctcore.agenda.NoClassDays.Plan;
import br.com.ctcore.agenda.NoClassDays.PreviewLesson;
import br.com.ctcore.agenda.NoClassDays.UnitCtx;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RF-AGE-05: criar, editar e desfazer dia sem aula. Uma transação por chamada: dia, aulas canceladas e auditoria juntos (tudo ou nada). Só as
 * aulas com {@code no_class_day_id} do dia são devolvidas, para não reabrir cancelamento manual (D6). Nenhuma operação toca dado financeiro (RN-AGE-11).
 */
@Service
public class NoClassDayWrite {
    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final NoClassDays days;
    private final AuditService audit;

    public NoClassDayWrite(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, NoClassDays days, AuditService audit) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.days = days;
        this.audit = audit;
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name());
    }

    private static String noticeStatus(String mode) {
        return "SCHEDULED".equals(mode) ? "Agendado" : "Ainda não avisado";
    }

    private void requireFree(UUID org, UUID unit, LocalDate start, LocalDate end, UUID self) {
        int clash = jdbc.sql("select count(*) from no_class_day where organization_id = :org and unit_id = :unit and starts_on <= :e and ends_on >= :s "
                + "and (cast(:self as uuid) is null or id <> :self)").param("org", org).param("unit", unit).param("s", start).param("e", end)
                .param("self", self).query(Integer.class).single();
        if (clash > 0) throw new ApiStatusException(ErrorCode.CONFLICT, List.of(new ApiError.Field("startsOn", "já existe dia sem aula neste período")));
    }

    private void saveClasses(UUID org, UUID day, List<UUID> classIds) {
        for (UUID c : classIds) {
            jdbc.sql("insert into no_class_day_class (organization_id, no_class_day_id, class_id) values (:org, :day, :c)")
                    .param("org", org).param("day", day).param("c", c).update();
        }
    }

    private int cancel(UUID org, UUID day, String origin, String reason, List<PreviewLesson> lessons) {
        if (lessons.isEmpty()) return 0;
        return jdbc.sql("update lesson set cancelled = true, cancel_reason_code = :code, cancel_reason = :reason, no_class_day_id = :day "
                + "where organization_id = :org and id in (:ids)").param("code", "HOLIDAY".equals(origin) ? "HOLIDAY" : "OTHER").param("reason", reason)
                .param("day", day).param("org", org).param("ids", lessons.stream().map(PreviewLesson::id).toList()).update();
    }

    private int release(UUID org, UUID day) {
        return jdbc.sql("update lesson set cancelled = false, cancel_reason_code = null, cancel_reason = null, no_class_day_id = null "
                + "where organization_id = :org and no_class_day_id = :day").param("org", org).param("day", day).update();
    }

    /** R3: um dia por unidade, tudo ou nada. */
    public List<Item> create(AccountPrincipal who, NoClassDays.Input in, String ip) {
        return transactions.execute(() -> {
            Plan p = days.plan(who, in, true, null);
            UUID org = who.organizationId();
            for (UnitCtx u : p.units()) requireFree(org, u.id(), p.start(), p.end(), null);
            String origin = p.holiday() ? "HOLIDAY" : who.role() == Role.COORDINATOR ? "COORDINATOR" : "UNIT";
            List<Item> out = new ArrayList<>();
            for (UnitCtx u : p.units()) {
                UUID id = UUID.randomUUID();
                jdbc.sql("insert into no_class_day (id, organization_id, unit_id, starts_on, ends_on, reason, origin, notice_status, notice_mode, notice_at, created_by) "
                        + "values (:id, :org, :unit, :s, :e, :reason, :origin, :status, :mode, :at, :by)").param("id", id).param("org", org)
                        .param("unit", u.id()).param("s", p.start()).param("e", p.end()).param("reason", p.reason()).param("origin", origin)
                        .param("status", noticeStatus(p.mode())).param("mode", p.mode()).param("at", at(p.sendAt())).param("by", who.accountId()).update();
                saveClasses(org, id, p.classIds());
                int cancelled = cancel(org, id, origin, p.reason(), days.affected(org, u, p.start(), p.end(), p.classIds()));
                Map<String, Object> next = new LinkedHashMap<>();
                next.put("startsOn", p.start().toString());
                next.put("endsOn", p.end().toString());
                next.put("reason", p.reason());
                next.put("origin", origin);
                next.put("classIds", p.classIds().stream().map(UUID::toString).toList());
                next.put("noticeMode", p.mode());
                next.put("noticeAt", p.sendAt() == null ? null : p.sendAt().toString());
                next.put("cancelledLessons", cancelled);
                audit.record(new AuditEntry(actor(who), "no_class_day.created", "no_class_day", id.toString(), u.id(), null, next, null, ip));
                out.add(days.items(who, u, "and d.id = :id", Map.of("id", id), days.today(u)).get(0));
            }
            return out;
        });
    }

    private static OffsetDateTime at(Instant i) {
        return i == null ? null : i.atOffset(java.time.ZoneOffset.UTC);
    }

    private record Stored(UUID unitId, LocalDate startsOn, String status, String mode, Instant noticeAt, String origin) {}

    /** Dentro da transação: 404 fora da organização ou do alcance, 409 se o dia já começou (D5). */
    private Stored editable(AccountPrincipal who, UUID id) {
        days.requireManage(who);
        Stored s = jdbc.sql("select unit_id, starts_on, notice_status, notice_mode, notice_at, origin from no_class_day where id = :id and organization_id = :org")
                .param("id", id).param("org", who.organizationId()).query((rs, n) -> {
                    OffsetDateTime at = rs.getObject(5, OffsetDateTime.class);
                    return new Stored(rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class), rs.getString(3), rs.getString(4),
                            at == null ? null : at.toInstant(), rs.getString(6));
                }).optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
        UnitCtx u = days.unit(who, s.unitId());
        if (!s.startsOn().isAfter(days.today(u))) throw new ApiStatusException(ErrorCode.CONFLICT);
        return s;
    }

    /** R10: período, motivo, turmas e aviso; as aulas que saíram voltam e as recém afetadas são canceladas. */
    public Item update(AccountPrincipal who, UUID id, NoClassDays.Input in, String ip) {
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            Stored s = editable(who, id);
            if ("Enviado".equals(s.status()) && in != null && in.notice() != null && !sameNotice(s, in.notice())) {
                throw new ApiValidationException("notice", "dia já avisado: o aviso não muda");
            }
            Plan p = days.plan(who, in, true, List.of(s.unitId()));
            UnitCtx u = p.units().get(0);
            requireFree(org, u.id(), p.start(), p.end(), id);
            Item before = days.items(who, u, "and d.id = :id", Map.of("id", id), days.today(u)).get(0);
            boolean sent = "Enviado".equals(s.status());
            String mode = sent ? s.mode() : p.mode();
            Instant sendAt = sent ? s.noticeAt() : p.sendAt();
            int released = release(org, id);
            jdbc.sql("update no_class_day set starts_on = :s, ends_on = :e, reason = :reason, notice_mode = :mode, notice_at = :at, notice_status = :status "
                    + "where id = :id and organization_id = :org").param("s", p.start()).param("e", p.end()).param("reason", p.reason()).param("mode", mode)
                    .param("at", at(sendAt)).param("status", sent ? s.status() : noticeStatus(mode)).param("id", id).param("org", org).update();
            jdbc.sql("delete from no_class_day_class where no_class_day_id = :id and organization_id = :org").param("id", id).param("org", org).update();
            saveClasses(org, id, p.classIds());
            int cancelled = cancel(org, id, s.origin(), p.reason(), days.affected(org, u, p.start(), p.end(), p.classIds()));
            Item after = days.items(who, u, "and d.id = :id", Map.of("id", id), days.today(u)).get(0);
            Map<String, Object> next = days.snapshot(after);
            next.put("releasedLessons", released);
            next.put("cancelledLessons", cancelled);
            audit.record(new AuditEntry(actor(who), "no_class_day.updated", "no_class_day", id.toString(), u.id(), days.snapshot(before), next, null, ip));
            return after;
        });
    }

    private static boolean sameNotice(Stored s, NoClassDays.NoticeInput in) {
        try {
            return s.mode().equals(in.mode()) && (in.sendAt() == null ? s.noticeAt() == null : Instant.parse(in.sendAt()).equals(s.noticeAt()));
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** R11: apaga o dia e devolve as aulas que ele cancelou; {@code notify} só fica na auditoria até a Sprint 09. */
    public void delete(AccountPrincipal who, UUID id, boolean notify, String ip) {
        transactions.execute(() -> {
            UUID org = who.organizationId();
            Stored s = editable(who, id);
            UnitCtx u = days.unit(who, s.unitId());
            Item before = days.items(who, u, "and d.id = :id", Map.of("id", id), days.today(u)).get(0);
            int released = release(org, id);
            jdbc.sql("delete from no_class_day where id = :id and organization_id = :org").param("id", id).param("org", org).update();
            Map<String, Object> next = new LinkedHashMap<>();
            next.put("notify", notify);
            next.put("releasedLessons", released);
            audit.record(new AuditEntry(actor(who), "no_class_day.deleted", "no_class_day", id.toString(), u.id(), days.snapshot(before), next, null, ip));
            return null;
        });
    }
}

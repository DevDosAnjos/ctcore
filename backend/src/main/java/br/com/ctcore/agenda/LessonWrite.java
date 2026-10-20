package br.com.ctcore.agenda;

import br.com.ctcore.agenda.Agenda.Loaded;
import br.com.ctcore.agenda.Agenda.Status;
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
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import java.time.ZoneId;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-AGE-03: editar e cancelar aula. Organização, perfil e alcance vêm da sessão; {@code Agenda.can} é conferido de novo na gravação.
 * Uma transação por chamada (tudo ou nada). Sem lista de espera, presença ou frequência (RN-AGE-13).
 */
@Service
public class LessonWrite {
    private static final Pattern TIME = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");
    private static final Set<String> REASONS = Set.of("TEACHER_UNAVAILABLE", "HOLIDAY", "ACADEMY_EVENT", "OTHER");
    private static final Set<String> CHANNELS = Set.of("EMAIL", "WHATSAPP");
    private static final Set<String> TRIAL_DECISIONS = Set.of("REBOOK", "CANCEL_TRIALS");

    public record EditInput(String scope, String date, String startsAt, String endsAt, UUID teacherId, String reason) {}

    public record CancelInput(String reason, String reasonText, List<String> channels, String trialDecision) {}

    private record Edit(boolean following, LocalDate date, LocalTime start, LocalTime end, UUID teacherId, String reason) {}

    private record Row(UUID id, LocalDate date, LocalTime start, LocalTime end) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Agenda agenda;
    private final AuditService audit;
    private final Clock clock;
    private final Permissions permissions;

    public LessonWrite(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Agenda agenda, AuditService audit, Clock clock,
            Permissions permissions) {
        this.permissions = permissions;
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.agenda = agenda;
        this.audit = audit;
        this.clock = clock;
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name());
    }

    private static LocalDate date(String raw) {
        try {
            if (raw == null || raw.isBlank()) throw new ApiValidationException("date", "obrigatório");
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new ApiValidationException("date", "data inválida, use yyyy-MM-dd");
        }
    }

    private static LocalTime time(String field, String raw) {
        if (raw == null || !TIME.matcher(raw).matches()) throw new ApiValidationException(field, "horário HH:mm");
        return LocalTime.parse(raw);
    }

    private static Edit clean(EditInput in) {
        if (in == null) throw new ApiValidationException("scope", "obrigatório");
        if (!"THIS".equals(in.scope()) && !"FOLLOWING".equals(in.scope())) throw new ApiValidationException("scope", "use THIS ou FOLLOWING");
        LocalDate date = date(in.date());
        LocalTime start = time("startsAt", in.startsAt());
        LocalTime end = time("endsAt", in.endsAt());
        if (!end.isAfter(start)) throw new ApiValidationException("endsAt", "o término precisa ser depois do início");
        if (in.teacherId() == null) throw new ApiValidationException("teacherId", "obrigatório");
        String reason = in.reason() == null || in.reason().isBlank() ? null : in.reason().strip();
        if (reason != null && reason.length() > 200) throw new ApiValidationException("reason", "até 200 caracteres");
        return new Edit("FOLLOWING".equals(in.scope()), date, start, end, in.teacherId(), reason);
    }

    /** Dentro da transação: 403 sem AGENDA_EDIT; 409 se o estado da aula não permite. */
    private void requireAllowed(Loaded l, boolean edit) {
        if (!l.editPermission()) throw new AccessDeniedException("Permission denied");
        if (edit ? !l.can().edit() : !l.can().cancel()) throw new ApiStatusException(ErrorCode.CONFLICT);
    }

    public record CreateInput(UUID unitId, String title, UUID modalityId, String date, String startsAt, String endsAt, UUID teacherId,
            UUID visibilityClassId) {}

    private static ApiStatusException unprocessable(String field, String message) {
        return new ApiStatusException(ErrorCode.UNPROCESSABLE, List.of(new ApiError.Field(field, message)));
    }

    /** RF-AGE-04: cria aula avulsa (sem turma). Permissão e alcance antes da validação; uma transação, aula e auditoria juntas. */
    public Agenda.LessonDetail create(AccountPrincipal who, CreateInput in, String ip) {
        if (in == null) throw new ApiValidationException("unitId", "obrigatório");
        return transactions.execute(() -> {
            agenda.requireView(who);
            UUID org = who.organizationId();
            if (in.unitId() == null) throw new ApiValidationException("unitId", "obrigatório");
            String zone = jdbc.sql("select timezone from unit where id = :id and organization_id = :org and active")
                    .param("id", in.unitId()).param("org", org).query(String.class).optional()
                    .orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            if (who.role() != Role.COORDINATOR && (who.units() == null || !who.units().contains(in.unitId()))) {
                throw new ApiStatusException(ErrorCode.NOT_FOUND);
            }
            if (permissions.grant(who, Permission.AGENDA_EDIT, in.unitId()) == Access.NONE) throw new AccessDeniedException("Permission denied");
            if (who.role() == Role.TEACHER) {
                UUID me = jdbc.sql("select id from teacher where organization_id = :org and account_id = :acc")
                        .param("org", org).param("acc", who.accountId()).query(UUID.class).optional().orElse(null);
                if (me == null || !me.equals(in.teacherId())) throw new AccessDeniedException("Permission denied");
            }
            String title = in.title() == null ? "" : in.title().strip();
            if (title.isEmpty() || title.length() > 80) throw new ApiValidationException("title", "de 1 a 80 caracteres");
            if (in.modalityId() == null) throw new ApiValidationException("modalityId", "obrigatório");
            if (in.teacherId() == null) throw new ApiValidationException("teacherId", "obrigatório");
            LocalDate date = date(in.date());
            LocalTime start = time("startsAt", in.startsAt());
            LocalTime end = time("endsAt", in.endsAt());
            if (!end.isAfter(start)) throw new ApiValidationException("endsAt", "o término precisa ser depois do início");
            if (date.atTime(start).isBefore(LocalDateTime.ofInstant(clock.instant(), ZoneId.of(zone)))) {
                throw new ApiValidationException("date", "o início não pode ficar no passado");
            }
            int offered = jdbc.sql("select count(*) from modality m join organization_modality o on o.modality_id = m.id and o.organization_id = :org "
                    + "and o.active join unit_modality um on um.modality_id = m.id and um.unit_id = :unit and um.offered where m.id = :m")
                    .param("org", org).param("unit", in.unitId()).param("m", in.modalityId()).query(Integer.class).single();
            if (offered == 0) throw unprocessable("modalityId", "escolha uma modalidade ativa oferecida pela unidade");
            checkTeacher(org, in.teacherId(), null, in.modalityId());
            if (in.visibilityClassId() != null) {
                String cls = jdbc.sql("select unit_id, status from training_class where id = :id and organization_id = :org")
                        .param("id", in.visibilityClassId()).param("org", org).query((rs, n) -> rs.getObject(1, UUID.class) + "|" + rs.getString(2))
                        .optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
                if (!cls.equals(in.unitId() + "|ACTIVE")) throw unprocessable("visibilityClassId", "escolha uma turma ativa da unidade");
            }
            UUID id = UUID.randomUUID();
            jdbc.sql("insert into lesson (id, organization_id, unit_id, class_id, lesson_date, starts_at, ends_at, teacher_id, substitute, title, "
                    + "modality_id, visibility_class_id) values (:id, :org, :unit, null, :date, :start, :end, :teacher, false, :title, :m, :v)")
                    .param("id", id).param("org", org).param("unit", in.unitId()).param("date", date).param("start", start).param("end", end)
                    .param("teacher", in.teacherId()).param("title", title).param("m", in.modalityId()).param("v", in.visibilityClassId()).update();
            Map<String, Object> next = new LinkedHashMap<>();
            next.put("unitId", in.unitId().toString());
            next.put("title", title);
            next.put("modalityId", in.modalityId().toString());
            next.put("date", date.toString());
            next.put("startsAt", start.toString());
            next.put("endsAt", end.toString());
            next.put("teacherId", in.teacherId().toString());
            if (in.visibilityClassId() != null) next.put("visibilityClassId", in.visibilityClassId().toString());
            audit.record(new AuditEntry(actor(who), "lesson.created", "lesson", id.toString(), in.unitId(), null, next, null, ip));
            return agenda.load(who, id, false).detail();
        });
    }

    public Agenda.LessonDetail update(AccountPrincipal who, UUID id, EditInput input, String ip) {
        Edit e = clean(input);
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            Loaded l = agenda.load(who, id); // permissão de ver, alcance e 404
            requireAllowed(l, true);
            Agenda.LessonDetail before = l.detail();
            boolean completed = before.status() == Status.COMPLETED;
            if (completed) { // RN-AGE-08: só o professor, com motivo
                if (!e.date().toString().equals(before.date()) || !e.start().toString().equals(before.startsAt())
                        || !e.end().toString().equals(before.endsAt())) {
                    throw new ApiValidationException("date", "aula concluída: só o professor pode ser corrigido");
                }
                if (e.following()) throw new ApiValidationException("scope", "aula concluída: use THIS");
                if (e.reason() == null) throw new ApiValidationException("reason", "obrigatório para aula concluída");
            }
            boolean adhoc = before.trainingClass() == null; // RF-AGE-04: aula avulsa não tem série
            if (adhoc && e.following()) throw new ApiValidationException("scope", "aula avulsa: use THIS");
            UUID classId = adhoc ? null : before.trainingClass().id();
            checkTeacher(org, e.teacherId(), l.headId(), before.modality().id());
            boolean substitute = !adhoc && !e.teacherId().equals(l.headId());
            LocalDate oldDate = LocalDate.parse(before.date());
            long delta = ChronoUnit.DAYS.between(oldDate, e.date());
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), l.zone());

            List<Row> rows = new ArrayList<>();
            rows.add(new Row(id, oldDate, LocalTime.parse(before.startsAt()), LocalTime.parse(before.endsAt())));
            if (e.following()) {
                jdbc.sql("select id, lesson_date, starts_at, ends_at, cancelled from lesson where organization_id = :org and class_id = :class "
                        + "and lesson_date > :date and id <> :id order by lesson_date, starts_at").param("org", org).param("class", classId)
                        .param("date", oldDate).param("id", id).query((rs, n) -> {
                            LocalDate d = rs.getObject(2, LocalDate.class);
                            LocalTime s = rs.getObject(3, LocalTime.class);
                            LocalTime en = rs.getObject(4, LocalTime.class);
                            boolean open = Agenda.status(rs.getBoolean(5), d.atTime(s), d.atTime(en), now) == Status.SCHEDULED;
                            return open ? new Row(rs.getObject(1, UUID.class), d, s, en) : null;
                        }).list().stream().filter(r -> r != null).forEach(rows::add);
            }
            // Sem colidir na própria operação: avançando as datas, a última aula vai primeiro; recuando, a primeira.
            if (delta > 0) java.util.Collections.reverse(rows);
            for (Row r : rows) {
                LocalDate nd = r.date().plusDays(delta);
                boolean moved = !nd.equals(r.date()) || !e.start().equals(r.start());
                if (moved && nd.atTime(e.start()).isBefore(now)) {
                    throw new ApiValidationException("date", "o novo início não pode ficar no passado");
                }
                try {
                    jdbc.sql("update lesson set lesson_date = :date, starts_at = :start, ends_at = :end, teacher_id = :teacher, substitute = :sub, "
                            + "manually_edited = true, original_date = coalesce(original_date, :od), original_starts_at = coalesce(original_starts_at, :os) "
                            + "where id = :id and organization_id = :org").param("date", completed ? r.date() : nd).param("start", e.start())
                            .param("end", e.end()).param("teacher", e.teacherId()).param("sub", substitute).param("od", r.date())
                            .param("os", r.start()).param("id", r.id()).param("org", org).update();
                } catch (DuplicateKeyException ex) {
                    throw new ApiStatusException(ErrorCode.CONFLICT, List.of(new ApiError.Field("startsAt", "já existe aula da turma neste dia e horário")));
                }
            }
            Map<String, Object> old = snapshot(before.date(), before.startsAt(), before.endsAt(), before.teacher().id(), before.substitute());
            Map<String, Object> next = snapshot(e.date().toString(), e.start().toString(), e.end().toString(), e.teacherId(), substitute);
            next.put("scope", e.following() ? "FOLLOWING" : "THIS");
            next.put("lessons", rows.size());
            audit.record(new AuditEntry(actor(who), "lesson.updated", "lesson", id.toString(), before.unit().id(), old, next, e.reason(), ip));
            return agenda.load(who, id, false).detail();
        });
    }

    private static Map<String, Object> snapshot(String date, String start, String end, UUID teacher, boolean substitute) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date);
        m.put("startsAt", start);
        m.put("endsAt", end);
        m.put("teacherId", teacher.toString());
        m.put("substitute", substitute);
        return m;
    }

    /** RN-PRO-06 (D2): ativo, da organização e que ensina a modalidade da turma (o titular da turma sempre vale). */
    private void checkTeacher(UUID org, UUID teacherId, UUID headId, UUID modalityId) {
        int ok = jdbc.sql("select count(*) from teacher t where t.organization_id = :org and t.id = :id and t.status = 'ACTIVE' "
                + "and (t.id = :head or exists (select 1 from teacher_modality tm where tm.teacher_id = t.id and tm.modality_id = :m))")
                .param("org", org).param("id", teacherId).param("head", headId).param("m", modalityId).query(Integer.class).single();
        if (ok == 0) {
            throw new ApiStatusException(ErrorCode.UNPROCESSABLE,
                    List.of(new ApiError.Field("teacherId", "escolha um professor ativo que ensine a modalidade da aula")));
        }
    }

    /** RN-AGE-06: com aula experimental marcada, a decisão é obrigatória (422). */
    static void requireTrialDecision(int trialClasses, String decision) {
        if (trialClasses > 0 && decision == null) {
            throw new ApiStatusException(ErrorCode.UNPROCESSABLE,
                    List.of(new ApiError.Field("trialDecision", "há aula experimental marcada: escolha remarcar ou cancelar")));
        }
    }

    public Agenda.LessonDetail cancel(AccountPrincipal who, UUID id, CancelInput in, String ip) {
        if (in == null || in.reason() == null || !REASONS.contains(in.reason())) throw new ApiValidationException("reason", "motivo inválido");
        String text = null;
        if ("OTHER".equals(in.reason())) {
            text = in.reasonText() == null ? "" : in.reasonText().strip();
            if (text.isEmpty() || text.length() > 200) throw new ApiValidationException("reasonText", "de 1 a 200 caracteres");
        }
        Set<String> channels = new LinkedHashSet<>();
        for (String c : in.channels() == null ? List.<String>of() : in.channels()) {
            if (c == null || !CHANNELS.contains(c)) throw new ApiValidationException("channels", "use EMAIL ou WHATSAPP");
            channels.add(c);
        }
        if (in.trialDecision() != null && !TRIAL_DECISIONS.contains(in.trialDecision())) throw new ApiValidationException("trialDecision", "valor inválido");
        String cancelText = text;
        return transactions.execute(() -> {
            Loaded l = agenda.load(who, id);
            requireAllowed(l, false);
            requireTrialDecision(l.detail().trialClasses(), in.trialDecision());
            jdbc.sql("update lesson set cancelled = true, cancel_reason_code = :code, cancel_reason = :text where id = :id and organization_id = :org")
                    .param("code", in.reason()).param("text", cancelText).param("id", id).param("org", who.organizationId()).update();
            if (l.detail().trialClasses() > 0) agenda.resolveTrials(id, in.trialDecision());
            Map<String, Object> old = new LinkedHashMap<>();
            old.put("cancelled", false);
            Map<String, Object> next = new LinkedHashMap<>();
            next.put("cancelled", true);
            next.put("reasonCode", in.reason());
            if (cancelText != null) next.put("reasonText", cancelText);
            List<String> all = new ArrayList<>(List.of("PORTAL")); // o portal sempre recebe o aviso (RN-COM-03)
            all.addAll(channels);
            next.put("channels", all);
            if (in.trialDecision() != null) next.put("trialDecision", in.trialDecision());
            audit.record(new AuditEntry(actor(who), "lesson.cancelled", "lesson", id.toString(), l.detail().unit().id(), old, next, in.reason(), ip));
            return agenda.load(who, id, false).detail();
        });
    }
}

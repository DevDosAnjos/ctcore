package br.com.ctcore.agenda;

import br.com.ctcore.api.error.ApiError;
import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import br.com.ctcore.trainingclass.TrainingClasses.Ref;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-AGE-05: dias sem aula (leitura, sugestões de feriado, prévia) e a validação que a gravação reaproveita. Organização, perfil e alcance vêm da
 * sessão. Nada aqui lê ou grava dado financeiro (RN-AGE-11) nem lista de espera, presença ou frequência (RN-AGE-13).
 */
@Service
public class NoClassDays {
    static final int MAX_DAYS = 90;
    static final int MAX_REASON = 120;
    private static final Set<String> MODES = Set.of("NONE", "NOW", "SCHEDULED");

    public record NoticeInput(String mode, String sendAt) {}

    public record Input(List<UUID> unitIds, String startsOn, String endsOn, String reason, Boolean holiday, List<UUID> classIds, NoticeInput notice) {}

    public record Notice(String mode, String sendAt, String status) {}

    public record Item(UUID id, Ref unit, String startsOn, String endsOn, String reason, String origin, boolean allClasses, List<Ref> classes,
            Notice notice, int cancelledLessons, boolean canEdit) {}

    public record Suggestion(String code, String date) {}

    public record View(List<Ref> units, Ref unit, List<Item> days, List<Suggestion> suggestions) {}

    public record PreviewLesson(UUID id, String date, String startsAt, String endsAt, @JsonProperty("class") Ref trainingClass, String title) {}

    public record UnitPreview(Ref unit, List<PreviewLesson> lessons) {}

    public record Preview(List<UnitPreview> units) {}

    record UnitCtx(UUID id, String name, ZoneId zone) {
        Ref ref() {
            return new Ref(id, name);
        }
    }

    record Plan(List<UnitCtx> units, LocalDate start, LocalDate end, String reason, boolean holiday, List<UUID> classIds, String mode,
            Instant sendAt) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final Clock clock;

    public NoClassDays(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.clock = clock;
    }

    void requireManage(AccountPrincipal who) {
        if (who == null || who.role() == null || permissions.effective(who).get(Permission.NO_CLASS_DAYS) == Access.NONE) {
            throw new AccessDeniedException("Permission denied");
        }
    }

    private static ApiStatusException unprocessable(String field, String message) {
        return new ApiStatusException(ErrorCode.UNPROCESSABLE, List.of(new ApiError.Field(field, message)));
    }

    private static LocalDate date(String field, String raw) {
        if (raw == null || raw.isBlank()) throw new ApiValidationException(field, "obrigatório");
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new ApiValidationException(field, "data inválida, use yyyy-MM-dd");
        }
    }

    /** Dentro da transação: unidade da organização e do alcance (404 senão), com a permissão conferida na unidade (403). */
    UnitCtx unit(AccountPrincipal who, UUID id) {
        if (who.role() != Role.COORDINATOR && (who.units() == null || !who.units().contains(id))) throw new ApiStatusException(ErrorCode.NOT_FOUND);
        UnitCtx u = jdbc.sql("select id, name, timezone from unit where id = :id and organization_id = :org and active")
                .param("id", id).param("org", who.organizationId())
                .query((rs, n) -> new UnitCtx(rs.getObject(1, UUID.class), rs.getString(2), ZoneId.of(rs.getString(3)))).optional()
                .orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
        if (permissions.grant(who, Permission.NO_CLASS_DAYS, id) == Access.NONE) throw new AccessDeniedException("Permission denied");
        return u;
    }

    LocalDate today(UnitCtx u) {
        return LocalDate.ofInstant(clock.instant(), u.zone());
    }

    /**
     * Valida período, unidades, turmas e aviso. Ordem: 403, 400 de formato, 404 de unidade, 400 que depende do fuso, 404/422 de turma. A prévia não
     * exige motivo nem aviso. {@code fixedUnits} traz a unidade do dia (PUT); nulo resolve de {@code in.unitIds()}.
     */
    Plan plan(AccountPrincipal who, Input in, boolean full, List<UUID> fixedUnits) {
        requireManage(who);
        if (in == null) throw new ApiValidationException("unitIds", "obrigatório");
        List<UUID> ids = fixedUnits != null ? fixedUnits : in.unitIds() == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(in.unitIds()));
        if (ids.isEmpty() || ids.stream().anyMatch(java.util.Objects::isNull)) throw new ApiValidationException("unitIds", "obrigatório");
        if (who.role() != Role.COORDINATOR && ids.size() > 1) throw new ApiValidationException("unitIds", "informe uma só unidade");
        LocalDate start = date("startsOn", in.startsOn());
        LocalDate end = date("endsOn", in.endsOn());
        if (end.isBefore(start)) throw new ApiValidationException("endsOn", "não pode ser anterior a startsOn");
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) throw new ApiValidationException("endsOn", "período de no máximo " + MAX_DAYS + " dias");
        String reason = in.reason() == null ? "" : in.reason().strip();
        if (full && (reason.isEmpty() || reason.length() > MAX_REASON)) throw new ApiValidationException("reason", "de 1 a " + MAX_REASON + " caracteres");
        String mode = "NONE";
        Instant sendAt = null;
        if (full && in.notice() != null) {
            mode = in.notice().mode();
            if (mode == null || !MODES.contains(mode)) throw new ApiValidationException("notice", "modo inválido");
            if ("SCHEDULED".equals(mode)) {
                if (in.notice().sendAt() == null) throw new ApiValidationException("notice", "sendAt obrigatório para aviso agendado");
                try {
                    sendAt = Instant.parse(in.notice().sendAt());
                } catch (DateTimeParseException e) {
                    throw new ApiValidationException("notice", "sendAt inválido, use um instante ISO 8601");
                }
            }
        }
        List<UnitCtx> units = new ArrayList<>();
        for (UUID id : ids) units.add(unit(who, id));
        for (UnitCtx u : units) {
            if (start.isBefore(today(u))) throw new ApiValidationException("startsOn", "o início não pode ficar no passado");
            if (sendAt != null && (!sendAt.isAfter(clock.instant()) || !sendAt.isBefore(start.atStartOfDay(u.zone()).toInstant()))) {
                throw new ApiValidationException("notice", "sendAt precisa ser futuro e antes do início do dia");
            }
        }
        List<UUID> classIds = in.classIds() == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(in.classIds()));
        if (!classIds.isEmpty()) {
            if (units.size() > 1) throw unprocessable("classIds", "turmas só com uma unidade");
            for (UUID c : classIds) {
                if (c == null) throw new ApiValidationException("classIds", "turma inválida");
                String row = jdbc.sql("select unit_id, status from training_class where id = :id and organization_id = :org")
                        .param("id", c).param("org", who.organizationId()).query((rs, n) -> rs.getObject(1, UUID.class) + "|" + rs.getString(2))
                        .optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
                if (!row.equals(units.get(0).id() + "|ACTIVE")) throw unprocessable("classIds", "escolha turmas ativas da unidade");
            }
        }
        return new Plan(units, start, end, reason, Boolean.TRUE.equals(in.holiday()), classIds, mode, sendAt);
    }

    /** RN-AGE-10: aulas da unidade no período, de turma escolhida (ou todas), que não terminaram e não estão canceladas. */
    List<PreviewLesson> affected(UUID org, UnitCtx u, LocalDate start, LocalDate end, List<UUID> classIds) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), u.zone());
        var spec = jdbc.sql("select l.id, l.lesson_date, l.starts_at, l.ends_at, c.id, c.name, l.title from lesson l "
                + "left join training_class c on c.id = l.class_id and c.organization_id = l.organization_id "
                + "where l.organization_id = :org and l.unit_id = :unit and l.lesson_date between :s and :e and not l.cancelled "
                + "and (l.lesson_date + l.ends_at) > cast(:now as timestamp)" + (classIds.isEmpty() ? "" : " and l.class_id in (:classes)")
                + " order by l.lesson_date, l.starts_at, l.id")
                .param("org", org).param("unit", u.id()).param("s", start).param("e", end).param("now", now);
        if (!classIds.isEmpty()) spec = spec.param("classes", classIds);
        return spec.query((rs, n) -> {
            UUID cid = rs.getObject(5, UUID.class);
            return new PreviewLesson(rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class).toString(), rs.getObject(3, LocalTime.class).toString(),
                    rs.getObject(4, LocalTime.class).toString(), cid == null ? null : new Ref(cid, rs.getString(6)), rs.getString(7));
        }).list();
    }

    /** R9: a prévia, sem gravar. */
    public Preview preview(AccountPrincipal who, Input in) {
        return transactions.execute(() -> {
            Plan p = plan(who, in, false, null);
            return new Preview(p.units().stream().map(u -> new UnitPreview(u.ref(), affected(who.organizationId(), u, p.start(), p.end(), p.classIds()))).toList());
        });
    }

    /** R1, R2: dias da unidade que ainda não terminaram e feriados dos próximos 12 meses ainda não confirmados. */
    public View view(AccountPrincipal who, UUID unitId) {
        requireManage(who);
        boolean coordinator = who.role() == Role.COORDINATOR;
        Set<UUID> reach = who.units() == null ? Set.of() : who.units();
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            List<Ref> units = jdbc.sql("select id, name from unit where organization_id = :org and active order by name, id")
                    .param("org", org).query((rs, n) -> new Ref(rs.getObject(1, UUID.class), rs.getString(2))).list().stream()
                    .filter(u -> coordinator || reach.contains(u.id())).toList();
            Ref unit;
            if (unitId != null) {
                unit = units.stream().filter(u -> u.id().equals(unitId)).findFirst().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            } else if (units.isEmpty()) {
                return new View(units, null, List.of(), List.of());
            } else {
                unit = units.get(0);
            }
            UnitCtx u = unit(who, unit.id());
            LocalDate today = today(u);
            List<Item> days = items(who, u, "and d.ends_on >= :today", Map.of("today", today), today);
            Set<LocalDate> taken = new LinkedHashSet<>();
            for (Item d : days) taken.add(LocalDate.parse(d.startsOn()));
            List<Suggestion> suggestions = Holidays.next12Months(today).stream().filter(h -> !taken.contains(h.date()))
                    .map(h -> new Suggestion(h.code(), h.date().toString())).toList();
            return new View(units, unit, days, suggestions);
        });
    }

    static String status(String stored) {
        return switch (stored) {
            case "Agendado" -> "SCHEDULED";
            case "Enviado" -> "SENT";
            default -> "NOT_NOTIFIED";
        };
    }

    /** Dentro da transação. {@code extra} é fixo no código; só os valores vêm de parâmetros ligados. */
    List<Item> items(AccountPrincipal who, UnitCtx u, String extra, Map<String, Object> params, LocalDate today) {
        UUID org = who.organizationId();
        return jdbc.sql("select d.id, d.starts_on, d.ends_on, d.reason, d.origin, d.notice_mode, d.notice_at, d.notice_status, "
                + "(select count(*) from lesson l where l.no_class_day_id = d.id and l.cancelled) from no_class_day d "
                + "where d.organization_id = :org and d.unit_id = :unit " + extra + " order by d.starts_on, d.id")
                .param("org", org).param("unit", u.id()).params(params).query((rs, n) -> {
                    UUID id = rs.getObject(1, UUID.class);
                    LocalDate start = rs.getObject(2, LocalDate.class);
                    List<Ref> classes = jdbc.sql("select c.id, c.name from no_class_day_class x join training_class c on c.id = x.class_id "
                            + "and c.organization_id = x.organization_id where x.no_class_day_id = :id and x.organization_id = :org order by c.name_key, c.id")
                            .param("id", id).param("org", org).query((r, i) -> new Ref(r.getObject(1, UUID.class), r.getString(2))).list();
                    OffsetDateTime at = rs.getObject(7, OffsetDateTime.class);
                    return new Item(id, u.ref(), start.toString(), rs.getObject(3, LocalDate.class).toString(), rs.getString(4), rs.getString(5),
                            classes.isEmpty(), classes, new Notice(rs.getString(6), at == null ? null : at.toInstant().toString(), status(rs.getString(8))),
                            rs.getInt(9), start.isAfter(today));
                }).list();
    }

    Map<String, Object> snapshot(Item d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("startsOn", d.startsOn());
        m.put("endsOn", d.endsOn());
        m.put("reason", d.reason());
        m.put("origin", d.origin());
        m.put("classIds", d.classes().stream().map(c -> c.id().toString()).toList());
        m.put("noticeMode", d.notice().mode());
        m.put("noticeAt", d.notice().sendAt());
        m.put("cancelledLessons", d.cancelledLessons());
        return m;
    }
}

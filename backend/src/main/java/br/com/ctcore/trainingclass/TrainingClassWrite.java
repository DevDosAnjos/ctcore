package br.com.ctcore.trainingclass;

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
import br.com.ctcore.modality.Modalities;
import java.net.URI;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-TUR-03: criar e editar turma, e as opções do formulário. Organização, perfil e alcance vêm da sessão. A unidade não muda depois
 * de criada (D2) e turma desativada não é editável (D3). Não há presença nem frequência aqui (RN-TUR-06).
 */
@Service
public class TrainingClassWrite {
    private static final Pattern TIME = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    public record SlotInput(Integer weekday, String startsAt, String endsAt) {}

    public record Input(UUID unitId, String name, UUID modalityId, UUID ageRangeId, List<SlotInput> schedule, Integer capacity,
            UUID headTeacherId, List<UUID> assistantIds, String groupLink, Boolean notifyClass) {}

    public record UnitOption(UUID id, String name, List<TrainingClasses.Ref> modalities, List<TrainingClasses.Ref> teachers) {}

    public record Options(List<UnitOption> units, List<TrainingClasses.AgeRangeRef> ageRanges) {}

    private record Clean(UUID unitId, String name, UUID modalityId, UUID ageRangeId, List<TrainingClasses.Slot> schedule, Integer capacity,
            UUID headId, Set<UUID> assistants, String groupLink, Boolean notifyClass) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final TrainingClasses classes;
    private final ClassScheduleChange scheduleChange;
    private final AuditService audit;

    public TrainingClassWrite(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, TrainingClasses classes,
            ClassScheduleChange scheduleChange, AuditService audit) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.classes = classes;
        this.scheduleChange = scheduleChange;
        this.audit = audit;
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name());
    }

    private static Clean clean(Input in) {
        if (in == null) throw new ApiValidationException("name", "obrigatório");
        String name = in.name() == null ? "" : in.name().strip();
        if (name.length() < 2 || name.length() > 80) throw new ApiValidationException("name", "de 2 a 80 caracteres");
        if (in.modalityId() == null) throw new ApiValidationException("modalityId", "obrigatório");
        if (in.headTeacherId() == null) throw new ApiValidationException("headTeacherId", "obrigatório");
        if (in.capacity() != null && (in.capacity() < 1 || in.capacity() > 200)) throw new ApiValidationException("capacity", "de 1 a 200");
        List<TrainingClasses.Slot> schedule = slots(in.schedule());
        Set<UUID> assistants = new LinkedHashSet<>();
        for (UUID a : in.assistantIds() == null ? List.<UUID>of() : in.assistantIds()) {
            if (a == null || a.equals(in.headTeacherId()) || !assistants.add(a)) {
                throw new ApiValidationException("assistantIds", "auxiliar repetido ou igual ao titular");
            }
        }
        return new Clean(in.unitId(), name, in.modalityId(), in.ageRangeId(), schedule, in.capacity(), in.headTeacherId(), assistants,
                link(in.groupLink()), in.notifyClass());
    }

    private static List<TrainingClasses.Slot> slots(List<SlotInput> asked) {
        if (asked == null || asked.isEmpty()) throw new ApiValidationException("schedule", "informe ao menos um horário");
        Set<Integer> days = new HashSet<>();
        List<TrainingClasses.Slot> out = new ArrayList<>();
        for (SlotInput s : asked) {
            if (s == null || s.weekday() == null || s.weekday() < 1 || s.weekday() > 7 || !days.add(s.weekday())) {
                throw new ApiValidationException("schedule", "dia de 1 a 7, sem repetir");
            }
            if (s.startsAt() == null || s.endsAt() == null || !TIME.matcher(s.startsAt()).matches() || !TIME.matcher(s.endsAt()).matches()
                    || LocalTime.parse(s.endsAt()).compareTo(LocalTime.parse(s.startsAt())) <= 0) {
                throw new ApiValidationException("schedule", "horário HH:mm, com término depois do início");
            }
            out.add(new TrainingClasses.Slot(s.weekday(), s.startsAt(), s.endsAt()));
        }
        out.sort(Comparator.comparingInt(TrainingClasses.Slot::weekday));
        return out;
    }

    /** RF-TUR-03 (D7): vazio vira nulo; só http ou https, até 300, sem espaços. */
    private static String link(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String bad = "http ou https, até 300 caracteres";
        if (raw.length() > 300 || !raw.equals(raw.strip())) throw new ApiValidationException("groupLink", bad);
        try {
            URI uri = new URI(raw);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null) {
                throw new ApiValidationException("groupLink", bad);
            }
        } catch (java.net.URISyntaxException e) {
            throw new ApiValidationException("groupLink", bad);
        }
        return raw;
    }

    public TrainingClasses.Detail create(AccountPrincipal who, Input input, String ip) {
        classes.requireAccess(who);
        Clean c = clean(input);
        if (c.unitId() == null) throw new ApiValidationException("unitId", "obrigatório");
        return transactions.execute(() -> {
            if (who.role() == Role.ADMINISTRATOR && (who.units() == null || !who.units().contains(c.unitId()))) {
                throw new AccessDeniedException("Unit out of reach");
            }
            int unit = jdbc.sql("select count(*) from unit where organization_id = :org and active and id = :id")
                    .param("org", who.organizationId()).param("id", c.unitId()).query(Integer.class).single();
            if (unit == 0) throw new ApiValidationException("unitId", "escolha uma unidade ativa da organização");
            checkReferences(who, c, c.unitId());
            refuseTakenName(who, c.unitId(), c.name(), null);
            UUID id = UUID.randomUUID();
            try {
                jdbc.sql("insert into training_class (id, organization_id, unit_id, name, name_key, modality_id, age_range_id, capacity, "
                        + "head_teacher_id, group_link) values (:id, :org, :unit, :name, :key, :modality, :age, :capacity, :head, :link)")
                        .param("id", id).param("org", who.organizationId()).param("unit", c.unitId()).param("name", c.name())
                        .param("key", Modalities.normalize(c.name())).param("modality", c.modalityId()).param("age", c.ageRangeId())
                        .param("capacity", c.capacity()).param("head", c.headId()).param("link", c.groupLink()).update();
            } catch (DuplicateKeyException e) {
                throw nameTaken();
            }
            replaceChildren(who, id, c);
            audit.record(new AuditEntry(actor(who), "class.created", "training_class", id.toString(), c.unitId(), null,
                    snapshot(c.name(), c.modalityId(), c.ageRangeId(), c.schedule(), c.capacity(), c.headId(), c.assistants(), c.groupLink(), null),
                    null, ip));
            return classes.get(who, id);
        });
    }

    public TrainingClasses.Detail update(AccountPrincipal who, UUID id, Input input, String ip) {
        classes.requireAccess(who);
        Clean c = clean(input);
        return transactions.execute(() -> {
            TrainingClasses.Detail before = classes.get(who, id); // permissão, alcance e 404
            UUID unit = before.unit().id();
            jdbc.sql("select id from training_class where id = :id and organization_id = :org for update")
                    .param("id", id).param("org", who.organizationId()).query(UUID.class).single();
            if (before.status().equals("INACTIVE")) throw new ApiStatusException(ErrorCode.CONFLICT);
            checkReferences(who, c, unit);
            refuseTakenName(who, unit, c.name(), id);
            try {
                jdbc.sql("update training_class set name = :name, name_key = :key, modality_id = :modality, age_range_id = :age, capacity = :capacity, "
                        + "head_teacher_id = :head, group_link = :link where id = :id and organization_id = :org")
                        .param("name", c.name()).param("key", Modalities.normalize(c.name())).param("modality", c.modalityId())
                        .param("age", c.ageRangeId()).param("capacity", c.capacity()).param("head", c.headId()).param("link", c.groupLink())
                        .param("id", id).param("org", who.organizationId()).update();
            } catch (DuplicateKeyException e) {
                throw nameTaken();
            }
            replaceChildren(who, id, c);
            boolean changed = !before.schedule().equals(c.schedule());
            boolean notify = changed && !Boolean.FALSE.equals(c.notifyClass());
            if (changed) scheduleChange.changed(id, before.schedule(), c.schedule());
            Set<UUID> oldAssistants = new LinkedHashSet<>(before.assistants().stream().map(TrainingClasses.Ref::id).toList());
            Map<String, Object> old = snapshot(before.name(), before.modality().id(), before.ageRange() == null ? null : before.ageRange().id(),
                    before.schedule(), before.capacity(), before.head().id(), oldAssistants, before.groupLink(), before.groupLink());
            Map<String, Object> next = snapshot(c.name(), c.modalityId(), c.ageRangeId(), c.schedule(), c.capacity(), c.headId(), c.assistants(),
                    c.groupLink(), before.groupLink());
            if (changed) next.put("notifyClass", notify);
            audit.record(new AuditEntry(actor(who), "class.updated", "training_class", id.toString(), unit, old, next, null, ip));
            return classes.get(who, id).withScheduleChanged(changed);
        });
    }

    /** D8: unidades ativas do alcance, com modalidades oferecidas e professores ativos de cada uma, e as faixas da organização. */
    public Options options(AccountPrincipal who) {
        classes.requireAccess(who);
        boolean admin = who.role() == Role.ADMINISTRATOR;
        Set<UUID> reach = who.units() == null ? Set.of() : who.units();
        if (admin && reach.isEmpty()) return new Options(List.of(), List.of());
        return transactions.execute(() -> {
            var units = jdbc.sql("select id, name from unit where organization_id = :org and active" + (admin ? " and id in (:reach)" : "")
                    + " order by name, id").param("org", who.organizationId());
            if (admin) units = units.param("reach", reach);
            List<UnitOption> out = units.query((rs, n) -> new UnitOption(rs.getObject(1, UUID.class), rs.getString(2), null, null)).list().stream()
                    .map(u -> new UnitOption(u.id(), u.name(),
                            jdbc.sql("select m.id, m.name from modality m join organization_modality o on o.modality_id = m.id and o.organization_id = :org and o.active "
                                    + "join unit_modality um on um.modality_id = m.id and um.unit_id = :unit and um.offered order by m.name, m.id")
                                    .param("org", who.organizationId()).param("unit", u.id())
                                    .query((rs, n) -> new TrainingClasses.Ref(rs.getObject(1, UUID.class), rs.getString(2))).list(),
                            jdbc.sql("select t.id, t.name from teacher t join teacher_unit tu on tu.teacher_id = t.id and tu.unit_id = :unit "
                                    + "where t.organization_id = :org and t.status = 'ACTIVE' order by t.name_key, t.id")
                                    .param("org", who.organizationId()).param("unit", u.id())
                                    .query((rs, n) -> new TrainingClasses.Ref(rs.getObject(1, UUID.class), rs.getString(2))).list()))
                    .toList();
            List<TrainingClasses.AgeRangeRef> ages = jdbc.sql("select id, name, min_age, max_age from age_range where organization_id = :org order by min_age, name, id")
                    .param("org", who.organizationId())
                    .query((rs, n) -> new TrainingClasses.AgeRangeRef(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3), (Integer) rs.getObject(4)))
                    .list();
            return new Options(out, ages);
        });
    }

    /** Modalidade oferecida e ativa, faixa da organização, titular e auxiliares ativos da unidade (400 no campo). */
    private void checkReferences(AccountPrincipal who, Clean c, UUID unit) {
        int offered = jdbc.sql("select count(*) from organization_modality o join unit_modality um on um.modality_id = o.modality_id "
                + "and um.unit_id = :unit and um.offered where o.organization_id = :org and o.modality_id = :m and o.active")
                .param("org", who.organizationId()).param("unit", unit).param("m", c.modalityId()).query(Integer.class).single();
        if (offered == 0) throw new ApiValidationException("modalityId", "escolha uma modalidade ativa oferecida pela unidade");
        if (c.ageRangeId() != null && jdbc.sql("select count(*) from age_range where organization_id = :org and id = :id")
                .param("org", who.organizationId()).param("id", c.ageRangeId()).query(Integer.class).single() == 0) {
            throw new ApiValidationException("ageRangeId", "faixa etária inválida");
        }
        if (!activeTeachers(who, unit, Set.of(c.headId()))) throw new ApiValidationException("headTeacherId", "escolha um professor ativo da unidade");
        if (!c.assistants().isEmpty() && !activeTeachers(who, unit, c.assistants())) {
            throw new ApiValidationException("assistantIds", "escolha professores ativos da unidade");
        }
    }

    private boolean activeTeachers(AccountPrincipal who, UUID unit, Set<UUID> ids) {
        int found = jdbc.sql("select count(*) from teacher t where t.organization_id = :org and t.status = 'ACTIVE' and t.id in (:ids) "
                + "and exists (select 1 from teacher_unit tu where tu.teacher_id = t.id and tu.unit_id = :unit)")
                .param("org", who.organizationId()).param("ids", ids).param("unit", unit).query(Integer.class).single();
        return found == ids.size();
    }

    /** RN-TUR-01: nome único na unidade entre turmas ativas, sem caixa nem acento; a própria turma não conta. */
    private void refuseTakenName(AccountPrincipal who, UUID unit, String name, UUID self) {
        int taken = jdbc.sql("select count(*) from training_class where organization_id = :org and unit_id = :unit and name_key = :key "
                + "and status = 'ACTIVE' and (cast(:self as uuid) is null or id <> :self)")
                .param("org", who.organizationId()).param("unit", unit).param("key", Modalities.normalize(name)).param("self", self)
                .query(Integer.class).single();
        if (taken > 0) throw nameTaken();
    }

    private static ApiStatusException nameTaken() {
        return new ApiStatusException(ErrorCode.CONFLICT, List.of(new ApiError.Field("name", "já existe uma turma ativa com este nome na unidade")));
    }

    private void replaceChildren(AccountPrincipal who, UUID id, Clean c) {
        jdbc.sql("delete from training_class_schedule where organization_id = :org and class_id = :id").param("org", who.organizationId()).param("id", id).update();
        jdbc.sql("delete from training_class_assistant where organization_id = :org and class_id = :id").param("org", who.organizationId()).param("id", id).update();
        for (TrainingClasses.Slot s : c.schedule()) {
            jdbc.sql("insert into training_class_schedule (organization_id, class_id, weekday, starts_at, ends_at) values (:org, :id, :d, :s, :e)")
                    .param("org", who.organizationId()).param("id", id).param("d", s.weekday()).param("s", LocalTime.parse(s.startsAt()))
                    .param("e", LocalTime.parse(s.endsAt())).update();
        }
        for (UUID a : c.assistants()) {
            jdbc.sql("insert into training_class_assistant (organization_id, class_id, teacher_id) values (:org, :id, :t)")
                    .param("org", who.organizationId()).param("id", id).param("t", a).update();
        }
    }

    // O link nunca vai à auditoria: só "informado" (ou "alterado" quando o valor mudou).
    private static Map<String, Object> snapshot(String name, UUID modality, UUID age, List<TrainingClasses.Slot> schedule, Integer capacity,
            UUID head, Set<UUID> assistants, String link, String previousLink) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("modalityId", modality == null ? null : modality.toString());
        m.put("ageRangeId", age == null ? null : age.toString());
        m.put("schedule", schedule.stream().map(s -> s.weekday() + " " + s.startsAt() + "-" + s.endsAt()).toList());
        m.put("capacity", capacity);
        m.put("headTeacherId", head.toString());
        m.put("assistantIds", new TreeSet<>(assistants).stream().map(UUID::toString).toList());
        m.put("groupLink", link == null ? null : Objects.equals(link, previousLink) ? "informado" : previousLink == null ? "informado" : "alterado");
        return m;
    }
}

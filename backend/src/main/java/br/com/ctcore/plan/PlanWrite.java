package br.com.ctcore.plan;

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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-PLA-03: criar, editar, desativar e ativar plano, opções da janela e impacto da mudança de valor. Organização, perfil e alcance vêm
 * da sessão. Unidade e modalidade não mudam depois de criado (D2); plano nunca é excluído (RN-PLA-06). Nada aqui controla aulas feitas (RN-PLA-02).
 */
@Service
public class PlanWrite {
    private static final long MAX_CENTS = 99_999_999L;

    public record Input(UUID unitId, UUID modalityId, String name, BigDecimal valueCents, Integer weeklyFrequency, Boolean validOtherUnits,
            String unpaidCharges, Boolean paidFutureDifference) {}

    public record ModalityOption(UUID id, String name) {}

    public record UnitOption(UUID id, String name, List<ModalityOption> modalities) {}

    public record Options(String crossUnitTraining, List<UnitOption> units) {}

    public record ValueImpact(int studentsAffected, String effectiveFrom, int unpaidCharges, int paidFutureCharges) {}

    private record Clean(UUID unitId, UUID modalityId, String name, long valueCents, Integer weeklyFrequency, boolean validOtherUnits,
            String unpaidCharges, boolean paidFutureDifference) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Plans plans;
    private final AuditService audit;
    private final Clock clock;

    public PlanWrite(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Plans plans, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.plans = plans;
        this.audit = audit;
        this.clock = clock;
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name());
    }

    private static long cents(BigDecimal raw) {
        if (raw == null) throw new ApiValidationException("valueCents", "obrigatório");
        if (raw.stripTrailingZeros().scale() > 0 || raw.compareTo(BigDecimal.ONE) < 0 || raw.compareTo(BigDecimal.valueOf(MAX_CENTS)) > 0) {
            throw new ApiValidationException("valueCents", "inteiro de 1 a 99999999 centavos");
        }
        return raw.longValueExact();
    }

    private static Clean clean(Input in) {
        if (in == null) throw new ApiValidationException("name", "obrigatório");
        String name = in.name() == null ? "" : in.name().strip();
        if (name.length() < 2 || name.length() > 60) throw new ApiValidationException("name", "de 2 a 60 caracteres");
        long value = cents(in.valueCents());
        if (in.weeklyFrequency() != null && in.weeklyFrequency() != 2 && in.weeklyFrequency() != 3) {
            throw new ApiValidationException("weeklyFrequency", "2, 3 ou vazio (livre)");
        }
        String unpaid = in.unpaidCharges() == null ? "KEEP" : in.unpaidCharges();
        if (!unpaid.equals("KEEP") && !unpaid.equals("ADJUST")) throw new ApiValidationException("unpaidCharges", "KEEP ou ADJUST");
        return new Clean(in.unitId(), in.modalityId(), name, value, in.weeklyFrequency(), Boolean.TRUE.equals(in.validOtherUnits()), unpaid,
                Boolean.TRUE.equals(in.paidFutureDifference()));
    }

    private String crossUnitTraining(AccountPrincipal who) {
        return jdbc.sql("select cross_unit_training from organization_settings where organization_id = :org")
                .param("org", who.organizationId()).query(String.class).optional().orElse("plan");
    }

    /** D4: o campo só vale com a configuração "plan"; nos outros casos a regra da organização manda e o plano guarda falso. */
    private boolean validOtherUnits(AccountPrincipal who, boolean asked) {
        return asked && crossUnitTraining(who).equals("plan");
    }

    public Plans.Row create(AccountPrincipal who, Input input, String ip) {
        plans.requireAccess(who);
        Clean c = clean(input);
        if (c.unitId() == null) throw new ApiValidationException("unitId", "obrigatório");
        if (c.modalityId() == null) throw new ApiValidationException("modalityId", "obrigatório");
        return transactions.execute(() -> {
            if (who.role() == Role.ADMINISTRATOR && (who.units() == null || !who.units().contains(c.unitId()))) {
                throw new AccessDeniedException("Unit out of reach");
            }
            int unit = jdbc.sql("select count(*) from unit where organization_id = :org and active and id = :id")
                    .param("org", who.organizationId()).param("id", c.unitId()).query(Integer.class).single();
            if (unit == 0) throw new ApiValidationException("unitId", "escolha uma unidade ativa da organização");
            int offered = jdbc.sql("select count(*) from organization_modality o join unit_modality um on um.modality_id = o.modality_id "
                    + "and um.unit_id = :unit and um.offered where o.organization_id = :org and o.modality_id = :m and o.active")
                    .param("org", who.organizationId()).param("unit", c.unitId()).param("m", c.modalityId()).query(Integer.class).single();
            if (offered == 0) throw new ApiValidationException("modalityId", "escolha uma modalidade ativa oferecida pela unidade");
            UUID id = UUID.randomUUID();
            boolean other = validOtherUnits(who, c.validOtherUnits());
            try {
                jdbc.sql("insert into plan (id, organization_id, unit_id, modality_id, name, name_key, value_cents, weekly_frequency, valid_other_units) "
                        + "values (:id, :org, :unit, :modality, :name, :key, :value, :freq, :other)")
                        .param("id", id).param("org", who.organizationId()).param("unit", c.unitId()).param("modality", c.modalityId())
                        .param("name", c.name()).param("key", Modalities.normalize(c.name())).param("value", c.valueCents())
                        .param("freq", c.weeklyFrequency()).param("other", other).update();
            } catch (DuplicateKeyException e) {
                throw nameTaken();
            }
            history(who, id, c.unitId(), c.valueCents());
            audit.record(new AuditEntry(actor(who), "plan.created", "plan", id.toString(), c.unitId(), null,
                    snapshot(c.modalityId().toString(), c.name(), c.valueCents(), c.weeklyFrequency(), other, "ACTIVE"), null, ip));
            return plans.get(who, id);
        });
    }

    public Plans.Row update(AccountPrincipal who, UUID id, Input input, String ip) {
        plans.requireAccess(who);
        Clean c = clean(input);
        return transactions.execute(() -> {
            Plans.Row before = plans.get(who, id); // permissão, alcance e 404
            lock(who, id);
            if (c.unitId() != null && !c.unitId().equals(before.unit().id())) throw new ApiValidationException("unitId", "a unidade não muda");
            if (c.modalityId() != null && !c.modalityId().equals(before.modality().id())) {
                throw new ApiValidationException("modalityId", "a modalidade não muda");
            }
            if (before.status().equals("INACTIVE")) throw new ApiStatusException(ErrorCode.CONFLICT);
            boolean other = validOtherUnits(who, c.validOtherUnits());
            try {
                jdbc.sql("update plan set name = :name, name_key = :key, value_cents = :value, weekly_frequency = :freq, valid_other_units = :other "
                        + "where id = :id and organization_id = :org")
                        .param("name", c.name()).param("key", Modalities.normalize(c.name())).param("value", c.valueCents())
                        .param("freq", c.weeklyFrequency()).param("other", other).param("id", id).param("org", who.organizationId()).update();
            } catch (DuplicateKeyException e) {
                throw nameTaken();
            }
            Map<String, Object> next = snapshot(before.modality().id().toString(), c.name(), c.valueCents(), c.weeklyFrequency(), other, "ACTIVE");
            if (c.valueCents() != before.valueCents()) {
                history(who, id, before.unit().id(), c.valueCents());
                var done = plans.applyRepricing(who, before.unit().id(), id, c.valueCents(), c.unpaidCharges().equals("ADJUST"), c.paidFutureDifference());
                next.put("unpaidCharges", c.unpaidCharges());
                next.put("paidFutureDifference", c.paidFutureDifference());
                next.put("adjustedCharges", done.adjusted().size());
                next.put("createdCharges", done.created().size());
                audit.record(new AuditEntry(actor(who), "plan.repriced", "plan", id.toString(), before.unit().id(), Map.of("valueCents", before.valueCents()),
                        Map.of("valueCents", c.valueCents(), "adjustedCharges", done.adjusted().stream().map(UUID::toString).toList(),
                                "createdCharges", done.created().stream().map(UUID::toString).toList()), null, ip));
            }
            audit.record(new AuditEntry(actor(who), "plan.updated", "plan", id.toString(), before.unit().id(),
                    snapshot(before.modality().id().toString(), before.name(), before.valueCents(), before.weeklyFrequency(), before.validOtherUnits(),
                            before.status()), next, null, ip));
            return plans.get(who, id);
        });
    }

    /** RN-PLA-06: repetir a chamada responde igual, sem mudar nem auditar. */
    public Plans.Row setActive(AccountPrincipal who, UUID id, boolean active, String ip) {
        plans.requireAccess(who);
        String target = active ? "ACTIVE" : "INACTIVE";
        return transactions.execute(() -> {
            Plans.Row before = plans.get(who, id);
            lock(who, id);
            if (before.status().equals(target)) return before;
            try {
                jdbc.sql("update plan set status = :status where id = :id and organization_id = :org")
                        .param("status", target).param("id", id).param("org", who.organizationId()).update();
            } catch (DuplicateKeyException e) {
                throw nameTaken(); // reativar com homônimo ativo
            }
            audit.record(new AuditEntry(actor(who), active ? "plan.activated" : "plan.deactivated", "plan", id.toString(), before.unit().id(),
                    Map.of("status", before.status()), Map.of("status", target), null, ip));
            return plans.get(who, id);
        });
    }

    /** RN-ORG-13: unidades ativas do alcance com as modalidades ativas oferecidas, e a configuração "Treinar em outra unidade". */
    public Options options(AccountPrincipal who) {
        plans.requireAccess(who);
        boolean admin = who.role() == Role.ADMINISTRATOR;
        Set<UUID> reach = who.units() == null ? Set.of() : who.units();
        return transactions.execute(() -> {
            String cross = crossUnitTraining(who);
            if (admin && reach.isEmpty()) return new Options(cross, List.of());
            var units = jdbc.sql("select id, name from unit where organization_id = :org and active" + (admin ? " and id in (:reach)" : "")
                    + " order by name, id").param("org", who.organizationId());
            if (admin) units = units.param("reach", reach);
            return new Options(cross, units.query((rs, n) -> new UnitOption(rs.getObject(1, UUID.class), rs.getString(2), null)).list().stream()
                    .map(u -> new UnitOption(u.id(), u.name(),
                            jdbc.sql("select m.id, m.name from modality m join organization_modality o on o.modality_id = m.id and o.organization_id = :org and o.active "
                                    + "join unit_modality um on um.modality_id = m.id and um.unit_id = :unit and um.offered order by m.name, m.id")
                                    .param("org", who.organizationId()).param("unit", u.id())
                                    .query((rs, n) -> new ModalityOption(rs.getObject(1, UUID.class), rs.getString(2))).list()))
                    .toList());
        });
    }

    /** RN-PLA-04: quantos alunos o novo valor atinge e desde quando (próxima cobrança gerada). */
    public ValueImpact impact(AccountPrincipal who, UUID id, String valueCents) {
        plans.requireAccess(who);
        try {
            cents(valueCents == null ? null : new BigDecimal(valueCents.strip()));
        } catch (NumberFormatException e) {
            throw new ApiValidationException("valueCents", "inteiro de 1 a 99999999 centavos");
        }
        return transactions.execute(() -> {
            var plan = plans.get(who, id);
            var counts = plans.chargeCounts(plan.unit().id(), id);
            return new ValueImpact(plans.studentsAffected(id), "NEXT_CHARGE", counts.unpaid(), counts.paidFuture());
        });
    }

    private void lock(AccountPrincipal who, UUID id) {
        jdbc.sql("select id from plan where id = :id and organization_id = :org for update").param("id", id).param("org", who.organizationId())
                .query(UUID.class).single();
    }

    /** D7: a linha começa hoje, no fuso da unidade. */
    private void history(AccountPrincipal who, UUID planId, UUID unitId, long cents) {
        ZoneId zone = ZoneId.of(jdbc.sql("select timezone from unit where id = :id and organization_id = :org")
                .param("id", unitId).param("org", who.organizationId()).query(String.class).single());
        jdbc.sql("insert into plan_value_history (organization_id, plan_id, value_cents, starts_on, created_by) values (:org, :plan, :value, :on, :by)")
                .param("org", who.organizationId()).param("plan", planId).param("value", cents)
                .param("on", LocalDate.ofInstant(clock.instant(), zone)).param("by", who.accountId()).update();
    }

    private static ApiStatusException nameTaken() {
        return new ApiStatusException(ErrorCode.CONFLICT, List.of(new ApiError.Field("name", "já existe um plano ativo com este nome na unidade e modalidade")));
    }

    private static Map<String, Object> snapshot(String modalityId, String name, long cents, Integer freq, boolean other, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("modalityId", modalityId);
        m.put("name", name);
        m.put("valueCents", cents);
        m.put("weeklyFrequency", freq);
        m.put("validOtherUnits", other);
        m.put("status", status);
        return m;
    }
}

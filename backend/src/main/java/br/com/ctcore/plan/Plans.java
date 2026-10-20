package br.com.ctcore.plan;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-PLA-01: resumo de planos e benefícios. Organização, perfil e alcance vêm da sessão: o Coordenador vê todas as unidades;
 * o Administrador, as dele. Sem controle de aulas feitas (RN-PLA-06).
 */
@Service
public class Plans {
    public record PlanCounts(int active, int inactive, int studentsUsing, Long minValueCents, Long maxValueCents) {}

    public record BenefitSummary(int active, int high) {}

    public record Summary(PlanCounts plans, BenefitSummary benefits) {}

    /** Alcance da consulta: unitIds nulo = todas as unidades da organização. */
    record Scope(UUID organizationId, Set<UUID> unitIds) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;

    public Plans(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
    }

    void requireAccess(AccountPrincipal who) {
        if (who.role() == null || permissions.effective(who).get(Permission.PLANS) == Access.NONE) {
            throw new AccessDeniedException("Permission denied");
        }
    }

    public Summary summary(AccountPrincipal who, UUID unitId) {
        requireAccess(who);
        Set<UUID> reach = null;
        if (who.role() == Role.ADMINISTRATOR) {
            Set<UUID> own = who.units() == null ? Set.of() : who.units();
            reach = unitId == null ? own : own.contains(unitId) ? Set.of(unitId) : Set.of();
        } else if (unitId != null) {
            reach = Set.of(unitId);
        }
        Scope scope = new Scope(who.organizationId(), reach);
        PlanCounts plans = scope.unitIds() != null && scope.unitIds().isEmpty() ? new PlanCounts(0, 0, 0, null, null) : counts(scope);
        return new Summary(plans, benefits(scope));
    }

    private PlanCounts counts(Scope scope) {
        Map<String, Object> params = new HashMap<>();
        params.put("org", scope.organizationId());
        String filter = "";
        if (scope.unitIds() != null) {
            filter = " and unit_id in (:units)";
            params.put("units", scope.unitIds());
        }
        String sql = "select count(*) filter (where status = 'ACTIVE'), count(*) filter (where status = 'INACTIVE'), "
                + "min(value_cents) filter (where status = 'ACTIVE'), max(value_cents) filter (where status = 'ACTIVE') "
                + "from plan where organization_id = :org" + filter;
        return transactions.execute(() -> jdbc.sql(sql).params(params).query((rs, n) -> new PlanCounts(rs.getInt(1), rs.getInt(2),
                studentsUsing(scope), (Long) rs.getObject(3), (Long) rs.getObject(4))).single());
    }

    /** Ponto único: a Sprint 07 (matrícula) troca por contagem real. */
    int studentsUsing(Scope scope) {
        return 0;
    }

    /** Ponto único: a Sprint 07 (benefícios) troca por contagem real. */
    BenefitSummary benefits(Scope scope) {
        return new BenefitSummary(0, 0);
    }
}

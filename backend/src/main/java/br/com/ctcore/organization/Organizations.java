package br.com.ctcore.organization;

import br.com.ctcore.isolation.OrganizationContext;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Internal provisioning only; operational callers must add audit before exposing writes. */
@Service
public class Organizations {
    private final JdbcClient jdbc;
    private final Clock clock;
    private final OrganizationTransactions transactions;

    public Organizations(@Lazy JdbcClient jdbc, Clock clock, @Lazy OrganizationTransactions transactions) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.transactions = transactions;
    }

    public record InitialOrganization(UUID id, String name, UUID unitId, String unitName, String shortName) {
        public InitialOrganization {
            Objects.requireNonNull(id, "organizationId is required");
            Objects.requireNonNull(unitId, "unitId is required");
            requireName(name);
            requireName(unitName);
            requireName(shortName);
        }
    }

    public record Defaults(int deactivationDays) {
        public Defaults {
            if (deactivationDays < 15 || deactivationDays > 90) {
                throw new IllegalArgumentException("deactivationDays must be between 15 and 90");
            }
        }
        public static Defaults standard() { return new Defaults(30); }
    }

    public void initialize(InitialOrganization organization, Defaults defaults) {
        Objects.requireNonNull(organization, "organization is required");
        Objects.requireNonNull(defaults, "defaults are required");
        transactions.provision(organization.id(), () -> {
            jdbc.sql("insert into organization (id, name, joined_at) values (:id, :name, :time)")
                    .param("id", organization.id()).param("name", organization.name())
                    .param("time", OffsetDateTime.now(clock)).update();
            jdbc.sql("insert into unit (id, organization_id, name, short_name) values (:id, :org, :name, :short)")
                    .param("id", organization.unitId()).param("org", organization.id())
                    .param("name", organization.unitName()).param("short", organization.shortName()).update();
            jdbc.sql("insert into organization_settings (organization_id, deactivation_days) values (:org, :days)")
                    .param("org", organization.id()).param("days", defaults.deactivationDays()).update();
            jdbc.sql("insert into unit_settings (organization_id, unit_id) values (:org, :unit)")
                    .param("org", organization.id()).param("unit", organization.unitId()).update();
            jdbc.sql("insert into organization_limits (organization_id) values (:org)")
                    .param("org", organization.id()).update();
            // RF-MOD-04: default age ranges (Infantil 4-12, Juvenil 13-17, Adulto 18+).
            jdbc.sql("insert into age_range (id, organization_id, name, name_key, min_age, max_age) "
                    + "select gen_random_uuid(), :org, d.name, d.name_key, d.min_age, d.max_age "
                    + "from (values ('Infantil', 'infantil', 4, 12), ('Juvenil', 'juvenil', 13, 17), ('Adulto', 'adulto', 18, null)) "
                    + "as d(name, name_key, min_age, max_age)").param("org", organization.id()).update();
            return null;
        });
    }

    public Optional<Map<String, Object>> settings(UUID organizationId) {
        if (!OrganizationContext.permits(organizationId)) return Optional.empty();
        return transactions.execute(() -> jdbc.sql("select * from organization_settings where organization_id = :org")
                .param("org", organizationId).query().listOfRows().stream().findFirst());
    }

    public Optional<Map<String, Object>> unitSettings(UUID organizationId, UUID unitId) {
        Objects.requireNonNull(unitId, "unitId is required");
        if (!OrganizationContext.permits(organizationId)) return Optional.empty();
        if (!OrganizationContext.permitsUnit(organizationId, unitId)) return Optional.empty();
        return transactions.execute(() -> jdbc.sql("select * from unit_settings where organization_id = :org and unit_id = :unit")
                .param("org", organizationId).param("unit", unitId).query().listOfRows().stream().findFirst());
    }

    public Optional<Map<String, Object>> limits(UUID organizationId) {
        if (!OrganizationContext.permits(organizationId)) return Optional.empty();
        return transactions.execute(() -> jdbc.sql("select * from organization_limits where organization_id = :org")
                .param("org", organizationId).query().listOfRows().stream().findFirst());
    }

    // An additional-unit writer must hold a lock on the organization in the same transaction.
    public static void requireUnitCapacity(int currentUnits, boolean supportAuthorized) {
        requireCapacity(currentUnits, 10, supportAuthorized);
    }

    public static void requireManagementUserCapacity(int currentUsers, boolean supportAuthorized) {
        requireCapacity(currentUsers, 200, supportAuthorized);
    }

    private static void requireCapacity(int count, int maximum, boolean supportAuthorized) {
        if (count < 0) throw new IllegalArgumentException("negative count");
        if (!supportAuthorized && count >= maximum) {
            throw new IllegalStateException("support authorization required");
        }
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
    }
}

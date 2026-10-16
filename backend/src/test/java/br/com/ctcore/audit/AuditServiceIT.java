package br.com.ctcore.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.db.PostgresIT;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.isolation.Scopes;
import br.com.ctcore.organization.Organizations;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.IllegalTransactionStateException;

@ExtendWith(OutputCaptureExtension.class)
class AuditServiceIT extends PostgresIT {
    @Autowired AuditService audit;
    @Autowired Organizations organizations;
    @Autowired OrganizationTransactions tx;
    @Autowired JdbcTemplate runtime;

    static final AuditActor ACCOUNT = new AuditActor(AuditActor.Type.ACCOUNT, UUID.randomUUID(), "ADMIN");

    Organizations.InitialOrganization create() {
        UUID id = UUID.randomUUID();
        var o = new Organizations.InitialOrganization(id, "Synthetic", UUID.randomUUID(), "Unit", id.toString());
        organizations.initialize(o, Organizations.Defaults.standard());
        return o;
    }

    AuditEntry entry(AuditActor actor, UUID unit, Map<String, Object> before, Map<String, Object> after) {
        return new AuditEntry(actor, "student.update", "student", "42", unit, before, after, "motivo", "10.0.0.1");
    }

    long count(UUID org) {
        return adminJdbc().queryForObject("select count(*) from audit_log where organization_id = ?", Long.class, org);
    }

    @Test
    @DisplayName("CA2, CA5, CA9: grava só os campos alterados, sem chaves sensíveis, e o log não traz valores")
    void writesDiffWithoutSecrets(CapturedOutput out) {
        var a = create();
        try (var s = Scopes.open(a.id(), a.unitId())) {
            tx.execute(() -> {
                audit.record(entry(ACCOUNT, a.unitId(),
                        Map.of("nome", "Ana", "faixa", "branca", "password", "SEGREDO-ANTES"),
                        Map.of("nome", "Ana", "faixa", "azul", "cardNumber", "4111111111111111")));
                return null;
            });
        }
        var row = adminJdbc().queryForMap("select * from audit_log where organization_id = ?", a.id());
        assertThat(row.get("before_data").toString()).contains("branca").doesNotContain("nome", "SEGREDO-ANTES");
        assertThat(row.get("after_data").toString()).contains("azul").doesNotContain("4111");
        assertThat(row).containsEntry("actor_type", "ACCOUNT").containsEntry("action", "student.update")
                .containsEntry("entity_type", "student").containsEntry("entity_id", "42");
        assertThat(row.get("occurred_at")).isNotNull();
        assertThat(out.getOut() + out.getErr()).contains("audit action=student.update entity=student id=42")
                .doesNotContain("branca", "azul", "SEGREDO-ANTES", "4111");
    }

    @Test
    @DisplayName("CA3: criação grava só depois; exclusão só antes")
    void creationAndDeletion() {
        var a = create();
        try (var s = Scopes.open(a.id(), null)) {
            tx.execute(() -> {
                audit.record(entry(ACCOUNT, null, null, Map.of("nome", "Ana")));
                audit.record(entry(ACCOUNT, null, Map.of("nome", "Ana"), null));
                return null;
            });
        }
        assertThat(adminJdbc().queryForObject(
                "select count(*) from audit_log where organization_id = ? and ((before_data is null) <> (after_data is null))",
                Long.class, a.id())).isEqualTo(2);
    }

    @Test
    @DisplayName("CA4: rollback leva o registro; sem transação o serviço recusa")
    void joinsTransaction() {
        var a = create();
        try (var s = Scopes.open(a.id(), null)) {
            assertThatThrownBy(() -> tx.execute(() -> {
                audit.record(entry(ACCOUNT, null, null, Map.of("x", 1)));
                throw new IllegalStateException("boom");
            })).hasMessage("boom");
            assertThatThrownBy(() -> audit.record(entry(ACCOUNT, null, null, Map.of("x", 1))))
                    .isInstanceOf(IllegalTransactionStateException.class);
        }
        assertThat(count(a.id())).isZero();
    }

    @Test
    @DisplayName("CA6: unidade de outra organização ou conta sem organização são negadas")
    void unitAndScope() {
        var a = create(); var b = create();
        try (var s = Scopes.open(a.id(), null)) {
            assertThatThrownBy(() -> tx.execute(() -> {
                audit.record(entry(ACCOUNT, b.unitId(), null, Map.of("x", 1)));
                return null;
            })).isInstanceOf(AccessDeniedException.class);
        }
        assertThatThrownBy(() -> tx.execute(() -> {
            audit.record(entry(ACCOUNT, null, null, Map.of("x", 1)));
            return null;
        })).isInstanceOf(AccessDeniedException.class);
        assertThat(count(a.id()) + count(b.id())).isZero();
    }

    @Test
    @DisplayName("CA8: só a plataforma grava sem organização")
    void platformWithoutOrganization() {
        long before = adminJdbc().queryForObject("select count(*) from audit_log where organization_id is null", Long.class);
        tx.execute(() -> {
            audit.record(entry(new AuditActor(AuditActor.Type.PLATFORM_SUPPORT, null, null), null, null, Map.of("x", 1)));
            return null;
        });
        assertThat(adminJdbc().queryForObject("select count(*) from audit_log where organization_id is null", Long.class))
                .isEqualTo(before + 1);
        for (var type : AuditActor.Type.values()) {
            if (type == AuditActor.Type.PLATFORM_SUPPORT || type == AuditActor.Type.PLATFORM_OWNER) continue;
            assertThatThrownBy(() -> tx.execute(() -> {
                audit.record(entry(new AuditActor(type, null, null), null, null, Map.of("x", 1)));
                return null;
            })).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    @DisplayName("CA7: leitura isolada por organização e inserção cruzada recusada pelo banco")
    void rowLevelSecurity() {
        var a = create(); var b = create();
        try (var s = Scopes.open(a.id(), null)) {
            tx.execute(() -> { audit.record(entry(ACCOUNT, null, null, Map.of("x", 1))); return null; });
        }
        try (var s = Scopes.open(b.id(), null)) {
            tx.execute(() -> { audit.record(entry(ACCOUNT, null, null, Map.of("x", 2))); return null; });
            assertThat(tx.execute(() -> runtime.queryForObject("select count(*) from audit_log", Long.class))).isEqualTo(1L);
            assertThatThrownBy(() -> tx.execute(() -> runtime.update(
                    "insert into audit_log (organization_id, actor_type, action, entity_type, entity_id, occurred_at) "
                            + "values (?, 'SYSTEM', 'a.b', 'a', '1', now())", a.id())))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
        assertThat(runtime.queryForObject("select count(*) from audit_log", Long.class)).isZero();
    }

    @Test
    @DisplayName("CA1, RN-AUD-01: update, delete e truncate recusados para o runtime e para a migração")
    void appendOnly() {
        var a = create();
        try (var s = Scopes.open(a.id(), null)) {
            tx.execute(() -> { audit.record(entry(ACCOUNT, null, null, Map.of("x", 1))); return null; });
            for (String sql : new String[]{"update audit_log set action = 'x.y'", "delete from audit_log", "truncate audit_log"}) {
                assertThatThrownBy(() -> tx.execute(() -> runtime.update(sql))).isInstanceOf(org.springframework.dao.DataAccessException.class);
                assertThatThrownBy(() -> adminJdbc().update(sql)).rootCause().hasMessageContaining("append-only");
            }
        }
        assertThat(count(a.id())).isEqualTo(1);
    }
}

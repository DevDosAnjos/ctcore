package br.com.ctcore.isolation;

import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class OrganizationContextTest {
    @Test
    void snapshotsValidatesAndClearsClaims() throws Exception {
        UUID a = UUID.randomUUID(), unit = UUID.randomUUID();
        assertThat(OrganizationContext.organizationId()).isNull();
        assertThat(OrganizationContext.permits(a)).isFalse();
        assertThat(OrganizationContext.permitsUnit(a, unit)).isFalse();
        for (var invalid : new Scopes[]{new Scopes(null, null, Set.of()), new Scopes(a, null, null),
                new Scopes(a, unit, Set.of())}) {
            assertThatThrownBy(() -> OrganizationContext.open(invalid)).isInstanceOf(AccessDeniedException.class);
        }
        assertThatThrownBy(() -> OrganizationContext.open(null)).isInstanceOf(NullPointerException.class);
        try (var scope = Scopes.open(a, unit)) {
            assertThat(OrganizationContext.permits(a)).isTrue();
            assertThat(OrganizationContext.permitsUnit(a, unit)).isTrue();
            assertThat(OrganizationContext.permitsUnit(a, UUID.randomUUID())).isFalse();
            assertThatThrownBy(() -> Scopes.open(a, unit)).isInstanceOf(AccessDeniedException.class);
            try (var executor = Executors.newSingleThreadExecutor()) {
                assertThat(executor.submit(OrganizationContext::organizationId).get()).isNull();
            }
        }
        assertThat(OrganizationContext.organizationId()).isNull();
        assertThatThrownBy(() -> {
            try (var scope = Scopes.open(a, null)) { throw new IllegalStateException("probe"); }
        }).isInstanceOf(IllegalStateException.class);
        assertThat(OrganizationContext.organizationId()).isNull();
    }
}

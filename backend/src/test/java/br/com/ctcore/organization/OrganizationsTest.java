package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrganizationsTest {
    @Test
    @DisplayName("RN-ORG-05: inclusive operational ceilings, independent resources and explicit support exception")
    void capacity() {
        assertThatCode(() -> Organizations.requireUnitCapacity(9, false)).doesNotThrowAnyException();
        assertThatCode(() -> Organizations.requireManagementUserCapacity(199, false)).doesNotThrowAnyException();
        for (int count : new int[]{10, 11}) {
            assertThatThrownBy(() -> Organizations.requireUnitCapacity(count, false)).isInstanceOf(IllegalStateException.class);
            assertThatCode(() -> Organizations.requireUnitCapacity(count, true)).doesNotThrowAnyException();
        }
        for (int count : new int[]{200, 201}) {
            assertThatThrownBy(() -> Organizations.requireManagementUserCapacity(count, false)).isInstanceOf(IllegalStateException.class);
            assertThatCode(() -> Organizations.requireManagementUserCapacity(count, true)).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> Organizations.requireUnitCapacity(-1, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Organizations.requireManagementUserCapacity(-1, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesProvisioningBeforeWriting() {
        UUID id = UUID.randomUUID();
        for (String invalid : new String[]{null, "", " \t"}) {
            assertThatThrownBy(() -> new Organizations.InitialOrganization(id, invalid, id, "Unit", "unit"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Organizations.InitialOrganization(id, "Org", id, invalid, "unit"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Organizations.InitialOrganization(id, "Org", id, "Unit", invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new Organizations.InitialOrganization(null, "Org", id, "Unit", "unit"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Organizations.InitialOrganization(id, "Org", null, "Unit", "unit"))
                .isInstanceOf(NullPointerException.class);
        for (int invalid : new int[]{14, 91}) {
            assertThatThrownBy(() -> new Organizations.Defaults(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatCode(() -> new Organizations.Defaults(15)).doesNotThrowAnyException();
        assertThatCode(() -> new Organizations.Defaults(90)).doesNotThrowAnyException();
    }
}

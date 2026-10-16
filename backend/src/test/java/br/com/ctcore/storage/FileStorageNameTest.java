package br.com.ctcore.storage;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FileStorageNameTest {

    @Test
    void refusesNamesThatEscapeTheOrganizationFolder() {
        for (var bad : new String[]{null, "", " ", "a/b", "/a", "a\\b", "..", "a..b", "../x", "org/1/foto.png",
                "a\nb", "a\u0000b", "x".repeat(201)}) {
            assertThatThrownBy(() -> FileStorage.checkName(bad)).as("name %s", bad).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatCode(() -> FileStorage.checkName("foto.png")).doesNotThrowAnyException();
        assertThatCode(() -> FileStorage.checkName("x".repeat(200))).doesNotThrowAnyException();
    }

    @Test
    void acceptsOneByteUpToFiveMegabytesOnly() {
        assertThatThrownBy(() -> FileStorage.checkSize(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FileStorage.checkSize(new byte[0])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FileStorage.checkSize(new byte[5 * 1024 * 1024 + 1])).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> FileStorage.checkSize(new byte[1])).doesNotThrowAnyException();
        assertThatCode(() -> FileStorage.checkSize(new byte[5 * 1024 * 1024])).doesNotThrowAnyException();
    }
}

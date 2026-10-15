package br.com.ctcore.api.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.api.error.ApiValidationException;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PageRequestTest {

    private static final Set<String> ALLOWED = Set.of("name");

    @Test
    @DisplayName("CA5: padrões page=1 e size=25")
    void defaults() {
        PageRequest r = PageRequest.of(null, null, null, ALLOWED);
        assertThat(r.page()).isEqualTo(1);
        assertThat(r.size()).isEqualTo(25);
        assertThat(r.sort()).isNull();
        assertThat(PageRequest.of(3, 50, null, ALLOWED).offset()).isEqualTo(100);
    }

    @Test
    @DisplayName("CA8: sort=name,desc expõe campo e direção")
    void sort() {
        assertThat(PageRequest.of(1, 25, "name,desc", ALLOWED).sort()).isEqualTo(new PageRequest.Sort("name", true));
    }

    @Test
    @DisplayName("CA6/CA8: page, size e sort inválidos lançam erro com o campo")
    void invalid() {
        assertThatThrownBy(() -> PageRequest.of(0, 25, null, ALLOWED)).hasMessageStartingWith("page");
        assertThatThrownBy(() -> PageRequest.of(1, 10, null, ALLOWED)).hasMessageStartingWith("size");
        assertThatThrownBy(() -> PageRequest.of(1, 25, "password,asc", ALLOWED)).isInstanceOf(ApiValidationException.class);
        assertThatThrownBy(() -> PageRequest.of(1, 25, "name,up", ALLOWED)).hasMessageStartingWith("sort");
    }
}

package br.com.ctcore.api.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ctcore.api.error.ApiExceptionHandler;
import br.com.ctcore.api.error.SecurityErrorHandlers;
import br.com.ctcore.api.error.TraceIdFilter;
import br.com.ctcore.config.SecurityConfig;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = PageControllerTest.Probe.class)
@Import({SecurityConfig.class, SecurityErrorHandlers.class, ApiExceptionHandler.class, TraceIdFilter.class,
        PageControllerTest.Probe.class})
@TestPropertySource(properties = "ctcore.security.require-https=false")
@WithMockUser
public class PageControllerTest {

    /** Controlador de teste: lista fixa de 120 itens. Reusado pelo teste de contrato. */
    @RestController
    public static class Probe {
        public record Item(int n) {}

        @GetMapping("/probe-pages")
        PageResponse<Item> list(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
                @RequestParam(required = false) String sort) {
            PageRequest r = PageRequest.of(page, size, sort, Set.of("name"));
            List<Item> all = IntStream.rangeClosed(1, 120).mapToObj(Item::new).toList();
            int to = Math.min(all.size(), r.offset() + r.size());
            return PageResponse.of(r.offset() >= to ? List.of() : all.subList(r.offset(), to), r, all.size());
        }

        @GetMapping("/probe-pages/required")
        void required(@RequestParam String x) {}

        @GetMapping("/probe-pages/boom")
        void boom() {
            throw new IllegalStateException("boom");
        }

        @PostMapping("/probe-pages/json")
        void json(@RequestBody Item item) {}
    }

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("CA5: sem parâmetros devolve page=1, size=25 e total")
    void defaults() throws Exception {
        mvc.perform(get("/probe-pages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.total").value(120))
                .andExpect(jsonPath("$.items.length()").value(25));
    }

    @Test
    @DisplayName("CA5: size 50 e 100 aceitos; page=2 devolve o recorte certo")
    void sizesAndSlice() throws Exception {
        mvc.perform(get("/probe-pages?size=50")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(50));
        mvc.perform(get("/probe-pages?size=100")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(100));
        mvc.perform(get("/probe-pages?page=2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].n").value(26));
    }

    @Test
    @DisplayName("CA6: size=10, size=0, page=0, page=-1 e sort inválido devolvem 400 VALIDATION_ERROR com o campo")
    void invalidPaging() throws Exception {
        for (String[] c : new String[][] {{"size=10", "size"}, {"size=0", "size"}, {"page=0", "page"}, {"page=-1", "page"},
                {"sort=password,asc", "sort"}, {"sort=name,up", "sort"}}) {
            mvc.perform(get("/probe-pages?" + c[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.changed").value(false))
                    .andExpect(jsonPath("$.fields[0].field").value(c[1]));
        }
        mvc.perform(get("/probe-pages?sort=name,desc")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("CA9: size=abc e parâmetro ausente dão 400; text/plain dá 415; sem pilha")
    void parameterErrors() throws Exception {
        mvc.perform(get("/probe-pages?size=abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields[0].field").value("size"));
        mvc.perform(get("/probe-pages/required")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields[0].field").value("x"));
        String body = mvc.perform(post("/probe-pages/json").with(csrf()).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Exception", "at org.");
    }
}

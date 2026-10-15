package br.com.ctcore.api.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ctcore.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ApiExceptionHandlerTest.Probe.class)
@Import({SecurityConfig.class, SecurityErrorHandlers.class, ApiExceptionHandler.class, TraceIdFilter.class,
        ApiExceptionHandlerTest.Probe.class})
@TestPropertySource(properties = "ctcore.security.require-https=false")
class ApiExceptionHandlerTest {

    @RestController
    static class Probe {
        record Body(@NotBlank(message = "obrigatório") String name) {}

        @PostMapping("/probe-errors/validate")
        void validate(@Valid @RequestBody Body body) {}

        @GetMapping("/probe-errors/boom")
        void boom() {
            throw new IllegalStateException("select * from tabela_secreta");
        }

        @GetMapping("/probe-errors/conflict")
        void conflict() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint uk_x");
        }

        @GetMapping("/probe-errors/admin")
        @PreAuthorize("hasRole('ADMIN')")
        void admin() {}

        @GetMapping("/probe-errors/protected")
        void protectedRoute() {}
    }

    @Autowired MockMvc mvc;

    @Test
    @WithMockUser
    @DisplayName("CA6 / RNF-29: validação devolve 400 com campo, mensagem e traceId")
    void validation() throws Exception {
        mvc.perform(post("/probe-errors/validate").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.fields[0].field").value("name"))
                .andExpect(jsonPath("$.fields[0].message").value("obrigatório"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @WithMockUser
    @DisplayName("R6: corpo ilegível devolve 400 MALFORMED_REQUEST")
    void malformed() throws Exception {
        mvc.perform(post("/probe-errors/validate").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{nao e json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.fields").isEmpty());
    }

    @Test
    @WithMockUser
    @DisplayName("CA7 / RNF-29: erro inesperado devolve 500 genérico, sem pilha, classe ou SQL")
    void unexpected() throws Exception {
        String body = mvc.perform(get("/probe-errors/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.changed").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("tabela_secreta", "IllegalStateException", "at br.com", "select");
    }

    @Test
    @WithMockUser
    @DisplayName("CA8: violação de unicidade devolve 409 com changed=false")
    void conflict() throws Exception {
        String body = mvc.perform(get("/probe-errors/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.changed").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("uk_x", "duplicate key");
    }

    @Test
    @DisplayName("CA9: sem autenticação devolve 401 no formato único")
    void unauthenticated() throws Exception {
        mvc.perform(get("/probe-errors/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @WithMockUser
    @DisplayName("CA9: rota inexistente devolve 404 no formato único")
    void notFound() throws Exception {
        mvc.perform(get("/probe-errors/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser
    @DisplayName("CA9: método não permitido devolve 405 no formato único")
    void methodNotAllowed() throws Exception {
        mvc.perform(post("/probe-errors/protected").with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("CA10: 403 e 404 usam a mesma mensagem genérica e não revelam existência")
    void forbiddenLooksLikeNotFound() throws Exception {
        String forbidden = mvc.perform(get("/probe-errors/admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.changed").value(false))
                .andReturn().getResponse().getContentAsString();
        String notFound = mvc.perform(get("/probe-errors/nao-existe")).andReturn().getResponse().getContentAsString();
        assertThat(message(forbidden)).isEqualTo(message(notFound));
    }

    private static String message(String json) throws Exception {
        return new ObjectMapper().readTree(json).get("message").asText();
    }
}

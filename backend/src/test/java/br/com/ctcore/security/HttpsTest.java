package br.com.ctcore.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ctcore.api.error.ApiExceptionHandler;
import br.com.ctcore.api.error.SecurityErrorHandlers;
import br.com.ctcore.api.error.TraceIdFilter;
import br.com.ctcore.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(properties = "ctcore.security.require-https=true")
@Import({SecurityConfig.class, SecurityErrorHandlers.class, ApiExceptionHandler.class, TraceIdFilter.class})
class HttpsTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("CA11 / RNF-04: requisição http é redirecionada para https")
    void redirectsHttp() throws Exception {
        mvc.perform(get("/qualquer").header("X-Forwarded-Proto", "http").secure(false))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://localhost/qualquer"));
    }

    @Test
    @DisplayName("CA11 / RNF-04: resposta https traz Strict-Transport-Security")
    void hstsOnHttps() throws Exception {
        mvc.perform(get("/qualquer").header("X-Forwarded-Proto", "https").secure(true))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=")));
    }
}

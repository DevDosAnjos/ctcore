package br.com.ctcore.api.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * RNF-32: o contrato acompanha o servidor real. Toda rota dos controllers de br.com.ctcore fica sob o prefixo
 * de {@code servers} e existe em {@code paths} com o mesmo método; o cookie de sessão e o cabeçalho de CSRF
 * descritos no contrato são os que o servidor usa. Aplicação inteira, sem banco e sem as Probe dos testes.
 */
@SpringBootTest(properties = {
        "ctcore.scheduler.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"})
class ContractRoutesTest {

    private static final File FILE = new File("../docs/openapi.yaml");

    record Route(String method, String path) {}

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapping;

    @Autowired Environment env;

    private static JsonNode spec() throws Exception {
        return new YAMLMapper().readTree(FILE);
    }

    /** Rotas implementadas que faltam no contrato, com o motivo; vazio quando tudo bate. */
    static List<String> missing(Set<Route> implemented, JsonNode spec) {
        String base = spec.at("/servers/0/url").asText();
        List<String> out = new ArrayList<>();
        for (Route r : implemented) {
            if (!r.path().startsWith(base + "/")) {
                out.add(r.method() + " " + r.path() + ": fora de " + base);
                continue;
            }
            String rel = r.path().substring(base.length());
            if (spec.path("paths").path(rel).path(r.method().toLowerCase(Locale.ROOT)).isMissingNode()) {
                out.add(r.method() + " " + r.path() + ": falta " + rel + " no contrato");
            }
        }
        return out;
    }

    /** Pares (método, caminho) dos controllers da aplicação; mapeamento sem método vira ANY e é acusado. */
    private Set<Route> implemented() {
        Set<Route> routes = new HashSet<>();
        mapping.getHandlerMethods().forEach((info, handler) -> {
            if (!handler.getBeanType().getPackageName().startsWith("br.com.ctcore")) return;
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String path : info.getPatternValues()) {
                if (methods.isEmpty()) routes.add(new Route("ANY", path));
                methods.forEach(m -> routes.add(new Route(m.name(), path)));
            }
        });
        return routes;
    }

    @Test
    @DisplayName("RNF-32: toda rota da aplicação está sob /api/v1 e no contrato, com o mesmo método")
    void everyRouteInContract() throws Exception {
        assertThat(missing(implemented(), spec())).isEmpty();
    }

    @Test
    @DisplayName("RNF-32: a conferência acusa rota ausente, método diferente e caminho fora de /api/v1")
    void detectsMissingRoutes() throws Exception {
        JsonNode spec = new YAMLMapper().readTree("""
                servers: [{url: /api/v1}]
                paths:
                  /units/{id}: {get: {}}
                """);
        assertThat(missing(Set.of(new Route("GET", "/api/v1/units/{id}")), spec)).isEmpty();
        assertThat(missing(Set.of(new Route("DELETE", "/api/v1/units/{id}")), spec)).singleElement(STRING).contains("falta");
        assertThat(missing(Set.of(new Route("GET", "/api/v1/students")), spec)).singleElement(STRING).contains("falta");
        assertThat(missing(Set.of(new Route("GET", "/units/{id}")), spec)).singleElement(STRING).contains("fora de");
        assertThat(missing(Set.of(new Route("ANY", "/api/v1/units/{id}")), spec)).hasSize(1);
    }

    @Test
    @DisplayName("RNF-32 / RNF-07: o contrato descreve o cookie de sessão e o cabeçalho de CSRF que o servidor usa")
    void securitySchemesMatchServer() throws Exception {
        JsonNode schemes = spec().at("/components/securitySchemes");
        assertThat(schemes.at("/sessionCookie/name").asText()).isEqualTo(env.getProperty("server.servlet.session.cookie.name"));
        String csrfHeader = CookieCsrfTokenRepository.withHttpOnlyFalse().generateToken(new MockHttpServletRequest()).getHeaderName();
        assertThat(schemes.at("/csrfHeader/name").asText()).isEqualTo(csrfHeader);
    }
}

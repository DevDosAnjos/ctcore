package br.com.ctcore.api.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import br.com.ctcore.api.error.ApiExceptionHandler;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.api.error.SecurityErrorHandlers;
import br.com.ctcore.api.error.TraceIdFilter;
import br.com.ctcore.api.page.PageControllerTest.Probe;
import br.com.ctcore.config.SecurityConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = Probe.class)
@Import({SecurityConfig.class, SecurityErrorHandlers.class, ApiExceptionHandler.class, TraceIdFilter.class, Probe.class})
@TestPropertySource(properties = "ctcore.security.require-https=false")
@WithMockUser
class OpenApiContractTest {

    private static final File FILE = new File("../docs/openapi.yaml");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired MockMvc mvc;

    private static JsonNode spec() throws Exception {
        return new YAMLMapper().readTree(FILE);
    }

    /** Valida o JSON contra components.schemas.{name}; o documento inteiro vai junto para resolver os $ref. */
    private static List<String> errors(String name, String json) throws Exception {
        ObjectNode root = (ObjectNode) spec();
        root.putArray("allOf").addObject().put("$ref", "#/components/schemas/" + name);
        JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(root);
        return schema.validate(JSON.readTree(json)).stream().map(Object::toString).toList();
    }

    private String body(String url) throws Exception {
        return mvc.perform(get(url)).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("RNF-32 / CA1: openapi.yaml é OpenAPI válido com servidor /api/v1")
    void fileIsValid() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult r = new OpenAPIV3Parser().readLocation(FILE.getPath(), null, options);
        assertThat(r.getMessages()).isEmpty();
        assertThat(r.getOpenAPI().getServers().get(0).getUrl()).isEqualTo("/api/v1");
    }

    @Test
    @DisplayName("RNF-32 / CA2, CA4: convenções descritas, respostas de erro e Page sem snake_case")
    void conventions() throws Exception {
        JsonNode spec = spec();
        assertThat(spec.at("/info/description").asText()).contains("camelCase", "centavos", "date-time", "sort");
        JsonNode responses = spec.at("/components/responses");
        assertThat(responses.size()).isEqualTo(8);
        responses.forEach(r ->
                assertThat(r.at("/content/application~1json/schema/$ref").asText()).isEqualTo("#/components/schemas/Error"));
        spec.at("/components/schemas/Page/properties").fieldNames()
                .forEachRemaining(n -> assertThat(n).doesNotContain("_"));
        List<String> codes = new ArrayList<>();
        spec.at("/components/schemas/Error/properties/code/enum").forEach(c -> codes.add(c.asText()));
        assertThat(codes).containsExactlyInAnyOrder(
                Arrays.stream(ErrorCode.values()).map(Enum::name).toArray(String[]::new));
    }

    @Test
    @DisplayName("RNF-32 / CA3: erros reais 400, 404 e 500 conferem com Error; corpo incompleto é recusado")
    void errorsMatchSchema() throws Exception {
        for (String url : List.of("/probe-pages?size=10", "/probe-pages/nao-existe", "/probe-pages/boom")) {
            assertThat(errors("Error", body(url))).as(url).isEmpty();
        }
        String ok = body("/probe-pages/boom");
        assertThat(errors("Error", ok.replaceAll(",\"traceId\":\"[^\"]*\"", ""))).isNotEmpty();
        assertThat(errors("Error", ok.replace("INTERNAL_ERROR", "OUTRO"))).isNotEmpty();
    }

    @Test
    @DisplayName("RNF-32 / CA7: envelope de página confere com Page; sem total é recusado")
    void pageMatchesSchema() throws Exception {
        String page = body("/probe-pages?page=2");
        assertThat(errors("Page", page)).isEmpty();
        assertThat(errors("Page", page.replaceAll(",\"total\":[0-9]+", ""))).isNotEmpty();
    }
}

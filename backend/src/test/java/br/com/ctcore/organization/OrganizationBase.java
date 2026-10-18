package br.com.ctcore.organization;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import br.com.ctcore.db.PostgresIT;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** Banco e armazenamento reais (mesma imagem do docker-compose) para as rotas de /organization. */
@AutoConfigureMockMvc
abstract class OrganizationBase extends PostgresIT {
    static final GenericContainer<?> STORAGE = new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:3.80"))
            .withCommand("server", "-dir=/data", "-s3", "-s3.port=8333", "-s3.config=/etc/seaweedfs/s3.json", "-ip.bind=0.0.0.0")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("../infra/storage/s3.json")), "/etc/seaweedfs/s3.json")
            .withExposedPorts(8333)
            .waitingFor(Wait.forHttp("/status").forPort(8333));

    static {
        STORAGE.start();
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("ctcore.storage.endpoint", () -> "http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(8333));
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected Organizations organizations;

    record Tenant(UUID orgId, UUID unitId) {}

    protected Tenant tenant() {
        UUID id = UUID.randomUUID();
        var input = new Organizations.InitialOrganization(id, "Synthetic " + id, UUID.randomUUID(), "Unit", id.toString());
        organizations.initialize(input, Organizations.Defaults.standard());
        return new Tenant(input.id(), input.unitId());
    }

    protected AccountPrincipal who(Tenant t, Role role) {
        return new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), role, Set.of(t.unitId()));
    }

    protected MvcResult call(MockHttpServletRequestBuilder request, AccountPrincipal who) throws Exception {
        request.secure(true).with(csrf());
        if (who != null) {
            request.with(authentication(UsernamePasswordAuthenticationToken.authenticated(who, "synthetic",
                    AuthorityUtils.createAuthorityList("ROLE_" + (who.role() == null ? "NONE" : who.role().name())))));
        }
        return mvc.perform(request).andReturn();
    }

    protected static MockHttpServletRequestBuilder get() {
        return MockMvcRequestBuilders.get("/api/v1/organization");
    }

    protected static MockHttpServletRequestBuilder patch(String json) {
        return MockMvcRequestBuilders.patch("/api/v1/organization").contentType("application/json").content(json);
    }

    protected static MockHttpServletRequestBuilder putLogo(byte[] content, String name) {
        return MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/organization/logo")
                .file(new MockMultipartFile("file", name, "image/png", content));
    }

    protected static MockHttpServletRequestBuilder deleteLogo() {
        return MockMvcRequestBuilders.delete("/api/v1/organization/logo");
    }

    protected String row(Tenant t) {
        return adminJdbc().queryForList("select name, color, holder_document, logo_url from organization where id = ?", t.orgId()).toString();
    }
}

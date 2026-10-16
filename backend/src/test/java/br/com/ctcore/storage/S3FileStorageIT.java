package br.com.ctcore.storage;

import static org.assertj.core.api.Assertions.*;

import br.com.ctcore.isolation.Scopes;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** Armazenamento real compatível com S3 (mesma imagem do docker-compose). */
class S3FileStorageIT {

    static final GenericContainer<?> STORAGE = new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:3.80"))
            .withCommand("server", "-dir=/data", "-s3", "-s3.port=8333", "-s3.config=/etc/seaweedfs/s3.json", "-ip.bind=0.0.0.0")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("../infra/storage/s3.json")), "/etc/seaweedfs/s3.json")
            .withExposedPorts(8333)
            .waitingFor(Wait.forHttp("/status").forPort(8333));
    static S3FileStorage storage;
    static S3FileStorage shortLived;
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final UUID A = UUID.randomUUID(), B = UUID.randomUUID();

    @BeforeAll
    static void start() {
        STORAGE.start();
        var props = new StorageProperties();
        props.setEndpoint("http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(8333));
        storage = new S3FileStorage(props);
        storage.ensureBucket();
        storage.ensureBucket(); // idempotente
        var shortProps = new StorageProperties();
        shortProps.setEndpoint(props.getEndpoint());
        shortProps.setUrlExpiry(java.time.Duration.ofSeconds(1));
        shortLived = new S3FileStorage(shortProps);
    }

    static HttpResponse<String> get(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    @Test
    @DisplayName("RN-ORG-03: grava na pasta da organização e serve por URL temporária assinada")
    void storesUnderOrganizationFolderAndServesByTemporarySignedUrl() throws Exception {
        try (var ignored = Scopes.open(A, null)) {
            storage.put("foto.png", bytes("conteudo-a"), "image/png");
            var url = storage.readUrl("foto.png");
            assertThat(url).contains("/org/" + A + "/foto.png").contains("X-Amz-Signature=").contains("X-Amz-Expires=300");
            var ok = get(url);
            assertThat(ok.statusCode()).isEqualTo(200);
            assertThat(ok.body()).isEqualTo("conteudo-a");
        }
    }

    @Test
    @DisplayName("RN-ORG-03: recusa URL vencida ou com assinatura alterada")
    void refusesExpiredAndTamperedUrls() throws Exception {
        try (var ignored = Scopes.open(A, null)) {
            storage.put("termo.pdf", bytes("pdf"), "application/pdf");
            var url = shortLived.readUrl("termo.pdf");
            assertThat(get(url).statusCode()).isEqualTo(200);
            Thread.sleep(2500);
            assertThat(get(url).statusCode()).isEqualTo(403);

            var tampered = storage.readUrl("termo.pdf").replaceFirst("X-Amz-Signature=(.)", "X-Amz-Signature=" + "0".repeat(1) + "$1");
            assertThat(get(tampered).statusCode()).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("RN-ORG-03: outra organização nunca alcança o arquivo")
    void anotherOrganizationNeverReachesTheFile() throws Exception {
        try (var ignored = Scopes.open(A, null)) {
            storage.put("segredo.txt", bytes("a"), "text/plain");
        }
        try (var ignored = Scopes.open(B, null)) {
            assertThatThrownBy(() -> storage.readUrl("segredo.txt")).isInstanceOf(FileStorage.NotFound.class);
            assertThatThrownBy(() -> storage.delete("segredo.txt")).isInstanceOf(FileStorage.NotFound.class);
            for (var bad : new String[]{"../" + A + "/segredo.txt", "org/" + A + "/segredo.txt", "a/b", "a\\b", ".."}) {
                assertThatThrownBy(() -> storage.readUrl(bad)).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> storage.delete(bad)).isInstanceOf(IllegalArgumentException.class);
            }
        }
        try (var ignored = Scopes.open(A, null)) {
            assertThat(get(storage.readUrl("segredo.txt")).body()).isEqualTo("a");
            storage.delete("segredo.txt");
            assertThatThrownBy(() -> storage.readUrl("segredo.txt")).isInstanceOf(FileStorage.NotFound.class);
        }
    }

    @Test
    @DisplayName("RN-ORG-03: recusa arquivo vazio ou acima do limite sem criar objeto")
    void refusesEmptyAndOversizedFilesAndCreatesNothing() {
        try (var ignored = Scopes.open(A, null)) {
            assertThatThrownBy(() -> storage.put("vazio.bin", new byte[0], "application/octet-stream"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.put("grande.bin", new byte[FileStorage.MAX_BYTES + 1], "application/octet-stream"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.readUrl("vazio.bin")).isInstanceOf(FileStorage.NotFound.class);
            assertThatThrownBy(() -> storage.readUrl("grande.bin")).isInstanceOf(FileStorage.NotFound.class);
            storage.put("exato.bin", new byte[FileStorage.MAX_BYTES], "application/octet-stream");
            assertThat(storage.readUrl("exato.bin")).contains("exato.bin");
        }
    }

    @Test
    @DisplayName("RN-ORG-03: sem organização no contexto, toda operação é negada")
    void deniesEveryOperationWithoutOrganization() {
        assertThatThrownBy(() -> storage.put("x.png", bytes("x"), "image/png")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> storage.readUrl("x.png")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> storage.delete("x.png")).isInstanceOf(AccessDeniedException.class);
    }
}

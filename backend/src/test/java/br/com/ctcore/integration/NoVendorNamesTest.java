package br.com.ctcore.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** RNF-39: nenhum nome de fornecedor fora de integration/real. */
class NoVendorNamesTest {

    private static final List<String> VENDORS =
            List.of("mercadopago", "turnstile", "cloudflare", "plausible", "umami", "twilio", "sendgrid");

    @Test
    void noVendorNameOutsideRealPackage() throws Exception {
        List<String> hits;
        try (Stream<Path> files = Stream.of(Path.of("src/main"), Path.of("src/test")).flatMap(NoVendorNamesTest::walk)) {
            hits = files.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().replace('\\', '/').contains("br/com/ctcore/integration/real/"))
                    .filter(p -> !p.getFileName().toString().equals("NoVendorNamesTest.java"))
                    .filter(p -> {
                        try {
                            String s = Files.readString(p).toLowerCase();
                            return VENDORS.stream().anyMatch(s::contains);
                        } catch (Exception e) {
                            return false; // binary file
                        }
                    })
                    .map(Path::toString).toList();
        }
        assertThat(hits).isEmpty();
    }

    private static Stream<Path> walk(Path p) {
        try {
            return Files.walk(p);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

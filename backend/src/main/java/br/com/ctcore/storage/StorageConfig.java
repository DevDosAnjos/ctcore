package br.com.ctcore.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    public S3FileStorage fileStorage(StorageProperties props) { return new S3FileStorage(props); }

    /** Cria o bucket ao subir; armazenamento fora do ar não impede a aplicação de subir. */
    @Bean
    public ApplicationRunner ensureStorageBucket(S3FileStorage storage) {
        return args -> {
            try {
                storage.ensureBucket();
            } catch (RuntimeException e) {
                log.warn("Storage bucket not ready: {}", e.getClass().getSimpleName());
            }
        };
    }
}

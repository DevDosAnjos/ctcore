package br.com.ctcore.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Padrões iguais aos de infra/docker-compose.yml (valores fictícios de desenvolvimento, D5). */
@ConfigurationProperties("ctcore.storage")
public class StorageProperties {
    private String endpoint = "http://localhost:8333";
    private String bucket = "ctcore";
    private String region = "us-east-1";
    private String accessKey = "dev";
    private String secretKey = "dev-secret";
    private Duration urlExpiry = Duration.ofMinutes(5);

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public Duration getUrlExpiry() { return urlExpiry; }
    public void setUrlExpiry(Duration urlExpiry) { this.urlExpiry = urlExpiry; }
}

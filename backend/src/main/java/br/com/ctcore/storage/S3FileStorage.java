package br.com.ctcore.storage;

import br.com.ctcore.isolation.OrganizationContext;
import java.net.URI;
import org.springframework.security.access.AccessDeniedException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/** Adaptador S3 (D1): objetos em {@code org/<organizationId>/<nome>}, leitura só por URL SigV4 temporária. */
public class S3FileStorage implements FileStorage {

    private final StorageProperties props;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3FileStorage(StorageProperties props) {
        this.props = props;
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey()));
        var region = Region.of(props.getRegion());
        var pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        this.client = S3Client.builder().endpointOverride(URI.create(props.getEndpoint())).region(region)
                .credentialsProvider(credentials).serviceConfiguration(pathStyle)
                // Armazenamentos compatíveis nem sempre aceitam os checksums adicionais do SDK novo.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED).build();
        this.presigner = S3Presigner.builder().endpointOverride(URI.create(props.getEndpoint())).region(region)
                .credentialsProvider(credentials).serviceConfiguration(pathStyle).build();
    }

    /** Cria o bucket se não existir. */
    public void ensureBucket() {
        try {
            client.headBucket(b -> b.bucket(props.getBucket()));
        } catch (NoSuchBucketException e) {
            client.createBucket(b -> b.bucket(props.getBucket()));
        } catch (S3Exception e) {
            if (e.statusCode() != 404) throw e;
            client.createBucket(b -> b.bucket(props.getBucket()));
        }
    }

    private static String key(String name) {
        var organizationId = OrganizationContext.organizationId();
        if (organizationId == null) throw new AccessDeniedException("No organization in context");
        FileStorage.checkName(name);
        return "org/" + organizationId + "/" + name;
    }

    private void requireExists(String key, String name) {
        try {
            client.headObject(b -> b.bucket(props.getBucket()).key(key));
        } catch (NoSuchKeyException e) {
            throw new NotFound(name);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) throw new NotFound(name);
            throw e;
        }
    }

    @Override
    public void put(String name, byte[] content, String contentType) {
        var key = key(name);
        FileStorage.checkSize(content);
        client.putObject(b -> b.bucket(props.getBucket()).key(key).contentType(contentType), RequestBody.fromBytes(content));
    }

    @Override
    public String readUrl(String name) {
        var key = key(name);
        requireExists(key, name);
        var get = GetObjectRequest.builder().bucket(props.getBucket()).key(key).build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(props.getUrlExpiry()).getObjectRequest(get).build()).url().toString();
    }

    @Override
    public void delete(String name) {
        var key = key(name);
        requireExists(key, name);
        client.deleteObject(b -> b.bucket(props.getBucket()).key(key));
    }
}

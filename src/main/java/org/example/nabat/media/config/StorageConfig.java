package org.example.nabat.media.config;

import org.example.nabat.media.adapter.out.FileSystemStorageAdapter;
import org.example.nabat.media.adapter.out.S3StorageAdapter;
import org.example.nabat.media.application.port.out.FileStoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Chooses where alert photos live.
 *
 * <p>{@code nabat.storage.type} picks one of two implementations of the same port —
 * {@code filesystem} (the default, and the right answer for a single local process) or
 * {@code s3}. Nothing above {@link FileStoragePort} knows which is in play, which is the
 * point of having had the port there first: this change adds an implementation rather than
 * rewriting the module.
 *
 * <p>Exactly one is created. Both beans were previously a single {@code @Component}, so the
 * conditions live here instead — a stray second {@code FileStoragePort} would be an
 * ambiguous-dependency failure at startup, which is at least loud, but the choice belongs
 * in one readable place either way.
 */
@Configuration
public class StorageConfig {

    /**
     * The default. Correct for local development and a single instance; wrong the moment
     * there are two replicas without a shared volume, which is why {@code s3} exists.
     */
    @Bean
    @ConditionalOnProperty(name = "nabat.storage.type", havingValue = "filesystem", matchIfMissing = true)
    public FileStoragePort fileSystemStorage(
            @Value("${nabat.storage.upload-dir}") String uploadDir,
            @Value("${nabat.storage.serve-path}") String servePath,
            @Value("${nabat.storage.max-file-size-bytes:10485760}") long maxBytes
    ) {
        return new FileSystemStorageAdapter(uploadDir, servePath, maxBytes);
    }

    @Bean
    @ConditionalOnProperty(name = "nabat.storage.type", havingValue = "s3")
    public FileStoragePort s3Storage(
            S3Client s3Client,
            @Value("${nabat.storage.s3.bucket}") String bucket,
            @Value("${nabat.storage.serve-path}") String servePath,
            @Value("${nabat.storage.max-file-size-bytes:10485760}") long maxBytes
    ) {
        return new S3StorageAdapter(s3Client, bucket, servePath, maxBytes);
    }

    /**
     * Built only when S3 storage is selected, so a filesystem deployment neither needs
     * credentials nor opens a client it will not use.
     *
     * <p>{@code pathStyleAccessEnabled} is required for MinIO and for any endpoint reached
     * by host or IP: the SDK's default is virtual-host addressing
     * ({@code bucket.host/key}), which needs wildcard DNS that a local MinIO does not have.
     * Real S3 accepts path style too, so this is one setting for both rather than a
     * MinIO-only branch.
     *
     * <p>Credentials are static rather than from the default provider chain because the
     * target is as likely to be MinIO as AWS, and an in-cluster MinIO has no instance
     * metadata to fall back on. On AWS, set the same two values from a role's short-lived
     * credentials, or replace this bean.
     */
    @Bean
    @ConditionalOnProperty(name = "nabat.storage.type", havingValue = "s3")
    public S3Client s3Client(
            @Value("${nabat.storage.s3.endpoint}") String endpoint,
            @Value("${nabat.storage.s3.region:us-east-1}") String region,
            @Value("${nabat.storage.s3.access-key}") String accessKey,
            @Value("${nabat.storage.s3.secret-key}") String secretKey
    ) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }
}

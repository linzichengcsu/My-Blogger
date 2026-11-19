package csulzc.My_Personal_Blogger.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置。
 * <p>
 * 仅当 {@code file.type=minio} 时激活，构造全局单例 {@link MinioClient}。
 * MinioClient 实现了 {@link AutoCloseable}，其 HTTP 连接池由 Spring IoC 托管生命周期，
 * 禁止在业务方法里自行 try-with-resources 包裹（详见项目经验 Issue 2169250）。
 * </p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "file.type", havingValue = "minio")
public class MinioConfig {

    private final FileStorageProperties properties;

    @Bean
    public MinioClient minioClient() {
        String endpoint = properties.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("MinIO endpoint 未配置，请检查 file.endpoint");
        }
        if (properties.getAccessKey() == null || properties.getAccessKey().isBlank()) {
            throw new IllegalStateException("MinIO accessKey 未配置，请检查 file.access-key");
        }
        if (properties.getSecretKey() == null || properties.getSecretKey().isBlank()) {
            throw new IllegalStateException("MinIO secretKey 未配置，请检查 file.secret-key");
        }
        if (properties.getBucket() == null || properties.getBucket().isBlank()) {
            throw new IllegalStateException("MinIO bucket 未配置，请检查 file.bucket");
        }

        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();

        log.info("MinIO 客户端初始化完成：endpoint={}, bucket={}, ssl={}",
                endpoint, properties.getBucket(), properties.isUseSsl());
        return client;
    }
}

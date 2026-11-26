package csulzc.My_Personal_Blogger.service.infrastructure;

import csulzc.My_Personal_Blogger.config.FileStorageProperties;
import io.minio.*;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * MinIO（S3 兼容）对象存储实现。
 * <p>
 * 通过 {@code file.type=minio} 激活。与 {@link FileStorageService} 共享 {@link FileValidator}
 * 做统一的文件安全校验（大小 / MIME / 扩展名 / 魔数）。
 * </p>
 * <p>
 * 下载 URL 策略：保持返回 {@code /api/files/{storageKey}} 相对路径，
 * 由 {@link csulzc.My_Personal_Blogger.controller.FileController} 统一代理下载。
 * 这样前端零改动、Controller 统一做鉴权/日志/限流、未来切云 OSS/CDN 可平滑迁移。
 * </p>
 * <p>
 * 幂等性保证（契约测试 {@code StorageServiceContractTest}）：
 * MinIO 的 removeObject 对不存在的 key 静默成功，天然满足 deleteFile 幂等约束。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "file.type", havingValue = "minio")
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;
    private final FileValidator fileValidator;
    private final FileStorageProperties fileStorageProperties;

    private String bucket;

    @PostConstruct
    public void init() {
        this.bucket = fileStorageProperties.getBucket();

        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket '{}' 不存在，已自动创建", bucket);
            } else {
                log.info("MinIO bucket '{}' 已就绪", bucket);
            }
        } catch (Exception e) {
            throw new RuntimeException("MinIO bucket 初始化失败：" + e.getMessage(), e);
        }
    }

    @Override
    public String storeFile(MultipartFile file) {
        // 统一校验（FileValidator 与本地实现共享同一套安全规则）
        fileValidator.validateFull(file);

        try {
            String originalFileName = file.getOriginalFilename();
            if (originalFileName == null) {
                throw new IllegalArgumentException("文件名无效");
            }

            // 清洗 + 取扩展名 + UUID 重命名（与本地实现保持一致的 storageKey 格式）
            String sanitizedFileName = FileValidator.sanitizeFileName(originalFileName);
            String fileExtension = FileValidator.getFileExtension(sanitizedFileName);
            String storageKey = UUID.randomUUID().toString() + fileExtension;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(storageKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());

            log.debug("文件上传 MinIO：bucket={}, key={}, size={}",
                    bucket, storageKey, file.getSize());
            return storageKey;
        } catch (Exception ex) {
            log.error("文件上传 MinIO 失败", ex);
            throw new RuntimeException("文件上传失败：" + ex.getMessage(), ex);
        }
    }

    @Override
    public Resource loadFileAsResource(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(storageKey).build())) {

            // storageKey 本身不具备路径穿越语义（对象 key 是扁平的），
            // 但我们仍然做一次基本校验，拒绝 . 开头或 .. 的非法 key
            if (storageKey.contains("..") || storageKey.startsWith(".")) {
                throw new IllegalArgumentException("非法的文件 key");
            }

            byte[] data = inputStream.readAllBytes();
            return new ByteArrayResource(data) {
                @Override
                public String getFilename() {
                    return storageKey;
                }
            };
        } catch (Exception ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("NoSuchKey")) {
                throw new IllegalArgumentException("文件不存在：" + storageKey);
            }
            log.error("从 MinIO 下载文件失败：key={}", storageKey, ex);
            throw new RuntimeException("文件下载失败：" + ex.getMessage(), ex);
        }
    }

    @Override
    public void deleteFile(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(storageKey).build());
            // MinIO 的 removeObject 对不存在的 key 静默成功 → 天然幂等，满足契约
            log.debug("文件从 MinIO 删除：bucket={}, key={}", bucket, storageKey);
        } catch (Exception ex) {
            log.error("从 MinIO 删除文件失败：key={}", storageKey, ex);
            throw new RuntimeException("文件删除失败：" + ex.getMessage(), ex);
        }
    }

    @Override
    public String getFileUrl(String storageKey) {
        // 保持与本地实现一致的相对路径，由 Controller 统一代理下载
        return "/api/files/" + storageKey;
    }
}

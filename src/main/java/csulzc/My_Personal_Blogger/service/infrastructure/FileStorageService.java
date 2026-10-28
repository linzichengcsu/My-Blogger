package csulzc.My_Personal_Blogger.service.infrastructure;

import csulzc.My_Personal_Blogger.config.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 本地磁盘存储实现。
 * <p>
 * 通过 {@code file.type=local}（或未配置）激活，依赖 {@link FileValidator} 进行统一的安全校验。
 * 未来切换到 OSS / S3 / MinIO 时，只需新增一个 StorageService 实现类并修改配置即可。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "file.type", havingValue = "local", matchIfMissing = true)
public class FileStorageService implements StorageService {

    private final FileStorageProperties fileStorageProperties;
    private final FileValidator fileValidator;

    private Path fileStorageLocation;

    @PostConstruct
    public void init() {
        this.fileStorageLocation = Paths.get(fileStorageProperties.getUploadDir())
                .toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (IOException ex) {
            throw new RuntimeException("无法创建文件存储目录", ex);
        }
    }

    @Override
    public String storeFile(MultipartFile file) {
        // 统一校验（空/大小/MIME/扩展名/魔数）
        fileValidator.validateFull(file);

        try {
            String originalFileName = file.getOriginalFilename();
            if (originalFileName == null) {
                throw new IllegalArgumentException("文件名无效");
            }

            // 清洗 + 取扩展名 + UUID 重命名
            String sanitizedFileName = FileValidator.sanitizeFileName(originalFileName);
            String fileExtension = FileValidator.getFileExtension(sanitizedFileName);

            String newFileName = UUID.randomUUID().toString() + fileExtension;

            Path targetLocation = this.fileStorageLocation.resolve(newFileName).normalize();
            validatePathWithinStorage(targetLocation);

            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return newFileName;
        } catch (IOException ex) {
            log.error("文件上传失败", ex);
            throw new RuntimeException("文件上传失败", ex);
        }
    }

    @Override
    public Resource loadFileAsResource(String storageKey) {
        try {
            if (storageKey == null || storageKey.isBlank()) {
                throw new IllegalArgumentException("文件名不能为空");
            }

            Path filePath = this.fileStorageLocation.resolve(storageKey).normalize();
            validatePathWithinStorage(filePath);

            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new IllegalArgumentException("文件不存在");
            }
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("文件路径无效");
        }
    }

    @Override
    public void deleteFile(String storageKey) {
        try {
            if (storageKey == null || storageKey.isBlank()) {
                throw new IllegalArgumentException("文件名不能为空");
            }

            Path filePath = this.fileStorageLocation.resolve(storageKey).normalize();
            validatePathWithinStorage(filePath);

            Files.deleteIfExists(filePath);
        } catch (IOException ex) {
            log.error("删除文件失败", ex);
            throw new RuntimeException("删除文件失败");
        }
    }

    @Override
    public String getFileUrl(String storageKey) {
        return "/api/files/" + storageKey;
    }

    // ==================== 本地实现私有（存储后端相关，不进接口） ====================

    /**
     * 防止路径穿越攻击：确保目标路径始终在存储目录内。
     * <p>
     * OSS 实现通常不需要此校验（对象 key 本身不具备路径语义）。
     * </p>
     */
    private void validatePathWithinStorage(Path targetPath) {
        if (!targetPath.startsWith(this.fileStorageLocation)) {
            throw new IllegalArgumentException("非法的文件路径访问");
        }
    }
}

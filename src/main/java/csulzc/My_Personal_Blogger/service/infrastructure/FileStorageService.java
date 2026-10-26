package csulzc.My_Personal_Blogger.service.infrastructure;

import csulzc.My_Personal_Blogger.config.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final FileStorageProperties fileStorageProperties;

    private Path fileStorageLocation;

    private static final Map<String, byte[]> MAGIC_BYTES = Map.of(
            "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},
            "image/gif", new byte[]{0x47, 0x49, 0x46, 0x38},
            "image/webp", new byte[]{0x52, 0x49, 0x46, 0x46}
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp"
    );

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

    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        validateFile(file);

        try {
            String originalFileName = file.getOriginalFilename();
            if (originalFileName == null) {
                throw new IllegalArgumentException("文件名无效");
            }

            String sanitizedFileName = sanitizeFileName(originalFileName);
            String fileExtension = getFileExtension(sanitizedFileName);

            if (!ALLOWED_EXTENSIONS.contains(fileExtension)) {
                throw new IllegalArgumentException("不允许的文件扩展名: " + fileExtension);
            }

            validateFileMagicBytes(file, file.getContentType());

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

    public Resource loadFileAsResource(String fileName) {
        try {
            if (fileName == null || fileName.isBlank()) {
                throw new IllegalArgumentException("文件名不能为空");
            }

            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
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

    public void deleteFile(String fileName) {
        try {
            if (fileName == null || fileName.isBlank()) {
                throw new IllegalArgumentException("文件名不能为空");
            }

            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            validatePathWithinStorage(filePath);

            Files.deleteIfExists(filePath);
        } catch (IOException ex) {
            log.error("删除文件失败", ex);
            throw new RuntimeException("删除文件失败");
        }
    }

    public String getFileUrl(String fileName) {
        return "/api/files/" + fileName;
    }

    private void validateFile(MultipartFile file) {
        long fileSize = file.getSize();
        long maxFileSize = fileStorageProperties.getMaxFileSize();

        if (fileSize > maxFileSize) {
            throw new IllegalArgumentException("文件大小超过限制，最大允许: " + (maxFileSize / 1024 / 1024) + "MB");
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            throw new IllegalArgumentException("无法识别文件类型");
        }

        String[] allowedTypes = fileStorageProperties.getAllowedFileTypes();
        if (allowedTypes == null || !Arrays.asList(allowedTypes).contains(contentType)) {
            throw new IllegalArgumentException("不支持的文件类型: " + contentType);
        }
    }

    private void validateFileMagicBytes(MultipartFile file, String contentType) {
        byte[] expectedMagic = MAGIC_BYTES.get(contentType);
        if (expectedMagic == null) {
            throw new IllegalArgumentException("无法验证文件内容类型");
        }

        try (InputStream is = file.getInputStream()) {
            byte[] headerBytes = is.readNBytes(expectedMagic.length);
            if (headerBytes.length < expectedMagic.length) {
                throw new IllegalArgumentException("文件内容过短，无法验证");
            }

            for (int i = 0; i < expectedMagic.length; i++) {
                if (headerBytes[i] != expectedMagic[i]) {
                    throw new IllegalArgumentException("文件内容与声明的类型不匹配");
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("文件验证失败");
        }
    }

    private String sanitizeFileName(String fileName) {
        String name = Paths.get(fileName).getFileName().toString();
        return name.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
    }

    private void validatePathWithinStorage(Path targetPath) {
        if (!targetPath.startsWith(this.fileStorageLocation)) {
            throw new IllegalArgumentException("非法的文件路径访问");
        }
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex == -1) {
            return "";
        }
        return fileName.substring(lastDotIndex).toLowerCase();
    }

    public String encodeFileToBase64(String fileName) {
        try {
            if (fileName == null || fileName.isBlank()) {
                throw new IllegalArgumentException("文件名不能为空");
            }

            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            validatePathWithinStorage(filePath);

            byte[] fileContent = Files.readAllBytes(filePath);
            return Base64.getEncoder().encodeToString(fileContent);
        } catch (IOException ex) {
            log.error("文件转Base64失败", ex);
            throw new RuntimeException("文件转Base64失败");
        }
    }

    public String decodeBase64ToFile(String base64Data, String originalFileName) {
        try {
            if (base64Data == null || base64Data.isBlank()) {
                throw new IllegalArgumentException("Base64数据不能为空");
            }
            if (originalFileName == null || originalFileName.isBlank()) {
                throw new IllegalArgumentException("文件名不能为空");
            }

            String base64Content = base64Data;
            if (base64Data.contains(",")) {
                base64Content = base64Data.split(",", 2)[1];
            }

            byte[] decodedBytes = Base64.getDecoder().decode(base64Content);

            long maxFileSize = fileStorageProperties.getMaxFileSize();
            if (decodedBytes.length > maxFileSize) {
                throw new IllegalArgumentException("Base64解码后文件大小超过限制，最大允许: " + (maxFileSize / 1024 / 1024) + "MB");
            }

            String sanitizedFileName = sanitizeFileName(originalFileName);
            String fileExtension = getFileExtension(sanitizedFileName);

            if (!ALLOWED_EXTENSIONS.contains(fileExtension)) {
                fileExtension = getFileExtensionFromMimeType(base64Data);
            }

            if (!ALLOWED_EXTENSIONS.contains(fileExtension)) {
                throw new IllegalArgumentException("不支持的文件类型");
            }

            String newFileName = UUID.randomUUID().toString() + fileExtension;

            Path targetLocation = this.fileStorageLocation.resolve(newFileName).normalize();
            validatePathWithinStorage(targetLocation);

            Files.write(targetLocation, decodedBytes);

            return newFileName;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Base64转文件失败", ex);
            throw new RuntimeException("Base64转文件失败");
        }
    }

    private String getFileExtensionFromMimeType(String mimeType) {
        if (mimeType == null) return "";
        if (mimeType.contains("image/jpeg")) return ".jpg";
        if (mimeType.contains("image/png")) return ".png";
        if (mimeType.contains("image/gif")) return ".gif";
        if (mimeType.contains("image/webp")) return ".webp";
        return "";
    }
}

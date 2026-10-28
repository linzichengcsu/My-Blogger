package csulzc.My_Personal_Blogger.service.infrastructure;

import csulzc.My_Personal_Blogger.config.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * 文件安全校验工具类。
 * <p>
 * 从 {@link csulzc.My_Personal_Blogger.service.infrastructure.FileStorageService} 中抽离出
 * 与存储后端无关的校验逻辑（大小限制、白名单类型、魔数、文件名清洗、扩展名验证等），
 * 使所有 {@link StorageService} 实现都能复用同一套安全策略。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileValidator {

    private final FileStorageProperties properties;

    /** 不同 MIME 类型对应的文件头魔数，用于实际内容校验（防伪造扩展名） */
    private static final Map<String, byte[]> MAGIC_BYTES = Map.of(
            "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},
            "image/gif", new byte[]{0x47, 0x49, 0x46, 0x38},
            "image/webp", new byte[]{0x52, 0x49, 0x46, 0x46}
    );

    /** 允许的文件扩展名集合 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp"
    );

    // ==================== 复合校验（供入口方法一次性调用） ====================

    /**
     * 复合校验：空检查 + 大小 + MIME 类型 + 扩展名 + 魔数。
     * <p>
     * 失败即抛出，避免调用方遗漏任一环节。
     * </p>
     */
    public void validateFull(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        long fileSize = file.getSize();
        long maxSize = properties.getMaxFileSize();
        if (fileSize > maxSize) {
            throw new IllegalArgumentException("文件大小超过限制，最大允许: " + (maxSize / 1024 / 1024) + "MB");
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            throw new IllegalArgumentException("无法识别文件类型");
        }
        String[] allowedTypes = properties.getAllowedFileTypes();
        if (allowedTypes == null || !Arrays.asList(allowedTypes).contains(contentType)) {
            throw new IllegalArgumentException("不支持的文件类型: " + contentType);
        }

        // 扩展名校验
        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            throw new IllegalArgumentException("文件名无效");
        }
        String sanitizedName = sanitizeFileName(originalName);
        String ext = getFileExtension(sanitizedName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("不允许的文件扩展名: " + ext);
        }

        // 魔数校验（真实内容 vs 声明类型）
        validateMagicBytes(file, contentType);
    }

    // ==================== 分项校验（供 Base64 等场景独立调用） ====================

    /** 校验字节数组大小是否在上限内 */
    public void validateSize(long sizeBytes) {
        long maxSize = properties.getMaxFileSize();
        if (sizeBytes > maxSize) {
            throw new IllegalArgumentException("文件大小超过限制，最大允许: " + (maxSize / 1024 / 1024) + "MB");
        }
    }

    /** 校验扩展名是否在白名单内 */
    public void validateExtension(String fileName) {
        String ext = getFileExtension(fileName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("不支持的文件类型");
        }
    }

    /** 根据 MIME/URI 推断扩展名（Base64 data:URI 场景用） */
    public String getFileExtensionFromMimeType(String mimeType) {
        if (mimeType == null) return "";
        if (mimeType.contains("image/jpeg")) return ".jpg";
        if (mimeType.contains("image/png")) return ".png";
        if (mimeType.contains("image/gif")) return ".gif";
        if (mimeType.contains("image/webp")) return ".webp";
        return "";
    }

    // ==================== 静态工具（无需配置） ====================

    /** 清洗文件名，剥离路径段、非法字符 */
    public static String sanitizeFileName(String fileName) {
        String name = Paths.get(fileName).getFileName().toString();
        return name.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
    }

    /** 提取小写扩展名（含前导点，如 ".jpg"） */
    public static String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex == -1) {
            return "";
        }
        return fileName.substring(lastDotIndex).toLowerCase();
    }

    /** 判断扩展名是否在允许集合中 */
    public static boolean isAllowedExtension(String extension) {
        return ALLOWED_EXTENSIONS.contains(extension);
    }

    // ==================== 私有 ====================

    private void validateMagicBytes(MultipartFile file, String contentType) {
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
            log.error("魔数校验失败", ex);
            throw new RuntimeException("文件验证失败");
        }
    }
}

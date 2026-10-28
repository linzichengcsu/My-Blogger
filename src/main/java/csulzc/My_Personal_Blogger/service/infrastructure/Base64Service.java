package csulzc.My_Personal_Blogger.service.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;

/**
 * Base64 文件编解码服务。
 * <p>
 * 从原 {@link FileStorageService} 中独立出来，作为业务层能力存在：
 * <ul>
 *     <li>{@link #encodeFileToBase64} —— 将存储层已有文件读取出字节再转 Base64</li>
 *     <li>{@link #decodeBase64ToFile} —— 将 Base64 解码后委托 {@link StorageService} 完成实际存储</li>
 * </ul>
 * <p>
 * 这样存储层（StorageService）只关心「存 / 取 / 删 / URL」四类核心语义，
 * Base64 转码、数据 URI 前缀解析、MIME 推断等业务逻辑全部在此类中收口。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Base64Service {

    private final StorageService storageService;
    private final FileValidator fileValidator;

    // ==================== 对外 API ====================

    /**
     * 将存储层中的文件读取为 Base64 编码字符串。
     */
    public String encodeFileToBase64(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        try {
            Resource resource = storageService.loadFileAsResource(storageKey);
            byte[] fileContent = resource.getInputStream().readAllBytes();
            return Base64.getEncoder().encodeToString(fileContent);
        } catch (IOException ex) {
            log.error("文件转Base64失败", ex);
            throw new RuntimeException("文件转Base64失败");
        }
    }

    /**
     * 将 Base64 字符串解码后保存到存储层。
     * <p>支持纯 Base64 字符串或 data:URI 前缀（如 {@code data:image/jpeg;base64,...}）。</p>
     *
     * @return 存储后的文件 key
     */
    public String decodeBase64ToFile(String base64Data, String originalFileName) {
        if (base64Data == null || base64Data.isBlank()) {
            throw new IllegalArgumentException("Base64数据不能为空");
        }
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        // 剥离 data:URI 前缀
        String base64Content = base64Data;
        String mimeTypeHint = null;
        if (base64Data.contains(",")) {
            // data:image/png;base64,xxx → 取 "," 前部分推断 MIME
            String prefix = base64Data.split(",", 2)[0];
            mimeTypeHint = prefix.substring(5).split(";")[0]; // 去掉 "data:" 和 ";base64"
            base64Content = base64Data.split(",", 2)[1];
        }

        byte[] decodedBytes = Base64.getDecoder().decode(base64Content);

        // 大小校验（走 FileValidator，复用统一上限）
        fileValidator.validateSize(decodedBytes.length);

        // 扩展名校验：优先用原始文件名，不行再看 MIME 提示
        String sanitizedName = FileValidator.sanitizeFileName(originalFileName);
        String ext = FileValidator.getFileExtension(sanitizedName);

        if (!FileValidator.isAllowedExtension(ext)) {
            String mimeExt = fileValidator.getFileExtensionFromMimeType(mimeTypeHint);
            if (mimeExt != null && FileValidator.isAllowedExtension(mimeExt)) {
                ext = mimeExt;
            }
        }

        if (!FileValidator.isAllowedExtension(ext)) {
            throw new IllegalArgumentException("不支持的文件类型");
        }

        // 用 content type 推断（兜底）
        String contentType = inferContentType(ext);

        // 包装成 MultipartFile，走 StorageService.storeFile() 统一流程
        MultipartFile multipart = new ByteArrayMultipartFile(sanitizedName, contentType, decodedBytes);
        return storageService.storeFile(multipart);
    }

    // ==================== 私有工具 ====================

    private static String inferContentType(String extension) {
        if (extension == null) return "application/octet-stream";
        return switch (extension.toLowerCase()) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    /**
     * 将字节数组包装成 MultipartFile，使 Base64 解码结果能够复用 StorageService 的
     * 完整校验 + 存储流程。<p>
     * 这是一个极简适配器，仅实现 StorageService.storeFile() 所需的方法集合。
     * 不依赖 spring-test 的 MockMultipartFile，可在生产环境运行。
     */
    private static final class ByteArrayMultipartFile implements MultipartFile {

        private final String filename;
        private final String contentType;
        private final byte[] data;

        ByteArrayMultipartFile(String filename, String contentType, byte[] data) {
            this.filename = filename;
            this.contentType = contentType;
            this.data = data;
        }

        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return filename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return data == null || data.length == 0; }
        @Override public long getSize() { return data.length; }
        @Override public byte[] getBytes() { return data; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(data); }

        @Override
        public void transferTo(File dest) throws IOException {
            Files.copy(getInputStream(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public void transferTo(Path dest) throws IOException {
            Files.copy(getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

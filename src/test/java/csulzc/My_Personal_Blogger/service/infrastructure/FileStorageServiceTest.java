package csulzc.My_Personal_Blogger.service.infrastructure;

import csulzc.My_Personal_Blogger.config.FileStorageProperties;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.*;

/**
 * StorageService（本地磁盘实现）集成测试。
 * <p>
 * 直接走 Spring 完整上下文，验证 storeFile / loadFileAsResource / deleteFile / getFileUrl
 * 四个核心方法的完整链路（包括文件系统 I/O 和校验逻辑）。
 * </p>
 * <p>
 * Base64 相关能力已迁移至 {@link Base64Service}，对应测试见 {@link Base64ServiceTest}。
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("StorageService（本地磁盘） 集成测试")
public class FileStorageServiceTest {

    @Autowired
    private StorageService storageService;

    @Autowired
    private FileStorageProperties fileStorageProperties;

    private static String uploadedFileName;

    private static byte[] createFakeJpegContent() {
        byte[] content = new byte[128];
        content[0] = (byte) 0xFF;
        content[1] = (byte) 0xD8;
        content[2] = (byte) 0xFF;
        return content;
    }

    private static byte[] createFakePngContent() {
        byte[] content = new byte[128];
        content[0] = (byte) 0x89;
        content[1] = 0x50;
        content[2] = 0x4E;
        content[3] = 0x47;
        return content;
    }

    @Test
    @Order(1)
    @DisplayName("测试上传图片文件 - 成功")
    void testStoreImageFile_Success() throws Exception {
        byte[] content = createFakeJpegContent();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-image.jpg",
                "image/jpeg",
                content
        );

        String fileName = storageService.storeFile(file);
        uploadedFileName = fileName;

        assertThat(fileName).isNotNull();
        assertThat(fileName).endsWith(".jpg");

        Path filePath = Paths.get(fileStorageProperties.getUploadDir()).toAbsolutePath().normalize().resolve(fileName);
        assertThat(Files.exists(filePath)).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("测试加载文件资源 - 成功")
    void testLoadFileAsResource_Success() {
        assertThat(uploadedFileName).isNotNull();

        Resource resource = storageService.loadFileAsResource(uploadedFileName);

        assertThat(resource).isNotNull();
        assertThat(resource.exists()).isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("测试获取文件URL")
    void testGetFileUrl() {
        String url = storageService.getFileUrl(uploadedFileName);

        assertThat(url).isEqualTo("/api/files/" + uploadedFileName);
    }

    @Test
    @Order(4)
    @DisplayName("测试上传空文件 - 失败")
    void testStoreEmptyFile_Fail() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        assertThatThrownBy(() -> storageService.storeFile(emptyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件不能为空");
    }

    @Test
    @Order(5)
    @DisplayName("测试上传不支持的文件类型 - 失败")
    void testStoreUnsupportedFileType_Fail() {
        MockMultipartFile txtFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "test content".getBytes()
        );

        assertThatThrownBy(() -> storageService.storeFile(txtFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的文件类型");
    }

    @Test
    @Order(6)
    @DisplayName("测试删除文件 - 成功")
    void testDeleteFile_Success() {
        assertThat(uploadedFileName).isNotNull();

        storageService.deleteFile(uploadedFileName);

        Path filePath = Paths.get(fileStorageProperties.getUploadDir()).toAbsolutePath().normalize().resolve(uploadedFileName);
        assertThat(Files.exists(filePath)).isFalse();
    }

    @Test
    @Order(7)
    @DisplayName("测试加载不存在的文件 - 失败")
    void testLoadNonExistentFile_Fail() {
        assertThatThrownBy(() -> storageService.loadFileAsResource("non-existent.jpg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件不存在");
    }
}

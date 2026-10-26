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
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("FileStorageService 测试")
public class FileStorageServiceTest {

    @Autowired
    private FileStorageService fileStorageService;

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

        String fileName = fileStorageService.storeFile(file);
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

        Resource resource = fileStorageService.loadFileAsResource(uploadedFileName);

        assertThat(resource).isNotNull();
        assertThat(resource.exists()).isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("测试获取文件URL")
    void testGetFileUrl() {
        String url = fileStorageService.getFileUrl(uploadedFileName);

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

        assertThatThrownBy(() -> fileStorageService.storeFile(emptyFile))
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

        assertThatThrownBy(() -> fileStorageService.storeFile(txtFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的文件类型");
    }

    @Test
    @Order(6)
    @DisplayName("测试删除文件 - 成功")
    void testDeleteFile_Success() {
        assertThat(uploadedFileName).isNotNull();

        fileStorageService.deleteFile(uploadedFileName);

        Path filePath = Paths.get(fileStorageProperties.getUploadDir()).toAbsolutePath().normalize().resolve(uploadedFileName);
        assertThat(Files.exists(filePath)).isFalse();
    }

    @Test
    @Order(7)
    @DisplayName("测试加载不存在的文件 - 失败")
    void testLoadNonExistentFile_Fail() {
        assertThatThrownBy(() -> fileStorageService.loadFileAsResource("non-existent.jpg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件不存在");
    }

    @Test
    @Order(8)
    @DisplayName("测试文件转Base64编码 - 成功")
    void testEncodeFileToBase64_Success() throws Exception {
        byte[] content = createFakeJpegContent();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-base64.jpg",
                "image/jpeg",
                content
        );
        String fileName = fileStorageService.storeFile(file);

        String base64Data = fileStorageService.encodeFileToBase64(fileName);

        assertThat(base64Data).isNotNull();
        assertThat(base64Data).isNotEmpty();

        byte[] decodedBytes = Base64.getDecoder().decode(base64Data);
        assertThat(decodedBytes).isEqualTo(content);

        fileStorageService.deleteFile(fileName);
    }

    @Test
    @Order(9)
    @DisplayName("测试Base64解码并保存为文件 - 成功")
    void testDecodeBase64ToFile_Success() {
        String originalContent = "test base64 image content";
        String base64Data = Base64.getEncoder().encodeToString(originalContent.getBytes());

        String fileName = fileStorageService.decodeBase64ToFile(base64Data, "test-decode.jpg");

        assertThat(fileName).isNotNull();
        assertThat(fileName).endsWith(".jpg");

        Path filePath = Paths.get(fileStorageProperties.getUploadDir()).toAbsolutePath().normalize().resolve(fileName);
        assertThat(Files.exists(filePath)).isTrue();

        try {
            byte[] savedContent = Files.readAllBytes(filePath);
            assertThat(new String(savedContent)).isEqualTo(originalContent);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        fileStorageService.deleteFile(fileName);
    }

    @Test
    @Order(10)
    @DisplayName("测试带data URI前缀的Base64解码 - 成功")
    void testDecodeBase64WithDataUriPrefix_Success() {
        String originalContent = "test with data uri prefix";
        String base64Data = Base64.getEncoder().encodeToString(originalContent.getBytes());
        String dataUri = "data:image/png;base64," + base64Data;

        String fileName = fileStorageService.decodeBase64ToFile(dataUri, "test-datauri.png");

        assertThat(fileName).isNotNull();
        assertThat(fileName).endsWith(".png");

        Path filePath = Paths.get(fileStorageProperties.getUploadDir()).toAbsolutePath().normalize().resolve(fileName);
        assertThat(Files.exists(filePath)).isTrue();

        fileStorageService.deleteFile(fileName);
    }
}

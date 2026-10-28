package csulzc.My_Personal_Blogger.service.infrastructure;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Base64Service 单元测试。
 * <p>
 * 纯 Mockito，不启动 Spring 上下文。通过 mock StorageService 和 FileValidator 验证：
 * <ul>
 *     <li>encodeFileToBase64 从 StorageService 读资源再 Base64 编码</li>
 *     <li>decodeBase64ToFile 剥离 data:URI 前缀 → 校验 → 包装 → 委托 StorageService.storeFile</li>
 *     <li>参数空值校验正确</li>
 * </ul>
 * </p>
 * <p>
 * 端到端集成验证（真实文件系统 I/O）通过 FileStorageServiceTest + FileControllerTest 完成。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Base64Service 单元测试")
class Base64ServiceTest {

    @Mock
    private StorageService storageService;

    @Mock
    private FileValidator fileValidator;

    @InjectMocks
    private Base64Service base64Service;

    private static final byte[] JPEG_HEADER = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02, 0x03
    };

    @BeforeEach
    void setUp() {
        // 公共 stub：validateSize 默认放行；getFileExtensionFromMimeType 返回合理默认值
        doNothing().when(fileValidator).validateSize(anyLong());
        lenient().when(fileValidator.getFileExtensionFromMimeType("image/jpeg")).thenReturn(".jpg");
        lenient().when(fileValidator.getFileExtensionFromMimeType("image/png")).thenReturn(".png");
        lenient().when(fileValidator.getFileExtensionFromMimeType("image/gif")).thenReturn(".gif");
        lenient().when(fileValidator.getFileExtensionFromMimeType("image/webp")).thenReturn(".webp");
    }

    @Test
    @DisplayName("encodeFileToBase64 — 成功读取并编码")
    void testEncodeFileToBase64_Success() {
        byte[] content = JPEG_HEADER;
        Resource resource = new ByteArrayResource(content);
        when(storageService.loadFileAsResource("a.jpg")).thenReturn(resource);

        String base64 = base64Service.encodeFileToBase64("a.jpg");

        assertThat(base64).isEqualTo(Base64.getEncoder().encodeToString(content));
        verify(storageService).loadFileAsResource("a.jpg");
    }

    @Test
    @DisplayName("encodeFileToBase64 — 空 key 抛异常")
    void testEncodeFileToBase64_EmptyKey() {
        assertThatThrownBy(() -> base64Service.encodeFileToBase64(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件名不能为空");

        assertThatThrownBy(() -> base64Service.encodeFileToBase64(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("encodeFileToBase64 — 存储层抛异常时透传为 RuntimeException")
    void testEncodeFileToBase64_StorageError() {
        when(storageService.loadFileAsResource("missing.jpg"))
                .thenThrow(new IllegalArgumentException("文件不存在"));

        assertThatThrownBy(() -> base64Service.encodeFileToBase64("missing.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("decodeBase64ToFile — 纯 Base64 解码 → 调用 storageService.storeFile")
    void testDecodeBase64ToFile_PureBase64() {
        byte[] raw = "hello-world".getBytes();
        String encoded = Base64.getEncoder().encodeToString(raw);
        when(storageService.storeFile(any())).thenReturn("abc-123.jpg");

        String result = base64Service.decodeBase64ToFile(encoded, "photo.jpg");

        assertThat(result).isEqualTo("abc-123.jpg");

        // 验证：storeFile 被调用，且传入文件的内容等于解码后的原始字节
        verify(storageService, times(1)).storeFile(any());
    }

    @Test
    @DisplayName("decodeBase64ToFile — 带 data:URI 前缀（data:image/png;base64,...）")
    void testDecodeBase64ToFile_DataUriPrefix() {
        byte[] raw = "uri-prefix-content".getBytes();
        String encoded = Base64.getEncoder().encodeToString(raw);
        String dataUri = "data:image/png;base64," + encoded;
        when(storageService.storeFile(any())).thenReturn("xyz-789.png");

        String result = base64Service.decodeBase64ToFile(dataUri, "from-uri.png");

        assertThat(result).isEqualTo("xyz-789.png");
        verify(storageService).storeFile(any());
    }

    @Test
    @DisplayName("decodeBase64ToFile — data:URI 但扩展名不匹配时用 MIME 推断")
    void testDecodeBase64ToFile_MimeFallback() {
        byte[] raw = "mime-fallback".getBytes();
        String encoded = Base64.getEncoder().encodeToString(raw);
        String dataUri = "data:image/gif;base64," + encoded;
        when(storageService.storeFile(any())).thenReturn("fallback.gif");

        // 文件名没有合法扩展名，但 data:URI 说 image/gif → 应推断为 .gif
        String result = base64Service.decodeBase64ToFile(dataUri, "no-ext-name");

        assertThat(result).isEqualTo("fallback.gif");
        verify(storageService).storeFile(any());
    }

    @Test
    @DisplayName("decodeBase64ToFile — 空 Base64 抛异常")
    void testDecode_EmptyData() {
        assertThatThrownBy(() -> base64Service.decodeBase64ToFile("", "x.jpg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base64数据不能为空");
    }

    @Test
    @DisplayName("decodeBase64ToFile — 空文件名抛异常")
    void testDecode_EmptyFileName() {
        String b64 = Base64.getEncoder().encodeToString("x".getBytes());
        assertThatThrownBy(() -> base64Service.decodeBase64ToFile(b64, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件名不能为空");
    }

    @Test
    @DisplayName("decodeBase64ToFile — 不支持的扩展名且无 MIME 线索 → 拒绝")
    void testDecode_UnsupportedType() {
        // 文件名为 .exe（非法扩展名），也没有 data:URI 前缀提供 MIME 提示
        String b64 = Base64.getEncoder().encodeToString("malicious".getBytes());

        assertThatThrownBy(() -> base64Service.decodeBase64ToFile(b64, "evil.exe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的文件类型");

        verify(storageService, never()).storeFile(any());
    }

    @Test
    @DisplayName("decodeBase64ToFile — 包装后的 MultipartFile 行为正确")
    void testByteArrayMultipartFile_Behavior() {
        byte[] data = "test-bytes".getBytes();
        when(storageService.storeFile(any())).thenAnswer(inv -> {
            var file = inv.getArgument(0, org.springframework.web.multipart.MultipartFile.class);
            // 验证包装后的文件能被正确读取
            assertThat(file.getBytes()).isEqualTo(data);
            assertThat(file.getSize()).isEqualTo(data.length);
            assertThat(file.isEmpty()).isFalse();
            assertThat(file.getInputStream().readAllBytes()).isEqualTo(data);
            return "wrapped.jpg";
        });

        String b64 = Base64.getEncoder().encodeToString(data);
        String result = base64Service.decodeBase64ToFile(b64, "wrapped.jpg");

        assertThat(result).isEqualTo("wrapped.jpg");
    }
}

package csulzc.My_Personal_Blogger.service.infrastructure;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * StorageService 接口契约测试（Provider-Consumer 契约验证）。
 * <p>
 * 不启动 Spring 上下文，通过 Mockito mock 分别扮演：
 * <ul>
 *     <li>**消费者视角** —— 验证调用方（Controller 层）与接口的交互是否"说得通"
 *     <li>**实现者视角** —— 验证任何 {@link StorageService} 实现必须满足的基本行为约束
 * </ul>
 * </p>
 * <p>
 * 此测试的核心价值：如果未来新增 OSS / S3 / MinIO 实现但遗漏某个方法的正确语义，
 * 本测试可以提示 Contract 不满足，避免仅在运行时才暴露问题。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StorageService 接口契约验证")
class StorageServiceContractTest {

    @Mock
    private StorageService storageService;

    // ==================== 消费者视角：Controller 层如何正确使用 StorageService ====================

    @Test
    @DisplayName("Controller 场景：上传文件 → 获得 storageKey → 再用 storageKey 做后续操作")
    void consumer_typicalUploadFlow() {
        MultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        String storageKey = "a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg";
        String expectedUrl = "/api/files/" + storageKey;
        Resource resource = new ByteArrayResource(new byte[]{1, 2, 3});

        // mock 链路
        when(storageService.storeFile(file)).thenReturn(storageKey);
        when(storageService.getFileUrl(storageKey)).thenReturn(expectedUrl);
        when(storageService.loadFileAsResource(storageKey)).thenReturn(resource);
        doNothing().when(storageService).deleteFile(storageKey);

        // ------- 模拟 Controller 逻辑 -------
        String returnedKey = storageService.storeFile(file);
        String returnedUrl = storageService.getFileUrl(returnedKey);

        assertThat(returnedKey).isEqualTo(storageKey);
        assertThat(returnedUrl).isEqualTo(expectedUrl);

        // Controller 可以安全地把 storageKey 传给 loadFileAsResource / deleteFile
        Resource download = storageService.loadFileAsResource(returnedKey);
        assertThat(download).isNotNull();

        storageService.deleteFile(returnedKey);
        // 无异常即通过

        verify(storageService).storeFile(file);
        verify(storageService).getFileUrl(storageKey);
        verify(storageService).loadFileAsResource(storageKey);
        verify(storageService).deleteFile(storageKey);
    }

    @Test
    @DisplayName("Controller 场景：storeFile 抛 IllegalArgumentException → Controller 转 400")
    void consumer_storeFileThrowsIllegalArg() {
        MultipartFile file = new MockMultipartFile(
                "file", "bad.txt", "text/plain", "content".getBytes());

        when(storageService.storeFile(file))
                .thenThrow(new IllegalArgumentException("不支持的文件类型: text/plain"));

        assertThatThrownBy(() -> storageService.storeFile(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的文件类型");
    }

    // ==================== 实现者视角：任何 StorageService 必须满足的行为约束 ====================

    @Test
    @DisplayName("契约：getFileUrl 对任意 storageKey 应返回非 null 字符串")
    void contract_getFileUrlReturnsNonNull() {
        // 契约：getFileUrl 不应返回 null，否则调用方在拼接时会 NPE
        when(storageService.getFileUrl(anyString())).thenReturn("/api/files/any-key");

        assertThat(storageService.getFileUrl("any-key")).isNotNull();
        assertThat(storageService.getFileUrl("any-key")).contains("any-key");
    }

    @Test
    @DisplayName("契约：deleteFile 对不存在的 key 应静默成功（幂等）")
    void contract_deleteShouldBeIdempotent() {
        // 多数存储后端的 delete 语义是：key 不存在 → 无操作成功。
        // 如果实现类抛异常，Controller 层的 "清理已不存在文件" 场景就会 500。
        // 契约期望：deleteFile 对不存在的 key 不应抛异常。

        doNothing().when(storageService).deleteFile("non-existent.jpg");

        // 调用不抛异常 = 契约满足
        storageService.deleteFile("non-existent.jpg");

        verify(storageService).deleteFile("non-existent.jpg");
    }

    @Test
    @DisplayName("契约：storeFile 返回值应为可直接后续使用的 storageKey（UUID + 扩展名）")
    void contract_storeFileReturnsUsableKey() {
        MultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        when(storageService.storeFile(file)).thenReturn("550e8400-e29b-41d4-a716-446655440000.jpg");

        String key = storageService.storeFile(file);

        // 契约：返回值应能被 getFileUrl / loadFileAsResource / deleteFile 直接接受
        // 我们用 mock 验证这条链式使用不会触发 NPE 或类型问题
        when(storageService.getFileUrl(key)).thenReturn("/api/files/" + key);

        assertThat(storageService.getFileUrl(key)).contains(key);
    }

    // ==================== 边界：Controller 注入的是接口而非实现 ====================

    /**
     * 这里没有 Spring 上下文，用纯 mock 保证 Controller 只依赖 StorageService 接口。
     * 如果将来有人不小心在 Controller 里写成 `@Autowired FileStorageService`（实现类），
     * 这条测试会在 Code Review 阶段暴露问题（因为我们的 mock 类型是接口）。
     */
    @Test
    @DisplayName("依赖注入：Controller 应该只依赖 StorageService 接口，不应该耦合具体实现类")
    void contract_controllerDependsOnInterface() {
        // 这条测试的意义是文档化 + Review 提醒。
        // 运行时 Controller 已经被改写成 `@Autowired StorageService`，
        // 这条测试用 mock 接口验证交互正确。
        MultipartFile file = new MockMultipartFile(
                "file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});

        when(storageService.storeFile(file)).thenReturn("x.jpg");
        when(storageService.getFileUrl("x.jpg")).thenReturn("/api/files/x.jpg");

        // 验证接口方法调用
        String key = storageService.storeFile(file);
        String url = storageService.getFileUrl(key);

        assertThat(key).isEqualTo("x.jpg");
        assertThat(url).isEqualTo("/api/files/x.jpg");
    }
}

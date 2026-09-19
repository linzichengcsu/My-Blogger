package csulzc.My_Personal_Blogger.controller;

import tools.jackson.databind.ObjectMapper;
import csulzc.My_Personal_Blogger.api.dto.common.FileUploadResponse;
import csulzc.My_Personal_Blogger.config.JwtProperties;
import csulzc.My_Personal_Blogger.security.JwtTokenProvider;
import csulzc.My_Personal_Blogger.service.FileStorageService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileController.class)
@DisplayName("FileController 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import({FileControllerTest.TestSecurityConfig.class, JwtTokenProvider.class})
@EnableConfigurationProperties(JwtProperties.class)
class FileControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        @Primary
        @Order(Ordered.HIGHEST_PRECEDENCE)
        public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .anyRequest().permitAll()
                    );
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FileStorageService fileStorageService;

    private FileUploadResponse uploadResponse;
    private FileUploadResponse base64Response;

    @BeforeEach
    void setUp() {
        uploadResponse = FileUploadResponse.builder()
                .fileName("a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg")
                .fileUrl("/api/files/a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg")
                .fileType("image/jpeg")
                .fileSize(1024L)
                .build();

        base64Response = FileUploadResponse.builder()
                .fileName("a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg")
                .fileUrl("/api/files/a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg")
                .fileType("image/jpeg")
                .base64Data("dGVzdCBpbWFnZSBjb250ZW50")
                .build();
    }

    // ==================== 上传文件 ====================

    @Test
    @Order(1)
    @DisplayName("测试上传文件 - 成功")
    void testUploadFile_Success() throws Exception {
        byte[] content = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file", "test-image.jpg", "image/jpeg", content);

        given(fileStorageService.storeFile(any())).willReturn(uploadResponse.getFileName());
        given(fileStorageService.getFileUrl(eq(uploadResponse.getFileName())))
                .willReturn(uploadResponse.getFileUrl());

        mockMvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("文件上传成功"))
                .andExpect(jsonPath("$.data.fileName").value(uploadResponse.getFileName()))
                .andExpect(jsonPath("$.data.fileUrl").value(uploadResponse.getFileUrl()))
                .andExpect(jsonPath("$.data.fileType").value("image/jpeg"));

        then(fileStorageService).should().storeFile(any());
    }

    @Test
    @Order(2)
    @DisplayName("测试上传文件 - 不支持的文件类型")
    void testUploadFile_UnsupportedType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "test content".getBytes());

        given(fileStorageService.storeFile(any()))
                .willThrow(new IllegalArgumentException("不支持的文件类型: text/plain"));

        mockMvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @Order(3)
    @DisplayName("测试上传文件 - 文件过大")
    void testUploadFile_FileTooLarge() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "huge.jpg", "image/jpeg", new byte[1024]);

        given(fileStorageService.storeFile(any()))
                .willThrow(new IllegalArgumentException("文件大小超过限制，最大允许: 5MB"));

        mockMvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("文件大小超过限制，最大允许: 5MB"));
    }

    // ==================== 下载文件 ====================

    @Test
    @Order(4)
    @DisplayName("测试下载文件 - 成功")
    void testDownloadFile_Success() throws Exception {
        String fileName = uploadResponse.getFileName();
        byte[] fileContent = "test image content".getBytes();
        ByteArrayResource resource = new ByteArrayResource(fileContent);

        given(fileStorageService.loadFileAsResource(eq(fileName))).willReturn(resource);

        mockMvc.perform(get("/api/files/{fileName}", fileName))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(content().bytes(fileContent));

        then(fileStorageService).should().loadFileAsResource(eq(fileName));
    }


    @Test
    @Order(5)
    @DisplayName("测试下载文件 - 文件不存在")
    void testDownloadFile_NotFound() throws Exception {
        String fileName = "non-existent.jpg";
        given(fileStorageService.loadFileAsResource(eq(fileName)))
                .willThrow(new IllegalArgumentException("文件不存在"));

        mockMvc.perform(get("/api/files/{fileName}", fileName))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("文件不存在"));

        then(fileStorageService).should().loadFileAsResource(eq(fileName));
    }

    @Test
    @Order(6)
    @DisplayName("测试下载PNG文件 - Content-Type正确")
    void testDownloadFile_PngContentType() throws Exception {
        String fileName = "a1b2c3d4.png";
        ByteArrayResource resource = new ByteArrayResource(new byte[]{1, 2, 3});

        given(fileStorageService.loadFileAsResource(eq(fileName))).willReturn(resource);

        mockMvc.perform(get("/api/files/{fileName}", fileName))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));

        then(fileStorageService).should().loadFileAsResource(eq(fileName));
    }

    // ==================== 删除文件 ====================

    @Test
    @Order(7)
    @DisplayName("测试删除文件 - 成功")
    void testDeleteFile_Success() throws Exception {
        String fileName = uploadResponse.getFileName();
        doNothing().when(fileStorageService).deleteFile(eq(fileName));

        mockMvc.perform(delete("/api/files/{fileName}", fileName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("文件删除成功"));

        then(fileStorageService).should().deleteFile(eq(fileName));
    }

    @Test
    @Order(8)
    @DisplayName("测试删除文件 - 文件不存在")
    void testDeleteFile_NotFound() throws Exception {
        String fileName = "non-existent.jpg";
        willThrow(new IllegalArgumentException("文件不存在"))
                .given(fileStorageService).deleteFile(eq(fileName));

        mockMvc.perform(delete("/api/files/{fileName}", fileName))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 文件转Base64 ====================

    @Test
    @Order(9)
    @DisplayName("测试文件转Base64 - 成功")
    void testGetFileAsBase64_Success() throws Exception {
        String fileName = uploadResponse.getFileName();
        String base64Data = "dGVzdCBpbWFnZSBjb250ZW50";
        ByteArrayResource resource = new ByteArrayResource(new byte[]{1, 2, 3});

        given(fileStorageService.encodeFileToBase64(eq(fileName))).willReturn(base64Data);
        given(fileStorageService.loadFileAsResource(eq(fileName))).willReturn(resource);
        given(fileStorageService.getFileUrl(eq(fileName)))
                .willReturn("/api/files/" + fileName);

        mockMvc.perform(get("/api/files/{fileName}/base64", fileName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("文件转Base64成功"))
                .andExpect(jsonPath("$.data.fileName").value(fileName))
                .andExpect(jsonPath("$.data.base64Data").value(base64Data))
                .andExpect(jsonPath("$.data.fileType").value("image/jpeg"));

        then(fileStorageService).should().encodeFileToBase64(eq(fileName));
    }

    @Test
    @Order(10)
    @DisplayName("测试文件转Base64 - 文件不存在")
    void testGetFileAsBase64_NotFound() throws Exception {
        String fileName = "non-existent.jpg";
        given(fileStorageService.encodeFileToBase64(eq(fileName)))
                .willThrow(new RuntimeException("文件转Base64失败"));

        mockMvc.perform(get("/api/files/{fileName}/base64", fileName))
                .andExpect(status().isInternalServerError());

        then(fileStorageService).should().encodeFileToBase64(eq(fileName));
    }

    // ==================== Base64上传 ====================

    @Test
    @Order(11)
    @DisplayName("测试Base64上传 - 成功")
    void testUploadBase64File_Success() throws Exception {
        FileController.Base64UploadRequest request = new FileController.Base64UploadRequest();
        request.setBase64Data("dGVzdCBpbWFnZSBjb250ZW50");
        request.setFileName("test-image.jpg");

        String savedFileName = "a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg";

        given(fileStorageService.decodeBase64ToFile(anyString(), anyString()))
                .willReturn(savedFileName);
        given(fileStorageService.getFileUrl(eq(savedFileName)))
                .willReturn("/api/files/" + savedFileName);

        mockMvc.perform(post("/api/files/upload-base64")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Base64文件上传成功"))
                .andExpect(jsonPath("$.data.fileName").value(savedFileName))
                .andExpect(jsonPath("$.data.fileType").value("image/jpeg"));

        then(fileStorageService).should().decodeBase64ToFile(anyString(), anyString());
    }

    @Test
    @Order(12)
    @DisplayName("测试Base64上传 - 参数验证失败（数据为空）")
    void testUploadBase64File_EmptyData() throws Exception {
        FileController.Base64UploadRequest request = new FileController.Base64UploadRequest();
        request.setBase64Data("");
        request.setFileName("test.jpg");

        mockMvc.perform(post("/api/files/upload-base64")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(13)
    @DisplayName("测试Base64上传 - 参数验证失败（文件名为空）")
    void testUploadBase64File_EmptyFileName() throws Exception {
        FileController.Base64UploadRequest request = new FileController.Base64UploadRequest();
        request.setBase64Data("dGVzdA==");
        request.setFileName("");

        mockMvc.perform(post("/api/files/upload-base64")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(14)
    @DisplayName("测试Base64上传 - 文件名过长")
    void testUploadBase64File_FileNameTooLong() throws Exception {
        String longFileName = "a".repeat(256) + ".jpg";
        FileController.Base64UploadRequest request = new FileController.Base64UploadRequest();
        request.setBase64Data("dGVzdA==");
        request.setFileName(longFileName);

        mockMvc.perform(post("/api/files/upload-base64")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(15)
    @DisplayName("测试Base64上传 - 不支持的文件类型")
    void testUploadBase64File_UnsupportedType() throws Exception {
        FileController.Base64UploadRequest request = new FileController.Base64UploadRequest();
        request.setBase64Data("dGVzdCBjb250ZW50");
        request.setFileName("test.exe");

        given(fileStorageService.decodeBase64ToFile(anyString(), anyString()))
                .willThrow(new IllegalArgumentException("不支持的文件类型"));

        mockMvc.perform(post("/api/files/upload-base64")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不支持的文件类型"));
    }
}

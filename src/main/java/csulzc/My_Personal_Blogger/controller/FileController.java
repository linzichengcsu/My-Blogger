package csulzc.My_Personal_Blogger.controller;

import csulzc.My_Personal_Blogger.api.dto.common.FileUploadResponse;
import csulzc.My_Personal_Blogger.api.response.Result;
import csulzc.My_Personal_Blogger.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "文件管理", description = "文件上传、下载、删除及Base64转换接口")
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "上传文件", description = "上传图片文件到服务器（需要登录）")
    public ResponseEntity<Result<FileUploadResponse>> uploadFile(
            @Parameter(description = "要上传的文件") @RequestParam("file") MultipartFile file) {
        String fileName = fileStorageService.storeFile(file);

        FileUploadResponse response = FileUploadResponse.builder()
                .fileName(fileName)
                .fileUrl(fileStorageService.getFileUrl(fileName))
                .fileType(file.getContentType())
                .fileSize(file.getSize())
                .build();

        return ResponseEntity.ok(Result.success(response, "文件上传成功"));
    }

    @GetMapping("/{fileName}")
    @Operation(summary = "下载文件", description = "根据文件名下载或访问文件（GET 匿名可访问，供 <img> 直接展示头像/封面；文件名不可枚举）")
    public ResponseEntity<Resource> downloadFile(
            @Parameter(description = "文件名") @PathVariable String fileName) {
        Resource resource = fileStorageService.loadFileAsResource(fileName);

        String contentType = getContentTypeFromFileName(fileName);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{fileName}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "删除文件", description = "根据文件名删除文件（需要登录）")
    public ResponseEntity<Result<Void>> deleteFile(
            @Parameter(description = "要删除的文件名") @PathVariable String fileName) {
        fileStorageService.deleteFile(fileName);
        return ResponseEntity.ok(Result.success(null, "文件删除成功"));
    }

    @GetMapping("/{fileName}/base64")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "文件转Base64", description = "将指定文件转换为Base64编码字符串（需要登录）")
    public ResponseEntity<Result<FileUploadResponse>> getFileAsBase64(
            @Parameter(description = "要转换的文件名") @PathVariable String fileName) {
        String base64Data = fileStorageService.encodeFileToBase64(fileName);

        Resource resource = fileStorageService.loadFileAsResource(fileName);
        String contentType = getContentTypeFromFileName(fileName);

        FileUploadResponse response = FileUploadResponse.builder()
                .fileName(fileName)
                .fileUrl(fileStorageService.getFileUrl(fileName))
                .fileType(contentType)
                .base64Data(base64Data)
                .build();

        return ResponseEntity.ok(Result.success(response, "文件转Base64成功"));
    }

    @PostMapping("/upload-base64")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Base64文件上传", description = "接收Base64编码数据并保存为文件（需要登录）")
    public ResponseEntity<Result<FileUploadResponse>> uploadBase64File(
            @Valid @RequestBody Base64UploadRequest request) {
        String fileName = fileStorageService.decodeBase64ToFile(request.getBase64Data(), request.getFileName());

        String contentType = getContentTypeFromFileName(fileName);

        FileUploadResponse response = FileUploadResponse.builder()
                .fileName(fileName)
                .fileUrl(fileStorageService.getFileUrl(fileName))
                .fileType(contentType)
                .build();

        return ResponseEntity.ok(Result.success(response, "Base64文件上传成功"));
    }

    private String getContentTypeFromFileName(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "application/octet-stream";
        }

        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    @Data
    public static class Base64UploadRequest {
        @NotBlank(message = "Base64数据不能为空")
        private String base64Data;

        @NotBlank(message = "文件名不能为空")
        @Size(max = 255, message = "文件名长度不能超过255")
        private String fileName;
    }
}

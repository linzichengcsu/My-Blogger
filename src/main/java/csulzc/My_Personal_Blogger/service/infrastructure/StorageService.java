package csulzc.My_Personal_Blogger.service.infrastructure;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储抽象接口。
 * <p>
 * 仅定义存储层的核心语义（上传 / 下载 / 删除 / URL 生成），
 * 不绑定任何具体实现（本地磁盘、对象存储、分布式文件系统等）。
 * 安全校验、Base64 编解码等属于业务层职责，由调用方或辅助类处理。
 * </p>
 * <p>
 * 实现类通过 {@code file.type} 配置项注册，当前仅提供 local 实现，
 * 后续可扩展 oss / s3 / minio 等后端而无需改动上层业务代码。
 * </p>
 */
public interface StorageService {

    /**
     * 存储上传文件。
     *
     * @param file 原始上传文件，非 null 且非空
     * @return 存储后的文件 key（本地实现返回 UUID 文件名，OSS 实现返回对象 key）
     * @throws IllegalArgumentException 参数校验失败（大小超限、类型不匹配等）
     * @throws RuntimeException         底层 I/O 错误
     */
    String storeFile(MultipartFile file);

    /**
     * 根据存储 key 加载资源。
     *
     * @param storageKey 文件的存储 key
     * @return Spring Resource 包装，用于下载或流式读取
     * @throws IllegalArgumentException 文件不存在或路径非法
     */
    Resource loadFileAsResource(String storageKey);

    /**
     * 根据存储 key 删除文件。
     *
     * @param storageKey 文件的存储 key
     * @throws IllegalArgumentException 参数非法
     * @throws RuntimeException         底层 I/O 错误
     */
    void deleteFile(String storageKey);

    /**
     * 生成文件的对外访问 URL。
     *
     * @param storageKey 文件的存储 key
     * @return 可直接用于 HTTP GET 的 URL（本地实现返回相对路径，OSS 实现返回 CDN/对象存储地址）
     */
    String getFileUrl(String storageKey);
}

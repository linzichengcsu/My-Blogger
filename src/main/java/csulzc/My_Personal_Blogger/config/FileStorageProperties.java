package csulzc.My_Personal_Blogger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "file")
public class FileStorageProperties {

    /** 存储后端类型：local（本地磁盘，默认）/ oss / s3 / minio */
    private String type = "local";

    /** 本地磁盘存储目录（仅 type=local 时使用） */
    private String uploadDir = "uploads";

    private long maxFileSize = 5 * 1024 * 1024; // 5MB

    private String[] allowedFileTypes = {"image/jpeg", "image/png", "image/gif", "image/webp"};
}

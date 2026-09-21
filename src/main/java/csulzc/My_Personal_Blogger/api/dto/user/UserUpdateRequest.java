package csulzc.My_Personal_Blogger.api.dto.user;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    @Size(max = 50, message = "显示名称不能超过50个字符")
    private String displayName;

    @Size(max = 200, message = "个人简介不能超过200个字符")
    private String bio;

    private String avatar;
}
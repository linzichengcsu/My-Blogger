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
public class UserLoginRequest {

    @NotBlank(message = "用户名/邮箱不能为空")
    private String username;  // 可以是用户名或邮箱

    @NotBlank(message = "密码不能为空")
    private String password;
}

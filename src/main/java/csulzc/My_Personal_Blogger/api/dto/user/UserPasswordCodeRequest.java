package csulzc.My_Personal_Blogger.api.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 忘记密码（发送验证码）请求DTO：用户名 + 注册邮箱，验证匹配后向该邮箱发送验证码
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPasswordCodeRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}

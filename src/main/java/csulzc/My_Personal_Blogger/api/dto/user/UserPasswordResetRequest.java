package csulzc.My_Personal_Blogger.api.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 忘记密码（重置密码）请求DTO：用户名 + 注册邮箱验证身份
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPasswordResetRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 20, message = "新密码长度必须在8-20之间")
    private String newPassword;
}

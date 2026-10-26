package csulzc.My_Personal_Blogger.controller;

import csulzc.My_Personal_Blogger.api.dto.common.PageResponseDTO;
import csulzc.My_Personal_Blogger.api.dto.user.*;
import csulzc.My_Personal_Blogger.api.dto.user.LoginResponseDTO;
import csulzc.My_Personal_Blogger.api.response.Result;
import csulzc.My_Personal_Blogger.domain.entity.User;
import csulzc.My_Personal_Blogger.security.JwtTokenProvider;
import csulzc.My_Personal_Blogger.security.RequestSourceResolver;
import csulzc.My_Personal_Blogger.security.SecurityContextUtil;
import csulzc.My_Personal_Blogger.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "用户管理", description = "用户认证及信息管理")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private final RequestSourceResolver requestSourceResolver;

    private final SecurityContextUtil securityContextUtil;

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 用户注册或创建
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册或创建", description = "前端仅限普通用户，管理员须由Apifox创建")
    public ResponseEntity<Result<UserDetailDTO>> register(
            @Valid @RequestBody UserRegisterRequest request,
            HttpServletRequest httpRequest) {
        User.UserRole role = resolveRegisterRole(request, httpRequest);
        UserDetailDTO user = userService.register(request, role);
        return ResponseEntity.ok(Result.success(user, "注册成功"));
    }

    /**
     * 私有辅助方法：注册权限过滤
     */
    private User.UserRole resolveRegisterRole(UserRegisterRequest request, HttpServletRequest httpRequest) {
        // 前端请求：强制为 USER 权限，忽略请求体中的角色
        if (requestSourceResolver.isFromFrontend(httpRequest)) {
            return User.UserRole.USER;
        }
        // Apifox 等 API 客户端：允许 USER 或 ADMIN
        User.UserRole role = request.getRole() != null ? request.getRole() : User.UserRole.USER;
        if (role == User.UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("注册接口不允许创建超级管理员");
        }
        return role;
    }

    /**
     * 用户登录(返回Token)
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "返回Token；失败次数过多将触发账号/IP 维度限流（429）")
    public ResponseEntity<Result<LoginResponseDTO>> login(
            @Valid @RequestBody UserLoginRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = requestSourceResolver.resolveClientIp(httpRequest);
        LoginResponseDTO response = userService.loginWithToken(request, clientIp);
        return ResponseEntity.ok(Result.success(response, "登录成功"));
    }

    /**
     * 刷新Token
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新Token", description = "返回新的Token")
    public ResponseEntity<Result<LoginResponseDTO>> refreshToken(
            @RequestParam String refreshToken) {
        LoginResponseDTO response = userService.refreshToken(refreshToken);
        return ResponseEntity.ok(Result.success(response, "Token刷新成功"));
    }

    /**
     * 用户登出：将当前 access token 加入黑名单立即失效，可选提交 refreshToken 一并失效
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "将当前 Token 加入黑名单立即失效；可选提交 refreshToken 一并失效")
    public ResponseEntity<Result<Void>> logout(
            HttpServletRequest httpRequest,
            @RequestParam(required = false) String refreshToken) {
        String accessToken = jwtTokenProvider.extractTokenFromHeader(
                httpRequest.getHeader(jwtTokenProvider.getJwtProperties().getHeader()));
        userService.logout(accessToken, refreshToken);
        return ResponseEntity.ok(Result.success(null, "登出成功"));
    }

    /**
     * 忘记密码第一步：发送邮箱验证码（无需登录）
     */
    @PostMapping("/forgot-password/send-code")
    @Operation(summary = "忘记密码-发送验证码", description = "校验用户名与注册邮箱匹配后发送验证码，10 分钟有效；60 秒发送冷却")
    public ResponseEntity<Result<Void>> sendPasswordResetCode(
            @Valid @RequestBody UserPasswordCodeRequest request) {
        userService.sendPasswordResetCode(request.getUsername(), request.getEmail());
        return ResponseEntity.ok(Result.success(null, "验证码已发送，请查收邮件"));
    }

    /**
     * 忘记密码第二步：凭邮箱验证码重置密码（无需登录）
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "忘记密码-重置密码", description = "凭用户名 + 邮箱验证码重置密码，重置后所有旧登录态立即失效")
    public ResponseEntity<Result<Void>> forgotPassword(
            @Valid @RequestBody UserPasswordResetRequest request) {
        userService.resetPasswordWithCode(request.getUsername(), request.getCode(), request.getNewPassword());
        return ResponseEntity.ok(Result.success(null, "密码重置成功，请使用新密码登录"));
    }

    /**
     * 获取用户详情（通过ID）
     */
    @Operation(summary = "获取用户详情", description = "通过用户ID获取用户详情")
    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Result<UserDetailDTO>> getUserDetail(@PathVariable Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户ID无效");
        }
        UserDetailDTO user = userService.getUserDetail(userId);
        return ResponseEntity.ok(Result.success(user));
    }

    /**
     * 获取当前登录用户个人信息（仅凭Token，无需传入用户ID）
     */
    @Operation(summary = "获取当前登录用户个人信息", description = "仅凭Token，无需手动传入用户ID")
    @GetMapping("/me")
    public ResponseEntity<Result<UserDetailDTO>> getMyInfo() {
        Long currentUserId = securityContextUtil.getCurrentUserId();
        UserDetailDTO user = userService.getUserDetail(currentUserId);
        return ResponseEntity.ok(Result.success(user));
    }

    /**
     * 获取用户详情（通过用户名）
     * <p>公开接口仅返回公开资料（UserProfileDTO），不暴露 email/status/role/lastLoginAt，
     * 防止用户枚举与撞库情报收集。</p>
     */
    @GetMapping("/username/{username}")
    @Operation(summary = "获取用户公开资料", description = "通过用户名获取用户公开资料（不含邮箱等隐私字段）")
    public ResponseEntity<Result<UserProfileDTO>> getUserDetailByUsername(@PathVariable String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        UserProfileDTO user = userService.getUserProfileByUsername(username);
        return ResponseEntity.ok(Result.success(user));
    }

    /**
     * 获取用户公开资料
     */
    @Operation(summary = "获取用户公开资料", description = "通过用户ID获取用户公开资料")
    @GetMapping("/{userId}/profile")
    public ResponseEntity<Result<UserProfileDTO>> getUserProfile(@PathVariable Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户ID无效");
        }
        UserProfileDTO profile = userService.getUserProfile(userId);
        return ResponseEntity.ok(Result.success(profile));
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "更新用户信息", description = "通过用户ID更新用户信息")
    public ResponseEntity<Result<UserDetailDTO>> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UserUpdateRequest request) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户ID无效");
        }
        UserDetailDTO user = userService.updateUser(userId, request);
        return ResponseEntity.ok(Result.success(user, "更新成功"));
    }

    /**
     * 修改当前登录用户个人信息（仅凭Token，无需传入用户ID）
     */
    @PutMapping("/me")
    @Operation(summary = "修改当前登录用户个人信息", description = "仅凭Token，无需手动传入用户ID")
    public ResponseEntity<Result<UserDetailDTO>> updateMyInfo(
            @Valid @RequestBody UserUpdateRequest request) {
        Long currentUserId = securityContextUtil.getCurrentUserId();
        UserDetailDTO user = userService.updateUser(currentUserId, request);
        return ResponseEntity.ok(Result.success(user, "更新成功"));
    }

    /**
     * 修改密码
     */
    @Operation(summary = "修改密码", description = "通过用户ID修改密码")
    @PostMapping("/{userId}/change-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Result<Void>> changePassword(
            @PathVariable Long userId,
            @Valid @RequestBody UserPasswordChangeRequest request) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户ID无效");
        }
        userService.changePassword(userId, request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.ok(Result.success(null, "密码修改成功"));
    }

    @Operation(summary = "修改密码", description = "仅凭Token，无需手动传入用户ID")
    @PostMapping("/me/change-password")
    public ResponseEntity<Result<Void>> changeMyPassword(
            @Valid @RequestBody UserPasswordChangeRequest request) {
        Long currentUserId = securityContextUtil.getCurrentUserId();
        userService.changePassword(currentUserId, request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.ok(Result.success(null, "密码修改成功"));
    }

    /**
     * 获取用户活动统计
     */
    @GetMapping("/{userId}/activity")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取用户活动统计", description = "通过用户ID获取用户活动统计")
    public ResponseEntity<Result<UserActivityDTO>> getUserActivity(@PathVariable Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户ID无效");
        }
        UserActivityDTO activity = userService.getUserActivity(userId);
        return ResponseEntity.ok(Result.success(activity));
    }

    /**
     * 获取所有用户（分页）
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @param sortBy 排序字段
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取所有用户", description = "分页获取所有用户")
    public ResponseEntity<Result<PageResponseDTO<UserProfileDTO>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy) {
        if (page < 0) {
            throw new IllegalArgumentException("页码不能为负数");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("每页大小必须在1-100之间");
        }
        PageResponseDTO<UserProfileDTO> users = userService.getAllUsers(page, size, sortBy);
        return ResponseEntity.ok(Result.success(users));
    }

    /**
     * 搜索用户
     * @param keyword 关键词
     * @param page 页码（从0开始）
     * @param size 每页大小
     */
    @GetMapping("/search")
    @Operation(summary = "搜索用户", description = "通过关键词搜索用户")
    public ResponseEntity<Result<PageResponseDTO<UserProfileDTO>>> searchUsers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("搜索关键词不能为空");
        }
        if (page < 0) {
            throw new IllegalArgumentException("页码不能为负数");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("每页大小必须在1-100之间");
        }
        PageResponseDTO<UserProfileDTO> users = userService.searchUsers(keyword, page, size);
        return ResponseEntity.ok(Result.success(users));
    }

    /**
     * 启用用户（管理员功能）
     */
    @PostMapping("/{userId}/activate")
    @Operation(summary = "启用用户", description = "通过用户ID启用用户")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Result<Void>> activateUser(@PathVariable Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户ID无效");
        }
        userService.activateUser(userId);
        return ResponseEntity.ok(Result.success(null, "用户已启用"));
    }

    /**
     * 禁用用户（管理员功能）
     */
    @PostMapping("/{userId}/deactivate")
    @Operation(summary = "禁用用户", description = "通过用户ID禁用用户")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Result<Void>> deactivateUser(@PathVariable Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户ID无效");
        }
        userService.deactivateUser(userId);
        return ResponseEntity.ok(Result.success(null, "用户已禁用"));
    }

    /**
     * 锁定用户（管理员功能）
     */
    @PostMapping("/{userId}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "锁定用户", description = "通过用户ID锁定用户")
    public ResponseEntity<Result<Void>> lockUser(@PathVariable Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户ID无效");
        }
        userService.lockUser(userId);
        return ResponseEntity.ok(Result.success(null, "用户已锁定"));
    }

    /**
     * 解锁用户（管理员功能）
     */
    @PostMapping("/{userId}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "解锁用户", description = "通过用户ID解锁用户")
    public ResponseEntity<Result<Void>> unlockUser(@PathVariable Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户ID无效");
        }
        userService.unlockUser(userId);
        return ResponseEntity.ok(Result.success(null, "用户已解锁"));
    }

    /**
     * 删除用户（管理员功能）
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "删除用户", description = "通过用户ID删除用户")
    public ResponseEntity<Result<Void>> deleteUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "true") boolean softDelete) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户ID无效");
        }
        userService.deleteUser(userId, softDelete);
        return ResponseEntity.ok(Result.success(null, softDelete ? "用户已禁用" : "用户已删除"));
    }

    /**
     * 获取活跃用户数
     */
    @GetMapping("/stats/active")
    @Operation(summary = "获取活跃用户数", description = "获取当前活跃的用户数")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Result<Long>> countActiveUsers() {
        long count = userService.countActiveUsers();
        return ResponseEntity.ok(Result.success(count));
    }

    /**
     * 获取总用户数
     */
    @Operation(summary = "获取总用户数", description = "获取系统的总用户数")
    @GetMapping("/stats/total")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Result<Long>> getTotalUserCount() {
        long count = userService.getTotalUserCount();
        return ResponseEntity.ok(Result.success(count));
    }

    /**
     * 获取最近活跃用户列表
     */
    @Operation(summary = "获取最近活跃用户列表", description = "获取最近活跃的用户列表")
    @GetMapping("/stats/recently-active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Result<List<UserActivityDTO>>> getRecentlyActiveUsers(
            @RequestParam(defaultValue = "10") int limit) {
        if (limit <= 0 || limit > 50) {
            throw new IllegalArgumentException("限制数量必须在1-50之间");
        }
        List<UserActivityDTO> users = userService.getRecentlyActiveUsers(limit);
        return ResponseEntity.ok(Result.success(users));
    }
}

package csulzc.My_Personal_Blogger.security;

import csulzc.My_Personal_Blogger.domain.entity.User;
import csulzc.My_Personal_Blogger.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityContextUtil {

    private final UserRepository userRepository;

    /**
     * 获取当前登录用户ID
     */
    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("请先登录");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }

        throw new SecurityException("请先登录");
    }

    /**
     * 获取当前登录用户实体（带数据库查询）
     */
    public User getCurrentUser() {
        Long userId = getCurrentUserId();

        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));
    }

    /**
     * 获取当前登录用户实体并验证状态
     */
    public User getCurrentUserAndValidateStatus() {
        User user = getCurrentUser();

        if (user.getStatus() == User.UserStatus.INACTIVE) {
            throw new IllegalStateException("用户已被禁用，请联系管理员");
        }

        if (user.getStatus() == User.UserStatus.LOCKED) {
            throw new IllegalStateException("用户已被锁定，请联系管理员");
        }

        return user;
    }

    /**
     * 检查当前用户是否有管理员权限
     */
    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN") ||
                                authority.getAuthority().equals("ROLE_SUPER_ADMIN")
                );
    }

    /**
     * 验证是否为资源所有者或管理员
     */
    public void validateOwnershipOrAdmin(Long resourceOwnerId, String resourceName) {
        Long currentUserId = getCurrentUserId();

        if (!currentUserId.equals(resourceOwnerId) && !isAdmin()) {
            throw new SecurityException("无权限操作此" + resourceName);
        }
    }

    /**
     * 获取当前登录用户ID（未登录返回 null，不抛异常）
     */
    public Long getCurrentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        return principal instanceof Long ? (Long) principal : null;
    }

    /**
     * 是否有权查看指定作者的全部状态文章（作者本人或管理员）
     */
    public boolean canViewAllStatuses(Long authorId) {
        if (isAdmin()) {
            return true;
        }
        Long currentUserId = getCurrentUserIdOrNull();
        return currentUserId != null && currentUserId.equals(authorId);
    }
}

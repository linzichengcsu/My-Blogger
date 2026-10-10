package csulzc.My_Personal_Blogger.service;

import csulzc.My_Personal_Blogger.api.dto.common.PageResponseDTO;
import csulzc.My_Personal_Blogger.api.dto.user.*;
import csulzc.My_Personal_Blogger.domain.entity.User;
import csulzc.My_Personal_Blogger.repository.ArticleRepository;
import csulzc.My_Personal_Blogger.repository.CommentRepository;
import csulzc.My_Personal_Blogger.repository.UserRepository;
import csulzc.My_Personal_Blogger.security.JwtTokenProvider;
import csulzc.My_Personal_Blogger.security.PasswordValidator;
import csulzc.My_Personal_Blogger.security.SecurityContextUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final ArticleRepository articleRepository;
    private final CommentRepository commentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityContextUtil securityContextUtil;
    private final PasswordValidator passwordValidator;

    // ==================== 用户注册与登录 ====================

    /**
     * 用户注册
     */
    @Transactional
    public UserDetailDTO register(UserRegisterRequest request, User.UserRole role) {
        passwordValidator.validate(request.getPassword());

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("用户名已存在");
        }

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("邮箱已被注册");
        }

        User.UserRole registerRole = role != null ? role : User.UserRole.USER;
        if (registerRole == User.UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("注册接口不允许创建超级管理员");
        }


        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(StringUtils.hasText(request.getDisplayName())
                        ? request.getDisplayName()
                        : request.getUsername())
                .status(User.UserStatus.ACTIVE)
                .role(registerRole)
                .build();

        User savedUser = userRepository.save(user);
        return convertToDetailDTO(savedUser);
    }

    /**
     * 用户登录
     */
    @Transactional
    public LoginResponseDTO loginWithToken(UserLoginRequest request) {
        User user = findUserByLoginId(request.getUsername());

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("密码错误");
        }

        // 检查用户状态
        if (user.getStatus() == User.UserStatus.INACTIVE) {
            throw new IllegalStateException("用户已被禁用");
        }
        if (user.getStatus() == User.UserStatus.LOCKED) {
            throw new IllegalStateException("用户已被锁定");
        }

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        User updatedUser = userRepository.save(user);

        // 生成Token（包含角色信息）
        String role = updatedUser.getRole().name();
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), User.UserRole.valueOf(role), jwtTokenProvider.getJwtProperties().getExpiration());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getUsername(), User.UserRole.valueOf(role), jwtTokenProvider.getJwtProperties().getRefreshExpiration());

        UserDetailDTO userDetailDTO = convertToDetailDTO(updatedUser);

        return LoginResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getJwtProperties().getExpiration())
                .user(userDetailDTO)
                .build();
    }

    public LoginResponseDTO refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("无效的刷新令牌");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));

        // 生成新的Token（包含角色信息）
        String role = user.getRole().name();
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId, username, User.UserRole.valueOf(role), jwtTokenProvider.getJwtProperties().getExpiration());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId, username, User.UserRole.valueOf(role), jwtTokenProvider.getJwtProperties().getRefreshExpiration());

        UserDetailDTO userDetailDTO = convertToDetailDTO(user);

        return LoginResponseDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getJwtProperties().getExpiration())
                .user(userDetailDTO)
                .build();
    }


    /**
     * 根据登录标识查找用户（用户名或邮箱）
     */
    private User findUserByLoginId(String loginId) {
        Optional<User> user = userRepository.findByUsername(loginId);
        if (user.isEmpty()) {
            user = userRepository.findByEmail(loginId);
        }
        if (user.isEmpty()) {
            throw new EntityNotFoundException("用户不存在");
        }
        return user.get();
    }

    // ==================== 用户信息查询 ====================

    /**
     * 根据 ID 获取用户详情
     */
    public UserDetailDTO getUserDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));

        // 校验当前登录用户是否为本人或管理员
        securityContextUtil.validateOwnershipOrAdmin(userId, "用户信息");

        return convertToDetailDTO(user);
    }

    /**
     * 根据用户名获取用户详情
     */
    public UserDetailDTO getUserDetailByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));
        return convertToDetailDTO(user);
    }

    /**
     * 获取用户公开资料
     */
    public UserProfileDTO getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));
        return convertToProfileDTO(user);
    }

    /**
     * 获取用户公开资料（通过用户名）
     */
    public UserProfileDTO getUserProfileByUsername(String username) {
        User user = userRepository.findByUsernameWithArticles(username)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));
        return convertToProfileDTO(user);
    }

    // ==================== 用户信息更新 ====================

    /**
     * 更新用户信息
     */
    @Transactional
    public UserDetailDTO updateUser(Long userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));

        // 校验当前登录用户是否为本人或管理员
        securityContextUtil.validateOwnershipOrAdmin(userId, "用户信息");

        // 更新字段
        if (StringUtils.hasText(request.getDisplayName())) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }

        User updatedUser = userRepository.save(user);
        return convertToDetailDTO(updatedUser);
    }

    /**
     * 修改密码
     */
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        Long currentUserId = securityContextUtil.getCurrentUserId();

        if (!currentUserId.equals(userId) && !securityContextUtil.isAdmin()) {
            throw new SecurityException("无权限修改此用户密码");
        }

        passwordValidator.validate(newPassword);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("原密码错误");
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("新密码不能与原密码相同");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    /**
     * 重置密码（管理员功能）
     */
    public void resetPassword(Long userId, String newPassword) {
        passwordValidator.validate(newPassword);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }


    // ==================== 用户统计信息 ====================

    /**
     * 获取用户活动统计
     */
    public UserActivityDTO getUserActivity(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));

        long articleCount = articleRepository.countByAuthor(user);
        long commentCount = commentRepository.countByCommenter(user);

        return UserActivityDTO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .articleCount(articleCount)
                .commentCount(commentCount)
                .likeReceived(calculateTotalLikes(user))
                .lastActiveAt(user.getUpdatedAt())
                .build();
    }

    /**
     * 计算用户获得的总点赞数（SQL 聚合，避免加载该用户全部文章实体）
     */
    private long calculateTotalLikes(User user) {
        return articleRepository.sumLikeCountByAuthor(user);
    }

    /**
     * 统计用户文章数
     */
    public long countUserArticles(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));
        return articleRepository.countByAuthor(user);
    }

    /**
     * 统计用户评论数
     */
    public long countUserComments(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));
        return commentRepository.countByCommenter(user);
    }

    // ==================== 用户管理功能 ====================

    /**
     * 启用用户
     */
    @Transactional
    public void activateUser(Long userId) {
        if (!securityContextUtil.isAdmin()) {
            throw new SecurityException("只有管理员可以启用用户");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));
        user.setStatus(User.UserStatus.ACTIVE);
        userRepository.save(user);
    }

    /**
     * 禁用用户
     */
    @Transactional
    public void deactivateUser(Long userId) {
        if (!securityContextUtil.isAdmin()) {
            throw new SecurityException("只有管理员可以禁用用户");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));
        user.setStatus(User.UserStatus.INACTIVE);
        userRepository.save(user);
    }

    /**
     * 锁定用户
     */
    @Transactional
    public void lockUser(Long userId) {
        if (!securityContextUtil.isAdmin()) {
            throw new SecurityException("只有管理员可以锁定用户");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));
        user.setStatus(User.UserStatus.LOCKED);
        userRepository.save(user);
    }

    /**
     * 解锁用户
     */
    @Transactional
    public void unlockUser(Long userId) {
        if (!securityContextUtil.isAdmin()) {
            throw new SecurityException("只有管理员可以解锁用户");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));
        user.setStatus(User.UserStatus.ACTIVE);
        userRepository.save(user);
    }

    /**
     * 删除用户（软删除或硬删除）
     */
    @Transactional
    public void deleteUser(Long userId, boolean softDelete) {
        if (!securityContextUtil.isAdmin()) {
            throw new SecurityException("只有管理员可以删除用户");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));

        if (softDelete) {
            user.setStatus(User.UserStatus.INACTIVE);
            userRepository.save(user);
        } else {
            commentRepository.deleteByArticle(null);
            userRepository.delete(user);
        }
    }

    // ==================== 用户查询与搜索 ====================

    /**
     * 分页查询所有用户
     */
    public PageResponseDTO<UserProfileDTO> getAllUsers(int page, int size, String sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy).descending());
        Page<User> userPage = userRepository.findAll(pageable);

        Map<Long, Long> articleCounts = loadArticleCounts(userPage.getContent());

        List<UserProfileDTO> dtos = userPage.getContent().stream()
                .map(user -> convertToProfileDTO(user, articleCounts))
                .collect(Collectors.toList());

        return PageResponseDTO.<UserProfileDTO>builder()
                .content(dtos)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .first(userPage.isFirst())
                .last(userPage.isLast())
                .build();
    }

    /**
     * 根据状态查询用户（数据库过滤，避免全表加载后在内存过滤）
     */
    public List<UserDetailDTO> getUsersByStatus(User.UserStatus status) {
        List<User> users = userRepository.findByStatus(status);

        Map<Long, Long> articleCounts = loadArticleCounts(users);
        Map<Long, Long> commentCounts = loadCommentCounts(users);

        return users.stream()
                .map(user -> convertToDetailDTO(user, articleCounts, commentCounts))
                .collect(Collectors.toList());
    }

    /**
     * 搜索用户（根据用户名或显示名称，数据库模糊查询 + 数据库分页）
     */
    public PageResponseDTO<UserProfileDTO> searchUsers(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<User> userPage = userRepository.findByUsernameContainingOrDisplayNameContaining(keyword, keyword, pageable);

        Map<Long, Long> articleCounts = loadArticleCounts(userPage.getContent());

        List<UserProfileDTO> dtos = userPage.getContent().stream()
                .map(user -> convertToProfileDTO(user, articleCounts))
                .collect(Collectors.toList());

        return PageResponseDTO.<UserProfileDTO>builder()
                .content(dtos)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .first(userPage.isFirst())
                .last(userPage.isLast())
                .build();
    }

    // ==================== 数据统计功能 ====================

    /**
     * 统计活跃用户数
     */
    public long countActiveUsers() {
        return userRepository.countByStatus(User.UserStatus.ACTIVE);
    }

    /**
     * 统计禁用用户数
     */
    public long countInactiveUsers() {
        return userRepository.countByStatus(User.UserStatus.INACTIVE);
    }

    /**
     * 统计锁定用户数
     */
    public long countLockedUsers() {
        return userRepository.countByStatus(User.UserStatus.LOCKED);
    }

    /**
     * 获取总用户数
     */
    public long getTotalUserCount() {
        return userRepository.count();
    }

    /**
     * 获取最近登录的用户列表
     */
    public List<UserActivityDTO> getRecentlyActiveUsers(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by("lastLoginAt").descending());
        Page<User> userPage = userRepository.findAll(pageable);
        List<User> users = userPage.getContent();

        // 批量预加载文章数、评论数与获赞总数，避免逐个用户查询（N+1）
        Map<Long, Long> articleCounts = loadArticleCounts(users);
        Map<Long, Long> commentCounts = loadCommentCounts(users);
        Map<Long, Long> likeCounts = loadLikeCounts(users);

        return users.stream()
                .map(user -> UserActivityDTO.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .displayName(user.getDisplayName())
                        .articleCount(articleCounts.getOrDefault(user.getId(), 0L))
                        .commentCount(commentCounts.getOrDefault(user.getId(), 0L))
                        .likeReceived(likeCounts.getOrDefault(user.getId(), 0L))
                        .lastActiveAt(user.getLastLoginAt() != null ? user.getLastLoginAt() : user.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // ==================== 辅助方法 ====================

    /**
     * 转换为用户详情 DTO（单用户场景：固定 2 次 count 查询）
     */
    private UserDetailDTO convertToDetailDTO(User user) {
        return buildDetailDTO(user,
                articleRepository.countByAuthor(user),
                commentRepository.countByCommenter(user));
    }

    /**
     * 转换为用户详情 DTO（列表场景：文章数/评论数由批量查询预加载）
     */
    private UserDetailDTO convertToDetailDTO(User user, Map<Long, Long> articleCounts, Map<Long, Long> commentCounts) {
        return buildDetailDTO(user,
                articleCounts.getOrDefault(user.getId(), 0L),
                commentCounts.getOrDefault(user.getId(), 0L));
    }

    /**
     * 构建用户详情 DTO
     */
    private UserDetailDTO buildDetailDTO(User user, long articleCount, long commentCount) {
        return UserDetailDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .avatar(user.getAvatar())
                .bio(user.getBio())
                .status(user.getStatus())
                .role(user.getRole())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .articleCount(articleCount)
                .commentCount(commentCount)
                .favoriteCount(0L) // 可根据需求扩展
                .build();
    }

    /**
     * 转换为用户资料 DTO（单用户场景：固定 1 次 count 查询）
     */
    private UserProfileDTO convertToProfileDTO(User user) {
        return buildProfileDTO(user, articleRepository.countByAuthor(user));
    }

    /**
     * 转换为用户资料 DTO（列表场景：文章数由批量查询预加载）
     */
    private UserProfileDTO convertToProfileDTO(User user, Map<Long, Long> articleCounts) {
        return buildProfileDTO(user, articleCounts.getOrDefault(user.getId(), 0L));
    }

    /**
     * 构建用户资料 DTO
     */
    private UserProfileDTO buildProfileDTO(User user, long articleCount) {
        return UserProfileDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .avatar(user.getAvatar())
                .bio(user.getBio())
                .createdAt(user.getCreatedAt())
                .articleCount(articleCount)
                .followerCount(0L) // 可根据需求扩展
                .build();
    }

    /**
     * 批量查询文章数：userId -> count（一条 SQL 替代逐个 countByAuthor）
     */
    private Map<Long, Long> loadArticleCounts(List<User> users) {
        List<Long> userIds = users.stream().map(User::getId).toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return articleRepository.countByAuthorIds(userIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).longValue()));
    }

    /**
     * 批量查询评论数：userId -> count（一条 SQL 替代逐个 countByCommenter）
     */
    private Map<Long, Long> loadCommentCounts(List<User> users) {
        List<Long> userIds = users.stream().map(User::getId).toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return commentRepository.countByCommenterIds(userIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).longValue()));
    }

    /**
     * 批量查询获赞总数：userId -> sum(likeCount)（一条 SQL 替代逐个加载全部文章实体）
     */
    private Map<Long, Long> loadLikeCounts(List<User> users) {
        List<Long> userIds = users.stream().map(User::getId).toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return articleRepository.sumLikeCountByAuthorIds(userIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).longValue()));
    }

    /**
     * 检查用户是否存在
     */
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * 检查邮箱是否已注册
     */
    public boolean existsByEmail(String email) {
        return userRepository.findByEmail(email).isPresent();
    }
}
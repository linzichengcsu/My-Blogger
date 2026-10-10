package csulzc.My_Personal_Blogger.service;

import csulzc.My_Personal_Blogger.api.dto.dashboard.DashboardStatsDTO;
import csulzc.My_Personal_Blogger.domain.entity.Article;
import csulzc.My_Personal_Blogger.domain.entity.Category;
import csulzc.My_Personal_Blogger.domain.entity.User;
import csulzc.My_Personal_Blogger.repository.ArticleRepository;
import csulzc.My_Personal_Blogger.repository.CategoryRepository;
import csulzc.My_Personal_Blogger.repository.CommentRepository;
import csulzc.My_Personal_Blogger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final ArticleRepository articleRepository;
    private final CommentRepository commentRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public DashboardStatsDTO getDashboardStats() {
        return DashboardStatsDTO.builder()
                .userStats(getUserStats())
                .articleStats(getArticleStats())
                .commentStats(getCommentStats())
                .additionalMetrics(getAdditionalMetrics())
                .build();
    }

    private DashboardStatsDTO.UserStats getUserStats() {
        Long totalUsers = userRepository.count();

        Long activeUsers = userRepository.countByStatus(User.UserStatus.ACTIVE);

        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        Long newUsersToday = userRepository.countByCreatedAtAfter(startOfToday);

        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        Long newUsersThisMonth = userRepository.countByCreatedAtAfter(startOfMonth);

        return DashboardStatsDTO.UserStats.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .newUsersToday(newUsersToday)
                .newUsersThisMonth(newUsersThisMonth)
                .build();
    }

    private DashboardStatsDTO.ArticleStats getArticleStats() {
        Long totalArticles = articleRepository.count();

        Long publishedArticles = articleRepository.countByStatus(Article.ArticleStatus.RELEASE);

        Long draftArticles = articleRepository.countByStatus(Article.ArticleStatus.DRAFT);

        Long totalViews = articleRepository.sumViewCount();

        Long totalLikes = articleRepository.sumLikeCount();

        return DashboardStatsDTO.ArticleStats.builder()
                .totalArticles(totalArticles)
                .publishedArticles(publishedArticles)
                .draftArticles(draftArticles)
                .totalViews(totalViews)
                .totalLikes(totalLikes)
                .build();
    }

    private DashboardStatsDTO.CommentStats getCommentStats() {
        Long totalComments = commentRepository.count();

        Long pendingComments = commentRepository.countByIsApproved(false);

        Long approvedComments = commentRepository.countByIsApproved(true);

        return DashboardStatsDTO.CommentStats.builder()
                .totalComments(totalComments)
                .pendingComments(pendingComments)
                .approvedComments(approvedComments)
                .build();
    }

    private Map<String, Object> getAdditionalMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        metrics.put("topCategories", getCategoryStats());
        metrics.put("recentActivity", getRecentActivity());

        return metrics;
    }

    private Object getCategoryStats() {
        List<Object[]> categoryData = categoryRepository.findAllWithArticleCount();
        long totalArticles = articleRepository.count();

        List<Map<String, Object>> categoryStats = categoryData.stream()
                .map(obj -> {
                    Category category = (Category) obj[0];
                    Long articleCount = (Long) obj[1];
                    BigDecimal percentage = totalArticles > 0
                            ? BigDecimal.valueOf(articleCount * 100L)
                            .divide(BigDecimal.valueOf(totalArticles), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    Map<String, Object> stats = new LinkedHashMap<>();
                    stats.put("categoryId", category.getId());
                    stats.put("categoryName", category.getName());
                    stats.put("articleCount", articleCount);
                    stats.put("percentage", percentage);
                    return stats;
                })
                .sorted((a, b) -> Long.compare((Long) b.get("articleCount"), (Long) a.get("articleCount")))
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("totalCategories", categoryData.size());
        result.put("categoryDistribution", categoryStats);
        return result;
    }

    private Object getRecentActivity() {
        // 最近注册的 5 位用户
        List<Map<String, Object>> recentUsers = userRepository.findAll(
                        PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent()
                .stream()
                .map(user -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("userId", user.getId());
                    map.put("username", user.getUsername());
                    map.put("displayName", user.getDisplayName());
                    map.put("registeredAt", user.getCreatedAt());
                    return map;
                })
                .collect(Collectors.toList());

        // 最近的 5 篇文章（预加载作者，避免逐篇懒加载）
        List<Map<String, Object>> recentArticles = articleRepository.findAllWithAuthor(
                        PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent()
                .stream()
                .map(article -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("articleId", article.getId());
                    map.put("title", article.getTitle());
                    map.put("authorName", article.getAuthor() != null ? article.getAuthor().getUsername() : "未知");
                    map.put("publishedAt", article.getCreatedAt());
                    map.put("status", article.getStatus());
                    return map;
                })
                .collect(Collectors.toList());

        // 最近的 5 条评论（预加载评论者与文章，避免逐条懒加载）
        List<Map<String, Object>> recentComments = commentRepository.findAllWithDetails(
                        PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent()
                .stream()
                .map(comment -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("commentId", comment.getId());
                    map.put("content", comment.getContent() != null && comment.getContent().length() > 50
                            ? comment.getContent().substring(0, 50) + "..."
                            : comment.getContent());
                    map.put("commenterName", comment.getCommenter() != null ? comment.getCommenter().getUsername() : "未知");
                    map.put("articleTitle", comment.getArticle() != null ? comment.getArticle().getTitle() : "未知");
                    map.put("commentedAt", comment.getCreatedAt());
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("recentUsers", recentUsers);
        result.put("recentArticles", recentArticles);
        result.put("recentComments", recentComments);
        return result;
    }

    public boolean isAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return user.getRole() == User.UserRole.ADMIN
                || user.getRole() == User.UserRole.SUPER_ADMIN;
    }

    @Transactional(timeout = 30)
    public void promoteToAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (user.getRole() == User.UserRole.ADMIN || user.getRole() == User.UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("该用户已经是管理员");
        }

        user.setRole(User.UserRole.ADMIN);
        userRepository.save(user);
    }

    @Transactional(timeout = 30)
    public void demoteFromAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (user.getRole() == User.UserRole.USER) {
            throw new IllegalArgumentException("该用户不是管理员");
        }

        if (user.getRole() == User.UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("不能降级超级管理员");
        }

        user.setRole(User.UserRole.USER);
        userRepository.save(user);
    }
}

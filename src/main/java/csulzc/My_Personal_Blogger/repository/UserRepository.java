package csulzc.My_Personal_Blogger.repository;

import csulzc.My_Personal_Blogger.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends BaseRepository<User, Long> {

    // 1. 根据用户名查询（派生查询）
    Optional<User> findByUsername(String username);

    // 2. 根据邮箱查询
    Optional<User> findByEmail(String email);

    // 3. 检查用户名是否存在
    boolean existsByUsername(String username);

    // 4. 自定义JPQL查询
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.articles WHERE u.username = :username")
    Optional<User> findByUsernameWithArticles(@Param("username") String username);

    // 5. 统计活跃用户
    @Query("SELECT COUNT(u) FROM User u WHERE u.status = :status")
    long countByStatus(@Param("status") User.UserStatus status);

    long countByCreatedAtAfter(LocalDateTime dateTime);

    List<User> findByRole(User.UserRole role);

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role")
    long countByRole(@Param("role") User.UserRole role);

    // ==================== 全表加载优化新增方法 ====================

    // 根据状态查询用户（数据库过滤，避免 findAll 后在内存过滤）
    List<User> findByStatus(User.UserStatus status);

    // 按用户名或显示名称模糊搜索（数据库过滤 + 数据库分页，避免 findAll 后在内存过滤/分页）
    Page<User> findByUsernameContainingOrDisplayNameContaining(String username, String displayName, Pageable pageable);
}
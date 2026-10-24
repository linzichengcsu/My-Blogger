-- ============================================
-- V5: 文章点赞去重表
-- 请求路径只写 Redis，定时任务批量 INSERT IGNORE 落库；
-- 唯一约束 (article_id, user_id) 是点赞去重的最终保证。
-- ============================================

CREATE TABLE article_like (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    article_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_article_like UNIQUE (article_id, user_id),
    CONSTRAINT fk_article_like_article FOREIGN KEY (article_id) REFERENCES articles (id) ON DELETE CASCADE,
    CONSTRAINT fk_article_like_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

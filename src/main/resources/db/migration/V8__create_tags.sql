-- ============================================
-- V8: 文章标签系统
--   tags        标签主表（全局共享，名称唯一）
--   article_tag 文章-标签关联中间表（仿 article_like 的独立实体模式）
-- 唯一约束 (article_id, tag_id) 保证同一文章不会重复挂载同一标签；
-- 外键 ON DELETE CASCADE 保证文章/标签删除时关联记录自动清理。
-- ============================================

CREATE TABLE tags (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    name        VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tags_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE article_tag (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    article_id  BIGINT      NOT NULL,
    tag_id      BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_article_tag UNIQUE (article_id, tag_id),
    CONSTRAINT fk_article_tag_article FOREIGN KEY (article_id) REFERENCES articles (id) ON DELETE CASCADE,
    CONSTRAINT fk_article_tag_tag     FOREIGN KEY (tag_id)     REFERENCES tags (id)     ON DELETE CASCADE,
    INDEX idx_article_tag_tag (tag_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

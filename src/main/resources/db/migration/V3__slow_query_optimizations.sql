-- ============================================
-- V3: 慢 SQL 优化（数据库 Schema 层，需人工确认后执行）
-- 适用：MySQL 8（生产库 Flyway 会自动执行；开发库 flyway.enabled=false，请按需手动执行）
-- 说明：本脚本涉及列类型变更与索引新增，执行前建议先备份 / 低峰期执行。
--       其中第 1 节（列类型放宽）与第 4 节（外键级联）为可选项，按业务需要取舍。
-- ============================================

-- 1. [可选] 放宽长文本列
--    原 V1 中 articles.content / comments.content 均为 VARCHAR(255)，
--    博客正文/长评论会触发 MySQL 严格模式的 "Data too long" 写入失败。
--    注意：实体 Article/Comment 未同步 columnDefinition，若 prod 使用
--    ddl-auto=validate 且 validate 对 TEXT/VARCHAR 类型差异校验严格，需同时
--    给实体加 @Lob 或 @Column(columnDefinition=...) 保持同步（并处理 H2 兼容）。
ALTER TABLE articles   MODIFY COLUMN content     MEDIUMTEXT;
ALTER TABLE articles   MODIFY COLUMN summary     VARCHAR(500);
ALTER TABLE comments   MODIFY COLUMN content     TEXT;
ALTER TABLE users      MODIFY COLUMN bio         VARCHAR(1000);
ALTER TABLE categories MODIFY COLUMN description VARCHAR(1000);

-- 2. [可选] 标题全文索引（中文）
--    现有 findByTitleContaining 使用 LIKE '%kw%'，前导通配无法走 B+ 树索引。
--    若文章量较大（数万+），启用 ngram 全文索引后可改用 MATCH...AGAINST；
--    个人博客量级（<1 万篇）现有 LIKE 可接受，此索引可暂不建。
ALTER TABLE articles ADD FULLTEXT INDEX ft_article_title (title) WITH PARSER ngram;

-- 3. [推荐] 热门文章查询复合索引
--    对应 ArticleRepository.findPopularArticlesSince：WHERE created_at >= ? ORDER BY like_count DESC。
--    现有单列索引（created_at / like_count）无法同时满足过滤+排序，会产生 filesort。
CREATE INDEX idx_article_created_like ON articles(created_at, like_count DESC);

-- 4. [可选] 外键级联（兜底加固）
--    代码层已在删除文章前先批量清理评论与分类关联（避免 FK 失败）；
--    若希望数据库层兜底，可取消注释以下语句（FK 名称以 V1 建表为准）：
-- ALTER TABLE comments DROP FOREIGN KEY fk_comments_article;
-- ALTER TABLE comments ADD CONSTRAINT fk_comments_article
--     FOREIGN KEY (article_id) REFERENCES articles (id) ON DELETE CASCADE;
-- ALTER TABLE article_category DROP FOREIGN KEY fk_ac_article;
-- ALTER TABLE article_category ADD CONSTRAINT fk_ac_article
--     FOREIGN KEY (article_id) REFERENCES articles (id) ON DELETE CASCADE;
-- ALTER TABLE article_category DROP FOREIGN KEY fk_ac_category;
-- ALTER TABLE article_category ADD CONSTRAINT fk_ac_category
--     FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE CASCADE;

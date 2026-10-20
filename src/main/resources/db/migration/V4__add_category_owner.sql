-- ============================================
-- V4: 收藏夹归属用户
-- 收藏夹为用户个人资产，按创建者隔离展示（写文章下拉/个人中心仅展示本人创建的收藏夹）
-- 1) 新增 owner_id 列 + 外键 + 索引
-- 2) 兼容历史数据：将无归属的收藏夹归属给最早的管理员
-- ============================================

ALTER TABLE categories ADD COLUMN owner_id BIGINT NULL AFTER description;

ALTER TABLE categories ADD CONSTRAINT fk_categories_owner
    FOREIGN KEY (owner_id) REFERENCES users (id);

CREATE INDEX idx_categories_owner_id ON categories(owner_id);

-- 历史数据补记创建者（无管理员时保持 NULL，由应用启动时的补记逻辑兜底）
UPDATE categories
SET owner_id = (SELECT u.id FROM users u WHERE u.role IN ('ADMIN', 'SUPER_ADMIN') ORDER BY u.id LIMIT 1)
WHERE owner_id IS NULL;

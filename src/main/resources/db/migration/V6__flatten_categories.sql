-- ============================================
-- V6: 收藏夹扁平化 - 移除父子层级
-- 删除 categories.parent_category_id 自引用列及其外键，所有收藏夹平级
-- 历史迁移 V1/V2/V4 不可修改，此处通过新增迁移完成 schema 变更
-- ============================================

-- 先删除自引用外键，再删除列（MySQL 删除带外键的列需先移除外键约束）
ALTER TABLE categories DROP FOREIGN KEY fk_categories_parent;
ALTER TABLE categories DROP COLUMN parent_category_id;

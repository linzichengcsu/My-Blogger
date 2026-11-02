# Pull Request 模板

## ✅ PR Checklist

- [ ] 我读了 [CONTRIBUTING.md](https://github.com/linzichengcsu/My-Personal-Blogger/blob/main/CONTRIBUTING.md)
- [ ] 代码在本地通过了 `mvn verify`（后端）或 `npm run build && npm run test:run`（前端）
- [ ] 新增 / 修改的代码有对应的单元测试
- [ ] 提交信息符合 Conventional Commits 规范（`feat:` / `fix:` / `refactor:` 等）
- [ ] 没有把 `.env`、`target/`、`frontend/node_modules/` 等不该进仓库的文件提交进来
- [ ] （若改了 SQL）数据库迁移 Flyway 文件版本号已递增、脚本已在 H2 测试环境验证

## 📝 变更说明

**关联 Issue**（选填）：Closes #

**做了什么**：
- ...

**为什么这样做** / **替代方案**：
- ...

**影响范围**：
- [ ] 后端 API / 业务逻辑
- [ ] 前端 UI / 交互
- [ ] 数据库 Schema / Flyway 迁移
- [ ] 安全 / 认证 / 权限
- [ ] 性能 / 缓存
- [ ] 文档 / 测试
- [ ] Docker / CI / 部署

## 🧪 验证方式

- [ ] 单元测试通过
- [ ] 集成测试通过（`ApiIntegrationTest`）
- [ ] 前端 typecheck 通过（`npm run typecheck`）
- [ ] 手动测试了相关页面 / API

**特殊说明**（如需要评审者关注的地方）：
- ...

## 📸 截图 / 录屏（可选，适用于前端 UI 改动）

（粘贴图片或录屏链接）

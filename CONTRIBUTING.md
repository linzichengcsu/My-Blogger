# CONTRIBUTING — 行吟博客贡献指南

首先，感谢你花时间为行吟博客贡献代码 ❤️

---

## 目录

- [行为准则](#行为准则)
- [开发环境搭建](#开发环境搭建)
- [编码规范](#编码规范)
- [分支模型](#分支模型)
- [提交流程](#提交流程)
- [测试](#测试)
- [SQL / 数据库迁移](#sql--数据库迁移)
- [API 约定](#api-约定)
- [常见问题](#常见问题)

---

## 行为准则

请尊重所有贡献者。我们期望：

- 友善且包容的语言与行为
- 尊重不同观点与经验
- 接受建设性批评
- 关注对社区最有利的事情

违反行为的贡献者可能被项目维护者要求离开贡献者行列。

---

## 开发环境搭建

### 系统要求

| 工具 | 版本 |
|---|---|
| JDK | 21 |
| Maven | 3.9+ |
| Node.js | 22 |
| npm | 随 Node 内置 |
| MySQL | 9.x（测试用 H2，可免） |
| Redis | 7.x（测试可免，Spring Cache 有开关） |
| ElasticSearch | 9.x（测试可免，有降级） |
| Git | 最新 |

### 一键启动（Docker Compose）

如果本地不想装 MySQL/Redis/ES，直接用 Docker：

```bash
cd deploy
cp .env.example .env   # Windows: copy .env.example .env
docker compose up -d   # 等容器全部 healthy
```

### 本地手动启动

```bash
# 1. 克隆
git clone https://github.com/linzichengcsu/My-Personal-Blogger.git
cd My-Personal-Blogger

# 2. 创建数据库（dev 环境）
mysql -u root -p -e "CREATE DATABASE csulzc_blogdb_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

# 3. 配置环境变量（或直接改 application-dev.yml）
export SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/csulzc_blogdb_dev?useSSL=false&serverTimezone=UTC
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=123456

# 4. 后端启动
mvn spring-boot:run

# 5. 前端启动（新终端）
cd frontend
npm install
npm run dev    # http://localhost:5173，自动代理 /api → localhost:8080
```

### 测试环境（推荐先跑这个）

后端 `test` profile 自动使用 H2（MySQL 模式），**不需要 MySQL/Redis/ES**：

```bash
mvn test                          # 跑全部 48+ 测试类
mvn -Dtest=ApiIntegrationTest test   # 跑端到端集成测试
```

---

## 编码规范

### 后端（Java / Spring Boot）

- **包结构**：`csulzc.My_Personal_Blogger.{api,aspect,config,controller,domain,repository,search,security,service}`
- **命名约定**：
  - Controller → `XxxController`（`/api/xxx/**`）
  - Service → `XxxService` + `XxxServiceImpl` 接口分离
  - Repository → `XxxRepository`（继承 `JpaRepository`）
  - Entity → `Xxx`（继承 `BaseEntity<Long>`）
  - DTO → `XxxDTO`（响应）/ `XxxRequest`（请求）
  - Exception → `XxxException` 或直接 `ApiError`
- **不要在 Controller 里写业务逻辑**——Controller 只做参数校验、调用 Service、返回 Result 包装
- **不要在 Entity 里直接暴露给前端**——使用 DTO 转换
- **日志**：用 `@Slf4j` + `log.info/error`，不要用 `System.out.println`
- **敏感操作**：密码必须 `BCryptPasswordEncoder`，JWT 令牌用 JJWT 0.12.3

### 前端（React / TypeScript）

- **严格模式**：项目启用了 `strict: true`、`noUnusedLocals: true`、`noImplicitAny: true`——提交前 `npm run typecheck` 必须通过
- **目录结构**：
  - `src/api/` — 后端接口（按 domain 分文件，不要把 axios 调调用散在组件里）
  - `src/components/` — 可复用组件（`ArticleCard.tsx`、`MarkdownView.tsx`）
  - `src/context/` — React Context（`AuthContext.tsx` 管理登录态）
  - `src/pages/` — 页面组件
  - `src/utils.ts` — 纯函数工具（日期、截断、URL 拼接）
  - `src/constants/` — 常量（路由 `routes.ts`）
  - `src/types/` — TypeScript 类型定义
  - `src/__tests__/` — 单元测试（vitest）
- **不要直接在组件里调 axios**——统一用 `http.get/post/...`（`src/api/request.ts`）
- **不要硬编码后端 URL**——用 `import.meta.env.VITE_API_BASE_URL`（默认 `/api`）
- **不要在组件里写 localStorage**——Token 存取封装在 `request.ts` 的 `setAuthTokens/getAccessToken`

### 通用

- **行尾分号**：前端 TS 加分号；后端 Java 本来就加；SQL 分号加分号
- **缩进**：Java 4 空格；前端 2 空格（ESLint / Prettier 自动）
- **Git 提交信息**：Conventional Commits 格式
  - `feat:` 新功能
  - `fix:` Bug 修复
  - `refactor:` 重构（不改变外部行为）
  - `docs:` 文档
  - `test:` 测试
  - `chore:` 构建 / 工具 / CI

---

## 分支模型

项目采用 `main` + `develop` 双分支协作：

| 分支 | 角色 | 合入来源 | 去向 |
|---|---|---|---|
| `main` | 稳定、可发布代码 | `develop`（发布）、`hotfix/*`（紧急修复） | 打 tag 发布 |
| `develop` | 日常集成分支 | `feat/*`、`fix/*`、`refactor/*`、`docs/*`、`test/*`、`chore/*` | `main`（发布时） |

- **日常功能 / 修复**：从 `develop` 切出，PR 合回 `develop`
- **发布**：`develop` → `main` 的 PR，评审通过后合并并打 tag（如 `v1.2.0`）
- **紧急修复**：从 `main` 切 `hotfix/xxx`，PR 合入 `main`，随后再合并回 `develop`，避免修复丢失
- 任何人**不直接 push** `main` / `develop`，一律走 PR

### 发布

```bash
git checkout develop && git pull upstream develop
# 在 GitHub 上发起 develop → main 的 PR，CI 全绿后合并
git checkout main && git pull upstream main
git tag -a v1.2.0 -m "release: v1.2.0"
git push upstream v1.2.0
```

**合并方式**：`feat/*` → `develop` 可用 Squash；`develop` → `main` 请用 Merge commit，避免两条分支历史分叉、后续发布出现大量重复冲突。

### 紧急修复

```bash
git checkout main && git pull upstream main
git checkout -b hotfix/xxx
# 修复 + 测试 + 提交
git push origin hotfix/xxx          # PR 目标分支：main
# 合入 main 后，再把 main 合并回 develop
git checkout develop && git pull upstream develop
git merge upstream/main && git push upstream develop
```

### 分支保护（仓库管理员在 GitHub 设置）

1. **Settings → General → Default branch** 改为 `develop`，让新克隆和新 PR 默认指向集成分支
2. **Settings → Branches** 对 `main` 和 `develop` 各添加一条 protection rule：
   - Require a pull request before merging（至少 1 个 approval）
   - Require status checks to pass：勾选 `Maven Test (JDK 21)` 与 `Typecheck, Unit Test & Build (Node 22)`
   - Require branches to be up to date before merging
   - 取消 Allow force pushes / Allow deletions
   - 可选：Require linear history、Require conversation resolution

## 提交流程

```bash
# 1. Fork → Clone
git clone https://github.com/<你的用户名>/My-Personal-Blogger.git
cd My-Personal-Blogger
git remote add upstream https://github.com/linzichengcsu/My-Personal-Blogger.git

# 2. 建分支（从 develop 切）
git checkout develop
git pull upstream develop
git checkout -b feat/your-feature-name    # 或 fix/xxx、refactor/xxx

# 3. 本地开发 + 跑测试
# 后端：
mvn verify
# 前端：
cd frontend && npm run build && npm run test:run

# 4. Commit（Conventional Commits）
git add .
git commit -m "feat(article): 文章编辑器支持自动保存"

# 5. Push → 发起 Pull Request
git push origin feat/your-feature-name
# 在 GitHub 上比较 develop...feat/your-feature-name，创建 PR（目标分支选 develop）
```

**PR 目标分支**：日常改动一律合入 `develop`；只有发布（`develop → main`）和紧急修复（`hotfix/* → main`）才以 `main` 为目标。

**PR 被合并前请保持分支同步**：
```bash
git fetch upstream
git rebase upstream/develop
# 有冲突则解决后继续
git push origin feat/your-feature-name --force-with-lease
```

---

## 测试

### 后端（JUnit 5 + Mockito + AssertJ）

```bash
# 全量测试（48+ 测试类）
mvn test

# 分层测试
mvn -Dtest="*ControllerTest" test       # Controller 层
mvn -Dtest="*ServiceTest" test          # Service 层（Mock 依赖）
mvn -Dtest="ApiIntegrationTest" test    # 端到端集成（RANDOM_PORT + H2）

# 测试覆盖率（JaCoCo）
mvn clean test jacoco:report
# 报告在 target/site/jacoco/index.html
```

**测试约定**：
- 测试放在 `src/test/java`，**包路径与 main 完全对齐**（方便 `@SpringBootTest`）
- Service 层单元测试用 `@ExtendWith(MockitoExtension.class)` + `@Mock`
- Controller 层用 `@WebMvcTest(XxxController.class)` + `MockMvc`
- 集成测试用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`

### 前端（Vitest + Testing Library + jsdom）

```bash
# 单次运行（CI 用这个）
cd frontend && npm run test:run

# 监听模式（开发时用）
cd frontend && npm run test

# 覆盖率报告
cd frontend && npm run test:coverage
# 报告在 frontend/coverage/index.html
```

**测试约定**：
- 测试文件放在 `src/__tests__/`，命名 `xxx.test.ts(x)`
- 优先测**行为**而不是实现细节（用 `screen.getByRole` / `screen.getByText`，不要 `.querySelector`）
- 请求层 mock 用 `axios-mock-adapter`
- 组件里的 mock 用 `vi.mock()` + `vi.hoisted()`（避免工厂函数提升问题）
- 不要 mock 纯函数工具函数（utils.ts、format 函数），直接测返回值

---

## SQL / 数据库迁移

**不要手动改 `src/main/resources/db/migration/` 里已有的 Flyway 文件！** 已应用的迁移在生产数据库里会记录版本号，改了就炸。

**新增迁移**的正确姿势：

1. 确认当前最高版本号：
   ```bash
   ls src/main/resources/db/migration/
   # 看到 V5__xxx.sql，你就要写 V6__xxx.sql
   ```

2. 新建文件：`V6__描述_用下划线分隔.sql`
   ```sql
   -- 示例：新增用户个人博客子域名
   ALTER TABLE user ADD COLUMN subdomain VARCHAR(64) UNIQUE;
   UPDATE user SET subdomain = username WHERE subdomain IS NULL;
   ```

3. **在 H2 测试环境验证**：
   ```bash
   mvn test -Dtest=ApiIntegrationTest
   # 集成测试会自动执行新迁移，失败说明 SQL 不兼容 H2
   ```

4. 同时更新 Entity 字段 + DTO + Repository 查询。

---

## API 约定

### 请求 / 响应格式

所有业务 API 统一返回 `Result<T>` 包装（HTTP 200）：

```json
{
  "code": 200,
  "message": "success",
  "data": { "id": 1, "title": "..." }
}
```

管理员 API（`/api/admin/**`）统一返回 `ApiResponse<T>`：

```json
{
  "success": true,
  "message": "ok",
  "data": { "totalUsers": 12 }
}
```

**失败时**（HTTP 非 2xx 或业务 code 非 200）：
```json
{
  "code": 404,
  "message": "文章不存在",
  "data": null
}
```

### 认证

- 需要登录的接口：请求头加 `Authorization: Bearer <accessToken>`
- Token 过期（401）：前端自动用 refreshToken 刷新并重放原请求（见 `src/api/request.ts`）
- 后端通过 `Spring Security` 过滤器链校验，JWT 黑名单（登出后立即失效）

### 分页

统一用 `page` / `size` 查询参数，返回 `PageResponse<T>`：

```
GET /api/articles?page=1&size=10&keyword=React&categoryId=5
```

---

## 常见问题

**Q: 我只是想提个小修改（拼写、文档、格式），要走完整流程吗？**
A: 小修改直接在 GitHub 网页上编辑文件就行，GitHub 会自动为你开 PR。Commit message 写 `docs:` 或 `style:`。

**Q: 前端 npm install 在我的 Windows 电脑上 EBUSY 怎么办？**
A: 这是已知问题（杀软实时扫描和符号链接冲突）。方案：① 把项目目录加进 Windows Defender 排除项；② 确保用 npm 而不是 pnpm（pnpm 硬链接更容易撞锁）。

**Q: 测试用例写太多了怎么办？**
A: 优先覆盖：① 业务逻辑（Service 层）；② 错误分支；③ 边界条件。UI 层测试只测关键交互（登录成功/失败、按钮是否渲染），不要测 Ant Design 本身能不能显示。

**Q: 我能加第三方前端 UI 库吗？**
A: **暂不**。项目统一 Ant Design 5。如果 Ant Design 解决不了你的需求，提 Issue 先讨论。

---

再次感谢你的贡献！🎉

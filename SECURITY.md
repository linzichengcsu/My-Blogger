# SECURITY — 行吟博客安全政策

本项目的安全是社区成员共同维护的。如果你发现了安全漏洞，请**不要在公开 Issue 或 PR 中报告**，按下方流程私下联系我们。

---

## 🔒 项目已知安全机制

| 机制 | 说明 |
|---|---|
| **认证** | JWT 无状态（JJWT 0.12.3），Bearer Token，Token 黑名单（登出后立即失效） |
| **密码** | BCryptPasswordEncoder，禁止明文存储 |
| **授权** | Spring Security 6 + 三级角色（USER / ADMIN / SUPER_ADMIN） |
| **请求来源** | `/api/**` 强制认证；基于 Origin 检测区分前端请求（普通 USER）与直接 API 调用（ADMIN） |
| **文件上传** | 类型白名单（jpeg/png/gif/webp）、大小限制（≤ 5MB）、本地磁盘存储（非可执行路径） |
| **密码重置** | 邮箱验证码（SMTP 可配置），验证码单次使用 + 过期 |
| **限流** | 登录失败次数限制（防暴力破解） |
| **SQL** | JPA / Hibernate 参数化查询，全项目不拼接原始 SQL |
| **CORS** | 生产环境只允许前端域名 |
| **日志脱敏** | AOP 自动脱敏敏感字段（密码、Token、身份证号等） |
| **数据库** | HikariCP 连接池 + 泄漏检测；Flyway 迁移不执行未评审的 SQL |
| **依赖** | GitHub Dependabot + GitHub Actions `npm audit` / Maven OWASP 检测 |

---

## 📮 安全漏洞报告流程

### 适用范围

以下情况不属于安全漏洞（请用普通 Issue 或 Discussion 讨论）：

- 理论性/概念性攻击（例如"如果你能猜中用户的 BCrypt salt..."）
- 在公开 Issue / PR 中已经被讨论过、且已修复的问题
- 需要物理访问、社会工程、或需要大量钓鱼才能实现的攻击
- 自签名证书、非生产环境配置、测试账户、默认密码
- 未获得授权的测试（**请先联系我们获得书面授权后再进行渗透测试**）

### 报告方式

**请通过 GitHub Security Advisories 私下报告**：

👉 [https://github.com/linzichengcsu/My-Personal-Blogger/security/advisories](https://github.com/linzichengcsu/My-Personal-Blogger/security/advisories)

如果 GitHub Advisories 不可用，**发邮件到项目维护者**（不要在 Issue 里贴漏洞详情）。

### 报告时请包含

- **漏洞类型**（SQL 注入 / XSS / CSRF / 越权 / 敏感信息泄露 / 文件上传绕过 / 认证绕过 等）
- **影响范围**（哪个版本、哪个组件、哪个接口）
- **复现步骤**（最小 PoC：curl 命令、HTTP 请求、前端操作序列）
- **影响评估**（能拿到什么权限、能做什么操作、影响哪些用户）
- **建议修复方向**（可选，但如果有能帮我们快速定位）
- **你的联系方式**（用于后续沟通）

### 处理流程（SLA）

| 阶段 | 时间 | 动作 |
|---|---|---|
| 收到报告 | ≤ 24 小时 | 自动确认 → 分配给维护者 → 初步评估 |
| 分析与修复 | ≤ 7 天 | 复现漏洞 → 开发修复 → 本地测试 → 写 CVE 请求 |
| 验证修复 | ≤ 10 天 | 报告者验证修复方案（如提供了 PoC） |
| 公开披露 | ≤ 14 天 | 发布安全公告 + CVE → 感谢报告者 |

> 复杂漏洞（影响范围广、需要大规模重构）可能需要更长时间，我们会同步进度。

---

## 🔨 开发时的安全守则

### 后端（Spring Boot / Java）

1. **密码只存 BCrypt 哈希**——`user.password` 永远不能是明文
2. **不要绕过 Spring Security**——Controller 上加 `@PreAuthorize` 明确权限要求，不要靠前端隐藏按钮
3. **Controller 参数要校验**——`@Valid` + `@NotNull` / `@Size` / `@Email`，不要假设用户会传合法值
4. **不要在响应里返回敏感字段**——DTO 里用 `@JsonIgnore` 或显式字段列表，不要把整个 Entity 直接返回
5. **文件上传不要 trust 客户端文件名**——用 UUID 重命名，存到非 Web 根目录，下载时通过 `/api/files/{id}` 走 Controller 鉴权
6. **JWT 的 secret 不要硬编码**——用环境变量 `app.jwt.secret`，dev 环境可以内置，prod 必须覆盖
7. **日志脱敏**——敏感字段加 `@Sensitive` 注解，AOP 自动在日志里替换为 `******`

### 前端（React / TypeScript）

1. **不要信任 localStorage 的 Token**——Token 可以被篡改、过期，每次请求失败后端会拒绝；前端只是存储，不是信任来源
2. **路由守卫只是 UX 优化**——`AuthContext` 控制的"是否显示管理后台按钮"**不等于安全**，后端 `/api/admin/**` 必须有独立鉴权
3. **不要把敏感信息塞进前端代码**——API Key、secret 全部走后端；前端 `import.meta.env.VITE_*` 只能是公开配置
4. **XSS 防护**——所有动态渲染的内容走 React 默认转义；`dangerouslySetInnerHTML` 必须先经过 `dompurify` 或确认来源可信
5. **eval() / new Function() 绝对禁止**——没有例外

### Docker / 部署

1. **不要把 `.env` 打进镜像**——用 `docker-compose.yml` 的 `environment:` 或 Secrets
2. **不要以 root 跑容器**——Dockerfile 里 `USER node`（前端）/ `RUN adduser && USER`（后端）
3. **后端不要暴露 8080 到公网**——只让 Nginx 在同一个 Docker network 里访问
4. **MySQL / Redis / ES 不要暴露端口**——用 Docker network 内部通信，端口映射只给本机（`-p 3306:3306` 改成 `-p 127.0.0.1:3306:3306`）

### Git 提交前

- 检查有没有意外提交 `.env`、`target/`、`frontend/node_modules/`
- `.gitignore` 已经覆盖了，但手动检查没坏处
- 提交信息里不要贴密码、Token、真实 API URL

---

## 📋 依赖安全扫描

项目已接入 GitHub 安全工具：

- **Dependabot**：自动检测过时依赖 + 已知漏洞，每周检查一次
- **npm audit**：CI 里 `npm ci` 后自动执行
- **OWASP Dependency-Check**：Maven 构建时扫描 Java 依赖

如果你收到 Dependabot 告警：

1. 先读告警详情——**不是所有 CVE 都影响我们的用法**
2. 评估是否是生产依赖（devDependencies 里的 vitest/jsdom 漏洞通常可忽略）
3. 升级到修复版本 + 跑测试验证
4. 如果升级 breaking change 太多，写 Issue 记录并延后

---

## 🤝 致谢

安全报告者的贡献将在 GitHub Security Advisories 和 CHANGELOG.md 中公开致谢。在 CVE 申请时我们会把你的名字列为"发现者"（经你同意）。

---

再次感谢你帮助我们守护行吟博客的安全！

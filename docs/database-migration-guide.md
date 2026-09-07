# 生产环境数据库部署指南（Flyway 自动迁移）

> 适用项目：My-Personal-Blogger（Spring Boot 3.2.5 / Java 23 / MySQL 8.0+ / Redis / Flyway）
>
> 本文替代旧版《数据库迁移与 Flyway 操作指南》。旧版所述“V1 只建索引不建表”“索引命名与列不符”等问题均已在迁移脚本中修复，当前脚本可直接用于生产首次部署。
>
> 核心结论：**你不需要手工建表建索引**。生产库建好后，以 `prod` profile 启动应用，Flyway 会在启动阶段自动执行迁移脚本，完成建表 + 建索引。

---

## 1. 迁移脚本一览

脚本位于 `src/main/resources/db/migration/`，打包后随应用一并发布（`classpath:db/migration`）：

| 版本 | 文件 | 内容 |
|---|---|---|
| V1 | `V1__create_production_schema.sql` | 创建 6 张表：`users` / `articles` / `comments` / `categories` / `article_category` / `admins`，含唯一约束、外键、复合主键；`utf8mb4_general_ci`、InnoDB |
| V2 | `V2__create_production_indexes.sql` | 生产查询索引（user/article/comment/category 各类查询索引） |

生产环境相关配置（`application-prod.yml`）：

| 配置项 | 值 | 说明 |
|---|---|---|
| `spring.jpa.hibernate.ddl-auto` | `validate` | 应用**不建表**，只校验结构与实体一致 → 建表完全由 Flyway 负责 |
| `spring.flyway.enabled` | `true` | 启动时自动执行未跑过的迁移 |
| `spring.flyway.baseline-on-migrate` | `true` | 库中已有表但无历史表时自动打基线，避免首次启动报错 |
| `spring.flyway.baseline-version` | `0` | 基线版本号（已有表视为 V0 之前） |
| `spring.flyway.out-of-order` | `false` | 禁止乱序执行（生产必须保持 false） |
| `spring.flyway.validate-on-migrate` | `true` | 迁移前校验已执行脚本未被篡改 |

> dev/test 环境 `flyway.enabled=false`，迁移脚本只在生产生效，不影响日常开发。

---

## 2. 一次性准备：数据库与账号

### 2.1 创建数据库

迁移脚本**不包含建库语句**，需先手工建库（库名与 prod 配置一致）：

```sql
CREATE DATABASE csulzc_blogdb_prod
  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

### 2.2 创建专用账号（不要用 root）

```sql
CREATE USER 'blog_app'@'%' IDENTIFIED BY '<替换为强密码>';
GRANT ALL PRIVILEGES ON csulzc_blogdb_prod.* TO 'blog_app'@'%';
FLUSH PRIVILEGES;
```

- 迁移需要建表/建索引权限，`ALL ON csulzc_blogdb_prod.*` 已足够，**无需全局权限**。
- 若 MySQL 与应用不在同一台机器，请将 `'%'` 限制为应用服务器网段或 IP，并确保数据库端口对应用服务器开放。

### 2.3 关于 SSL（重要）

`application-prod.yml` 默认连接串带 `useSSL=true&requireSSL=true`。**如果 MySQL 未开启 SSL，连接会失败**。两种情况任选其一：

- 生产 MySQL 已配置 SSL 证书 → 保持默认即可；
- 未配置 SSL（如内网/演示环境）→ 在环境变量中覆盖连接串（见第 3 节），去掉 SSL 参数。

---

## 3. 运行环境变量

应用启动时会自动读取**运行目录下的 `.env` 文件**（`application.properties` 中配置了 `spring.config.import=optional:file:./.env[.properties]`，`.env` 不存在则忽略，不会报错）。推荐在部署目录创建 `.env`：

```properties
# ---- 数据库 ----
DB_USERNAME=blog_app
DB_PASSWORD=<替换为强密码>
# 仅当数据库不在 localhost:3306、或需调整 SSL 时取消注释并修改：
# SPRING_DATASOURCE_URL=jdbc:mysql://<host>:3306/csulzc_blogdb_prod?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf8

# ---- Redis（prod 缓存默认开启，必需）----
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
# REDIS_PASSWORD=<若 Redis 设了密码>

# ---- JWT（长度 ≥ 32 字符的随机串，务必替换默认值）----
JWT_SECRET=<替换为至少32字符的强随机密钥>
```

注意：

- 配置文件为 **properties 格式**（`KEY=VALUE`），不要写 `export`。
- `.env` 是明文文件，**务必加入 `.gitignore`**，不要提交进仓库。
- 也可不建 `.env`，改用系统环境变量注入同名变量（如 `export DB_PASSWORD=...`），效果相同。
- 生产**禁止**使用默认值：密码默认 `change-me-in-production`、JWT 有占位默认值，必须覆盖。

---

## 4. 打包并首次启动（自动迁移）

### 4.1 准备运行目录

prod 配置会将上传文件写入 `/var/www/uploads`、日志写入 `/var/log/blog`，需先创建并授权（按实际运行用户调整）：

```bash
sudo mkdir -p /var/www/uploads /var/log/blog
sudo chown -R $(whoami) /var/www/uploads /var/log/blog
```

### 4.2 打包

```bash
cd <项目目录>
mvn -DskipTests package
```

### 4.3 启动（首次部署自动执行迁移）

```bash
# 在放置 .env 的部署目录下启动
java -jar target/blog-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

首次启动时 Flyway 自动执行 V1、V2，日志中应出现：

```
Successfully applied 2 migrations to schema `csulzc_blogdb_prod` (execution time ...)
```

> 生产建议用 `nohup` 或 systemd 托管，例如：
>
> ```bash
> nohup java -jar target/blog-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod > /var/log/blog/app.out 2>&1 &
> ```
>
> 用 systemd 时，`WorkingDirectory` 必须指向 `.env` 所在目录，`spring.config.import` 才能读到 `.env`。

---

## 5. 验证迁移结果

```bash
mysql -u blog_app -p csulzc_blogdb_prod -e \
  "SELECT installed_rank, version, description, success FROM flyway_schema_history;"

mysql -u blog_app -p csulzc_blogdb_prod -e "SHOW TABLES;"
# 应看到 6 张业务表 + flyway_schema_history

mysql -u blog_app -p csulzc_blogdb_prod -e "SHOW INDEX FROM articles;"
# 抽查 V2 创建的索引
```

预期：`flyway_schema_history` 有 V1、V2 两条记录且 `success = 1`。

---

## 6. 日常升级：后续数据库变更

**铁律：已执行成功的迁移脚本永不修改。** Flyway 会校验已执行脚本的 checksum，改动会导致启动失败（checksum mismatch）。

正确做法——每次变更新增一个更高版本号的脚本：

```
src/main/resources/db/migration/
├── V1__create_production_schema.sql   # 已执行，不动
├── V2__create_production_indexes.sql  # 已执行，不动
└── V3__xxx_new_change.sql             # 新增，描述本次变更
```

重新打包发布后，重启应用即自动应用 V3。破坏性变更（删列/删表）务必先备份并评估影响。

---

## 7. 备份（迁移前必做）

```bash
mysqldump -u blog_app -p csulzc_blogdb_prod > backup_$(date +%F_%H%M).sql
```

Flyway **不提供自动回滚**：迁移失败时，正确的恢复方式是先从备份还原，再修复脚本（新增版本）重跑。平时也应定期备份。

---

## 8. 常见问题速查

| 现象 | 处理 |
|---|---|
| `Access denied for user ...` | 检查 `DB_USERNAME` / `DB_PASSWORD` 与账号授权；`allowPublicKeyRetrieval=true` 仅在不走 SSL 的 URL 中需要 |
| 连接失败 / SSL 握手失败 | prod 默认 `requireSSL=true`；MySQL 未开 SSL 时用 `SPRING_DATASOURCE_URL` 覆盖为 `useSSL=false&allowPublicKeyRetrieval=true` |
| `FlywayValidateException` / checksum mismatch | 改动了已执行脚本——禁止。从备份恢复，或追加新版本脚本修正 |
| `validate failed`（表/列与实体不一致） | 核对迁移脚本与 JPA 实体，以新版本迁移修正，勿改旧脚本 |
| 启动报 Redis 连接失败 | prod 缓存默认开启，Redis 必须先就绪；检查 `REDIS_HOST` / `REDIS_PORT` / 密码 |
| 启动报目录不存在/无写权限 | 确认 `/var/www/uploads`、`/var/log/blog` 已创建且运行用户可写 |
| 数据库不在本机或端口被占 | 通过 `SPRING_DATASOURCE_URL` 覆盖 host 与端口 |

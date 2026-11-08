# 项目快速启动流程（技术小白版）

> 目标：4 分钟读懂，照着做就能把「行吟博客」在你电脑上跑起来。

## 这是个什么项目？

一个**前后端分离**的个人博客系统：

- **后端**：Spring Boot（Java）——负责文章、评论、用户、管理后台等数据接口
- **前端**：React ——你在浏览器里看到的网页界面
- 两个小帮手：**MySQL** 存数据、**Redis** 存缓存；**Elasticsearch** 管文章搜索（不装也能跑，搜索会自动降级）

---

## 选哪条路？

| 你的想法 | 推荐方式 |
|---|---|
| 只想最快看到网站跑起来，不改代码 | ✅ **方式一：Docker 一键启动** |
| 想改代码、边写边看效果 | 方式二：本地开发启动 |

---

## ✅ 方式一：Docker 一键启动（推荐，3 步）

Docker 会把后端、前端、MySQL、Redis、搜索服务**一次性装好并启动**，你不用分别安装。

**前提**：电脑已安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)，并且处于运行状态（右下角小鲸鱼图标）。

### 第 1 步：准备密码文件

进入项目的 `deploy` 文件夹，把 `.env.example` **复制一份**，改名为 `.env`，然后把里面的密码改成你自己的：

- `MYSQL_ROOT_PASSWORD` / `MYSQL_PASSWORD`：MySQL 密码
- `REDIS_PASSWORD`：Redis 密码
- `JWT_SECRET_PROD`：一串密钥，**至少 32 个字符**

> 💻 **Windows**：在资源管理器里直接复制、粘贴、重命名即可；文件名以 `.` 开头是正常的。
> 🍎 **macOS**：`.env` 是隐藏文件，Finder 默认不显示。在 Finder 里按 `Command + Shift + .` 即可显示/隐藏隐藏文件；用文本编辑器的"打开"对话框时也按这个快捷键。也可以直接用终端操作：
> ```bash
> cp .env.example .env      # 复制
> open -e .env              # 用"文本编辑"打开
> ```

### 第 2 步：一条命令启动全部

打开终端（Windows 用 PowerShell、macOS 用 Terminal），进入项目的 `deploy` 目录，运行：

```powershell
cd deploy
docker compose up -d --build
```

第一次会下载镜像、编译代码，需要等几分钟（取决于网速）。看到服务状态变成 `running` / `healthy` 就说明好了。

### 第 3 步：打开浏览器

访问 **http://localhost** ，就能看到博客首页啦。

想停掉：在 `deploy` 目录运行 `docker compose down`（数据不会丢）。

---

## 方式二：本地开发启动（想改代码时用）

需要先在电脑上装好这几样：**JDK 21**、**Maven 3.9+**、**MySQL 8.0+**、**Redis**、**Node.js 20+**。

### 1. 建数据库

在 MySQL 里执行一句 SQL，建一个空库：

```sql
CREATE DATABASE csulzc_blogdb_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

### 2. 配环境变量

在**项目根目录**，把 `.env.example` 复制一份改名为 `.env`，填上你的数据库账号密码，以及一串 ≥32 字符的 `JWT_SECRET`：

```
DB_USERNAME_DEV=你的MySQL用户名
DB_PASSWORD_DEV=你的MySQL密码
JWT_SECRET=随便敲一串至少32个字符的密钥
```

> ⚠️ `JWT_SECRET` 不填，后端会启动失败。

### 3. 启动后端（端口 8080）

在项目根目录的终端里运行：

```powershell
mvn spring-boot:run
```

（电脑没装 Maven？用项目自带的：`.\mvnw.cmd spring-boot:run`）

### 4. 启动前端（端口 5173）

**另开一个终端**（后端那个别关）：

```powershell
cd frontend
npm install
npm run dev
```

### 5. 打开浏览器

- 前端网页：**http://localhost:5173**
- 后端接口文档（Swagger）：**http://localhost:8080/swagger-ui.html**
- 后端健康检查：**http://localhost:8080/actuator/health**

> 没装 Elasticsearch 也能启动，搜索会自动降级为普通模糊匹配。Redis 建议启动着，否则登录保护等功能会报错。

---

## 登录与默认管理员

首次启动后端时，系统会**自动创建一个管理员账号**：

- 用户名：`admin`
- 密码：**随机生成的一串字符**，会打印在后端启动日志里（找「默认管理员账号已创建」那几行）

请务必从日志里复制记下这个密码，登录后立即修改。

---

## 常见小问题

- **macOS 找不到 / 打不开 `.env` 文件**：`.env` 以 `.` 开头，是隐藏文件，Finder 默认不显示。按 `Command + Shift + .` 切换显示；或用终端 `cp .env.example .env` 复制、`open -e .env` 打开。
- **后端启动失败，提示 JWT 相关**：检查根目录 `.env` 里 `JWT_SECRET` 是否填了、且 ≥32 字符。
- **前端页面空白 / 接口报错**：确认后端已在 8080 端口成功启动（看后端终端有没有红色报错）。
- **Docker 第一次启动很慢**：正常现象，耐心等；之后重启只需几秒。
- **想关掉服务**：Docker 方式用 `docker compose down`；本地方式在对应终端按 `Ctrl + C`。

## 还想了解更多？

- 完整项目说明：[README.md](README.md)
- 搜索服务搭建：[docs/elasticsearch-setup.md](docs/elasticsearch-setup.md)
- Docker 部署细节：[docs/docker-redis-elasticsearch-deployment.md](docs/docker-redis-elasticsearch-deployment.md)

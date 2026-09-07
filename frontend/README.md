# 个人博客系统前端 · Model 层框架

基于后端 `openapi.yaml`（v1.0.0）生成的 React + TypeScript 数据模型层与 API 客户端。
遵循 `skills/frontend.md` 目录约定：API 客户端位于 `src/api/`，共享类型位于 `src/types/`。

## 目录结构

```
frontend/
├── src/
│   ├── api/                  # API 客户端（统一从 request.ts 复用 axios 实例）
│   │   ├── request.ts        # axios 实例 + Result 解包 + Token 注入 + 401 静默刷新
│   │   ├── auth.ts           # 登录 / 注册 / 刷新 / 登出
│   │   ├── user.ts           # 用户管理（me / CRUD / 状态 / 统计 / 搜索）
│   │   ├── article.ts        # 文章管理（CRUD / 发布归档 / 点赞浏览 / 批量 / 搜索）
│   │   ├── category.ts       # 分类管理（CRUD / 树与层级 / 统计 / 搜索）
│   │   ├── comment.ts        # 评论管理（CRUD / 回复 / 统计 / 批量审核）
│   │   ├── file.ts           # 文件管理（上传 / 下载 / Base64）
│   │   ├── admin.ts          # 管理后台（看板统计）
│   │   └── index.ts          # 统一出口
│   └── types/                # 共享类型（与 openapi schemas 一一对应）
│       ├── common.ts         # Result<T> / PageResponse<T> / PageQuery / BatchIdRequest
│       ├── user.ts           # UserDetailDTO / UserProfileDTO / 登录注册请求等
│       ├── article.ts        # ArticleDetailDTO / ArticleListItemDTO / 创建更新请求
│       ├── category.ts       # CategoryDTO / CategoryTreeDTO / CategoryStatDTO
│       ├── comment.ts        # CommentDTO / CommentReplyDTO / 审核请求
│       ├── file.ts           # FileUploadResponse / Base64UploadRequest
│       ├── dashboard.ts      # DashboardStatsDTO 及子统计
│       └── index.ts          # 统一出口
├── vite.config.ts            # 开发代理 /api → http://localhost:8080
├── tsconfig.json             # 严格模式
└── package.json
```

## 核心设计

### 1. 响应解包（request.ts）

后端接口返回 `Result<T> = { data, message, code }`（成功 code=200），
看板接口返回 `ApiResponse<T> = { success, message, data, ... }`。
`http.get/post/put/del` 会自动解包，**页面拿到的是业务数据而非包装体**：

```ts
import { getArticleList } from '@/api'          // 或相对导入 './api'

// 返回 Promise<PageResponse<ArticleListItemDTO>>，失败时抛 ApiError
const { content, totalElements } = await getArticleList({ page: 0, size: 10 })
```

错误统一为 `ApiError`（含 `message`、业务 `code`、HTTP `status`）：

```ts
import { ApiError } from '@/api/request'
try {
  await login({ username, password })
} catch (e) {
  if (e instanceof ApiError) console.error(e.message, e.code)
}
```

### 2. 登录态与 401 静默刷新

- 登录成功后 `login()` 自动将 `accessToken/refreshToken` 存入 localStorage；
- 请求拦截器自动附加 `Authorization: Bearer <token>`；
- 响应遇到 401 时，自动用 refreshToken 调用 `/users/refresh` 换新 token 并**重放原请求**（并发请求会排队等待），刷新失败则清理登录态并触发登出回调：

```ts
import { setUnauthorizedHandler } from '@/api/request'

// 在应用入口注册：跳转登录页等
setUnauthorizedHandler(() => {
  window.location.href = '/login'
})
```

### 3. 分页参数

后端 `PageRequestDTO` 以扁平 query 接收（`@ModelAttribute`），
与 openapi 中 `pageRequest: $ref` 的写法等价：

```
GET /api/articles?page=0&size=10&sortBy=createdAt&sortDirection=desc
```

## 页面中使用示例

```tsx
import { useEffect, useState } from 'react'
import { getArticleList, viewArticle } from './api'
import type { ArticleListItemDTO } from './types'

export default function ArticleListPage() {
  const [articles, setArticles] = useState<ArticleListItemDTO[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getArticleList({ page: 0, size: 10 })
      .then((res) => setArticles(res.content))
      .finally(() => setLoading(false))
  }, [])

  // ...
}
```

## 环境与验证

| 用途 | 命令 |
| --- | --- |
| 安装依赖 | `cd frontend && pnpm install` |
| 类型检查 | `cd frontend && pnpm run typecheck` |
| 构建 | `cd frontend && pnpm run build` |
| 开发启动 | `cd frontend && pnpm run dev`（5173，代理 /api → localhost:8080） |

> 注意：若沙箱/CI 环境限制 esbuild 的 spawn（`spawn EPERM`），
> 请退出受限环境后在常规终端执行 `pnpm run build`。

## 后续扩展（页面层）

- 页面组件 → `frontend/src/pages/`
- 可复用组件 → `frontend/src/components/`
- 路由路径常量 → `frontend/src/constants/routes.ts`
- UI 延续 React + TailwindCSS 4 + `lucide-react`，不再引入其他 UI 框架

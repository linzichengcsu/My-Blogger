/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** API 基础路径，默认 '/api'（配合 Vite proxy 使用） */
  readonly VITE_API_BASE_URL?: string
  /** Vite 开发代理目标，默认 http://localhost:8080 */
  readonly VITE_PROXY_TARGET?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

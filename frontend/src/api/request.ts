/**
 * 统一的 HTTP 请求层。
 *
 * 职责：
 *  - 创建 axios 实例（baseURL 默认 '/api'，由 Vite 代理转发）
 *  - 请求前注入 `Authorization: Bearer <token>`
 *  - 响应后解包后端 `Result<T>` / `ApiResponse<T>`，仅返回业务 data
 *  - 401 时用 refreshToken 静默刷新并重放原请求；刷新失败触发登出回调
 *  - 统一抛出 ApiError，包含业务 code 与后端 message
 *
 * 页面层不要直接使用 axios，统一从这里导入 http / request。
 */
import axios, { AxiosError } from 'axios'
import type { AxiosInstance, AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse, Result } from '../types/common'
import type { LoginResponseDTO } from '../types/user'

/** token 的 localStorage 存储键 */
export const TOKEN_STORAGE_KEYS = {
  accessToken: 'blog.access_token',
  refreshToken: 'blog.refresh_token',
  expiresIn: 'blog.expires_in',
} as const

/** API 基础路径：默认 '/api'，可用 VITE_API_BASE_URL 覆盖 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

/* ------------------------------------------------------------------ */
/* Token 管理                                                          */
/* ------------------------------------------------------------------ */

export function getAccessToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEYS.accessToken)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEYS.refreshToken)
}

export interface AuthTokens {
  accessToken: string
  refreshToken?: string
  expiresIn?: number
}

/** 保存登录态（登录成功后调用） */
export function setAuthTokens(tokens: AuthTokens): void {
  localStorage.setItem(TOKEN_STORAGE_KEYS.accessToken, tokens.accessToken)
  if (tokens.refreshToken) {
    localStorage.setItem(TOKEN_STORAGE_KEYS.refreshToken, tokens.refreshToken)
  }
  if (typeof tokens.expiresIn === 'number') {
    localStorage.setItem(TOKEN_STORAGE_KEYS.expiresIn, String(tokens.expiresIn))
  }
}

/** 清除登录态（登出 / 刷新失败时调用） */
export function clearAuthTokens(): void {
  localStorage.removeItem(TOKEN_STORAGE_KEYS.accessToken)
  localStorage.removeItem(TOKEN_STORAGE_KEYS.refreshToken)
  localStorage.removeItem(TOKEN_STORAGE_KEYS.expiresIn)
}

/* ------------------------------------------------------------------ */
/* 错误类型                                                            */
/* ------------------------------------------------------------------ */

export interface ApiErrorOptions {
  /** HTTP 状态码 */
  status?: number
  /** 后端业务码（Result.code 或 ApiResponse.errorCode） */
  code?: number | string
  /** 后端返回的业务数据（失败时通常为 null） */
  data?: unknown
  cause?: unknown
}

/** 统一的 API 业务错误 */
export class ApiError extends Error {
  readonly status?: number
  readonly code?: number | string
  readonly data?: unknown

  constructor(message: string, options: ApiErrorOptions = {}) {
    super(message, options.cause !== undefined ? { cause: options.cause } : undefined)
    this.name = 'ApiError'
    this.status = options.status
    this.code = options.code
    this.data = options.data
  }
}

/** 将 axios 错误（网络/超时/HTTP 非 2xx）规范化为 ApiError */
function normalizeError(error: unknown): unknown {
  if (error instanceof ApiError) return error
  if (error instanceof AxiosError) {
    const status = error.response?.status
    const responseData = error.response?.data as Record<string, unknown> | undefined
    const message =
      responseData?.message ??
      (status === 401 ? '登录已过期，请重新登录' : status ? `请求失败（HTTP ${status}）` : '网络异常，请稍后重试')
    return new ApiError(String(message), {
      status,
      code: (responseData?.code as number | string | undefined) ?? (responseData?.errorCode as string | undefined),
      data: responseData?.data,
      cause: error,
    })
  }
  return error
}

/* ------------------------------------------------------------------ */
/* 响应解包                                                            */
/* ------------------------------------------------------------------ */

/** 判断是否为后端 `Result<T>` 包装（含 code/data/message 三字段） */
function isResultBody(body: unknown): body is Result<unknown> {
  if (body === null || typeof body !== 'object') return false
  const b = body as Record<string, unknown>
  return 'code' in b && 'data' in b && 'message' in b
}

/** 判断是否为后端 `ApiResponse<T>` 包装（含 success 布尔字段） */
function isApiResponseBody(body: unknown): body is ApiResponse<unknown> {
  if (body === null || typeof body !== 'object') return false
  return typeof (body as Record<string, unknown>).success === 'boolean'
}

/** 解包响应体：成功返回业务 data，失败抛出 ApiError */
function unwrapBody<T>(body: unknown, status: number): T {
  if (isResultBody(body)) {
    if (body.code === 200) return (body.data ?? undefined) as T
    throw new ApiError(body.message ?? '请求失败', { status, code: body.code, data: body.data })
  }
  if (isApiResponseBody(body)) {
    if (body.success) return (body.data ?? undefined) as T
    throw new ApiError(body.message || '请求失败', { status, code: body.errorCode, data: body.data })
  }
  // 非标准包装（如二进制、裸字符串），原样返回
  return body as T
}

/* ------------------------------------------------------------------ */
/* 401 静默刷新                                                        */
/* ------------------------------------------------------------------ */

type RefreshWaiter = { resolve: (token: string) => void; reject: (error: unknown) => void }

let isRefreshing = false
let refreshWaiters: RefreshWaiter[] = []

/** 登出回调（由应用层注册，例如跳转登录页） */
let unauthorizedHandler: (() => void) | null = null

/** 注册“认证失效”回调（401 且刷新失败时触发） */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

/** 使用 refreshToken 换取新 token，并处理并发请求等待 */
async function refreshAccessToken(refreshToken: string): Promise<string> {
  if (isRefreshing) {
    return new Promise<string>((resolve, reject) => {
      refreshWaiters.push({ resolve, reject })
    })
  }
  isRefreshing = true
  try {
    // 使用裸 axios，避免触发本层拦截造成递归
    const res = await axios.post(`${API_BASE_URL}/users/refresh`, null, { params: { refreshToken } })
    const body = res.data as Result<LoginResponseDTO>
    if (body.code !== 200 || !body.data?.accessToken) {
      throw new ApiError(body.message ?? 'Token 刷新失败', { status: res.status, code: body.code })
    }
    setAuthTokens({
      accessToken: body.data.accessToken,
      refreshToken: body.data.refreshToken ?? refreshToken,
      expiresIn: body.data.expiresIn,
    })
    const token = body.data.accessToken
    refreshWaiters.forEach((w) => w.resolve(token))
    refreshWaiters = []
    return token
  } catch (error) {
    refreshWaiters.forEach((w) => w.reject(error))
    refreshWaiters = []
    throw error
  } finally {
    isRefreshing = false
  }
}

/* ------------------------------------------------------------------ */
/* axios 实例与拦截器                                                  */
/* ------------------------------------------------------------------ */

export const service: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
})

// 请求拦截：注入 Bearer token
service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getAccessToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

// 响应拦截：401 刷新重放 / 其余错误规范化
service.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (error instanceof AxiosError && error.response?.status === 401) {
      const config = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
      const refreshToken = getRefreshToken()
      const isRefreshCall = config?.url?.includes('/users/refresh')
      if (config && !config._retried && refreshToken && !isRefreshCall) {
        config._retried = true
        try {
          const newToken = await refreshAccessToken(refreshToken)
          config.headers.set('Authorization', `Bearer ${newToken}`)
          return service(config)
        } catch (refreshError) {
          clearAuthTokens()
          unauthorizedHandler?.()
          return Promise.reject(normalizeError(refreshError))
        }
      }
      clearAuthTokens()
      unauthorizedHandler?.()
    }
    return Promise.reject(normalizeError(error))
  },
)

/* ------------------------------------------------------------------ */
/* 类型化请求辅助                                                      */
/* ------------------------------------------------------------------ */

export interface RequestOptions extends Omit<AxiosRequestConfig, 'url' | 'method' | 'data'> {}

/** 发送请求并解包为业务数据；返回原始响应请用 service */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await service.request<T>(config)
  return unwrapBody<T>(response.data, response.status)
}

/** 常用 HTTP 方法的类型化封装（页面/Store 层直接使用） */
export const http = {
  get: <T>(url: string, options?: RequestOptions) =>
    request<T>({ ...options, url, method: 'GET' }),
  post: <T>(url: string, data?: unknown, options?: RequestOptions) =>
    request<T>({ ...options, url, method: 'POST', data }),
  put: <T>(url: string, data?: unknown, options?: RequestOptions) =>
    request<T>({ ...options, url, method: 'PUT', data }),
  patch: <T>(url: string, data?: unknown, options?: RequestOptions) =>
    request<T>({ ...options, url, method: 'PATCH', data }),
  del: <T>(url: string, options?: RequestOptions) =>
    request<T>({ ...options, url, method: 'DELETE' }),
}

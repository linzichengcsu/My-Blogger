/**
 * 认证接口（用户管理 - 登录/注册/刷新/登出）。
 * 对应 openapi.yaml：/api/users/login、/register、/refresh
 */
import { clearAuthTokens, getAccessToken, getRefreshToken, http, setAuthTokens } from './request'
import type { LoginResponseDTO, UserLoginRequest, UserPasswordCodeRequest, UserPasswordResetRequest, UserRegisterRequest, UserDetailDTO } from '../types/user'

/** 用户登录；成功后自动保存 token 到 localStorage */
export async function login(data: UserLoginRequest): Promise<LoginResponseDTO> {
  const res = await http.post<LoginResponseDTO>('/users/login', data)
  if (res.accessToken) {
    setAuthTokens({
      accessToken: res.accessToken,
      refreshToken: res.refreshToken,
      expiresIn: res.expiresIn,
    })
  }
  return res
}

/** 注册（前端仅限普通用户） */
export function register(data: UserRegisterRequest): Promise<UserDetailDTO> {
  return http.post<UserDetailDTO>('/users/register', data)
}

/** 忘记密码第一步：发送邮箱验证码（无需登录；后端有 60 秒发送冷却） */
export function sendPasswordResetCode(data: UserPasswordCodeRequest): Promise<void> {
  return http.post<void>('/users/forgot-password/send-code', data)
}

/** 忘记密码第二步：凭邮箱验证码重置密码（无需登录） */
export function forgotPassword(data: UserPasswordResetRequest): Promise<void> {
  return http.post<void>('/users/forgot-password', data)
}

/** 显式刷新 Token（返回新的 token 信息，不自动覆盖存储） */
export function refreshToken(refreshToken: string): Promise<LoginResponseDTO> {
  return http.post<LoginResponseDTO>('/users/refresh', null, { params: { refreshToken } })
}

/** 登出：清理本地登录态（后端无登出接口） */
export function logout(): void {
  clearAuthTokens()
}

/** 当前登录态 */
export function isLoggedIn(): boolean {
  return Boolean(getAccessToken())
}

/** 当前 refresh token（供业务方按需使用） */
export function getCurrentRefreshToken(): string | null {
  return getRefreshToken()
}

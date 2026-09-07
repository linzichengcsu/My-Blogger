/**
 * 用户模块类型（对齐 openapi.yaml 用户管理相关 schema）。
 */

/** 用户状态 */
export const USER_STATUS = ['ACTIVE', 'INACTIVE', 'LOCKED'] as const
export type UserStatus = (typeof USER_STATUS)[number]

/** 用户角色 */
export const USER_ROLE = ['USER', 'ADMIN', 'SUPER_ADMIN'] as const
export type UserRole = (typeof USER_ROLE)[number]

/** 用户详情（完整信息，含管理字段） */
export interface UserDetailDTO {
  id?: number
  createdAt?: string
  updatedAt?: string
  username?: string
  email?: string
  displayName?: string
  avatar?: string
  bio?: string
  status?: UserStatus
  role?: UserRole
  lastLoginAt?: string
  articleCount?: number
  commentCount?: number
  favoriteCount?: number
}

/** 用户公开资料（对外展示用） */
export interface UserProfileDTO {
  id?: number
  username?: string
  displayName?: string
  avatar?: string
  bio?: string
  createdAt?: string
  articleCount?: number
  commentCount?: number
  followerCount?: number
}

/** 用户活动统计 */
export interface UserActivityDTO {
  userId?: number
  username?: string
  displayName?: string
  articleCount?: number
  commentCount?: number
  likeReceived?: number
  lastActiveAt?: string
}

/** 登录响应 */
export interface LoginResponseDTO {
  accessToken?: string
  refreshToken?: string
  tokenType?: string
  /** 有效时长（秒） */
  expiresIn?: number
  user?: UserDetailDTO
}

/** 登录请求 */
export interface UserLoginRequest {
  username: string
  password: string
}

/** 注册请求（前端仅限普通用户 USER） */
export interface UserRegisterRequest {
  username: string
  email: string
  password: string
  displayName?: string
  role?: UserRole
}

/** 更新用户信息请求 */
export interface UserUpdateRequest {
  displayName?: string
  bio?: string
  avatar?: string
}

/** 修改密码请求（oldPassword 为当前密码） */
export interface UserPasswordChangeRequest {
  oldPassword: string
  newPassword: string
}

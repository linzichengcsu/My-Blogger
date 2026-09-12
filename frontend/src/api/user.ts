/**
 * 用户管理接口（当前用户 / 用户 CRUD / 状态管理 / 统计搜索）。
 * 对应 openapi.yaml：/api/users/**
 * 权限提示：SecurityConfig 对 GET /api/users/** 放行，但方法级 @PreAuthorize
 * 仍会拦截（getUserDetail/getUserActivity/全部状态与统计接口需 ADMIN），
 * 修改类操作（PUT/POST/DELETE）一律需要认证。
 */
import { http } from './request'
import type { PageQuery, PageResponse } from '../types/common'
import type {
  UserActivityDTO,
  UserDetailDTO,
  UserPasswordChangeRequest,
  UserProfileDTO,
  UserUpdateRequest,
} from '../types/user'

/* ---------------- 当前登录用户（仅凭 Token） ---------------- */

/** 获取当前登录用户个人信息（需登录，无 Token 时后端会拒绝） */
export function getMyInfo(): Promise<UserDetailDTO> {
  return http.get<UserDetailDTO>('/users/me')
}

/** 修改当前登录用户个人信息（需登录） */
export function updateMyInfo(data: UserUpdateRequest): Promise<UserDetailDTO> {
  return http.put<UserDetailDTO>('/users/me', data)
}

/** 修改当前登录用户密码（需登录） */
export function changeMyPassword(data: UserPasswordChangeRequest): Promise<void> {
  return http.post<void>('/users/me/change-password', data)
}

/* ---------------- 用户 CRUD ---------------- */

/** 获取用户详情 */
export function getUserDetail(userId: number): Promise<UserDetailDTO> {
  return http.get<UserDetailDTO>(`/users/${userId}`)
}

/** 更新用户信息（管理员） */
export function updateUser(userId: number, data: UserUpdateRequest): Promise<UserDetailDTO> {
  return http.put<UserDetailDTO>(`/users/${userId}`, data)
}

/** 删除用户；softDelete=true 软删除（禁用），false 物理删除 */
export function deleteUser(userId: number, softDelete = true): Promise<void> {
  return http.del<void>(`/users/${userId}`, { params: { softDelete } })
}

/** 获取用户公开资料（公开） */
export function getUserProfile(userId: number): Promise<UserProfileDTO> {
  return http.get<UserProfileDTO>(`/users/${userId}/profile`)
}

/** 获取用户活动统计（需管理员） */
export function getUserActivity(userId: number): Promise<UserActivityDTO> {
  return http.get<UserActivityDTO>(`/users/${userId}/activity`)
}

/** 通过用户名获取用户详情（公开） */
export function getUserDetailByUsername(username: string): Promise<UserDetailDTO> {
  return http.get<UserDetailDTO>(`/users/username/${encodeURIComponent(username)}`)
}

/* ---------------- 用户状态管理（管理员） ---------------- */

/** 启用用户 */
export function activateUser(userId: number): Promise<void> {
  return http.post<void>(`/users/${userId}/activate`)
}

/** 禁用用户 */
export function deactivateUser(userId: number): Promise<void> {
  return http.post<void>(`/users/${userId}/deactivate`)
}

/** 锁定用户 */
export function lockUser(userId: number): Promise<void> {
  return http.post<void>(`/users/${userId}/lock`)
}

/** 解锁用户 */
export function unlockUser(userId: number): Promise<void> {
  return http.post<void>(`/users/${userId}/unlock`)
}

/** 指定用户修改密码（管理员） */
export function changeUserPassword(userId: number, data: UserPasswordChangeRequest): Promise<void> {
  return http.post<void>(`/users/${userId}/change-password`, data)
}

/* ---------------- 列表 / 搜索 / 统计 ---------------- */

/** 分页获取所有用户（需管理员；后端仅支持 page/size/sortBy，sortDirection 不生效） */
export function getAllUsers(query: PageQuery = {}): Promise<PageResponse<UserProfileDTO>> {
  return http.get<PageResponse<UserProfileDTO>>('/users', { params: query })
}

/** 搜索用户（需登录；后端仅支持 keyword/page/size） */
export function searchUsers(
  keyword: string,
  query: Pick<PageQuery, 'page' | 'size'> = {},
): Promise<PageResponse<UserProfileDTO>> {
  return http.get<PageResponse<UserProfileDTO>>('/users/search', { params: { keyword, ...query } })
}

/** 获取系统总用户数（需管理员） */
export function getTotalUserCount(): Promise<number> {
  return http.get<number>('/users/stats/total')
}

/** 获取当前活跃用户数（需管理员） */
export function countActiveUsers(): Promise<number> {
  return http.get<number>('/users/stats/active')
}

/** 获取最近活跃用户列表（需管理员，limit 取值 1-50） */
export function getRecentlyActiveUsers(limit = 10): Promise<UserActivityDTO[]> {
  return http.get<UserActivityDTO[]>('/users/stats/recently-active', { params: { limit } })
}

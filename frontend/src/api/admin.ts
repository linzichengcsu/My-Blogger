/**
 * 管理后台接口（看板统计等）。
 * 对应 openapi.yaml：/api/admin/**
 * 注意：该模块接口使用 ApiResponse<T> 包装（success/message/data），
 * request 层已统一解包，此处直接得到业务数据。
 */
import { http } from './request'
import type { DashboardStatsDTO } from '../types/dashboard'

/** 获取看板统计数据（仅管理员） */
export function getDashboardStats(): Promise<DashboardStatsDTO> {
  return http.get<DashboardStatsDTO>('/admin/dashboard/stats')
}

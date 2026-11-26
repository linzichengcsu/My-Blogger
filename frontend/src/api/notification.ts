/**
 * 站内通知接口。
 * 对应 openapi.yaml：/api/notifications/**
 */
import { http } from './request'
import type { PageResponse } from '../types/common'
import type { NotificationCountDTO, NotificationDTO } from '../types/notification'

/** 获取通知列表（按时间倒序） */
export function listNotifications(
  query: { page?: number; size?: number } = {},
): Promise<PageResponse<NotificationDTO>> {
  return http.get<PageResponse<NotificationDTO>>('/notifications', { params: query })
}

/** 获取未读数 + 总数（头部铃铛红点用） */
export function getNotificationCounts(): Promise<NotificationCountDTO> {
  return http.get<NotificationCountDTO>('/notifications/count')
}

/** 标记单条已读 */
export function markNotificationAsRead(id: number): Promise<void> {
  return http.post<void>(`/notifications/${id}/read`)
}

/** 批量标记已读 */
export function batchMarkNotificationsAsRead(ids: number[]): Promise<number> {
  return http.post<number>('/notifications/read-batch', { ids })
}

/** 全部标记已读 */
export function markAllNotificationsAsRead(): Promise<number> {
  return http.post<number>('/notifications/read-all')
}

/** 删除单条 */
export function deleteNotification(id: number): Promise<void> {
  return http.del<void>(`/notifications/${id}`)
}

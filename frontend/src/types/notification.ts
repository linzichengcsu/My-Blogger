/**
 * 通知模块类型。
 */
import type { UserProfileDTO } from './user'

/** 通知类型枚举（后端 NotificationType） */
export type NotificationType = 'COMMENT' | 'REPLY' | 'LIKE_ARTICLE' | 'LIKE_COMMENT' | 'SYSTEM'

/** 通知卡片 */
export interface NotificationDTO {
  id?: number
  type?: NotificationType
  title?: string
  content?: string
  /** 触发者，系统通知时为 null */
  sender?: UserProfileDTO | null
  articleId?: number | null
  commentId?: number | null
  isRead?: boolean
  createdAt?: string
}

/** 未读计数 + 总数 */
export interface NotificationCountDTO {
  unread: number
  total: number
}

/** 批量标记已读请求体 */
export interface NotificationBatchReadRequest {
  ids: number[]
}

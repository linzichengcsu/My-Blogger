/**
 * 通知全局 store。
 * - 管理未读数量、通知列表
 * - 提供 fetchCounts / fetchList / markAllRead 等 action
 * - 供 NotificationBell 及其他需要感知通知变化的组件复用
 */
import { create } from 'zustand'
import * as notificationApi from '@/api/notification'
import type { NotificationCountDTO, NotificationDTO } from '@/types'

interface NotificationState {
  counts: NotificationCountDTO
  notifications: NotificationDTO[]
  loading: boolean
  /** 上次成功拉取未读数的时间戳（用于节流） */
  lastCountFetch: number

  fetchCounts: (force?: boolean) => Promise<void>
  fetchList: () => Promise<void>
  markAllRead: () => Promise<void>
  markOneRead: (id: number) => Promise<void>
  /** 外部触发未读数刷新（例如评论/点赞后） */
  refresh: () => void
}

/** 未读数最小刷新间隔（毫秒），避免频繁请求 */
const COUNT_THROTTLE_MS = 10_000

export const useNotificationStore = create<NotificationState>((set, get) => ({
  counts: { unread: 0, total: 0 },
  notifications: [],
  loading: false,
  lastCountFetch: 0,

  fetchCounts: async (force = false) => {
    const now = Date.now()
    if (!force && now - get().lastCountFetch < COUNT_THROTTLE_MS) return
    try {
      const counts = await notificationApi.getNotificationCounts()
      set({ counts, lastCountFetch: now })
    } catch {
      // 未登录或网络错误时静默忽略
    }
  },

  fetchList: async () => {
    set({ loading: true })
    try {
      const page = await notificationApi.listNotifications({ page: 0, size: 10 })
      set({ notifications: page.content ?? [] })
    } catch {
      // 静默忽略
    } finally {
      set({ loading: false })
    }
  },

  markAllRead: async () => {
    await notificationApi.markAllNotificationsAsRead()
    set((s) => ({
      counts: { ...s.counts, unread: 0 },
      notifications: s.notifications.map((n) => ({ ...n, isRead: true })),
    }))
  },

  markOneRead: async (id) => {
    try {
      await notificationApi.markNotificationAsRead(id)
      set((s) => ({
        counts: { ...s.counts, unread: Math.max(0, s.counts.unread - 1) },
        notifications: s.notifications.map((n) => (n.id === id ? { ...n, isRead: true } : n)),
      }))
    } catch {
      // 静默忽略
    }
  },

  refresh: () => {
    void get().fetchCounts(true)
  },
}))

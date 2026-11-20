/**
 * 全站通知铃铛（吸顶导航右侧）。
 * 未读数量徽标 + 弹出 Popover 展示最近 5 条通知。
 */
import { useCallback, useEffect, useRef, useState } from 'react'
import { Avatar, Badge, Button, List, Popover, Space, Typography, message } from 'antd'
import { BellOutlined, CheckOutlined, ClearOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import * as notificationApi from '@/api/notification'
import type { NotificationCountDTO, NotificationDTO } from '@/types'
import { fileUrl } from '@/utils'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const { Text } = Typography

/** 通知类型的图标前缀（纯文本 emoji，比引入 @ant-design/icons 更轻量） */
const TYPE_ICON: Record<string, string> = {
  COMMENT: '💬',
  REPLY: '↩️',
  LIKE_ARTICLE: '❤️',
  LIKE_COMMENT: '👍',
  SYSTEM: '📢',
}

export default function NotificationBell() {
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [counts, setCounts] = useState<NotificationCountDTO>({ unread: 0, total: 0 })
  const [notifications, setNotifications] = useState<NotificationDTO[]>([])
  const pollRef = useRef<number | null>(null)

  /** 拉取未读数（轻量，每次打开 Popover 和每 60s 轮询） */
  const fetchCounts = useCallback(async () => {
    try {
      setCounts(await notificationApi.getNotificationCounts())
    } catch {
      // 未登录或网络错误时静默忽略
    }
  }, [])

  /** 拉取最近通知列表（打开 Popover 时调用） */
  const fetchList = useCallback(async () => {
    setLoading(true)
    try {
      const page = await notificationApi.listNotifications({ page: 0, size: 10 })
      setNotifications(page.content ?? [])
    } catch {
      // 静默忽略
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchCounts()
    // 60s 轮询未读数
    pollRef.current = window.setInterval(fetchCounts, 60_000)
    return () => {
      if (pollRef.current) window.clearInterval(pollRef.current)
    }
  }, [fetchCounts])

  const handleOpenChange = (nextOpen: boolean) => {
    setOpen(nextOpen)
    if (nextOpen) {
      fetchList()
    }
  }

  const handleMarkAllRead = async () => {
    try {
      await notificationApi.markAllNotificationsAsRead()
      message.success('已全部标记为已读')
      setCounts((c) => ({ ...c, unread: 0 }))
      setNotifications((list) => list.map((n) => ({ ...n, isRead: true })))
    } catch {
      message.error('操作失败')
    }
  }

  const handleItemClick = async (n: NotificationDTO) => {
    // 标记单条已读
    if (!n.isRead && n.id) {
      try {
        await notificationApi.markNotificationAsRead(n.id)
        setCounts((c) => ({ ...c, unread: Math.max(0, c.unread - 1) }))
        setNotifications((list) => list.map((x) => (x.id === n.id ? { ...x, isRead: true } : x)))
      } catch {
        // 静默忽略
      }
    }
    // 跳转到关联文章（前端可根据 articleId 路由）
    if (n.articleId) {
      window.open(`/articles/${n.articleId}`, '_blank', 'noopener')
    }
    setOpen(false)
  }

  const content = (
    <div style={{ width: 360 }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 8,
          paddingBottom: 8,
          borderBottom: '1px solid #f0f0f0',
        }}
      >
        <Text strong>通知</Text>
        <Space size={4}>
          <Button
            size="small"
            type="text"
            icon={<CheckOutlined />}
            disabled={counts.unread === 0}
            onClick={handleMarkAllRead}
          >
            全部已读
          </Button>
          <Button
            size="small"
            type="text"
            icon={<ClearOutlined />}
            onClick={() => setOpen(false)}
          />
        </Space>
      </div>

      <List
        size="small"
        loading={loading}
        dataSource={notifications}
        locale={{ emptyText: '暂无通知' }}
        renderItem={(n) => (
          <List.Item
            onClick={() => handleItemClick(n)}
            style={{
              cursor: 'pointer',
              padding: '10px 8px',
              background: n.isRead ? 'transparent' : '#fff7e6',
            }}
          >
            <List.Item.Meta
              avatar={
                n.sender ? (
                  <Avatar src={fileUrl(n.sender.avatar)} style={{ backgroundColor: '#fa541c' }}>
                    {(n.sender.displayName ?? n.sender.username ?? '?').slice(0, 1)}
                  </Avatar>
                ) : (
                  <Avatar style={{ backgroundColor: '#878787' }}>
                    {TYPE_ICON[n.type ?? 'SYSTEM']}
                  </Avatar>
                )
              }
              title={
                <span style={{ fontSize: 13 }}>
                  {TYPE_ICON[n.type ?? '']} {n.title ?? '通知'}
                  {!n.isRead && (
                    <span
                      style={{
                        display: 'inline-block',
                        width: 6,
                        height: 6,
                        borderRadius: '50%',
                        background: '#fa541c',
                        marginLeft: 6,
                        verticalAlign: 'middle',
                      }}
                    />
                  )}
                </span>
              }
              description={
                <div style={{ fontSize: 12, color: '#8c8c8c' }}>
                  <div
                    style={{
                      color: '#595959',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      display: '-webkit-box',
                      WebkitLineClamp: 2,
                      WebkitBoxOrient: 'vertical',
                      marginBottom: 2,
                    }}
                  >
                    {n.content}
                  </div>
                  <span>{n.createdAt ? dayjs(n.createdAt).fromNow() : ''}</span>
                </div>
              }
            />
          </List.Item>
        )}
      />
    </div>
  )

  return (
    <Popover
      open={open}
      onOpenChange={handleOpenChange}
      content={content}
      trigger="click"
      placement="bottomRight"
      arrow={false}
    >
      <Badge count={counts.unread} overflowCount={99} size="small">
        <Button
          type="text"
          icon={<BellOutlined style={{ fontSize: 18 }} />}
          title="通知"
        />
      </Badge>
    </Popover>
  )
}

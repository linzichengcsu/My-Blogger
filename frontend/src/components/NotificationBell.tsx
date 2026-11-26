/**
 * 全站通知铃铛（吸顶导航右侧）。
 * 未读数量徽标 + 弹出 Popover 展示最近 5 条通知。
 *
 * 通知状态已迁移为 Zustand notificationStore（stores/notificationStore.ts）。
 */
import { useCallback, useEffect, useRef, useState } from 'react'
import { Avatar, Badge, Button, List, Popover, Space, Typography, message } from 'antd'
import { BellOutlined, CheckOutlined, ClearOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import { useNotificationStore } from '@/stores'
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
  const fetchCounts = useNotificationStore((s) => s.fetchCounts)
  const fetchList = useNotificationStore((s) => s.fetchList)
  const markAllRead = useNotificationStore((s) => s.markAllRead)
  const markOneRead = useNotificationStore((s) => s.markOneRead)
  const counts = useNotificationStore((s) => s.counts)
  const notifications = useNotificationStore((s) => s.notifications)
  const loading = useNotificationStore((s) => s.loading)

  const pollRef = useRef<number | null>(null)

  useEffect(() => {
    fetchCounts(true)
    // 60s 轮询未读数
    pollRef.current = window.setInterval(() => fetchCounts(true), 60_000)
    return () => {
      if (pollRef.current) window.clearInterval(pollRef.current)
    }
  }, [fetchCounts])

  const handleOpenChange = (nextOpen: boolean) => {
    setOpen(nextOpen)
    if (nextOpen) {
      void fetchList()
    }
  }

  const handleMarkAllRead = async () => {
    try {
      await markAllRead()
      message.success('已全部标记为已读')
    } catch {
      message.error('操作失败')
    }
  }

  const handleItemClick = useCallback(async (n: typeof notifications[number]) => {
    if (!n.isRead && n.id) {
      void markOneRead(n.id)
    }
    if (n.articleId) {
      window.open(`/articles/${n.articleId}`, '_blank', 'noopener')
    }
    setOpen(false)
  }, [markOneRead])

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

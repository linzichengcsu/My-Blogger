/**
 * 文章列表卡片（微博信息流风格：左封面 + 右标题摘要）。
 * 首页、用户主页等所有文章列表复用本组件，避免重复实现。
 */
import { Avatar, Card, Space, Tag, Typography } from 'antd'
import { CommentOutlined, LikeOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import type { ArticleListItemDTO } from '@/types'
import { ROUTES } from '@/constants/routes'
import { displayName, fileUrl, fromNow, truncate } from '@/utils'

const { Text } = Typography

export default function ArticleCard({ article }: { article: ArticleListItemDTO }) {
  const author = article.author
  return (
    <Card hoverable size="small" styles={{ body: { padding: 16 } }} style={{ marginBottom: 16 }}>
      <div style={{ display: 'flex', gap: 16 }}>
        {article.coverImage && (
          <Link to={ROUTES.article(article.id ?? '')} style={{ flex: '0 0 200px' }}>
            <img
              src={fileUrl(article.coverImage)}
              alt={article.title}
              style={{ width: 200, height: 124, objectFit: 'cover', borderRadius: 8 }}
            />
          </Link>
        )}
        <div style={{ flex: 1, minWidth: 0 }}>
          <Link to={ROUTES.article(article.id ?? '')}>
            <h3 className="article-card-title">{article.title}</h3>
          </Link>
          <p className="text-clamp-2" style={{ color: '#666', marginBottom: 12 }}>
            {truncate(article.summary, 120) || '这个作者很懒，还没有写摘要~'}
          </p>
          <Space size={8} wrap>
            {author && (
              <Link to={ROUTES.user(author.id ?? '')}>
                <Space size={6}>
                  <Avatar size={20} src={fileUrl(author.avatar)}>
                    {displayName(author.displayName, author.username).slice(0, 1)}
                  </Avatar>
                  <Text type="secondary">{displayName(author.displayName, author.username)}</Text>
                </Space>
              </Link>
            )}
            <Text type="secondary">{fromNow(article.createdAt)}</Text>
            {article.categories?.slice(0, 2).map((c) => (
              <Tag key={c.id} color="orange" style={{ marginInlineEnd: 0 }}>
                {c.name}
              </Tag>
            ))}
            <span style={{ marginLeft: 'auto' }}>
              <Space size={12}>
                <Text type="secondary">
                  <LikeOutlined /> {article.likeCount ?? 0}
                </Text>
                <Text type="secondary">
                  <CommentOutlined /> {article.commentCount ?? 0}
                </Text>
              </Space>
            </span>
          </Space>
        </div>
      </div>
    </Card>
  )
}

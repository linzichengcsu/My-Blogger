/**
 * 文章详情：正文（Markdown）+ 点赞 + 评论区。
 * 评论接口需要登录，未登录显示引导；评论/回复项作为文件内组件，不再拆分子文件。
 */
import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  Avatar,
  Button,
  Card,
  Divider,
  Empty,
  Input,
  Pagination,
  Popconfirm,
  Skeleton,
  Space,
  Tag,
  Typography,
  message,
} from 'antd'
import {
  CommentOutlined,
  DeleteOutlined,
  LikeFilled,
  LikeOutlined,
  SendOutlined,
  StarOutlined,
} from '@ant-design/icons'
import { Link, useNavigate, useParams } from 'react-router-dom'
import MarkdownView from '@/components/MarkdownView'
import {
  createComment,
  deleteComment,
  getArticleById,
  getCommentReplies,
  getTopLevelComments,
  likeArticle,
  viewArticle,
} from '@/api'
import { ApiError } from '@/api/request'
import { useAuth } from '@/context/AuthContext'
import type { ArticleDetailDTO, CommentDTO, CommentReplyDTO, PageResponse } from '@/types'
import { ROUTES } from '@/constants/routes'
import { displayName, fileUrl, formatDateTime, fromNow } from '@/utils'

const { Title, Text, Paragraph } = Typography

/* ---------------- 评论 / 回复项（文件内组件） ---------------- */

/** 嵌套回复（CommentDTO）与扁平回复（CommentReplyDTO）的公共结构 */
type ReplyView = Pick<CommentReplyDTO, 'id' | 'content' | 'commenter' | 'replyToUser' | 'createdAt'>

function ReplyItem({ reply }: { reply: ReplyView }) {
  return (
    <div style={{ display: 'flex', gap: 8, padding: '6px 0' }}>
      <Avatar size={26} src={fileUrl(reply.commenter?.avatar)}>
        {displayName(reply.commenter?.displayName, reply.commenter?.username).slice(0, 1)}
      </Avatar>
      <div>
        <Space size={6} wrap>
          <Text strong>{displayName(reply.commenter?.displayName, reply.commenter?.username)}</Text>
          {reply.replyToUser && <Text type="secondary">回复 {displayName(reply.replyToUser.displayName, reply.replyToUser.username)}</Text>}
          <Text type="secondary" style={{ fontSize: 12 }}>{fromNow(reply.createdAt)}</Text>
        </Space>
        <div style={{ color: '#333' }}>{reply.content}</div>
      </div>
    </div>
  )
}

function CommentItem({
  comment,
  articleId,
  canManage,
  reload,
}: {
  comment: CommentDTO
  articleId: number
  canManage: boolean
  reload: () => void
}) {
  const { user, isAdmin } = useAuth()
  const [showEditor, setShowEditor] = useState(false)
  const [extraReplies, setExtraReplies] = useState<CommentReplyDTO[]>([])
  const [replyLoading, setReplyLoading] = useState(false)
  const [sending, setSending] = useState(false)
  const [text, setText] = useState('')

  const owner = comment.commenter?.id === user?.id
  const replies = comment.replies?.length ? comment.replies : []

  const loadMoreReplies = async () => {
    setReplyLoading(true)
    try {
      const res = await getCommentReplies(comment.id!, { size: 50 })
      setExtraReplies(res.content)
    } finally {
      setReplyLoading(false)
    }
  }

  const sendReply = async () => {
    if (!text.trim()) return
    setSending(true)
    try {
      await createComment(articleId, { content: text.trim(), parentCommentId: comment.id })
      message.success('回复成功')
      setText('')
      setShowEditor(false)
      reload()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '回复失败')
    } finally {
      setSending(false)
    }
  }

  return (
    <div style={{ display: 'flex', gap: 12, padding: '14px 0' }}>
      <Link to={ROUTES.user(comment.commenter?.id ?? '')}>
        <Avatar src={fileUrl(comment.commenter?.avatar)}>
          {displayName(comment.commenter?.displayName, comment.commenter?.username).slice(0, 1)}
        </Avatar>
      </Link>
      <div style={{ flex: 1 }}>
        <Space size={8} wrap>
          <Text strong>{displayName(comment.commenter?.displayName, comment.commenter?.username)}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>{fromNow(comment.createdAt)}</Text>
        </Space>
        <Paragraph style={{ margin: '4px 0 6px' }}>{comment.content}</Paragraph>
        <Space size={16}>
          <a onClick={() => setShowEditor((v) => !v)}>
            <CommentOutlined /> 回复
          </a>
          {(canManage || owner || isAdmin) && (
            <Popconfirm title="确认删除这条评论？" onConfirm={async () => { await deleteComment(comment.id!); message.success('已删除'); reload() }}>
              <a style={{ color: '#ff4d4f' }}><DeleteOutlined /> 删除</a>
            </Popconfirm>
          )}
        </Space>

        {replies.map((r) => <ReplyItem key={r.id} reply={r} />)}
        {extraReplies.map((r) => <ReplyItem key={r.id} reply={r} />)}

        {(comment.replyCount ?? 0) > replies.length + extraReplies.length && (
          <Button type="link" size="small" loading={replyLoading} onClick={loadMoreReplies} style={{ padding: '4px 0' }}>
            展开全部 {comment.replyCount} 条回复
          </Button>
        )}

        {showEditor && (
          <Space.Compact style={{ width: '100%', marginTop: 8 }}>
            <Input.TextArea
              value={text}
              autoSize={{ minRows: 1, maxRows: 4 }}
              placeholder={`回复 ${displayName(comment.commenter?.displayName, comment.commenter?.username)}…`}
              onChange={(e) => setText(e.target.value)}
            />
            <Button type="primary" icon={<SendOutlined />} loading={sending} onClick={sendReply}>
              回复
            </Button>
          </Space.Compact>
        )}
      </div>
    </div>
  )
}

/* ---------------- 文章详情页 ---------------- */

export default function ArticleDetailPage() {
  const { id } = useParams<{ id: string }>()
  const articleId = Number(id)
  const navigate = useNavigate()
  const { isLoggedIn, user, isAdmin } = useAuth()

  const [article, setArticle] = useState<ArticleDetailDTO | null>(null)
  const [loading, setLoading] = useState(true)
  const [comments, setComments] = useState<PageResponse<CommentDTO> | null>(null)
  const [commentPage, setCommentPage] = useState(0)
  const [commentText, setCommentText] = useState('')
  const [sending, setSending] = useState(false)
  const [liking, setLiking] = useState(false)

  const loadArticle = useCallback(async () => {
    setLoading(true)
    try {
      setArticle(await getArticleById(articleId))
    } finally {
      setLoading(false)
    }
  }, [articleId])

  const loadComments = useCallback(async () => {
    if (!isLoggedIn) return
    try {
      setComments(await getTopLevelComments(articleId, { page: commentPage, size: 10, sortBy: 'createdAt', sortDirection: 'desc' }))
    } catch {
      setComments(null)
    }
  }, [articleId, commentPage, isLoggedIn])

  useEffect(() => { void loadArticle() }, [loadArticle])
  useEffect(() => { void loadComments() }, [loadComments])

  // 记录浏览（接口要求登录，失败静默）
  useEffect(() => {
    if (isLoggedIn) viewArticle(articleId).catch(() => undefined)
  }, [articleId, isLoggedIn])

  const onLike = async () => {
    if (!isLoggedIn) {
      message.warning('请先登录后再点赞')
      navigate(ROUTES.login, { state: { from: ROUTES.article(articleId) } })
      return
    }
    setLiking(true)
    try {
      await likeArticle(articleId)
      setArticle((prev) =>
        prev
          ? { ...prev, isLiked: !prev.isLiked, likeCount: (prev.likeCount ?? 0) + (prev.isLiked ? -1 : 1) }
          : prev,
      )
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '操作失败')
    } finally {
      setLiking(false)
    }
  }

  const sendComment = async () => {
    if (!commentText.trim()) return
    setSending(true)
    try {
      await createComment(articleId, { content: commentText.trim() })
      message.success('评论发表成功')
      setCommentText('')
      setCommentPage(0)
      void loadComments()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '评论失败')
    } finally {
      setSending(false)
    }
  }

  if (loading) {
    return <Card><Skeleton active paragraph={{ rows: 10 }} /></Card>
  }
  if (!article) {
    return <Card><Empty description="文章不存在或已被删除" /></Card>
  }

  return (
    <>
      <Card style={{ marginBottom: 16 }}>
        <Title level={2} style={{ marginBottom: 12 }}>{article.title}</Title>
        <Space size={12} wrap style={{ color: '#999', marginBottom: 16 }}>
          {article.author && (
            <Link to={ROUTES.user(article.author.id ?? '')}>
              <Space size={6}>
                <Avatar size={22} src={fileUrl(article.author.avatar)} />
                <Text>{displayName(article.author.displayName, article.author.username)}</Text>
              </Space>
            </Link>
          )}
          <span>发布于 {formatDateTime(article.createdAt)}</span>
          {article.categories?.map((c) => (
            <Tag key={c.id} color="orange" style={{ marginInlineEnd: 0 }}>{c.name}</Tag>
          ))}
        </Space>

        {article.coverImage && (
          <img src={fileUrl(article.coverImage)} alt="cover" style={{ width: '100%', maxHeight: 360, objectFit: 'cover', borderRadius: 8, marginBottom: 20 }} />
        )}

        <MarkdownView content={article.content} />

        <Divider />
        <Space size={24} style={{ justifyContent: 'center', width: '100%', fontSize: 16 }}>
          <Button
            size="large"
            shape="round"
            loading={liking}
            type={article.isLiked ? 'primary' : 'default'}
            danger={article.isLiked}
            icon={article.isLiked ? <LikeFilled /> : <LikeOutlined />}
            onClick={onLike}
          >
            {article.likeCount ?? 0}
          </Button>
          <span style={{ color: '#999' }}><CommentOutlined /> {article.commentCount ?? 0}</span>
          <span style={{ color: '#999' }}><StarOutlined /> {article.favoriteCount ?? 0}</span>
        </Space>
      </Card>

      <Card title={`评论 ${article.commentCount ?? ''}`}>
        {!isLoggedIn ? (
          <Alert
            type="warning"
            showIcon
            message="登录后即可查看和发表评论"
            action={<Button type="primary" size="small" onClick={() => navigate(ROUTES.login, { state: { from: ROUTES.article(articleId) } })}>去登录</Button>}
          />
        ) : (
          <>
            <Space.Compact style={{ width: '100%' }}>
              <Input.TextArea
                value={commentText}
                autoSize={{ minRows: 2, maxRows: 6 }}
                placeholder={`${user?.displayName || user?.username || '我'}，说点什么吧…（1-500 字）`}
                maxLength={500}
                showCount
                onChange={(e) => setCommentText(e.target.value)}
              />
            </Space.Compact>
            <div style={{ textAlign: 'right', marginTop: 8 }}>
              <Button type="primary" icon={<SendOutlined />} loading={sending} onClick={sendComment}>
                发表评论
              </Button>
            </div>
            <Divider style={{ margin: '12px 0' }} />
            {comments && comments.content.length > 0 ? (
              <>
                {comments.content.map((c) => (
                  <CommentItem key={c.id} comment={c} articleId={articleId} canManage={isAdmin} reload={loadComments} />
                ))}
                {comments.totalPages > 1 && (
                  <Pagination
                    style={{ marginTop: 12, textAlign: 'center' }}
                    current={commentPage + 1}
                    pageSize={comments.size}
                    total={comments.totalElements}
                    showSizeChanger={false}
                    onChange={(p) => setCommentPage(p - 1)}
                  />
                )}
              </>
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有评论，快来抢沙发" />
            )}
          </>
        )}
      </Card>
    </>
  )
}

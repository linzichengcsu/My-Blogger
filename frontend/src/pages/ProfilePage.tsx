/**
 * 个人中心 / 用户主页（同一页面按路由区分）：
 * - /profile      当前用户：资料编辑、头像上传、修改密码
 * - /u/:userId    公开主页：用户卡片 + TA 的文章列表
 */
import { useCallback, useEffect, useState } from 'react'
import {
  Avatar,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Form,
  Input,
  Pagination,
  Row,
  Skeleton,
  Space,
  Tabs,
  Tag,
  Typography,
  Upload,
  message,
} from 'antd'
import type { UploadProps } from 'antd'
import { UploadOutlined } from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import ArticleCard from '@/components/ArticleCard'
import {
  changeMyPassword,
  getArticlesByAuthor,
  getUserProfile,
  updateMyInfo,
  uploadFile,
} from '@/api'
import { ApiError } from '@/api/request'
import { useAuth } from '@/context/AuthContext'
import type { ArticleListItemDTO, PageResponse, UserPasswordChangeRequest, UserProfileDTO, UserUpdateRequest } from '@/types'
import { ROUTES } from '@/constants/routes'
import { displayName, fileUrl, formatDate } from '@/utils'

/** customRequest 入参类型，直接从 antd 推导，避免依赖 rc-upload 内部路径 */
type UploadRequestOption = Parameters<NonNullable<UploadProps['customRequest']>>[0]

const { Title, Paragraph, Text } = Typography

/* ---------------- 公开用户主页 ---------------- */

function PublicProfile({ userId }: { userId: number }) {
  const navigate = useNavigate()
  const { user: me } = useAuth()
  const [profile, setProfile] = useState<UserProfileDTO | null>(null)
  const [articles, setArticles] = useState<PageResponse<ArticleListItemDTO> | null>(null)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [p, a] = await Promise.all([
        getUserProfile(userId),
        getArticlesByAuthor(userId, { page, size: 8, sortBy: 'createdAt', sortDirection: 'desc' }),
      ])
      setProfile(p)
      setArticles(a)
    } finally {
      setLoading(false)
    }
  }, [userId, page])

  useEffect(() => { void load() }, [load])

  if (loading) return <Skeleton active />
  if (!profile) return <Card><Empty description="用户不存在" /></Card>

  return (
    <Row gutter={24}>
      <Col xs={24} md={7}>
        <Card style={{ textAlign: 'center' }}>
          <Avatar size={96} src={fileUrl(profile.avatar)} style={{ backgroundColor: '#fa541c' }}>
            {displayName(profile.displayName, profile.username).slice(0, 1)}
          </Avatar>
          <Title level={4} style={{ margin: '12px 0 4px' }}>
            {displayName(profile.displayName, profile.username)}
          </Title>
          <Text type="secondary">@{profile.username}</Text>
          <Paragraph type="secondary" style={{ marginTop: 12 }}>{profile.bio || '这个人很神秘，什么都没有留下'}</Paragraph>
          <Space size={24} style={{ justifyContent: 'center' }}>
            <div><Text strong>{profile.articleCount ?? 0}</Text><br /><Text type="secondary">文章</Text></div>
            <div><Text strong>{profile.commentCount ?? 0}</Text><br /><Text type="secondary">评论</Text></div>
            <div><Text strong>{profile.followerCount ?? 0}</Text><br /><Text type="secondary">粉丝</Text></div>
          </Space>
          <Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0, fontSize: 12 }}>
            加入于 {formatDate(profile.createdAt)}
          </Paragraph>
          {me?.id === userId && (
            <Button style={{ marginTop: 16 }} onClick={() => navigate(ROUTES.profile)}>编辑我的资料</Button>
          )}
        </Card>
      </Col>
      <Col xs={24} md={17}>
        <Title level={5}>TA 的文章</Title>
        {articles?.content.length ? (
          <>
            {articles.content.map((a) => <ArticleCard key={a.id} article={a} />)}
            <div style={{ textAlign: 'center' }}>
              <Pagination current={page + 1} pageSize={articles.size} total={articles.totalElements} showSizeChanger={false} onChange={(p) => setPage(p - 1)} />
            </div>
          </>
        ) : (
          <Card><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂时没有发布文章" /></Card>
        )}
      </Col>
    </Row>
  )
}

/* ---------------- 当前用户个人中心 ---------------- */

function MyProfile() {
  const { user, setUser, refreshUser } = useAuth()
  const [profileForm] = Form.useForm<UserUpdateRequest>()
  const [pwdForm] = Form.useForm<UserPasswordChangeRequest>()
  const [savingProfile, setSavingProfile] = useState(false)
  const [savingPwd, setSavingPwd] = useState(false)
  const [uploading, setUploading] = useState(false)

  useEffect(() => {
    if (user) profileForm.setFieldsValue({ displayName: user.displayName ?? '', bio: user.bio ?? '', avatar: user.avatar ?? '' })
  }, [user, profileForm])

  const customUpload = async (option: UploadRequestOption) => {
    setUploading(true)
    try {
      const res = await uploadFile(option.file as File)
      profileForm.setFieldValue('avatar', res.fileUrl ?? res.fileName ?? '')
      setUser(user ? { ...user, avatar: res.fileUrl ?? res.fileName } : user)
      message.success('头像上传成功，保存资料后生效')
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '上传失败')
    } finally {
      setUploading(false)
    }
  }

  const saveProfile = async (values: UserUpdateRequest) => {
    setSavingProfile(true)
    try {
      await updateMyInfo(values)
      await refreshUser()
      message.success('资料已更新')
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '保存失败')
    } finally {
      setSavingProfile(false)
    }
  }

  const changePassword = async (values: UserPasswordChangeRequest) => {
    setSavingPwd(true)
    try {
      await changeMyPassword(values)
      message.success('密码修改成功')
      pwdForm.resetFields()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '修改失败')
    } finally {
      setSavingPwd(false)
    }
  }

  if (!user) return <Skeleton active />

  return (
    <Row gutter={24}>
      <Col xs={24} md={8}>
        <Card style={{ textAlign: 'center' }}>
          <Avatar size={96} src={fileUrl(user.avatar)} style={{ backgroundColor: '#fa541c' }}>
            {displayName(user.displayName, user.username).slice(0, 1)}
          </Avatar>
          <Title level={4} style={{ margin: '12px 0 0' }}>{displayName(user.displayName, user.username)}</Title>
          <Space style={{ marginTop: 8 }}>
            <Tag color={user.status === 'ACTIVE' ? 'green' : 'default'}>{user.status}</Tag>
            <Tag color="orange">{user.role}</Tag>
          </Space>
          <Descriptions column={1} size="small" style={{ marginTop: 16, textAlign: 'left' }}>
            <Descriptions.Item label="文章">{user.articleCount ?? 0}</Descriptions.Item>
            <Descriptions.Item label="评论">{user.commentCount ?? 0}</Descriptions.Item>
            <Descriptions.Item label="收藏">{user.favoriteCount ?? 0}</Descriptions.Item>
            <Descriptions.Item label="上次登录">{formatDate(user.lastLoginAt)}</Descriptions.Item>
          </Descriptions>
        </Card>
      </Col>
      <Col xs={24} md={16}>
        <Card>
          <Tabs
            items={[
              {
                key: 'profile',
                label: '编辑资料',
                children: (
                  <Form form={profileForm} layout="vertical" style={{ maxWidth: 480 }} onFinish={saveProfile}>
                    <Form.Item label="头像">
                      <Form.Item name="avatar" hidden><Input /></Form.Item>
                      <Upload showUploadList={false} customRequest={customUpload} accept="image/*">
                        <Button icon={<UploadOutlined />} loading={uploading}>上传新头像</Button>
                      </Upload>
                    </Form.Item>
                    <Form.Item label="显示名称" name="displayName" rules={[{ max: 50, message: '不超过 50 个字符' }]}>
                      <Input placeholder="给自己起个昵称" />
                    </Form.Item>
                    <Form.Item label="个人简介" name="bio">
                      <Input.TextArea rows={4} maxLength={200} showCount placeholder="介绍一下自己吧" />
                    </Form.Item>
                    <Form.Item>
                      <Button type="primary" htmlType="submit" loading={savingProfile}>保存资料</Button>
                    </Form.Item>
                  </Form>
                ),
              },
              {
                key: 'password',
                label: '修改密码',
                children: (
                  <Form form={pwdForm} layout="vertical" style={{ maxWidth: 480 }} onFinish={changePassword}>
                    <Form.Item label="当前密码" name="oldPassword" rules={[{ required: true, message: '请输入当前密码' }]}>
                      <Input.Password placeholder="当前密码" />
                    </Form.Item>
                    <Form.Item
                      label="新密码"
                      name="newPassword"
                      rules={[
                        { required: true, message: '请输入新密码' },
                        { min: 8, max: 20, message: '密码长度 8-20 位' },
                        { pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/, message: '需包含大小写字母、数字和特殊字符' },
                      ]}
                      hasFeedback
                    >
                      <Input.Password placeholder="8-20 位，含大小写/数字/特殊字符" />
                    </Form.Item>
                    <Form.Item
                      label="确认新密码"
                      name="confirmPassword"
                      dependencies={['newPassword']}
                      rules={[
                        { required: true, message: '请再次输入新密码' },
                        ({ getFieldValue }) => ({
                          validator: (_, value) => (value === getFieldValue('newPassword') ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致'))),
                        }),
                      ]}
                    >
                      <Input.Password placeholder="确认新密码" />
                    </Form.Item>
                    <Form.Item>
                      <Button type="primary" htmlType="submit" loading={savingPwd}>修改密码</Button>
                    </Form.Item>
                  </Form>
                ),
              },
            ]}
          />
        </Card>
      </Col>
    </Row>
  )
}

export default function ProfilePage() {
  const { userId } = useParams<{ userId: string }>()
  return userId ? <PublicProfile userId={Number(userId)} /> : <MyProfile />
}

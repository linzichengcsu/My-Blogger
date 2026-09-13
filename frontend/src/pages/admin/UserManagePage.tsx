/**
 * 用户管理：分页表格 + 关键词搜索；详情抽屉（状态/角色/启锁定操作）、
 * 资料编辑弹窗、重置密码弹窗、软删除。
 */
import { useCallback, useEffect, useState } from 'react'
import {
  Avatar,
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import type { TableProps } from 'antd'
import {
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  LockOutlined,
  UnlockOutlined,
  UserDeleteOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  activateUser,
  changeUserPassword,
  deactivateUser,
  deleteUser,
  getAllUsers,
  getUserDetail,
  lockUser,
  searchUsers,
  unlockUser,
  updateUser,
} from '@/api'
import { ApiError } from '@/api/request'
import type { UserDetailDTO, UserPasswordChangeRequest, UserProfileDTO, UserUpdateRequest } from '@/types'
import type { PageResponse } from '@/types'
import { displayName, fileUrl, formatDateTime } from '@/utils'

const { Title, Text } = Typography

const STATUS_COLOR: Record<string, string> = { ACTIVE: 'green', INACTIVE: 'default', LOCKED: 'red' }

export default function UserManagePage() {
  const [data, setData] = useState<PageResponse<UserProfileDTO> | null>(null)
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [keyword, setKeyword] = useState('')

  const [detail, setDetail] = useState<UserDetailDTO | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [editing, setEditing] = useState<UserProfileDTO | null>(null)
  const [resetTarget, setResetTarget] = useState<UserProfileDTO | null>(null)
  const [saving, setSaving] = useState(false)
  const [editForm] = Form.useForm<UserUpdateRequest>()
  const [pwdForm] = Form.useForm<UserPasswordChangeRequest>()

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setData(
        keyword
          ? await searchUsers(keyword, { page, size: 10 })
          : await getAllUsers({ page, size: 10, sortBy: 'createdAt', sortDirection: 'desc' }),
      )
    } finally {
      setLoading(false)
    }
  }, [page, keyword])

  useEffect(() => { void load() }, [load])

  const act = async (fn: () => Promise<unknown>, success: string) => {
    try {
      await fn()
      message.success(success)
      void load()
      if (detail?.id) {
        const fresh = await getUserDetail(detail.id)
        setDetail(fresh)
      }
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '操作失败')
    }
  }

  const openDetail = async (record: UserProfileDTO) => {
    setDetailLoading(true)
    setDetail({ ...record } as UserDetailDTO)
    try {
      setDetail(await getUserDetail(record.id!))
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '详情加载失败')
    } finally {
      setDetailLoading(false)
    }
  }

  const openEdit = (record: UserProfileDTO) => {
    setEditing(record)
    editForm.setFieldsValue({ displayName: record.displayName ?? '', bio: record.bio ?? '' })
  }

  const submitEdit = async () => {
    const values = await editForm.validateFields()
    setSaving(true)
    try {
      await updateUser(editing!.id!, values)
      message.success('用户资料已更新')
      setEditing(null)
      void load()
    } catch (err) {
      if (err instanceof ApiError) message.error(err.message)
    } finally {
      setSaving(false)
    }
  }

  const submitResetPwd = async () => {
    const values = await pwdForm.validateFields()
    setSaving(true)
    try {
      await changeUserPassword(resetTarget!.id!, values)
      message.success('密码已重置')
      setResetTarget(null)
      pwdForm.resetFields()
    } catch (err) {
      if (err instanceof ApiError) message.error(err.message)
    } finally {
      setSaving(false)
    }
  }

  const columns: TableProps<UserProfileDTO>['columns'] = [
    {
      title: '用户',
      width: 200,
      render: (_, row) => (
        <Space>
          <Avatar src={fileUrl(row.avatar)} style={{ backgroundColor: '#fa541c' }}>
            {displayName(row.displayName, row.username).slice(0, 1)}
          </Avatar>
          <div>
            <div><Text strong>{displayName(row.displayName, row.username)}</Text></div>
            <Text type="secondary" style={{ fontSize: 12 }}>@{row.username}</Text>
          </div>
        </Space>
      ),
    },
    { title: '简介', dataIndex: 'bio', ellipsis: true, render: (v: string) => v || '-' },
    { title: '文章', dataIndex: 'articleCount', width: 70 },
    { title: '评论', dataIndex: 'commentCount', width: 70 },
    { title: '粉丝', dataIndex: 'followerCount', width: 70 },
    { title: '注册时间', dataIndex: 'createdAt', width: 110, render: (v: string) => (v ? v.slice(0, 10) : '-') },
    {
      title: '操作',
      width: 250,
      render: (_, row) => (
        <Space size={4} wrap>
          <Button size="small" icon={<EyeOutlined />} onClick={() => void openDetail(row)}>详情</Button>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(row)}>编辑</Button>
          <Button size="small" icon={<LockOutlined />} onClick={() => setResetTarget(row)}>改密</Button>
          <Popconfirm title="确认禁用/软删除该用户？" onConfirm={() => act(() => deleteUser(row.id!, true), '用户已禁用')}>
            <Button size="small" type="link" danger icon={<UserDeleteOutlined />}>禁用</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <Card>
      <Title level={5} style={{ marginTop: 0 }}>用户管理</Title>
      <Space style={{ marginBottom: 16 }}>
        <Input.Search
          placeholder="搜索用户名 / 昵称"
          allowClear
          style={{ width: 260 }}
          onSearch={(v) => { setPage(0); setKeyword(v.trim()) }}
        />
      </Space>

      <Table
        rowKey={(row) => row.id!}
        columns={columns}
        dataSource={data?.content ?? []}
        loading={loading}
        scroll={{ x: 1000 }}
        pagination={{
          current: page + 1,
          pageSize: data?.size ?? 10,
          total: data?.totalElements ?? 0,
          showSizeChanger: false,
          onChange: (p) => setPage(p - 1),
        }}
      />

      {/* 用户详情抽屉：状态/角色 + 启锁定 */}
      <Drawer open={Boolean(detail)} width={420} onClose={() => setDetail(null)} loading={detailLoading} title="用户详情">
        {detail && (
          <>
            <Space style={{ marginBottom: 16 }}>
              <Avatar size={56} src={fileUrl(detail.avatar)} style={{ backgroundColor: '#fa541c' }}>
                {displayName(detail.displayName, detail.username).slice(0, 1)}
              </Avatar>
              <div>
                <Title level={5} style={{ margin: 0 }}>{displayName(detail.displayName, detail.username)}</Title>
                <Space size={8}>
                  <Tag color={STATUS_COLOR[detail.status ?? ''] ?? 'default'}>{detail.status}</Tag>
                  <Tag color="orange">{detail.role}</Tag>
                </Space>
              </div>
            </Space>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="用户 ID">{detail.id}</Descriptions.Item>
              <Descriptions.Item label="用户名">{detail.username}</Descriptions.Item>
              <Descriptions.Item label="邮箱">{detail.email ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="简介">{detail.bio || '-'}</Descriptions.Item>
              <Descriptions.Item label="文章 / 评论 / 收藏">
                {detail.articleCount ?? 0} / {detail.commentCount ?? 0} / {detail.favoriteCount ?? 0}
              </Descriptions.Item>
              <Descriptions.Item label="上次登录">{formatDateTime(detail.lastLoginAt)}</Descriptions.Item>
              <Descriptions.Item label="注册时间">{formatDateTime(detail.createdAt)}</Descriptions.Item>
            </Descriptions>
            <Space style={{ marginTop: 20 }} wrap>
              {detail.status !== 'ACTIVE' && (
                <Button type="primary" icon={<UnlockOutlined />} onClick={() => act(() => activateUser(detail.id!), '已启用')}>启用</Button>
              )}
              {detail.status === 'ACTIVE' && (
                <Button icon={<UserOutlined />} onClick={() => act(() => deactivateUser(detail.id!), '已禁用')}>禁用</Button>
              )}
              {detail.status !== 'LOCKED' ? (
                <Button danger icon={<LockOutlined />} onClick={() => act(() => lockUser(detail.id!), '已锁定')}>锁定</Button>
              ) : (
                <Button type="primary" ghost icon={<UnlockOutlined />} onClick={() => act(() => unlockUser(detail.id!), '已解锁')}>解锁</Button>
              )}
              <Popconfirm title="确认物理删除该用户？不可恢复！" onConfirm={() => { setDetail(null); void act(() => deleteUser(detail.id!, false), '已删除') }}>
                <Button danger icon={<DeleteOutlined />}>物理删除</Button>
              </Popconfirm>
            </Space>
          </>
        )}
      </Drawer>

      {/* 编辑资料 */}
      <Drawer
        open={Boolean(editing)}
        title="编辑用户资料"
        width={420}
        onClose={() => setEditing(null)}
        extra={<Space>
          <Button onClick={() => setEditing(null)}>取消</Button>
          <Button type="primary" loading={saving} onClick={submitEdit}>保存</Button>
        </Space>}
      >
        <Form form={editForm} layout="vertical">
          <Form.Item label="显示名称" name="displayName" rules={[{ max: 50, message: '不超过 50 个字符' }]}>
            <Input placeholder="显示名称" />
          </Form.Item>
          <Form.Item label="个人简介" name="bio">
            <Input.TextArea rows={4} maxLength={200} showCount />
          </Form.Item>
        </Form>
      </Drawer>

      {/* 重置密码 */}
      <Modal
        open={Boolean(resetTarget)}
        title={`重置密码 - ${displayName(resetTarget?.displayName, resetTarget?.username)}`}
        onCancel={() => { setResetTarget(null); pwdForm.resetFields() }}
        confirmLoading={saving}
        onOk={submitResetPwd}
        okText="确认重置"
      >
        <Form form={pwdForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="oldPassword" label="管理员密码" rules={[{ required: true, message: '请输入当前管理员密码' }]}>
            <Input.Password placeholder="当前管理员密码" />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="用户新密码"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 8, max: 20, message: '密码长度 8-20 位' },
              { pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/, message: '需包含大小写字母、数字和特殊字符' },
            ]}
          >
            <Input.Password placeholder="8-20 位，含大小写/数字/特殊字符" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}

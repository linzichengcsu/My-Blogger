/**
 * 评论审核：后端无「全站评论列表」接口，按 文章ID / 用户ID 两个维度检索后审核。
 * 支持批量通过 / 拒绝 / 删除与单条删除。
 */
import { useState } from 'react'
import {
  Button,
  Card,
  Empty,
  InputNumber,
  Popconfirm,
  Space,
  Table,
  Tabs,
  Typography,
  message,
} from 'antd'
import type { TableProps } from 'antd'
import { CheckOutlined, CloseOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons'
import {
  batchApproveComments,
  batchDeleteComments,
  deleteComment,
  getTopLevelComments,
  getUserComments,
} from '@/api'
import { ApiError } from '@/api/request'
import type { CommentDTO, PageResponse } from '@/types'
import { displayName, formatDateTime } from '@/utils'

const { Title, Text } = Typography

export default function CommentManagePage() {
  const [scope, setScope] = useState<'article' | 'user'>('article')
  const [targetId, setTargetId] = useState<number | null>(null)
  const [data, setData] = useState<PageResponse<CommentDTO> | null>(null)
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(0)
  const [selectedIds, setSelectedIds] = useState<number[]>([])

  const load = async (nextPage = page) => {
    if (!targetId) {
      message.warning('请先输入 ID')
      return
    }
    setLoading(true)
    try {
      const query = { page: nextPage, size: 10, sortBy: 'createdAt', sortDirection: 'desc' as const }
      setData(scope === 'article' ? await getTopLevelComments(targetId, query) : await getUserComments(targetId, query))
      setPage(nextPage)
      setSelectedIds([])
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '加载失败')
      setData(null)
    } finally {
      setLoading(false)
    }
  }

  const act = async (fn: () => Promise<unknown>, success: string) => {
    try {
      await fn()
      message.success(success)
      setSelectedIds([])
      void load(page)
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '操作失败')
    }
  }

  const columns: TableProps<CommentDTO>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '评论内容', dataIndex: 'content', ellipsis: true },
    {
      title: '评论者',
      dataIndex: 'commenter',
      width: 140,
      render: (_: unknown, row) => displayName(row.commenter?.displayName, row.commenter?.username),
    },
    { title: '文章ID', dataIndex: 'articleId', width: 90 },
    { title: '回复数', dataIndex: 'replyCount', width: 80 },
    { title: '点赞', dataIndex: 'likeCount', width: 70 },
    { title: '评论时间', dataIndex: 'createdAt', width: 160, render: (v: string) => formatDateTime(v) },
    {
      title: '操作',
      width: 90,
      render: (_, row) => (
        <Popconfirm title="确认删除该评论？" onConfirm={() => act(() => deleteComment(row.id!), '已删除')}>
          <Button size="small" type="link" danger icon={<DeleteOutlined />}>删除</Button>
        </Popconfirm>
      ),
    },
  ]

  return (
    <Card>
      <Title level={5} style={{ marginTop: 0 }}>评论审核</Title>

      <Tabs
        activeKey={scope}
        onChange={(k) => { setScope(k as 'article' | 'user'); setData(null); setTargetId(null) }}
        items={[
          { key: 'article', label: '按文章查询' },
          { key: 'user', label: '按用户查询' },
        ]}
      />
      <Space style={{ marginBottom: 16 }} wrap>
        <Text type="secondary">{scope === 'article' ? '文章 ID' : '用户 ID'}</Text>
        <InputNumber min={1} value={targetId} onChange={(v) => setTargetId(v)} style={{ width: 160 }} placeholder="请输入 ID" />
        <Button type="primary" icon={<SearchOutlined />} onClick={() => load(0)}>查询</Button>
        <Button
          icon={<CheckOutlined />}
          disabled={!selectedIds.length}
          onClick={() => act(() => batchApproveComments(selectedIds, true), `已通过 ${selectedIds.length} 条`)}
        >
          批量通过
        </Button>
        <Button
          danger
          icon={<CloseOutlined />}
          disabled={!selectedIds.length}
          onClick={() => act(() => batchApproveComments(selectedIds, false), `已拒绝 ${selectedIds.length} 条`)}
        >
          批量拒绝
        </Button>
        <Popconfirm
          title={`确认删除选中的 ${selectedIds.length} 条评论？`}
          disabled={!selectedIds.length}
          onConfirm={() => act(() => batchDeleteComments(selectedIds), '批量删除成功')}
        >
          <Button danger icon={<DeleteOutlined />} disabled={!selectedIds.length}>批量删除</Button>
        </Popconfirm>
      </Space>

      <Table
        rowKey={(row) => row.id!}
        columns={columns}
        dataSource={data?.content ?? []}
        loading={loading}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="输入 ID 后查询评论" /> }}
        scroll={{ x: 900 }}
        rowSelection={{
          selectedRowKeys: selectedIds,
          onChange: (keys) => setSelectedIds(keys as number[]),
        }}
        pagination={{
          current: page + 1,
          pageSize: data?.size ?? 10,
          total: data?.totalElements ?? 0,
          showSizeChanger: false,
          onChange: (p) => void load(p - 1),
        }}
      />
    </Card>
  )
}

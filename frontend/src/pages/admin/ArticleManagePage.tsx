/**
 * 文章管理：服务端分页表格 + 关键词搜索 + 批量发布/归档/删除。
 */
import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Card,
  Input,
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
  FolderOpenOutlined,
  PlusOutlined,
  SendOutlined,
} from '@ant-design/icons'
import { Link, useNavigate } from 'react-router-dom'
import {
  archiveArticle,
  batchArchiveArticles,
  batchDeleteArticles,
  batchPublishArticles,
  deleteArticle,
  getArticleList,
  publishArticle,
  searchArticles,
} from '@/api'
import { ApiError } from '@/api/request'
import type { ArticleListItemDTO, PageResponse } from '@/types'
import { ROUTES } from '@/constants/routes'
import { displayName, formatDate } from '@/utils'

const { Title } = Typography

export default function ArticleManagePage() {
  const navigate = useNavigate()
  const [data, setData] = useState<PageResponse<ArticleListItemDTO> | null>(null)
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [selectedIds, setSelectedIds] = useState<number[]>([])

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const query = { page, size: 10, sortBy: 'createdAt', sortDirection: 'desc' as const }
      setData(keyword ? await searchArticles(keyword, query) : await getArticleList(query))
    } finally {
      setLoading(false)
    }
  }, [page, keyword])

  useEffect(() => { void load() }, [load])

  /** 单条/批量动作后统一刷新 */
  const act = async (fn: () => Promise<unknown>, success: string) => {
    try {
      await fn()
      message.success(success)
      setSelectedIds([])
      void load()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '操作失败')
    }
  }

  const columns: TableProps<ArticleListItemDTO>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    {
      title: '标题',
      dataIndex: 'title',
      render: (text: string, row) => <Link to={ROUTES.article(row.id ?? '')}>{text}</Link>,
    },
    {
      title: '作者',
      dataIndex: ['author', 'displayName'],
      width: 120,
      render: (_: unknown, row) => displayName(row.author?.displayName, row.author?.username),
    },
    {
      title: '收藏夹',
      dataIndex: 'categories',
      width: 160,
      render: (_: unknown, row: ArticleListItemDTO) => (
        <Space size={4} wrap>
          {row.categories?.map((c) => <Tag key={c.id} style={{ marginInlineEnd: 0 }}>{c.name}</Tag>) ?? '-'}
        </Space>
      ),
    },
    { title: '点赞', dataIndex: 'likeCount', width: 70 },
    { title: '评论', dataIndex: 'commentCount', width: 70 },
    { title: '创建时间', dataIndex: 'createdAt', width: 110, render: (v: string) => formatDate(v) },
    {
      title: '操作',
      width: 230,
      render: (_, row) => {
        const id = row.id!
        return (
          <Space size={4}>
            <Button size="small" icon={<EditOutlined />} onClick={() => navigate(ROUTES.edit(id))}>编辑</Button>
            <Button size="small" type="link" icon={<SendOutlined />} onClick={() => act(() => publishArticle(id), '已发布')}>发布</Button>
            <Button size="small" type="link" icon={<FolderOpenOutlined />} onClick={() => act(() => archiveArticle(id), '已归档')}>归档</Button>
            <Popconfirm title="确认删除该文章？" onConfirm={() => act(() => deleteArticle(id), '已删除')}>
              <Button size="small" type="link" danger icon={<DeleteOutlined />}>删除</Button>
            </Popconfirm>
          </Space>
        )
      },
    },
  ]

  return (
    <Card>
      <Title level={5} style={{ marginTop: 0 }}>文章管理</Title>
      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          placeholder="搜索标题或内容"
          allowClear
          style={{ width: 260 }}
          onSearch={(v) => { setPage(0); setKeyword(v.trim()) }}
        />
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate(ROUTES.write)}>新建文章</Button>
        <Button disabled={!selectedIds.length} onClick={() => act(() => batchPublishArticles(selectedIds), `已发布 ${selectedIds.length} 篇`)}>
          批量发布
        </Button>
        <Button disabled={!selectedIds.length} onClick={() => act(() => batchArchiveArticles(selectedIds), `已归档 ${selectedIds.length} 篇`)}>
          批量归档
        </Button>
        <Popconfirm
          title={`确认删除选中的 ${selectedIds.length} 篇文章？`}
          disabled={!selectedIds.length}
          onConfirm={() => act(() => batchDeleteArticles(selectedIds), '批量删除成功')}
        >
          <Button danger disabled={!selectedIds.length}>批量删除</Button>
        </Popconfirm>
      </Space>

      <Table
        rowKey={(row) => row.id!}
        columns={columns}
        dataSource={data?.content ?? []}
        loading={loading}
        scroll={{ x: 1100 }}
        pagination={{
          current: page + 1,
          pageSize: data?.size ?? 10,
          total: data?.totalElements ?? 0,
          showSizeChanger: false,
          onChange: (p) => setPage(p - 1),
        }}
        rowSelection={{
          selectedRowKeys: selectedIds,
          onChange: (keys) => setSelectedIds(keys as number[]),
        }}
      />
    </Card>
  )
}

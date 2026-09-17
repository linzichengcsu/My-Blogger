/**
 * 收藏夹管理：分页表格 + 新建/编辑弹窗（父收藏夹树选）+ 删除时可选转移文章。
 */
import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  TreeSelect,
  Typography,
  message,
} from 'antd'
import type { TableProps } from 'antd'
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import {
  buildCategoryTree,
  createCategory,
  deleteCategory,
  deleteCategoryAndTransferArticles,
  getAllCategories,
  updateCategory,
} from '@/api'
import { ApiError } from '@/api/request'
import type { CategoryDTO, CategoryRequest, CategoryTreeDTO, PageResponse } from '@/types'
import { formatDate } from '@/utils'

const { Title } = Typography

function toTreeData(nodes: CategoryTreeDTO[] = []): NonNullable<React.ComponentProps<typeof TreeSelect>['treeData']> {
  return nodes.map((n) => ({
    title: n.name,
    value: n.id!,
    children: toTreeData(n.children ?? []),
  }))
}

/** 拍平收藏夹树供普通下拉使用 */
function flattenTree(nodes: CategoryTreeDTO[] = [], depth = 0): { label: string; value: number }[] {
  return nodes.flatMap((n) => [
    { label: `${'\u00A0\u00A0'.repeat(depth)}${depth ? '└ ' : ''}${n.name}`, value: n.id! },
    ...flattenTree(n.children ?? [], depth + 1),
  ])
}

export default function CategoryManagePage() {
  const [data, setData] = useState<PageResponse<CategoryDTO> | null>(null)
  const [tree, setTree] = useState<CategoryTreeDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)

  const [formOpen, setFormOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<CategoryRequest>()

  /** 删除确认弹窗状态 */
  const [deleteTarget, setDeleteTarget] = useState<CategoryDTO | null>(null)
  const [transferTo, setTransferTo] = useState<number | undefined>()

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [pageRes, treeRes] = await Promise.all([
        getAllCategories({ page, size: 10, sortBy: 'createdAt', sortDirection: 'desc' }),
        buildCategoryTree(),
      ])
      setData(pageRes)
      setTree(treeRes)
    } finally {
      setLoading(false)
    }
  }, [page])

  useEffect(() => { void load() }, [load])

  const openCreate = () => {
    setEditingId(null)
    form.resetFields()
    setFormOpen(true)
  }

  const openEdit = (record: CategoryDTO) => {
    setEditingId(record.id!)
    form.setFieldsValue({
      name: record.name ?? '',
      description: record.description ?? '',
      parentCategoryId: record.parentCategoryId ?? undefined,
    })
    setFormOpen(true)
  }

  const submit = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      if (editingId) await updateCategory(editingId, values)
      else await createCategory(values)
      message.success(editingId ? '收藏夹已更新' : '收藏夹已创建')
      setFormOpen(false)
      void load()
    } catch (err) {
      if (err instanceof ApiError) message.error(err.message)
    } finally {
      setSaving(false)
    }
  }

  const confirmDelete = async () => {
    try {
      if (transferTo) await deleteCategoryAndTransferArticles(deleteTarget!.id!, transferTo)
      else await deleteCategory(deleteTarget!.id!)
      message.success('收藏夹已删除')
      setDeleteTarget(null)
      setTransferTo(undefined)
      void load()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '删除失败')
    }
  }

  const columns: TableProps<CategoryDTO>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '名称', dataIndex: 'name', width: 160 },
    { title: '父收藏夹', dataIndex: 'parentCategoryName', width: 140, render: (v: string) => v || '-' },
    { title: '描述', dataIndex: 'description', ellipsis: true, render: (v: string) => v || '-' },
    { title: '文章数', dataIndex: 'articleCount', width: 90 },
    { title: '创建时间', dataIndex: 'createdAt', width: 110, render: (v: string) => formatDate(v) },
    {
      title: '操作',
      width: 140,
      render: (_, record) => (
        <Space size={4}>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
          <Button size="small" type="link" danger icon={<DeleteOutlined />} onClick={() => setDeleteTarget(record)}>删除</Button>
        </Space>
      ),
    },
  ]

  return (
    <Card>
      <Title level={5} style={{ marginTop: 0 }}>收藏夹管理</Title>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建收藏夹</Button>
      </Space>

      <Table
        rowKey={(row) => row.id!}
        columns={columns}
        dataSource={data?.content ?? []}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize: data?.size ?? 10,
          total: data?.totalElements ?? 0,
          showSizeChanger: false,
          onChange: (p) => setPage(p - 1),
        }}
      />

      <Modal
        title={editingId ? '编辑收藏夹' : '新建收藏夹'}
        open={formOpen}
        onOk={submit}
        confirmLoading={saving}
        onCancel={() => setFormOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="name" label="收藏夹名称" rules={[
            { required: true, message: '请输入收藏夹名称' },
            { min: 2, max: 20, message: '名称长度 2-20 个字符' },
          ]}>
            <Input placeholder="2-20 个字符" maxLength={20} />
          </Form.Item>
          <Form.Item name="parentCategoryId" label="父收藏夹">
            <TreeSelect
              treeData={toTreeData(tree)}
              treeDefaultExpandAll
              allowClear
              placeholder="不选则为顶级收藏夹"
            />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} maxLength={200} showCount placeholder="收藏夹描述（可选）" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="删除收藏夹"
        open={Boolean(deleteTarget)}
        onOk={confirmDelete}
        onCancel={() => { setDeleteTarget(null); setTransferTo(undefined) }}
        okText="确认删除"
        okButtonProps={{ danger: true }}
      >
        <p>确认删除收藏夹「<b>{deleteTarget?.name}</b>」吗？</p>
        <p style={{ color: '#999', marginBottom: 8 }}>可先将其下文章转移到其他收藏夹（不选则直接移除关联）：</p>
        <Select
          style={{ width: '100%' }}
          placeholder="选择目标收藏夹（可选）"
          allowClear
          options={flattenTree(tree).filter((c) => c.value !== deleteTarget?.id)}
          value={transferTo}
          onChange={setTransferTo}
        />
      </Modal>
    </Card>
  )
}

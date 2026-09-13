/**
 * 写文章 / 编辑文章（/write 与 /edit/:id 共用）。
 * 左表单右预览的双栏写作页：分类树拉取后拍平为多选，封面走 /files/upload。
 */
import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  Radio,
  Row,
  Select,
  Skeleton,
  Space,
  Tabs,
  Tag,
  Upload,
  message,
} from 'antd'
import type { UploadProps } from 'antd'
import { UploadOutlined } from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import MarkdownView from '@/components/MarkdownView'
import { buildCategoryTree, createArticle, getArticleById, updateArticle, uploadFile } from '@/api'
import { ApiError } from '@/api/request'
import type { ArticleCreateRequest, ArticleStatus, CategoryTreeDTO } from '@/types'
import { ROUTES } from '@/constants/routes'
import { fileUrl } from '@/utils'

type ArticleForm = Omit<ArticleCreateRequest, 'status'> & { status: ArticleStatus }

/** customRequest 入参类型，直接从 antd 推导，避免依赖 rc-upload 内部路径 */
type UploadRequestOption = Parameters<NonNullable<UploadProps['customRequest']>>[0]

/** 拍平分类树为 Select options（按层级缩进） */
function flattenTree(nodes: CategoryTreeDTO[] = [], depth = 0): { label: string; value: number }[] {
  return nodes.flatMap((n) => [
    { label: `${'\u00A0\u00A0'.repeat(depth)}${depth ? '└ ' : ''}${n.name}`, value: n.id! },
    ...flattenTree(n.children ?? [], depth + 1),
  ])
}

export default function WritePage() {
  const { id } = useParams<{ id: string }>()
  const articleId = id ? Number(id) : null
  const isEdit = Boolean(articleId)
  const navigate = useNavigate()
  const [form] = Form.useForm<ArticleForm>()
  const [tree, setTree] = useState<CategoryTreeDTO[]>([])
  const [loading, setLoading] = useState(isEdit)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [coverImage, setCoverImage] = useState<string>()
  const content = Form.useWatch('content', form)
  const tags = Form.useWatch('tags', form) as string[] | undefined

  useEffect(() => {
    buildCategoryTree().then(setTree).catch(() => setTree([]))
    if (articleId) {
      getArticleById(articleId)
        .then((a) => {
          setCoverImage(a.coverImage ?? undefined)
          form.setFieldsValue({
            title: a.title ?? '',
            content: a.content ?? '',
            summary: a.summary ?? '',
            coverImage: a.coverImage ?? '',
            categoryIds: a.categories?.map((c) => c.id!).filter((v): v is number => Boolean(v)) ?? [],
            tags: a.tags ?? [],
            status: a.status ?? 'DRAFT',
          })
        })
        .catch(() => message.error('文章加载失败'))
        .finally(() => setLoading(false))
    }
  }, [articleId, form])

  const categoryOptions = useMemo(() => flattenTree(tree), [tree])

  const customUpload = async (option: UploadRequestOption) => {
    setUploading(true)
    try {
      const res = await uploadFile(option.file as File, () => undefined)
      const url = res.fileUrl ?? res.fileName ?? ''
      setCoverImage(url)
      form.setFieldValue('coverImage', url)
      message.success('封面上传成功')
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '上传失败')
    } finally {
      setUploading(false)
    }
  }

  const submit = async (status: ArticleStatus) => {
    const values = await form.validateFields()
    const payload = { ...values, status }
    setSaving(true)
    try {
      const saved = isEdit
        ? await updateArticle(articleId!, payload)
        : await createArticle(payload as ArticleCreateRequest)
      message.success(isEdit ? '文章已更新' : status === 'RELEASE' ? '文章已发布' : '草稿已保存')
      navigate(ROUTES.article(saved.id!))
    } catch (err) {
      if (err instanceof ApiError) message.error(err.message)
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <Card><Skeleton active paragraph={{ rows: 8 }} /></Card>

  return (
    <Form
      form={form}
      layout="vertical"
      initialValues={{ status: 'DRAFT' as ArticleStatus, categoryIds: [], tags: [] }}
    >
      <Row gutter={16}>
        <Col xs={24} lg={14}>
          <Card>
            <Form.Item name="title" rules={[
              { required: true, message: '请输入标题' },
              { min: 5, max: 100, message: '标题长度 5-100 个字符' },
            ]}>
              <Input size="large" placeholder="请输入标题（5-100 字）" maxLength={100} showCount />
            </Form.Item>

            <Form.Item name="content" rules={[
              { required: true, message: '请输入正文' },
              { validator: (_, v: string) => (v && v.trim().length >= 20 ? Promise.resolve() : Promise.reject(new Error('正文至少 20 个字符'))) },
            ]}>
              <Tabs
                items={[
                  {
                    key: 'edit',
                    label: '编辑（支持 Markdown）',
                    children: <Input.TextArea rows={18} placeholder="开始写作，支持 Markdown 语法…" style={{ fontFamily: 'monospace' }} />,
                  },
                  { key: 'preview', label: '预览', children: <MarkdownView content={content} /> },
                ]}
              />
            </Form.Item>

            <Form.Item name="summary" rules={[{ max: 200, message: '摘要不超过 200 字' }]}>
              <Input.TextArea rows={3} maxLength={200} showCount placeholder="摘要（可选，不填则由正文自动截取）" />
            </Form.Item>
          </Card>
        </Col>

        <Col xs={24} lg={10}>
          <Card title="发布设置" style={{ marginBottom: 16 }}>
            <Form.Item name="categoryIds" rules={[{ required: true, message: '至少选择一个分类' }]}>
              <Select
                mode="multiple"
                options={categoryOptions}
                placeholder="选择分类（可多选）"
                notFoundContent={tree.length === 0 ? '暂无可用分类' : undefined}
              />
            </Form.Item>
            <Form.Item name="tags" label="标签">
              <Select mode="tags" placeholder="输入后回车添加标签" tokenSeparators={[',']} />
            </Form.Item>
            <Form.Item name="status" label="状态">
              <Radio.Group
                optionType="button"
                buttonStyle="solid"
                options={[
                  { label: '草稿', value: 'DRAFT' },
                  { label: '发布', value: 'RELEASE' },
                  { label: '归档', value: 'ARCHIVE' },
                ]}
              />
            </Form.Item>
            <Space>
              <Button type="primary" loading={saving} onClick={() => void submit(form.getFieldValue('status') ?? 'DRAFT')}>
                保存
              </Button>
              <Button loading={saving} onClick={() => void submit('RELEASE')}>直接发布</Button>
            </Space>
          </Card>

          <Card title="封面图">
            <Form.Item name="coverImage" hidden><Input /></Form.Item>
            {coverImage ? (
              <>
                <img src={fileUrl(coverImage)} alt="cover" style={{ width: '100%', borderRadius: 8, marginBottom: 12 }} />
                <Button danger onClick={() => { setCoverImage(undefined); form.setFieldValue('coverImage', '') }}>
                  移除封面
                </Button>
              </>
            ) : (
              <Upload showUploadList={false} customRequest={customUpload} accept="image/*">
                <Button icon={<UploadOutlined />} loading={uploading}>上传封面</Button>
              </Upload>
            )}
          </Card>

          <Card title="标签预览" size="small" style={{ marginTop: 16 }}>
            <Space size={[8, 8]} wrap>
              {tags?.length ? (
                tags.map((t) => <Tag key={t} color="orange">{t}</Tag>)
              ) : (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无标签" />
              )}
            </Space>
          </Card>
        </Col>
      </Row>
    </Form>
  )
}

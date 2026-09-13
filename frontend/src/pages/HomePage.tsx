/**
 * 首页：文章信息流 + 右侧边栏。
 * 支持 URL 驱动的关键词搜索与分页（?keyword=&page=），
 * 分类接口需要登录，侧栏分类直接从已加载文章中聚合，点击走搜索。
 */
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Card, Col, Empty, Pagination, Row, Skeleton, Space, Tag, Typography, Button } from 'antd'
import { EditOutlined, FireOutlined } from '@ant-design/icons'
import { useNavigate, useSearchParams } from 'react-router-dom'
import ArticleCard from '@/components/ArticleCard'
import { getArticleList, searchArticles } from '@/api'
import type { ArticleListItemDTO, PageResponse } from '@/types'
import { ROUTES } from '@/constants/routes'
import { useAuth } from '@/context/AuthContext'

const { Title, Paragraph, Text } = Typography
const PAGE_SIZE = 8

export default function HomePage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { isLoggedIn } = useAuth()
  const keyword = searchParams.get('keyword') ?? ''
  const page = Number(searchParams.get('page') ?? 0)

  const [data, setData] = useState<PageResponse<ArticleListItemDTO> | null>(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = keyword
        ? await searchArticles(keyword, { page, size: PAGE_SIZE, sortBy: 'createdAt', sortDirection: 'desc' })
        : await getArticleList({ page, size: PAGE_SIZE, sortBy: 'createdAt', sortDirection: 'desc' })
      setData(res)
    } finally {
      setLoading(false)
    }
  }, [keyword, page])

  useEffect(() => {
    void load()
  }, [load])

  /** 从当前数据聚合分类（名称 -> 出现次数） */
  const hotCategories = useMemo(() => {
    const counter = new Map<string, number>()
    data?.content.forEach((a) =>
      a.categories?.forEach((c) => c.name && counter.set(c.name, (counter.get(c.name) ?? 0) + 1)),
    )
    return [...counter.entries()].sort((x, y) => y[1] - x[1]).slice(0, 12)
  }, [data])

  const goPage = (next: number) => navigate(`${ROUTES.home}?${keyword ? `keyword=${encodeURIComponent(keyword)}&` : ''}page=${next - 1}`)

  return (
    <Row gutter={24}>
      <Col xs={24} md={17}>
        {keyword && (
          <Paragraph>
            搜索「<Text strong>{keyword}</Text>」共 {data?.totalElements ?? 0} 篇结果
          </Paragraph>
        )}
        {loading ? (
          [...Array(3)].map((_, i) => <Skeleton key={i} active avatar paragraph={{ rows: 2 }} style={{ background: '#fff', padding: 24, marginBottom: 16, borderRadius: 8 }} />)
        ) : data && data.content.length > 0 ? (
          <>
            {data.content.map((a) => (
              <ArticleCard key={a.id} article={a} />
            ))}
            <div style={{ textAlign: 'center', marginTop: 16 }}>
              <Pagination
                current={page + 1}
                pageSize={data.size || PAGE_SIZE}
                total={data.totalElements}
                showSizeChanger={false}
                onChange={goPage}
              />
            </div>
          </>
        ) : (
          <Card>
            <Empty description={keyword ? '没有找到相关文章，换个关键词试试' : '还没有文章，快来发布第一篇吧'}>
              {isLoggedIn && (
                <Button type="primary" icon={<EditOutlined />} onClick={() => navigate(ROUTES.write)}>
                  写文章
                </Button>
              )}
            </Empty>
          </Card>
        )}
      </Col>

      <Col xs={0} md={7}>
        <Card style={{ marginBottom: 16 }}>
          <Title level={5} style={{ marginTop: 0, color: '#fa541c' }}>
            关于博客
          </Title>
          <Paragraph type="secondary" style={{ marginBottom: isLoggedIn ? 16 : 0 }}>
            这里是一个基于 Spring Boot + React + Ant Design 构建的个人博客，记录技术与生活。欢迎留言交流。
          </Paragraph>
          {isLoggedIn && (
            <Button type="primary" block icon={<EditOutlined />} onClick={() => navigate(ROUTES.write)}>
              发布新文章
            </Button>
          )}
        </Card>

        <Card title={<Space><FireOutlined style={{ color: '#fa541c' }} />热门分类</Space>}>
          {hotCategories.length === 0 ? (
            <Text type="secondary">暂无分类数据</Text>
          ) : (
            <Space size={[8, 8]} wrap>
              {hotCategories.map(([name, count]) => (
                <Tag
                  key={name}
                  color="orange"
                  style={{ cursor: 'pointer', marginInlineEnd: 0 }}
                  onClick={() => navigate(`${ROUTES.home}?keyword=${encodeURIComponent(name)}`)}
                >
                  {name} ({count})
                </Tag>
              ))}
            </Space>
          )}
        </Card>
      </Col>
    </Row>
  )
}

import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import ArticleCard from '@/components/ArticleCard'
import type { ArticleListItemDTO } from '@/types'

function renderCard(article: ArticleListItemDTO) {
  return render(
    <MemoryRouter>
      <ArticleCard article={article} />
    </MemoryRouter>,
  )
}

describe('ArticleCard', () => {
  it('渲染标题、作者与互动数据', () => {
    renderCard({
      id: 1,
      title: '深入理解 Spring Security',
      summary: '一篇关于认证与授权的文章',
      createdAt: '2026-09-13 14:30:00',
      likeCount: 12,
      commentCount: 3,
      author: { id: 10, username: 'linzicheng', displayName: '林子程' },
    })

    expect(screen.getByText('深入理解 Spring Security')).toBeInTheDocument()
    expect(screen.getByText('林子程')).toBeInTheDocument()
    expect(screen.getByText('2026-09-13 14:30')).toBeInTheDocument()
    expect(screen.getByText('12')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
  })

  it('摘要为空时展示兜底文案', () => {
    renderCard({ id: 2, title: '无摘要文章', author: { id: 1, username: 'u' } })

    expect(screen.getByText('这个作者很懒，还没有写摘要~')).toBeInTheDocument()
  })

  it('超长摘要按 120 字符截断', () => {
    const longSummary = 'A'.repeat(130)
    renderCard({ id: 3, title: '长摘要', summary: longSummary, author: { id: 1, username: 'u' } })

    expect(screen.getByText(`${'A'.repeat(120)}…`)).toBeInTheDocument()
  })

  it('只展示前两个收藏夹标签', () => {
    renderCard({
      id: 4,
      title: '标签测试',
      categories: [
        { id: 1, name: '技术' },
        { id: 2, name: '生活' },
        { id: 3, name: '随笔' },
      ],
      author: { id: 1, username: 'u' },
    })

    expect(screen.getByText('技术')).toBeInTheDocument()
    expect(screen.getByText('生活')).toBeInTheDocument()
    expect(screen.queryByText('随笔')).not.toBeInTheDocument()
  })

  it('封面图相对路径补 /api 前缀', () => {
    renderCard({ id: 5, title: '带封面', coverImage: 'uploads/cover.png', author: { id: 1, username: 'u' } })

    expect(screen.getByRole('img', { name: '带封面' })).toHaveAttribute('src', '/api/uploads/cover.png')
  })

  it('标题与作者链接指向正确路由', () => {
    renderCard({ id: 6, title: '链接测试', author: { id: 66, username: 'u', displayName: '作者A' } })

    expect(screen.getByRole('link', { name: '链接测试' })).toHaveAttribute('href', '/articles/6')
    expect(screen.getByRole('link', { name: '作者A' })).toHaveAttribute('href', '/u/66')
  })

  it('无作者信息时不渲染作者行', () => {
    renderCard({ id: 7, title: '匿名文章' })

    expect(screen.queryByRole('link', { name: /匿名用户/ })).not.toBeInTheDocument()
    expect(screen.getByText('匿名文章')).toBeInTheDocument()
  })
})

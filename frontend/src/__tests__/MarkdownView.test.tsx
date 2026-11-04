import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import MarkdownView from '@/components/MarkdownView'

describe('MarkdownView', () => {
  it('渲染标题与段落', () => {
    render(<MarkdownView content={'# 一级标题\n\n这是正文段落。'} />)

    expect(screen.getByRole('heading', { level: 1, name: '一级标题' })).toBeInTheDocument()
    expect(screen.getByText('这是正文段落。')).toBeInTheDocument()
  })

  it('支持 GFM 表格', () => {
    render(<MarkdownView content={'| 名称 | 数量 |\n| --- | --- |\n| 文章 | 12 |'} />)

    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '文章' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '12' })).toBeInTheDocument()
  })

  it('内容为空时渲染空容器而不报错', () => {
    const { container } = render(<MarkdownView />)

    expect(container.querySelector('.markdown-body')).toBeInTheDocument()
    expect(container.textContent).toBe('')
  })

  it('单个换行渲染为 <br>（诗歌逐行排版）', () => {
    const { container } = render(<MarkdownView content={'床前明月光\n疑是地上霜'} />)

    const p = container.querySelector('.markdown-body p')
    expect(p?.querySelector('br')).toBeInTheDocument()
    expect(p?.textContent).toContain('床前明月光')
    expect(p?.textContent).toContain('疑是地上霜')
  })

  it('渲染行内代码与链接', () => {
    render(<MarkdownView content={'使用 `pnpm test`，详见 [文档](https://example.com)'} />)

    expect(screen.getByText('pnpm test')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '文档' })).toHaveAttribute('href', 'https://example.com')
  })
})

/**
 * Markdown 正文渲染（文章详情 / 写文章预览共用）。
 * 支持 GFM 表格、删除线、任务列表；样式见 index.css 的 .markdown-body。
 */
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import remarkBreaks from 'remark-breaks'

export default function MarkdownView({ content }: { content?: string }) {
  return (
    <div className="markdown-body">
      {/* remark-breaks：把单个换行渲染为 <br>，保留诗歌等逐行排版 */}
      <ReactMarkdown remarkPlugins={[remarkGfm, remarkBreaks]}>{content || ''}</ReactMarkdown>
    </div>
  )
}

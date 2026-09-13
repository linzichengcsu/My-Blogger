/**
 * 跨页面复用的纯工具函数：日期格式化、文本截断、文件地址处理。
 */
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

/** 完整日期时间，如 2026-09-13 14:30 */
export function formatDateTime(value?: string | null): string {
  if (!value) return '-'
  return dayjs(value).format('YYYY-MM-DD HH:mm')
}

/** 短日期，如 2026-09-13 */
export function formatDate(value?: string | null): string {
  if (!value) return '-'
  return dayjs(value).format('YYYY-MM-DD')
}

/** 相对时间，如「3 小时前」 */
export function fromNow(value?: string | null): string {
  if (!value) return '-'
  return dayjs(value).fromNow()
}

/** 截断长文本并补省略号 */
export function truncate(text: string | undefined | null, max: number): string {
  if (!text) return ''
  return text.length > max ? `${text.slice(0, max)}…` : text
}

/**
 * 统一解析后端返回的文件地址：
 * - 完整 http(s)/data/blob 地址原样返回
 * - 以 /api 开头原样返回
 * - 其余相对路径补 /api 前缀（后端文件接口挂在 /api/files 下）
 */
export function fileUrl(url?: string | null): string | undefined {
  if (!url) return undefined
  if (/^(https?:|data:|blob:|\/api\/)/.test(url)) return url
  return `/api/${url.replace(/^\//, '')}`
}

/** 用户名展示名兜底 */
export function displayName(name?: string | null, username?: string | null): string {
  return name || username || '匿名用户'
}

import dayjs from 'dayjs'
import { describe, expect, it } from 'vitest'
import { ROUTES } from '@/constants/routes'
import { displayName, fileUrl, formatDate, formatDateTime, fromNow, truncate } from '@/utils'

describe('日期格式化', () => {
  it('formatDateTime 输出 YYYY-MM-DD HH:mm', () => {
    expect(formatDateTime('2026-09-13 14:30:00')).toBe('2026-09-13 14:30')
  })

  it('formatDate 输出 YYYY-MM-DD', () => {
    expect(formatDate('2026-09-13T08:00:00')).toBe('2026-09-13')
  })

  it('空值统一返回占位符 -', () => {
    expect(formatDateTime(null)).toBe('-')
    expect(formatDateTime(undefined)).toBe('-')
    expect(formatDate('')).toBe('-')
    expect(fromNow(null)).toBe('-')
  })

  it('fromNow 返回中文相对时间', () => {
    const twoHoursAgo = dayjs().subtract(2, 'hour').format('YYYY-MM-DD HH:mm:ss')
    expect(fromNow(twoHoursAgo)).toMatch(/2\s*小时前/)
  })
})

describe('truncate 文本截断', () => {
  it('空文本返回空字符串', () => {
    expect(truncate(null, 10)).toBe('')
    expect(truncate(undefined, 10)).toBe('')
  })

  it('未超出长度时原样返回', () => {
    expect(truncate('短文本', 10)).toBe('短文本')
  })

  it('超出长度时截断并补省略号', () => {
    expect(truncate('1234567890abc', 10)).toBe('1234567890…')
  })
})

describe('fileUrl 文件地址解析', () => {
  it('相对路径补 /api 前缀', () => {
    expect(fileUrl('uploads/a.png')).toBe('/api/uploads/a.png')
  })

  it('已带前导斜杠的路径同样补 /api', () => {
    expect(fileUrl('/uploads/a.png')).toBe('/api/uploads/a.png')
  })

  it('完整地址与 /api 路径原样返回', () => {
    expect(fileUrl('https://cdn.example.com/a.png')).toBe('https://cdn.example.com/a.png')
    expect(fileUrl('data:image/png;base64,AAA')).toBe('data:image/png;base64,AAA')
    expect(fileUrl('blob:http://localhost/1')).toBe('blob:http://localhost/1')
    expect(fileUrl('/api/files/a.png')).toBe('/api/files/a.png')
  })

  it('空值返回 undefined', () => {
    expect(fileUrl(null)).toBeUndefined()
    expect(fileUrl('')).toBeUndefined()
  })
})

describe('displayName 展示名兜底', () => {
  it('优先使用昵称', () => {
    expect(displayName('张三', 'zhangsan')).toBe('张三')
  })

  it('昵称为空时回退用户名', () => {
    expect(displayName('', 'zhangsan')).toBe('zhangsan')
  })

  it('昵称与用户名都为空时返回匿名用户', () => {
    expect(displayName(null, null)).toBe('匿名用户')
  })
})

describe('ROUTES 路由常量', () => {
  it('静态路径正确', () => {
    expect(ROUTES.home).toBe('/')
    expect(ROUTES.login).toBe('/login')
    expect(ROUTES.adminUsers).toBe('/admin/users')
  })

  it('动态路径正确拼接', () => {
    expect(ROUTES.article(12)).toBe('/articles/12')
    expect(ROUTES.user('abc')).toBe('/u/abc')
    expect(ROUTES.edit(7)).toBe('/edit/7')
  })
})

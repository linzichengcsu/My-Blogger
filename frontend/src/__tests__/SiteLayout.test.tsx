import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'

const { authState } = vi.hoisted(() => ({
  authState: {
    user: null as null | { displayName?: string; username?: string },
    isLoggedIn: false,
    isAdmin: false,
    logout: vi.fn(),
  },
}))

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => authState,
}))

import SiteLayout from '@/components/SiteLayout'

function LocationProbe() {
  const { pathname, search } = useLocation()
  return <div data-testid="location">{`${pathname}${search}`}</div>
}

function renderLayout(initialPath = '/') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route element={<SiteLayout />}>
          <Route index element={<div />} />
        </Route>
      </Routes>
      <LocationProbe />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  authState.user = null
  authState.isLoggedIn = false
  authState.isAdmin = false
  authState.logout.mockReset()
})

describe('SiteLayout', () => {
  it('未登录时展示登录/注册按钮', () => {
    renderLayout()

    expect(screen.getByText('行吟博客')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /登录/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /注.*册/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /写文章/ })).not.toBeInTheDocument()
  })

  it('已登录时展示用户名与写文章入口', () => {
    authState.isLoggedIn = true
    authState.user = { displayName: '张三', username: 'zhangsan' }
    renderLayout()

    expect(screen.getByText('张三')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /写文章/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /注册/ })).not.toBeInTheDocument()
  })

  it('管理员额外展示管理后台按钮', () => {
    authState.isLoggedIn = true
    authState.isAdmin = true
    authState.user = { displayName: '管理员' }
    renderLayout()

    expect(screen.getByRole('button', { name: /管理后台/ })).toBeInTheDocument()
  })

  it('搜索框回车后跳转到首页关键词结果', async () => {
    const user = userEvent.setup()
    renderLayout()

    const input = screen.getByPlaceholderText('搜索文章标题、内容…')
    await user.type(input, 'React{Enter}')

    expect(screen.getByTestId('location')).toHaveTextContent('/?keyword=React')
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

vi.mock('@/api', () => ({
  getAccessToken: vi.fn(),
  clearAuthTokens: vi.fn(),
  getMyInfo: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

import { getAccessToken, clearAuthTokens, getMyInfo, login, logout } from '@/api'
import { AuthProvider, useAuth } from '@/context/AuthContext'

const mocked = {
  getAccessToken: vi.mocked(getAccessToken),
  clearAuthTokens: vi.mocked(clearAuthTokens),
  getMyInfo: vi.mocked(getMyInfo),
  login: vi.mocked(login),
  logout: vi.mocked(logout),
}

function Probe() {
  const { user, loading, isLoggedIn, isAdmin, login: doLogin, logout: doLogout } = useAuth()
  return (
    <div>
      <span data-testid="loading">{String(loading)}</span>
      <span data-testid="user">{user?.displayName ?? 'none'}</span>
      <span data-testid="logged">{String(isLoggedIn)}</span>
      <span data-testid="admin">{String(isAdmin)}</span>
      <button onClick={() => void doLogin({ username: 'u', password: 'p' })}>登录</button>
      <button onClick={doLogout}>退出</button>
    </div>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('AuthProvider', () => {
  it('本地无 Token 时保持未登录状态', async () => {
    mocked.getAccessToken.mockReturnValue(null)

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    expect(await screen.findByTestId('loading')).toHaveTextContent('false')
    expect(screen.getByTestId('user')).toHaveTextContent('none')
    expect(screen.getByTestId('logged')).toHaveTextContent('false')
    expect(mocked.getMyInfo).not.toHaveBeenCalled()
  })

  it('本地有 Token 时拉取用户信息并恢复登录态', async () => {
    mocked.getAccessToken.mockReturnValue('token')
    mocked.getMyInfo.mockResolvedValue({ id: 1, displayName: '张三', role: 'USER' })

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    expect(await screen.findByTestId('user')).toHaveTextContent('张三')
    expect(screen.getByTestId('logged')).toHaveTextContent('true')
    expect(screen.getByTestId('admin')).toHaveTextContent('false')
  })

  it('管理员角色 isAdmin 为 true', async () => {
    mocked.getAccessToken.mockReturnValue('token')
    mocked.getMyInfo.mockResolvedValue({ id: 1, displayName: '管理员', role: 'SUPER_ADMIN' })

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    expect(await screen.findByTestId('admin')).toHaveTextContent('true')
  })

  it('拉取用户信息失败时清理 Token', async () => {
    mocked.getAccessToken.mockReturnValue('token')
    mocked.getMyInfo.mockRejectedValue(new Error('401'))

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    expect(await screen.findByTestId('loading')).toHaveTextContent('false')
    expect(mocked.clearAuthTokens).toHaveBeenCalledTimes(1)
    expect(screen.getByTestId('user')).toHaveTextContent('none')
  })

  it('login 成功后刷新用户信息', async () => {
    mocked.getAccessToken.mockReturnValue(null)
    mocked.login.mockImplementation(async () => {
      mocked.getAccessToken.mockReturnValue('new-token')
      return { accessToken: 'a', user: { id: 2, displayName: '李四', role: 'USER' } }
    })
    mocked.getMyInfo.mockResolvedValue({ id: 2, displayName: '李四', role: 'USER' })
    const user = userEvent.setup()

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    await screen.findByTestId('loading')
    await user.click(screen.getByRole('button', { name: '登录' }))

    expect(await screen.findByTestId('user')).toHaveTextContent('李四')
    expect(mocked.login).toHaveBeenCalledWith({ username: 'u', password: 'p' })
  })

  it('logout 清理用户状态', async () => {
    mocked.getAccessToken.mockReturnValue('token')
    mocked.getMyInfo.mockResolvedValue({ id: 1, displayName: '张三', role: 'USER' })
    const user = userEvent.setup()

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    await screen.findByTestId('user')
    await user.click(screen.getByRole('button', { name: '退出' }))

    expect(mocked.logout).toHaveBeenCalledTimes(1)
    expect(screen.getByTestId('user')).toHaveTextContent('none')
  })
})

describe('useAuth', () => {
  it('在 Provider 外调用时抛错', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    expect(() => render(<Probe />)).toThrow('useAuth 必须在 AuthProvider 内使用')
    spy.mockRestore()
  })
})

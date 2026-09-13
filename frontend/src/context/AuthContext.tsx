/**
 * 登录态上下文。
 * - 启动时若本地存在 Token，则拉取 /users/me 恢复用户信息
 * - 暴露 login / logout / refreshUser，全站共享一份用户状态
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { message } from 'antd'
import {
  clearAuthTokens,
  getAccessToken,
  getMyInfo,
  login as loginApi,
  logout as logoutApi,
} from '@/api'
import type { UserDetailDTO, UserLoginRequest } from '@/types'

interface AuthContextValue {
  user: UserDetailDTO | null
  loading: boolean
  isLoggedIn: boolean
  isAdmin: boolean
  login: (data: UserLoginRequest) => Promise<UserDetailDTO | undefined>
  logout: () => void
  refreshUser: () => Promise<void>
  setUser: (user: UserDetailDTO | null) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserDetailDTO | null>(null)
  const [loading, setLoading] = useState(true)

  const refreshUser = useCallback(async () => {
    if (!getAccessToken()) {
      setUser(null)
      return
    }
    try {
      setUser(await getMyInfo())
    } catch {
      clearAuthTokens()
      setUser(null)
    }
  }, [])

  useEffect(() => {
    void (async () => {
      await refreshUser()
      setLoading(false)
    })()
  }, [refreshUser])

  const login = useCallback(async (data: UserLoginRequest) => {
    const res = await loginApi(data)
    await refreshUser()
    return res.user
  }, [refreshUser])

  const logout = useCallback(() => {
    logoutApi()
    setUser(null)
    message.success('已退出登录')
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      loading,
      isLoggedIn: Boolean(user),
      isAdmin: user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN',
      login,
      logout,
      refreshUser,
      setUser,
    }),
    [user, loading, login, logout, refreshUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

/** 获取登录态；禁止在 Provider 外使用 */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth 必须在 AuthProvider 内使用')
  return ctx
}

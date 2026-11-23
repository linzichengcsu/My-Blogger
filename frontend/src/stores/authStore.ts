/**
 * 登录态全局 store。
 * - 启动时若本地存在 Token，则拉取 /users/me 恢复用户信息
 * - 暴露 login / logout / refreshUser，全站共享一份用户状态
 *
 * 迁移自 context/AuthContext.tsx —— 用法不变，只是从 Context 换成 Zustand。
 */
import { create } from 'zustand'
import { clearAuthTokens, getAccessToken, getMyInfo, login as loginApi, logout as logoutApi } from '@/api'
import type { UserDetailDTO, UserLoginRequest } from '@/types'

interface AuthState {
  user: UserDetailDTO | null
  loading: boolean
  /** 用户是否已登录（派生值） */
  isLoggedIn: boolean
  /** 当前用户是否管理员（派生值） */
  isAdmin: boolean

  refreshUser: () => Promise<void>
  login: (data: UserLoginRequest) => Promise<UserDetailDTO | undefined>
  logout: () => void
  setUser: (user: UserDetailDTO | null) => void
  /** 应用启动时调用一次，恢复登录态并结束 loading */
  init: () => Promise<void>
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  loading: true,
  isLoggedIn: false,
  isAdmin: false,

  setUser: (user) => {
    set({
      user,
      isLoggedIn: Boolean(user),
      isAdmin: user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN',
    })
  },

  refreshUser: async () => {
    if (!getAccessToken()) {
      get().setUser(null)
      return
    }
    try {
      get().setUser(await getMyInfo())
    } catch {
      clearAuthTokens()
      get().setUser(null)
    }
  },

  login: async (data) => {
    const res = await loginApi(data)
    await get().refreshUser()
    return res.user
  },

  logout: () => {
    logoutApi()
    get().setUser(null)
  },

  init: async () => {
    await get().refreshUser()
    set({ loading: false })
  },
}))

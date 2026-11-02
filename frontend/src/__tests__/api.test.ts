import { beforeEach, describe, expect, it, vi } from 'vitest'

const { httpMock, setAuthTokensMock } = vi.hoisted(() => ({
  httpMock: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    del: vi.fn(),
  },
  setAuthTokensMock: vi.fn(),
}))

vi.mock('@/api/request', () => ({
  http: httpMock,
  service: { get: vi.fn(), request: vi.fn() },
  setAuthTokens: setAuthTokensMock,
  clearAuthTokens: vi.fn(),
  getAccessToken: vi.fn(),
  getRefreshToken: vi.fn(),
  ApiError: class ApiError extends Error {},
}))

import { login as loginApi, logout as logoutApi, register, refreshToken } from '@/api/auth'
import { getArticleById, getArticleList, searchArticles, batchDeleteArticles } from '@/api/article'
import { deleteUser, getMyInfo, getAllUsers } from '@/api/user'
import { uploadFile } from '@/api/file'

beforeEach(() => {
  Object.values(httpMock).forEach((fn) => fn.mockReset())
  setAuthTokensMock.mockReset()
})

describe('认证接口', () => {
  it('login 成功后保存 Token', async () => {
    httpMock.post.mockResolvedValue({
      accessToken: 'a',
      refreshToken: 'r',
      expiresIn: 3600,
      user: { id: 1 },
    })

    const res = await loginApi({ username: 'u', password: 'p' })

    expect(httpMock.post).toHaveBeenCalledWith('/users/login', { username: 'u', password: 'p' })
    expect(setAuthTokensMock).toHaveBeenCalledWith({ accessToken: 'a', refreshToken: 'r', expiresIn: 3600 })
    expect(res.user).toEqual({ id: 1 })
  })

  it('login 响应缺少 accessToken 时不写入存储', async () => {
    httpMock.post.mockResolvedValue({ user: { id: 1 } })

    await loginApi({ username: 'u', password: 'p' })

    expect(setAuthTokensMock).not.toHaveBeenCalled()
  })

  it('register 调用 /users/register', async () => {
    httpMock.post.mockResolvedValue({ id: 2 })

    await register({ username: 'u', email: 'u@example.com', password: 'Passw0rd!' })

    expect(httpMock.post).toHaveBeenCalledWith('/users/register', {
      username: 'u',
      email: 'u@example.com',
      password: 'Passw0rd!',
    })
  })

  it('refreshToken 通过 query 参数传递', async () => {
    httpMock.post.mockResolvedValue({ accessToken: 'new' })

    await refreshToken('r-1')

    expect(httpMock.post).toHaveBeenCalledWith('/users/refresh', null, { params: { refreshToken: 'r-1' } })
  })

  it('logout 不发起网络请求', () => {
    logoutApi()
    expect(httpMock.post).not.toHaveBeenCalled()
  })
})

describe('文章接口', () => {
  it('列表查询透传分页参数', async () => {
    httpMock.get.mockResolvedValue({ content: [] })

    await getArticleList({ page: 2, size: 8, sortBy: 'createdAt', sortDirection: 'desc' })

    expect(httpMock.get).toHaveBeenCalledWith('/articles', {
      params: { page: 2, size: 8, sortBy: 'createdAt', sortDirection: 'desc' },
    })
  })

  it('详情与搜索路径正确', async () => {
    httpMock.get.mockResolvedValue({})

    await getArticleById(7)
    await searchArticles('react', { page: 1 })

    expect(httpMock.get).toHaveBeenNthCalledWith(1, '/articles/7')
    expect(httpMock.get).toHaveBeenNthCalledWith(2, '/articles/search', {
      params: { keyword: 'react', page: 1 },
    })
  })

  it('批量删除按约定请求体发送', async () => {
    httpMock.post.mockResolvedValue(2)

    await batchDeleteArticles([1, 2])

    expect(httpMock.post).toHaveBeenCalledWith('/articles/batch/delete', { ids: [1, 2] })
  })
})

describe('用户接口', () => {
  it('getMyInfo 请求 /users/me', async () => {
    httpMock.get.mockResolvedValue({ id: 1 })

    await getMyInfo()

    expect(httpMock.get).toHaveBeenCalledWith('/users/me')
  })

  it('getAllUsers 透传分页参数', async () => {
    httpMock.get.mockResolvedValue({ content: [] })

    await getAllUsers({ page: 0, size: 10 })

    expect(httpMock.get).toHaveBeenCalledWith('/users', { params: { page: 0, size: 10 } })
  })

  it('deleteUser 默认软删除', async () => {
    httpMock.del.mockResolvedValue(undefined)

    await deleteUser(3)

    expect(httpMock.del).toHaveBeenCalledWith('/users/3', { params: { softDelete: true } })
    await deleteUser(3, false)
    expect(httpMock.del).toHaveBeenLastCalledWith('/users/3', { params: { softDelete: false } })
  })
})

describe('文件接口', () => {
  it('uploadFile 使用 multipart 并回调进度', async () => {
    httpMock.post.mockImplementation((_url, _data, options) => {
      options.onUploadProgress({ loaded: 50, total: 100 })
      return Promise.resolve({ fileName: 'a.png' })
    })
    const onProgress = vi.fn()

    const file = new File(['x'], 'a.png', { type: 'image/png' })
    await uploadFile(file, onProgress)

    expect(httpMock.post).toHaveBeenCalledWith(
      '/files/upload',
      expect.any(FormData),
      expect.objectContaining({ headers: { 'Content-Type': 'multipart/form-data' } }),
    )
    expect(onProgress).toHaveBeenCalledWith(50)
  })
})

import axios from 'axios'
import MockAdapter from 'axios-mock-adapter'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  TOKEN_STORAGE_KEYS,
  clearAuthTokens,
  getAccessToken,
  getRefreshToken,
  http,
  service,
  setAuthTokens,
  setUnauthorizedHandler,
} from '@/api/request'

const mockService = new MockAdapter(service)
const mockAxios = new MockAdapter(axios)

beforeEach(() => {
  localStorage.clear()
  mockService.reset()
  mockAxios.reset()
  setUnauthorizedHandler(null)
})

afterEach(() => {
  setUnauthorizedHandler(null)
})

describe('Token 存取', () => {
  it('setAuthTokens 写入 localStorage', () => {
    setAuthTokens({ accessToken: 'access-1', refreshToken: 'refresh-1', expiresIn: 3600 })

    expect(getAccessToken()).toBe('access-1')
    expect(getRefreshToken()).toBe('refresh-1')
    expect(localStorage.getItem(TOKEN_STORAGE_KEYS.expiresIn)).toBe('3600')
  })

  it('clearAuthTokens 清空全部登录态', () => {
    setAuthTokens({ accessToken: 'access-1', refreshToken: 'refresh-1', expiresIn: 3600 })
    clearAuthTokens()

    expect(getAccessToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()
    expect(localStorage.getItem(TOKEN_STORAGE_KEYS.expiresIn)).toBeNull()
  })
})

describe('ApiError', () => {
  it('保留 status / code / data 与错误信息', () => {
    const error = new ApiError('出错了', { status: 400, code: 40001, data: { field: 'title' } })

    expect(error).toBeInstanceOf(Error)
    expect(error.name).toBe('ApiError')
    expect(error.message).toBe('出错了')
    expect(error.status).toBe(400)
    expect(error.code).toBe(40001)
    expect(error.data).toEqual({ field: 'title' })
  })
})

describe('响应解包', () => {
  it('Result 包装成功时返回 data', async () => {
    mockService.onGet('/articles').reply(200, {
      code: 200,
      message: 'success',
      data: { content: [{ id: 1 }] },
    })

    await expect(http.get('/articles')).resolves.toEqual({ content: [{ id: 1 }] })
  })

  it('Result 业务失败时抛出 ApiError', async () => {
    mockService.onGet('/articles/1').reply(200, { code: 404, message: '文章不存在', data: null })

    await expect(http.get('/articles/1')).rejects.toMatchObject({
      name: 'ApiError',
      message: '文章不存在',
      code: 404,
    })
  })

  it('ApiResponse 包装成功时返回 data', async () => {
    mockService.onGet('/admin/dashboard').reply(200, {
      success: true,
      message: 'ok',
      data: { totalUsers: 12 },
    })

    await expect(http.get('/admin/dashboard')).resolves.toEqual({ totalUsers: 12 })
  })

  it('ApiResponse 失败时抛出 ApiError 并带 errorCode', async () => {
    mockService.onGet('/admin/dashboard').reply(200, {
      success: false,
      message: '无权限',
      errorCode: 'FORBIDDEN',
      data: null,
    })

    await expect(http.get('/admin/dashboard')).rejects.toMatchObject({
      name: 'ApiError',
      message: '无权限',
      code: 'FORBIDDEN',
    })
  })

  it('非标准包装（裸数据）原样返回', async () => {
    mockService.onGet('/ping').reply(200, 'pong')

    await expect(http.get('/ping')).resolves.toBe('pong')
  })
})

describe('请求拦截器', () => {
  it('存在 accessToken 时注入 Authorization 头', async () => {
    setAuthTokens({ accessToken: 'token-abc' })
    mockService.onGet('/users/me').reply((config) => {
      // axios-mock-adapter 的 headers 类型较松，这里用 any 绕开
      const h = config.headers as any
      const auth = typeof h?.get === 'function' ? h.get('Authorization') : h?.Authorization
      expect(auth).toBe('Bearer token-abc')
      return [200, { code: 200, message: 'success', data: { id: 1 } }]
    })

    await http.get('/users/me')
  })
})

describe('HTTP 错误规范化', () => {
  it('HTTP 500 转为 ApiError 并带上状态码', async () => {
    mockService.onGet('/boom').reply(500, { message: '服务器内部错误' })

    await expect(http.get('/boom')).rejects.toMatchObject({
      name: 'ApiError',
      message: '服务器内部错误',
      status: 500,
    })
  })

  it('无响应体时按状态码生成默认文案', async () => {
    mockService.onGet('/empty-error').reply(502)

    await expect(http.get('/empty-error')).rejects.toMatchObject({
      message: '请求失败（HTTP 502）',
      status: 502,
    })
  })
})

describe('401 静默刷新', () => {
  it('无 refreshToken 时清理登录态并触发登出回调', async () => {
    setAuthTokens({ accessToken: 'expired' })
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    mockService.onGet('/users/me').reply(401)

    await expect(http.get('/users/me')).rejects.toMatchObject({
      name: 'ApiError',
      message: '登录已过期，请重新登录',
      status: 401,
    })
    expect(getAccessToken()).toBeNull()
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('刷新成功后重放原请求并保存新 Token', async () => {
    setAuthTokens({ accessToken: 'old-token', refreshToken: 'refresh-1' })
    mockAxios.onPost('/api/users/refresh').reply(200, {
      code: 200,
      message: 'success',
      data: { accessToken: 'new-token', refreshToken: 'refresh-2', expiresIn: 3600 },
    })
    let callCount = 0
    mockService.onGet('/users/me').reply(() => {
      callCount += 1
      if (callCount === 1) return [401, { message: '过期' }]
      return [200, { code: 200, message: 'success', data: { id: 9 } }]
    })

    await expect(http.get('/users/me')).resolves.toEqual({ id: 9 })
    expect(getAccessToken()).toBe('new-token')
    expect(getRefreshToken()).toBe('refresh-2')
  })

  it('刷新失败时清理登录态、触发登出回调并抛出 ApiError', async () => {
    setAuthTokens({ accessToken: 'old-token', refreshToken: 'bad-refresh' })
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    mockAxios.onPost('/api/users/refresh').reply(200, { code: 401, message: '刷新令牌无效' })
    mockService.onGet('/users/me').reply(401, { message: '过期' })

    await expect(http.get('/users/me')).rejects.toMatchObject({
      name: 'ApiError',
      message: '刷新令牌无效',
    })
    expect(getAccessToken()).toBeNull()
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })
})

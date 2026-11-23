/**
 * 测试环境初始化：
 * - jest-dom 断言扩展（toBeInTheDocument 等）
 * - antd v5 在 React 19 下的兼容补丁
 * - jsdom 缺失的浏览器 API（matchMedia / ResizeObserver / scrollTo）
 */
import '@testing-library/jest-dom/vitest'
import '@ant-design/v5-patch-for-react-19'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

afterEach(() => {
  cleanup()
})

// Node 22+ 在没有 --localstorage-file 时会用自己未启用的 localStorage 覆盖 jsdom 的实现，
// 导致 window.localStorage 为 undefined（测试里 localStorage.clear() 直接报错）。
// 这里补一个符合 Storage 接口的内存实现。
if (!window.localStorage) {
  class LocalStorageMock implements Storage {
    private store = new Map<string, string>()

    get length(): number {
      return this.store.size
    }

    clear(): void {
      this.store.clear()
    }

    getItem(key: string): string | null {
      return this.store.get(key) ?? null
    }

    key(index: number): string | null {
      return [...this.store.keys()][index] ?? null
    }

    removeItem(key: string): void {
      this.store.delete(key)
    }

    setItem(key: string, value: string): void {
      this.store.set(key, String(value))
    }
  }

  Object.defineProperty(window, 'localStorage', {
    value: new LocalStorageMock(),
    writable: true,
    configurable: true,
  })
}

// antd 的响应式组件依赖 matchMedia，jsdom 未实现
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})

// antd 的 AutoResize / Table 等组件依赖 ResizeObserver，jsdom 未实现
class ResizeObserverMock {
  observe = vi.fn()
  unobserve = vi.fn()
  disconnect = vi.fn()
}
window.ResizeObserver = window.ResizeObserver ?? (ResizeObserverMock as unknown as typeof ResizeObserver)

window.scrollTo = vi.fn()

/**
 * 全站路由路径常量。
 * 页面/组件一律引用此处，禁止硬编码路径字符串。
 */
export const ROUTES = {
  home: '/',
  login: '/login',
  register: '/register',
  article: (id: number | string) => `/articles/${id}`,
  user: (userId: number | string) => `/u/${userId}`,
  profile: '/profile',
  write: '/write',
  edit: (id: number | string) => `/edit/${id}`,
  admin: '/admin',
  adminDashboard: '/admin/dashboard',
  adminArticles: '/admin/articles',
  adminCategories: '/admin/categories',
  adminComments: '/admin/comments',
  adminUsers: '/admin/users',
} as const

/**
 * 应用根组件：路由表 + 登录守卫 + Ant Design 全局配置。
 *
 * 结构刻意保持扁平：所有路由共用一个 SiteLayout，
 * 仅 RequireAuth / RequireAdmin 两个守卫组件，不再新增额外包装层。
 */
import { useEffect } from 'react'
import { BrowserRouter, Navigate, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { App as AntdApp, Button, ConfigProvider, Result, Spin, theme } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import SiteLayout from '@/components/SiteLayout'
import { AuthProvider, useAuth } from '@/context/AuthContext'
import { setUnauthorizedHandler } from '@/api'
import { ROUTES } from '@/constants/routes'
import { LoginPage, RegisterPage } from '@/pages/AuthPage'
import HomePage from '@/pages/HomePage'
import ArticleDetailPage from '@/pages/ArticleDetailPage'
import ProfilePage from '@/pages/ProfilePage'
import WritePage from '@/pages/WritePage'
import AdminLayout from '@/pages/admin/AdminLayout'
import DashboardPage from '@/pages/admin/DashboardPage'
import ArticleManagePage from '@/pages/admin/ArticleManagePage'
import CategoryManagePage from '@/pages/admin/CategoryManagePage'
import CommentManagePage from '@/pages/admin/CommentManagePage'
import UserManagePage from '@/pages/admin/UserManagePage'

/** 401 刷新失败后的统一处理：清状态并跳登录页 */
function AuthBridge() {
  const navigate = useNavigate()
  const { setUser } = useAuth()
  useEffect(() => {
    setUnauthorizedHandler(() => {
      setUser(null)
      navigate(ROUTES.login)
    })
    return () => setUnauthorizedHandler(null)
  }, [navigate, setUser])
  return null
}

function FullScreenLoading() {
  return <div style={{ textAlign: 'center', padding: 96 }}><Spin size="large" /></div>
}

/** 登录守卫 */
function RequireAuth() {
  const { isLoggedIn, loading } = useAuth()
  const location = useLocation()
  if (loading) return <FullScreenLoading />
  return isLoggedIn
    ? <Outlet />
    : <Navigate to={ROUTES.login} state={{ from: location.pathname }} replace />
}

/** 管理员守卫 */
function RequireAdmin() {
  const { isAdmin, loading } = useAuth()
  if (loading) return <FullScreenLoading />
  return isAdmin ? <Outlet /> : <Navigate to={ROUTES.home} replace />
}

function NotFound() {
  const navigate = useNavigate()
  return (
    <Result
      status="404"
      title="404"
      subTitle="页面走丢了，看看其他文章吧"
      extra={<Button type="primary" onClick={() => navigate(ROUTES.home)}>返回首页</Button>}
    />
  )
}

export default function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#fa541c',
          borderRadius: 8,
          fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
        },
        components: {
          Layout: { headerBg: '#ffffff', headerHeight: 60 },
        },
        algorithm: theme.defaultAlgorithm,
      }}
    >
      <AntdApp>
        <BrowserRouter>
          <AuthProvider>
            <AuthBridge />
            <Routes>
              <Route element={<SiteLayout />}>
                <Route index element={<HomePage />} />
                <Route path="articles/:id" element={<ArticleDetailPage />} />
                <Route path="u/:userId" element={<ProfilePage />} />
                <Route path="login" element={<LoginPage />} />
                <Route path="register" element={<RegisterPage />} />

                <Route element={<RequireAuth />}>
                  <Route path="profile" element={<ProfilePage />} />
                  <Route path="write" element={<WritePage />} />
                  <Route path="edit/:id" element={<WritePage />} />

                  <Route path="admin" element={<RequireAdmin />}>
                    <Route element={<AdminLayout />}>
                      <Route index element={<Navigate to="dashboard" replace />} />
                      <Route path="dashboard" element={<DashboardPage />} />
                      <Route path="articles" element={<ArticleManagePage />} />
                      <Route path="categories" element={<CategoryManagePage />} />
                      <Route path="comments" element={<CommentManagePage />} />
                      <Route path="users" element={<UserManagePage />} />
                    </Route>
                  </Route>
                </Route>

                <Route path="*" element={<NotFound />} />
              </Route>
            </Routes>
          </AuthProvider>
        </BrowserRouter>
      </AntdApp>
    </ConfigProvider>
  )
}

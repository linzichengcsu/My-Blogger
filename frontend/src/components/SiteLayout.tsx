/**
 * 全站外壳（微博式白色吸顶导航 + 居中内容区 + 页脚）。
 * 前台页面与 /admin 子路由均挂在本布局的 Outlet 下，不再单做第二套 Layout。
 */
import { useEffect, useState } from 'react'
import { Avatar, Button, Dropdown, Input, Layout, Space, Typography } from 'antd'
import type { MenuProps } from 'antd'
import {
  AppstoreOutlined,
  BookOutlined,
  EditOutlined,
  LoginOutlined,
  LogoutOutlined,
  SearchOutlined,
  SettingOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Outlet, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'
import { ROUTES } from '@/constants/routes'
import { displayName, fileUrl } from '@/utils'

const { Header, Content, Footer } = Layout
const { Text } = Typography

/** 路由切换时回到顶部 */
function ScrollToTop() {
  const { pathname } = useLocation()
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [pathname])
  return null
}

export default function SiteLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const { user, isLoggedIn, isAdmin, logout } = useAuth()
  const [keyword, setKeyword] = useState(searchParams.get('keyword') ?? '')

  // 从详情页返回首页时同步 URL 中的搜索词
  useEffect(() => {
    if (location.pathname === ROUTES.home) {
      setKeyword(searchParams.get('keyword') ?? '')
    }
  }, [location.pathname, searchParams])

  const userMenu: MenuProps['items'] = [
    { key: 'profile', icon: <UserOutlined />, label: '个人中心' },
    { key: 'write', icon: <EditOutlined />, label: '写文章' },
    ...(isAdmin
      ? [{ type: 'divider' as const }, { key: 'admin', icon: <SettingOutlined />, label: '管理后台' }]
      : []),
    { type: 'divider' as const },
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', danger: true },
  ]

  const onUserMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'logout') logout()
    else if (key === 'profile') navigate(ROUTES.profile)
    else if (key === 'write') navigate(ROUTES.write)
    else if (key === 'admin') navigate(ROUTES.admin)
  }

  return (
    <Layout style={{ minHeight: '100vh', background: '#f5f5f5' }}>
      <ScrollToTop />
      <Header
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 100,
          display: 'flex',
          alignItems: 'center',
          gap: 24,
          padding: '0 24px',
          background: '#fff',
          boxShadow: '0 1px 4px rgba(0,0,0,0.08)',
        }}
      >
        <Space
          size={8}
          style={{ cursor: 'pointer', fontSize: 20, fontWeight: 700, color: '#fa541c', whiteSpace: 'nowrap' }}
          onClick={() => navigate(ROUTES.home)}
        >
          <BookOutlined />
          个人博客
        </Space>

        <Input
          allowClear
          prefix={<SearchOutlined style={{ color: '#bbb' }} />}
          placeholder="搜索文章标题、内容…"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onPressEnter={() => navigate(`${ROUTES.home}?keyword=${encodeURIComponent(keyword.trim())}`)}
          style={{ maxWidth: 420, margin: '0 auto', borderRadius: 16 }}
        />

        <Space size={12} style={{ marginLeft: 'auto' }}>
          {isLoggedIn ? (
            <>
              <Button type="primary" ghost icon={<EditOutlined />} onClick={() => navigate(ROUTES.write)}>
                写文章
              </Button>
              {isAdmin && (
                <Button icon={<AppstoreOutlined />} onClick={() => navigate(ROUTES.admin)}>
                  管理后台
                </Button>
              )}
              <Dropdown menu={{ items: userMenu, onClick: onUserMenuClick }} placement="bottomRight">
                <Space size={8} style={{ cursor: 'pointer' }}>
                  <Avatar src={fileUrl(user?.avatar)} style={{ backgroundColor: '#fa541c' }}>
                    {displayName(user?.displayName, user?.username).slice(0, 1)}
                  </Avatar>
                  <Text className="layout-username">
                    {displayName(user?.displayName, user?.username)}
                  </Text>
                </Space>
              </Dropdown>
            </>
          ) : (
            <>
              <Button type="primary" icon={<LoginOutlined />} onClick={() => navigate(ROUTES.login)}>
                登录
              </Button>
              <Button onClick={() => navigate(ROUTES.register)}>注册</Button>
            </>
          )}
        </Space>
      </Header>

      <Content style={{ maxWidth: 1040, width: '100%', margin: '0 auto', padding: '24px 16px 48px' }}>
        <Outlet />
      </Content>

      <Footer style={{ textAlign: 'center', background: 'transparent', color: '#999' }}>
        My Personal Blogger · React + Ant Design · ©{new Date().getFullYear()}
      </Footer>
    </Layout>
  )
}

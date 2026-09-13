/**
 * 管理后台外壳：顶部横向菜单 + Outlet。
 * 直接复用全站 SiteLayout 的导航，不再额外实现 Sider 版第二套布局。
 */
import { Card, Menu } from 'antd'
import {
  AppstoreOutlined,
  DashboardOutlined,
  FileTextOutlined,
  MessageOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { ROUTES } from '@/constants/routes'

const items = [
  { key: ROUTES.adminDashboard, icon: <DashboardOutlined />, label: '数据看板' },
  { key: ROUTES.adminArticles, icon: <FileTextOutlined />, label: '文章管理' },
  { key: ROUTES.adminCategories, icon: <AppstoreOutlined />, label: '分类管理' },
  { key: ROUTES.adminComments, icon: <MessageOutlined />, label: '评论审核' },
  { key: ROUTES.adminUsers, icon: <TeamOutlined />, label: '用户管理' },
]

export default function AdminLayout() {
  const navigate = useNavigate()
  const { pathname } = useLocation()

  return (
    <>
      <Card styles={{ body: { padding: 8, marginBottom: 16 } }}>
        <Menu
          mode="horizontal"
          selectedKeys={[pathname]}
          items={items}
          onClick={({ key }) => navigate(key)}
          style={{ borderBottom: 'none' }}
        />
      </Card>
      <Outlet />
    </>
  )
}

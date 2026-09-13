/**
 * React 19 对 antd v5 的兼容补丁，必须在使用 antd 前导入。
 * https://ant.design/docs/react/react-19-cn
 */
import '@ant-design/v5-patch-for-react-19'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import 'dayjs/locale/zh-cn'
import './index.css'
import App from './App'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)

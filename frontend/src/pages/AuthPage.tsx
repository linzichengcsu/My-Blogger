/**
 * 登录 / 注册 / 忘记密码（同一文件导出三个页面，减少层级）。
 * 居中卡片式布局，注册成功后自动登录并跳转回来源页。
 */
import { useEffect, useState } from 'react'
import { BookOutlined, LockOutlined, MailOutlined, SafetyOutlined, UserOutlined } from '@ant-design/icons'
import { Button, Card, Form, Input, message, Steps, Typography } from 'antd'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { forgotPassword, register, sendPasswordResetCode } from '@/api'
import { ApiError } from '@/api/request'
import { useAuth } from '@/context/AuthContext'
import type { UserLoginRequest, UserPasswordCodeRequest, UserPasswordResetRequest, UserRegisterRequest } from '@/types'
import { ROUTES } from '@/constants/routes'

const { Title, Text } = Typography

/** 与后端 PasswordValidator 对齐：8-20 位，含大小写字母、数字、特殊字符 */
const passwordRules = [
  { required: true, message: '请输入密码' },
  { min: 8, max: 20, message: '密码长度 8-20 位' },
  {
    pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/,
    message: '需包含大小写字母、数字和特殊字符',
  },
]

function AuthCard({ title, children, footer }: { title: string; children: React.ReactNode; footer: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 48 }}>
      <Card style={{ width: 400 }} styles={{ body: { padding: 32 } }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <BookOutlined style={{ fontSize: 36, color: '#fa541c' }} />
          <Title level={3} style={{ margin: '12px 0 0' }}>
            {title}
          </Title>
        </div>
        {children}
        <div style={{ textAlign: 'center', marginTop: 16 }}>{footer}</div>
      </Card>
    </div>
  )
}

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { login } = useAuth()
  const [loading, setLoading] = useState(false)
  const from = (location.state as { from?: string } | null)?.from ?? ROUTES.home

  const onFinish = async (values: UserLoginRequest) => {
    setLoading(true)
    try {
      await login(values)
      message.success('登录成功')
      navigate(from, { replace: true })
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '登录失败，请重试')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthCard
      title="欢迎回来"
      footer={
        <Text type="secondary">
          还没有账号？<Link to={ROUTES.register}>立即注册</Link>
          <span style={{ margin: '0 8px' }}>·</span>
          <Link to={ROUTES.forgotPassword}>忘记密码</Link>
        </Text>
      }
    >
      <Form size="large" onFinish={onFinish} autoComplete="off">
        <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
          <Input prefix={<UserOutlined />} placeholder="用户名" />
        </Form.Item>
        <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
          <Input.Password prefix={<LockOutlined />} placeholder="密码" />
        </Form.Item>
        <Button type="primary" htmlType="submit" block loading={loading}>
          登录
        </Button>
      </Form>
    </AuthCard>
  )
}

export function ForgotPasswordPage() {
  const navigate = useNavigate()
  const [step, setStep] = useState(0)
  const [username, setUsername] = useState('')
  const [loading, setLoading] = useState(false)
  const [countdown, setCountdown] = useState(0)
  const [codeForm] = Form.useForm<UserPasswordCodeRequest>()
  const [resetForm] = Form.useForm<UserPasswordResetRequest & { confirmPassword: string }>()

  // 60 秒发送冷却倒计时（组件卸载时自动清理定时器）
  useEffect(() => {
    if (countdown <= 0) return
    const timer = setInterval(() => setCountdown((c) => c - 1), 1000)
    return () => clearInterval(timer)
  }, [countdown > 0])

  const onSendCode = async (values: UserPasswordCodeRequest) => {
    setLoading(true)
    try {
      await sendPasswordResetCode(values)
      setUsername(values.username)
      setCountdown(60)
      setStep(1)
      message.success('验证码已发送，请查收邮件')
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '验证码发送失败，请重试')
    } finally {
      setLoading(false)
    }
  }

  const onReset = async (values: UserPasswordResetRequest & { confirmPassword: string }) => {
    setLoading(true)
    try {
      await forgotPassword({ username, code: values.code, newPassword: values.newPassword })
      message.success('密码重置成功，请使用新密码登录')
      navigate(ROUTES.login, { replace: true })
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '重置失败，请重试')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthCard title="忘记密码" footer={<Text type="secondary">想起来了？<Link to={ROUTES.login}>去登录</Link></Text>}>
      <Steps
        size="small"
        current={step}
        items={[{ title: '验证身份' }, { title: '重置密码' }]}
        style={{ marginBottom: 24 }}
      />
      {step === 0 ? (
        <Form form={codeForm} size="large" onFinish={onSendCode} autoComplete="off">
          <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} placeholder="用户名" />
          </Form.Item>
          <Form.Item name="email" rules={[{ required: true, message: '请输入注册邮箱' }, { type: 'email', message: '邮箱格式不正确' }]}>
            <Input prefix={<MailOutlined />} placeholder="注册时使用的邮箱" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading} disabled={countdown > 0}>
            {countdown > 0 ? `验证码已发送（${countdown}s 后可重发）` : '发送验证码'}
          </Button>
        </Form>
      ) : (
        <Form form={resetForm} size="large" onFinish={onReset} autoComplete="off">
          <Form.Item name="code" rules={[{ required: true, message: '请输入邮箱验证码' }]}>
            <Input prefix={<SafetyOutlined />} placeholder="邮箱验证码" />
          </Form.Item>
          <Form.Item name="newPassword" rules={passwordRules} hasFeedback>
            <Input.Password prefix={<LockOutlined />} placeholder="新密码（8-20 位，含大小写/数字/特殊字符）" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请再次输入新密码' },
              ({ getFieldValue }) => ({
                validator: (_, value) => (value === getFieldValue('newPassword') ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致'))),
              }),
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="确认新密码" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>
            重置密码
          </Button>
          <Button type="link" block onClick={() => setStep(0)} style={{ marginTop: 8 }}>
            返回上一步
          </Button>
        </Form>
      )}
    </AuthCard>
  )
}

export function RegisterPage() {
  const navigate = useNavigate()
  const { login } = useAuth()
  const [form] = Form.useForm<UserRegisterRequest>()
  const [loading, setLoading] = useState(false)

  const onFinish = async (values: UserRegisterRequest) => {
    setLoading(true)
    try {
      await register(values)
      await login({ username: values.username, password: values.password })
      message.success('注册成功，已自动登录')
      navigate(ROUTES.home, { replace: true })
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '注册失败，请重试')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthCard title="创建账号" footer={<Text type="secondary">已有账号？<Link to={ROUTES.login}>去登录</Link></Text>}>
      <Form form={form} size="large" onFinish={onFinish} autoComplete="off">
        <Form.Item
          name="username"
          rules={[
            { required: true, message: '请输入用户名' },
            { min: 3, max: 20, message: '用户名长度 3-20 位' },
            { pattern: /^[a-zA-Z0-9_]+$/, message: '只能包含字母、数字和下划线' },
          ]}
        >
          <Input prefix={<UserOutlined />} placeholder="用户名（3-20 位字母数字下划线）" />
        </Form.Item>
        <Form.Item name="email" rules={[{ required: true, message: '请输入邮箱' }, { type: 'email', message: '邮箱格式不正确' }]}>
          <Input prefix={<MailOutlined />} placeholder="邮箱" />
        </Form.Item>
        <Form.Item name="displayName" rules={[{ max: 50, message: '显示名称不超过 50 个字符' }]}>
          <Input prefix={<UserOutlined />} placeholder="显示名称（可选）" />
        </Form.Item>
        <Form.Item name="password" rules={passwordRules} hasFeedback>
          <Input.Password prefix={<LockOutlined />} placeholder="密码（8-20 位，含大小写/数字/特殊字符）" />
        </Form.Item>
        <Form.Item
          name="confirmPassword"
          dependencies={['password']}
          rules={[
            { required: true, message: '请再次输入密码' },
            ({ getFieldValue }) => ({
              validator: (_, value) => (value === getFieldValue('password') ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致'))),
            }),
          ]}
        >
          <Input.Password prefix={<LockOutlined />} placeholder="确认密码" />
        </Form.Item>
        <Button type="primary" htmlType="submit" block loading={loading}>
          注册
        </Button>
      </Form>
    </AuthCard>
  )
}

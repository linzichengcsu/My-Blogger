/**
 * 数据看板：用户 / 文章 / 评论三块统计卡片。
 */
import { useEffect, useState } from 'react'
import { Card, Col, Row, Skeleton, Statistic, Typography } from 'antd'
import {
  CommentOutlined,
  EyeOutlined,
  FileTextOutlined,
  LikeOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { getDashboardStats } from '@/api'
import type { DashboardStatsDTO } from '@/types'

const { Title } = Typography

export default function DashboardPage() {
  const [stats, setStats] = useState<DashboardStatsDTO | null>(null)

  useEffect(() => {
    getDashboardStats().then(setStats).catch(() => setStats(null))
  }, [])

  if (!stats) return <Skeleton active paragraph={{ rows: 6 }} />

  return (
    <>
      <Title level={5}>数据看板</Title>
      <Row gutter={[16, 16]}>
        <Col xs={12} md={6}>
          <Card><Statistic title="总用户数" value={stats.userStats?.totalUsers ?? 0} prefix={<TeamOutlined />} /></Card>
        </Col>
        <Col xs={12} md={6}>
          <Card><Statistic title="活跃用户" value={stats.userStats?.activeUsers ?? 0} prefix={<TeamOutlined />} valueStyle={{ color: '#52c41a' }} /></Card>
        </Col>
        <Col xs={12} md={6}>
          <Card><Statistic title="本月新增" value={stats.userStats?.newUsersThisMonth ?? 0} prefix={<TeamOutlined />} valueStyle={{ color: '#fa541c' }} /></Card>
        </Col>
        <Col xs={12} md={6}>
          <Card><Statistic title="今日新增" value={stats.userStats?.newUsersToday ?? 0} /></Card>
        </Col>

        <Col xs={12} md={6}>
          <Card><Statistic title="文章总数" value={stats.articleStats?.totalArticles ?? 0} prefix={<FileTextOutlined />} /></Card>
        </Col>
        <Col xs={12} md={6}>
          <Card><Statistic title="已发布" value={stats.articleStats?.publishedArticles ?? 0} valueStyle={{ color: '#52c41a' }} /></Card>
        </Col>
        <Col xs={12} md={6}>
          <Card><Statistic title="草稿箱" value={stats.articleStats?.draftArticles ?? 0} valueStyle={{ color: '#8c8c8c' }} /></Card>
        </Col>
        <Col xs={12} md={6}>
          <Card><Statistic title="总点赞" value={stats.articleStats?.totalLikes ?? 0} prefix={<LikeOutlined />} valueStyle={{ color: '#fa541c' }} /></Card>
        </Col>

        <Col xs={12} md={8}>
          <Card><Statistic title="总浏览量" value={stats.articleStats?.totalViews ?? 0} prefix={<EyeOutlined />} /></Card>
        </Col>
        <Col xs={12} md={8}>
          <Card><Statistic title="评论总数" value={stats.commentStats?.totalComments ?? 0} prefix={<CommentOutlined />} /></Card>
        </Col>
        <Col xs={12} md={8}>
          <Card><Statistic title="待审核评论" value={stats.commentStats?.pendingComments ?? 0} valueStyle={{ color: '#cf1322' }} /></Card>
        </Col>
      </Row>
    </>
  )
}

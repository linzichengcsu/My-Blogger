/**
 * 管理后台看板类型（对齐 openapi.yaml Dashboard 相关 schema）。
 */

/** 用户统计 */
export interface UserStats {
  totalUsers?: number
  activeUsers?: number
  newUsersToday?: number
  newUsersThisMonth?: number
}

/** 文章统计 */
export interface ArticleStats {
  totalArticles?: number
  publishedArticles?: number
  draftArticles?: number
  totalViews?: number
  totalLikes?: number
}

/** 评论统计 */
export interface CommentStats {
  totalComments?: number
  pendingComments?: number
  approvedComments?: number
}

/** 看板统计数据 */
export interface DashboardStatsDTO {
  userStats?: UserStats
  articleStats?: ArticleStats
  commentStats?: CommentStats
  /** 附加指标 */
  additionalMetrics?: Record<string, unknown>
}

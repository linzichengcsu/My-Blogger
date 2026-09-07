/**
 * 评论模块类型（对齐 openapi.yaml 评论管理相关 schema）。
 */

/** 评论（顶级评论可带 replies 嵌套） */
export interface CommentDTO {
  id?: number
  createdAt?: string
  updatedAt?: string
  content?: string
  commenter?: import('./user').UserProfileDTO
  articleId?: number
  parentCommentId?: number
  replyCount?: number
  /** 嵌套回复 */
  replies?: CommentDTO[]
  likeCount?: number
  isLiked?: boolean
}

/** 评论回复（扁平结构，带被回复用户） */
export interface CommentReplyDTO {
  id?: number
  content?: string
  commenter?: import('./user').UserProfileDTO
  replyToUser?: import('./user').UserProfileDTO
  createdAt?: string
}

/** 创建评论请求（parentCommentId 为空表示顶级评论） */
export interface CommentCreateRequest {
  /** 1-500 字符，必填 */
  content: string
  parentCommentId?: number
}

/** 评论批量审核请求 */
export interface CommentBatchApprovalRequest {
  ids: number[]
  /** true=通过，false=不通过 */
  approved: boolean
}

/**
 * 评论管理接口（CRUD / 回复 / 统计 / 批量操作）。
 * 对应 openapi.yaml：/api/comments/**
 */
import { http } from './request'
import type { PageQuery, PageResponse } from '../types/common'
import type {
  CommentCreateRequest,
  CommentDTO,
  CommentReplyDTO,
} from '../types/comment'

/* ---------------- 列表 / 详情 ---------------- */

/** 获取文章顶级评论列表（公开） */
export function getTopLevelComments(
  articleId: number,
  query: PageQuery = {},
): Promise<PageResponse<CommentDTO>> {
  return http.get<PageResponse<CommentDTO>>(`/comments/article/${articleId}`, { params: query })
}

/** 获取评论详情（公开） */
export function getCommentById(commentId: number): Promise<CommentDTO> {
  return http.get<CommentDTO>(`/comments/${commentId}`)
}

/** 获取评论回复列表（公开） */
export function getCommentReplies(
  commentId: number,
  query: PageQuery = {},
): Promise<PageResponse<CommentReplyDTO>> {
  return http.get<PageResponse<CommentReplyDTO>>(`/comments/${commentId}/replies`, { params: query })
}

/** 获取用户评论列表（公开） */
export function getUserComments(
  userId: number,
  query: PageQuery = {},
): Promise<PageResponse<CommentDTO>> {
  return http.get<PageResponse<CommentDTO>>(`/comments/user/${userId}`, { params: query })
}

/* ---------------- 创建 / 删除 ---------------- */

/** 创建评论（需登录，parentCommentId 为空则为顶级评论） */
export function createComment(articleId: number, data: CommentCreateRequest): Promise<CommentDTO> {
  return http.post<CommentDTO>(`/comments/article/${articleId}`, data)
}

/** 删除评论（作者或管理员） */
export function deleteComment(commentId: number): Promise<void> {
  return http.del<void>(`/comments/${commentId}`)
}

/* ---------------- 统计 / 批量（管理员） ---------------- */

/** 统计文章评论数（公开） */
export function countCommentsByArticle(articleId: number): Promise<number> {
  return http.get<number>(`/comments/article/${articleId}/count`)
}

/** 统计用户评论数（公开） */
export function countCommentsByUser(userId: number): Promise<number> {
  return http.get<number>(`/comments/user/${userId}/count`)
}

/** 批量删除评论（管理员） */
export function batchDeleteComments(ids: number[]): Promise<number> {
  return http.post<number>('/comments/batch/delete', { ids })
}

/** 批量审核评论（管理员） */
export function batchApproveComments(ids: number[], approved: boolean): Promise<number> {
  return http.post<number>('/comments/batch/approve', { ids, approved })
}

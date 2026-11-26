/**
 * 标签管理接口（CRUD / 统计）。
 * 对应后端：/api/tags/**
 * SecurityConfig：GET /api/tags/** 公开，其余需登录；/api/tags/stats/total 需管理员。
 */
import { http } from './request'
import type { TagDTO, TagRequest } from '../types/tag'

/** 获取全部标签（公开，供写作页选择器与标签云） */
export function getAllTags(): Promise<TagDTO[]> {
  return http.get<TagDTO[]>('/tags')
}

/** 获取标签详情（公开） */
export function getTagById(tagId: number): Promise<TagDTO> {
  return http.get<TagDTO>(`/tags/${tagId}`)
}

/** 创建标签（需登录） */
export function createTag(data: TagRequest): Promise<TagDTO> {
  return http.post<TagDTO>('/tags', data)
}

/** 更新标签（需登录） */
export function updateTag(tagId: number, data: TagRequest): Promise<TagDTO> {
  return http.put<TagDTO>(`/tags/${tagId}`, data)
}

/** 删除标签（需登录；仍有文章引用时后端拒绝删除） */
export function deleteTag(tagId: number): Promise<void> {
  return http.del<void>(`/tags/${tagId}`)
}

/** 获取标签总数（需管理员） */
export function getTotalTagCount(): Promise<number> {
  return http.get<number>('/tags/stats/total')
}

/**
 * 通用类型：后端统一响应包装、分页结构、批量请求体。
 * 与 openapi.yaml components/schemas 对齐。
 */

/** 后端统一响应包装 `Result<T>`（业务成功 code = 200） */
export interface Result<T> {
  /** 业务数据；失败时为 null */
  data: T | null
  /** 提示信息，如 "success" */
  message?: string
  /** 业务状态码，200 表示成功 */
  code: number
}

/** 看板等接口使用的另一种包装 `ApiResponse<T>` */
export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T | null
  errorCode?: string
  timestamp?: string
}

/** 通用分页响应体（Spring Data Page 序列化） */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

/** 排序方向 */
export type SortDirection = 'asc' | 'desc' | 'ASC' | 'DESC'

/**
 * 扁平分页查询参数（对应后端 `PageRequestDTO`，以 query 字符串形式发送）。
 * 后端校验：page>=0，1<=size<=100，sortDirection 仅限 asc/desc。
 */
export interface PageQuery {
  /** 页码，从 0 开始（默认 0） */
  page?: number
  /** 每页条数（默认 10，最大 100） */
  size?: number
  /** 排序字段，如 createdAt（默认 createdAt） */
  sortBy?: string
  /** 排序方向（默认 asc） */
  sortDirection?: SortDirection
}

/** 批量操作请求体（删除/发布/归档/审核） */
export interface BatchIdRequest {
  /** ID 列表 */
  ids: number[]
}

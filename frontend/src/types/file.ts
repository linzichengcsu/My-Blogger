/**
 * 文件模块类型（对齐 openapi.yaml 文件管理相关 schema）。
 */

/** 文件上传/转换响应 */
export interface FileUploadResponse {
  fileName?: string
  /** 文件访问 URL（可能为相对路径） */
  fileUrl?: string
  fileType?: string
  fileSize?: number
  /** 转 Base64 接口返回的数据 */
  base64Data?: string
}

/** Base64 文件上传请求 */
export interface Base64UploadRequest {
  base64Data: string
  fileName: string
}

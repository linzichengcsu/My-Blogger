/**
 * 文件管理接口（上传 / 下载 / 删除 / Base64 转换）。
 * 对应 openapi.yaml：/api/files/**
 */
import { http, service } from './request'
import type { Base64UploadRequest, FileUploadResponse } from '../types/file'

/** 上传文件（multipart/form-data，字段名 file） */
export function uploadFile(
  file: File,
  onProgress?: (percent: number) => void,
): Promise<FileUploadResponse> {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<FileUploadResponse>('/files/upload', formData, {
    // 必须显式覆盖实例默认的 application/json：
    // 否则 axios 的 transformRequest 会把 FormData 序列化成 JSON 发送，
    // 导致后端报 "Current request is not a multipart request"。
    // 这里指定 multipart 后，浏览器会自动附加 boundary。
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress
      ? (event) => {
          if (event.total && event.total > 0) {
            onProgress(Math.round((event.loaded / event.total) * 100))
          }
        }
      : undefined,
  })
}

/** Base64 文件上传 */
export function uploadBase64File(data: Base64UploadRequest): Promise<FileUploadResponse> {
  return http.post<FileUploadResponse>('/files/upload-base64', data)
}

/** 下载/访问文件（需登录，返回 Blob，由调用方创建 URL 展示） */
export async function downloadFile(fileName: string): Promise<Blob> {
  const res = await service.get<Blob>(`/files/${encodeURIComponent(fileName)}`, {
    responseType: 'blob',
  })
  return res.data
}

/** 删除文件（需登录） */
export function deleteFile(fileName: string): Promise<void> {
  return http.del<void>(`/files/${encodeURIComponent(fileName)}`)
}

/** 文件转 Base64（需登录） */
export function getFileAsBase64(fileName: string): Promise<FileUploadResponse> {
  return http.get<FileUploadResponse>(`/files/${encodeURIComponent(fileName)}/base64`)
}

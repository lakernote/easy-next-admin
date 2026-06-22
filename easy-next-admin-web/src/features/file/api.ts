import axios, { type AxiosResponse } from 'axios'
import request from '@/api/request'
import { createTraceHeaders } from '@/api/trace'
import { toData, toPageResult, type ApiResponse, type PageApiResponse } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import type { FileCenterItem, FileDownloadPayload, FileEntityId, FilePageQuery } from './types'

const fileEndpoints = {
  list: '/system/files',
  upload: '/system/files/upload',
  download: (fileId: FileEntityId) => `/system/files/${fileId}/download`,
  delete: (fileId: FileEntityId) => `/system/files/${fileId}`,
  batchDelete: (fileIds: FileEntityId[]) => `/system/files/batch/${fileIds.join(',')}`
}

export async function pageSystemFiles(query: FilePageQuery) {
  const response = await request.get<PageApiResponse<FileCenterItem>>(fileEndpoints.list, {
    params: {
      page: query.page,
      limit: query.limit,
      keyWord: query.keyWord
    }
  })
  return toPageResult(response.data)
}

export async function uploadSystemFile(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await request.post<ApiResponse<FileCenterItem>>(fileEndpoints.upload, formData)
  return toData(response.data)
}

export async function deleteSystemFile(fileId: FileEntityId) {
  const response = await request.delete<ApiResponse<void>>(fileEndpoints.delete(fileId))
  return toData(response.data)
}

export async function batchDeleteSystemFiles(fileIds: FileEntityId[]) {
  if (fileIds.length === 0) {
    return
  }
  const response = await request.delete<ApiResponse<void>>(fileEndpoints.batchDelete(fileIds))
  return toData(response.data)
}

export async function downloadSystemFile(file: Pick<FileCenterItem, 'fileId' | 'originalName' | 'fileName' | 'storageName'>): Promise<FileDownloadPayload> {
  const response = await axios.get<Blob>(resolveApiUrl(fileEndpoints.download(file.fileId)), {
    responseType: 'blob',
    headers: createDownloadHeaders(),
    validateStatus: () => true
  })
  if (response.status < 200 || response.status >= 300) {
    throw new Error(await resolveDownloadError(response))
  }
  const contentType = readHeader(response, 'content-type') || response.data.type || 'application/octet-stream'
  return {
    blob: response.data,
    filename: resolveDownloadFilename(response, file),
    contentType
  }
}

function createDownloadHeaders() {
  const auth = useAuthStore()
  const headers: Record<string, string> = {
    ...createTraceHeaders()
  }
  if (auth.accessToken) {
    headers.Authorization = `Bearer ${auth.accessToken}`
  }
  return headers
}

function resolveApiUrl(endpoint: string) {
  const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'
  const base = baseURL.endsWith('/') ? baseURL.slice(0, -1) : baseURL
  const path = endpoint.startsWith('/') ? endpoint : `/${endpoint}`
  return `${base}${path}`
}

async function resolveDownloadError(response: AxiosResponse<Blob>) {
  const fallback = response.status === 403 ? '没有文件下载权限' : response.status === 404 ? '文件不存在或已被清理' : '文件下载失败'
  const contentType = readHeader(response, 'content-type')
  if (!contentType.includes('application/json')) {
    return fallback
  }
  try {
    const text = await response.data.text()
    const body = JSON.parse(text) as ApiResponse
    return body.message || fallback
  } catch {
    return fallback
  }
}

function resolveDownloadFilename(
  response: AxiosResponse<Blob>,
  file: Pick<FileCenterItem, 'originalName' | 'fileName' | 'storageName'>
) {
  const disposition = readHeader(response, 'content-disposition')
  const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  if (encodedName) {
    try {
      return decodeURIComponent(encodedName)
    } catch {
      return encodedName
    }
  }
  const quotedName = disposition.match(/filename="?([^";]+)"?/i)?.[1]
  return quotedName || file.originalName || file.fileName || file.storageName || 'download'
}

function readHeader(response: AxiosResponse<Blob>, name: string) {
  const headers = response.headers as Record<string, string | undefined>
  return headers[name] || headers[name.toLowerCase()] || ''
}

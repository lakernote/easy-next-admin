export type FileEntityId = string | number

export interface FileCenterItem {
  fileId: FileEntityId
  filePath?: string
  fileName?: string
  originalName?: string
  storageName?: string
  storageType?: string
  fileSize?: number
  contentType?: string
  businessType?: string
  businessId?: FileEntityId
  userId?: FileEntityId
  nickName?: string
  createTime?: string
  updateTime?: string
}

export interface FilePageQuery {
  page: number
  limit: number
  keyWord?: string
}

export interface FileDownloadPayload {
  blob: Blob
  filename: string
  contentType: string
}

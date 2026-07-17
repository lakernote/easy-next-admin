import type { FileCenterItem } from './types'

export type FileCategory = 'image' | 'office' | 'archive' | 'text' | 'other'
export type FilePreviewKind = 'image' | 'pdf' | 'text'

type FilePreviewSource = Pick<FileCenterItem, 'fileId' | 'originalName' | 'fileName' | 'storageName' | 'contentType'>

const imageExtensions = ['jpg', 'jpeg', 'png', 'gif', 'webp']
const officeExtensions = ['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'pdf']
const textPreviewExtensions = ['txt', 'csv', 'log', 'json', 'xml', 'md']

export function displayFileName(file: FilePreviewSource) {
  return file.originalName || file.fileName || file.storageName || `文件 ${file.fileId}`
}

export function resolveFileCategory(file: FilePreviewSource): FileCategory {
  const contentType = normalizedContentType(file)
  const extension = fileExtension(displayFileName(file))
  if (contentType.startsWith('image/') || imageExtensions.includes(extension)) {
    return 'image'
  }
  if (
    contentType.includes('word') ||
    contentType.includes('excel') ||
    contentType.includes('powerpoint') ||
    contentType.includes('officedocument') ||
    contentType === 'application/pdf' ||
    officeExtensions.includes(extension)
  ) {
    return 'office'
  }
  if (contentType.includes('zip') || extension === 'zip') {
    return 'archive'
  }
  if (contentType.startsWith('text/') || ['txt', 'csv'].includes(extension)) {
    return 'text'
  }
  return 'other'
}

export function canPreviewFile(file: FilePreviewSource) {
  return resolveFilePreviewKind(file) !== undefined
}

export function resolveFilePreviewKind(file: FilePreviewSource): FilePreviewKind | undefined {
  const contentType = normalizedContentType(file)
  const extension = fileExtension(displayFileName(file))
  if (contentType.startsWith('image/') || imageExtensions.includes(extension)) {
    return 'image'
  }
  if (contentType === 'application/pdf' || extension === 'pdf') {
    return 'pdf'
  }
  if (
    contentType.startsWith('text/') ||
    contentType.includes('json') ||
    contentType.includes('xml') ||
    textPreviewExtensions.includes(extension)
  ) {
    return 'text'
  }
  return undefined
}

export function fileExtension(filename: string) {
  const index = filename.lastIndexOf('.')
  return index >= 0 ? filename.slice(index + 1).toLowerCase() : ''
}

function normalizedContentType(file: FilePreviewSource) {
  return (file.contentType || '').toLowerCase()
}

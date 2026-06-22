import request from '@/api/request'
import { toData, toPageResult, type ApiResponse, type PageApiResponse } from '@/api/types'
import type {
  MessageItem,
  MessagePageQuery,
  MessageUnreadCount
} from './types'

const endpoints = {
  messages: '/messages',
  unreadCount: '/messages/unread-count',
  read: (id: string | number) => `/messages/${id}/read`,
  readAll: '/messages/read-all'
}

export async function pageMessages(query: MessagePageQuery) {
  const response = await request.get<PageApiResponse<MessageItem>>(endpoints.messages, {
    params: {
      page: query.page,
      limit: query.limit,
      keyword: query.keyword,
      category: query.type ? query.type.toUpperCase() : undefined,
      readStatus: typeof query.read === 'boolean' ? (query.read ? 1 : 0) : undefined
    }
  })
  const result = toPageResult(response.data)
  return {
    ...result,
    list: result.list.map(normalizeMessage)
  }
}

export async function getMessageUnreadCount() {
  const response = await request.get<ApiResponse<MessageUnreadCount | number>>(endpoints.unreadCount)
  const data = toData(response.data)
  return normalizeUnreadCount(data)
}

export async function markMessageRead(id: string | number) {
  const response = await request.put<ApiResponse<boolean>>(endpoints.read(id))
  return toData(response.data)
}

export async function markAllMessagesRead() {
  const response = await request.put<ApiResponse<boolean>>(endpoints.readAll)
  return toData(response.data)
}

export function normalizeMessageType(type: string) {
  const value = type.toUpperCase()
  if (value === 'WORKFLOW' || value === 'WORKFLOW_CC') return 'workflow'
  if (value === 'AUDIT' || value === 'SECURITY') return 'audit'
  if (value === 'TASK' || value === 'EXPORT' || value === 'IMPORT_EXPORT') return 'task'
  return type.toLowerCase()
}

export function normalizeUnreadCount(data: unknown): MessageUnreadCount {
  if (typeof data === 'number' || typeof data === 'string') {
    return { total: numericCount(data) }
  }
  if (!data || typeof data !== 'object') {
    return { total: 0 }
  }
  const count = data as Partial<Record<keyof MessageUnreadCount, unknown>>
  return {
    total: numericCount(count.total),
    workflow: numericOptionalCount(count.workflow),
    audit: numericOptionalCount(count.audit),
    task: numericOptionalCount(count.task)
  }
}

function numericCount(value: unknown) {
  const count = Number(value || 0)
  return Number.isFinite(count) && count > 0 ? count : 0
}

function numericOptionalCount(value: unknown) {
  if (value === undefined || value === null || value === '') return undefined
  return numericCount(value)
}

function normalizeMessage(row: MessageItem & {
  category?: string
  readStatus?: number
  bizType?: string
  bizId?: string | number
}) {
  const type = normalizeMessageType(row.type || row.category || '')
  return {
    ...row,
    type,
    level: row.level?.toLowerCase(),
    read: typeof row.read === 'boolean' ? row.read : row.readStatus === 1,
    relatedBizType: row.relatedBizType || row.bizType,
    relatedBizId: row.relatedBizId || row.bizId
  }
}

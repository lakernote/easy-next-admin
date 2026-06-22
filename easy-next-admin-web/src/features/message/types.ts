import type { PageQuery } from '@/api/types'

export type MessageType = 'workflow' | 'audit' | 'task' | string
export type MessageLevel = 'info' | 'success' | 'warning' | 'error' | string

export interface MessagePageQuery extends PageQuery {
  keyword?: string
  type?: MessageType
  read?: boolean
}

export interface MessageItem {
  id: string | number
  title: string
  content: string
  type: MessageType
  level?: MessageLevel
  read: boolean
  senderName?: string
  relatedBizType?: string
  relatedBizId?: string | number
  link?: string
  createdAt: string
  readAt?: string
}

export interface MessageUnreadCount {
  total: number
  workflow?: number
  audit?: number
  task?: number
}

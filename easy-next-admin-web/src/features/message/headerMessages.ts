import type { MessageUnreadCount } from './types'

export function hasHeaderUnread(total?: number) {
  return Number(total || 0) > 0
}

export function formatHeaderUnreadBadge(total?: number) {
  const count = Number(total || 0)
  if (count <= 0) return ''
  return count > 99 ? '99+' : String(count)
}

export function headerNoticeSummary(count: MessageUnreadCount) {
  const total = Number(count.total || 0)
  if (total <= 0) return '暂无未读消息'
  const parts = [
    count.workflow ? `流程 ${count.workflow} 条` : '',
    count.audit ? `审计 ${count.audit} 条` : '',
    count.task ? `任务 ${count.task} 条` : ''
  ].filter(Boolean)
  return parts.length ? `${total} 条未读，${parts.join('，')}` : `${total} 条未读消息`
}

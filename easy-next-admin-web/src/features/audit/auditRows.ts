import type {
  AuditApiLog,
  AuditDataChangeLog,
  AuditErrorLog,
  AuditLoginLog,
  AuditOperationLog,
  AuditUser
} from './types'

export type AuditTabKey = 'operation' | 'login' | 'dataChange' | 'error' | 'api'
export type AuditResultType = 'success' | 'warning' | 'danger' | 'info'
export type AuditLogRecord = AuditOperationLog | AuditLoginLog | AuditDataChangeLog | AuditErrorLog | AuditApiLog

export interface AuditTabMeta {
  key: AuditTabKey
  label: string
  metricLabel: string
  keywordLabel: string
  keywordPlaceholder: string
  emptyText: string
  stateLabel?: string
  stateKey?: 'loginResult' | 'responseStatus' | 'changeType' | 'errorType'
  stateOptions?: Array<{ label: string; value: string }>
}

export interface AuditRow {
  id: string
  time: string
  actor: string
  event: string
  target: string
  source: string
  result: string
  resultType: AuditResultType
  summary: string
  raw: AuditLogRecord
}

export interface AuditDetailItem {
  label: string
  value: string
  code?: boolean
}

export const AUDIT_TABS: AuditTabMeta[] = [
  {
    key: 'operation',
    label: '操作审计',
    metricLabel: '操作记录',
    keywordLabel: '操作',
    keywordPlaceholder: '模块 / 动作 / 操作人 / URI / IP',
    emptyText: '暂无操作审计记录',
    stateLabel: '结果',
    stateKey: 'responseStatus',
    stateOptions: [
      { label: '成功', value: 'SUCCESS' },
      { label: '失败', value: 'FAIL' },
      { label: '异常', value: 'ERROR' }
    ]
  },
  {
    key: 'login',
    label: '登录审计',
    metricLabel: '登录记录',
    keywordLabel: '登录',
    keywordPlaceholder: '账号 / IP / 客户端 / 失败原因',
    emptyText: '暂无登录审计记录',
    stateLabel: '结果',
    stateKey: 'loginResult',
    stateOptions: [
      { label: '成功', value: 'SUCCESS' },
      { label: '失败', value: 'FAIL' }
    ]
  },
  {
    key: 'dataChange',
    label: '敏感变更',
    metricLabel: '敏感变更',
    keywordLabel: '业务',
    keywordPlaceholder: '菜单 / 角色 / 流程 / 表名 / 字段',
    emptyText: '暂无敏感变更审计记录',
    stateLabel: '类型',
    stateKey: 'changeType',
    stateOptions: [
      { label: '新增', value: 'CREATE' },
      { label: '修改', value: 'UPDATE' },
      { label: '删除', value: 'DELETE' }
    ]
  },
  {
    key: 'error',
    label: '异常审计',
    metricLabel: '异常记录',
    keywordLabel: '异常',
    keywordPlaceholder: 'URI / 方法 / 异常信息',
    emptyText: '暂无异常审计记录',
    stateLabel: '类型',
    stateKey: 'errorType'
  },
  {
    key: 'api',
    label: '接口访问',
    metricLabel: '访问记录',
    keywordLabel: '接口',
    keywordPlaceholder: '接口 / 方法 / IP / 请求参数',
    emptyText: '暂无接口访问记录'
  }
]

export function toAuditRows(tab: AuditTabKey, records: AuditLogRecord[]): AuditRow[] {
  return records.map((record) => toAuditRow(tab, record))
}

export function toAuditRow(tab: AuditTabKey, record: AuditLogRecord): AuditRow {
  switch (tab) {
    case 'operation':
      return operationRow(record as AuditOperationLog)
    case 'login':
      return loginRow(record as AuditLoginLog)
    case 'dataChange':
      return dataChangeRow(record as AuditDataChangeLog)
    case 'error':
      return errorRow(record as AuditErrorLog)
    case 'api':
    default:
      return apiRow(record as AuditApiLog)
  }
}

export function auditDetailItems(tab: AuditTabKey, row: AuditRow | undefined): AuditDetailItem[] {
  if (!row) return []
  const common = [
    item('审计时间', row.time),
    item('操作人', row.actor),
    item('事件', row.event),
    item('审计对象', row.target),
    item('来源', row.source),
    item('结果', row.result)
  ]

  switch (tab) {
    case 'operation': {
      const raw = row.raw as AuditOperationLog
      return [
        ...common,
        item('请求方法', raw.requestMethod),
        item('请求地址', raw.requestUri),
        item('耗时', formatDuration(raw.durationMs)),
        item('请求参数', prettyText(raw.requestParams), true),
        item('错误信息', raw.errorMessage)
      ]
    }
    case 'login': {
      const raw = row.raw as AuditLoginLog
      return [
        ...common,
        item('用户账号', raw.userName),
        item('客户端', raw.clientType),
        item('User-Agent', raw.userAgent, true),
        item('失败原因', raw.failReason)
      ]
    }
    case 'dataChange': {
      const raw = row.raw as AuditDataChangeLog
      return [
        ...common,
        item('业务类型', raw.bizType),
        item('业务 ID', raw.bizId),
        item('数据表', raw.tableName),
        item('变更字段', raw.changedFields, true),
        item('变更前', prettyText(raw.beforeJson), true),
        item('变更后', prettyText(raw.afterJson), true)
      ]
    }
    case 'error': {
      const raw = row.raw as AuditErrorLog
      return [
        ...common,
        item('异常类型', raw.errorType),
        item('异常信息', raw.errorMessage, true),
        item('堆栈', raw.stackTrace, true)
      ]
    }
    case 'api':
    default: {
      const raw = row.raw as AuditApiLog
      return [
        ...common,
        item('请求方法', raw.method),
        item('请求地址', raw.uri),
        item('TraceId', raw.traceId),
        item('耗时', formatDuration(raw.cost)),
        item('请求参数', prettyText(raw.request), true),
        item('响应摘要', prettyText(raw.response), true)
      ]
    }
  }
}

function operationRow(log: AuditOperationLog): AuditRow {
  const responseStatus = normalized(log.responseStatus)
  const isSuccess = responseStatus === 'SUCCESS'
  const isError = responseStatus === 'ERROR'
  const isFail = responseStatus === 'FAIL' || responseStatus.startsWith('FAIL_')
  return {
    id: auditId('operation', log.id),
    time: text(log.createdAt),
    actor: text(log.operatorName || operator(log.operatorId)),
    event: text([log.module, log.action].filter(Boolean).join(' / ')),
    target: text(log.requestUri || log.requestParams),
    source: text(log.ip),
    result: isSuccess ? '成功' : isError ? '异常' : isFail ? '失败' : text(log.responseStatus, '失败'),
    resultType: isSuccess ? 'success' : isError ? 'danger' : 'warning',
    summary: text(log.errorMessage || formatDuration(log.durationMs)),
    raw: log
  }
}

function loginRow(log: AuditLoginLog): AuditRow {
  const isSuccess = normalized(log.loginResult) === 'SUCCESS'
  return {
    id: auditId('login', log.id),
    time: text(log.loginTime),
    actor: text(log.userName || operator(log.userId)),
    event: isSuccess ? '登录成功' : '登录失败',
    target: text(log.clientType),
    source: text(log.ip),
    result: isSuccess ? '成功' : '失败',
    resultType: isSuccess ? 'success' : 'danger',
    summary: text(log.failReason || log.userAgent),
    raw: log
  }
}

function dataChangeRow(log: AuditDataChangeLog): AuditRow {
  const changeType = normalized(log.changeType)
  const result = changeType === 'DELETE' ? '删除' : changeType === 'CREATE' ? '新增' : '修改'
  const actor = auditUserName(log.operator) || operator(log.operatorId)
  return {
    id: auditId('data-change', log.id),
    time: text(log.createdAt),
    actor: text(actor),
    event: text([log.bizType, result].filter(Boolean).join(' / ')),
    target: text([log.tableName, log.bizId].filter(Boolean).join(' # ')),
    source: text(actor),
    result,
    resultType: changeType === 'DELETE' ? 'danger' : changeType === 'CREATE' ? 'success' : 'warning',
    summary: text(log.changedFields),
    raw: log
  }
}

function errorRow(log: AuditErrorLog): AuditRow {
  const actor = auditUserName(log.operator) || operator(log.operatorId)
  return {
    id: auditId('error', log.id),
    time: text(log.createdAt),
    actor: text(actor),
    event: text(shortClassName(log.errorType), '系统异常'),
    target: text([log.requestMethod, log.requestUri].filter(Boolean).join(' ')),
    source: text(actor),
    result: '异常',
    resultType: 'danger',
    summary: text(log.errorMessage),
    raw: log
  }
}

function apiRow(log: AuditApiLog): AuditRow {
  const user = auditUserName(log.user) || operator(log.userId)
  const isSuccess = log.status !== false
  return {
    id: auditId('api', log.logId),
    time: text(log.createTime),
    actor: text(user),
    event: text([log.method, isSuccess ? '访问' : '异常'].filter(Boolean).join(' ')),
    target: text(log.uri || log.request),
    source: text(log.ip),
    result: isSuccess ? '成功' : '失败',
    resultType: isSuccess ? 'success' : 'danger',
    summary: text(log.traceId || formatDuration(log.cost)),
    raw: log
  }
}

function auditUserName(user?: AuditUser) {
  return user?.realName || user?.nickName || user?.userName || ''
}

function item(label: string, value: unknown, code = false): AuditDetailItem {
  return { label, value: text(value), code }
}

function operator(id?: string | number) {
  return id ? `用户 ${id}` : ''
}

function auditId(prefix: string, id: string | number | undefined) {
  return `${prefix}-${id ?? 'unknown'}`
}

function normalized(value?: string) {
  return (value || '').trim().toUpperCase()
}

function text(value: unknown, fallback = '-') {
  if (value === null || value === undefined) return fallback
  const content = String(value).trim()
  return content || fallback
}

function shortClassName(value?: string) {
  const content = text(value, '')
  if (!content) return ''
  return content.split('.').pop() || content
}

function formatDuration(value?: number) {
  if (typeof value !== 'number' || Number.isNaN(value)) return '-'
  return `${Math.max(0, Math.round(value))} ms`
}

function prettyText(value?: string) {
  const content = text(value, '')
  if (!content) return '-'
  try {
    return JSON.stringify(JSON.parse(content), null, 2)
  } catch {
    return content
  }
}

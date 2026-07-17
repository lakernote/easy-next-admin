export interface AuditUser {
  userName?: string
  nickName?: string
  realName?: string
}

export interface AuditApiLog {
  logId: string | number
  userId?: string | number
  user?: AuditUser
  traceId?: string
  ip?: string
  city?: string
  client?: string
  uri?: string
  method?: string
  request?: string
  response?: string
  status?: boolean
  cost?: number
  createTime?: string
}

export interface AuditLoginLog {
  id: string | number
  userId?: string | number
  userName?: string
  loginResult?: string
  failReason?: string
  ip?: string
  userAgent?: string
  clientType?: string
  loginTime?: string
}

export interface AuditOperationLog {
  id: string | number
  module?: string
  action?: string
  operatorId?: string | number
  operatorName?: string
  requestMethod?: string
  requestUri?: string
  requestParams?: string
  responseStatus?: string
  errorMessage?: string
  ip?: string
  userAgent?: string
  durationMs?: number
  createdAt?: string
}

export interface AuditDataChangeLog {
  id: string | number
  bizType?: string
  bizId?: string
  tableName?: string
  changeType?: string
  beforeJson?: string
  afterJson?: string
  changedFields?: string
  operatorId?: string | number
  operator?: AuditUser
  createdAt?: string
}

export interface AuditErrorLog {
  id: string | number
  requestUri?: string
  requestMethod?: string
  errorType?: string
  errorMessage?: string
  stackTrace?: string
  operatorId?: string | number
  operator?: AuditUser
  createdAt?: string
}

export interface AuditPageQuery {
  page?: number
  limit?: number
  keyWord?: string
  loginResult?: string
  responseStatus?: string
  changeType?: string
  errorType?: string
}

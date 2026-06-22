import request from '@/api/request'
import { toPageResult, type PageApiResponse } from '@/api/types'
import type {
  AuditApiLog,
  AuditDataChangeLog,
  AuditErrorLog,
  AuditLoginLog,
  AuditOperationLog,
  AuditPageQuery
} from './types'

export async function pageAuditLogs(query: AuditPageQuery) {
  return pageApiAuditLogs(query)
}

export async function pageApiAuditLogs(query: AuditPageQuery) {
  const response = await request.get<PageApiResponse<AuditApiLog>>('/audit/api-logs', { params: pageParams(query) })
  return toPageResult(response.data)
}

export async function pageLoginAuditLogs(query: AuditPageQuery) {
  const response = await request.get<PageApiResponse<AuditLoginLog>>('/audit/login-logs', { params: pageParams(query) })
  return toPageResult(response.data)
}

export async function pageOperationAuditLogs(query: AuditPageQuery) {
  const response = await request.get<PageApiResponse<AuditOperationLog>>('/audit/operation-logs', { params: pageParams(query) })
  return toPageResult(response.data)
}

export async function pageDataChangeAuditLogs(query: AuditPageQuery) {
  const response = await request.get<PageApiResponse<AuditDataChangeLog>>('/audit/data-change-logs', { params: pageParams(query) })
  return toPageResult(response.data)
}

export async function pageErrorAuditLogs(query: AuditPageQuery) {
  const response = await request.get<PageApiResponse<AuditErrorLog>>('/audit/error-logs', { params: pageParams(query) })
  return toPageResult(response.data)
}

function pageParams(query: AuditPageQuery) {
  return {
    page: query.page,
    limit: query.limit,
    keyWord: query.keyWord,
    loginResult: query.loginResult,
    responseStatus: query.responseStatus,
    changeType: query.changeType,
    errorType: query.errorType
  }
}

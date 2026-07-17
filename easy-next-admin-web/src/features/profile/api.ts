import request from '@/api/request'
import { toData, toPageResult, type ApiResponse, type PageApiResponse } from '@/api/types'
import type {
  ChangePasswordPayload,
  ProfileLoginHistory,
  ProfileLoginHistoryQuery,
  ProfilePayload,
  ProfileSession
} from './types'

const endpoints = {
  me: '/profile',
  avatar: '/profile/avatar',
  password: '/profile/password',
  loginHistory: '/profile/login-history',
  sessions: '/profile/sessions',
  session: (id: string | number) => `/profile/sessions/${id}`
}

export async function updateProfile(data: ProfilePayload) {
  const response = await request.put<ApiResponse<boolean>>(endpoints.me, data)
  return toData(response.data)
}

export async function changePassword(data: ChangePasswordPayload) {
  const response = await request.put<ApiResponse<boolean>>(endpoints.password, data)
  return toData(response.data)
}

export async function uploadProfileAvatar(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await request.post<ApiResponse<ProfilePayload>>(endpoints.avatar, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return toData(response.data)
}

export async function pageProfileLoginHistory(query: ProfileLoginHistoryQuery) {
  const response = await request.get<PageApiResponse<ProfileLoginHistory>>(endpoints.loginHistory, {
    params: { page: query.page, limit: query.limit, keyword: query.keyword }
  })
  const result = toPageResult(response.data)
  return {
    ...result,
    list: result.list.map(normalizeLoginHistory)
  }
}

export async function listProfileSessions() {
  const response = await request.get<ApiResponse<ProfileSession[]>>(endpoints.sessions)
  return toData(response.data).map(normalizeSession)
}

export async function revokeProfileSession(id: string | number) {
  const response = await request.delete<ApiResponse<boolean>>(endpoints.session(id))
  return toData(response.data)
}

function normalizeLoginHistory(row: ProfileLoginHistory & { loginResult?: string }) {
  return {
    ...row,
    result: row.result || row.loginResult || 'SUCCESS'
  }
}

function normalizeSession(row: ProfileSession & { sessionId?: string | number; lastActiveTime?: string }) {
  return {
    ...row,
    id: row.id || row.sessionId || '',
    lastAccessTime: row.lastAccessTime || row.lastActiveTime
  }
}

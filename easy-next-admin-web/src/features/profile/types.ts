import type { PageQuery } from '@/api/types'

export interface ProfilePayload {
  nickName?: string
  realName?: string
  phone?: string
  email?: string
  avatar?: string
}

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

export interface ProfileLoginHistoryQuery extends PageQuery {
  keyword?: string
}

export interface ProfileLoginHistory {
  id: string | number
  loginTime: string
  ip?: string
  location?: string
  clientType?: string
  userAgent?: string
  result: 'SUCCESS' | 'FAIL' | string
  failReason?: string
}

export interface ProfileSession {
  id: string | number
  clientType?: string
  ip?: string
  location?: string
  userAgent?: string
  loginTime?: string
  lastAccessTime?: string
  current?: boolean
}

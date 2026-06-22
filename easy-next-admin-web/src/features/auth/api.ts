import request from '@/api/request'
import type { ApiResponse } from '@/api/types'
import type { AuthProfile, CaptchaResult, DemoAccount, LoginPayload, LoginResult } from './types'

// 认证接口按业务动作命名，页面调用时能直接读出意图。
export function login(data: LoginPayload) {
  return request.post<ApiResponse<LoginResult>>('/auth/login', data).then((response) => response.data)
}

export function getCaptcha() {
  return request.get<ApiResponse<CaptchaResult>>('/auth/captcha').then((response) => response.data)
}

export function listDemoAccounts() {
  return request.get<ApiResponse<DemoAccount[]>>('/auth/demo-accounts').then((response) => response.data)
}

export function getAuthProfile() {
  return request.get<ApiResponse<AuthProfile>>('/auth/me').then((response) => response.data)
}

export function logout() {
  return request.post<ApiResponse<void>>('/auth/logout').then((response) => response.data)
}

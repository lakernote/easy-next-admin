import request from '@/api/request'
import type { ApiResponse } from '@/api/types'
import type { AuthProfile, CaptchaResult, DemoAccount, LoginPayload, LoginResult } from './types'

export function loginApi(data: LoginPayload) {
  return request.post<ApiResponse<LoginResult>>('/auth/login', data).then((response) => response.data)
}

export function captchaApi() {
  return request.get<ApiResponse<CaptchaResult>>('/auth/captcha').then((response) => response.data)
}

export function demoAccountsApi() {
  return request.get<ApiResponse<DemoAccount[]>>('/auth/demo-accounts').then((response) => response.data)
}

export function profileApi() {
  return request.get<ApiResponse<AuthProfile>>('/auth/me').then((response) => response.data)
}

export function logoutApi() {
  return request.post<ApiResponse<void>>('/auth/logout').then((response) => response.data)
}

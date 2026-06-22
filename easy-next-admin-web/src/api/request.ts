import axios, { type AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import type { ApiResponse } from './types'
import { TRACE_ID_HEADER, createTraceHeaders } from './trace'

const LOGIN_PATH = '/login'
let redirectingToLogin = false
let lastTraceId = ''

type RetriableRequestConfig = InternalAxiosRequestConfig & { _retry?: boolean }

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const auth = useAuthStore()
  Object.assign(config.headers, createTraceHeaders())
  if (auth.accessToken) {
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  return config
})

request.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    rememberTraceId(response)
    if (isBinaryResponse(response)) {
      return response
    }
    const body = response.data
    if (isUnauthorized(undefined, body)) {
      if (isLoginUrl(response.config.url)) {
        const message = responseMessage(body, '认证失败')
        ElMessage.error(message)
        return Promise.reject(new Error(message))
      }
      return handleUnauthorizedResponse(response.config as RetriableRequestConfig, response)
    }
    if (isForbidden(undefined, body)) {
      showForbiddenMessage(responseMessage(body, '没有权限访问该资源'))
      return Promise.reject(new Error(responseMessage(body, '没有权限访问该资源')))
    }
    if (Number(body?.code) === 0) return response
    const message = body?.message || '请求处理失败'
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  },
  (error: AxiosError<ApiResponse>) => {
    rememberTraceId(error.response)
    if (isUnauthorized(error.response?.status, error.response?.data)) {
      if (isLoginUrl(error.config?.url)) {
        ElMessage.error(responseMessage(error.response?.data, '认证失败'))
        return Promise.reject(error)
      }
      return handleUnauthorizedResponse(error.config as RetriableRequestConfig | undefined, error)
    }
    if (isForbidden(error.response?.status, error.response?.data)) {
      showForbiddenMessage(responseMessage(error.response?.data, '没有权限访问该资源'))
      return Promise.reject(error)
    }
    ElMessage.error(error.response?.data?.message || error.message || '网络请求失败')
    return Promise.reject(error)
  }
)

async function handleUnauthorizedResponse<T>(
  config: RetriableRequestConfig | undefined,
  original: AxiosResponse<ApiResponse<T>> | AxiosError<ApiResponse<T>> | Error
) {
  if (config && !config._retry && !isAuthUrl(config.url)) {
    config._retry = true
  }
  redirectToLogin()
  return Promise.reject(original)
}

function responseMessage(body: ApiResponse | undefined, fallback: string) {
  return body?.message || fallback
}

function responseCodeGroup(body?: ApiResponse) {
  const code = Number(body?.code)
  if (!Number.isFinite(code)) return undefined
  return Math.trunc(code / 1000)
}

function isUnauthorized(status?: number, body?: ApiResponse) {
  return status === 401 || responseCodeGroup(body) === 401
}

function isForbidden(status?: number, body?: ApiResponse) {
  return status === 403 || responseCodeGroup(body) === 403
}

function isBinaryResponse(response: AxiosResponse) {
  return response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer'
}

function currentFullPath() {
  return `${window.location.pathname}${window.location.search}${window.location.hash}` || '/'
}

function rememberTraceId(response?: AxiosResponse) {
  const traceId = readHeader(response?.headers, TRACE_ID_HEADER)
  if (traceId) {
    lastTraceId = traceId
  }
}

function readHeader(headers: AxiosResponse['headers'] | undefined, name: string) {
  if (!headers) return ''
  const getter = (headers as { get?: (headerName: string) => string | null }).get
  if (typeof getter === 'function') {
    return getter.call(headers, name) || getter.call(headers, name.toLowerCase()) || ''
  }
  const record = headers as Record<string, string | undefined>
  return record[name] || record[name.toLowerCase()] || ''
}

export function getLastTraceId() {
  return lastTraceId
}

function isAuthUrl(url?: string) {
  return Boolean(url && url.includes('/auth/'))
}

function isLoginUrl(url?: string) {
  return Boolean(url && url.includes('/auth/login'))
}

function redirectToLogin() {
  if (redirectingToLogin) return
  redirectingToLogin = true
  const auth = useAuthStore()
  auth.logout()
  if (window.location.pathname !== LOGIN_PATH) {
    ElMessage.warning('登录已过期，请重新登录')
    const redirect = encodeURIComponent(currentFullPath())
    window.location.replace(`${LOGIN_PATH}?redirect=${redirect}`)
    return
  }
  window.setTimeout(() => {
    redirectingToLogin = false
  }, 0)
}

function showForbiddenMessage(message: string) {
  ElMessage.error(message)
}

export default request

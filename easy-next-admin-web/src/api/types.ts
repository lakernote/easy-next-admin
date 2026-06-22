export interface ApiErrorDetail {
  field?: string
  message: string
}

export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  details?: ApiErrorDetail[]
}

export interface PageData<T> {
  list: T[]
  total: number | string
}

export type PageApiResponse<T> = ApiResponse<PageData<T>>

export interface PageQuery {
  page?: number
  limit?: number
  current?: number
  size?: number
}

export interface PageResult<T> {
  list: T[]
  total: number
}

export function toPageResult<T>(body: PageApiResponse<T>): PageResult<T> {
  return {
    list: body.data?.list || [],
    total: Number(body.data?.total ?? 0)
  }
}

export function toData<T>(body: ApiResponse<T>): T {
  return body.data
}

import request from '@/api/request'
import { toData, toPageResult, type ApiResponse, type PageApiResponse } from '@/api/types'
import type { EntityId } from '@/features/system/types'
import type {
  BusinessNumberGenerated,
  BusinessNumberRule,
  BusinessNumberRulePayload,
  BusinessNumberRuleQuery
} from './types'

export async function pageBusinessNumberRules(query: BusinessNumberRuleQuery = {}) {
  const response = await request.get<PageApiResponse<BusinessNumberRule>>('/business-numbers/rules', {
    params: {
      page: query.page || 1,
      limit: query.limit || 10,
      keyword: query.keyword,
      enable: query.enable
    }
  })
  return toPageResult(response.data)
}

export async function saveBusinessNumberRule(rule: BusinessNumberRulePayload) {
  const response = await request.post<ApiResponse<BusinessNumberRule>>('/business-numbers/rules', rule)
  return toData(response.data)
}

export async function deleteBusinessNumberRule(id: EntityId) {
  const response = await request.delete<ApiResponse<boolean>>(`/business-numbers/rules/${id}`)
  return toData(response.data)
}

export async function generateBusinessNumber(ruleCode: string) {
  const response = await request.post<ApiResponse<BusinessNumberGenerated>>(
    `/business-numbers/rules/${encodeURIComponent(ruleCode)}/generate`
  )
  return toData(response.data)
}

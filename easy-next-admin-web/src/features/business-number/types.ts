import type { PageQuery } from '@/api/types'
import type { EntityId } from '@/features/system/types'

export type BusinessNumberDatePattern = 'yyyyMMdd' | 'yyyyMM' | 'yyyy' | 'NONE'

export interface BusinessNumberRuleQuery extends PageQuery {
  keyword?: string
  enable?: boolean
}

export interface BusinessNumberRule {
  id: EntityId
  ruleCode: string
  ruleName: string
  prefix: string
  datePattern: BusinessNumberDatePattern | string
  datePatternName: string
  separator: string
  sequenceWidth: number
  sequenceStep: number
  initialValue: number
  enable: boolean
  sampleNumber: string
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface BusinessNumberRulePayload {
  id?: EntityId
  ruleCode: string
  ruleName: string
  prefix: string
  datePattern: BusinessNumberDatePattern | string
  separator: string
  sequenceWidth: number
  sequenceStep: number
  initialValue: number
  enable: boolean
  remark?: string
}

export interface BusinessNumberGenerated {
  ruleCode: string
  number: string
  generatedAt: string
}

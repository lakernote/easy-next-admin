export interface BusinessFieldConfig {
  key: string
  label: string
  wide?: boolean
  span?: 2 | 3 | 4
}

export interface BusinessField {
  key: string
  label: string
  value: string
  wide?: boolean
  span?: 2 | 3 | 4
}

const businessFieldConfig: Record<string, BusinessFieldConfig[]> = {
  leave: [
    { key: 'leaveType', label: '请假类型' },
    { key: 'days', label: '请假天数' },
    { key: 'startTime', label: '开始时间' },
    { key: 'endTime', label: '结束时间' },
    { key: 'reason', label: '请假事由', wide: true }
  ],
  purchase: [
    { key: 'itemName', label: '采购物品' },
    { key: 'category', label: '采购类别' },
    { key: 'quantity', label: '数量' },
    { key: 'estimatedAmount', label: '预估金额' },
    { key: 'requiredDate', label: '期望到货' },
    { key: 'reason', label: '采购原因', wide: true }
  ],
  expense: [
    { key: 'expenseType', label: '报销类型' },
    { key: 'amount', label: '报销金额' },
    { key: 'occurredDate', label: '发生日期' },
    { key: 'reason', label: '报销事由', wide: true }
  ],
  repair: [
    { key: 'repairType', label: '报修类型' },
    { key: 'assetName', label: '设备/资产' },
    { key: 'urgency', label: '紧急程度' },
    { key: 'faultTime', label: '故障时间' },
    { key: 'location', label: '故障位置', wide: true },
    { key: 'description', label: '故障描述', wide: true }
  ]
}

const businessValueText: Record<string, Record<string, string>> = {
  leaveType: {
    ANNUAL: '年假',
    SICK: '病假',
    PERSONAL: '事假',
    MARRIAGE: '婚假',
    MATERNITY: '产假',
    COMPENSATORY: '调休',
    OTHER: '其他'
  },
  repairType: {
    DEVICE: '办公设备',
    NETWORK: '网络',
    SOFTWARE: '软件系统',
    FACILITY: '办公设施',
    OTHER: '其他'
  },
  urgency: {
    LOW: '低',
    NORMAL: '普通',
    HIGH: '较急',
    URGENT: '紧急'
  },
  category: {
    OFFICE_SUPPLIES: '办公用品',
    IT_EQUIPMENT: 'IT 设备',
    SOFTWARE_SERVICE: '软件服务',
    ADMIN_SERVICE: '行政服务'
  },
  expenseType: {
    TRAVEL: '差旅',
    OFFICE: '办公',
    CLIENT: '客户招待',
    OTHER: '其他'
  }
}

const internalBusinessKeys = new Set([
  'definitionId',
  'processKey',
  'businessType',
  'businessId',
  'title',
  'initiatorId',
  'operatorId',
  'instanceId',
  'applicantDeptId',
  'duration',
  'repairRequestId',
  'repairAttachments',
  'attachmentCount'
])

export function buildBusinessFields(businessType: string | undefined, variables: Record<string, unknown>): BusinessField[] {
  if (businessType === 'leave') {
    const leaveFields = buildLeaveBusinessFields(variables)
    if (leaveFields.length) return leaveFields
  }

  const config = businessFieldConfig[businessType || ''] || []
  const fields = config
    .map((item) => ({
      ...item,
      value: formatBusinessValue(variables[item.key], item.key)
    }))
    .filter((item) => item.value !== '-')

  if (fields.length) return fields

  return Object.entries(variables)
    .filter(([key, value]) => !internalBusinessKeys.has(key) && hasBusinessValue(value))
    .slice(0, 8)
    .map(([key, value]) => ({
      key,
      label: fallbackBusinessLabel(key),
      value: formatBusinessValue(value, key),
      wide: String(value).length > 28
    }))
}

function buildLeaveBusinessFields(variables: Record<string, unknown>): BusinessField[] {
  const fields: BusinessField[] = []
  const leaveType = formatBusinessValue(variables.leaveType, 'leaveType')
  const leavePeriod = formatLeavePeriod(variables)
  const reason = formatBusinessValue(variables.reason, 'reason')

  if (leaveType !== '-') {
    fields.push({ key: 'leaveType', label: '请假类型', value: leaveType })
  }
  if (leavePeriod !== '-') {
    fields.push({ key: 'leavePeriod', label: '请假时间', value: leavePeriod, span: 3 })
  }
  if (reason !== '-') {
    fields.push({ key: 'reason', label: '请假事由', value: reason, wide: true })
  }
  return fields
}

function formatLeavePeriod(variables: Record<string, unknown>) {
  const startTime = formatBusinessValue(variables.startTime, 'startTime')
  const endTime = formatBusinessValue(variables.endTime, 'endTime')
  const days = formatBusinessValue(variables.days, 'days')
  if (startTime === '-' && endTime === '-' && days === '-') return '-'
  const range = formatLeaveTimeRange(startTime, endTime)
  if (!range) return `共 ${days}`
  return days !== '-' ? `${range}（共 ${days}）` : range
}

function formatLeaveTimeRange(startTime: string, endTime: string) {
  if (startTime !== '-' && endTime !== '-') {
    return `自 ${startTime} 起，至 ${endTime} 止`
  }
  if (startTime !== '-') return `自 ${startTime} 起`
  if (endTime !== '-') return `至 ${endTime} 止`
  return ''
}

function hasBusinessValue(value: unknown) {
  return value !== undefined && value !== null && value !== ''
}

function formatBusinessValue(value: unknown, key: string) {
  if (!hasBusinessValue(value)) return '-'
  const text = businessValueText[key]?.[String(value)]
  if (text) return text
  if (Array.isArray(value)) return `${value.length} 项`
  if (typeof value === 'object') return JSON.stringify(value)
  if (isAmountKey(key) && isFiniteNumberText(value)) return `￥${Number(value).toFixed(2)}`
  if (key === 'days' && isFiniteNumberText(value)) return `${Number(value)} 天`
  if (typeof value === 'string' && isBusinessDateTimeKey(key) && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value)) {
    return value.replace('T', ' ').slice(0, 16)
  }
  return String(value)
}

function isBusinessDateTimeKey(key: string) {
  return /(time|date|at)$/i.test(key)
}

function isAmountKey(key: string) {
  return /amount/i.test(key)
}

function isFiniteNumberText(value: unknown) {
  return value !== '' && Number.isFinite(Number(value))
}

function fallbackBusinessLabel(key: string) {
  const textMap: Record<string, string> = {
    amount: '金额',
    estimatedAmount: '预估金额',
    requiredDate: '期望日期',
    startTime: '开始时间',
    endTime: '结束时间',
    reason: '原因',
    description: '说明'
  }
  return textMap[key] || key
}

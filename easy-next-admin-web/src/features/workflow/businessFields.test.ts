import { describe, expect, it } from 'vitest'
import { buildBusinessFields } from './businessFields'

describe('workflow business fields', () => {
  it('builds visible leave details from workflow variables', () => {
    const fields = buildBusinessFields('leave', {
      leaveType: 'ANNUAL',
      days: 2,
      startTime: '2026-05-10T09:00',
      endTime: '2026-05-11T18:00',
      reason: '家庭事务，申请年假。'
    })

    expect(fields.map((field) => field.label)).toEqual(['请假类型', '请假时间', '请假事由'])
    expect(fields.find((field) => field.key === 'leaveType')?.value).toBe('年假')
    expect(fields.find((field) => field.key === 'leavePeriod')?.value).toBe('自 2026-05-10 09:00 起，至 2026-05-11 18:00 止（共 2 天）')
    expect(fields.find((field) => field.key === 'leavePeriod')?.span).toBe(3)
    expect(fields.find((field) => field.key === 'reason')?.value).toBe('家庭事务，申请年假。')
  })

  it('builds configured details for purchase, expense and repair workflow variables', () => {
    expect(buildBusinessFields('purchase', {
      itemName: '研发测试设备',
      category: 'IT_EQUIPMENT',
      quantity: 1,
      estimatedAmount: 12800,
      requiredDate: '2026-05-16',
      reason: '补充研发测试环境设备。'
    }).map((field) => `${field.label}:${field.value}`)).toEqual([
      '采购物品:研发测试设备',
      '采购类别:IT 设备',
      '数量:1',
      '预估金额:￥12800.00',
      '期望到货:2026-05-16',
      '采购原因:补充研发测试环境设备。'
    ])

    expect(buildBusinessFields('expense', {
      expenseType: 'TRAVEL',
      amount: 3680.5,
      occurredDate: '2026-05-06',
      reason: '客户现场差旅费用报销。'
    }).map((field) => `${field.label}:${field.value}`)).toEqual([
      '报销类型:差旅',
      '报销金额:￥3680.50',
      '发生日期:2026-05-06',
      '报销事由:客户现场差旅费用报销。'
    ])

    const repairFields = buildBusinessFields('repair', {
      repairType: 'DEVICE',
      assetName: '会议室投影仪',
      urgency: 'HIGH',
      faultTime: '2026-05-09T18:00:00',
      location: '深圳总部 12F 会议室',
      description: '投影仪无法开机。'
    })
    expect(repairFields.map((field) => `${field.label}:${field.value}`)).toEqual([
      '报修类型:办公设备',
      '设备/资产:会议室投影仪',
      '紧急程度:较急',
      '故障时间:2026-05-09 18:00',
      '故障位置:深圳总部 12F 会议室',
      '故障描述:投影仪无法开机。'
    ])
    expect(repairFields.find((field) => field.key === 'location')?.wide).toBe(true)
    expect(repairFields.find((field) => field.key === 'description')?.wide).toBe(true)
  })
})

import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { describe, expect, it } from 'vitest'

const applyRequestFiles = [
  './LeaveRequestView.vue',
  './PurchaseRequestView.vue',
  './RepairRequestView.vue'
]
const paperTitles = new Map([
  ['./LeaveRequestView.vue', '请假申请单'],
  ['./PurchaseRequestView.vue', '采购申请单'],
  ['./RepairRequestView.vue', '报修申请单']
])
const startSource = readFileSync(fileURLToPath(new URL('./WorkflowStartView.vue', import.meta.url)), 'utf-8')

describe('workflow application paper usage', () => {
  it('uses the same paper-style application sheet for every submit page', () => {
    applyRequestFiles.forEach((file) => {
      const source = readFileSync(fileURLToPath(new URL(file, import.meta.url)), 'utf-8')

      expect(source).toContain('WorkflowApplicationPaper')
      expect(source).toContain('applyPaperMeta')
      expect(source).toContain('提交后生成')
      expect(source).toContain('流程状态')
      expect(source).toContain('待提交')
      expect(source).toContain('paper-apply-form')
      expect(source).toContain('workflowTaskCenterPath(\'started\')')
      expect(source).toContain('#field-')
      expect(source).toContain('提交申请')
      expect(source).toContain(`computed(() => '${paperTitles.get(file)}')`)
      expect(source).not.toContain('el-drawer')
      expect(source).not.toContain('admin-table')
      expect(source).not.toContain('申请部门')
      expect(source).not.toContain('apply-route-preview')
      expect(source).not.toContain('drawer-alert')
    })
  })

  it('opens request forms directly from the workflow start cards', () => {
    expect(startSource).toContain('openWorkflowApply(item.path)')
    expect(startSource).toContain('router.push(path)')
    expect(startSource).toContain('开始填写')
    expect(startSource).not.toContain("query: { apply: '1' }")

    applyRequestFiles.forEach((file) => {
      const source = readFileSync(fileURLToPath(new URL(file, import.meta.url)), 'utf-8')

      expect(source).toContain('useRouter')
      expect(source).toContain('resetApplyForm')
      expect(source).toContain('goMyStarted')
      expect(source).not.toContain('openApplyDrawerFromRoute')
      expect(source).not.toContain('pageLeaveRequests')
      expect(source).not.toContain('pagePurchaseRequests')
      expect(source).not.toContain('pageRepairRequests')
    })
  })

  it('keeps the paper component responsible for the Chinese approval sheet layout', () => {
    const source = readFileSync(fileURLToPath(new URL('./components/WorkflowApplicationPaper.vue', import.meta.url)), 'utf-8')

    expect(source).toContain('application-paper-sheet')
    expect(source).toContain('application-paper-title')
    expect(source).toContain('application-paper-meta')
    expect(source).toContain('application-paper-grid')
    expect(source).toContain('application-paper-watermark')
    expect(source).toContain('max-width: 980px;')
    expect(source).toContain('approvalStatusLine(item)')
    expect(source).toContain('resize: none;')
    expect(source).toContain('审批记录')
    expect(source).toContain('业务申请单')
    expect(source).not.toContain("{{ item.statusText || '-' }} · {{ item.timeText || '-' }}")
    expect(source).not.toContain('内部流转 留痕归档')
    expect(source).not.toContain('application-paper-stamp')
    expect(source).not.toContain('流程专用章')
  })

  it('renders leave request time as a handwritten-style paper row', () => {
    const source = readFileSync(fileURLToPath(new URL('./LeaveRequestView.vue', import.meta.url)), 'utf-8')

    expect(source).toContain('#field-leavePeriod')
    expect(source).toContain('duration-shortcuts')
    expect(source).toContain('applyDurationShortcut')
    expect(source).toContain('syncDaysFromPeriod')
    expect(source).toContain('calculateLeaveDaysFromRange')
    expect(source).toMatch(/function handleEndTimeChange\(\) \{\s+syncDaysFromPeriod\(\)/)
    expect(source).toContain('自</span>')
    expect(source).toContain('起，至</span>')
    expect(source).toContain('止，共</span>')
    expect(source).not.toContain(':formatter=')
    expect(source).not.toContain(':parser=')
    expect(source).not.toContain('show-word-limit')
    expect(source).not.toContain('#field-days')
    expect(source).not.toContain('#field-startTime')
    expect(source).not.toContain('#field-endTime')
  })
})

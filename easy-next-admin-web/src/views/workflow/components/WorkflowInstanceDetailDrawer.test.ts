import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(
  fileURLToPath(new URL('./WorkflowInstanceDetailDrawer.vue', import.meta.url)),
  'utf-8'
)
const paperSource = readFileSync(
  fileURLToPath(new URL('./WorkflowApplicationPaper.vue', import.meta.url)),
  'utf-8'
)

describe('workflow instance detail drawer', () => {
  it('uses a flat detail layout without the duplicated summary hero', () => {
    expect(viewSource).not.toContain('detail-hero')
    expect(viewSource).not.toContain('detail-compact-meta')
    expect(viewSource).toContain('detail-flat-section')
    expect(viewSource).toContain('detail-flow-section')
    expect(viewSource).toContain('detail-dynamics-section')
  })

  it('exposes in-detail approval actions only for the current assignee task', () => {
    expect(viewSource).toContain('currentUserId')
    expect(viewSource).toContain('actionableTask')
    expect(viewSource).toContain("task.status === 'PENDING'")
    expect(viewSource).toContain('sameEntityId(task.assigneeId, props.currentUserId)')
    expect(viewSource).toContain('detail-task-action-section')
    expect(viewSource).toContain('detail-task-action-title')
    expect(viewSource).toContain('detail-task-action-node')
    expect(viewSource).toContain('同意')
    expect(viewSource).toContain('拒绝')
    expect(viewSource).toContain("emit('task-action'")
    expect(viewSource).not.toContain('detail-task-action-meta')
    expect(viewSource).not.toContain('看完申请内容后直接处理，无需返回列表。')
  })

  it('renders submitted business content as a paper-style Chinese application sheet', () => {
    expect(viewSource).toContain('WorkflowApplicationPaper')
    expect(viewSource).toContain('businessPaperMeta')
    expect(viewSource).toContain('businessPaperApprovals')
    expect(viewSource).toContain('businessSheetTitle')
    expect(viewSource).toContain('businessSheetNo')
    expect(paperSource).toContain('application-paper-sheet')
    expect(paperSource).toContain('application-paper-grid')
    expect(paperSource).toContain('业务申请单')
    expect(viewSource).toContain('申请单号')
  })
})

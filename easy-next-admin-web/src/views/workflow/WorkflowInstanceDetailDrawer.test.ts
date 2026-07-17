import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { describe, expect, it } from 'vitest'

const drawerSource = readFileSync(
  fileURLToPath(new URL('./components/WorkflowInstanceDetailDrawer.vue', import.meta.url)),
  'utf-8'
)
const paperSource = readFileSync(
  fileURLToPath(new URL('./components/WorkflowApplicationPaper.vue', import.meta.url)),
  'utf-8'
)

describe('Workflow instance runtime graph motion', () => {
  it('animates traversed edges and nodes like data flowing through the process', () => {
    expect(drawerSource).toContain('flow-edge-flow')
    expect(drawerSource).toContain('isFlowAnimatedEdge')
    expect(drawerSource).toContain('@keyframes workflow-edge-flow')
    expect(drawerSource).toContain('@keyframes workflow-node-flow')
    expect(drawerSource).toContain('prefers-reduced-motion')
  })

  it('renders grouped workflow dynamics for enterprise approval tracking', () => {
    expect(drawerSource).toContain('流程动态')
    expect(drawerSource).toContain('workflowDynamics')
    expect(drawerSource).toContain('workflow-dynamics')
    expect(drawerSource).toContain('dynamic-table-head')
    expect(drawerSource).toContain('dynamic-row')
    expect(drawerSource).toContain('dynamic-cell')
    expect(drawerSource).not.toContain('dynamic-card')
    expect(drawerSource).not.toContain('dynamic-actor')
    expect(drawerSource).toContain('到达时间')
    expect(drawerSource).toContain('处理时间')
    expect(drawerSource).toContain('processedAtText')
  })

  it('does not render a duplicated node task panel beside workflow dynamics', () => {
    expect(drawerSource).not.toContain('节点任务')
    expect(drawerSource).not.toContain('detail-task-list')
  })

  it('uses flat sections instead of duplicated summary blocks', () => {
    expect(drawerSource).not.toContain('detail-summary-grid')
    expect(drawerSource).not.toContain('detail-compact-meta')
    expect(drawerSource).not.toContain('detail-hero')
    expect(drawerSource).toContain('detail-flat-section')
    expect(drawerSource).toContain('detail-flow-section')
    expect(drawerSource).toContain('detail-dynamics-section')
  })

  it('keeps application fields and detail task actions compact', () => {
    expect(drawerSource).toContain('detail-section-head is-compact')
    expect(drawerSource).toContain('WorkflowApplicationPaper')
    expect(paperSource).toContain('grid-column: 1 / -1')
    expect(drawerSource).toContain('detail-task-action-title')
    expect(drawerSource).toContain('detail-task-action-node')
    expect(drawerSource).not.toContain('detail-task-action-meta')
    expect(drawerSource).not.toContain('当前节点为 {{ actionableTask.nodeName }}，可在核对申请内容后直接给出审批意见。')
    expect(drawerSource).not.toContain('看完申请内容后直接处理，无需返回列表。')
  })

  it('sizes the runtime graph canvas from the actual graph height', () => {
    expect(drawerSource).toContain('flowViewBoxMetrics')
    expect(drawerSource).toContain('isCompactFlowGraph')
    expect(drawerSource).toContain('flowCanvasHeight')
    expect(drawerSource).toContain('--runtime-flow-height')
    expect(drawerSource).not.toContain('height: 420px')
  })

  it('uses a person icon for approval nodes in the runtime graph', () => {
    expect(drawerSource).toContain("APPROVAL: 'user'")
    expect(drawerSource).toContain("nodeIconKind(node.nodeType) === 'user'")
    expect(drawerSource).not.toContain("APPROVAL: 'check'")
  })
})

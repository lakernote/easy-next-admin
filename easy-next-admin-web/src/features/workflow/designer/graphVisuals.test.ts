import { describe, expect, it } from 'vitest'
import {
  applyWorkflowGraphVisuals,
  edgeLabelLayoutFromDraggedText,
  edgePointsFromEdge,
  optimizeWorkflowGraphLayout,
} from './graphVisuals'
import type { WorkflowGraph } from '../types'

describe('workflow designer graph visuals', () => {
  it('routes condition branches into lanes and keeps merge edges readable', () => {
    const graph: WorkflowGraph = {
      nodes: [
        node('start', '开始', 'START'),
        node('submit', '提交申请', 'SUBMIT'),
        node('condition', '金额判断', 'CONDITION'),
        node('approve_low', '部门审批', 'APPROVAL', 0, 240),
        node('approve_high', '财务审批', 'APPROVAL', 0, 320),
        node('cc', '抄送归档', 'CC'),
        node('end', '结束', 'END')
      ],
      edges: [
        edge('start_submit', 'start', 'submit', '提交'),
        edge('submit_condition', 'submit', 'condition', '进入判断'),
        edge('condition_low', 'condition', 'approve_low', '金额小于等于 5000'),
        edge('condition_high', 'condition', 'approve_high', '金额超过 5000'),
        edge('low_cc', 'approve_low', 'cc', '通过'),
        edge('high_cc', 'approve_high', 'cc', '通过'),
        edge('cc_end', 'cc', 'end', '完成')
      ]
    }

    const layout = optimizeWorkflowGraphLayout(graph)
    const condition = layout.nodes.find((item) => item.id === 'condition')
    const low = layout.nodes.find((item) => item.id === 'approve_low')
    const high = layout.nodes.find((item) => item.id === 'approve_high')
    const branch = layout.edges.find((item) => item.id === 'condition_low')
    const merge = layout.edges.find((item) => item.id === 'low_cc')
    const branchProperties = branch?.properties as Record<string, unknown> | undefined

    expect(condition?.y).toBe(296)
    expect(low?.y).toBeLessThan(296)
    expect(high?.y).toBeGreaterThan(296)
    expect(branchProperties?.labelPlacement).toBe('START')
    expect(branch?.pointsList).toHaveLength(4)
    expect(branch?.text).toMatchObject({ value: '金额小于等于 5000', draggable: true, editable: false })
    expect(merge?.pointsList).toHaveLength(4)
    expect(merge?.text).toMatchObject({ value: '通过', draggable: true, editable: false })
  })

  it('normalizes legacy node and edge visuals for LogicFlow rendering', () => {
    const graph: WorkflowGraph = {
      nodes: [
        node('condition', '条件判断', 'CONDITION'),
        {
          id: 'approve',
          type: 'rect',
          text: '部门审批',
          properties: {
            nodeType: 'APPROVAL'
          }
        }
      ],
      edges: [
        {
          id: 'edge1',
          sourceNodeId: 'condition',
          targetNodeId: 'approve',
          text: '金额超过5000',
          pointsList: [
            { x: 168, y: 296 },
            { x: 210, y: 296 },
            { x: 210, y: 146 },
            { x: 272, y: 146 }
          ],
          properties: {
            conditionExpression: 'amount > 5000',
            labelOffsetX: '8',
            lineColor: '#ff0000'
          }
        }
      ]
    }

    const visual = applyWorkflowGraphVisuals(graph)
    const approval = visual.nodes.find((item) => item.id === 'approve')
    const edge1 = visual.edges[0]
    const approvalProperties = approval?.properties as Record<string, unknown> | undefined
    const edgeProperties = edge1.properties as Record<string, unknown>

    expect(approval?.type).toBe('workflow-rect')
    expect(approvalProperties?.style).toMatchObject({ stroke: '#2563eb' })
    expect(edge1.type).toBe('polyline')
    expect(edgeProperties.style).toMatchObject({ stroke: '#64748b', strokeWidth: 1.8 })
    expect(edgeProperties.lineColor).toBeUndefined()
    expect(edgeProperties.labelOffsetX).toBe(8)
    expect(edge1.text).toMatchObject({ value: '金额超过5000', draggable: true, editable: false })
  })

  it('converts dragged edge text into a custom label offset', () => {
    const edge = {
      id: 'edge1',
      sourceNodeId: 'start',
      targetNodeId: 'approve',
      text: { value: '同意', x: 70, y: -30 },
      pointsList: [
        { x: 0, y: 0 },
        { x: 100, y: 0 }
      ],
      properties: {
        labelPlacement: 'AUTO'
      }
    }

    const layout = edgeLabelLayoutFromDraggedText(edge, edgePointsFromEdge(edge), 'AUTO')

    expect(layout).toEqual({
      labelPlacement: 'CUSTOM',
      labelOffsetX: 20,
      labelOffsetY: -12
    })
  })
})

function node(id: string, text: string, nodeType: string, x = 0, y = 296) {
  return {
    id,
    text,
    x,
    y,
    properties: {
      nodeType
    }
  }
}

function edge(id: string, sourceNodeId: string, targetNodeId: string, text: string) {
  return {
    id,
    sourceNodeId,
    targetNodeId,
    text
  }
}

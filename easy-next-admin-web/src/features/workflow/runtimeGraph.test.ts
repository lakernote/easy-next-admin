import { describe, expect, it } from 'vitest'
import { buildRuntimeGraph, edgePath } from './runtimeGraph'
import type { WorkflowInstanceDetail } from './types'

describe('workflow runtime graph', () => {
  it('does not mark a completed approved instance current even when currentNodeKey is stale', () => {
    const detail = approvedDetailWithStaleCurrentNode()

    const graph = buildRuntimeGraph(detail)

    expect(graph.nodes.some((node) => node.state === 'current')).toBe(false)
    expect(graph.nodes.find((node) => node.id === 'condition')?.state).toBe('done')
    expect(graph.nodes.find((node) => node.id === 'dept_approve')?.state).toBe('done')
    expect(graph.nodes.find((node) => node.id === 'archive')?.state).toBe('done')
    expect(graph.nodes.find((node) => node.id === 'end')?.state).toBe('done')
    expect(graph.edges.find((edge) => edge.sourceNodeId === 'dept_approve' && edge.targetNodeId === 'archive')?.state).toBe('done')
  })

  it('keeps arrow endpoints outside rectangular nodes so marker heads remain visible', () => {
    const graph = buildRuntimeGraph(approvedDetailWithStaleCurrentNode())
    const edge = graph.edges.find((item) => item.targetNodeId === 'dept_approve')

    expect(edge).toBeTruthy()
    expect(edgePath(edge!)).toContain(`L ${Math.round(edge!.targetX - 92)}`)
  })

  it('keeps saved designer coordinates and routed edge points', () => {
    const detail = approvedDetailWithStaleCurrentNode()
    detail.graphJson = JSON.stringify({
      nodes: [
        { id: 'start', text: '开始', x: 80, y: 260, properties: { nodeType: 'START' } },
        { id: 'approve', text: '部门负责人审批', x: 520, y: 180, properties: { nodeType: 'APPROVAL' } }
      ],
      edges: [
        {
          id: 'route',
          sourceNodeId: 'start',
          targetNodeId: 'approve',
          text: { value: '通过', x: 310, y: 206 },
          pointsList: [
            { x: 112, y: 260 },
            { x: 260, y: 260 },
            { x: 260, y: 180 },
            { x: 436, y: 180 }
          ]
        }
      ]
    })

    const graph = buildRuntimeGraph(detail)
    const edge = graph.edges[0]

    expect(graph.nodes.find((node) => node.id === 'start')?.x).toBe(80)
    expect(graph.nodes.find((node) => node.id === 'start')?.y).toBe(260)
    expect(edge.pointsList).toEqual([
      { x: 112, y: 260 },
      { x: 260, y: 260 },
      { x: 260, y: 180 },
      { x: 436, y: 180 }
    ])
    expect(edge.labelX).toBe(310)
    expect(edge.labelY).toBe(206)
    expect(edgePath(edge)).toBe('M 112 260 L 260 260 L 260 180 L 436 180')
  })

  it('keeps persisted edge label offsets after save sanitizes text to a string', () => {
    const detail = approvedDetailWithStaleCurrentNode()
    detail.graphJson = JSON.stringify({
      nodes: [
        { id: 'start', text: '开始', x: 80, y: 260, properties: { nodeType: 'START' } },
        { id: 'approve', text: '部门负责人审批', x: 520, y: 180, properties: { nodeType: 'APPROVAL' } }
      ],
      edges: [
        {
          id: 'route',
          sourceNodeId: 'start',
          targetNodeId: 'approve',
          text: '通过',
          pointsList: [
            { x: 112, y: 260 },
            { x: 260, y: 260 },
            { x: 260, y: 180 },
            { x: 436, y: 180 }
          ],
          properties: {
            labelPlacement: 'CUSTOM',
            labelOffsetX: 70,
            labelOffsetY: -35
          }
        }
      ]
    })

    const graph = buildRuntimeGraph(detail)
    const edge = graph.edges[0]

    expect(edge.label).toBe('通过')
    expect(edge.labelX).toBe(268)
    expect(edge.labelY).toBe(205)
  })

  it('places condition branch labels in the outgoing branch corridor', () => {
    const detail = approvedDetailWithStaleCurrentNode()
    detail.graphJson = JSON.stringify({
      nodes: [
        { id: 'condition', text: '时长判断', x: 360, y: 260, properties: { nodeType: 'CONDITION' } },
        { id: 'approve', text: '部门负责人审批', x: 704, y: 160, properties: { nodeType: 'APPROVAL' } }
      ],
      edges: [
        {
          id: 'condition_to_approve',
          sourceNodeId: 'condition',
          targetNodeId: 'approve',
          text: '3天内',
          pointsList: [
            { x: 412, y: 260 },
            { x: 456, y: 260 },
            { x: 456, y: 160 },
            { x: 620, y: 160 }
          ],
          properties: {
            labelPlacement: 'START'
          }
        }
      ]
    })

    const edge = buildRuntimeGraph(detail).edges[0]

    expect(edge.labelX).toBe(531)
    expect(edge.labelY).toBe(184)
    expect(edge.labelX).toBeGreaterThan(456)
    expect(edge.labelX).toBeLessThan(620)
  })
})

function approvedDetailWithStaleCurrentNode(): WorkflowInstanceDetail {
  const graph = {
    nodes: [
      { id: 'start', text: '开始', x: 80, y: 180, properties: { nodeType: 'START' } },
      { id: 'submit', text: '提交请假', x: 220, y: 180, properties: { nodeType: 'SUBMIT' } },
      { id: 'condition', text: '时长判断', x: 360, y: 180, properties: { nodeType: 'CONDITION' } },
      { id: 'dept_approve', text: '部门负责人审批', x: 500, y: 180, properties: { nodeType: 'APPROVAL' } },
      { id: 'archive', text: '行政备案', x: 790, y: 180, properties: { nodeType: 'CC' } },
      { id: 'end', text: '结束', x: 930, y: 180, properties: { nodeType: 'END' } }
    ],
    edges: [
      { id: 'e1', sourceNodeId: 'start', targetNodeId: 'submit' },
      { id: 'e2', sourceNodeId: 'submit', targetNodeId: 'condition' },
      { id: 'e3', sourceNodeId: 'condition', targetNodeId: 'dept_approve', text: '3天内' },
      { id: 'e4', sourceNodeId: 'dept_approve', targetNodeId: 'archive', text: '通过' },
      { id: 'e5', sourceNodeId: 'archive', targetNodeId: 'end' }
    ]
  }
  return {
    instance: {
      id: 1,
      definitionId: 1,
      versionId: 1,
      processKey: 'leave',
      businessType: 'leave',
      businessId: 'LV-1',
      title: '林员工请假申请',
      initiatorId: 3,
      currentNodeKey: 'dept_approve',
      status: 'APPROVED',
      startedAt: '2026-04-30 19:23:46',
      endedAt: '2026-04-30 19:23:47'
    },
    graphJson: JSON.stringify(graph),
    tasks: [
      {
        id: 1,
        instanceId: 1,
        nodeKey: 'dept_approve',
        nodeName: '部门负责人审批',
        assigneeId: 2,
        status: 'TRANSFERRED',
        startedAt: '2026-04-30 19:23:46',
        finishedAt: '2026-04-30 19:23:47'
      }
    ],
    events: [
      { id: 1, instanceId: 1, action: 'SUBMIT', toNodeKey: 'dept_approve', createdAt: '2026-04-30 19:23:46' },
      { id: 2, instanceId: 1, action: 'TRANSFER', fromNodeKey: 'dept_approve', toNodeKey: 'dept_approve', createdAt: '2026-04-30 19:23:47' },
      { id: 3, instanceId: 1, action: 'APPROVE', fromNodeKey: 'dept_approve', createdAt: '2026-04-30 19:23:47' }
    ],
    ccList: []
  }
}

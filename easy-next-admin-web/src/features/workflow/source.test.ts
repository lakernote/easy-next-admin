import { describe, expect, it } from 'vitest'
import { formatWorkflowGraphSource, parseWorkflowGraphJson, parseWorkflowGraphSource, sanitizeWorkflowGraphForSave, validateWorkflowGraphForEnable } from './source'
import type { WorkflowGraph } from './types'

describe('workflow graph source helpers', () => {
  it('formats missing graph as an empty workflow graph', () => {
    expect(formatWorkflowGraphSource()).toBe('{\n  "nodes": [],\n  "edges": []\n}')
  })

  it('formats valid graph JSON with two-space indentation', () => {
    const source = formatWorkflowGraphSource('{"nodes":[{"id":"start"}],"edges":[]}')

    expect(source).toBe('{\n  "nodes": [\n    {\n      "id": "start"\n    }\n  ],\n  "edges": []\n}')
  })

  it('keeps invalid JSON unchanged when formatting', () => {
    expect(formatWorkflowGraphSource('{bad json')).toBe('{bad json')
  })

  it('keeps empty string unchanged when formatting', () => {
    expect(formatWorkflowGraphSource('')).toBe('')
  })

  it('keeps graph JSON with non-array nodes unchanged when formatting', () => {
    expect(formatWorkflowGraphSource('{"nodes":{},"edges":[]}')).toBe('{"nodes":{},"edges":[]}')
  })

  it('keeps array JSON unchanged when formatting', () => {
    expect(formatWorkflowGraphSource('[]')).toBe('[]')
  })

  it('keeps graph JSON with non-object nodes unchanged when formatting', () => {
    expect(formatWorkflowGraphSource('{"nodes":[null],"edges":[]}')).toBe('{"nodes":[null],"edges":[]}')
  })

  it('keeps graph JSON with non-object edges unchanged when formatting', () => {
    expect(formatWorkflowGraphSource('{"nodes":[],"edges":[null]}')).toBe('{"nodes":[],"edges":[null]}')
  })

  it('returns a validation error for invalid JSON', () => {
    const result = parseWorkflowGraphSource('{bad json')

    expect(result.ok).toBe(false)
    expect(result.message).toBe('流程源码不是有效 JSON')
  })

  it('returns a validation error when source is an array', () => {
    const result = parseWorkflowGraphSource('[]')

    expect(result.ok).toBe(false)
    expect(result.message).toBe('流程源码必须是 JSON 对象')
  })

  it('returns a validation error when nodes is not an array', () => {
    const result = parseWorkflowGraphSource('{"nodes":{},"edges":[]}')

    expect(result.ok).toBe(false)
    expect(result.message).toBe('流程源码必须包含 nodes 数组')
  })

  it('returns a validation error when edges is not an array', () => {
    const result = parseWorkflowGraphSource('{"nodes":[],"edges":{}}')

    expect(result.ok).toBe(false)
    expect(result.message).toBe('流程源码必须包含 edges 数组')
  })

  it('returns a validation error when nodes contains non-object items', () => {
    const result = parseWorkflowGraphSource('{"nodes":[null],"edges":[]}')

    expect(result.ok).toBe(false)
    expect(result.message).toBe('流程源码 nodes 数组只能包含对象')
  })

  it('returns a validation error when edges contains non-object items', () => {
    const result = parseWorkflowGraphSource('{"nodes":[],"edges":[null]}')

    expect(result.ok).toBe(false)
    expect(result.message).toBe('流程源码 edges 数组只能包含对象')
  })

  it('returns normalized graph JSON for valid source', () => {
    const result = parseWorkflowGraphSource('{"nodes":[{"id":"start"}],"edges":[]}')

    expect(result.ok).toBe(true)
    expect(result.graph).toEqual({ nodes: [{ id: 'start' }], edges: [] })
    expect(result.source).toBe('{\n  "nodes": [\n    {\n      "id": "start"\n    }\n  ],\n  "edges": []\n}')
  })

  it('protects long participant ids before JSON parsing loses precision', () => {
    const graph = parseWorkflowGraphJson('{"nodes":[{"id":"cc","properties":{"nodeType":"CC","ccUserIds":[202604280101000017]}},{"id":"approval","properties":{"nodeType":"APPROVAL","assigneeIds":[202604280101000001]}}],"edges":[]}')

    expect((graph.nodes[0].properties as Record<string, unknown>).ccUserIds).toEqual(['202604280101000017'])
    expect((graph.nodes[1].properties as Record<string, unknown>).assigneeIds).toEqual(['202604280101000001'])
  })
})

describe('workflow graph enable validation', () => {
  it('rejects an empty graph', () => {
    const result = validateWorkflowGraphForEnable({ nodes: [], edges: [] })

    expect(result.valid).toBe(false)
    expect(result.message).toBe('启用流程前请先配置流程图')
  })

  it('rejects duplicate node ids', () => {
    const graph = validBasicGraph()
    graph.nodes.push({ id: 'approval_1', text: '重复审批', properties: { nodeType: 'APPROVAL', approverType: 'DEPT_LEADER' } })

    const result = validateWorkflowGraphForEnable(graph)

    expect(result.valid).toBe(false)
    expect(result.message).toBe('启用流程存在重复节点标识')
  })

  it('rejects nodes without ids', () => {
    const graph = validBasicGraph()
    graph.nodes[1] = { text: '部门审批', properties: { nodeType: 'APPROVAL', approverType: 'DEPT_LEADER' } }

    const result = validateWorkflowGraphForEnable(graph)

    expect(result.valid).toBe(false)
    expect(result.message).toBe('启用流程存在未设置标识的节点')
  })

  it('rejects multiple start nodes', () => {
    const graph = validBasicGraph()
    graph.nodes.push({ id: 'start_2', text: '开始二', properties: { nodeType: 'START' } })

    const result = validateWorkflowGraphForEnable(graph)

    expect(result.valid).toBe(false)
    expect(result.message).toBe('启用流程只能有一个开始节点')
  })

  it('rejects multiple end nodes', () => {
    const graph = validBasicGraph()
    graph.nodes.push({ id: 'end_2', text: '结束二', properties: { nodeType: 'END' } })

    const result = validateWorkflowGraphForEnable(graph)

    expect(result.valid).toBe(false)
    expect(result.message).toBe('启用流程只能有一个结束节点')
  })

  it('rejects a graph without an approval node', () => {
    const graph = validBasicGraph()
    graph.nodes = graph.nodes.filter((node) => node.id !== 'approval_1')
    graph.edges = [{ id: 'edge_start_end', sourceNodeId: 'start', targetNodeId: 'end' }]

    const result = validateWorkflowGraphForEnable(graph)

    expect(result.valid).toBe(false)
    expect(result.message).toBe('启用流程至少需要一个审批节点')
  })

  it('rejects USER approval nodes without assignees using the node name', () => {
    const graph = validBasicGraph()
    graph.nodes[1] = {
      id: 'approval_1',
      text: '部门审批',
      properties: { nodeType: 'APPROVAL', approverType: 'USER' }
    }

    const result = validateWorkflowGraphForEnable(graph)

    expect(result.valid).toBe(false)
    expect(result.message).toBe('部门审批 未配置有效处理人规则')
  })

  it('rejects USER approval nodes with blank assignee ids', () => {
    const graph = validBasicGraph()
    graph.nodes[1] = {
      id: 'approval_1',
      text: '部门审批',
      properties: { nodeType: 'APPROVAL', approverType: 'USER', assigneeIds: [''] }
    }

    const result = validateWorkflowGraphForEnable(graph)

    expect(result.valid).toBe(false)
    expect(result.message).toBe('部门审批 未配置有效处理人规则')
  })

  it('rejects ROLE approval nodes with blank role codes', () => {
    const graph = validBasicGraph()
    graph.nodes[1] = {
      id: 'approval_1',
      text: '部门审批',
      properties: { nodeType: 'APPROVAL', approverType: 'ROLE', roleCode: '   ' }
    }

    const result = validateWorkflowGraphForEnable(graph)

    expect(result.valid).toBe(false)
    expect(result.message).toBe('部门审批 未配置有效处理人规则')
  })

  it('rejects unsupported approval rule types', () => {
    const graph = validBasicGraph()
    graph.nodes[1] = {
      id: 'approval_1',
      text: '部门审批',
      properties: { nodeType: 'APPROVAL', approverType: 'CUSTOM_RULE' }
    }

    const result = validateWorkflowGraphForEnable(graph)

    expect(result.valid).toBe(false)
    expect(result.message).toBe('部门审批 未配置有效处理人规则')
  })

  it('rejects an edge with a dangling endpoint', () => {
    const graph = validBasicGraph()
    graph.edges[0] = { id: 'edge_start_missing', sourceNodeId: 'start', targetNodeId: 'missing' }

    const result = validateWorkflowGraphForEnable(graph)

    expect(result.valid).toBe(false)
    expect(result.message).toBe('启用流程存在连接到不存在节点的连线')
  })

  it('rejects an edge without endpoints', () => {
    const graph = validBasicGraph()
    graph.edges[0] = { id: 'edge_without_endpoint', sourceNodeId: 'start' }

    const result = validateWorkflowGraphForEnable(graph)

    expect(result.valid).toBe(false)
    expect(result.message).toBe('启用流程存在未设置端点的连线')
  })

  it('rejects a graph where start cannot reach end', () => {
    const graph = validBasicGraph()
    graph.edges = [{ id: 'edge_start_approval', sourceNodeId: 'start', targetNodeId: 'approval_1' }]

    const result = validateWorkflowGraphForEnable(graph)

    expect(result.valid).toBe(false)
    expect(result.message).toBe('启用流程需要从开始节点连通到结束节点')
  })

  it('rejects conditional branches without a default path', () => {
    const graph = validConditionalGraph()
    graph.edges[2] = {
      id: 'edge_check_default',
      sourceNodeId: 'check',
      targetNodeId: 'approval_default',
      properties: { conditionType: 'EXPRESSION', conditionExpression: 'amount <= 5000' }
    }

    const result = validateWorkflowGraphForEnable(graph)

    expect(result.valid).toBe(false)
    expect(result.message).toBe('金额判断 条件分支需要配置默认路径')
  })

  it('rejects conditional branches with multiple default paths', () => {
    const graph = validConditionalGraph()
    graph.edges.push({ id: 'edge_check_backup', sourceNodeId: 'check', targetNodeId: 'approval_high' })

    const result = validateWorkflowGraphForEnable(graph)

    expect(result.valid).toBe(false)
    expect(result.message).toBe('金额判断 条件分支只能配置一条默认路径')
  })

  it('accepts a valid basic graph', () => {
    const result = validateWorkflowGraphForEnable(validBasicGraph())

    expect(result).toEqual({ valid: true })
  })

  it('accepts a conditional branch with one default path', () => {
    const result = validateWorkflowGraphForEnable(validConditionalGraph())

    expect(result).toEqual({ valid: true })
  })

  it('accepts manager approval rule without fixed assignees', () => {
    const graph = validBasicGraph()
    graph.nodes[1] = {
      id: 'approval_1',
      text: '直属上级审批',
      properties: { nodeType: 'APPROVAL', approverType: 'MANAGER' }
    }

    const result = validateWorkflowGraphForEnable(graph)

    expect(result).toEqual({ valid: true })
  })
})

describe('workflow graph save sanitation', () => {
  it('keeps only current CC node schema fields', () => {
    const graph = sanitizeWorkflowGraphForSave({
      nodes: [
        {
          id: 'cc_hr',
          text: '行政备案',
          properties: {
            nodeType: 'CC',
            ccUserIds: ['202604280101000017'],
            assigneeIds: ['202604280101000017'],
            approverType: 'USER',
            roleCode: 'auditor',
            extraField: 'ignored'
          }
        }
      ],
      edges: []
    })

    const properties = graph.nodes[0].properties as Record<string, unknown>
    expect(properties.ccUserIds).toEqual(['202604280101000017'])
    expect(properties).not.toHaveProperty('assigneeIds')
    expect(properties).not.toHaveProperty('approverType')
    expect(properties).not.toHaveProperty('roleCode')
    expect(properties).not.toHaveProperty('extraField')
  })

  it('does not infer CC receivers from unrelated fields', () => {
    const graph = sanitizeWorkflowGraphForSave({
      nodes: [
        {
          id: 'cc_empty',
          text: '抄送',
          properties: {
            nodeType: 'CC',
            assigneeIds: [202604280101000030]
          }
        }
      ],
      edges: []
    })

    const properties = graph.nodes[0].properties as Record<string, unknown>
    expect(properties.ccUserIds).toEqual([])
    expect(properties).not.toHaveProperty('assigneeIds')
  })

  it('normalizes numeric participant ids to strings before saving', () => {
    const graph = sanitizeWorkflowGraphForSave({
      nodes: [
        {
          id: 'cc_hr',
          text: '行政备案',
          properties: {
            nodeType: 'CC',
            ccUserIds: [12345]
          }
        },
        {
          id: 'approval_1',
          text: '总经办审批',
          properties: {
            nodeType: 'APPROVAL',
            approverType: 'USER',
            assigneeIds: [67890]
          }
        }
      ],
      edges: []
    })

    expect((graph.nodes[0].properties as Record<string, unknown>).ccUserIds).toEqual(['12345'])
    expect((graph.nodes[1].properties as Record<string, unknown>).assigneeIds).toEqual(['67890'])
  })

  it('keeps only current approval node schema fields', () => {
    const graph = sanitizeWorkflowGraphForSave({
      nodes: [
        {
          id: 'approval_1',
          text: '部门审批',
          properties: {
            nodeType: 'APPROVAL',
            approverType: 'USER',
            assigneeIds: ['202604280101000017'],
            ccUserIds: ['202604280101000030'],
            extraField: 'ignored'
          }
        }
      ],
      edges: []
    })

    const properties = graph.nodes[0].properties as Record<string, unknown>
    expect(properties.assigneeIds).toEqual(['202604280101000017'])
    expect(properties).not.toHaveProperty('ccUserIds')
    expect(properties).not.toHaveProperty('extraField')
  })

  it('stores edge labels as stable text values instead of transient canvas coordinates', () => {
    const graph = sanitizeWorkflowGraphForSave({
      nodes: [],
      edges: [
        {
          id: 'edge_condition_approval',
          sourceNodeId: 'condition',
          targetNodeId: 'approval_1',
          text: {
            value: '3天内',
            x: 818,
            y: 183,
            draggable: true,
            editable: false
          },
          properties: {
            labelPlacement: 'CUSTOM',
            labelOffsetX: 70,
            labelOffsetY: -35
          }
        }
      ]
    })

    expect(graph.edges[0].text).toBe('3天内')
    expect(graph.edges[0].properties).toMatchObject({
      labelPlacement: 'CUSTOM',
      labelOffsetX: 70,
      labelOffsetY: -35
    })
  })

  it('removes empty transient edge text objects', () => {
    const graph = sanitizeWorkflowGraphForSave({
      nodes: [],
      edges: [
        {
          id: 'edge_empty',
          sourceNodeId: 'start',
          targetNodeId: 'end',
          text: {
            value: '',
            x: 0,
            y: 0,
            draggable: true
          }
        }
      ]
    })

    expect(graph.edges[0]).not.toHaveProperty('text')
  })
})

function validBasicGraph(): WorkflowGraph {
  return {
    nodes: [
      { id: 'start', text: '开始', properties: { nodeType: 'START' } },
      { id: 'approval_1', text: '部门审批', properties: { nodeType: 'APPROVAL', approverType: 'DEPT_LEADER' } },
      { id: 'end', text: '结束', properties: { nodeType: 'END' } }
    ],
    edges: [
      { id: 'edge_start_approval', sourceNodeId: 'start', targetNodeId: 'approval_1' },
      { id: 'edge_approval_end', sourceNodeId: 'approval_1', targetNodeId: 'end' }
    ]
  }
}

function validConditionalGraph(): WorkflowGraph {
  return {
    nodes: [
      { id: 'start', text: '开始', properties: { nodeType: 'START' } },
      { id: 'check', text: '金额判断', properties: { nodeType: 'CONDITION' } },
      { id: 'approval_high', text: '高额审批', properties: { nodeType: 'APPROVAL', approverType: 'DEPT_LEADER' } },
      { id: 'approval_default', text: '默认审批', properties: { nodeType: 'APPROVAL', approverType: 'DEPT_LEADER' } },
      { id: 'end', text: '结束', properties: { nodeType: 'END' } }
    ],
    edges: [
      { id: 'edge_start_check', sourceNodeId: 'start', targetNodeId: 'check' },
      {
        id: 'edge_check_high',
        sourceNodeId: 'check',
        targetNodeId: 'approval_high',
        properties: { conditionType: 'EXPRESSION', conditionExpression: 'amount > 5000' }
      },
      { id: 'edge_check_default', sourceNodeId: 'check', targetNodeId: 'approval_default' },
      { id: 'edge_high_end', sourceNodeId: 'approval_high', targetNodeId: 'end' },
      { id: 'edge_default_end', sourceNodeId: 'approval_default', targetNodeId: 'end' }
    ]
  }
}

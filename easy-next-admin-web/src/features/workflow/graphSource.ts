import type { WorkflowGraph } from './types'

// 流程图 JSON 的解析、清洗和启用校验集中在这里，页面只负责编辑交互。
export interface WorkflowGraphSourceResult {
  ok: boolean
  graph?: WorkflowGraph
  source?: string
  message?: string
}

export interface WorkflowGraphValidationResult {
  valid: boolean
  message?: string
}

type WorkflowNodeType = 'START' | 'SUBMIT' | 'APPROVAL' | 'CC' | 'CONDITION' | 'END'

const emptyGraph: WorkflowGraph = {
  nodes: [],
  edges: []
}

export function formatWorkflowGraphSource(graphJson?: string) {
  if (graphJson === undefined) {
    return stringifyGraph(emptyGraph)
  }

  try {
    const parsed = JSON.parse(protectWorkflowEntityIds(graphJson)) as unknown
    if (!isWorkflowGraphObject(parsed)) {
      return graphJson
    }
    return stringifyGraph(normalizeGraph(parsed))
  } catch {
    return graphJson
  }
}

export function parseWorkflowGraphSource(source: string): WorkflowGraphSourceResult {
  let parsed: unknown

  try {
    parsed = JSON.parse(protectWorkflowEntityIds(source))
  } catch {
    return { ok: false, message: '流程源码不是有效 JSON' }
  }

  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return { ok: false, message: '流程源码必须是 JSON 对象' }
  }

  const graph = parsed as Partial<WorkflowGraph>
  if (!Array.isArray(graph.nodes)) {
    return { ok: false, message: '流程源码必须包含 nodes 数组' }
  }
  if (!Array.isArray(graph.edges)) {
    return { ok: false, message: '流程源码必须包含 edges 数组' }
  }
  if (!hasOnlyRecordItems(graph.nodes)) {
    return { ok: false, message: '流程源码 nodes 数组只能包含对象' }
  }
  if (!hasOnlyRecordItems(graph.edges)) {
    return { ok: false, message: '流程源码 edges 数组只能包含对象' }
  }

  const normalized = normalizeGraph(graph)
  return {
    ok: true,
    graph: normalized,
    source: stringifyGraph(normalized)
  }
}

export function parseWorkflowGraphJson(graphJson?: string): WorkflowGraph {
  if (!graphJson) return { nodes: [], edges: [] }
  try {
    const graph = JSON.parse(protectWorkflowEntityIds(graphJson)) as unknown
    if (!isWorkflowGraphObject(graph)) {
      return { nodes: [], edges: [] }
    }
    return normalizeGraph(graph)
  } catch {
    return { nodes: [], edges: [] }
  }
}

export function sanitizeWorkflowGraphForSave(graph: WorkflowGraph): WorkflowGraph {
  const normalized = normalizeGraph(graph)
  return {
    nodes: normalized.nodes.map(sanitizeWorkflowNodeForSave),
    edges: normalized.edges.map(sanitizeWorkflowEdgeForSave)
  }
}

export function validateWorkflowGraphForEnable(graph: WorkflowGraph): WorkflowGraphValidationResult {
  if (graph.nodes.length === 0) {
    return { valid: false, message: '启用流程前请先配置流程图' }
  }

  const nodeIds = new Set<string>()
  for (const node of graph.nodes) {
    const nodeId = graphNodeId(node)
    if (!nodeId) {
      return { valid: false, message: '启用流程存在未设置标识的节点' }
    }
    if (nodeIds.has(nodeId)) {
      return { valid: false, message: '启用流程存在重复节点标识' }
    }
    nodeIds.add(nodeId)
  }

  const startNodes = graph.nodes.filter((node) => inferNodeType(node) === 'START')
  if (startNodes.length === 0) {
    return { valid: false, message: '启用流程需要开始节点' }
  }
  if (startNodes.length > 1) {
    return { valid: false, message: '启用流程只能有一个开始节点' }
  }

  const endNodes = graph.nodes.filter((node) => inferNodeType(node) === 'END')
  if (endNodes.length === 0) {
    return { valid: false, message: '启用流程需要结束节点' }
  }
  if (endNodes.length > 1) {
    return { valid: false, message: '启用流程只能有一个结束节点' }
  }

  const approvalNodes = graph.nodes.filter((node) => inferNodeType(node) === 'APPROVAL')
  if (approvalNodes.length === 0) {
    return { valid: false, message: '启用流程至少需要一个审批节点' }
  }

  const invalidApproval = approvalNodes.find((node) => !hasValidApproverRule(node))
  if (invalidApproval) {
    return {
      valid: false,
      message: `${graphNodeText(invalidApproval, '审批节点')} 未配置有效处理人规则`
    }
  }

  if (graph.edges.length === 0) {
    return { valid: false, message: '启用流程需要配置节点连线' }
  }

  const outgoingEdges = new Map<string, Array<Record<string, unknown>>>()
  for (const edge of graph.edges) {
    const sourceNodeId = graphEdgeEndpoint(edge, 'source')
    const targetNodeId = graphEdgeEndpoint(edge, 'target')
    if (!sourceNodeId || !targetNodeId) {
      return { valid: false, message: '启用流程存在未设置端点的连线' }
    }
    if (!nodeIds.has(sourceNodeId) || !nodeIds.has(targetNodeId)) {
      return { valid: false, message: '启用流程存在连接到不存在节点的连线' }
    }
    const conditionType = edgeConditionType(edge)
    if (!['ALWAYS', 'EXPRESSION'].includes(conditionType)) {
      return { valid: false, message: '启用流程存在不支持的连线条件类型' }
    }
    if (conditionType === 'EXPRESSION' && !edgeConditionExpression(edge)) {
      return { valid: false, message: '启用流程存在未配置表达式的条件连线' }
    }
    outgoingEdges.set(sourceNodeId, [...(outgoingEdges.get(sourceNodeId) || []), edge])
  }

  for (const [sourceNodeId, edges] of outgoingEdges) {
    if (edges.length <= 1) continue
    const conditionalCount = edges.filter((edge) => edgeConditionType(edge) === 'EXPRESSION').length
    const defaultCount = edges.filter((edge) => edgeConditionType(edge) === 'ALWAYS').length
    const sourceName = graphNodeText(graph.nodes.find((node) => graphNodeId(node) === sourceNodeId) || {}, sourceNodeId)
    if (conditionalCount === 0) {
      return { valid: false, message: `${sourceName} 存在多条默认路径，请配置条件分支` }
    }
    if (defaultCount === 0) {
      return { valid: false, message: `${sourceName} 条件分支需要配置默认路径` }
    }
    if (defaultCount > 1) {
      return { valid: false, message: `${sourceName} 条件分支只能配置一条默认路径` }
    }
  }

  if (!canReachEndNode(graph, graphNodeId(startNodes[0]), graphNodeId(endNodes[0]))) {
    return { valid: false, message: '启用流程需要从开始节点连通到结束节点' }
  }

  return { valid: true }
}

function sanitizeWorkflowNodeForSave(node: Record<string, unknown>) {
  const properties = graphProperties(node)
  if (!Object.keys(properties).length) return node
  const nodeType = inferNodeType(node)
  if (nodeType === 'CC') {
    return { ...node, properties: sanitizeCcNodeProperties(properties) }
  }
  return { ...node, properties: sanitizeNodeProperties(properties, nodeType) }
}

function sanitizeWorkflowEdgeForSave(edge: Record<string, unknown>) {
  const label = graphNodeText(edge, '')
  if (!label) {
    const { text, ...rest } = edge
    void text
    return rest
  }
  return {
    ...edge,
    text: label
  }
}

function sanitizeCcNodeProperties(properties: Record<string, unknown>) {
  return {
    ...baseNodeProperties(properties, 'CC'),
    nodeType: 'CC',
    ccUserIds: toEntityIds(properties.ccUserIds)
  }
}

function sanitizeNodeProperties(properties: Record<string, unknown>, nodeType: WorkflowNodeType) {
  const result = baseNodeProperties(properties, nodeType)
  if (nodeType !== 'APPROVAL') {
    return result
  }

  const approverType = stringProperty(properties.approverType).trim()
  return {
    ...result,
    approveType: stringProperty(properties.approveType).trim() || 'ANY_ONE',
    ...(approverType ? { approverType } : {}),
    ...(approverType === 'USER' ? { assigneeIds: toEntityIds(properties.assigneeIds) } : {}),
    ...(approverType === 'ROLE' ? { roleCode: stringProperty(properties.roleCode).trim() } : {})
  }
}

function baseNodeProperties(properties: Record<string, unknown>, nodeType: WorkflowNodeType) {
  const result: Record<string, unknown> = { nodeType }
  copyDefinedProperty(result, properties, 'description')
  copyDefinedProperty(result, properties, 'style')
  copyDefinedProperty(result, properties, 'textStyle')
  copyDefinedProperty(result, properties, 'width')
  copyDefinedProperty(result, properties, 'height')
  copyDefinedProperty(result, properties, 'radius')
  copyDefinedProperty(result, properties, 'r')
  copyDefinedProperty(result, properties, 'rx')
  copyDefinedProperty(result, properties, 'ry')
  return result
}

function copyDefinedProperty(target: Record<string, unknown>, source: Record<string, unknown>, key: string) {
  if (source[key] !== undefined) {
    target[key] = source[key]
  }
}

function normalizeGraph(graph: Partial<WorkflowGraph>): WorkflowGraph {
  return {
    nodes: Array.isArray(graph.nodes) ? graph.nodes : [],
    edges: Array.isArray(graph.edges) ? graph.edges : []
  }
}

function isWorkflowGraphObject(value: unknown): value is WorkflowGraph {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return false
  }

  const graph = value as Partial<WorkflowGraph>
  return hasOnlyRecordItems(graph.nodes) && hasOnlyRecordItems(graph.edges)
}

function hasOnlyRecordItems(items: unknown): items is Array<Record<string, unknown>> {
  return Array.isArray(items) && items.every(isRecord)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function stringifyGraph(graph: WorkflowGraph) {
  return JSON.stringify(graph, null, 2)
}

function inferNodeType(node: Record<string, unknown>): WorkflowNodeType {
  const properties = graphProperties(node)
  const configuredType = stringProperty(properties.nodeType)
  if (isWorkflowNodeType(configuredType)) {
    return configuredType
  }
  const id = graphNodeId(node).toLowerCase()
  const type = String(node.type || '').toLowerCase()
  const text = graphNodeText(node, '')
  if (id === 'start' || text.includes('开始')) return 'START'
  if (id === 'end' || text.includes('结束')) return 'END'
  if (id.includes('submit') || text.startsWith('提交')) return 'SUBMIT'
  if (id.includes('cc') || text.includes('抄送')) return 'CC'
  if (id.includes('condition') || type.includes('diamond') || text.includes('条件') || text.includes('判断')) return 'CONDITION'
  return 'APPROVAL'
}

function hasValidApproverRule(node: Record<string, unknown>) {
  const properties = graphProperties(node)
  const approverType = stringProperty(properties.approverType)
  const assigneeIds = toEntityIds(properties.assigneeIds)
  const roleCode = stringProperty(properties.roleCode).trim()
  if (approverType === 'USER') return assigneeIds.length > 0
  if (approverType === 'ROLE') return Boolean(roleCode)
  return ['INITIATOR', 'INITIATOR_SELECTED', 'MANAGER', 'DEPT_LEADER', 'UPPER_DEPT_LEADER'].includes(approverType)
}

function edgeConditionType(edge: Record<string, unknown>) {
  return stringProperty(graphProperties(edge).conditionType).trim() || 'ALWAYS'
}

function edgeConditionExpression(edge: Record<string, unknown>) {
  return stringProperty(graphProperties(edge).conditionExpression).trim()
}

function canReachEndNode(graph: WorkflowGraph, startNodeId: string, endNodeId: string) {
  const nextNodeIds = new Map<string, string[]>()
  graph.edges.forEach((edge) => {
    const sourceNodeId = graphEdgeEndpoint(edge, 'source')
    const targetNodeId = graphEdgeEndpoint(edge, 'target')
    nextNodeIds.set(sourceNodeId, [...(nextNodeIds.get(sourceNodeId) || []), targetNodeId])
  })

  const visited = new Set<string>()
  const pending = [startNodeId]
  while (pending.length > 0) {
    const currentNodeId = pending.shift()
    if (!currentNodeId || visited.has(currentNodeId)) continue
    if (currentNodeId === endNodeId) return true
    visited.add(currentNodeId)
    pending.push(...(nextNodeIds.get(currentNodeId) || []))
  }
  return false
}

function graphProperties(data: Record<string, unknown>) {
  return isRecord(data.properties) ? data.properties : {}
}

function graphNodeId(node: Record<string, unknown>) {
  const id = node.id
  return typeof id === 'string' || typeof id === 'number' ? String(id).trim() : ''
}

function graphNodeText(node: Record<string, unknown>, fallback = '-') {
  const text = node.text
  if (typeof text === 'string' && text.trim()) return text
  if (isRecord(text)) {
    const value = text.value || text.text
    if (typeof value === 'string' && value.trim()) return value
  }
  return fallback
}

function graphEdgeEndpoint(edge: Record<string, unknown>, endpoint: 'source' | 'target') {
  const value = endpoint === 'source'
    ? edge.sourceNodeId
    : edge.targetNodeId
  return typeof value === 'string' || typeof value === 'number' ? String(value).trim() : ''
}

function stringProperty(value: unknown) {
  return typeof value === 'string' ? value : ''
}

function toEntityIds(value: unknown): Array<string | number> {
  if (!value) return []
  if (Array.isArray(value)) {
    return value
      .map(normalizeEntityId)
      .filter((item): item is string => Boolean(item))
  }
  const entityId = normalizeEntityId(value)
  if (entityId) return [entityId]
  return []
}

function normalizeEntityId(value: unknown) {
  if (typeof value === 'string') {
    const trimmed = value.trim()
    return trimmed ? trimmed : undefined
  }
  if (typeof value === 'number' && Number.isFinite(value)) {
    return String(value)
  }
  return undefined
}

function protectWorkflowEntityIds(source: string) {
  return source.replace(/("(?:assigneeIds|ccUserIds|addSignUserIds)"\s*:\s*\[)([^\]]*)(\])/g, (_match, start: string, body: string, end: string) => {
    const normalizedBody = body.replace(/(^|[\s,])(\d{16,})(?=\s*(?:,|$))/g, '$1"$2"')
    return `${start}${normalizedBody}${end}`
  })
}

function isWorkflowNodeType(value: string): value is WorkflowNodeType {
  return ['START', 'SUBMIT', 'APPROVAL', 'CC', 'CONDITION', 'END'].includes(value)
}

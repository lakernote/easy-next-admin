import { parseWorkflowGraphJson } from './source'
import type { WorkflowGraph, WorkflowInstanceDetail } from './types'

export type GraphNodeState = 'done' | 'current' | 'todo'
export type GraphEdgeState = 'done' | 'current' | 'todo'

export interface RuntimeGraphNode {
  id: string
  label: string
  nodeType: string
  x: number
  y: number
  state: GraphNodeState
}

export interface RuntimeGraphPoint {
  x: number
  y: number
}

export interface RuntimeGraphEdge {
  id: string
  sourceNodeId: string
  targetNodeId: string
  label: string
  sourceX: number
  sourceY: number
  sourceNodeType: string
  targetX: number
  targetY: number
  targetNodeType: string
  state: GraphEdgeState
  pointsList?: RuntimeGraphPoint[]
  labelX?: number
  labelY?: number
}

type EdgeLabelPlacement = 'AUTO' | 'START' | 'END' | 'ABOVE' | 'BELOW' | 'CUSTOM'

export interface RuntimeGraph {
  nodes: RuntimeGraphNode[]
  edges: RuntimeGraphEdge[]
  viewBox: string
}

const flowPaddingX = 40
const flowPaddingY = 32

export function buildRuntimeGraph(instanceDetail?: WorkflowInstanceDetail): RuntimeGraph {
  if (!instanceDetail) return emptyRuntimeGraph()
  const graph = parseWorkflowGraph(instanceDetail.graphJson || instanceDetail.version?.graphJson || instanceDetail.instance.definitionSnapshotJson)
  if (!graph.nodes.length) return emptyRuntimeGraph()

  const isRunning = instanceDetail.instance.status === 'RUNNING'
  const pendingKeys = new Set(instanceDetail.tasks.filter((task) => task.status === 'PENDING').map((task) => task.nodeKey))
  const doneKeys = new Set<string>()
  const touchedEdges = new Set<string>()
  const finalApprovalNodeKeys = new Set<string>()

  instanceDetail.tasks.forEach((task) => {
    if (task.status !== 'PENDING' && task.status !== 'CANCELED') doneKeys.add(task.nodeKey)
  })
  instanceDetail.events.forEach((event) => {
    if (event.fromNodeKey) doneKeys.add(event.fromNodeKey)
    if (event.toNodeKey && !['REJECT', 'RETURN'].includes(event.action)) doneKeys.add(event.toNodeKey)
    if (event.fromNodeKey && event.toNodeKey) touchedEdges.add(`${event.fromNodeKey}->${event.toNodeKey}`)
    if (event.action === 'APPROVE' && event.fromNodeKey) finalApprovalNodeKeys.add(event.fromNodeKey)
  })

  const activeNodeKeys = isRunning
    ? new Set([...pendingKeys, instanceDetail.instance.currentNodeKey].filter((key): key is string => Boolean(key)))
    : new Set<string>()
  const completedAnchorKeys = new Set(doneKeys)
  completedAnchorKeys.delete('end')
  collectAncestorKeys(graph.edges, isRunning ? activeNodeKeys : completedAnchorKeys).forEach((key) => doneKeys.add(key))
  if (instanceDetail.instance.startedAt) {
    doneKeys.add('start')
    doneKeys.add('submit')
  }
  if (!isRunning) {
    pendingKeys.clear()
    if (instanceDetail.instance.status === 'APPROVED') {
      doneKeys.add('end')
      const terminalPath = collectApprovedTerminalPath(graph, finalApprovalNodeKeys)
      terminalPath.nodes.forEach((key) => doneKeys.add(key))
      terminalPath.edges.forEach((key) => touchedEdges.add(key))
    }
  }

  const nodes = graph.nodes.map((node, index) => {
    const id = graphNodeId(node) || `node_${index}`
    const nodeType = inferNodeType(node)
    const isCurrent = isRunning && (pendingKeys.has(id) || id === instanceDetail.instance.currentNodeKey)
    const state: GraphNodeState = isCurrent ? 'current' : doneKeys.has(id) ? 'done' : 'todo'
    return {
      id,
      label: graphNodeText(node, nodeTypeText(nodeType)),
      nodeType,
      x: graphNodeNumber(node.x, 120 + index * 180),
      y: graphNodeNumber(node.y, 180),
      state
    }
  }).filter((node) => Boolean(node.id))

  const nodeMap = new Map(nodes.map((node) => [node.id, node]))
  const edges: RuntimeGraphEdge[] = []
  graph.edges.forEach((edge, index) => {
    const sourceNodeId = graphEdgeEndpoint(edge, 'source')
    const targetNodeId = graphEdgeEndpoint(edge, 'target')
    const source = nodeMap.get(sourceNodeId)
    const target = nodeMap.get(targetNodeId)
    if (!source || !target) return
    const key = `${sourceNodeId}->${targetNodeId}`
    const state: GraphEdgeState = touchedEdges.has(key) || (source.state === 'done' && target.state === 'done')
      ? 'done'
      : source.state === 'done' && target.state === 'current'
        ? 'current'
        : 'todo'
    const pointsList = graphEdgePoints(edge)
    const runtimeEdge: RuntimeGraphEdge = {
      id: graphEdgeId(edge, index),
      sourceNodeId,
      targetNodeId,
      label: graphNodeText(edge, ''),
      sourceX: source.x,
      sourceY: source.y,
      sourceNodeType: source.nodeType,
      targetX: target.x,
      targetY: target.y,
      targetNodeType: target.nodeType,
      state,
      ...(pointsList ? { pointsList } : {})
    }
    const textPosition = graphTextPosition(edge) || graphLabelPosition(edge, runtimeEdge)
    edges.push({
      ...runtimeEdge,
      ...(textPosition ? { labelX: textPosition.x, labelY: textPosition.y } : {})
    })
  })

  return { nodes, edges, viewBox: runtimeViewBox(nodes, edges) }
}

export function emptyRuntimeGraph(): RuntimeGraph {
  return { nodes: [], edges: [], viewBox: '0 0 1160 320' }
}

export function edgePath(edge: RuntimeGraphEdge) {
  if (edge.pointsList && edge.pointsList.length >= 2) {
    return edge.pointsList.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' ')
  }
  const { x: sourceX, y: sourceY } = edgeConnectorPoint(
    edge.sourceX,
    edge.sourceY,
    edge.targetX,
    edge.targetY,
    edge.sourceNodeType
  )
  const { x: targetX, y: targetY } = edgeConnectorPoint(
    edge.targetX,
    edge.targetY,
    edge.sourceX,
    edge.sourceY,
    edge.targetNodeType
  )
  const midX = (sourceX + targetX) / 2
  return `M ${sourceX} ${sourceY} L ${midX} ${sourceY} L ${midX} ${targetY} L ${targetX} ${targetY}`
}

export function edgeLabelX(edge: RuntimeGraphEdge) {
  if (typeof edge.labelX === 'number' && Number.isFinite(edge.labelX)) return edge.labelX
  const middlePoint = edgeMiddlePoint(edge)
  if (middlePoint) return middlePoint.x
  return (edge.sourceX + edge.targetX) / 2
}

export function edgeLabelY(edge: RuntimeGraphEdge) {
  if (typeof edge.labelY === 'number' && Number.isFinite(edge.labelY)) return edge.labelY
  const middlePoint = edgeMiddlePoint(edge)
  if (middlePoint) return middlePoint.y - 8
  return (edge.sourceY + edge.targetY) / 2 - 8
}

function parseWorkflowGraph(graphJson?: string): WorkflowGraph {
  return parseWorkflowGraphJson(graphJson)
}

function collectAncestorKeys(edges: Array<Record<string, unknown>>, targetKeys: Set<string>) {
  const reverseMap = new Map<string, string[]>()
  edges.forEach((edge) => {
    const source = graphEdgeEndpoint(edge, 'source')
    const target = graphEdgeEndpoint(edge, 'target')
    if (!source || !target) return
    reverseMap.set(target, [...(reverseMap.get(target) || []), source])
  })

  const ancestors = new Set<string>()
  const stack = [...targetKeys]
  while (stack.length) {
    const target = stack.pop()
    if (!target) continue
    ;(reverseMap.get(target) || []).forEach((source) => {
      if (ancestors.has(source) || targetKeys.has(source)) return
      ancestors.add(source)
      stack.push(source)
    })
  }
  return ancestors
}

function collectApprovedTerminalPath(graph: WorkflowGraph, sourceKeys: Set<string>) {
  const outgoingMap = new Map<string, Array<Record<string, unknown>>>()
  const nodeTypeMap = new Map<string, string>()
  graph.nodes.forEach((node, index) => {
    const id = graphNodeId(node) || `node_${index}`
    nodeTypeMap.set(id, inferNodeType(node))
  })
  graph.edges.forEach((edge) => {
    const source = graphEdgeEndpoint(edge, 'source')
    if (!source) return
    outgoingMap.set(source, [...(outgoingMap.get(source) || []), edge])
  })

  const nodes = new Set<string>()
  const edges = new Set<string>()
  const visited = new Set<string>()
  const stack = [...sourceKeys]
  while (stack.length) {
    const source = stack.pop()
    if (!source || visited.has(source)) continue
    visited.add(source)
    ;(outgoingMap.get(source) || []).forEach((edge) => {
      const target = graphEdgeEndpoint(edge, 'target')
      if (!target) return
      const targetType = nodeTypeMap.get(target) || ''
      if (!['CC', 'CONDITION', 'END', 'START', 'SUBMIT'].includes(targetType)) return
      nodes.add(target)
      edges.add(`${source}->${target}`)
      if (targetType !== 'END') stack.push(target)
    })
  }

  return { nodes, edges }
}

function runtimeViewBox(nodes: RuntimeGraphNode[], edges: RuntimeGraphEdge[]) {
  if (!nodes.length) return '0 0 1160 320'
  const nodeMinX = nodes.map((node) => node.x - nodeHorizontalRadius(node.nodeType))
  const nodeMaxX = nodes.map((node) => node.x + nodeHorizontalRadius(node.nodeType))
  const nodeMinY = nodes.map((node) => node.y - nodeVerticalRadius(node.nodeType))
  const nodeMaxY = nodes.map((node) => node.y + nodeVerticalRadius(node.nodeType) + 18)
  const edgePoints = edges.flatMap((edge) => edge.pointsList || [])
  const labelPoints = edges
    .filter((edge) => typeof edge.labelX === 'number' && typeof edge.labelY === 'number')
    .map((edge) => ({ x: edge.labelX as number, y: edge.labelY as number }))
  const allPoints = [...edgePoints, ...labelPoints]
  const minX = Math.min(...nodeMinX, ...allPoints.map((point) => point.x)) - flowPaddingX
  const maxX = Math.max(...nodeMaxX, ...allPoints.map((point) => point.x)) + flowPaddingX
  const minY = Math.min(...nodeMinY, ...allPoints.map((point) => point.y)) - flowPaddingY
  const maxY = Math.max(...nodeMaxY, ...allPoints.map((point) => point.y)) + flowPaddingY
  return `${Math.floor(minX)} ${Math.floor(minY)} ${Math.ceil(maxX - minX)} ${Math.ceil(maxY - minY)}`
}

function edgeConnectorPoint(x: number, y: number, toX: number, toY: number, nodeType: string) {
  const dx = toX - x
  const dy = toY - y
  const length = Math.hypot(dx, dy) || 1
  const ux = dx / length
  const uy = dy / length
  const offset = nodeBoundaryDistance(nodeType, ux, uy) + 8
  return {
    x: Math.round(x + ux * offset),
    y: Math.round(y + uy * offset)
  }
}

function nodeBoundaryDistance(nodeType: string, ux: number, uy: number) {
  if (nodeType === 'START' || nodeType === 'END') return 32
  if (nodeType === 'CONDITION') return 1 / (Math.abs(ux) / 52 + Math.abs(uy) / 52 || 1)
  const halfWidth = 84
  const halfHeight = 32
  const xDistance = Math.abs(ux) > 0.001 ? halfWidth / Math.abs(ux) : Number.POSITIVE_INFINITY
  const yDistance = Math.abs(uy) > 0.001 ? halfHeight / Math.abs(uy) : Number.POSITIVE_INFINITY
  return Math.min(xDistance, yDistance)
}

function nodeHorizontalRadius(nodeType: string) {
  if (nodeType === 'START' || nodeType === 'END') return 32
  if (nodeType === 'CONDITION') return 52
  return 84
}

function nodeVerticalRadius(nodeType: string) {
  if (nodeType === 'START' || nodeType === 'END') return 32
  if (nodeType === 'CONDITION') return 52
  return 48
}

function nodeTypeText(nodeType?: string) {
  const map: Record<string, string> = { START: '开始', SUBMIT: '提交', APPROVAL: '审批', CC: '抄送', CONDITION: '条件', END: '结束' }
  return nodeType ? map[nodeType] || nodeType : '-'
}

function inferNodeType(node: Record<string, unknown>) {
  const properties = graphProperties(node)
  const configuredType = stringProperty(properties.nodeType)
  if (configuredType && ['START', 'SUBMIT', 'APPROVAL', 'CC', 'CONDITION', 'END'].includes(configuredType)) return configuredType
  const id = graphNodeId(node).toLowerCase()
  const type = String(node.type || '').toLowerCase()
  const text = graphNodeText(node, '')
  if (id === 'start' || text.includes('开始')) return 'START'
  if (id === 'end' || text.includes('结束')) return 'END'
  if (id.includes('submit') || text.includes('提交')) return 'SUBMIT'
  if (id.includes('cc') || text.includes('抄送')) return 'CC'
  if (id.includes('condition') || type.includes('diamond') || text.includes('条件') || text.includes('判断')) return 'CONDITION'
  return 'APPROVAL'
}

function graphProperties(data: Record<string, unknown>) {
  const properties = data.properties
  return properties && typeof properties === 'object' ? (properties as Record<string, unknown>) : {}
}

function stringProperty(value: unknown) {
  return typeof value === 'string' ? value : ''
}

function graphNodeId(node: Record<string, unknown>) {
  const id = node.id
  return typeof id === 'string' || typeof id === 'number' ? String(id) : ''
}

function graphNodeText(node: Record<string, unknown>, fallback = '-') {
  const text = node.text
  if (typeof text === 'string') return text
  if (text && typeof text === 'object') {
    const value = (text as { value?: unknown; text?: unknown }).value || (text as { value?: unknown; text?: unknown }).text
    if (typeof value === 'string' && value.trim()) return value
  }
  return fallback
}

function graphNodeNumber(value: unknown, fallback: number) {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function graphEdgePoints(edge: Record<string, unknown>) {
  const points = edge.pointsList
  if (!Array.isArray(points)) return undefined
  const result = points
    .map((point) => {
      if (!point || typeof point !== 'object') return undefined
      const x = graphNodeNumber((point as { x?: unknown }).x, Number.NaN)
      const y = graphNodeNumber((point as { y?: unknown }).y, Number.NaN)
      return Number.isFinite(x) && Number.isFinite(y) ? { x, y } : undefined
    })
    .filter((point): point is RuntimeGraphPoint => Boolean(point))
  return result.length >= 2 ? result : undefined
}

function graphTextPosition(edge: Record<string, unknown>) {
  const text = edge.text
  if (!text || typeof text !== 'object') return undefined
  const x = graphNodeNumber((text as { x?: unknown }).x, Number.NaN)
  const y = graphNodeNumber((text as { y?: unknown }).y, Number.NaN)
  return Number.isFinite(x) && Number.isFinite(y) ? { x, y } : undefined
}

function graphLabelPosition(edgeData: Record<string, unknown>, edge: RuntimeGraphEdge) {
  if (!graphNodeText(edgeData, '')) return undefined
  const properties = graphProperties(edgeData)
  const fallbackPlacement: EdgeLabelPlacement = edge.sourceNodeType === 'CONDITION' ? 'START' : 'AUTO'
  const placement = properties.labelPlacement ? normalizeEdgeLabelPlacement(properties.labelPlacement) : fallbackPlacement
  const points = runtimeEdgePoints(edge)
  if (points.length < 2) return undefined
  const basePlacement = placement === 'CUSTOM' ? fallbackPlacement : placement
  const base = edgeLabelBasePoint(points, basePlacement)
  const { x: baseOffsetX, y: baseOffsetY } = edgeLabelBaseOffset(points, basePlacement)
  return {
    x: Math.round(base.x + baseOffsetX + numberProperty(properties.labelOffsetX, 0)),
    y: Math.round(base.y + baseOffsetY + numberProperty(properties.labelOffsetY, 0))
  }
}

function runtimeEdgePoints(edge: RuntimeGraphEdge) {
  if (edge.pointsList && edge.pointsList.length >= 2) return edge.pointsList
  const { x: sourceX, y: sourceY } = edgeConnectorPoint(
    edge.sourceX,
    edge.sourceY,
    edge.targetX,
    edge.targetY,
    edge.sourceNodeType
  )
  const { x: targetX, y: targetY } = edgeConnectorPoint(
    edge.targetX,
    edge.targetY,
    edge.sourceX,
    edge.sourceY,
    edge.targetNodeType
  )
  const midX = Math.round(sourceX + (targetX - sourceX) / 2)
  if (Math.abs(sourceY - targetY) < 4) return [{ x: sourceX, y: sourceY }, { x: targetX, y: targetY }]
  return [
    { x: sourceX, y: sourceY },
    { x: midX, y: sourceY },
    { x: midX, y: targetY },
    { x: targetX, y: targetY }
  ]
}

function normalizeEdgeLabelPlacement(value: unknown): EdgeLabelPlacement {
  return ['AUTO', 'START', 'END', 'ABOVE', 'BELOW', 'CUSTOM'].includes(String(value))
    ? String(value) as EdgeLabelPlacement
    : 'AUTO'
}

function numberProperty(value: unknown, fallback: number) {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function edgeLabelBasePoint(points: RuntimeGraphPoint[], placement: EdgeLabelPlacement) {
  if (placement === 'START') {
    const branchAnchor = conditionBranchLabelAnchor(points)
    if (branchAnchor) return { x: branchAnchor.x, y: branchAnchor.y }
    return pointOnPolyline(points, 0.22)
  }
  if (placement === 'END') return pointOnPolyline(points, 0.72)
  if (placement === 'AUTO') {
    const firstSegmentAnchor = firstSegmentLabelAnchor(points)
    if (firstSegmentAnchor) return firstSegmentAnchor
  }
  return pointOnPolyline(points, 0.5)
}

function edgeLabelBaseOffset(points: RuntimeGraphPoint[], placement: EdgeLabelPlacement) {
  if (placement === 'ABOVE') return { x: 0, y: -18 }
  if (placement === 'BELOW') return { x: 0, y: 20 }
  if (placement === 'START') {
    const branchAnchor = conditionBranchLabelAnchor(points)
    if (branchAnchor) return { x: 0, y: branchAnchor.isUpperBranch ? 24 : -22 }
    return { x: 8, y: -18 }
  }
  if (placement === 'END') return { x: -8, y: -18 }
  if (placement === 'AUTO' && firstSegmentLabelAnchor(points)) return { x: 0, y: -20 }
  const first = points[0]
  const last = points[points.length - 1]
  const verticalTravel = Math.abs(last.y - first.y)
  return { x: 0, y: verticalTravel > 24 ? -16 : -18 }
}

function conditionBranchLabelAnchor(points: RuntimeGraphPoint[]) {
  if (!orthogonalPolyline(points)) return undefined
  const start = points[0]
  const branchPoint = points[2]
  const end = points[points.length - 1]
  const horizontalProgress = 0.46
  return {
    x: Math.round(branchPoint.x + (end.x - branchPoint.x) * horizontalProgress),
    y: Math.round(branchPoint.y),
    isUpperBranch: end.y < start.y
  }
}

function firstSegmentLabelAnchor(points: RuntimeGraphPoint[]) {
  if (!orthogonalPolyline(points)) return undefined
  const start = points[0]
  const bend = points[1]
  return {
    x: Math.round(start.x + (bend.x - start.x) * 0.58),
    y: Math.round(start.y)
  }
}

function orthogonalPolyline(points: RuntimeGraphPoint[]) {
  if (points.length < 4) return false
  const start = points[0]
  const firstBend = points[1]
  const secondBend = points[2]
  const end = points[points.length - 1]
  return Math.abs(start.y - firstBend.y) < 4
    && Math.abs(firstBend.x - secondBend.x) < 4
    && Math.abs(secondBend.y - end.y) < 4
}

function pointOnPolyline(points: RuntimeGraphPoint[], ratio: number) {
  const segments = points.slice(1).map((point, index) => {
    const previous = points[index]
    return {
      start: previous,
      end: point,
      length: Math.hypot(point.x - previous.x, point.y - previous.y)
    }
  })
  const totalLength = segments.reduce((total, segment) => total + segment.length, 0)
  if (!totalLength) return points[0]
  let distance = totalLength * ratio
  for (const segment of segments) {
    if (distance <= segment.length) {
      const segmentRatio = segment.length ? distance / segment.length : 0
      return {
        x: Math.round(segment.start.x + (segment.end.x - segment.start.x) * segmentRatio),
        y: Math.round(segment.start.y + (segment.end.y - segment.start.y) * segmentRatio)
      }
    }
    distance -= segment.length
  }
  return points[points.length - 1]
}

function edgeMiddlePoint(edge: RuntimeGraphEdge) {
  const points = edge.pointsList
  if (!points || points.length < 2) return undefined
  const segments = points.slice(1).map((point, index) => {
    const previous = points[index]
    return { start: previous, end: point, length: Math.hypot(point.x - previous.x, point.y - previous.y) }
  })
  const totalLength = segments.reduce((total, segment) => total + segment.length, 0)
  if (!totalLength) return points[Math.floor(points.length / 2)]
  let distance = totalLength / 2
  for (const segment of segments) {
    if (distance > segment.length) {
      distance -= segment.length
      continue
    }
    const ratio = segment.length ? distance / segment.length : 0
    return {
      x: segment.start.x + (segment.end.x - segment.start.x) * ratio,
      y: segment.start.y + (segment.end.y - segment.start.y) * ratio
    }
  }
  return points[points.length - 1]
}

function graphEdgeEndpoint(edge: Record<string, unknown>, direction: 'source' | 'target') {
  const key = direction === 'source'
    ? edge.sourceNodeId
    : edge.targetNodeId
  return typeof key === 'string' || typeof key === 'number' ? String(key) : ''
}

function graphEdgeId(edge: Record<string, unknown>, index: number) {
  const id = edge.id
  return typeof id === 'string' || typeof id === 'number' ? String(id) : `edge_${index}`
}

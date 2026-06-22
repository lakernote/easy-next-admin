import type { WorkflowGraph } from '../types'

export type WorkflowNodeType = 'START' | 'SUBMIT' | 'APPROVAL' | 'CC' | 'CONDITION' | 'END'
export type WorkflowLogicNodeType = 'workflow-circle' | 'workflow-rect' | 'workflow-diamond'
export type EdgeLabelPlacement = 'AUTO' | 'ABOVE' | 'BELOW' | 'START' | 'END' | 'CUSTOM'

export const workflowEditorEdgeType = 'polyline'
export const edgeDefaultColor = '#64748b'

export interface WorkflowRoutePoint {
  x: number
  y: number
}

export interface WorkflowEdgeRoute {
  startPoint?: WorkflowRoutePoint
  endPoint?: WorkflowRoutePoint
  pointsList: WorkflowRoutePoint[]
}

const workflowLayoutXStart = 116
const workflowLayoutXGap = 240
const workflowLayoutCenterY = 296
const workflowLayoutYGap = 150
const workflowConditionSplitOffset = 72
const workflowMergeJoinOffset = 58
const workflowMergeLayerSpan = 1
const workflowLaneStackGap = 82

export function applyWorkflowGraphVisuals(graph: WorkflowGraph): WorkflowGraph {
  const visualNodes = graph.nodes.map((node) => {
    const properties = graphProperties(node)
    const nodeType = inferNodeType(node)
    return {
      ...node,
      type: workflowLogicType(nodeType),
      properties: {
        ...properties,
        ...nodeVisualProperties(nodeType)
      }
    }
  })
  const nodeMap = new Map(visualNodes.map((node) => [graphNodeId(node), node]))
  return {
    nodes: visualNodes,
    edges: graph.edges.map((edge) => {
      const properties = edgePropertiesWithoutColor(graphProperties(edge))
      const labelPlacement = normalizeEdgeLabelPlacement(properties.labelPlacement)
      const sourceType = inferNodeType(nodeMap.get(graphEdgeEndpoint(edge, 'source')) || {})
      const fallbackPlacement: EdgeLabelPlacement = sourceType === 'CONDITION' ? 'START' : 'AUTO'
      const nextEdge = {
        ...edge,
        type: workflowEditorEdgeType,
        properties: {
          ...properties,
          labelPlacement,
          labelOffsetX: numberProperty(properties.labelOffsetX, 0),
          labelOffsetY: numberProperty(properties.labelOffsetY, 0),
          ...edgeVisualProperties()
        }
      }
      const edgePoints = edgePointsFromEdge(nextEdge)
      const draggedLayout = edgeLabelLayoutFromDraggedText(nextEdge, edgePoints, fallbackPlacement)
      const visualEdge = draggedLayout ? {
        ...nextEdge,
        properties: {
          ...nextEdge.properties,
          ...draggedLayout
        }
      } : nextEdge
      return {
        ...visualEdge,
        text: normalizedWorkflowEdgeText(visualEdge, edgePoints, fallbackPlacement)
      }
    })
  }
}

export function optimizeWorkflowGraphLayout(graph: WorkflowGraph): WorkflowGraph {
  if (!graph.nodes.length) return graph
  const nodeIds = graph.nodes.map((node, index) => graphNodeId(node) || `node_${index}`)
  const nodeMap = new Map(graph.nodes.map((node, index) => [nodeIds[index], node]))
  const incomingCount = new Map(nodeIds.map((id) => [id, 0]))
  const incomingMap = new Map<string, string[]>()
  const outgoingMap = new Map<string, string[]>()

  graph.edges.forEach((edge) => {
    const source = graphEdgeEndpoint(edge, 'source')
    const target = graphEdgeEndpoint(edge, 'target')
    if (!source || !target || !nodeMap.has(source) || !nodeMap.has(target)) return
    outgoingMap.set(source, [...(outgoingMap.get(source) || []), target])
    incomingMap.set(target, [...(incomingMap.get(target) || []), source])
    incomingCount.set(target, (incomingCount.get(target) || 0) + 1)
  })

  const layerMap = new Map<string, number>()
  const startIds = nodeIds.filter((id) => inferNodeType(nodeMap.get(id) || {}) === 'START')
  const rootIds = startIds.length ? startIds : nodeIds.filter((id) => (incomingCount.get(id) || 0) === 0)
  const queue = [...rootIds]
  queue.forEach((id) => layerMap.set(id, 0))

  while (queue.length) {
    const source = queue.shift()
    if (!source) continue
    const sourceLayer = layerMap.get(source) || 0
    ;(outgoingMap.get(source) || []).forEach((target) => {
      const nextLayer = sourceLayer + 1
      if ((layerMap.get(target) ?? -1) >= nextLayer) return
      layerMap.set(target, nextLayer)
      queue.push(target)
    })
  }
  normalizeWorkflowLayoutLayers(nodeIds, nodeMap, graph.edges, incomingCount, layerMap)

  let fallbackLayer = Math.max(0, ...Array.from(layerMap.values()))
  nodeIds.forEach((id) => {
    if (layerMap.has(id)) return
    fallbackLayer += 1
    layerMap.set(id, fallbackLayer)
  })

  const layers = new Map<number, Array<{ node: Record<string, unknown>; id: string; index: number }>>()
  graph.nodes.forEach((node, index) => {
    const id = nodeIds[index]
    const layer = layerMap.get(id) || 0
    layers.set(layer, [...(layers.get(layer) || []), { node, id, index }])
  })

  const orderedLayers = Array.from(layers.entries()).sort(([left], [right]) => left - right)
  const laneMap = workflowLayoutLaneMap(nodeIds, nodeMap, orderedLayers, incomingMap, outgoingMap)
  const layoutPositionMap = new Map<string, { x: number; y: number }>()

  orderedLayers.forEach(([layer, items]) => {
    const sortedItems = [...items].sort((left, right) => {
      const laneDiff = (laneMap.get(left.id) || 0) - (laneMap.get(right.id) || 0)
      if (Math.abs(laneDiff) > 0.01) return laneDiff
      const yDiff = graphNodeNumber(left.node.y, workflowLayoutCenterY) - graphNodeNumber(right.node.y, workflowLayoutCenterY)
      if (Math.abs(yDiff) > 1) return yDiff
      return left.index - right.index
    })
    const laneCounts = sortedItems.reduce((map, item) => {
      const lane = laneMap.get(item.id) || 0
      map.set(lane, (map.get(lane) || 0) + 1)
      return map
    }, new Map<number, number>())
    const laneOrders = new Map<number, number>()
    sortedItems.forEach((item) => {
      const lane = laneMap.get(item.id) || 0
      const laneOrder = laneOrders.get(lane) || 0
      laneOrders.set(lane, laneOrder + 1)
      const stackedOffset = ((laneOrder - ((laneCounts.get(lane) || 1) - 1) / 2) * workflowLaneStackGap)
      layoutPositionMap.set(item.id, {
        x: workflowLayoutXStart + layer * workflowLayoutXGap,
        y: workflowLayoutCenterY + lane * workflowLayoutYGap + stackedOffset
      })
    })
  })

  return {
    nodes: graph.nodes.map((node, index) => {
      const id = nodeIds[index]
      const position = layoutPositionMap.get(id)
      const text = graphNodeText(node, id)
      return position ? { ...node, x: position.x, y: position.y, text } : { ...node, text }
    }),
    edges: graph.edges.map((edge) => optimizedWorkflowEdge(edge, layoutPositionMap, nodeMap))
  }
}

function workflowLayoutLaneMap(
  nodeIds: string[],
  nodeMap: Map<string, Record<string, unknown>>,
  orderedLayers: Array<[number, Array<{ node: Record<string, unknown>; id: string; index: number }>]>,
  incomingMap: Map<string, string[]>,
  outgoingMap: Map<string, string[]>
) {
  const laneMap = new Map<string, number>()
  const nodeOriginalY = (id: string) => graphNodeNumber(nodeMap.get(id)?.y, workflowLayoutCenterY)
  const fixedCenterTypes = new Set(['START', 'SUBMIT', 'CONDITION', 'CC', 'END'])

  nodeIds.forEach((id) => {
    if (fixedCenterTypes.has(inferNodeType(nodeMap.get(id) || {}))) {
      laneMap.set(id, 0)
    }
  })

  nodeIds.forEach((id) => {
    if (inferNodeType(nodeMap.get(id) || {}) !== 'CONDITION') return
    const targets = uniqueIds(outgoingMap.get(id) || [])
    if (targets.length <= 1) return
    const lanes = branchLaneIndexes(targets.length)
    targets
      .sort((left, right) => {
        const yDiff = nodeOriginalY(left) - nodeOriginalY(right)
        return Math.abs(yDiff) > 1 ? yDiff : left.localeCompare(right)
      })
      .forEach((targetId, index) => {
        if (fixedCenterTypes.has(inferNodeType(nodeMap.get(targetId) || {}))) return
        laneMap.set(targetId, lanes[index] || 0)
      })
  })

  orderedLayers.forEach(([, items]) => {
    items.forEach((item) => {
      if (!laneMap.has(item.id)) {
        const inheritedLane = medianLane((incomingMap.get(item.id) || []).map((sourceId) => laneMap.get(sourceId)))
        laneMap.set(item.id, inheritedLane)
      }
      const outgoingIds = uniqueIds(outgoingMap.get(item.id) || [])
      if (outgoingIds.length !== 1) return
      const targetId = outgoingIds[0]
      const targetType = inferNodeType(nodeMap.get(targetId) || {})
      if (laneMap.has(targetId) || fixedCenterTypes.has(targetType)) return
      laneMap.set(targetId, laneMap.get(item.id) || 0)
    })
  })

  return laneMap
}

function normalizeWorkflowLayoutLayers(
  nodeIds: string[],
  nodeMap: Map<string, Record<string, unknown>>,
  edges: Array<Record<string, unknown>>,
  incomingCount: Map<string, number>,
  layerMap: Map<string, number>
) {
  const maxPasses = Math.max(1, nodeIds.length)
  for (let pass = 0; pass < maxPasses; pass += 1) {
    let changed = false
    edges.forEach((edge) => {
      const source = graphEdgeEndpoint(edge, 'source')
      const target = graphEdgeEndpoint(edge, 'target')
      if (!source || !target || !nodeMap.has(source) || !nodeMap.has(target)) return
      const targetType = inferNodeType(nodeMap.get(target) || {})
      const needsMergeCorridor = ['CC', 'END'].includes(targetType) && (incomingCount.get(target) || 0) > 1
      const requiredLayer = (layerMap.get(source) || 0) + (needsMergeCorridor ? workflowMergeLayerSpan : 1)
      if ((layerMap.get(target) || 0) >= requiredLayer) return
      layerMap.set(target, requiredLayer)
      changed = true
    })
    if (!changed) break
  }
}

function uniqueIds(ids: string[]) {
  return Array.from(new Set(ids.filter(Boolean)))
}

function branchLaneIndexes(count: number) {
  if (count <= 1) return [0]
  if (count === 2) return [-1, 1]
  const middle = (count - 1) / 2
  return Array.from({ length: count }, (_, index) => index - middle)
}

function medianLane(values: Array<number | undefined>) {
  const lanes = values.filter((value): value is number => typeof value === 'number' && Number.isFinite(value)).sort((left, right) => left - right)
  if (!lanes.length) return 0
  return lanes[Math.floor(lanes.length / 2)]
}

function optimizedWorkflowEdge(
  edge: Record<string, unknown>,
  layoutPositionMap: Map<string, { x: number; y: number }>,
  nodeMap: Map<string, Record<string, unknown>>
) {
  const { pointsList, startPoint, endPoint, ...nextEdge } = edge
  void pointsList
  void startPoint
  void endPoint
  const edgePoints = optimizedWorkflowEdgePoints(edge, layoutPositionMap, nodeMap)
  const sourceType = inferNodeType(nodeMap.get(graphEdgeEndpoint(edge, 'source')) || {})
  const properties = {
    ...edgePropertiesWithoutColor(graphProperties(nextEdge)),
    labelPlacement: sourceType === 'CONDITION' ? 'START' : 'AUTO',
    labelOffsetX: 0,
    labelOffsetY: 0
  }
  const routedEdge = {
    ...nextEdge,
    properties
  }
  return {
    ...routedEdge,
    type: workflowEditorEdgeType,
    startPoint: edgePoints[0],
    endPoint: edgePoints[edgePoints.length - 1],
    pointsList: edgePoints,
    text: optimizedWorkflowEdgeText(routedEdge, edgePoints, nodeMap)
  }
}

function optimizedWorkflowEdgePoints(
  edge: Record<string, unknown>,
  layoutPositionMap: Map<string, { x: number; y: number }>,
  nodeMap: Map<string, Record<string, unknown>>
) {
  const sourceId = graphEdgeEndpoint(edge, 'source')
  const targetId = graphEdgeEndpoint(edge, 'target')
  const source = layoutPositionMap.get(sourceId)
  const target = layoutPositionMap.get(targetId)
  if (!source || !target) return []
  const sourceSize = workflowNodeHalfSize(inferNodeType(nodeMap.get(sourceId) || {}))
  const targetSize = workflowNodeHalfSize(inferNodeType(nodeMap.get(targetId) || {}))
  const direction = target.x >= source.x ? 1 : -1
  const start = { x: Math.round(source.x + sourceSize.width * direction), y: Math.round(source.y) }
  const end = { x: Math.round(target.x - targetSize.width * direction), y: Math.round(target.y) }
  if (Math.abs(start.y - end.y) < 4) return [start, end]
  const sourceType = inferNodeType(nodeMap.get(sourceId) || {})
  const targetType = inferNodeType(nodeMap.get(targetId) || {})
  const isConditionBranch = sourceType === 'CONDITION'
  const isMergeIntoUtility = ['CC', 'END'].includes(targetType)
  if (isConditionBranch) {
    const horizontalDistance = Math.abs(end.x - start.x)
    const bendDistance = Math.max(42, Math.min(workflowConditionSplitOffset, Math.round(horizontalDistance * 0.34)))
    const bendX = Math.round(start.x + bendDistance * direction)
    return [start, { x: bendX, y: start.y }, { x: bendX, y: end.y }, end]
  }
  if (isMergeIntoUtility) {
    const bendX = Math.round(end.x - workflowMergeJoinOffset * direction)
    return [start, { x: bendX, y: start.y }, { x: bendX, y: end.y }, end]
  }
  const midX = Math.round(start.x + (end.x - start.x) / 2)
  return [start, { x: midX, y: start.y }, { x: midX, y: end.y }, end]
}

function workflowNodeHalfSize(nodeType: string) {
  if (nodeType === 'START' || nodeType === 'END') return { width: 34, height: 34 }
  if (nodeType === 'CONDITION') return { width: 52, height: 52 }
  return { width: 84, height: 32 }
}

function optimizedWorkflowEdgeText(edge: Record<string, unknown>, points: Array<{ x: number; y: number }>, nodeMap: Map<string, Record<string, unknown>>) {
  const sourceType = inferNodeType(nodeMap.get(graphEdgeEndpoint(edge, 'source')) || {})
  const targetType = inferNodeType(nodeMap.get(graphEdgeEndpoint(edge, 'target')) || {})
  const fallbackPlacement: EdgeLabelPlacement = sourceType === 'CONDITION' ? 'START' : 'AUTO'
  const placement = graphProperties(edge).labelPlacement ? normalizeEdgeLabelPlacement(graphProperties(edge).labelPlacement) : fallbackPlacement
  if (placement !== 'CUSTOM' && sourceType === 'CONDITION' && (placement === 'START' || placement === 'AUTO')) {
    return conditionBranchWorkflowEdgeText(edge, points)
  }
  if (placement !== 'CUSTOM' && ['CC', 'END'].includes(targetType) && points.length >= 4) {
    return mergeWorkflowEdgeText(edge, points)
  }
  return normalizedWorkflowEdgeText(edge, points, fallbackPlacement)
}

function conditionBranchWorkflowEdgeText(edge: Record<string, unknown>, points: Array<{ x: number; y: number }>) {
  const label = graphNodeText(edge, '')
  if (!label || points.length < 4) return normalizedWorkflowEdgeText(edge, points, 'START')
  const anchor = conditionBranchLabelAnchor(points)
  if (!anchor) return normalizedWorkflowEdgeText(edge, points, 'START')
  const offset = edgeLabelBaseOffset(points, 'START')
  return draggableWorkflowEdgeText(
    label,
    Math.round(anchor.x + offset.x),
    Math.round(anchor.y + offset.y)
  )
}

function mergeWorkflowEdgeText(edge: Record<string, unknown>, points: Array<{ x: number; y: number }>) {
  const label = graphNodeText(edge, '')
  if (!label || points.length < 4) return normalizedWorkflowEdgeText(edge, points)
  const start = points[0]
  const joinPoint = points[1]
  const direction = joinPoint.x >= start.x ? 1 : -1
  const labelDistance = Math.min(96, Math.max(56, Math.abs(joinPoint.x - start.x) * 0.28))
  const verticalDirection = Math.sign(points[2].y - start.y)
  return draggableWorkflowEdgeText(
    label,
    Math.round(start.x + labelDistance * direction),
    Math.round(start.y + (verticalDirection > 0 ? -18 : 22))
  )
}

export function nodeVisualProperties(nodeType: string) {
  const baseText = { fontSize: 13, fontWeight: 700 }
  const visualMap: Record<string, Record<string, unknown>> = {
    START: {
      r: 32,
      style: { fill: '#f8fbff', stroke: '#2563eb', strokeWidth: 2 },
      textStyle: { ...baseText, fill: '#1e40af' }
    },
    SUBMIT: {
      width: 168,
      height: 64,
      radius: 8,
      style: { fill: '#ffffff', stroke: '#94a3b8', strokeWidth: 1.8 },
      textStyle: { ...baseText, fill: '#334155' }
    },
    APPROVAL: {
      width: 168,
      height: 64,
      radius: 8,
      style: { fill: '#f8fbff', stroke: '#2563eb', strokeWidth: 2 },
      textStyle: { ...baseText, fill: '#1e3a8a' }
    },
    CC: {
      width: 168,
      height: 64,
      radius: 8,
      style: { fill: '#fbfefc', stroke: '#16a34a', strokeWidth: 2, strokeDasharray: '5 4' },
      textStyle: { ...baseText, fill: '#166534' }
    },
    CONDITION: {
      rx: 52,
      ry: 52,
      style: { fill: '#fffbeb', stroke: '#d97706', strokeWidth: 2 },
      textStyle: { ...baseText, fill: '#9a3412' }
    },
    END: {
      r: 32,
      style: { fill: '#f8fafc', stroke: '#475569', strokeWidth: 2 },
      textStyle: { ...baseText, fill: '#334155' }
    }
  }
  return visualMap[nodeType] || {}
}

export function workflowLogicType(nodeType: string): WorkflowLogicNodeType {
  if (nodeType === 'START' || nodeType === 'END') return 'workflow-circle'
  if (nodeType === 'CONDITION') return 'workflow-diamond'
  return 'workflow-rect'
}

export function edgeVisualProperties() {
  return {
    style: {
      stroke: edgeDefaultColor,
      strokeWidth: 1.8,
      strokeLinecap: 'round',
      strokeLinejoin: 'round'
    },
    textStyle: {
      fill: edgeDefaultColor,
      fontSize: 12,
      fontWeight: 700,
      background: {
        fill: '#ffffff',
        stroke: '#dbe7f5',
        strokeWidth: 1,
        radius: 4,
        wrapPadding: '3px 6px'
      }
    }
  }
}

export function normalizeEdgeLabelPlacement(value: unknown): EdgeLabelPlacement {
  if (value === 'ABOVE' || value === 'BELOW' || value === 'START' || value === 'END' || value === 'CUSTOM') {
    return value
  }
  return 'AUTO'
}

function normalizedWorkflowEdgeText(
  edge: Record<string, unknown>,
  points: Array<{ x: number; y: number }>,
  fallbackPlacement: EdgeLabelPlacement = 'AUTO'
) {
  const label = graphNodeText(edge, '')
  if (!label) return ''
  return positionedWorkflowEdgeText(edge, points, fallbackPlacement)
}

export function positionedWorkflowEdgeText(
  edge: Record<string, unknown>,
  points: Array<{ x: number; y: number }>,
  fallbackPlacement: EdgeLabelPlacement = 'AUTO'
) {
  const label = graphNodeText(edge, '')
  if (!label) return ''
  if (points.length < 2) return draggableWorkflowEdgeText(label)
  const properties = graphProperties(edge)
  const placement = properties.labelPlacement ? normalizeEdgeLabelPlacement(properties.labelPlacement) : fallbackPlacement
  const basePlacement = placement === 'CUSTOM' ? fallbackPlacement : placement
  const base = edgeLabelBasePoint(points, basePlacement)
  const { x: baseOffsetX, y: baseOffsetY } = edgeLabelBaseOffset(points, basePlacement)
  return draggableWorkflowEdgeText(
    label,
    Math.round(base.x + baseOffsetX + numberProperty(properties.labelOffsetX, 0)),
    Math.round(base.y + baseOffsetY + numberProperty(properties.labelOffsetY, 0))
  )
}

function draggableWorkflowEdgeText(label: string, x?: number, y?: number) {
  const text = {
    value: label,
    draggable: true,
    editable: false
  }
  return typeof x === 'number' && typeof y === 'number' ? { ...text, x, y } : text
}

export function edgeLabelLayoutFromDraggedText(
  edge: Record<string, unknown>,
  points: Array<{ x: number; y: number }>,
  fallbackPlacement: EdgeLabelPlacement = 'AUTO'
) {
  const textPoint = pointProperty(edge.text)
  if (!textPoint || points.length < 2 || !graphNodeText(edge, '')) return undefined
  const properties = graphProperties(edge)
  const placement = properties.labelPlacement ? normalizeEdgeLabelPlacement(properties.labelPlacement) : fallbackPlacement
  const basePlacement = placement === 'CUSTOM' ? fallbackPlacement : placement
  const baselineEdge = {
    ...edge,
    properties: {
      ...properties,
      labelPlacement: basePlacement,
      labelOffsetX: 0,
      labelOffsetY: 0
    }
  }
  const baselineText = positionedWorkflowEdgeText(baselineEdge, points, fallbackPlacement)
  const currentText = positionedWorkflowEdgeText(edge, points, fallbackPlacement)
  if (!isPositionedWorkflowEdgeText(baselineText) || !isPositionedWorkflowEdgeText(currentText)) return undefined
  if (Math.abs(textPoint.x - currentText.x) <= 1 && Math.abs(textPoint.y - currentText.y) <= 1) return undefined
  return {
    labelPlacement: 'CUSTOM' as const,
    labelOffsetX: Math.round(textPoint.x - baselineText.x),
    labelOffsetY: Math.round(textPoint.y - baselineText.y)
  }
}

function isPositionedWorkflowEdgeText(value: unknown): value is { value: string; x: number; y: number } {
  if (!value || typeof value !== 'object') return false
  const text = value as { value?: unknown; x?: unknown; y?: unknown }
  return typeof text.value === 'string' && typeof text.x === 'number' && typeof text.y === 'number'
}

function edgeLabelBasePoint(points: Array<{ x: number; y: number }>, placement: EdgeLabelPlacement) {
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

function edgeLabelBaseOffset(points: Array<{ x: number; y: number }>, placement: EdgeLabelPlacement) {
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

function conditionBranchLabelAnchor(points: Array<{ x: number; y: number }>) {
  if (!orthogonalPolyline(points)) return undefined
  const start = points[0]
  const branchPoint = points[2]
  const end = points[points.length - 1]
  const horizontalProgress = 0.22
  return {
    x: Math.round(branchPoint.x + (end.x - branchPoint.x) * horizontalProgress),
    y: Math.round(branchPoint.y),
    isUpperBranch: end.y < start.y
  }
}

function firstSegmentLabelAnchor(points: Array<{ x: number; y: number }>) {
  if (!orthogonalPolyline(points)) return undefined
  const start = points[0]
  const bend = points[1]
  return {
    x: Math.round(start.x + (bend.x - start.x) * 0.58),
    y: Math.round(start.y)
  }
}

function orthogonalPolyline(points: Array<{ x: number; y: number }>) {
  if (points.length < 4) return false
  const start = points[0]
  const firstBend = points[1]
  const secondBend = points[2]
  const end = points[points.length - 1]
  return Math.abs(start.y - firstBend.y) < 4
    && Math.abs(firstBend.x - secondBend.x) < 4
    && Math.abs(secondBend.y - end.y) < 4
}

function pointOnPolyline(points: Array<{ x: number; y: number }>, ratio: number) {
  const segments = points.slice(1).map((point, index) => {
    const previous = points[index]
    return {
      start: previous,
      end: point,
      length: Math.hypot(point.x - previous.x, point.y - previous.y)
    }
  })
  const totalLength = segments.reduce((sum, segment) => sum + segment.length, 0)
  if (totalLength <= 0) return points[0]
  let distance = totalLength * ratio
  for (const segment of segments) {
    if (distance <= segment.length) {
      const segmentRatio = segment.length === 0 ? 0 : distance / segment.length
      return {
        x: segment.start.x + (segment.end.x - segment.start.x) * segmentRatio,
        y: segment.start.y + (segment.end.y - segment.start.y) * segmentRatio
      }
    }
    distance -= segment.length
  }
  return points[points.length - 1]
}

export function edgeRouteFromEdge(edge: Record<string, unknown>): WorkflowEdgeRoute | undefined {
  const pointsList = cloneWorkflowPoints(edgePointsFromEdge(edge))
  if (!pointsList.length) return undefined
  const startPoint = cloneWorkflowPoint(pointProperty(edge.startPoint) || pointsList[0])
  const endPoint = cloneWorkflowPoint(pointProperty(edge.endPoint) || pointsList[pointsList.length - 1])
  return { startPoint, endPoint, pointsList }
}

export function cloneWorkflowPoints(points: WorkflowRoutePoint[]) {
  return points.map((point) => ({ x: point.x, y: point.y }))
}

export function cloneWorkflowPoint(point?: WorkflowRoutePoint) {
  return point ? { x: point.x, y: point.y } : undefined
}

export function edgePointsFromEdge(edge: Record<string, unknown>) {
  const points = edge.pointsList
  if (Array.isArray(points)) {
    return points
      .map((point) => {
        if (!point || typeof point !== 'object') return undefined
        const item = point as { x?: unknown; y?: unknown }
        if (typeof item.x !== 'number' || typeof item.y !== 'number') return undefined
        return { x: item.x, y: item.y }
      })
      .filter((point): point is { x: number; y: number } => Boolean(point))
  }
  const start = pointProperty(edge.startPoint)
  const end = pointProperty(edge.endPoint)
  return start && end ? [start, end] : []
}

export function pointProperty(value: unknown) {
  if (!value || typeof value !== 'object') return undefined
  const point = value as { x?: unknown; y?: unknown }
  return typeof point.x === 'number' && typeof point.y === 'number' ? { x: point.x, y: point.y } : undefined
}

export function inferNodeType(node: Record<string, unknown>): WorkflowNodeType {
  const properties = graphProperties(node)
  const configuredType = stringProperty(properties.nodeType)
  if (configuredType && ['START', 'SUBMIT', 'APPROVAL', 'CC', 'CONDITION', 'END'].includes(configuredType)) {
    return configuredType as WorkflowNodeType
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

export function graphProperties(data: Record<string, unknown>) {
  const properties = data.properties
  return properties && typeof properties === 'object' ? (properties as Record<string, unknown>) : {}
}

export function recordProperty(value: unknown) {
  return value && typeof value === 'object' ? (value as Record<string, unknown>) : {}
}

export function edgePropertiesWithoutColor(properties: Record<string, unknown>) {
  const { lineColor, ...rest } = properties
  void lineColor
  return rest
}

export function stringProperty(value: unknown) {
  return typeof value === 'string' ? value : ''
}

export function graphNodeId(node: Record<string, unknown>) {
  const id = node.id
  return typeof id === 'string' || typeof id === 'number' ? String(id) : ''
}

export function graphEdgeEndpoint(edge: Record<string, unknown>, direction: 'source' | 'target') {
  const id = direction === 'source'
    ? edge.sourceNodeId
    : edge.targetNodeId
  return typeof id === 'string' || typeof id === 'number' ? String(id) : ''
}

export function graphNodeText(node: Record<string, unknown>, fallback = '-') {
  const text = node.text
  if (typeof text === 'string') return text
  if (text && typeof text === 'object') {
    const value = (text as { value?: unknown; text?: unknown }).value || (text as { value?: unknown; text?: unknown }).text
    if (typeof value === 'string' && value.trim()) return value
  }
  return fallback
}

export function graphNodeNumber(value: unknown, fallback: number) {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

export function numberProperty(value: unknown, fallback: number) {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim() && Number.isFinite(Number(value))) return Number(value)
  return fallback
}

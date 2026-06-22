export interface WorkflowDynamicDisplayOrderItem {
  sequenceTime: number
  sequenceRank?: number
}

export function sortWorkflowDynamicsForDisplay<T extends WorkflowDynamicDisplayOrderItem>(items: T[]): T[] {
  return items
    .map((item, index) => ({ item, index }))
    .sort((left, right) => {
      const timeDelta = left.item.sequenceTime - right.item.sequenceTime
      if (timeDelta !== 0) return timeDelta
      const rankDelta = (left.item.sequenceRank ?? 0) - (right.item.sequenceRank ?? 0)
      if (rankDelta !== 0) return rankDelta
      return left.index - right.index
    })
    .map(({ item }) => item)
}

export interface TableColumnOption {
  key: string
  label: string
  visible?: boolean
  required?: boolean
}
export type TableColumnVisibility = Record<string, boolean>

export function createTableColumnState(columns: TableColumnOption[], saved?: TableColumnVisibility): TableColumnOption[] {
  return columns.map((column) => ({
    ...column,
    visible: column.required ? true : saved?.[column.key] ?? column.visible ?? true
  }))
}

export function toggleTableColumn(columns: TableColumnOption[], key: string, visible: boolean): TableColumnOption[] {
  return columns.map((column) => {
    if (column.key !== key || column.required) return column
    return { ...column, visible }
  })
}

export function resetTableColumns(columns: TableColumnOption[]): TableColumnOption[] {
  return columns.map((column) => ({ ...column, visible: true }))
}

export function visibleColumnMap(columns: TableColumnOption[]): TableColumnVisibility {
  return columns.reduce<TableColumnVisibility>((map, column) => {
    map[column.key] = column.visible !== false
    return map
  }, {})
}

export function serializeColumnState(columns: TableColumnOption[]): TableColumnVisibility {
  return columns.reduce<TableColumnVisibility>((map, column) => {
    if (!column.required) {
      map[column.key] = column.visible !== false
    }
    return map
  }, {})
}

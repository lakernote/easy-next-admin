import { describe, expect, it } from 'vitest'
import {
  createTableColumnState,
  resetTableColumns,
  serializeColumnState,
  toggleTableColumn,
  visibleColumnMap
} from './tableColumns'

describe('table column state', () => {
  it('applies saved visibility while keeping required columns visible', () => {
    const columns = createTableColumnState(
      [
        { key: 'name', label: '名称', required: true },
        { key: 'owner', label: '负责人' },
        { key: 'status', label: '状态', visible: false }
      ],
      { name: false, owner: false }
    )

    expect(visibleColumnMap(columns)).toEqual({ name: true, owner: false, status: false })
  })

  it('toggles and serializes optional columns', () => {
    const columns = createTableColumnState([
      { key: 'name', label: '名称', required: true },
      { key: 'createdAt', label: '创建时间' }
    ])

    const next = toggleTableColumn(columns, 'createdAt', false)

    expect(visibleColumnMap(next).createdAt).toBe(false)
    expect(serializeColumnState(next)).toEqual({ createdAt: false })
    expect(visibleColumnMap(resetTableColumns(next)).createdAt).toBe(true)
  })
})

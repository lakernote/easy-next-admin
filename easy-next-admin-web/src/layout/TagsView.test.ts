import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { describe, expect, it } from 'vitest'

const tagsViewSource = readFileSync(fileURLToPath(new URL('./TagsView.vue', import.meta.url)), 'utf-8')
const themeSource = readFileSync(fileURLToPath(new URL('../styles/index.css', import.meta.url)), 'utf-8')

describe('TagsView overflow interaction', () => {
  it('provides visible left and right scroll controls for many opened tabs', () => {
    expect(tagsViewSource).toContain('tagsScrollRef')
    expect(tagsViewSource).toContain("scrollTags('left')")
    expect(tagsViewSource).toContain("scrollTags('right')")
    expect(tagsViewSource).toContain('syncScrollState')
    expect(themeSource).toContain('.tags-nav')
    expect(themeSource).toContain('.tags-nav:disabled')
  })

  it('hides scroll controls until opened tabs overflow horizontally', () => {
    expect(tagsViewSource).toContain('hasHorizontalOverflow')
    expect(tagsViewSource).toContain('v-if="hasHorizontalOverflow"')
  })

  it('exposes refresh current page as a visible tag bar action', () => {
    expect(tagsViewSource).toContain('class="tags-refresh"')
    expect(tagsViewSource).toContain('aria-label="刷新当前页面"')
    expect(tagsViewSource).toContain('refreshCurrentTag')
    expect(tagsViewSource).not.toContain('<el-dropdown-item command="refresh">刷新当前</el-dropdown-item>')
  })

  it('captures active state before closing a tab from its close icon', () => {
    expect(tagsViewSource).toContain('const wasActive = tag.path === tagsView.activePath')
    expect(tagsViewSource).toContain('if (wasActive)')
  })

  it('does not nest a close control inside the page tab button', () => {
    expect(tagsViewSource).toContain('class="tag-link"')
    expect(tagsViewSource).toContain('class="tag-close"')
    expect(tagsViewSource).not.toContain('role="button"')
  })
})

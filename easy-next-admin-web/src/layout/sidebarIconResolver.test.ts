import * as ElementPlusIcons from '@element-plus/icons-vue'
import { describe, expect, it } from 'vitest'
import { resolveSidebarIcon } from './sidebarIconResolver'

const icons = ElementPlusIcons as unknown as Record<string, unknown>

describe('resolveSidebarIcon', () => {
  it('uses the full Element Plus icon set selected from menu configuration', () => {
    expect(resolveSidebarIcon('Tools')).toBe(icons.Tools)
    expect(resolveSidebarIcon('Tickets')).toBe(icons.Tickets)
  })

  it('falls back to Menu for blank or unknown icon names', () => {
    expect(resolveSidebarIcon()).toBe(icons.Menu)
    expect(resolveSidebarIcon('DoesNotExist')).toBe(icons.Menu)
  })
})

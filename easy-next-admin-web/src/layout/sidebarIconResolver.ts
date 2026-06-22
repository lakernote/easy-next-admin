import * as ElementPlusIcons from '@element-plus/icons-vue'
import type { Component } from 'vue'

const elementPlusIcons = ElementPlusIcons as unknown as Record<string, Component>

export function resolveSidebarIcon(name?: string): Component {
  return elementPlusIcons[name || 'Menu'] || elementPlusIcons.Menu
}

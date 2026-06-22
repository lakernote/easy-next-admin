import { readdirSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const css = readFileSync(resolve(__dirname, 'index.css'), 'utf-8')
const srcRoot = resolve(__dirname, '..')
const loginView = readFileSync(resolve(srcRoot, 'views/login/LoginView.vue'), 'utf-8')
const menuView = readFileSync(resolve(srcRoot, 'views/system/MenuView.vue'), 'utf-8')
const roleView = readFileSync(resolve(srcRoot, 'views/system/RoleView.vue'), 'utf-8')
const appCssWithoutLegacyLogin = css.replace(/\.login-page \{[\s\S]*?(?=@media \(max-width: 720px\) \{)/, '')

function collectVisualFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = resolve(dir, entry.name)
    if (entry.isDirectory()) return collectVisualFiles(path)
    if (!/\.(vue|css|scss)$/.test(entry.name)) return []
    return [path]
  })
}

describe('modern flat theme', () => {
  it('defines flat enterprise tokens and removes previous tech decoration tokens', () => {
    expect(css).toContain('--ea-radius-sm:')
    expect(css).toContain('--ea-radius-md:')
    expect(css).toContain('--ea-flat-hover:')
    expect(css).toContain('--ea-header-bg:')
    expect(css).toContain('--ea-focus-ring:')
    expect(css).toContain('--ea-code-font:')
    expect(css).not.toContain('--ea-sidebar-rail:')
    expect(css).not.toContain('--ea-tech-line:')
  })

  it('keeps the visual language flat across shared surfaces', () => {
    expect(appCssWithoutLegacyLogin).not.toContain('linear-gradient')
    expect(appCssWithoutLegacyLogin).not.toContain('box-shadow:')
    expect(appCssWithoutLegacyLogin).not.toContain('backdrop-filter')
    expect(appCssWithoutLegacyLogin).not.toContain('var(--ea-shadow-panel)')
    expect(css).toContain('background: var(--ea-bg);')
    expect(css).toContain('background: var(--ea-panel);')
    expect(css).toContain('background: var(--ea-sidebar);')
    expect(css).toContain('@media (prefers-reduced-motion: reduce)')
  })

  it('keeps first-party page styles free of heavy decorative effects', () => {
    const decorativeEffectPattern = /linear-gradient|radial-gradient|box-shadow:|backdrop-filter|filter:\s*blur|drop-shadow/
    const offenders = collectVisualFiles(srcRoot)
      .filter((file) => {
        const content = readFileSync(file, 'utf-8')
        const normalized = file.endsWith('styles/index.css') ? appCssWithoutLegacyLogin : content
        return decorativeEffectPattern.test(normalized)
      })
      .map((file) => file.replace(`${srcRoot}/`, ''))

    expect(offenders).toEqual([])
  })

  it('keeps the login screen on the focused enterprise gateway layout', () => {
    expect(loginView).toContain('login-hero-copy')
    expect(loginView).toContain('login-background')
    expect(loginView).toContain('欢迎登录 EasyNextAdmin')
    expect(loginView).toContain('login-plane')
    expect(loginView).not.toContain('login-command-panel')
    expect(loginView).not.toContain('loginStats')
    expect(loginView).not.toContain('控制台能力预览')
    expect(loginView).not.toContain('login-shell')
    expect(loginView).not.toContain('login-aside-copy')
    expect(loginView).not.toContain('<strong>17</strong>')
    expect(css).toContain('grid-template-columns: minmax(500px, 590px) minmax(460px, 540px)')
    expect(css).toContain('background: #0f4aa2;')
    expect(css).toContain('background: rgba(255, 255, 255, 0.97);')
  })

  it('keeps tablet login and mobile shell from horizontal overflow', () => {
    expect(css).toContain('@media (max-width: 820px)')
    expect(css).toContain('grid-template-columns: minmax(0, 1fr)')
    expect(css).toContain('.mobile-menu-drawer')
    expect(css).toContain('.app-sidebar {')
    expect(css).toContain('display: none;')
  })

  it('keeps role authorization menu rows focused on business menu names', () => {
    expect(css).toContain('grid-template-columns: 22px 18px minmax(0, 1fr)')
    expect(css).not.toContain('.permission-menu-resource')
    expect(css).toContain('min-height: 46px;')
    expect(roleView).not.toContain('node.menu?.resource')
    expect(roleView).not.toContain('node.menu?.description')
    expect(roleView).not.toContain('后续可配置')
    expect(menuView).toContain('按钮权限')
  })
})

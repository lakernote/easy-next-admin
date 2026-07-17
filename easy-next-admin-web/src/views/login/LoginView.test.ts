import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(resolve(__dirname, 'LoginView.vue'), 'utf-8')
const authApiSource = readFileSync(resolve(__dirname, '../../features/auth/api.ts'), 'utf-8')
const authTypesSource = readFileSync(resolve(__dirname, '../../features/auth/types.ts'), 'utf-8')
const legacyAuthApiPath = resolve(__dirname, '../../api/auth.ts')

describe('login view enterprise scaffold surface', () => {
  it('keeps demo account shortcuts for public experience login', () => {
    expect(viewSource).toContain('login-demo-accounts')
    expect(viewSource).toContain('测试账号')
    expect(viewSource).toContain('listDemoAccounts')
    expect(viewSource).toContain('function useDemoAccount(account: DemoAccount)')
    expect(viewSource).toContain('fillDemoPasswordFromCurrentUser')
    expect(viewSource).toContain('form.password = account.password')
    expect(viewSource).toContain('记住我')
    expect(viewSource).toContain('LOGIN_PREFERENCE_KEY')
    expect(viewSource).not.toContain('login-command-panel')
    expect(viewSource).not.toContain('控制台能力预览')
    expect(viewSource).toContain('Copyright © 2026 EasyNextAdmin')
    expect(existsSync(legacyAuthApiPath)).toBe(false)
    expect(authTypesSource).toContain('export interface DemoAccount')
    expect(authApiSource).toContain("'/auth/demo-accounts'")
  })
})

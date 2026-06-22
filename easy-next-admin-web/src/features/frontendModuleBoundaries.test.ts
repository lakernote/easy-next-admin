import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const srcRoot = resolve(__dirname, '..')

function source(path: string) {
  return readFileSync(resolve(srcRoot, path), 'utf-8')
}

describe('frontend module boundaries', () => {
  it('keeps large domain APIs split into focused files', () => {
    const expectedFiles = [
      'features/system/userApi.ts',
      'features/system/roleApi.ts',
      'features/system/menuApi.ts',
      'features/system/departmentApi.ts',
      'features/system/userOptionsApi.ts',
      'features/workflow/definitionApi.ts',
      'features/workflow/taskApi.ts',
      'features/workflow/instanceApi.ts',
      'features/workflow/applicationApi.ts',
      'features/workflow/repairApi.ts',
      'features/workflow/graphSource.ts'
    ]

    expect(expectedFiles.filter((file) => !existsSync(resolve(srcRoot, file)))).toEqual([])
  })

  it('does not keep compatibility barrels in the new scaffold', () => {
    const removedFiles = [
      'features/system/api.ts',
      'features/workflow/api.ts',
      'features/workflow/source.ts',
      'views/file/FileCenterView.vue'
    ]

    expect(removedFiles.filter((file) => existsSync(resolve(srcRoot, file)))).toEqual([])
  })

  it('uses business-action auth names instead of generic Api suffixes', () => {
    const authApi = source('features/auth/api.ts')
    const loginView = source('views/login/LoginView.vue')

    expect(authApi).toContain('export function login')
    expect(authApi).toContain('export function getCaptcha')
    expect(authApi).toContain('export function listDemoAccounts')
    expect(loginView).toContain('listDemoAccounts')
    expect(loginView).not.toContain('demoAccountsApi')
    expect(loginView).not.toContain('captchaApi')
  })

  it('keeps file center under the system module boundary', () => {
    const migration = readFileSync(resolve(srcRoot, '../../easy-next-admin-server/src/main/resources/db/migration/V1__init.sql'), 'utf-8')

    expect(existsSync(resolve(srcRoot, 'views/system/FileCenterView.vue'))).toBe(true)
    expect(migration).toContain('@/views/system/FileCenterView.vue')
  })

  it('documents frontend scaffold files for learners', () => {
    const readme = readFileSync(resolve(srcRoot, '../README.md'), 'utf-8')
    const assetsReadme = readFileSync(resolve(srcRoot, 'assets/README.md'), 'utf-8')

    expect(assetsReadme).toContain('src/assets')
    expect(assetsReadme).toContain('public')
    expect(readme).toContain('## 工程文件说明')
    expect(readme).toContain('package-lock.json')
    expect(readme).toContain('tsconfig.node.json')
    expect(readme).toContain('vite.config.ts')
  })
})

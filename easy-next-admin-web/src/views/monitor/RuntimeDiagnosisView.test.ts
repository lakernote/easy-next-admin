import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(fileURLToPath(new URL('./RuntimeDiagnosisView.vue', import.meta.url)), 'utf-8')

describe('runtime diagnosis embedding', () => {
  it('keeps the standalone dashboard in an iframe and forwards auth only to that same-origin frame', () => {
    expect(viewSource).toContain('src="/easy-observability/"')
    expect(viewSource).toContain("event.origin !== window.location.origin")
    expect(viewSource).toContain('event.source !== frame.value?.contentWindow')
    expect(viewSource).toContain("event.data?.type !== 'easy-observability:auth-request'")
    expect(viewSource).toContain("type: 'easy-observability:auth-response'")
    expect(viewSource).toContain('token: auth.accessToken')
    expect(viewSource).not.toMatch(/src=.*accessToken/)
  })

  it('removes the message listener when the page is unmounted', () => {
    expect(viewSource).toContain('window.addEventListener(\'message\', handleAuthRequest)')
    expect(viewSource).toContain('onBeforeUnmount(() => window.removeEventListener')
  })
})

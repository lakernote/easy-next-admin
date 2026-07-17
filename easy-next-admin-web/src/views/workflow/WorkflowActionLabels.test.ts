import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { describe, expect, it } from 'vitest'

const workflowActionFiles = ['./WorkflowTaskCenterView.vue']

describe('workflow action labels', () => {
  it('labels full instance drawers as detail instead of only flow graph', () => {
    workflowActionFiles.forEach((file) => {
      const source = readFileSync(fileURLToPath(new URL(file, import.meta.url)), 'utf-8')

      expect(source).not.toContain('流程图')
      expect(source).toContain('详情')
    })
  })
})

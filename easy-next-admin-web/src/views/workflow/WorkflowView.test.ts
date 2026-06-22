import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(
  fileURLToPath(new URL('./WorkflowView.vue', import.meta.url)),
  'utf-8'
)

describe('workflow definition designer', () => {
  it('keeps edge label editing enabled without exposing edge path mutation', () => {
    expect(viewSource).toContain('adjustEdge: false')
    expect(viewSource).toContain('adjustEdgeMiddle: false')
    expect(viewSource).toContain('adjustEdgeStartAndEnd: false')
    expect(viewSource).toContain('edgeTextDraggable: true')
    expect(viewSource).toContain('hideAnchors: false')
    expect(viewSource).toContain('edgeGenerator: () => workflowEditorEdgeType')
    expect(viewSource).not.toContain('edgeGenerator: (_sourceNode, _targetNode, currentEdge)')
  })
})

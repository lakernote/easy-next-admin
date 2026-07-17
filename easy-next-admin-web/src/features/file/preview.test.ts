import { describe, expect, it } from 'vitest'
import { canPreviewFile, resolveFileCategory, resolveFilePreviewKind } from './preview'

describe('file preview helpers', () => {
  it('previews common image files through the authenticated download payload', () => {
    const file = {
      fileId: 1,
      originalName: 'avatar.PNG',
      contentType: ''
    }

    expect(resolveFileCategory(file)).toBe('image')
    expect(resolveFilePreviewKind(file)).toBe('image')
    expect(canPreviewFile(file)).toBe(true)
  })

  it('supports pdf and text-like file previews', () => {
    expect(resolveFilePreviewKind({ fileId: 2, originalName: '制度.pdf', contentType: 'application/pdf' })).toBe('pdf')
    expect(resolveFilePreviewKind({ fileId: 3, originalName: 'error.log', contentType: 'application/octet-stream' })).toBe('text')
    expect(resolveFilePreviewKind({ fileId: 4, originalName: 'payload', contentType: 'application/json' })).toBe('text')
  })

  it('keeps office and archive files downloadable without pretending they can preview inline', () => {
    expect(resolveFileCategory({ fileId: 5, originalName: '预算.xlsx', contentType: '' })).toBe('office')
    expect(resolveFilePreviewKind({ fileId: 5, originalName: '预算.xlsx', contentType: '' })).toBeUndefined()
    expect(resolveFileCategory({ fileId: 6, originalName: '归档.zip', contentType: '' })).toBe('archive')
    expect(canPreviewFile({ fileId: 6, originalName: '归档.zip', contentType: '' })).toBe(false)
  })
})

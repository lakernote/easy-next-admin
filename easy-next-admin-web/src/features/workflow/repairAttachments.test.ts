import { describe, expect, it } from 'vitest'
import { validateRepairImageFile } from './repairAttachments'

describe('repair attachment validation', () => {
  it('accepts common repair evidence images under 5MB', () => {
    expect(validateRepairImageFile({ name: 'fault.jpg', size: 4 * 1024 * 1024, type: 'image/jpeg' })).toBeUndefined()
    expect(validateRepairImageFile({ name: 'network-panel.webp', size: 800_000, type: 'image/webp' })).toBeUndefined()
  })

  it('rejects non-images and oversized images', () => {
    expect(validateRepairImageFile({ name: 'attachment.pdf', size: 100_000, type: 'application/pdf' })).toBe('仅支持 JPG、PNG、WEBP 图片')
    expect(validateRepairImageFile({ name: 'large.png', size: 6 * 1024 * 1024, type: 'image/png' })).toBe('图片大小不能超过 5MB')
  })
})

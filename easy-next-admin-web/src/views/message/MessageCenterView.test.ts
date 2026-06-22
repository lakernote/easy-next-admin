import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(
  fileURLToPath(new URL('./MessageCenterView.vue', import.meta.url)),
  'utf-8'
)

describe('message center product surface', () => {
  it('does not expose announcements in the message center', () => {
    expect(viewSource).not.toContain('发布公告')
    expect(viewSource).not.toContain('公告记录')
    expect(viewSource).not.toContain('pageAnnouncements')
    expect(viewSource).not.toContain('announcements')
    expect(viewSource).not.toContain('openAnnouncementDrawer')
    expect(viewSource).not.toContain('publishAnnouncement')
  })

  it('does not expose system notifications as a message center category', () => {
    expect(viewSource).not.toContain('系统通知')
    expect(viewSource).not.toContain('value="system"')
    expect(viewSource).not.toContain('unreadCount.value.system')
  })
})

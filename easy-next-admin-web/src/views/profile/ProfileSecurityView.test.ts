import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(resolve(__dirname, 'ProfileSecurityView.vue'), 'utf-8')

describe('profile security view avatar controls', () => {
  it('uses file upload and crop controls instead of an avatar URL field', () => {
    expect(viewSource).toContain('上传头像')
    expect(viewSource).toContain('profile-avatar-upload')
    expect(viewSource).toContain('profile-avatar-overlay')
    expect(viewSource).toContain('裁剪头像')
    expect(viewSource).toContain('type="file"')
    expect(viewSource).toContain('accept="image/jpeg,image/png,image/webp"')
    expect(viewSource).toContain('@pointerdown="startAvatarDrag"')
    expect(viewSource).toContain('uploadProfileAvatar')
    expect(viewSource).toContain('上传并使用')
    expect(viewSource).not.toContain('profile-avatar-actions')
    expect(viewSource).not.toContain('uploadSystemFile')
    expect(viewSource).not.toContain('label="头像地址"')
    expect(viewSource).not.toContain('填写内网图片地址或文件中心图片地址')
  })

  it('uses an enterprise profile layout with explicit form validation', () => {
    expect(viewSource).toContain('profile-sidebar-card')
    expect(viewSource).toContain('profile-workspace-card')
    expect(viewSource).toContain('profile-tabs')
    expect(viewSource).toContain('ref="profileFormRef"')
    expect(viewSource).toContain('profileFormRules')
    expect(viewSource).toContain('ref="passwordFormRef"')
    expect(viewSource).toContain('passwordFormRules')
    expect(viewSource).toContain('validatePasswordStrength')
    expect(viewSource).toContain('prop="nickName"')
    expect(viewSource).toContain('prop="phone"')
  })

  it('keeps history and session management inside tabs without the policy baseline section', () => {
    expect(viewSource).toContain('label="登录历史" name="history"')
    expect(viewSource).toContain('label="在线会话" name="sessions"')
    expect(viewSource).toContain('profile-tab-panel')
    expect(viewSource).not.toContain('profile-policy')
    expect(viewSource).not.toContain('安全策略基线')
    expect(viewSource).not.toContain('security-policy-card')
  })
})

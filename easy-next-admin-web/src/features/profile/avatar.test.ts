import { describe, expect, it } from 'vitest'
import { clampAvatarOffset, moveAvatarCrop, profileInitial, userAvatarInitial, userAvatarSrc } from './avatar'

describe('profile avatar helpers', () => {
  it('uses the first visible character from display name when avatar is missing', () => {
    expect(profileInitial('陈经理')).toBe('陈')
    expect(profileInitial(' manager ')).toBe('M')
    expect(profileInitial('')).toBe('用')
  })

  it('moves crop offsets by drag delta and clamps them to the supported range', () => {
    expect(moveAvatarCrop({ offsetX: 12, offsetY: -8 }, { deltaX: 30, deltaY: 18, limit: 120 })).toEqual({
      offsetX: 42,
      offsetY: 10
    })
    expect(clampAvatarOffset(150, 120)).toBe(120)
    expect(clampAvatarOffset(-150, 120)).toBe(-120)
  })

  it('resolves shared user avatar source and fallback initial', () => {
    expect(userAvatarSrc({ avatar: ' /storage/avatar.png ' })).toBe('/storage/avatar.png')
    expect(userAvatarSrc({ avatar: ' ' })).toBeUndefined()
    expect(userAvatarInitial({ nickName: '陈经理', userName: 'manager' })).toBe('陈')
    expect(userAvatarInitial({ userName: 'admin' })).toBe('A')
  })
})

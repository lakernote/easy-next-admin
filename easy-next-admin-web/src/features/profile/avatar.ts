export interface AvatarCropPosition {
  offsetX: number
  offsetY: number
}

export interface AvatarMoveDelta {
  deltaX: number
  deltaY: number
  limit: number
}

export interface UserAvatarSubject {
  avatar?: string
  nickName?: string
  realName?: string
  userName?: string
  name?: string
}

export function profileInitial(displayName?: string) {
  const value = (displayName || '').trim()
  return value ? value.slice(0, 1).toUpperCase() : '用'
}

export function userAvatarSrc(user?: UserAvatarSubject) {
  const avatar = user?.avatar?.trim()
  return avatar || undefined
}

export function userAvatarInitial(user?: UserAvatarSubject) {
  return profileInitial(user?.nickName || user?.realName || user?.name || user?.userName)
}

export function clampAvatarOffset(value: number, limit: number) {
  return Math.max(-limit, Math.min(limit, value))
}

export function moveAvatarCrop(position: AvatarCropPosition, delta: AvatarMoveDelta): AvatarCropPosition {
  return {
    offsetX: clampAvatarOffset(position.offsetX + delta.deltaX, delta.limit),
    offsetY: clampAvatarOffset(position.offsetY + delta.deltaY, delta.limit)
  }
}

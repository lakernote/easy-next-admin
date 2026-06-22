export type AuthEntityId = string | number

export interface AuthUserProfile {
  userId: AuthEntityId
  userName: string
  nickName: string
  realName?: string
  employeeNo?: string
  positionName?: string
  deptId?: AuthEntityId
  deptName?: string
  phone?: string
  email?: string
  avatar?: string
  lastLoginTime?: string
}

export interface LoginPayload {
  username: string
  password: string
  captchaId?: string
  captchaCode?: string
}

export interface LoginResult {
  accessToken: string
  accessExpiresIn: number
  user?: AuthUserProfile
  permissions?: string[]
  roles?: string[]
  roleNames?: string[]
  menus?: AuthMenu[]
}

export interface CaptchaResult {
  captchaId: string
  imageBase64: string
  expiresIn: number
}

export interface DemoAccount {
  roleName: string
  username: string
  password: string
  description: string
}

export interface AuthMenu {
  id: AuthEntityId
  pid?: AuthEntityId
  title: string
  icon?: string
  href?: string
  sort?: number
  enable?: boolean
  visible?: boolean
  type?: number
  permissionCode?: string
  componentPath?: string
  children?: AuthMenu[]
}

export interface AuthProfile {
  user?: AuthUserProfile
  roles?: string[]
  roleNames?: string[]
  permissions?: string[]
  menus?: AuthMenu[]
}

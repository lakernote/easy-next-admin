import { defineStore } from 'pinia'
import {
  loginApi,
  logoutApi,
  profileApi
} from '@/features/auth/api'
import type {
  AuthEntityId,
  AuthMenu,
  AuthProfile,
  LoginPayload
} from '@/features/auth/types'

interface UserProfile {
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

interface AuthState {
  accessToken: string
  user: UserProfile | null
  roles: string[]
  roleNames: string[]
  permissions: string[]
  menus: AuthMenu[]
  profileLoaded: boolean
  rememberLogin: boolean
}

const STORAGE_KEY = 'easy-next-admin-web-auth'

interface LoginOptions {
  rememberMe?: boolean
}

function defaultState(): AuthState {
  return {
    accessToken: '',
    user: null,
    roles: [],
    roleNames: [],
    permissions: [],
    menus: [],
    profileLoaded: false,
    rememberLogin: true
  }
}

function loadState(): AuthState {
  const localRaw = localStorage.getItem(STORAGE_KEY)
  const sessionRaw = sessionStorage.getItem(STORAGE_KEY)
  const raw = localRaw || sessionRaw
  if (!raw) {
    return defaultState()
  }
  try {
    const parsed = JSON.parse(raw) as Partial<AuthState>
    return {
      ...defaultState(),
      accessToken: parsed.accessToken || '',
      user: parsed.user || null,
      roles: parsed.roles || [],
      roleNames: parsed.roleNames || [],
      permissions: parsed.permissions || [],
      menus: parsed.menus || [],
      profileLoaded: false,
      rememberLogin: Boolean(localRaw)
    }
  } catch {
    localStorage.removeItem(STORAGE_KEY)
    sessionStorage.removeItem(STORAGE_KEY)
    return defaultState()
  }
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => loadState(),
  getters: {
    isLoggedIn: (state) => Boolean(state.accessToken),
    displayName: (state) => state.user?.nickName || state.user?.userName || '未登录',
    isSuperAdmin: (state) => state.roles.includes('admin')
  },
  actions: {
    persist() {
      const targetStorage = this.rememberLogin ? localStorage : sessionStorage
      const staleStorage = this.rememberLogin ? sessionStorage : localStorage
      staleStorage.removeItem(STORAGE_KEY)
      targetStorage.setItem(STORAGE_KEY, JSON.stringify(this.$state))
    },
    applyProfile(profile: AuthProfile) {
      this.user = profile.user || this.user
      this.roles = profile.roles || this.roles
      this.roleNames = profile.roleNames || this.roleNames
      this.permissions = profile.permissions || this.permissions
      this.menus = profile.menus || this.menus
      this.profileLoaded = true
      this.persist()
    },
    async login(payload: LoginPayload, options: LoginOptions = {}) {
      this.rememberLogin = options.rememberMe ?? true
      const response = await loginApi(payload)
      const data = response.data
      this.accessToken = data.accessToken
      this.user = data.user || { userId: '', userName: payload.username, nickName: payload.username }
      this.roles = data.roles || []
      this.roleNames = data.roleNames || []
      this.permissions = data.permissions || []
      this.menus = data.menus || []
      this.profileLoaded = false
      const profile = await profileApi()
      this.applyProfile(profile.data)
    },
    async loadProfile() {
      if (!this.accessToken) return
      const profile = await profileApi()
      this.applyProfile(profile.data)
    },
    async logoutCurrentSession() {
      try {
        if (this.accessToken) {
          await logoutApi()
        }
      } catch {
        // 本地退出不能被网络波动阻塞；服务端会话仍会按过期时间兜底清理。
      } finally {
        this.logout()
      }
    },
    logout() {
      this.accessToken = ''
      this.user = null
      this.roles = []
      this.roleNames = []
      this.permissions = []
      this.menus = []
      this.profileLoaded = false
      this.rememberLogin = true
      localStorage.removeItem(STORAGE_KEY)
      sessionStorage.removeItem(STORAGE_KEY)
    },
    hasAnyPermission(required?: string[]) {
      if (!required || required.length === 0) return true
      if (this.isSuperAdmin) return true
      return required.some((item) => this.permissions.includes(item))
    }
  }
})

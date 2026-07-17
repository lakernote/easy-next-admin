import type { App, DirectiveBinding } from 'vue'
import { useAuthStore } from '@/stores/auth'

type PermissionMode = 'hide' | 'disable'

interface PermissionBindingValue {
  permissions?: string | string[]
  value?: string | string[]
  mode?: PermissionMode
  reason?: string
}

type PermissionValue = string | string[] | PermissionBindingValue
type FormControlElement = HTMLButtonElement | HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement

const clickBlockers = new WeakMap<HTMLElement, EventListener>()
const originalTitles = new WeakMap<HTMLElement, string | null>()
const originalDisabledStates = new WeakMap<FormControlElement, boolean>()

function isPermissionObject(value: PermissionValue | undefined): value is PermissionBindingValue {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function normalizePermissions(value?: string | string[]) {
  if (!value) return []
  return Array.isArray(value) ? value.filter(Boolean) : [value]
}

function resolvePermissionOptions(binding: DirectiveBinding<PermissionValue>) {
  const rawValue = binding.value
  const permissionValue = isPermissionObject(rawValue) ? rawValue.permissions || rawValue.value : rawValue
  const explicitDisableMode =
    binding.arg === 'disable' ||
    binding.arg === 'disabled' ||
    binding.modifiers.disable ||
    binding.modifiers.disabled

  return {
    permissions: normalizePermissions(permissionValue),
    mode: isPermissionObject(rawValue) ? rawValue.mode || (explicitDisableMode ? 'disable' : 'hide') : explicitDisableMode ? 'disable' : 'hide',
    reason: isPermissionObject(rawValue) ? rawValue.reason || '无操作权限' : '无操作权限'
  }
}

function isFormControl(el: HTMLElement): el is FormControlElement {
  return ['BUTTON', 'INPUT', 'SELECT', 'TEXTAREA'].includes(el.tagName)
}

function findFormControl(el: HTMLElement) {
  if (isFormControl(el)) return el
  return el.querySelector<FormControlElement>('button,input,select,textarea')
}

function blockInteraction(el: HTMLElement) {
  if (clickBlockers.has(el)) return
  const blocker = (event: Event) => {
    event.preventDefault()
    event.stopImmediatePropagation()
  }
  clickBlockers.set(el, blocker)
  el.addEventListener('click', blocker, true)
}

function unblockInteraction(el: HTMLElement) {
  const blocker = clickBlockers.get(el)
  if (!blocker) return
  el.removeEventListener('click', blocker, true)
  clickBlockers.delete(el)
}

function setPermissionDisabled(el: HTMLElement, disabled: boolean, reason = '无操作权限') {
  const control = findFormControl(el)

  if (disabled) {
    if (!originalTitles.has(el)) {
      originalTitles.set(el, el.getAttribute('title'))
    }
    if (control && !originalDisabledStates.has(control)) {
      originalDisabledStates.set(control, control.disabled)
    }
    if (control) {
      control.disabled = true
    }
    el.classList.add('is-permission-disabled')
    el.setAttribute('aria-disabled', 'true')
    el.setAttribute('title', reason)
    blockInteraction(el)
    return
  }

  const originalTitle = originalTitles.get(el)
  if (originalTitle === null) {
    el.removeAttribute('title')
  } else if (originalTitle !== undefined) {
    el.setAttribute('title', originalTitle)
  }
  originalTitles.delete(el)

  if (control && originalDisabledStates.has(control)) {
    control.disabled = originalDisabledStates.get(control) || false
    originalDisabledStates.delete(control)
  }
  el.classList.remove('is-permission-disabled')
  el.removeAttribute('aria-disabled')
  unblockInteraction(el)
}

function updatePermission(el: HTMLElement, binding: DirectiveBinding<PermissionValue>) {
  const { permissions, mode, reason } = resolvePermissionOptions(binding)
  const auth = useAuthStore()
  const hasPermission = auth.hasAnyPermission(permissions)

  if (!hasPermission && mode === 'disable') {
    setPermissionDisabled(el, true, reason)
    return
  }

  setPermissionDisabled(el, false)
  if (!hasPermission) {
    el.remove()
  }
}

export function setupPermissionDirective(app: App) {
  app.directive('permission', {
    mounted: updatePermission,
    updated: updatePermission,
    beforeUnmount: (el) => setPermissionDisabled(el, false)
  })
}

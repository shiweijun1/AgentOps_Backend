import { computed, shallowRef } from 'vue'
import type { CurrentUser, Role } from '../types'

const TOKEN_KEY = 'agentops.accessToken'

export const accessToken = shallowRef<string | null>(sessionStorage.getItem(TOKEN_KEY))
export const currentUser = shallowRef<CurrentUser | null>(null)
export const primaryRole = computed<Role | null>(() => {
  const roles = currentUser.value?.roles ?? []
  if (roles.includes('ADMIN')) return 'ADMIN'
  if (roles.includes('SUPPORT')) return 'SUPPORT'
  if (roles.includes('CUSTOMER')) return 'CUSTOMER'
  return null
})

export function saveToken(token: string): void {
  sessionStorage.setItem(TOKEN_KEY, token)
  accessToken.value = token
}

export function clearSession(): void {
  sessionStorage.removeItem(TOKEN_KEY)
  accessToken.value = null
  currentUser.value = null
}

export function hasPermission(permission: string): boolean {
  return currentUser.value?.permissions.includes(permission) ?? false
}

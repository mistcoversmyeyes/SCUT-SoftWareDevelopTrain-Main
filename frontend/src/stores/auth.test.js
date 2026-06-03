import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'
import * as authApi from '../api/auth'

vi.mock('../api/auth', () => ({
  login: vi.fn(),
  fetchMe: vi.fn()
}))

describe('auth store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('stores token and user after login', async () => {
    authApi.login.mockResolvedValue({
      token: 'demo-token-admin',
      username: 'admin',
      displayName: '系统管理员'
    })

    const auth = useAuthStore()
    await auth.login('admin', '123456')

    expect(auth.token).toBe('demo-token-admin')
    expect(auth.user.displayName).toBe('系统管理员')
    expect(localStorage.getItem('wms-token')).toBe('demo-token-admin')
  })

  it('clears state on logout', () => {
    localStorage.setItem('wms-token', 'demo-token-admin')
    localStorage.setItem('wms-user', JSON.stringify({ username: 'admin', displayName: '系统管理员' }))

    const auth = useAuthStore()
    auth.logout()

    expect(auth.token).toBe('')
    expect(auth.user).toBeNull()
    expect(localStorage.getItem('wms-token')).toBeNull()
  })
})

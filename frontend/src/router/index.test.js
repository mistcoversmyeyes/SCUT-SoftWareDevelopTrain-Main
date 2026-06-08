import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import router from './index'
import { useAuthStore } from '../stores/auth'
import { useTabsStore } from '../stores/tabs'
import * as authApi from '../api/auth'

vi.mock('../api/auth', () => ({
  login: vi.fn(),
  fetchMe: vi.fn()
}))

describe('router auth guard', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('rejects an invalid persisted token and resets auth state', async () => {
    localStorage.setItem('wms-token', 'invalid-token')
    const auth = useAuthStore()
    const tabs = useTabsStore()
    tabs.openTab({
      key: 'materials',
      title: '物料信息',
      path: '/materials',
      closable: true
    })
    authApi.fetchMe.mockRejectedValue(new Error('invalid token'))

    await router.push('/dashboard?review=invalid')

    expect(authApi.fetchMe).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/dashboard?review=invalid')
    expect(auth.token).toBe('')
    expect(auth.verifiedToken).toBe('')
    expect(localStorage.getItem('wms-token')).toBeNull()
    expect(tabs.openTabs).toEqual([
      {
        key: 'dashboard',
        title: '首页',
        path: '/dashboard',
        closable: false
      }
    ])
  })

  it('accepts a valid persisted token after one verification request', async () => {
    localStorage.setItem('wms-token', 'valid-token')
    authApi.fetchMe.mockResolvedValue({
      username: 'admin',
      displayName: '系统管理员'
    })

    await router.push('/dashboard?review=valid')

    const auth = useAuthStore()
    expect(authApi.fetchMe).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.path).toBe('/dashboard')
    expect(auth.user).toEqual({
      username: 'admin',
      displayName: '系统管理员'
    })
    expect(auth.verifiedToken).toBe('valid-token')
    expect(auth.isSessionVerified).toBe(true)

    await router.push('/materials')

    expect(authApi.fetchMe).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.path).toBe('/materials')
  })
})

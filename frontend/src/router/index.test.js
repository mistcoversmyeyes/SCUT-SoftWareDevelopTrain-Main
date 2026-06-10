import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import router from './index'
import { useAuthStore } from '../stores/auth'
import { useTabsStore } from '../stores/tabs'
import * as authApi from '../api/auth'
import PlaceholderPage from '../views/PlaceholderPage.vue'
import InboundOrderListView from '../views/inbound/InboundOrderListView.vue'
import InboundScanView from '../views/inbound/InboundScanView.vue'
import InboundPrintView from '../views/inbound/InboundPrintView.vue'
import KanbanPrintView from '../views/inbound/KanbanPrintView.vue'
import InventoryBalanceView from '../views/inventory/InventoryBalanceView.vue'
import InventoryTraceView from '../views/inventory/InventoryTraceView.vue'
import KanbanTraceView from '../views/kanban/KanbanTraceView.vue'

vi.mock('../api/auth', () => ({
  login: vi.fn(),
  fetchMe: vi.fn()
}))

describe('router auth guard', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    useTabsStore().resetTabs()
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

  it('resolves inbound and inventory routes to concrete view components', async () => {
    localStorage.setItem('wms-token', 'valid-token')
    authApi.fetchMe.mockResolvedValue({
      username: 'admin',
      displayName: '系统管理员'
    })

    const routeCases = [
      {
        path: '/inbound/orders',
        name: 'inbound-orders',
        component: InboundOrderListView
      },
      {
        path: '/inbound/scan',
        name: 'inbound-scan',
        component: InboundScanView
      },
      {
        path: '/inventory/balances',
        name: 'inventory-balances',
        component: InventoryBalanceView
      },
      {
        path: '/inventory/trace',
        name: 'inventory-trace',
        component: InventoryTraceView
      },
      {
        path: '/kanbans/trace',
        name: 'kanbans-trace',
        component: KanbanTraceView
      },
      {
        path: '/inbound/10/print',
        name: 'inbound-print',
        component: InboundPrintView
      },
      {
        path: '/inbound/10/kanbans/print',
        name: 'kanban-print',
        component: KanbanPrintView
      }
    ]

    for (const routeCase of routeCases) {
      await router.push(routeCase.path)
      expect(router.currentRoute.value.name).toBe(routeCase.name)
      expect(router.currentRoute.value.path).toBe(routeCase.path)
      expect(router.currentRoute.value.matched.at(-1).components.default).toBe(routeCase.component)
    }
  })

  it('keeps generic dashboard placeholder mapped to placeholder view', async () => {
    localStorage.setItem('wms-token', 'valid-token')
    authApi.fetchMe.mockResolvedValue({
      username: 'admin',
      displayName: '系统管理员'
    })
    await router.push('/dashboard')

    expect(router.currentRoute.value.matched.at(-1).components.default).toBe(PlaceholderPage)
  })
})

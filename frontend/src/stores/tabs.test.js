import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useTabsStore } from './tabs'

describe('tabs store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('opens dashboard by default', () => {
    const tabs = useTabsStore()
    expect(tabs.openTabs).toEqual([{ key: 'dashboard', title: '首页', path: '/dashboard', closable: false }])
    expect(tabs.activeKey).toBe('dashboard')
  })

  it('adds a menu tab once and activates it', () => {
    const tabs = useTabsStore()
    tabs.openTab({ key: 'materials', title: '物料信息', path: '/materials', closable: true })
    tabs.openTab({ key: 'materials', title: '物料信息', path: '/materials', closable: true })

    expect(tabs.openTabs).toHaveLength(2)
    expect(tabs.activeKey).toBe('materials')
  })

  it('switches to neighbor after closing active tab', () => {
    const tabs = useTabsStore()
    tabs.openTab({ key: 'materials', title: '物料信息', path: '/materials', closable: true })
    const next = tabs.closeTab('materials')

    expect(next.path).toBe('/dashboard')
    expect(tabs.activeKey).toBe('dashboard')
  })

  it('keeps dashboard open and active when closing dashboard', () => {
    const tabs = useTabsStore()
    const next = tabs.closeTab('dashboard')

    expect(next).toEqual({ key: 'dashboard', title: '首页', path: '/dashboard', closable: false })
    expect(tabs.openTabs).toEqual([{ key: 'dashboard', title: '首页', path: '/dashboard', closable: false }])
    expect(tabs.activeKey).toBe('dashboard')
  })

  it('keeps active tab when closing a non-active tab', () => {
    const tabs = useTabsStore()
    tabs.openTab({ key: 'materials', title: '物料信息', path: '/materials', closable: true })
    tabs.openTab({ key: 'inbound', title: '入库管理', path: '/inbound', closable: true })
    const next = tabs.closeTab('materials')

    expect(next.path).toBe('/inbound')
    expect(tabs.activeKey).toBe('inbound')
    expect(tabs.openTabs.map((tab) => tab.key)).toEqual(['dashboard', 'inbound'])
  })

  it('switches to the right neighbor after closing an active middle tab', () => {
    const tabs = useTabsStore()
    tabs.openTab({ key: 'materials', title: '物料信息', path: '/materials', closable: true })
    tabs.openTab({ key: 'inbound', title: '入库管理', path: '/inbound', closable: true })
    tabs.openTab({ key: 'inventory', title: '库存监控', path: '/inventory', closable: true })
    tabs.activateTab('inbound')
    const next = tabs.closeTab('inbound')

    expect(next.path).toBe('/inventory')
    expect(tabs.activeKey).toBe('inventory')
  })

  it('switches left after closing an active last tab', () => {
    const tabs = useTabsStore()
    tabs.openTab({ key: 'materials', title: '物料信息', path: '/materials', closable: true })
    tabs.openTab({ key: 'inbound', title: '入库管理', path: '/inbound', closable: true })
    const next = tabs.closeTab('inbound')

    expect(next.path).toBe('/materials')
    expect(tabs.activeKey).toBe('materials')
  })

  it('resets to only a fresh dashboard tab', () => {
    const tabs = useTabsStore()
    tabs.openTab({ key: 'materials', title: '物料信息', path: '/materials', closable: true })
    tabs.openTabs[0].title = 'mutated'
    tabs.resetTabs()

    expect(tabs.openTabs).toEqual([{ key: 'dashboard', title: '首页', path: '/dashboard', closable: false }])
    expect(tabs.activeKey).toBe('dashboard')
  })

  it('ignores an unknown key when closing tabs', () => {
    const tabs = useTabsStore()
    tabs.openTab({ key: 'materials', title: '物料信息', path: '/materials', closable: true })
    const next = tabs.closeTab('missing')

    expect(next.path).toBe('/materials')
    expect(tabs.activeKey).toBe('materials')
    expect(tabs.openTabs.map((tab) => tab.key)).toEqual(['dashboard', 'materials'])
  })

  it('copies incoming tabs before storing them', () => {
    const tabs = useTabsStore()
    const tab = { key: 'materials', title: '物料信息', path: '/materials', closable: true }
    tabs.openTab(tab)
    tab.title = 'mutated'

    expect(tabs.openTabs[1].title).toBe('物料信息')
  })
})

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
})

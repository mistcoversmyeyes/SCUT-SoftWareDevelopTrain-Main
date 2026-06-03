import { defineStore } from 'pinia'

const dashboardTab = {
  key: 'dashboard',
  title: '首页',
  path: '/dashboard',
  closable: false
}

export const useTabsStore = defineStore('tabs', {
  state: () => ({
    openTabs: [dashboardTab],
    activeKey: dashboardTab.key
  }),
  actions: {
    openTab(tab) {
      if (!this.openTabs.some((item) => item.key === tab.key)) {
        this.openTabs.push(tab)
      }
      this.activeKey = tab.key
    },
    activateTab(key) {
      if (this.openTabs.some((item) => item.key === key)) {
        this.activeKey = key
      }
    },
    closeTab(key) {
      const index = this.openTabs.findIndex((item) => item.key === key)
      if (index === -1 || !this.openTabs[index].closable) {
        return this.openTabs.find((item) => item.key === this.activeKey)
      }

      const wasActive = this.activeKey === key
      this.openTabs.splice(index, 1)

      if (wasActive) {
        const nextIndex = Math.min(index, this.openTabs.length - 1)
        const nextTab = this.openTabs[nextIndex] || dashboardTab
        this.activeKey = nextTab.key
        return nextTab
      }

      return this.openTabs.find((item) => item.key === this.activeKey)
    },
    resetTabs() {
      this.openTabs = [dashboardTab]
      this.activeKey = dashboardTab.key
    }
  }
})

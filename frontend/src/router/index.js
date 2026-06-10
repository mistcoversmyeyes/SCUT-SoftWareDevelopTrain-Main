import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useTabsStore } from '../stores/tabs'
import { menuItems } from '../menu'
import LoginView from '../views/LoginView.vue'
import MainLayout from '../views/MainLayout.vue'
import PlaceholderPage from '../views/PlaceholderPage.vue'
import InboundOrderListView from '../views/inbound/InboundOrderListView.vue'
import InboundScanView from '../views/inbound/InboundScanView.vue'
import InventoryBalanceView from '../views/inventory/InventoryBalanceView.vue'
import InventoryTraceView from '../views/inventory/InventoryTraceView.vue'
import KanbanTraceView from '../views/kanban/KanbanTraceView.vue'

const routes = [
  { path: '/login', name: 'login', component: LoginView },
  {
    path: '/',
    component: MainLayout,
    meta: { requiresAuth: true },
    redirect: '/dashboard',
    children: menuItems.map((item) => {
      const pageByKey = {
        'inbound-orders': InboundOrderListView,
        'inbound-scan': InboundScanView,
        'inventory-balances': InventoryBalanceView,
        'inventory-trace': InventoryTraceView,
        'kanbans-trace': KanbanTraceView
      }
      return {
        path: item.path.slice(1),
        name: item.key,
        component: pageByKey[item.key] || PlaceholderPage,
        meta: {
          requiresAuth: true,
          tabKey: item.key,
          title: item.title
        },
        props: pageByKey[item.key]
          ? false
          : {
              title: item.title,
              description: item.description,
              fields: item.fields
            }
      }
    })
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

async function verifyCurrentSession(auth) {
  if (auth.isSessionVerified) {
    return true
  }

  await auth.loadCurrentUser()
  return true
}

function loginRedirect(to) {
  return { name: 'login', query: { redirect: to.fullPath } }
}

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  const tabs = useTabsStore()

  if (to.name === 'login' && auth.isLoggedIn) {
    try {
      await verifyCurrentSession(auth)
      return { name: 'dashboard' }
    } catch {
      auth.logout()
      tabs.resetTabs()
      return true
    }
  }

  if (!to.meta.requiresAuth) {
    return true
  }

  if (!auth.isLoggedIn) {
    return loginRedirect(to)
  }

  try {
    await verifyCurrentSession(auth)
  } catch {
    auth.logout()
    tabs.resetTabs()
    return loginRedirect(to)
  }

  return true
})

router.afterEach((to) => {
  if (!to.meta.tabKey) {
    return
  }
  const tabs = useTabsStore()
  tabs.openTab({
    key: to.meta.tabKey,
    title: to.meta.title,
    path: to.path,
    closable: to.meta.tabKey !== 'dashboard'
  })
})

export default router

import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useTabsStore } from '../stores/tabs'
import { menuItems, flattenMenuItems } from '../menu'
import LoginView from '../views/LoginView.vue'
import MainLayout from '../views/MainLayout.vue'
import DashboardView from '../views/DashboardView.vue'
import SupplierListView from '../views/master-data/SupplierListView.vue'
import MaterialListView from '../views/master-data/MaterialListView.vue'
import ContainerListView from '../views/master-data/ContainerListView.vue'
import WarehouseLocationView from '../views/master-data/WarehouseLocationView.vue'
import InboundOrderListView from '../views/inbound/InboundOrderListView.vue'
import InboundScanView from '../views/inbound/InboundScanView.vue'
import InboundDetailView from '../views/inbound/InboundDetailView.vue'
import InboundHistoryView from '../views/inbound/InboundHistoryView.vue'
import InboundPrintView from '../views/inbound/InboundPrintView.vue'
import KanbanPrintView from '../views/inbound/KanbanPrintView.vue'
import KanbanDetailView from '../views/inbound/KanbanDetailView.vue'
import OutboundOrderListView from '../views/outbound/OutboundOrderListView.vue'
import OutboundScanView from '../views/outbound/OutboundScanView.vue'
import OutboundDetailView from '../views/outbound/OutboundDetailView.vue'
import OutboundHistoryView from '../views/outbound/OutboundHistoryView.vue'
import OutboundPickWithOrderView from '../views/outbound/OutboundPickWithOrderView.vue'
import OutboundPickNoOrderView from '../views/outbound/OutboundPickNoOrderView.vue'
import OutboundLockView from '../views/outbound/OutboundLockView.vue'
import InventoryBalanceView from '../views/inventory/InventoryBalanceView.vue'
import InventoryTraceView from '../views/inventory/InventoryTraceView.vue'
import InventoryOverviewView from '../views/inventory/InventoryOverviewView.vue'
import InventoryAiImportView from '../views/inventory/InventoryAiImportView.vue'
import KanbanListView from '../views/kanban/KanbanListView.vue'
import KanbanTraceView from '../views/kanban/KanbanTraceView.vue'
import PlaceholderPage from '../views/PlaceholderPage.vue'


const pageByKey = {
  'dashboard': DashboardView,
  'suppliers': SupplierListView,
  'materials': MaterialListView,
  'containers': ContainerListView,
  'warehouses': WarehouseLocationView,
  'inbound-orders': InboundOrderListView,
  'inbound-scan': InboundScanView,
  'inbound-history': InboundHistoryView,
  'outbound-orders': OutboundOrderListView,
  'outbound-scan': OutboundScanView,
  'outbound-pick-with-order': OutboundPickWithOrderView,
  'outbound-pick-no-order': OutboundPickNoOrderView,
  'outbound-locks': OutboundLockView,
  'outbound-history': OutboundHistoryView,
  'inventory-balances': InventoryBalanceView,
  'inventory-trace': InventoryTraceView,
  'inventory-overview': InventoryOverviewView,
  'inventory-ai-import': InventoryAiImportView,
  'kanbans-list': KanbanListView,
  'kanbans-trace': KanbanTraceView
}

const routes = [
  { path: '/login', name: 'login', component: LoginView },
  {
    path: '/',
    component: MainLayout,
    meta: { requiresAuth: true },
    redirect: '/dashboard',
    children: flattenMenuItems(menuItems).map((item) => {
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
    }).concat([
      {
        path: 'outbound/scan',
        name: 'outbound-scan',
        component: OutboundScanView,
        meta: { requiresAuth: true, tabKey: 'outbound-scan', title: '出库扫码' }
      },
      {
        path: 'outbound/:id',
        name: 'outbound-detail',
        component: OutboundDetailView,
        meta: { requiresAuth: true, tabKey: 'outbound-detail', title: '出库单详情' }
      },
      {
        path: 'inbound/:id/print',
        name: 'inbound-print',
        component: InboundPrintView,
        meta: {
          requiresAuth: true,
          title: '入库单打印'
        }
      },
      {
        path: 'inbound/:id/kanbans/print',
        name: 'kanban-print',
        component: KanbanPrintView,
        meta: {
          requiresAuth: true,
          title: '看板打印'
        }
      },
      {
        path: 'inbound/:id',
        name: 'inbound-detail',
        component: InboundDetailView,
        meta: { requiresAuth: true, tabKey: 'inbound-detail', title: '入库单详情' }
      },
      {
        path: 'inbound/:id/kanbans',
        name: 'kanban-detail',
        component: KanbanDetailView,
        meta: { requiresAuth: true, tabKey: 'kanban-detail', title: '看板详情' }
      },
    ])
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

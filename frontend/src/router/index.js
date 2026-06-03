import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import MainLayout from '../views/MainLayout.vue'
import PlaceholderPage from '../views/PlaceholderPage.vue'

const pageMap = {
  dashboard: {
    title: '首页',
    description: '查看仓储运行概览、待办事项和基础统计。'
  },
  materials: {
    title: '物料信息',
    description: '维护汽车零部件物料编码、名称、规格和单位。'
  },
  inbound: {
    title: '入库管理',
    description: '跟踪采购到货、质检完成和上架入库流程。'
  },
  inventory: {
    title: '库存监控',
    description: '查看当前库存数量、安全库存和库位状态。'
  },
  outbound: {
    title: '出库管理',
    description: '处理销售出库、领料出库和发运状态。'
  }
}

const routes = [
  { path: '/login', name: 'login', component: LoginView },
  {
    path: '/',
    component: MainLayout,
    meta: { requiresAuth: true },
    redirect: '/dashboard',
    children: Object.entries(pageMap).map(([key, page]) => ({
      path: key,
      name: key,
      component: PlaceholderPage,
      meta: { requiresAuth: true, tabKey: key, title: page.title },
      props: {
        title: page.title,
        description: page.description
      }
    }))
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.isLoggedIn) {
    return { name: 'dashboard' }
  }
  return true
})

export default router

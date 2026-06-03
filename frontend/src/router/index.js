import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useTabsStore } from '../stores/tabs'
import { menuItems } from '../menu'
import LoginView from '../views/LoginView.vue'
import MainLayout from '../views/MainLayout.vue'
import PlaceholderPage from '../views/PlaceholderPage.vue'

const routes = [
  { path: '/login', name: 'login', component: LoginView },
  {
    path: '/',
    component: MainLayout,
    meta: { requiresAuth: true },
    redirect: '/dashboard',
    children: menuItems.map((item) => ({
      path: item.path.slice(1),
      name: item.key,
      component: PlaceholderPage,
      meta: { requiresAuth: true, tabKey: item.key, title: item.title },
      props: {
        title: item.title,
        description: item.description,
        fields: item.fields
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

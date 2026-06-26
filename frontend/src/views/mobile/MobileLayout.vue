<template>
  <div class="mobile-layout">
    <header class="mobile-header">
      <div>
        <p class="mobile-kicker">课堂演示 H5</p>
        <h1>汽车物流 WMS</h1>
      </div>
      <div class="mobile-header-actions">
        <span class="mobile-user">{{ auth.user?.displayName || '系统管理员' }}</span>
        <el-button size="small" plain @click="goDesktop">后台</el-button>
        <el-button size="small" @click="logout">退出</el-button>
      </div>
    </header>

    <main class="mobile-content">
      <router-view />
    </main>

    <nav class="mobile-nav" aria-label="mobile navigation">
      <button
        v-for="item in navItems"
        :key="item.name"
        type="button"
        class="mobile-nav-item"
        :class="{ active: route.name === item.name }"
        @click="router.push(item.path)"
      >
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </button>
    </nav>
  </div>
</template>

<script setup>
import { Box, Lock, Search, Van } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useTabsStore } from '../../stores/tabs'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const tabs = useTabsStore()

const navItems = [
  { name: 'mobile-inbound', label: '入库', path: '/mobile/inbound', icon: Box },
  { name: 'mobile-outbound', label: '出库', path: '/mobile/outbound', icon: Van },
  { name: 'mobile-inventory-tag', label: '库存标签', path: '/mobile/inventory-tag', icon: Search },
  { name: 'mobile-inventory-seal', label: '库存封存', path: '/mobile/seal', icon: Lock }
]

function goDesktop() {
  router.push('/dashboard')
}

function logout() {
  auth.logout()
  tabs.resetTabs()
  router.push({
    name: 'login',
    query: { redirect: typeof route.fullPath === 'string' ? route.fullPath : '/mobile/inbound' }
  })
}
</script>

<style scoped>
.mobile-layout {
  min-height: 100vh;
  display: grid;
  grid-template-rows: auto 1fr auto;
  background: #f8fafc;
}

.mobile-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 16px 14px;
  color: #e2e8f0;
  background: #0f172a;
}

.mobile-kicker {
  margin: 0 0 6px;
  color: #93c5fd;
  font-size: 12px;
}

.mobile-header h1 {
  margin: 0;
  font-size: 20px;
  color: #f8fafc;
}

.mobile-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.mobile-user {
  font-size: 13px;
  color: #cbd5e1;
}

.mobile-content {
  min-width: 0;
  padding: 16px 16px 88px;
}

.mobile-nav {
  position: sticky;
  bottom: 0;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  padding: 10px 12px calc(10px + env(safe-area-inset-bottom, 0px));
  background: rgba(255, 255, 255, 0.96);
  border-top: 1px solid #dbe4ef;
  backdrop-filter: blur(10px);
}

.mobile-nav-item {
  min-height: 56px;
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 4px;
  border: 1px solid #dbe4ef;
  border-radius: 8px;
  background: #ffffff;
  color: #475569;
}

.mobile-nav-item.active {
  color: #2563eb;
  border-color: #93c5fd;
  background: #eff6ff;
}

.mobile-nav-item span {
  font-size: 12px;
}

@media (min-width: 900px) {
  .mobile-layout {
    max-width: 480px;
    margin: 0 auto;
    box-shadow: 0 0 0 1px #dbe4ef;
  }
}
</style>

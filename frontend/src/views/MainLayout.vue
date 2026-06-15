<template>
  <el-container class="admin-layout">
    <el-header class="admin-header">
      <div>
        <strong>汽车物流 WMS</strong>
        <span>第一周作业演示</span>
      </div>
      <div class="header-user">
        <span>{{ auth.user?.displayName || '系统管理员' }}</span>
        <el-button size="small" @click="logout">退出登录</el-button>
      </div>
    </el-header>

    <el-container>
      <el-aside width="220px" class="admin-aside">
        <SideMenu />
      </el-aside>
      <el-container class="content-wrapper">
        <TabBar />
        <el-main class="admin-main">
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'
import SideMenu from '../components/SideMenu.vue'
import TabBar from '../components/TabBar.vue'
import { useAuthStore } from '../stores/auth'
import { useTabsStore } from '../stores/tabs'

const router = useRouter()
const auth = useAuthStore()
const tabs = useTabsStore()

function logout() {
  auth.logout()
  tabs.resetTabs()
  router.push('/login')
}
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
}

.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #ffffff;
  background: #0f172a;
}

.admin-header strong {
  margin-right: 16px;
  font-size: 18px;
}

.admin-header span {
  color: #cbd5e1;
}

.header-user {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-aside {
  background: #ffffff;
  border-right: 1px solid #e5e7eb;
}

.admin-main {
  padding: 24px;
}

.content-wrapper {
  display: flex;
  flex-direction: column;
}
</style>

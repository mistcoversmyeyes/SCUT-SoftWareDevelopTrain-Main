<template>
  <el-menu class="side-menu" :default-active="activeKey" @select="handleSelect">
    <el-menu-item v-for="item in menuItems" :key="item.key" :index="item.key">
      <el-icon><component :is="item.icon" /></el-icon>
      <span>{{ item.title }}</span>
    </el-menu-item>
  </el-menu>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { menuItems, findMenuItem } from '../menu'
import { useTabsStore } from '../stores/tabs'

const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const activeKey = computed(() => route.meta.tabKey || tabs.activeKey)

function handleSelect(key) {
  const item = findMenuItem(key)
  if (!item) {
    return
  }
  tabs.openTab({
    key: item.key,
    title: item.title,
    path: item.path,
    closable: item.key !== 'dashboard'
  })
  router.push(item.path)
}
</script>

<style scoped>
.side-menu {
  height: 100%;
  border-right: 0;
}
</style>

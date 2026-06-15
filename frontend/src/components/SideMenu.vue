<template>
  <el-menu class="side-menu" :default-active="activeKey" :collapse="false" @select="handleSelect">
    <template v-for="item in menuItems" :key="item.key">
      <el-sub-menu v-if="item.children" :index="item.key">
        <template #title>
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </template>
        <el-menu-item v-for="child in item.children" :key="child.key" :index="child.key">
          <el-icon v-if="child.icon"><component :is="child.icon" /></el-icon>
          <span>{{ child.title }}</span>
        </el-menu-item>
      </el-sub-menu>
      <el-menu-item v-else :index="item.key">
        <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
        <span>{{ item.title }}</span>
      </el-menu-item>
    </template>
  </el-menu>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTabsStore } from '../stores/tabs'
import { menuItems, findMenuItem } from '../menu'

const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()

const activeKey = computed(() => route.meta.tabKey || tabs.activeKey)

function handleSelect(key) {
  const item = findMenuItem(key)
  if (!item || !item.path) return
  tabs.openTab({ key: item.key, title: item.title, path: item.path, closable: item.key !== 'dashboard' })
  router.push(item.path)
}
</script>

<style scoped>
.side-menu {
  border-right: none;
  height: 100%;
}
</style>

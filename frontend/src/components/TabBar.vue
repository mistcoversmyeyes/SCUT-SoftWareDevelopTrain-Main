<template>
  <el-tabs
    class="tab-bar"
    type="card"
    :model-value="tabs.activeKey"
    @tab-change="handleChange"
    @tab-remove="handleRemove"
  >
    <el-tab-pane
      v-for="tab in tabs.openTabs"
      :key="tab.key"
      :label="tab.title"
      :name="tab.key"
      :closable="tab.closable"
    />
  </el-tabs>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useTabsStore } from '../stores/tabs'

const router = useRouter()
const tabs = useTabsStore()

function handleChange(key) {
  tabs.activateTab(key)
  const tab = tabs.openTabs.find((item) => item.key === key)
  if (tab) {
    router.push(tab.path)
  }
}

function handleRemove(key) {
  const nextTab = tabs.closeTab(key)
  if (nextTab) {
    router.push(nextTab.path)
  }
}
</script>

<style scoped>
.tab-bar {
  padding: 8px 16px 0;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
}
</style>

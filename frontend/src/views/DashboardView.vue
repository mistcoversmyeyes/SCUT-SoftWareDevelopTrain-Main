<template>
  <section class="dashboard-page">
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ stats.todayInbound ?? 0 }}</div>
          <div class="stat-label">今日入库</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ stats.todayOutbound ?? 0 }}</div>
          <div class="stat-label">今日出库</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ stats.alertCount ?? 0 }}</div>
          <div class="stat-label">库存预警</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ stats.totalMaterials ?? 0 }}</div>
          <div class="stat-label">总物料数</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="alert-row">
      <el-col :span="12">
        <el-card v-loading="loading">
          <template #header><span>低库存预警</span></template>
          <el-table :data="lowStockItems" border stripe size="small" empty-text="暂无低库存物料">
            <el-table-column prop="materialCode" label="物料编码" min-width="140" />
            <el-table-column prop="materialName" label="物料名称" min-width="160" />
            <el-table-column prop="currentQty" label="当前库存" width="120" align="right">
              <template #default="{ row }">{{ formatQty(row.currentQty) }}</template>
            </el-table-column>
            <el-table-column prop="lowStockQty" label="最低库存" width="120" align="right">
              <template #default="{ row }">{{ formatQty(row.lowStockQty) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card v-loading="loading">
          <template #header><span>高库存预警</span></template>
          <el-table :data="highStockItems" border stripe size="small" empty-text="暂无高库存物料">
            <el-table-column prop="materialCode" label="物料编码" min-width="140" />
            <el-table-column prop="materialName" label="物料名称" min-width="160" />
            <el-table-column prop="currentQty" label="当前库存" width="120" align="right">
              <template #default="{ row }">{{ formatQty(row.currentQty) }}</template>
            </el-table-column>
            <el-table-column prop="highStockQty" label="最高库存" width="120" align="right">
              <template #default="{ row }">{{ formatQty(row.highStockQty) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchDashboardStats } from '../api/dashboard'
import { fetchInventoryBalances } from '../api/inventory'

const loading = ref(false)
const stats = ref({})
const lowStockItems = ref([])
const highStockItems = ref([])

function formatQty(value) {
  if (value === null || value === undefined) return '0'
  const num = Number(value)
  if (Number.isNaN(num)) return value
  return num.toFixed(3)
}

async function loadData() {
  loading.value = true
  try {
    const result = await fetchDashboardStats()
    stats.value = result || {}
    const balances = await fetchInventoryBalances({ pageSize: 100 })
    const items = Array.isArray(balances) ? balances : (balances?.records || balances?.items || [])
    lowStockItems.value = items.filter(
      (item) => item.currentQty != null && item.lowStockQty != null && item.currentQty <= item.lowStockQty
    )
    highStockItems.value = items.filter(
      (item) => item.currentQty != null && item.highStockQty != null && item.currentQty >= item.highStockQty
    )
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '仪表盘数据加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.dashboard-page { min-height: 360px; }
.stat-row { margin-bottom: 24px; }
.stat-card { text-align: center; }
.stat-value { font-size: 36px; font-weight: 700; color: #409eff; line-height: 1.2; }
.stat-label { font-size: 14px; color: #909399; margin-top: 8px; }
.alert-row { margin-top: 0; }
</style>

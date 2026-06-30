<template>
  <section class="dashboard-page">
    <!-- ═══ 统计卡片 ═══ -->
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
          <div class="stat-value">{{ stats.pendingOrders ?? 0 }}</div>
          <div class="stat-label">待处理入库单</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ═══ 仪表盘区：饼图 + 预警表 ═══ -->
    <el-row :gutter="16" class="chart-row" type="flex">
      <el-col :span="10">
        <el-card v-loading="loadingPie" class="full-height-card">
          <template #header><span>库存状态分布</span></template>
          <div ref="pieChartRef" class="pie-chart" v-show="!pieEmpty" />
          <el-empty v-if="pieEmpty" description="暂无库存数据" :image-size="80" />
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card v-loading="loading" class="full-height-card">
          <template #header>
            <div class="card-header-row">
              <span>库存预警</span>
              <el-radio-group v-model="alertFilter" size="small">
                <el-radio-button value="">全部</el-radio-button>
                <el-radio-button value="low">低库存</el-radio-button>
                <el-radio-button value="high">超储</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <el-table :data="filteredAlerts" border stripe size="small" empty-text="暂无预警物料" max-height="320">
            <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="onHandQty" label="库存" width="80" align="right">
              <template #default="{ row }">{{ formatQty(row.onHandQty) }}</template>
            </el-table-column>
            <el-table-column label="类型" width="80">
              <template #default="{ row }">
                <el-tag :type="row.alertType === 'low' ? 'danger' : 'warning'" size="small">
                  {{ row.alertType === 'low' ? '短缺' : '超储' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="阈值" width="80" align="right">
              <template #default="{ row }">{{ formatQty(row.threshold) }}</template>
            </el-table-column>
            <el-table-column label="偏差" width="80" align="right">
              <template #default="{ row }">
                <span :class="row.alertType === 'low' ? 'num-red' : 'num-orange'">
                  {{ row.alertType === 'low' ? '-' : '+' }}{{ formatQty(Math.abs(row.gap)) }}
                </span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- ═══ 库位总览 ═══ -->
    <el-row :gutter="16" class="wh-row" type="flex">
      <el-col :span="12" v-for="wh in warehouseData" :key="wh.id">
        <el-card v-loading="loadingWh" shadow="hover" class="full-height-card">
          <template #header>
            <div class="card-header-row">
              <span>{{ wh.name }}</span>
              <span class="wh-summary">
                <strong>{{ wh.totalUsed }}</strong> / {{ wh.totalMax }} 箱
                ({{ wh.totalMax > 0 ? Math.round(wh.totalUsed / wh.totalMax * 100) : 0 }}%)
              </span>
            </div>
          </template>
          <div class="loc-list">
            <div v-for="loc in wh.locations" :key="loc.id" class="loc-row">
              <span class="loc-name">{{ loc.code }}</span>
              <div class="loc-bar-track">
                <div class="loc-bar-fill" :class="locBarClass(loc)" :style="{ width: locBarPct(loc) + '%' }" />
              </div>
              <span class="loc-nums" :class="locNumClass(loc)">
                {{ loc.usedBoxes }} / {{ loc.maxCapacity || '—' }}
                <span v-if="loc.totalPieces > 0" class="loc-pieces">({{ loc.totalPieces }})</span>
                <span v-if="locOver(loc)" class="warn-badge">超容</span>
                <span v-else-if="locFull(loc)" class="warn-badge warn-yellow">已满</span>
                <span v-else-if="locNearFull(loc)" class="warn-badge warn-yellow">快满</span>
              </span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { http } from '../api/http'
import { fetchDashboardStats } from '../api/dashboard'
import { fetchInventoryBalances } from '../api/inventory'

const loading = ref(false)
const loadingPie = ref(false)
const loadingWh = ref(false)
const stats = ref({})
const warehouseData = ref([])
const allAlerts = ref([])
const alertFilter = ref('')
const pieData = ref([])
const pieEmpty = ref(false)

const pieChartRef = ref(null)
let chartInstance = null

const PIE_COLORS = ['#67c23a', '#409eff', '#e6a23c', '#f56c6c', '#909399']
const PIE_LABELS = ['可用', '锁定', '封存', '手锁', '零头']

const filteredAlerts = computed(() => {
  if (!alertFilter.value) return allAlerts.value
  return allAlerts.value.filter(a => a.alertType === alertFilter.value)
})

function formatQty(value) {
  if (value === null || value === undefined) return '0'
  const num = Number(value)
  if (Number.isNaN(num)) return value
  return String(num)
}

function aggPieData(balances) {
  const totals = { available: 0, locked: 0, sealed: 0, manualLocked: 0, loose: 0 }
  for (const b of balances) {
    totals.available += Number(b.availableQty) || 0
    totals.locked += Number(b.outboundLockedQty) || 0
    totals.sealed += Number(b.sealedQty) || 0
    totals.manualLocked += Number(b.manualLockedQty) || 0
    totals.loose += Number(b.looseQty) || 0
  }
  return [
    { name: '可用', value: totals.available },
    { name: '锁定', value: totals.locked },
    { name: '封存', value: totals.sealed },
    { name: '手锁', value: totals.manualLocked },
    { name: '零头', value: totals.loose }
  ].filter(d => d.value > 0)
}

function buildAlerts(dashboardResult) {
  const list = []
  for (const item of (dashboardResult.lowStockAlerts || [])) {
    list.push({
      materialCode: item.materialCode,
      materialName: item.materialName,
      onHandQty: item.onHandQty,
      alertType: 'low',
      threshold: item.lowStockQty,
      gap: Number(item.onHandQty || 0) - Number(item.lowStockQty || 0)
    })
  }
  for (const item of (dashboardResult.highStockAlerts || [])) {
    list.push({
      materialCode: item.materialCode,
      materialName: item.materialName,
      onHandQty: item.onHandQty,
      alertType: 'high',
      threshold: item.highStockQty,
      gap: Number(item.onHandQty || 0) - Number(item.highStockQty || 0)
    })
  }
  // dedupe same materialCode + alertType
  const seen = new Set()
  return list.filter(a => {
    const key = `${a.materialCode}:${a.alertType}`
    if (seen.has(key)) return false
    seen.add(key)
    return true
  }).sort((a, b) => {
    if (a.alertType !== b.alertType) return a.alertType === 'low' ? -1 : 1
    return Number(a.gap) - Number(b.gap)
  })
}

function renderPieChart() {
  if (!pieChartRef.value) return
  if (!chartInstance) {
    chartInstance = echarts.init(pieChartRef.value)
  }
  if (!pieData.value.length) {
    pieEmpty.value = true
    return
  }
  pieEmpty.value = false
  chartInstance.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    color: PIE_COLORS,
    series: [{
      type: 'pie',
      radius: ['50%', '75%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}\n{d}%' },
      emphasis: { label: { fontSize: 16, fontWeight: 'bold' } },
      data: pieData.value
    }]
  }, true)
}

async function loadData() {
  loading.value = true
  loadingPie.value = true
  try {
    const r = await fetchDashboardStats()
    stats.value = {
      todayInbound: r.todayInboundCount ?? 0,
      todayOutbound: r.todayOutboundCount ?? 0,
      alertCount: (r.lowStockAlerts?.length || 0) + (r.highStockAlerts?.length || 0),
      pendingOrders: r.pendingOrders ?? 0
    }
    allAlerts.value = buildAlerts(r)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '仪表盘数据加载失败')
  } finally {
    loading.value = false
    loadingPie.value = false
  }

  try {
    const balances = await fetchInventoryBalances({ pageSize: 200 })
    const arr = Array.isArray(balances) ? balances : (balances?.records || balances?.items || [])
    pieData.value = aggPieData(arr)
    await nextTick()
    renderPieChart()
  } catch {
    pieData.value = []
  }

  loadingWh.value = true
  try {
    const res = await http.get('/inventory/overview')
    const warehouses = res.data?.warehouses || []
    warehouseData.value = warehouses.map(wh => ({
      ...wh,
      totalUsed: wh.locations.reduce((s, l) => s + (Number(l.usedBoxes) || 0), 0),
      totalMax: wh.locations.reduce((s, l) => s + (Number(l.maxCapacity) || 0), 0)
    }))
  } catch {
    warehouseData.value = []
  } finally {
    loadingWh.value = false
  }
}

function locOver(loc) { return (Number(loc.maxCapacity) || 0) > 0 && (Number(loc.usedBoxes) || 0) > (Number(loc.maxCapacity) || 0) }
function locFull(loc) { return (Number(loc.maxCapacity) || 0) > 0 && (Number(loc.usedBoxes) || 0) >= (Number(loc.maxCapacity) || 0) }
function locNearFull(loc) { const m = Number(loc.maxCapacity) || 0; const u = Number(loc.usedBoxes) || 0; return m > 0 && u >= m * 0.85 && u < m }
function locBarPct(loc) { const u = Number(loc.usedBoxes) || 0; const m = Number(loc.maxCapacity) || 0; const limit = Math.max(m, u); return limit > 0 ? Math.round(u / limit * 100) : 0 }
function locBarClass(loc) { if (locOver(loc)) return 'bar-over'; if (locFull(loc)) return 'bar-full'; return '' }
function locNumClass(loc) { if (locOver(loc)) return 'num-red'; if (locFull(loc)) return 'num-orange'; return '' }

onMounted(() => { loadData() })

onBeforeUnmount(() => {
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<style scoped>
.dashboard-page { min-height: 360px; }

.stat-row { margin-bottom: 20px; }
.stat-card { text-align: center; }
.stat-card :deep(.el-card__body) { padding: 24px 20px; }
.stat-value { font-size: 36px; font-weight: 700; color: #409eff; line-height: 1.2; }
.stat-label { font-size: 14px; color: #909399; margin-top: 8px; }

.chart-row { margin-top: 0; }

.full-height-card { height: 100%; }
.full-height-card :deep(.el-card__body) { padding: 16px; }

.pie-chart { width: 100%; height: 320px; }

.card-header-row {
  display: flex; align-items: center; justify-content: space-between;
}

.wh-row { margin-top: 20px; }

.wh-summary { font-size: 13px; color: #606266; }

.loc-list { display: grid; gap: 5px; }
.loc-row { display: flex; align-items: center; gap: 8px; padding: 3px 0; }

.loc-name { width: 48px; flex-shrink: 0; font-size: 12px; color: #909399; }

.loc-bar-track { flex: 1; height: 12px; background: #e4e7ed; border-radius: 6px; overflow: hidden; }
.loc-bar-fill { height: 100%; border-radius: 6px; background: #409eff; transition: width .3s; min-width: 0; }
.loc-bar-fill.bar-over { background: #f56c6c; }
.loc-bar-fill.bar-full { background: #e6a23c; }

.loc-nums { font-size: 12px; white-space: nowrap; color: #606266; min-width: 140px; }
.loc-pieces { color: #909399; margin-left: 2px; }

.warn-badge { font-size: 11px; margin-left: 4px; font-weight: 600; color: #f56c6c; }
.warn-badge.warn-yellow { color: #e6a23c; }

.num-red { color: #f56c6c; font-weight: 600; }
.num-orange { color: #e6a23c; font-weight: 600; }
</style>

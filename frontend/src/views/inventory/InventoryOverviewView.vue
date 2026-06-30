<template>
  <section class="overview-page">
    <div class="page-toolbar">
      <h2>库存总览</h2>
      <div class="toolbar-right">
        <span class="refresh-time" v-if="lastRefresh">最后刷新: {{ lastRefresh }}</span>
        <el-button :loading="loading" @click="loadData">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="errorMsg" :title="errorMsg" type="error" show-icon :closable="false" style="margin-bottom:16px" />

    <el-tabs v-model="activeTab" type="border-card">
      <!-- ==================== 库位总览 ==================== -->
      <el-tab-pane label="库位总览" name="location">
        <div class="filter-row">
          <el-select v-model="locFilters.warehouseId" clearable placeholder="全部仓库" style="width: 220px" @change="resetLocCollapse">
            <el-option v-for="wh in data.warehouses" :key="wh.id" :label="`${wh.code} ${wh.name}`" :value="wh.id" />
          </el-select>
          <el-select v-model="locFilters.status" clearable placeholder="全部状态" style="width: 160px">
            <el-option v-for="s in locStatusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <el-button @click="resetLocFilters">重置</el-button>
        </div>

        <template v-if="visibleWarehouses.length === 0 && !loading">
          <el-empty description="暂无仓库数据" />
        </template>

        <div v-for="wh in visibleWarehouses" :key="wh.id" class="group-card-wrap">
          <el-card shadow="never" class="group-card">
            <template #header>
              <div class="card-header-row">
                <div class="card-header-left">
                  <strong class="group-title">{{ wh.code }} {{ wh.name }}</strong>
                  <el-tag size="small" type="info" effect="plain">{{ wh.locations.length }} 个库位</el-tag>
                  <span class="group-summary">
                    总计已用: <strong class="num-blue">{{ fmt(totalUsed(wh)) }}</strong>
                    /
                    最大容量: <strong>{{ fmt(totalMax(wh)) }}</strong>
                    ({{ usagePercent(wh) }}%)
                  </span>
                </div>
                <el-button v-if="wh.locations.length > 6" text size="small" @click="toggleCollapse('loc', wh.id)">
                  {{ collapsedLoc[wh.id] ? '展开' : '收起' }}
                </el-button>
              </div>
            </template>

            <div v-if="!collapsedLoc[wh.id]">
              <div v-if="wh.locations.length === 0" class="empty-hint">该仓库暂无库位</div>
              <div v-for="loc in wh.locations" :key="loc.id" class="bar-row">
                <span class="bar-label-name">{{ loc.code }} {{ loc.name }}</span>
                <div class="bar-container">
                  <div class="bar-track">
                    <div class="bar-fill" :class="locBarClass(loc)" :style="{ width: locBarWidth(loc) + '%' }" />
                  </div>
                  <span class="bar-nums">
                    <span :class="locNumClass(loc)">{{ loc.usedBoxes }} 箱</span>
                    / {{ fmt(loc.maxCapacity) || '未设置' }} 箱
                    <span v-if="loc.totalPieces > 0" style="color:#909399; margin-left:4px">({{ loc.totalPieces }} 件)</span>
                    <span v-if="isOver(loc)" class="warn-badge">超容</span>
                    <span v-else-if="isFull(loc)" class="warn-badge warn-full">已满</span>
                    <span v-else-if="isNearFull(loc)" class="warn-badge warn-yellow">快满</span>
                  </span>
                </div>
              </div>
            </div>
          </el-card>
        </div>
      </el-tab-pane>

      <!-- ==================== 物料充足性 ==================== -->
      <el-tab-pane label="物料充足性" name="material">
        <div class="filter-row">
          <el-select v-model="matFilters.supplierId" clearable placeholder="全部供应商" style="width: 200px">
            <el-option v-for="sup in data.suppliers" :key="sup.id" :label="`${sup.code} ${sup.name}`" :value="sup.id" />
          </el-select>
          <el-input v-model="matFilters.keyword" clearable placeholder="搜索物料编码/名称" style="width: 220px" />
          <el-select v-model="matFilters.stockStatus" clearable placeholder="全部状态" style="width: 140px">
            <el-option v-for="s in matStatusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <el-button @click="resetMatFilters">重置</el-button>
        </div>

        <template v-if="visibleSuppliers.length === 0 && !loading">
          <el-empty description="暂无供应商数据" />
        </template>

        <div v-for="sup in visibleSuppliers" :key="sup.id" class="group-card-wrap">
          <el-card shadow="never" class="group-card">
            <template #header>
              <div class="card-header-row">
                <div class="card-header-left">
                  <strong class="group-title">{{ sup.code }} {{ sup.name }}</strong>
                  <el-tag size="small" type="info" effect="plain">{{ sup.materials.length }} 种物料</el-tag>
                  <span class="group-summary">
                    短缺: <strong class="num-red">{{ countByStatus(sup, 'shortage') }}</strong>
                    超储: <strong class="num-blue">{{ countByStatus(sup, 'overstock') }}</strong>
                    正常: <strong class="num-green">{{ countByStatus(sup, 'normal') }}</strong>
                  </span>
                </div>
                <el-button v-if="sup.materials.length > 6" text size="small" @click="toggleCollapse('mat', sup.id)">
                  {{ collapsedMat[sup.id] ? '展开' : '收起' }}
                </el-button>
              </div>
            </template>

            <div v-if="!collapsedMat[sup.id]">
              <div v-for="mat in sup.materials" :key="mat.id" class="bar-row mat-bar-row">
                <span class="bar-label-name mat-label">{{ mat.name }}</span>
                <div class="bar-container">
                  <div class="range-track">
                    <div class="range-zone range-danger" :style="{ flex: lowFlex(mat) }" />
                    <div class="range-zone range-healthy" :style="{ flex: healthyFlex(mat) }" />
                    <div class="range-zone range-over" :style="{ flex: overFlex(mat) }" />
                    <div class="range-marker" :style="{ left: markerPos(mat) + '%' }" />
                  </div>
                  <span class="bar-nums">
                    可用 <span :class="matStockColor(mat)">{{ fmt(mat.availableQty) }}</span>
                    <template v-if="mat.lowStockQty > 0"> / 低储 {{ fmt(mat.lowStockQty) }}</template>
                    <template v-if="mat.highStockQty > 0"> / 高储 {{ fmt(mat.highStockQty) }}</template>
                    <span v-if="mat.shortage" class="warn-badge">短缺</span>
                    <span v-else-if="isOverstock(mat)" class="warn-badge warn-blue">超储</span>
                  </span>
                </div>
              </div>
            </div>
          </el-card>
        </div>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { http } from '../../api/http'

const loading = ref(false)
const errorMsg = ref('')
const lastRefresh = ref('')
const activeTab = ref('location')

const data = reactive({
  warehouses: [],
  suppliers: []
})

const collapsedLoc = reactive({})
const collapsedMat = reactive({})

// ---- 库位筛选 ----
const locFilters = reactive({ warehouseId: '', status: '' })
const locStatusOptions = [
  { value: 'over', label: '超容' },
  { value: 'full', label: '已满' },
  { value: 'nearFull', label: '快满' },
  { value: 'normal', label: '正常' }
]

function locStatus(loc) {
  if (isOver(loc)) return 'over'
  if (isFull(loc)) return 'full'
  if (isNearFull(loc)) return 'nearFull'
  return 'normal'
}

const visibleWarehouses = computed(() => {
  return data.warehouses
    .filter((wh) => !locFilters.warehouseId || String(wh.id) === String(locFilters.warehouseId))
    .map((wh) => ({
      ...wh,
      locations: wh.locations.filter((loc) => {
        if (!locFilters.status) return true
        return locStatus(loc) === locFilters.status
      })
    }))
    .filter((wh) => wh.locations.length > 0)
})

function resetLocFilters() {
  locFilters.warehouseId = ''
  locFilters.status = ''
}
function resetLocCollapse() { Object.keys(collapsedLoc).forEach((k) => delete collapsedLoc[k]) }

// ---- 物料筛选 ----
const matFilters = reactive({ supplierId: '', keyword: '', stockStatus: '' })
const matStatusOptions = [
  { value: 'shortage', label: '短缺' },
  { value: 'overstock', label: '超储' },
  { value: 'normal', label: '正常' }
]

function isOverstock(mat) {
  const hi = Number(mat.highStockQty) || 0
  return hi > 0 && (Number(mat.availableQty) || 0) >= hi
}

function matStockStatus(mat) {
  if (mat.shortage) return 'shortage'
  if (isOverstock(mat)) return 'overstock'
  return 'normal'
}

function matMatchesKeyword(mat, sup) {
  if (!matFilters.keyword) return true
  const kw = matFilters.keyword.toLowerCase()
  return (mat.code || '').toLowerCase().includes(kw)
      || (mat.name || '').toLowerCase().includes(kw)
      || (sup.code || '').toLowerCase().includes(kw)
      || (sup.name || '').toLowerCase().includes(kw)
}

const visibleSuppliers = computed(() => {
  return data.suppliers
    .filter((sup) => !matFilters.supplierId || String(sup.id) === String(matFilters.supplierId))
    .map((sup) => ({
      ...sup,
      materials: [...sup.materials]
        .filter((mat) => {
          if (!matMatchesKeyword(mat, sup)) return false
          if (!matFilters.stockStatus) return true
          return matStockStatus(mat) === matFilters.stockStatus
        })
        .sort((a, b) => Number(b.shortage) - Number(a.shortage))
    }))
    .filter((sup) => sup.materials.length > 0)
    .sort((a, b) => {
      const sa = a.materials.filter((m) => m.shortage).length
      const sb = b.materials.filter((m) => m.shortage).length
      return sb - sa
    })
})

function resetMatFilters() {
  matFilters.supplierId = ''
  matFilters.keyword = ''
  matFilters.stockStatus = ''
}

// ---- 区间条 ----
function rangeMax(mat) {
  const hi = Number(mat.highStockQty) || 0
  const avail = Number(mat.availableQty) || 0
  if (hi > 0) return Math.max(hi, avail)
  const lo = Number(mat.lowStockQty) || 0
  return Math.max(lo * 2, avail, 100)
}

function lowFlex(mat) {
  const max = rangeMax(mat)
  if (max <= 0) return 0
  return Math.round(((Number(mat.lowStockQty) || 0) / max) * 100) || 0
}

function healthyFlex(mat) {
  const max = rangeMax(mat)
  if (max <= 0) return 100
  const lo = Math.min(Number(mat.lowStockQty) || 0, max)
  const hi = Math.min(Number(mat.highStockQty) || 0, max)
  if (hi <= lo) return 0
  return Math.round(((hi - lo) / max) * 100) || 0
}

function overFlex(mat) {
  const rest = 100 - lowFlex(mat) - healthyFlex(mat)
  return Math.max(0, rest)
}

function markerPos(mat) {
  const max = rangeMax(mat)
  if (max <= 0) return 0
  return Math.min(100, Math.round(((Number(mat.availableQty) || 0) / max) * 100))
}

function matStockColor(mat) {
  if (mat.shortage) return 'num-red'
  if (isOverstock(mat)) return 'num-blue'
  return 'num-green'
}

// ---- 统计 ----
function countByStatus(sup, status) {
  return sup.materials.filter((m) => matStockStatus(m) === status).length
}

// ---- 通用 ----
function toggleCollapse(type, id) {
  const map = type === 'loc' ? collapsedLoc : collapsedMat
  map[id] = !map[id]
}

async function loadData() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await http.get('/inventory/overview')
    data.warehouses = res.data.warehouses || []
    data.suppliers = res.data.suppliers || []
    lastRefresh.value = new Date().toLocaleString('zh-CN')
  } catch (e) {
    errorMsg.value = e.response?.data?.message || '加载库存总览失败'
  } finally {
    loading.value = false
  }
}

function fmt(v) { return v != null ? String(v) : '0' }

// ---- 库位进度条 ----
function locBarWidth(loc) {
  const u = Number(loc.usedBoxes) || 0
  const m = Number(loc.maxCapacity) || 0
  if (m <= 0 && u <= 0) return 0
  const limit = Math.max(m, u)
  if (limit <= 0) return 0
  return Math.round((u / limit) * 100)
}

function locBarClass(loc) {
  if (isOver(loc)) return 'bar-over'
  if (isFull(loc)) return 'bar-full'
  return ''
}

function locNumClass(loc) {
  if (isOver(loc)) return 'num-red'
  if (isFull(loc)) return 'num-orange'
  return ''
}

function isOver(loc) {
  const u = Number(loc.usedBoxes) || 0
  const m = Number(loc.maxCapacity) || 0
  return m > 0 && u > m
}

function isFull(loc) {
  const u = Number(loc.usedBoxes) || 0
  const m = Number(loc.maxCapacity) || 0
  return m > 0 && u >= m
}

function isNearFull(loc) {
  const u = Number(loc.usedBoxes) || 0
  const m = Number(loc.maxCapacity) || 0
  return m > 0 && u >= m * 0.85 && u < m
}

function totalUsed(wh) { return wh.locations.reduce((s, l) => s + (Number(l.usedBoxes) || 0), 0) }
function totalMax(wh) { return wh.locations.reduce((s, l) => s + (Number(l.maxCapacity) || 0), 0) }

function usagePercent(wh) {
  const max = totalMax(wh)
  if (max <= 0) return 0
  return Math.round((totalUsed(wh) / max) * 100)
}

loadData()
</script>

<style scoped>
.overview-page { padding: 16px 20px; }

.page-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 12px;
}
.page-toolbar h2 { margin: 0; }
.toolbar-right { display: flex; align-items: center; gap: 12px; }
.refresh-time { font-size: 13px; color: #909399; }

.filter-row {
  display: flex; align-items: center; gap: 12px;
  margin-bottom: 14px; flex-wrap: wrap;
}

.group-card-wrap { margin-bottom: 16px; }
.group-card { border-left: 4px solid #409eff; }

.card-header-row {
  display: flex; align-items: center; justify-content: space-between;
  flex-wrap: wrap; gap: 8px;
}
.card-header-left { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.group-title { font-size: 15px; }
.group-summary { font-size: 13px; color: #606266; }

/* ---- 库位条 ---- */
.bar-row {
  display: flex; align-items: center; gap: 10px;
  padding: 6px 0; border-bottom: 1px solid #f2f3f5;
}
.bar-row:last-child { border-bottom: none; }

.bar-label-name { width: 180px; flex-shrink: 0; font-size: 13px; color: #303133; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.bar-container { flex: 1; display: flex; align-items: center; gap: 12px; }

.bar-track {
  flex: 1; height: 20px; background: #e4e7ed; border-radius: 10px;
  overflow: hidden; position: relative;
}

.bar-fill {
  height: 100%; border-radius: 10px; background: #409eff;
  transition: width .3s ease; min-width: 0;
}
.bar-fill.bar-over { background: #f56c6c; }
.bar-fill.bar-full { background: #e6a23c; }

.bar-nums { font-size: 13px; white-space: nowrap; color: #606266; min-width: 180px; }

/* ---- 物料区间条 ---- */
.mat-bar-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.mat-label { width: 180px; }

.range-track {
  flex: 1; height: 16px; background: #e4e7ed; border-radius: 8px;
  position: relative; display: flex; overflow: hidden; min-width: 100px;
}

.range-zone { height: 100%; transition: flex .3s; }
.range-danger { background: #f56c6c; }
.range-healthy { background: #67c23a; }
.range-over { background: #409eff; }

.range-marker {
  position: absolute; top: -2px; bottom: -2px;
  width: 4px; background: #303133; border-radius: 2px;
  transition: left .3s; z-index: 2;
}

/* ---- 颜色/徽章 ---- */
.num-red { color: #f56c6c; font-weight: 600; }
.num-orange { color: #e6a23c; font-weight: 600; }
.num-blue { color: #409eff; font-weight: 600; }
.num-green { color: #67c23a; font-weight: 600; }

.warn-badge { font-size: 12px; margin-left: 6px; font-weight: 600; }
.warn-badge { color: #f56c6c; }
.warn-badge.warn-yellow { color: #e6a23c; }
.warn-badge.warn-full { color: #e6a23c; }
.warn-badge.warn-blue { color: #409eff; }

.empty-hint { text-align: center; color: #999; padding: 20px 0; font-size: 14px; }
</style>

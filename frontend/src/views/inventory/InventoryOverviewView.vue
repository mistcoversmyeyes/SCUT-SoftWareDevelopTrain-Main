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

    <div v-loading="loading">

      <!-- 第一部分：库位总览 -->
      <el-divider>库位总览 — 每个库位的容量使用情况</el-divider>

      <template v-if="data.warehouses.length === 0 && !loading">
        <el-empty description="暂无仓库数据" />
      </template>

      <div v-for="wh in data.warehouses" :key="wh.id" class="group-card-wrap">
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
              <el-button
                v-if="wh.locations.length > 6"
                text
                size="small"
                @click="toggleCollapse('loc', wh.id)"
              >
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
                  <div
                    class="bar-fill"
                    :class="{ 'bar-over': isOver(loc.usedBoxes, loc.maxCapacity), 'bar-full': !isOver(loc.usedBoxes, loc.maxCapacity) && isFull(loc.usedBoxes, loc.maxCapacity) }"
                    :style="{ width: barWidth(loc.usedBoxes, loc.maxCapacity) + '%' }"
                  />
                </div>
                <span class="bar-nums">
                  <span :class="isOver(loc.usedBoxes, loc.maxCapacity) ? 'num-red' : isFull(loc.usedBoxes, loc.maxCapacity) ? 'num-orange' : ''">
                    {{ loc.usedBoxes }} 箱
                  </span>
                  / {{ fmt(loc.maxCapacity) || '未设置' }} 箱
                  <span v-if="loc.totalPieces > 0" style="color:#909399; margin-left:4px">({{ loc.totalPieces }} 件)</span>
                  <span v-if="isOver(loc.usedBoxes, loc.maxCapacity)" class="warn-badge">超容</span>
                  <span v-else-if="isFull(loc.usedBoxes, loc.maxCapacity)" class="warn-badge warn-full">已满</span>
                  <span v-else-if="isNearFull(loc.usedBoxes, loc.maxCapacity)" class="warn-badge warn-yellow">快满</span>
                </span>
              </div>
            </div>
          </div>
        </el-card>
      </div>

      <!-- 第二部分：物料充足性 -->
      <el-divider>物料充足性 — 每个物料的库存是否达标</el-divider>

      <template v-if="data.suppliers.length === 0 && !loading">
        <el-empty description="暂无供应商数据" />
      </template>

      <div v-for="sup in data.suppliers" :key="sup.id" class="group-card-wrap">
        <el-card shadow="never" class="group-card">
          <template #header>
            <div class="card-header-row">
              <div class="card-header-left">
                <strong class="group-title">{{ sup.code }} {{ sup.name }}</strong>
                <el-tag size="small" type="info" effect="plain">{{ sup.materials.length }} 种物料</el-tag>
                <span class="group-summary">
                  充足: <strong class="num-green">{{ countSufficient(sup) }}</strong>
                  不足: <strong class="num-red">{{ countInsufficient(sup) }}</strong>
                </span>
              </div>
              <el-button
                v-if="sup.materials.length > 6"
                text
                size="small"
                @click="toggleCollapse('mat', sup.id)"
              >
                {{ collapsedMat[sup.id] ? '展开' : '收起' }}
              </el-button>
            </div>
          </template>

          <div v-if="!collapsedMat[sup.id]">
            <div v-for="mat in sup.materials" :key="mat.id" class="bar-row">
              <span class="bar-label-name">{{ mat.code }} {{ mat.name }}</span>
              <div class="bar-container">
                <div class="bar-track">
                  <div
                    class="bar-fill"
                    :class="{ 'bar-under': isUnder(mat.currentQty, mat.highStockQty) }"
                    :style="{ width: barWidth(mat.currentQty, mat.highStockQty) + '%' }"
                  />
                </div>
                <span class="bar-nums">
                  <span :class="stockColorClass(mat)">{{ fmt(mat.currentQty) }}</span>
                  / {{ fmt(mat.highStockQty) || '未设置' }}
                  <span v-if="isUnder(mat.currentQty, mat.highStockQty)" class="warn-badge">不足</span>
                </span>
              </div>
            </div>
          </div>
        </el-card>
      </div>

    </div>

  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { http } from '../../api/http'

const loading = ref(false)
const errorMsg = ref('')
const lastRefresh = ref('')

const data = reactive({
  warehouses: [],
  suppliers: []
})

const collapsedLoc = reactive({})
const collapsedMat = reactive({})

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

function barWidth(used, max) {
  const u = Number(used) || 0
  const m = Number(max) || 0
  if (m <= 0 && u <= 0) return 0
  const limit = Math.max(m, u)
  if (limit <= 0) return 0
  return Math.round((u / limit) * 100)
}

function isOver(used, max) {
  const u = Number(used) || 0
  const m = Number(max) || 0
  return m > 0 && u > m
}

function isFull(used, max) {
  const u = Number(used) || 0
  const m = Number(max) || 0
  return m > 0 && u >= m
}

function isNearFull(used, max) {
  const u = Number(used) || 0
  const m = Number(max) || 0
  return m > 0 && u >= m * 0.85 && u < m
}

function isUnder(current, baseline) {
  const c = Number(current) || 0
  const b = Number(baseline) || 0
  return b > 0 && c < b
}

function stockColorClass(mat) {
  const c = Number(mat.currentQty) || 0
  const b = Number(mat.highStockQty) || 0
  if (b <= 0) return ''
  if (c < b) return 'num-red'
  return 'num-green'
}

function totalUsed(wh) { return wh.locations.reduce((s, l) => s + (Number(l.usedBoxes) || 0), 0) }
function totalMax(wh) { return wh.locations.reduce((s, l) => s + (Number(l.maxCapacity) || 0), 0) }

function usagePercent(wh) {
  const max = totalMax(wh)
  if (max <= 0) return 0
  return Math.round((totalUsed(wh) / max) * 100)
}

function countSufficient(sup) {
  return sup.materials.filter(m => (Number(m.highStockQty) || 0) <= 0 || (Number(m.currentQty) || 0) >= (Number(m.highStockQty) || 0)).length
}

function countInsufficient(sup) {
  return sup.materials.filter(m => (Number(m.highStockQty) || 0) > 0 && (Number(m.currentQty) || 0) < (Number(m.highStockQty) || 0)).length
}

loadData()
</script>

<style scoped>
.overview-page { padding: 16px 20px; }

.page-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 8px;
}
.page-toolbar h2 { margin: 0; }
.toolbar-right { display: flex; align-items: center; gap: 12px; }
.refresh-time { font-size: 13px; color: #909399; }

.group-card-wrap { margin-bottom: 16px; }
.group-card { border-left: 4px solid #409eff; }

.card-header-row {
  display: flex; align-items: center; justify-content: space-between;
  flex-wrap: wrap; gap: 8px;
}
.card-header-left { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.group-title { font-size: 15px; }
.group-summary { font-size: 13px; color: #606266; }

.bar-row {
  display: flex; align-items: center; gap: 10px;
  padding: 6px 0; border-bottom: 1px solid #f2f3f5;
}
.bar-row:last-child { border-bottom: none; }

.bar-label-name { width: 180px; flex-shrink: 0; font-size: 13px; color: #303133; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.bar-container {
  flex: 1; display: flex; align-items: center; gap: 12px;
}

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
.bar-fill.bar-under { background: #e6a23c; }

.bar-nums { font-size: 13px; white-space: nowrap; color: #606266; min-width: 150px; }
.bar-nums .num-red { color: #f56c6c; font-weight: 600; }
.bar-nums .num-orange { color: #e6a23c; font-weight: 600; }

.edit-btn { flex-shrink: 0; }

.empty-hint { text-align: center; color: #999; padding: 20px 0; font-size: 14px; }

.warn-badge { font-size: 12px; margin-left: 6px; color: #f56c6c; font-weight: 600; }
.warn-badge.warn-yellow { color: #e6a23c; }
.warn-badge.warn-full { color: #e6a23c; }

.num-blue { color: #409eff; }
.num-green { color: #67c23a; }
.num-red { color: #f56c6c; }
.num-orange { color: #e6a23c; }
</style>

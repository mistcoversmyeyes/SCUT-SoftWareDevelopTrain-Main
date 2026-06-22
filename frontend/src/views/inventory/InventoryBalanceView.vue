<template>
  <section class="module-shell">
    <el-card>
      <template #header>
        <h2>当前库存</h2>
      </template>

      <el-alert
        title="数量口径：可用=RECEIVED 且无 ACTIVE 占用；出库锁定=LOCKED；封存=SEALED；手锁=RECEIVED + MANUAL_LOCK；零头=剩余数量小于原看板数量的在库/占用库存。"
        type="info"
        :closable="false"
        show-icon
      />

      <el-alert
        v-if="warningReadiness.reason"
        :title="`规则型预警数据状态：${warningReadiness.label}`"
        :description="warningReadiness.reason"
        :type="warningReadiness.tone"
        :closable="false"
        show-icon
        class="status-alert"
      />

      <el-alert v-if="fetchError" type="error" :title="fetchError" :closable="false" show-icon class="status-alert" />

      <el-form :model="filters" inline class="query-form">
        <el-form-item label="物料">
          <el-select
            v-model="filters.materialCode"
            placeholder="全部"
            clearable
            filterable
            style="width: 220px"
          >
            <el-option
              v-for="material in materialOptions"
              :key="material.code"
              :value="material.code"
              :label="material.code"
            >
              {{ material.code }} {{ material.name }}
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="仓库">
          <el-select
            v-model="filters.warehouseCode"
            placeholder="全部"
            clearable
            filterable
            style="width: 220px"
          >
            <el-option
              v-for="warehouse in warehouseOptions"
              :key="warehouse.code"
              :value="warehouse.code"
              :label="warehouse.code"
            >
              {{ warehouse.code }} {{ warehouse.name }}
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="库位">
          <el-select
            v-model="filters.locationCode"
            placeholder="全部"
            clearable
            filterable
            style="width: 240px"
          >
            <el-option
              v-for="location in locationOptions"
              :key="`${location.warehouseId}-${location.code}`"
              :value="location.code"
              :label="location.code"
            >
              {{ location.code }} {{ location.name }}
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" @click="queryBalances">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
        <el-form-item>
          <el-switch v-model="autoRefresh" active-text="自动刷新" inactive-text="手动" @change="toggleAutoRefresh" />
        </el-form-item>
      </el-form>

      <div class="summary-grid">
        <div class="summary-card">
          <span class="summary-label">库存行</span>
          <strong>{{ monitorRows.length }}</strong>
        </div>
        <div class="summary-card">
          <span class="summary-label">可用数量</span>
          <strong>{{ formatQty(summary.availableQty) }}</strong>
        </div>
        <div class="summary-card">
          <span class="summary-label">锁定/封存/手锁</span>
          <strong>{{ formatQty(summary.lockedQty) }} / {{ formatQty(summary.sealedQty) }} / {{ formatQty(summary.manualLockedQty) }}</strong>
        </div>
        <div class="summary-card">
          <span class="summary-label">缺货/呆滞</span>
          <strong>{{ summary.shortageCount }} / {{ summary.stagnationCount }}</strong>
        </div>
      </div>

      <el-table :data="paginatedRows" border stripe v-loading="loading" style="margin-top: 12px;">
        <el-table-column prop="materialCode" label="物料编码" width="140" />
        <el-table-column prop="materialName" label="物料名称" min-width="180" />
        <el-table-column label="供应商" min-width="160">
          <template #default="{ row }">
            <span v-if="row.supplierCode || row.supplierName">{{ row.supplierCode }} {{ row.supplierName }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="warehouseCode" label="仓库编码" width="120" />
        <el-table-column prop="locationCode" label="库位编码" width="120" />
        <el-table-column label="业务数量" min-width="520">
          <el-table-column prop="onHandQty" label="账面" width="90" align="right">
            <template #default="{ row }">{{ formatQty(row.onHandQty) }}</template>
          </el-table-column>
          <el-table-column prop="availableQty" label="可用" width="90" align="right">
            <template #default="{ row }">{{ formatQty(row.availableQty) }}</template>
          </el-table-column>
          <el-table-column prop="outboundLockedQty" label="出库锁定" width="100" align="right">
            <template #default="{ row }">{{ formatQty(row.outboundLockedQty) }}</template>
          </el-table-column>
          <el-table-column prop="sealedQty" label="封存" width="90" align="right">
            <template #default="{ row }">{{ formatQty(row.sealedQty) }}</template>
          </el-table-column>
          <el-table-column prop="manualLockedQty" label="手锁" width="90" align="right">
            <template #default="{ row }">{{ formatQty(row.manualLockedQty) }}</template>
          </el-table-column>
          <el-table-column prop="looseQty" label="零头" width="90" align="right">
            <template #default="{ row }">{{ formatQty(row.looseQty) }}</template>
          </el-table-column>
        </el-table-column>
        <el-table-column label="库存状态" width="110">
          <template #default="{ row }">
            <el-tag :type="tagType(row.stockState.tone)">{{ row.stockState.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="缺货风险" width="130">
          <template #default="{ row }">
            <el-tag :type="tagType(row.shortageRisk.tone)">{{ row.shortageRisk.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="呆滞风险" width="130">
          <template #default="{ row }">
            <el-tag :type="tagType(row.stagnationRisk.tone)">{{ row.stagnationRisk.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="规则说明" min-width="300">
          <template #default="{ row }">
            <div class="reason-cell">
              <div>{{ row.shortageRisk.reason }}</div>
              <div class="sub-reason">{{ row.stagnationRisk.reason }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="180">
          <template #default="scope">
            {{ formatDateTime(scope.row.updatedAt) }}
          </template>
        </el-table-column>
      </el-table>

      <div v-if="monitorRows.length > pageSize" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 15, 20, 50]"
          :total="monitorRows.length"
          layout="total, sizes, prev, pager, next, jumper"
          background
        />
      </div>
    </el-card>
  </section>
</template>

<script setup>
import { computed, onBeforeMount, onBeforeUnmount, reactive, ref } from 'vue'
import { fetchInventoryBalances } from '../../api/inventory'
import { fetchMasterDataOptions, fetchMaterials } from '../../api/masterData'
import { fetchInventoryFlowImportBatches, fetchInventoryFlowImportRecords } from '../../api/aiWarningImport'
import { buildInventoryMonitorRows, buildWarningDataReadiness } from '../../utils/monitoring'

const AUTO_REFRESH_INTERVAL = 30000

const filters = reactive({
  materialCode: '',
  warehouseCode: '',
  locationCode: ''
})

const loading = ref(false)
const fetchError = ref('')
const rawBalances = ref([])
const monitorRows = ref([])
const materialOptions = ref([])
const warehouseOptions = ref([])
const locationOptions = ref([])
const materials = ref([])
const warningRecords = ref([])
const warningReadiness = ref({ code: 'NOT_READY', label: '数据未准备', tone: 'warning', reason: '' })

const currentPage = ref(1)
const pageSize = ref(15)
const autoRefresh = ref(false)
let refreshTimer = null

const paginatedRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return monitorRows.value.slice(start, start + pageSize.value)
})

const summary = computed(() => monitorRows.value.reduce((acc, row) => {
  acc.availableQty += Number(row.availableQty) || 0
  acc.lockedQty += Number(row.outboundLockedQty) || 0
  acc.sealedQty += Number(row.sealedQty) || 0
  acc.manualLockedQty += Number(row.manualLockedQty) || 0
  if (['WATCH', 'HIGH', 'CRITICAL'].includes(row.shortageRisk.code)) {
    acc.shortageCount += 1
  }
  if (['WATCH', 'HIGH'].includes(row.stagnationRisk.code)) {
    acc.stagnationCount += 1
  }
  return acc
}, {
  availableQty: 0,
  lockedQty: 0,
  sealedQty: 0,
  manualLockedQty: 0,
  shortageCount: 0,
  stagnationCount: 0
}))

function pick(query) {
  return Object.fromEntries(Object.entries(query).filter(([, value]) => value))
}

function rebuildRows() {
  monitorRows.value = buildInventoryMonitorRows({
    balances: rawBalances.value,
    materials: materials.value,
    flowRecords: warningRecords.value,
    today: new Date()
  })
}

async function loadWarningData() {
  try {
    const batches = await fetchInventoryFlowImportBatches()
    const latestBatchId = batches[0]?.batchId
    warningRecords.value = latestBatchId ? await fetchInventoryFlowImportRecords({ batchId: latestBatchId }) : []
    warningReadiness.value = buildWarningDataReadiness(batches, warningRecords.value)
  } catch {
    warningRecords.value = []
    warningReadiness.value = buildWarningDataReadiness([], [])
  }
}

async function queryBalances() {
  loading.value = true
  fetchError.value = ''
  try {
    const [balances] = await Promise.all([
      fetchInventoryBalances(pick(filters)),
      loadWarningData()
    ])
    rawBalances.value = balances
    rebuildRows()
    currentPage.value = 1
  } catch (error) {
    fetchError.value = error.response?.data?.message || '库存查询失败'
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.materialCode = ''
  filters.warehouseCode = ''
  filters.locationCode = ''
  queryBalances()
}

function formatDateTime(value) {
  if (!value) return '-'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleString('zh-CN')
}

function formatQty(value) {
  if (value === null || value === undefined) {
    return '0'
  }
  const num = Number(value)
  if (Number.isNaN(num)) {
    return value
  }
  return String(num)
}

function tagType(tone) {
  if (tone === 'danger') return 'danger'
  if (tone === 'warning') return 'warning'
  if (tone === 'success') return 'success'
  return 'info'
}

async function loadMasterData() {
  try {
    const [options, materialList] = await Promise.all([
      fetchMasterDataOptions(),
      fetchMaterials()
    ])
    materialOptions.value = options.materials
    warehouseOptions.value = options.warehouses
    locationOptions.value = options.locations
    materials.value = materialList
    rebuildRows()
  } catch {
    // ignore and keep manual filters working
  }
}

function toggleAutoRefresh(val) {
  if (val) {
    refreshTimer = setInterval(queryBalances, AUTO_REFRESH_INTERVAL)
  } else {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

onBeforeMount(async () => {
  await loadMasterData()
  await queryBalances()
})

onBeforeUnmount(() => {
  clearInterval(refreshTimer)
})
</script>

<style scoped>
.module-shell {
  min-height: 360px;
}

.query-form {
  margin: 16px 0 12px;
}

.status-alert {
  margin-top: 12px;
}

h2 {
  margin: 0;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(160px, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.summary-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.summary-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.reason-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  line-height: 1.4;
}

.sub-reason {
  color: var(--el-text-color-secondary);
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(160px, 1fr));
  }
}
</style>

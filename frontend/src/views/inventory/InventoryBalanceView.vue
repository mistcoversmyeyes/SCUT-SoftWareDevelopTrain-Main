<template>
  <section class="module-shell">
    <el-card>
      <template #header>
        <h2>当前库存</h2>
      </template>

      <el-alert v-if="fetchError" type="error" :title="fetchError" :closable="false" show-icon />

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

      <el-table :data="paginatedBalances" border stripe v-loading="loading" style="margin-top: 12px;">
        <el-table-column prop="materialCode" label="物料编码" width="140" />
        <el-table-column prop="materialName" label="物料名称" min-width="220" />
        <el-table-column prop="warehouseCode" label="仓库编码" width="140" />
        <el-table-column prop="warehouseName" label="仓库名称" min-width="190" />
        <el-table-column prop="locationCode" label="库位编码" width="130" />
        <el-table-column prop="locationName" label="库位名称" min-width="170" />
        <el-table-column label="库存详情" width="200">
          <template #default="scope">
            {{ scope.row.usedBoxes ?? 0 }} 箱 ({{ scope.row.totalPieces ?? 0 }} 件) / {{ scope.row.maxCapacity ?? '-' }} 箱
          </template>
        </el-table-column>
        <el-table-column label="业务数量">
          <el-table-column prop="onHandQty" label="账面库存" width="100" align="right">
            <template #default="{ row }">{{ formatQty(row.onHandQty) }}</template>
          </el-table-column>
          <el-table-column prop="availableQty" label="可用" width="100" align="right">
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
        <el-table-column prop="updatedAt" label="更新时间" min-width="180">
          <template #default="scope">
            {{ formatDateTime(scope.row.updatedAt) }}
          </template>
        </el-table-column>
      </el-table>

      <div v-if="balances.length > pageSize" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 15, 20, 50]"
          :total="balances.length"
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
import { fetchMasterDataOptions } from '../../api/masterData'

const AUTO_REFRESH_INTERVAL = 30000

const filters = reactive({
  materialCode: '',
  warehouseCode: '',
  locationCode: ''
})

const loading = ref(false)
const fetchError = ref('')
const balances = ref([])
const materialOptions = ref([])
const warehouseOptions = ref([])
const locationOptions = ref([])

const currentPage = ref(1)
const pageSize = ref(15)
const autoRefresh = ref(false)
let refreshTimer = null

const paginatedBalances = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return balances.value.slice(start, start + pageSize.value)
})

function pick(query) {
  return Object.fromEntries(
    Object.entries(query).filter(([, value]) => value)
  )
}

async function queryBalances() {
  loading.value = true
  fetchError.value = ''
  try {
    balances.value = await fetchInventoryBalances(pick(filters))
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

async function loadMasterData() {
  try {
    const data = await fetchMasterDataOptions()
    materialOptions.value = data.materials
    warehouseOptions.value = data.warehouses
    locationOptions.value = data.locations
  } catch {
    // keep filter options empty and keep page functional
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

onBeforeMount(() => {
  loadMasterData()
  queryBalances()
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
  margin-bottom: 12px;
}

h2 {
  margin: 0;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>

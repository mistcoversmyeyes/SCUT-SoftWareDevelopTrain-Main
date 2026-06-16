<template>
  <section class="module-shell">
    <el-card>
      <template #header>
        <h2>库存追溯</h2>
      </template>

      <el-alert v-if="fetchError" type="error" :title="fetchError" :closable="false" show-icon />

      <el-form :model="filters" inline class="query-form">
        <el-form-item label="物料">
          <el-select
            v-model="filters.materialCode"
            placeholder="全部"
            clearable
            filterable
            style="width: 200px"
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
            style="width: 200px"
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
            style="width: 220px"
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

        <el-form-item label="入库单号">
          <el-input v-model="filters.inboundNo" clearable placeholder="请输入入库单号" />
        </el-form-item>

        <el-form-item label="看板码">
          <el-input v-model="filters.kanbanCode" clearable placeholder="请输入看板码" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" @click="queryMovements">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="paginatedMovements" border stripe v-loading="loading" style="margin-top: 12px;" :row-class-name="rowClassName">
        <el-table-column prop="movementNo" label="流水号" min-width="170" />
        <el-table-column prop="movementType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.movementType === 'INBOUND_RECEIVE' ? 'success' : 'warning'" size="small">
              {{ movementTypeLabel(row.movementType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="materialCode" label="物料编码" width="130" />
        <el-table-column prop="materialName" label="物料名称" min-width="200" />
        <el-table-column prop="warehouseCode" label="仓库" width="120" />
        <el-table-column prop="plannedLocationCode" label="计划库位" width="120">
          <template #default="{ row }">
            <span v-if="row.movementType === 'INBOUND_RECEIVE'">{{ row.plannedLocationCode || '—' }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="locationCode" label="实际库位" width="120" />
        <el-table-column label="数量" width="120" align="right">
          <template #default="{ row }">
            <span :style="{ color: row.movementType === 'OUTBOUND_PICK' ? '#e6a23c' : '#67c23a' }">
              {{ row.movementType === 'OUTBOUND_PICK' ? '-' : '' }}{{ formatQty(row.qty) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="kanbanCode" label="看板码" width="160" />
        <el-table-column prop="inboundNo" label="入库单号" width="140" />
        <el-table-column prop="occurredAt" label="发生时间" min-width="180">
          <template #default="scope">
            {{ formatDateTime(scope.row.occurredAt) }}
          </template>
        </el-table-column>
      </el-table>
      <div v-if="movements.length > pageSize" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 15, 20, 50]"
          :total="movements.length"
          layout="total, sizes, prev, pager, next, jumper"
          background
        />
      </div>
    </el-card>
  </section>
</template>

<script setup>
import { computed, onBeforeMount, reactive, ref } from 'vue'
import { fetchInventoryMovements } from '../../api/inventory'
import { fetchMasterDataOptions } from '../../api/masterData'

const filters = reactive({
  materialCode: '',
  warehouseCode: '',
  locationCode: '',
  inboundNo: '',
  kanbanCode: ''
})

const loading = ref(false)
const fetchError = ref('')
const movements = ref([])
const materialOptions = ref([])
const warehouseOptions = ref([])
const locationOptions = ref([])

const currentPage = ref(1)
const pageSize = ref(10)

const paginatedMovements = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return movements.value.slice(start, start + pageSize.value)
})

function pick(query) {
  return Object.fromEntries(
    Object.entries(query).filter(([, value]) => value)
  )
}

async function queryMovements() {
  loading.value = true
  fetchError.value = ''
  try {
    movements.value = await fetchInventoryMovements(pick(filters))
    currentPage.value = 1
  } catch (error) {
    fetchError.value = error.response?.data?.message || '库存追溯查询失败'
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.materialCode = ''
  filters.warehouseCode = ''
  filters.locationCode = ''
  filters.inboundNo = ''
  filters.kanbanCode = ''
  queryMovements()
}

function movementTypeLabel(type) {
  const map = { INBOUND_RECEIVE: '入库', OUTBOUND_PICK: '出库' }
  return map[type] || type || '-'
}

function formatQty(value) {
  if (value === null || value === undefined) return '0'
  const num = Number(value)
  if (Number.isNaN(num)) return value
  return String(num)
}

function formatDateTime(value) {
  if (!value) return '-'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleString('zh-CN')
}

function rowClassName({ row }) {
  if (row.movementType === 'INBOUND_RECEIVE' && row.plannedLocationCode && row.locationCode && row.plannedLocationCode !== row.locationCode) {
    return 'warning-row'
  }
  return ''
}

async function loadMasterData() {
  try {
    const data = await fetchMasterDataOptions()
    materialOptions.value = data.materials
    warehouseOptions.value = data.warehouses
    locationOptions.value = data.locations
  } catch {
    // ignore and keep manual input working
  }
}

onBeforeMount(() => {
  loadMasterData()
  queryMovements()
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

:deep(.warning-row) {
  background-color: #fdf6ec;
}

:deep(.warning-row:hover > td) {
  background-color: #fdf6ec !important;
}
</style>

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

      <el-table :data="movements" border stripe v-loading="loading" style="margin-top: 12px;">
        <el-table-column prop="movementNo" label="流水号" min-width="170" />
        <el-table-column prop="materialCode" label="物料编码" width="130" />
        <el-table-column prop="materialName" label="物料名称" min-width="200" />
        <el-table-column prop="warehouseCode" label="仓库" width="120" />
        <el-table-column prop="locationCode" label="库位" width="120" />
        <el-table-column prop="qty" label="数量" width="120" />
        <el-table-column prop="kanbanCode" label="看板码" width="160" />
        <el-table-column prop="inboundNo" label="入库单号" width="140" />
        <el-table-column prop="occurredAt" label="发生时间" min-width="180">
          <template #default="scope">
            {{ formatDateTime(scope.row.occurredAt) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup>
import { onBeforeMount, reactive, ref } from 'vue'
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

function formatDateTime(value) {
  if (!value) return '-'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleString('zh-CN')
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
</style>

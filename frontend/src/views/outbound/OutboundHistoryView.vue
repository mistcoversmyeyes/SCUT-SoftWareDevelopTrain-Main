<template>
  <section class="outbound-history-page">
    <el-card>
      <template #header>
        <h2>出库历史</h2>
      </template>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable multiple collapse-tags style="width: 150px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="单号">
          <el-input v-model="query.outboundNo" placeholder="支持模糊输入" clearable />
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="query.supplierKeyword" placeholder="编码 / 名称" clearable />
        </el-form-item>
        <el-form-item label="物料">
          <el-select v-model="query.materialCode" placeholder="全部" clearable filterable style="width: 220px">
            <el-option v-for="item in materialOptions" :key="item.id" :label="item.materialCode" :value="item.materialCode">
              {{ item.materialCode }} {{ item.materialName }}
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="强制出库">
          <el-switch v-model="query.forceOutboundOnly" active-text="仅看强制" inactive-text="全部" />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadOrders">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        title="当前页复用 outbound-orders + 强制出库审计日志，补成可演示的历史查询页，不新增后端查询面。"
        type="info"
        :closable="false"
        show-icon
      />

      <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon class="status-alert" />

      <el-table v-loading="loading" :data="paginatedOrders" border stripe size="small" class="order-table">
        <el-table-column prop="outboundNo" label="出库单号" min-width="160" />
        <el-table-column label="供应商" min-width="170">
          <template #default="{ row }">{{ row.supplier?.code }} {{ row.supplier?.name }}</template>
        </el-table-column>
        <el-table-column prop="materialSummary" label="物料摘要" min-width="220" />
        <el-table-column prop="purpose" label="出库用途" width="120">
          <template #default="{ row }">{{ purposeLabel(row.purpose) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'danger'" effect="light">
              {{ row.status === 'COMPLETED' ? '已完成' : '已取消' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="强制出库" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.hasForceOutbound" type="warning" effect="light">涉及强制</el-tag>
            <span v-else>否</span>
          </template>
        </el-table-column>
        <el-table-column prop="lineCount" label="明细行数" width="100" />
        <el-table-column prop="plannedQty" label="计划数量" width="120" align="right">
          <template #default="{ row }">{{ formatQty(row.plannedQty) }}</template>
        </el-table-column>
        <el-table-column prop="pickedQty" label="已发数量" width="120" align="right">
          <template #default="{ row }">{{ formatQty(row.pickedQty) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="completedAt" label="完成时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.completedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" text @click="handleView(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="filteredOrders.length > pageSize" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 15, 20, 50]"
          :total="filteredOrders.length"
          layout="total, sizes, prev, pager, next, jumper"
          background
        />
      </div>

      <el-empty v-if="!loading && !filteredOrders.length" description="暂无出库历史" />
    </el-card>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchMaterials } from '../../api/masterData'
import { fetchForceLogs, fetchOutboundOrders } from '../../api/outbound'
import { filterOutboundHistoryOrders } from '../../utils/monitoring'

const router = useRouter()
const statusOptions = [
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' }
]
const purposeMap = { PICKING: '生产领料', RETURN: '退货', TRANSFER: '调拨', OTHER: '其他' }

const query = reactive({
  status: ['COMPLETED', 'CANCELLED'],
  outboundNo: '',
  supplierKeyword: '',
  materialCode: '',
  forceOutboundOnly: false
})
const dateRange = ref(null)
const orders = ref([])
const forceLogs = ref([])
const materialOptions = ref([])
const loading = ref(false)
const loadError = ref('')
const currentPage = ref(1)
const pageSize = ref(10)

const filteredOrders = computed(() => filterOutboundHistoryOrders(orders.value, forceLogs.value, {
  statuses: query.status,
  outboundNo: query.outboundNo,
  supplierKeyword: query.supplierKeyword,
  materialCode: query.materialCode,
  forceOutboundOnly: query.forceOutboundOnly,
  dateRange: dateRange.value
}))

const paginatedOrders = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredOrders.value.slice(start, start + pageSize.value)
})

function purposeLabel(purpose) { return purposeMap[purpose] || purpose || '-' }
function formatDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN')
}
function formatQty(value) {
  if (value === null || value === undefined) return '0'
  const num = Number(value)
  if (Number.isNaN(num)) return value
  return String(num)
}

async function loadMaterialOptions() {
  try {
    materialOptions.value = await fetchMaterials()
  } catch {
    materialOptions.value = []
  }
}

async function loadOrders() {
  loading.value = true
  loadError.value = ''
  try {
    const payload = { outboundNo: query.outboundNo || undefined }
    if (query.status && query.status.length) {
      payload.status = query.status.join(',')
    }
    const [orderList, logList] = await Promise.all([
      fetchOutboundOrders(payload),
      fetchForceLogs({ outboundNo: query.outboundNo || undefined })
    ])
    orders.value = orderList
    forceLogs.value = logList
    currentPage.value = 1
  } catch (error) {
    loadError.value = error.response?.data?.message || '出库历史加载失败'
    orders.value = []
    forceLogs.value = []
  } finally { loading.value = false }
}

function resetFilters() {
  query.status = ['COMPLETED', 'CANCELLED']
  query.outboundNo = ''
  query.supplierKeyword = ''
  query.materialCode = ''
  query.forceOutboundOnly = false
  dateRange.value = null
  loadOrders()
}
function handleView(row) { router.push('/outbound/' + row.id) }

onMounted(async () => {
  await loadMaterialOptions()
  await loadOrders()
})
</script>

<style scoped>
.outbound-history-page :deep(.el-card__body) { padding-top: 12px; }
h2 { margin: 0; }
.filter-form { margin-bottom: 16px; }
.status-alert { margin-top: 12px; }
.order-table { min-height: 260px; margin-top: 12px; }
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>

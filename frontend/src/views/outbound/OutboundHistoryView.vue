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
        <el-form-item label="时间范围">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadOrders">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon />

      <el-table v-loading="loading" :data="paginatedOrders" border stripe size="small" class="order-table">
        <el-table-column prop="outboundNo" label="出库单号" min-width="160" />
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
        <el-table-column prop="lineCount" label="明细行数" width="100" />
        <el-table-column prop="plannedQty" label="计划数量" width="120" align="right">
          <template #default="{ row }">{{ formatQty(row.plannedQty) }}</template>
        </el-table-column>
        <el-table-column prop="shippedQty" label="已发数量" width="120" align="right">
          <template #default="{ row }">{{ formatQty(row.shippedQty) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" text @click="handleView(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="orders.length > pageSize" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 15, 20, 50]"
          :total="orders.length"
          layout="total, sizes, prev, pager, next, jumper"
          background
        />
      </div>

      <el-empty v-if="!loading && !orders.length" description="暂无出库历史" />
    </el-card>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchOutboundOrders } from '../../api/outbound'

const router = useRouter()

const statusOptions = [
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' }
]

const purposeMap = { PICKING: '生产领料', RETURN: '退货', TRANSFER: '调拨', OTHER: '其他' }

const query = reactive({ status: ['COMPLETED', 'CANCELLED'], outboundNo: '' })
const dateRange = ref(null)
const orders = ref([])
const loading = ref(false)
const loadError = ref('')

const currentPage = ref(1)
const pageSize = ref(10)

const paginatedOrders = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return orders.value.slice(start, start + pageSize.value)
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

async function loadOrders() {
  loading.value = true; loadError.value = ''
  try {
    const payload = { outboundNo: query.outboundNo || undefined }
    if (query.status && query.status.length) {
      payload.status = query.status.join(',')
    }
    if (dateRange.value && dateRange.value.length === 2) {
      payload.startDate = dateRange.value[0]
      payload.endDate = dateRange.value[1]
    }
    orders.value = await fetchOutboundOrders(payload)
    currentPage.value = 1
  } catch (error) {
    loadError.value = error.response?.data?.message || '出库历史加载失败'
    orders.value = []
  } finally { loading.value = false }
}

function resetFilters() { query.status = ['COMPLETED', 'CANCELLED']; query.outboundNo = ''; dateRange.value = null; loadOrders() }
function handleView(row) { router.push('/outbound/' + row.id) }

onMounted(() => { loadOrders() })
</script>

<style scoped>
.outbound-history-page :deep(.el-card__body) { padding-top: 12px; }
h2 { margin: 0; }
.filter-form { margin-bottom: 16px; }
.order-table { min-height: 260px; }
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>

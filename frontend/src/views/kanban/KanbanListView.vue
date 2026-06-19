<template>
  <section class="kanban-list-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <h2>看板列表</h2>
        </div>
      </template>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option
              v-for="s in statusOptions"
              :key="s.value"
              :label="s.label"
              :value="s.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="入库单号">
          <el-input v-model="query.inboundNo" placeholder="支持模糊输入" clearable />
        </el-form-item>

        <el-form-item label="物料编码">
          <el-input v-model="query.materialCode" placeholder="支持模糊输入" clearable />
        </el-form-item>

        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            clearable
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadData">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="loadError"
        :title="loadError"
        type="error"
        :closable="false"
        show-icon
      />

      <el-table
        v-loading="loading"
        :data="paginatedKanbans"
        border
        stripe
        size="small"
        class="kanban-table"
      >
        <el-table-column prop="kanbanCode" label="看板码" min-width="180">
          <template #default="{ row }">
            <span class="mono">{{ row.kanbanCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="inboundNo" label="入库单号" min-width="160" />
        <el-table-column prop="materialCode" label="物料编码" min-width="140" />
        <el-table-column prop="materialName" label="物料名称" min-width="200" />
        <el-table-column prop="qty" label="数量" width="100" align="right">
          <template #default="{ row }">{{ formatQty(row.qty) }}</template>
        </el-table-column>
        <el-table-column prop="containerTypeName" label="容器类型" min-width="120">
          <template #default="{ row }">{{ row.containerTypeName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="locationName" label="库位" min-width="140" />
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>

        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              text
              @click="handleView(row)"
            >
              查看详情
            </el-button>
            <el-button
              type="info"
              size="small"
              text
              @click="handleCopyCode(row)"
            >
              复制看板码
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="kanbans.length > pageSize" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 15, 20, 50]"
          :total="kanbans.length"
          layout="total, sizes, prev, pager, next, jumper"
          background
        />
      </div>

      <el-empty v-if="!loading && !kanbans.length" description="暂无看板数据" />
    </el-card>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchKanbanList } from '../../api/kanban'

const router = useRouter()

const statusOptions = [
  { value: 'ACTIVE', label: '活跃' },
  { value: 'PRINTED', label: '已打印' },
  { value: 'RECEIVED', label: '已入库' },
  { value: 'SHIPPED', label: '已出库' },
  { value: 'CANCELLED', label: '已取消' }
]

const query = reactive({
  status: '',
  inboundNo: '',
  materialCode: ''
})

const dateRange = ref(null)

const kanbans = ref([])
const loading = ref(false)
const loadError = ref('')

const currentPage = ref(1)
const pageSize = ref(10)

const paginatedKanbans = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return kanbans.value.slice(start, start + pageSize.value)
})

const statusMap = {
  ACTIVE: '活跃',
  PRINTED: '已打印',
  RECEIVED: '已入库',
  SHIPPED: '已出库',
  CANCELLED: '已取消'
}

const statusTagType = {
  ACTIVE: 'success',
  PRINTED: 'warning',
  RECEIVED: 'info',
  SHIPPED: 'success',
  CANCELLED: 'danger'
}

function statusType(status) {
  return statusTagType[status] || 'info'
}

function statusLabel(status) {
  return statusMap[status] || status || '未知'
}

function formatDateTime(value) {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString()
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

async function loadData() {
  loading.value = true
  loadError.value = ''
  try {
    const payload = {
      status: query.status || undefined,
      inboundNo: query.inboundNo || undefined,
      materialCode: query.materialCode || undefined
    }
    if (dateRange.value) {
      payload.startDate = dateRange.value[0]
      payload.endDate = dateRange.value[1]
    }
    const list = await fetchKanbanList(payload)
    kanbans.value = list
    currentPage.value = 1
  } catch (error) {
    loadError.value = error.response?.data?.message || '看板列表加载失败'
    kanbans.value = []
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  query.status = ''
  query.inboundNo = ''
  query.materialCode = ''
  dateRange.value = null
  loadData()
}

function handleView(row) {
  router.push('/inbound/' + row.inboundOrderId + '/kanbans')
}

async function handleCopyCode(row) {
  try {
    await navigator.clipboard.writeText(row.kanbanCode)
    ElMessage.success('看板码已复制: ' + row.kanbanCode)
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.kanban-list-page :deep(.el-card__body) {
  padding-top: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h2 {
  margin: 0;
}

.filter-form {
  margin-bottom: 16px;
}

.kanban-table {
  min-height: 260px;
}

.mono {
  font-family: ui-monospace, Menlo, Consolas, monospace;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>

<template>
  <section class="outbound-order-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <h2>出库单管理</h2>
          <el-button type="primary" @click="openCreateDrawer">新建出库单</el-button>
        </div>
      </template>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option
              v-for="status in statusOptions"
              :key="status.value"
              :label="status.label"
              :value="status.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="单号">
          <el-input v-model="query.outboundNo" placeholder="支持模糊输入" clearable />
        </el-form-item>

        <el-form-item label="供应商">
          <el-select v-model="query.supplierId" placeholder="全部供应商" clearable filterable style="width: 220px">
            <el-option
              v-for="supplier in masterData.suppliers"
              :key="supplier.id"
              :value="supplier.id"
              :label="`${supplier.code} ${supplier.name}`"
            />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadOrders">查询</el-button>
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
        :data="paginatedOrders"
        border
        stripe
        size="small"
        class="order-table"
      >
        <el-table-column prop="outboundNo" label="出库单号" min-width="160" />
        <el-table-column label="供应商" min-width="190">
          <template #default="{ row }">
            <template v-if="row.supplier">{{ row.supplier.code }} {{ row.supplier.name }}</template>
            <template v-else>—</template>
          </template>
        </el-table-column>
        <el-table-column prop="sourceDocNo" label="来源单号" min-width="140" />
        <el-table-column prop="status" label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lineCount" label="明细行数" width="100" />
        <el-table-column
          prop="plannedQty"
          label="总件数"
          width="100"
          align="right"
        >
          <template #default="{ row }">{{ formatQty(row.plannedQty) }}</template>
        </el-table-column>
        <el-table-column
          prop="pickedQty"
          label="已拣件数"
          width="100"
          align="right"
        >
          <template #default="{ row }">{{ formatQty(row.pickedQty) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />

        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-space size="small" wrap>
              <el-button
                type="primary"
                size="small"
                text
                :disabled="!canEdit(row)"
                @click="openEditDrawer(row)"
              >
                编辑
              </el-button>
              <el-button
                type="warning"
                size="small"
                text
                :disabled="!canRelease(row)"
                @click="handleReleaseAndLock(row)"
              >
                释放并加锁
              </el-button>
              <el-button
                type="success"
                size="small"
                text
                :disabled="!canPrint(row)"
                @click="handlePrintOrder(row)"
              >
                查看/打印出库单
              </el-button>
              <el-popconfirm
                :title="`确认取消出库单 ${row.outboundNo}？`"
                confirm-button-text="确认"
                cancel-button-text="取消"
                @confirm="handleCancel(row)"
              >
                <template #reference>
                  <el-button
                    type="danger"
                    size="small"
                    text
                    :disabled="!canCancel(row)"
                  >
                    取消
                  </el-button>
                </template>
              </el-popconfirm>
            </el-space>
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

      <el-empty v-if="!loading && !orders.length" description="暂无出库单" />
    </el-card>

    <OutboundOrderFormView
      v-model:visible="formVisible"
      :mode="formMode"
      :initial-order="editingOrder"
      :master-data="masterData"
      :on-save="handleSave"
    />

    <el-dialog v-model="warehouseDialogVisible" title="选择出库仓库" width="500px">
      <el-form label-position="top">
        <el-form-item label="请在以下仓库中选择出库目标仓库（多选）：">
          <el-select
            v-model="selectedWarehouseIds"
            multiple
            placeholder="全部仓库"
            style="width: 100%"
          >
            <el-option
              v-for="wh in masterData.warehouses"
              :key="wh.id"
              :label="wh.warehouseName || wh.name"
              :value="wh.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="warehouseDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmReleaseAndLock">确认释放并加锁</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  cancelOutboundOrder,
  createOutboundOrder,
  fetchOutboundOrders,
  releaseAndLockOrder,
  updateOutboundOrder
} from '../../api/outbound'
import { fetchMasterDataOptions } from '../../api/masterData'
import OutboundOrderFormView from './OutboundOrderFormView.vue'

const router = useRouter()

const statusOptions = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'LOCKED', label: '已锁定' },
  { value: 'PICKING', label: '拣货中' },
  { value: 'PARTIAL_SHIPPED', label: '部分发货' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' }
]

const DRAFT = 'DRAFT'
const LOCKED = 'LOCKED'
const PICKING = 'PICKING'
const COMPLETED = 'COMPLETED'
const PARTIAL_SHIPPED = 'PARTIAL_SHIPPED'
const CANCELLED = 'CANCELLED'

const query = reactive({
  status: '',
  outboundNo: '',
  supplierId: ''
})

const orders = ref([])
const loading = ref(false)
const loadError = ref('')
const formVisible = ref(false)
const formMode = ref('create')
const editingOrder = ref(null)

const masterData = ref({
  suppliers: [],
  materials: [],
  warehouses: [],
  locations: []
})

const currentPage = ref(1)
const pageSize = ref(10)

const paginatedOrders = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return orders.value.slice(start, start + pageSize.value)
})

const warehouseDialogVisible = ref(false)
const releasingOrderId = ref(null)
const selectedWarehouseIds = ref([])

const statusMap = {
  [DRAFT]: '草稿',
  [LOCKED]: '已锁定',
  [PICKING]: '拣货中',
  [PARTIAL_SHIPPED]: '部分发货',
  [COMPLETED]: '已完成',
  [CANCELLED]: '已取消'
}

const statusTagType = {
  [DRAFT]: 'info',
  [LOCKED]: '',
  [PICKING]: 'warning',
  [PARTIAL_SHIPPED]: 'success',
  [COMPLETED]: 'success',
  [CANCELLED]: 'danger'
}

const canEdit = (row) => [DRAFT].includes(row.status)
const canRelease = (row) => row.status === DRAFT
const canCancel = (row) => [DRAFT].includes(row.status)
const canPrint = (row) => [LOCKED, PICKING, PARTIAL_SHIPPED, COMPLETED].includes(row.status)

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

async function loadMasterData() {
  try {
    masterData.value = await fetchMasterDataOptions()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '主数据加载失败')
  }
}

async function loadOrders() {
  loading.value = true
  loadError.value = ''
  try {
    const payload = {
      status: query.status || undefined,
      outboundNo: query.outboundNo || undefined,
      supplierId: query.supplierId || undefined
    }
    const list = await fetchOutboundOrders(payload)
    orders.value = list
    currentPage.value = 1
  } catch (error) {
    loadError.value = error.response?.data?.message || '出库单列表加载失败'
    orders.value = []
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  query.status = ''
  query.outboundNo = ''
  query.supplierId = ''
  loadOrders()
}

function openCreateDrawer() {
  editingOrder.value = null
  formMode.value = 'create'
  formVisible.value = true
}

function openEditDrawer(row) {
  editingOrder.value = {
    id: row.id,
    purpose: row.purpose,
    sourceDocNo: row.sourceDocNo || '',
    remark: row.remark || '',
    lines: (row.lines || []).map((line) => ({
      materialId: line.materialId,
      supplierId: line.supplier?.id,
      plannedQty: line.plannedQty,
      containerTypeId: line.containerTypeId
    }))
  }
  formMode.value = 'edit'
  formVisible.value = true
}

async function handleSave(payload, mode) {
  try {
    if (mode === 'edit') {
      await updateOutboundOrder(editingOrder.value?.id, payload)
      ElMessage.success('出库单修改成功')
    } else {
      await createOutboundOrder(payload)
      ElMessage.success('出库单创建成功')
    }
    formVisible.value = false
    await loadOrders()
  } catch (error) {
    console.error('出库单保存失败', { error, response: error.response, data: error.response?.data, status: error.response?.status })
    ElMessage.error(error.response?.data?.message || '保存失败，请重试')
  }
}

async function handleReleaseAndLock(row) {
  releasingOrderId.value = row.id
  selectedWarehouseIds.value = []
  warehouseDialogVisible.value = true
}

async function confirmReleaseAndLock() {
  try {
    await releaseAndLockOrder(releasingOrderId.value, selectedWarehouseIds.value)
    ElMessage.success('释放并加锁成功')
    warehouseDialogVisible.value = false
    await loadOrders()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '释放并加锁失败')
  }
}

async function handleCancel(row) {
  try {
    await cancelOutboundOrder(row.id)
    ElMessage.success('出库单已取消')
    await loadOrders()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '取消失败')
  }
}

function handlePrintOrder(row) {
  router.push('/outbound/' + row.id)
}

onMounted(() => {
  Promise.all([loadMasterData(), loadOrders()])
})
</script>

<style scoped>
.outbound-order-page :deep(.el-card__body) {
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

.order-table {
  min-height: 260px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>

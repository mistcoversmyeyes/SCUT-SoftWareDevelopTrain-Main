<template>
  <section class="inbound-order-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <h2>入库单管理</h2>
          <el-button type="primary" @click="openCreateDrawer">新建入库单</el-button>
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
          <el-input v-model="query.inboundNo" placeholder="支持模糊输入" clearable />
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
        <el-table-column prop="inboundNo" label="入库单号" min-width="160" />
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
        <el-table-column prop="lineCount" label="明细行数" width="90" />
        <el-table-column
          prop="inventoryTagCount"
          label="库存标签数"
          width="80"
          align="right"
        >
          <template #default="{ row }">
            {{ row.inventoryTagCount > 0 ? row.inventoryTagCount : '—' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="plannedQty"
          label="总件数"
          width="100"
          align="right"
        >
          <template #default="{ row }">{{ formatQty(row.plannedQty) }}</template>
        </el-table-column>
        <el-table-column
          prop="receivedQty"
          label="已收件数"
          width="100"
          align="right"
        >
          <template #default="{ row }">{{ formatQty(row.receivedQty) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />

        <el-table-column label="操作" width="300" fixed="right">
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
                @click="handleRelease(row)"
              >
                释放
              </el-button>
              <el-button
                type="success"
                size="small"
                text
                :disabled="!canPrint(row)"
                @click="handlePrintOrder(row)"
              >
                查看/打印入库单
              </el-button>
              <el-button
                type="success"
                size="small"
                text
                :disabled="!canPrintInventoryTags(row)"
                @click="handlePrintInventoryTags(row)"
              >
                查看/打印库存标签
              </el-button>
              <el-button
                type="warning"
                size="small"
                text
                :disabled="!canPartialCancel(row)"
                @click="openPartialCancelDialog(row)"
              >
                部分取消
              </el-button>
              <el-popconfirm
                :title="`确认取消入库单 ${row.inboundNo}？`"
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

      <el-empty v-if="!loading && !orders.length" description="暂无入库单" />
    </el-card>

    <InboundOrderFormView
      v-model:visible="formVisible"
      :mode="formMode"
      :initial-order="editingOrder"
      :master-data="masterData"
      :on-save="handleSave"
    />

    <el-dialog
      v-model="partialCancelVisible"
      title="部分取消库存标签"
      width="700px"
    >
      <div v-if="partialCancelOrder">
        <p style="margin:0 0 12px; color:#606266;">
          入库单: {{ partialCancelOrder.inboundNo }} — 选择需要取消的库存标签
        </p>
        <el-table
          :data="partialCancelInventoryTags"
          border
          stripe
          size="small"
          @selection-change="onPartialCancelSelectionChange"
        >
          <el-table-column type="selection" width="50" :selectable="(row) => row.status !== 'RECEIVED'" />
          <el-table-column prop="inventoryTagCode" label="库存标签码" min-width="180" />
          <el-table-column prop="materialCode" label="物料编码" min-width="140" />
          <el-table-column prop="materialName" label="物料名称" min-width="180" />
          <el-table-column prop="qty" label="数量" width="100" align="right" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag
                :type="row.status === 'RECEIVED' ? 'info' : 'warning'"
                size="small"
              >
                {{ row.status === 'RECEIVED' ? '已入库' : '已打印' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-space>
          <el-button @click="partialCancelVisible = false">取消</el-button>
          <el-button
            type="danger"
            :loading="partialCancelling"
            :disabled="!partialCancelSelectedIds.length"
            @click="confirmPartialCancel"
          >
            确认取消
          </el-button>
        </el-space>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  cancelInboundOrder,
  createInboundOrder,
  fetchInboundOrders,
  fetchInventoryTagsByOrderId,
  releaseInboundOrder,
  updateInboundOrder
} from '../../api/inbound'
import { cancelInventoryTagsBatch } from '../../api/inventory'
import { fetchMasterDataOptions } from '../../api/masterData'
import InboundOrderFormView from './InboundOrderFormView.vue'

const router = useRouter()

const statusOptions = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'RELEASED', label: '已释放' },
  { value: 'PARTIAL_RECEIVED', label: '部分入库' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' }
]

const DRAFT = 'DRAFT'
const RELEASED = 'RELEASED'
const COMPLETED = 'COMPLETED'
const PARTIAL_RECEIVED = 'PARTIAL_RECEIVED'
const CANCELLED = 'CANCELLED'

const query = reactive({
  status: '',
  inboundNo: '',
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

// Partial cancel
const partialCancelVisible = ref(false)
const partialCancelOrder = ref(null)
const partialCancelInventoryTags = ref([])
const partialCancelSelectedIds = ref([])
const partialCancelling = ref(false)

const paginatedOrders = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return orders.value.slice(start, start + pageSize.value)
})

const statusMap = {
  [DRAFT]: '草稿',
  [RELEASED]: '已释放',
  [PARTIAL_RECEIVED]: '部分入库',
  [COMPLETED]: '已完成',
  [CANCELLED]: '已取消'
}

const statusTagType = {
  [DRAFT]: 'info',
  [RELEASED]: 'warning',
  [PARTIAL_RECEIVED]: 'success',
  [COMPLETED]: 'success',
  [CANCELLED]: 'danger'
}

const canEdit = (row) => [DRAFT, RELEASED].includes(row.status)
const canRelease = (row) => row.status === DRAFT
const canCancel = (row) => [DRAFT, RELEASED].includes(row.status)
const canPrint = (row) => [RELEASED, PARTIAL_RECEIVED, COMPLETED].includes(row.status)
const canPrintInventoryTags = (row) => [RELEASED, PARTIAL_RECEIVED, COMPLETED].includes(row.status)
const canPartialCancel = (row) => [RELEASED, PARTIAL_RECEIVED].includes(row.status)

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
      inboundNo: query.inboundNo || undefined,
      supplierId: query.supplierId || undefined
    }
    const list = await fetchInboundOrders(payload)
    orders.value = list
    currentPage.value = 1
  } catch (error) {
    loadError.value = error.response?.data?.message || '入库单列表加载失败'
    orders.value = []
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  query.status = ''
  query.inboundNo = ''
  query.supplierId = ''
  currentPage.value = 1
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
    sourceDocNo: row.sourceDocNo || '',
    remark: row.remark || '',
    lines: (row.lines || []).map((line) => ({
      materialId: line.materialId,
      supplierId: line.supplier?.id,
      plannedQty: line.plannedQty,
      targetWarehouseId: line.targetWarehouseId,
      targetLocationId: line.targetLocationId,
      containerTypeId: line.containerTypeId
    }))
  }
  formMode.value = 'edit'
  formVisible.value = true
}

async function handleSave(payload, mode) {
  try {
    if (mode === 'edit') {
      await updateInboundOrder(editingOrder.value?.id, payload)
      ElMessage.success('入库单修改成功')
    } else {
      await createInboundOrder(payload)
      ElMessage.success('入库单创建成功')
    }
    formVisible.value = false
    await loadOrders()
  } catch (error) {
    console.error('入库单保存失败', { error, response: error.response, data: error.response?.data, status: error.response?.status })
    ElMessage.error(error.response?.data?.message || '保存失败，请重试')
  }
}

async function handleRelease(row) {
  try {
    const result = await releaseInboundOrder(row.id)
    if (result.inventoryTagCount) {
      const totalQty = (result.inventoryTagCodes || []).length > 0 && result.order
        ? result.order.plannedQty || 0 : 0
      const msg = `已生成 ${result.inventoryTagCount} 个库存标签`
        + (totalQty ? `（共 ${totalQty} 件）` : '')
        + `: ${(result.inventoryTagCodes || []).join(', ')}`
      ElMessage({ message: msg, type: 'success', duration: 6000 })
    } else {
      ElMessage.success('入库单释放成功')
    }
    await loadOrders()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '释放失败')
  }
}

async function handleCancel(row) {
  try {
    await cancelInboundOrder(row.id)
    ElMessage.success('入库单已取消')
    await loadOrders()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '取消失败')
  }
}

function handlePrintOrder(row) {
  router.push('/inbound/' + row.id)
}

function handlePrintInventoryTags(row) {
  router.push('/inbound/' + row.id + '/inventory-tags')
}

async function openPartialCancelDialog(row) {
  partialCancelOrder.value = row
  partialCancelSelectedIds.value = []
  try {
    const inventoryTags = await fetchInventoryTagsByOrderId(row.id)
    partialCancelInventoryTags.value = (inventoryTags || []).filter(
      (k) => k.status === 'PRINTED' || k.status === 'RECEIVED'
    ).map((k) => ({
      ...k,
      _disabled: k.status === 'RECEIVED'
    }))
    partialCancelVisible.value = true
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载库存标签列表失败')
  }
}

function onPartialCancelSelectionChange(selection) {
  partialCancelSelectedIds.value = selection
    .filter((k) => !k._disabled)
    .map((k) => k.inventoryTagId)
}

async function confirmPartialCancel() {
  if (!partialCancelSelectedIds.value.length) {
    ElMessage.warning('请选择需要取消的库存标签')
    return
  }
  partialCancelling.value = true
  try {
    await cancelInventoryTagsBatch(partialCancelSelectedIds.value)
    ElMessage.success('库存标签取消成功')
    partialCancelVisible.value = false
    await loadOrders()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '取消失败')
  } finally {
    partialCancelling.value = false
  }
}

onMounted(() => {
  Promise.all([loadMasterData(), loadOrders()])
})
</script>

<style scoped>
.inbound-order-page :deep(.el-card__body) {
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

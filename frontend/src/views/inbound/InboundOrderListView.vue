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
          <el-select v-model="query.status" placeholder="全部" clearable>
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
          <el-select v-model="query.supplierId" placeholder="全部供应商" clearable filterable>
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
        :data="orders"
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
        <el-table-column prop="lineCount" label="明细行数" width="100" />
        <el-table-column
          prop="plannedQty"
          label="计划数量"
          width="120"
          align="right"
        >
          <template #default="{ row }">{{ formatQty(row.plannedQty) }}</template>
        </el-table-column>
        <el-table-column
          prop="receivedQty"
          label="已收数量"
          width="120"
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
                打印入库单
              </el-button>
              <el-button
                type="success"
                size="small"
                text
                :disabled="!canPrintKanbans(row)"
                @click="handlePrintKanbans(row)"
              >
                打印看板
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

      <el-empty v-if="!loading && !orders.length" description="暂无入库单" />
    </el-card>

    <InboundOrderFormView
      v-model:visible="formVisible"
      :mode="formMode"
      :initial-order="editingOrder"
      :master-data="masterData"
      @save="handleSave"
    />
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  cancelInboundOrder,
  createInboundOrder,
  fetchInboundOrders,
  printInboundOrder,
  printKanbans,
  releaseInboundOrder,
  updateInboundOrder
} from '../../api/inbound'
import { fetchMasterDataOptions } from '../../api/masterData'
import InboundOrderFormView from './InboundOrderFormView.vue'

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
const canPrintKanbans = (row) => [RELEASED, PARTIAL_RECEIVED, COMPLETED].includes(row.status)

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
  return num.toFixed(3)
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
    supplierId: row.supplier?.id,
    sourceDocNo: row.sourceDocNo || '',
    remark: row.remark || '',
    lines: (row.lines || []).map((line) => ({
      materialId: line.materialId,
      plannedQty: line.plannedQty,
      targetWarehouseId: line.targetWarehouseId,
      targetLocationId: line.targetLocationId
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
    await releaseInboundOrder(row.id)
    ElMessage.success('入库单释放成功')
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

function buildPreviewWindow(title, htmlBody) {
  const html = `<!doctype html>
<html>
  <head>
    <meta charset="UTF-8" />
    <title>${title}</title>
    <style>
      body { font-family: Arial, sans-serif; padding: 24px; color: #111827; }
      h1 { font-size: 20px; margin: 0 0 12px; }
      .meta { margin-bottom: 16px; color: #64748b; }
      table { width: 100%; border-collapse: collapse; margin-top: 12px; }
      th, td { border: 1px solid #d1d5db; padding: 8px; font-size: 12px; }
      th { background: #f1f5f9; text-align: left; }
      .mono { font-family: ui-monospace, Menlo, Consolas, monospace; }
    </style>
  </head>
  <body>
    <h1>${title}</h1>
    <p class="meta">本页为 WP-06 临时预览，后续可替换为 WP-07 打印路由。</p>
    ${htmlBody}
  </body>
</html>`
  const blob = new Blob([html], { type: 'text/html;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const win = window.open(url, '_blank', 'noopener')
  if (win) {
    setTimeout(() => URL.revokeObjectURL(url), 2000)
  }
}

function buildInboundPrintHtml(order) {
  const lines = (order.lines || []).map((line) => `
      <tr>
        <td>${line.lineNo}</td>
        <td>${line.materialCode || ''}</td>
        <td>${line.materialName || ''}</td>
        <td class="mono">${line.plannedQty ?? ''}</td>
        <td class="mono">${line.receivedQty ?? ''}</td>
        <td>${line.warehouseName || ''}</td>
        <td>${line.locationName || ''}</td>
      </tr>
  `).join('')
  return `
    <div class="meta">入库单号：${order.inboundNo || '-'}，供应商：${order.supplierCode || ''} ${order.supplierName || ''}</div>
    <div class="meta">来源单号：${order.sourceDocNo || '-'}，状态：${order.status || '-'}，备注：${order.remark || '-'}</div>
    <table>
      <thead>
        <tr><th>行号</th><th>物料编码</th><th>物料名称</th><th>计划数量</th><th>已收数量</th><th>仓库</th><th>库位</th></tr>
      </thead>
      <tbody>${lines}</tbody>
    </table>`
}

function buildKanbanPrintHtml(payload) {
  const list = Array.isArray(payload) ? payload : []
  const rows = list.map((item) => `
      <tr>
        <td class="mono">${item.kanbanCode || ''}</td>
        <td>${item.inboundNo || ''}</td>
        <td>${item.supplierCode || ''} ${item.supplierName || ''}</td>
        <td>${item.materialCode || ''}</td>
        <td>${item.materialName || ''}</td>
        <td>${item.locationName || ''}</td>
        <td class="mono">${item.qty ?? ''}</td>
        <td>${item.status || ''}</td>
      </tr>
  `).join('')
  return `
    <div class="meta">看板打印共 ${list.length} 条</div>
    <table>
      <thead>
        <tr>
          <th>看板码</th><th>入库单号</th><th>供应商</th><th>物料编码</th>
          <th>物料名称</th><th>库位</th><th>数量</th><th>状态</th>
        </tr>
      </thead>
      <tbody>${rows}</tbody>
    </table>`
}

async function handlePrintOrder(row) {
  try {
    const data = await printInboundOrder(row.id)
    buildPreviewWindow(`入库单打印 - ${row.inboundNo}`, buildInboundPrintHtml(data))
    // WP-07 打印页面路由约定：/inbound/orders/:id/print
    // 如该路由已实现，可在此页面内切换为直接跳转到正式页面。
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '入库单打印失败')
  }
}

async function handlePrintKanbans(row) {
  try {
    const data = await printKanbans(row.id)
    buildPreviewWindow(`看板打印 - ${row.inboundNo}`, buildKanbanPrintHtml(data))
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '看板打印失败')
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
</style>

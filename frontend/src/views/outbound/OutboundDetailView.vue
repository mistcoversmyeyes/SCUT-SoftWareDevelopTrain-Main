<template>
  <section class="module-shell">
    <el-card v-loading="loading">
      <template #header>
        <div class="toolbar">
          <h2>出库单详情</h2>
          <div class="toolbar-actions">
            <el-button size="default" @click="handleCopy">复制单号</el-button>
            <el-button type="primary" size="default" @click="handlePrint">打印</el-button>
          </div>
        </div>
      </template>

      <el-alert v-if="errorMessage" type="error" :title="errorMessage" show-icon :closable="false" />

      <template v-if="order">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="出库单号">{{ order.outboundNo }}</el-descriptions-item>
          <el-descriptions-item label="供应商（主）">
            {{ order.supplier?.code }} {{ order.supplier?.name }}
          </el-descriptions-item>
          <el-descriptions-item label="出库用途">{{ purposeLabel(order.purpose) }}</el-descriptions-item>
          <el-descriptions-item label="来源单号">{{ order.sourceDocNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(order.status)">{{ statusLabel(order.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="备注">{{ order.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(order.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="释放时间">{{ formatDateTime(order.releasedAt) }}</el-descriptions-item>
        </el-descriptions>

        <el-table :data="order.lines || []" border stripe class="detail-table" style="margin-top: 16px;">
          <el-table-column prop="lineNo" label="行号" width="80" />
          <el-table-column prop="materialCode" label="物料编码" min-width="160" />
          <el-table-column prop="materialName" label="物料名称" min-width="220" />
          <el-table-column label="供应商" min-width="180">
            <template #default="{ row }">
              <template v-if="row.supplier">{{ row.supplier.code }} {{ row.supplier.name }}</template>
              <template v-else>—</template>
            </template>
          </el-table-column>
          <el-table-column prop="plannedQty" label="计划数量" width="120" align="right">
            <template #default="{ row }">{{ formatQty(row.plannedQty) }}</template>
          </el-table-column>
          <el-table-column prop="pickedQty" label="已拣数量" width="120" align="right">
            <template #default="{ row }">{{ formatQty(row.pickedQty) }}</template>
          </el-table-column>
        </el-table>
      </template>

      <el-empty v-else-if="!loading && !errorMessage" description="暂无数据" />
    </el-card>
  </section>
</template>

<script setup>
import { onBeforeMount, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchOutboundOrderById } from '../../api/outbound'

const route = useRoute()
const loading = ref(false)
const errorMessage = ref('')
const order = ref(null)

const statusMap = {
  DRAFT: '草稿',
  RELEASED: '已释放',
  PARTIAL_SHIPPED: '部分发货',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

const statusTagType = {
  DRAFT: 'info',
  RELEASED: 'warning',
  PARTIAL_SHIPPED: 'success',
  COMPLETED: 'success',
  CANCELLED: 'danger'
}

const purposeMap = {
  PICKING: '生产领料',
  RETURN: '退货',
  TRANSFER: '调拨',
  OTHER: '其他'
}

function statusType(status) { return statusTagType[status] || 'info' }
function statusLabel(status) { return statusMap[status] || status || '未知' }
function purposeLabel(purpose) { return purposeMap[purpose] || purpose || '-' }

function formatDateTime(value) {
  if (!value) return '-'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleString('zh-CN')
}

function formatQty(value) {
  if (value === null || value === undefined) return '0'
  const num = Number(value)
  if (Number.isNaN(num)) return value
  return String(num)
}

function getOutboundId() {
  return Number(route.params.id)
}

async function loadData() {
  const id = getOutboundId()
  if (!id) {
    errorMessage.value = '出库单编号缺失'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    order.value = await fetchOutboundOrderById(id)
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '加载出库单详情失败'
    order.value = null
  } finally {
    loading.value = false
  }
}

function handleCopy() {
  if (order.value?.outboundNo) {
    navigator.clipboard.writeText(order.value.outboundNo)
    ElMessage.success('单号已复制')
  }
}

function handlePrint() {
  window.print()
}

onBeforeMount(() => { loadData() })
</script>

<style scoped>
.module-shell { min-height: 360px; }
h2 { margin: 0; }
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.toolbar-actions { display: flex; gap: 8px; }
.detail-table { min-height: 120px; }

@media print {
  .toolbar-actions { display: none; }
  .el-alert { display: none; }
  .module-shell { padding: 0; }
  :deep(.el-card) { border: none !important; box-shadow: none !important; }
  :deep(.el-card__body) { padding: 0 !important; }
  :deep(.el-descriptions) { border: 1px solid #000; }
}
</style>

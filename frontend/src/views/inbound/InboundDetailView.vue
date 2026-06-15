<template>
  <section class="module-shell">
    <el-card v-loading="loading">
      <template #header>
        <div class="toolbar">
          <h2>入库单详情</h2>
          <div class="toolbar-actions">
            <el-button size="default" @click="handleCopy">复制单号</el-button>
            <el-button type="primary" size="default" @click="handlePrint">打印</el-button>
          </div>
        </div>
      </template>

      <el-alert v-if="errorMessage" type="error" :title="errorMessage" show-icon :closable="false" />

      <template v-if="order">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="入库单号">{{ order.inboundNo }}</el-descriptions-item>
          <el-descriptions-item label="供应商">
            {{ order.supplierCode }} {{ order.supplierName }}
          </el-descriptions-item>
          <el-descriptions-item label="来源单号">{{ order.sourceDocNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(order.status)">{{ statusLabel(order.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(order.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ order.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-table :data="order.lines || []" border stripe class="detail-table" style="margin-top: 16px;">
          <el-table-column prop="lineNo" label="行号" width="80" />
          <el-table-column prop="materialCode" label="物料编码" min-width="160" />
          <el-table-column prop="materialName" label="物料名称" min-width="220" />
          <el-table-column prop="plannedQty" label="计划数量" width="120" align="right">
            <template #default="{ row }">{{ formatQty(row.plannedQty) }}</template>
          </el-table-column>
          <el-table-column prop="receivedQty" label="已收数量" width="120" align="right">
            <template #default="{ row }">{{ formatQty(row.receivedQty) }}</template>
          </el-table-column>
          <el-table-column prop="warehouseName" label="仓库" min-width="180" />
          <el-table-column prop="locationName" label="库位" min-width="180" />
        </el-table>
      </template>

      <el-empty v-else-if="!loading && !errorMessage" description="暂无数据" />
    </el-card>
  </section>
</template>

<script setup>
import { computed, onBeforeMount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchInboundOrderById } from '../../api/inbound'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const order = ref(null)

const statusMap = {
  DRAFT: '草稿',
  RELEASED: '已释放',
  PARTIAL_RECEIVED: '部分入库',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

const statusTagType = {
  DRAFT: 'info',
  RELEASED: 'warning',
  PARTIAL_RECEIVED: 'success',
  COMPLETED: 'success',
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
    return '-'
  }
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }
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
  return num.toFixed(3)
}

function getInboundId() {
  const rawId = route.params.id
  return Number(rawId)
}

async function loadData() {
  const id = getInboundId()
  if (!id) {
    errorMessage.value = '入库单编号缺失'
    return
  }

  loading.value = true
  errorMessage.value = ''
  try {
    order.value = await fetchInboundOrderById(id)
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '加载入库单详情失败'
    order.value = null
  } finally {
    loading.value = false
  }
}

function handleCopy() {
  if (order.value?.inboundNo) {
    navigator.clipboard.writeText(order.value.inboundNo)
    ElMessage.success('单号已复制')
  }
}

function handlePrint() {
  const id = getInboundId()
  router.push('/inbound/' + id + '/print')
}

onBeforeMount(() => {
  loadData()
})
</script>

<style scoped>
.module-shell {
  min-height: 360px;
}

h2 {
  margin: 0;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.detail-table {
  min-height: 120px;
}
</style>

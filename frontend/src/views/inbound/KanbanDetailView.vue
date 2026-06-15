<template>
  <section class="module-shell">
    <el-card v-loading="loading" class="print-container">
      <template #header>
        <div class="toolbar">
          <h2>看板详情</h2>
          <el-button type="primary" size="default" :loading="loading" @click="printNow">打印</el-button>
        </div>
      </template>

      <el-alert v-if="errorMessage" type="error" :title="errorMessage" show-icon :closable="false" />

      <div v-if="kanbans.length" class="kanban-grid">
        <article v-for="kanban in kanbans" :key="kanban.kanbanCode" class="kanban-card">
          <h3>{{ kanban.kanbanCode }}</h3>
          <p><strong>状态：</strong>
            <el-tag :type="statusType(kanban.status)" size="small">{{ statusLabel(kanban.status) }}</el-tag>
          </p>
          <p><strong>入库单号：</strong>{{ kanban.inboundNo }}</p>
          <p>
            <strong>供应商：</strong>
            {{ kanban.supplierCode }} {{ kanban.supplierName }}
          </p>
          <p>
            <strong>物料：</strong>
            {{ kanban.materialCode }} {{ kanban.materialName }}
          </p>
          <p><strong>库位：</strong>{{ kanban.locationName }}</p>
          <p><strong>数量：</strong>{{ kanban.qty }}</p>
          <div class="qrcode-box">
            <img v-if="qrCodes[kanban.kanbanCode]" :src="qrCodes[kanban.kanbanCode]" alt="QR Code" width="120" height="120" />
          </div>
        </article>
      </div>

      <el-empty v-else-if="!loading && !errorMessage" description="暂无看板数据" />
    </el-card>
  </section>
</template>

<script setup>
import { onBeforeMount, ref } from 'vue'
import { useRoute } from 'vue-router'
import QRCode from 'qrcode'
import { fetchKanbansByOrderId } from '../../api/inbound'

const route = useRoute()
const loading = ref(false)
const errorMessage = ref('')
const kanbans = ref([])
const qrCodes = ref({})

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

function getInboundId() {
  const rawId = route.params.id
  return Number(rawId)
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

async function generateQrCodes(list) {
  const codes = {}
  for (const kanban of list) {
    try {
      codes[kanban.kanbanCode] = await QRCode.toDataURL(kanban.kanbanCode, { width: 120 })
    } catch {
      codes[kanban.kanbanCode] = ''
    }
  }
  qrCodes.value = codes
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
    const result = await fetchKanbansByOrderId(id)
    kanbans.value = result || []
    if (kanbans.value.length) {
      await generateQrCodes(kanbans.value)
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '加载看板数据失败'
  } finally {
    loading.value = false
  }
}

function printNow() {
  window.print()
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

.kanban-grid {
  margin-top: 6px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.kanban-card {
  min-height: 220px;
  border: 1px solid #111827;
  padding: 10px;
  page-break-inside: avoid;
}

.kanban-card h3 {
  margin: 0 0 8px;
  font-size: 20px;
}

.kanban-card p {
  margin: 4px 0;
}

.qrcode-box {
  margin-top: 12px;
  height: 130px;
  border: 1px dashed #94a3b8;
  border-radius: 4px;
  display: flex;
  justify-content: center;
  align-items: center;
  color: #334155;
}

@media print {
  .toolbar {
    display: none;
  }

  .kanban-grid {
    grid-template-columns: repeat(2, 90mm);
    gap: 8mm;
  }

  .kanban-card {
    border: 1px solid #000;
  }

  .qrcode-box {
    border-color: #000;
  }
}
</style>

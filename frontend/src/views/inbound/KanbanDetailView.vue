<template>
  <section class="module-shell">
    <div class="toolbar">
      <h2>看板详情</h2>
      <div class="toolbar-actions">
        <el-button size="default" @click="printAll">全部打印</el-button>
      </div>
    </div>

    <el-alert v-if="errorMessage" type="error" :title="errorMessage" show-icon :closable="false" />

    <div v-if="kanbans.length" class="kanban-list">
      <div
        v-for="(kanban, i) in kanbans"
        :key="kanban.kanbanCode"
        class="kanban-row"
      >
        <article
          :class="['kanban-card', { 'printing-card': printingIndex === i }]"
        >
          <div class="card-left">
            <div class="card-header-row">
              <h3>{{ kanban.kanbanCode }}</h3>
            </div>
          <div class="info-row">
            <span><strong>状态</strong>
              <el-tag :type="statusType(kanban.status)" size="small">{{ statusLabel(kanban.status) }}</el-tag>
            </span>
            <span><strong>入库单</strong> {{ kanban.inboundNo }}</span>
          </div>
          <div class="info-row">
            <span><strong>供应商</strong> {{ kanban.supplierCode }} {{ kanban.supplierName }}</span>
          </div>
          <div class="info-row">
            <span><strong>物料</strong> {{ kanban.materialCode }} {{ kanban.materialName }}</span>
          </div>
          <div class="info-row">
            <span><strong>库位</strong> {{ kanban.locationName }}</span>
            <span><strong>数量</strong> {{ kanban.qty }}</span>
          </div>
        </div>
        <div class="card-right">
          <img v-if="qrCodes[kanban.kanbanCode]" :src="qrCodes[kanban.kanbanCode]" alt="QR" width="80" height="80" />
        </div>
      </article>
      <div class="kanban-actions">
        <el-button size="small" @click="copyCode(kanban.kanbanCode)">复制看板码</el-button>
        <el-button
          v-if="kanban.status === 'PRINTED'"
          size="small"
          type="success"
          :loading="receiving === i"
          @click="receiveOne(i, kanban)"
        >一键入库</el-button>
        <el-button size="small" @click="printOne(i)">打印此卡</el-button>
      </div>
    </div>
    </div>

    <el-empty v-else-if="!loading && !errorMessage" description="暂无看板数据" />
  </section>
</template>

<script setup>
import { onBeforeMount, onBeforeUnmount, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { fetchKanbansByOrderId } from '../../api/inbound'
import { scanInbound } from '../../api/inventory'

const route = useRoute()
const loading = ref(false)
const errorMessage = ref('')
const kanbans = ref([])
const qrCodes = ref({})
const printingIndex = ref(-1)
const receiving = ref(-1)

const statusMap = { ACTIVE:'活跃', PRINTED:'已打印', RECEIVED:'已入库', SHIPPED:'已出库', CANCELLED:'已取消' }
const statusTagType = { ACTIVE:'success', PRINTED:'warning', RECEIVED:'info', SHIPPED:'success', CANCELLED:'danger' }

function statusType(s) { return statusTagType[s] || 'info' }
function statusLabel(s) { return statusMap[s] || s || '未知' }
function getInboundId() { return Number(route.params.id) }

async function generateQrCodes(list) {
  const codes = {}
  for (const k of list) {
    try { codes[k.kanbanCode] = await QRCode.toDataURL(k.kanbanCode, { width: 80 }) }
    catch { codes[k.kanbanCode] = '' }
  }
  qrCodes.value = codes
}

async function loadData() {
  const id = getInboundId()
  if (!id) { errorMessage.value = '入库单编号缺失'; return }
  loading.value = true; errorMessage.value = ''
  try {
    const result = await fetchKanbansByOrderId(id)
    kanbans.value = result || []
    if (kanbans.value.length) await generateQrCodes(kanbans.value)
  } catch (e) {
    errorMessage.value = e.response?.data?.message || '加载看板数据失败'
  } finally { loading.value = false }
}

function copyCode(code) {
  navigator.clipboard.writeText(code)
  ElMessage.success('看板码已复制')
}

async function receiveOne(i, kanban) {
  receiving.value = i
  try {
    await scanInbound(kanban.kanbanCode)
    ElMessage.success('入库成功')
    await loadData()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '入库失败')
  } finally {
    receiving.value = -1
  }
}

function printAll() { printingIndex.value = -1; setTimeout(() => window.print(), 100) }
function printOne(i) { printingIndex.value = i; setTimeout(() => window.print(), 100) }
function onAfterPrint() { printingIndex.value = -1 }

onBeforeMount(() => { loadData(); window.addEventListener('afterprint', onAfterPrint) })
onBeforeUnmount(() => window.removeEventListener('afterprint', onAfterPrint))
</script>

<style scoped>
.module-shell { min-height: 360px; }
h2 { margin: 0; }

.toolbar {
  display: flex; align-items: center; justify-content: space-between;
  gap: 12px; margin-bottom: 20px;
}
.toolbar-actions { display: flex; gap: 8px; }

.kanban-list {
  display: flex; flex-direction: column; align-items: center; gap: 20px;
}

.kanban-row {
  display: flex; align-items: center; gap: 12px;
}

.kanban-actions {
  width: 100px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.kanban-actions :deep(.el-button) {
  width: 100px !important;
  margin-left: 0 !important;
  margin-right: 0 !important;
  justify-content: center;
}

.kanban-card {
  display: flex; width: 120mm; min-height: 32mm;
  border: 1px solid #111827; background: #fff; padding: 0;
}

.card-left {
  flex: 1; padding: 6px 10px;
  display: flex; flex-direction: column; justify-content: center; gap: 1px;
}

.card-header-row {
  display: flex; align-items: flex-start; justify-content: space-between;
  gap: 8px; margin-bottom: 3px;
}

.card-header-row h3 { margin: 0; font-size: 15px; font-weight: 700; word-break: break-all; }

.info-row {
  display: flex; gap: 18px; font-size: 10px; line-height: 1.5;
}

.card-right {
  width: 36mm; display: flex; align-items: center; justify-content: center;
  border-left: 1px dashed #94a3b8; padding: 4px;
}

@media print {
  .toolbar, .el-alert { display: none; }
  .kanban-card { display: none; border: 1px solid #000; }
  .kanban-card.printing-card { display: flex; margin: 0 auto; page-break-after: always; }
  .card-right { border-left-color: #000; }
  .kanban-actions { display: none; }
}
</style>

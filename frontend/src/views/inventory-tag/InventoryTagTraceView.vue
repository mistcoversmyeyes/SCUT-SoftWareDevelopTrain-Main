<template>
  <section class="module-shell">
    <el-card>
      <template #header>
        <h2>库存标签追溯</h2>
      </template>

      <el-alert v-if="fetchError" type="error" :title="fetchError" :closable="false" show-icon />

      <el-form inline class="query-form" @submit.prevent="queryTrace">
        <el-form-item label="库存标签码">
          <el-input v-model="inventoryTagCode" size="default" clearable placeholder="输入库存标签码" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="queryTrace">查询</el-button>
          <el-button @click="resetTrace">清空</el-button>
        </el-form-item>
      </el-form>

      <el-card v-if="traceData" class="result-card" shadow="never">
        <template #header>
          <div class="result-header">
            <span>追溯结果</span>
            <div class="result-actions">
              <el-button size="small" type="primary" @click="inventoryTagDialogVisible = true">查看/打印库存标签</el-button>
            </div>
          </div>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="库存标签码">{{ traceData.inventoryTagCode }}</el-descriptions-item>
          <el-descriptions-item label="库存标签状态">{{ traceData.inventoryTagStatus }}</el-descriptions-item>
          <el-descriptions-item label="所属入库单">{{ traceData.inboundNo }}</el-descriptions-item>
          <el-descriptions-item label="物料">{{ traceData.materialCode }} {{ traceData.materialName }}</el-descriptions-item>
          <el-descriptions-item label="库位">{{ traceData.locationCode }} {{ traceData.locationName }}</el-descriptions-item>
          <el-descriptions-item label="扫码时间">{{ formatDateTime(traceData.scannedAt) }}</el-descriptions-item>
          <el-descriptions-item label="库存流水">{{ traceData.movementNo || '未生成' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-empty v-else-if="searched" description="未查询到库存标签信息" />
    </el-card>

    <!-- InventoryTag card dialog -->
    <el-dialog v-model="inventoryTagDialogVisible" title="库存标签卡" width="520px" :close-on-click-modal="false">
      <article class="inventoryTag-card-dialog" ref="inventoryTagCardRef">
        <div class="card-left">
          <div class="card-header-row">
            <h3>{{ traceData?.inventoryTagCode }}</h3>
          </div>
          <div class="info-row">
            <span><strong>状态</strong> {{ traceData?.inventoryTagStatus }}</span>
            <span><strong>入库单</strong> {{ traceData?.inboundNo }}</span>
          </div>
          <div class="info-row">
            <span><strong>物料</strong> {{ traceData?.materialCode }} {{ traceData?.materialName }}</span>
          </div>
          <div class="info-row">
            <span><strong>库位</strong> {{ traceData?.locationCode }} {{ traceData?.locationName }}</span>
          </div>
        </div>
        <div class="card-right">
          <img v-if="qrDataUrl" :src="qrDataUrl" alt="QR" width="80" height="80" />
        </div>
      </article>
      <template #footer>
        <el-button @click="inventoryTagDialogVisible = false">关闭</el-button>
        <el-button type="success" @click="handleSaveInventoryTag">保存为图片</el-button>
        <el-button type="primary" @click="handlePrintInventoryTag">打印</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { fetchInventoryTagTrace } from '../../api/inventoryTag'
import { saveAsImage } from '../../composables/useSaveImage'

const inventoryTagCode = ref('')
const loading = ref(false)
const fetchError = ref('')
const traceData = ref(null)
const searched = ref(false)

const inventoryTagDialogVisible = ref(false)
const inventoryTagCardRef = ref(null)
const qrDataUrl = ref('')

watch(inventoryTagDialogVisible, async (visible) => {
  if (visible && traceData.value?.inventoryTagCode) {
    await nextTick()
    try { qrDataUrl.value = await QRCode.toDataURL(traceData.value.inventoryTagCode, { width: 80 }) }
    catch { qrDataUrl.value = '' }
  }
})

async function queryTrace() {
  const code = inventoryTagCode.value.trim()
  if (!code) { fetchError.value = '请输入库存标签码'; return }
  loading.value = true; fetchError.value = ''; searched.value = true; traceData.value = null
  try { traceData.value = await fetchInventoryTagTrace(code) }
  catch (error) {
    const message = error.response?.data?.message || '未查询到库存标签或请求异常'
    if (error.response?.status === 404) { traceData.value = null }
    else { fetchError.value = message }
  } finally { loading.value = false }
}

function resetTrace() {
  inventoryTagCode.value = ''; traceData.value = null; searched.value = false; fetchError.value = ''
}

function formatDateTime(value) {
  if (!value) return '-'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString('zh-CN')
}

function handlePrintInventoryTag() { window.print() }

async function handleSaveInventoryTag() {
  if (!inventoryTagCardRef.value) { ElMessage.error('未找到库存标签卡片'); return }
  try {
    await saveAsImage(inventoryTagCardRef.value, traceData.value?.inventoryTagCode || '库存标签')
    ElMessage.success('已保存为图片')
  } catch { ElMessage.error('保存失败') }
}
</script>

<style scoped>
.module-shell { min-height: 360px; }
.query-form { margin-bottom: 12px; }
.result-card { margin-top: 12px; }
h2 { margin: 0; }
.result-header { display: flex; justify-content: space-between; align-items: center; }
.result-actions { display: flex; gap: 8px; }

/* InventoryTag card in dialog */
.inventoryTag-card-dialog {
  display: flex; width: 100%; border: 1px solid #111827; background: #fff;
}
.card-left {
  flex: 1; padding: 10px 14px;
  display: flex; flex-direction: column; justify-content: center; gap: 2px;
}
.card-header-row h3 { margin: 0 0 6px; font-size: 16px; font-weight: 700; word-break: break-all; }
.info-row { display: flex; gap: 18px; font-size: 12px; line-height: 1.6; }
.card-right {
  width: 100px; display: flex; align-items: center; justify-content: center;
  border-left: 1px dashed #94a3b8; padding: 8px; flex-shrink: 0;
}

@media print {
  .el-dialog, .el-overlay { position: static !important; }
  :deep(.el-dialog__header), :deep(.el-dialog__footer) { display: none; }
  :deep(.el-dialog__body) { padding: 0; }
  .inventoryTag-card-dialog { border: 1px solid #000; }
  .card-right { border-left-color: #000; }
}
</style>

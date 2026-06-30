<template>
  <section class="scan-page">
    <!-- ═══════ 顶部上下文栏 ═══════ -->
    <div class="context-bar">
      <div class="context-left">
        <h2>出库扫码</h2>
      </div>
      <div class="context-right">
        <el-tag v-if="mode==='force'" type="danger" size="large" effect="dark">强制模式</el-tag>
        <el-tag v-else-if="mode==='no-order'" type="warning" size="large" effect="dark">不带单模式</el-tag>
        <el-tag v-else type="primary" size="large" effect="dark">正常模式</el-tag>
        <span v-if="outboundNo" class="context-order-no">{{ outboundNo }}</span>
      </div>
    </div>

    <!-- ═══════ 模式提示 ═══════ -->
    <el-alert v-if="mode==='force'" type="danger" :closable="false" show-icon
      title="强制模式：扫描库存标签将强制执行出库，被锁定的库存标签会被抢锁并算到本出库单。" style="margin-bottom:20px" />
    <el-alert v-if="mode==='no-order'" type="warning" :closable="false" show-icon
      title="不带单模式：直接扫描库存标签码出库。库存标签如被锁定将强制抢锁并记录审计日志。" style="margin-bottom:20px" />
    <el-alert v-if="mode==='normal' && orderId" type="info" :closable="false" show-icon
      title="按推荐方案扫码出库；扫描非推荐库存标签时需要二次确认。" style="margin-bottom:20px" />

    <!-- ═══════ 扫码区（主角） ═══════ -->
    <el-card class="scan-main" shadow="hover">
      <!-- 摄像头预览框 -->
      <div class="camera-wrapper">
        <div id="qr-reader" class="camera-view" :class="{ active: cameraActive }" v-show="cameraActive"></div>
        <div class="camera-placeholder" v-show="!cameraActive">
          <div class="placeholder-icon"><el-icon :size="48"><Camera /></el-icon></div>
          <div class="placeholder-text">点击下方按钮启动摄像头</div>
          <div class="placeholder-sub">或选择本地二维码图片文件</div>
        </div>
      </div>

      <div class="camera-actions">
        <el-button type="primary" :icon="Camera" size="large" @click="handleScanner('camera')">
          {{ cameraActive ? '关闭摄像头' : '启动摄像头' }}
        </el-button>
        <el-button size="large" @click="handleScanner('file')">选择文件</el-button>
      </div>

      <!-- 扫描错误提示 -->
      <el-alert v-if="scannerError" type="error" :title="scannerError" :closable="true"
        @close="scannerError=''" style="margin:12px 0" />

      <!-- ═══════ 或 — 手动输入（配角） ═══════ -->
      <el-divider content-position="center">
        <span class="divider-label">或手动输入</span>
      </el-divider>

      <div class="manual-input">
        <el-select
          v-if="mode==='normal' && recommendationLines.length"
          v-model="activeLineId"
          size="large"
          placeholder="选择出库明细行"
          style="width:300px"
        >
          <el-option
            v-for="line in recommendationLines"
            :key="line.outboundOrderLineId"
            :value="line.outboundOrderLineId"
            :label="`行${line.lineNo} ${line.materialCode || ''} ${line.materialName || ''}`"
          />
        </el-select>
        <el-input ref="scanInputRef" v-model="inventoryTagCode" size="large"
          placeholder="请输入库存标签码" :disabled="scanning" clearable
          style="width:360px" @keyup.enter="handleScan">
          <template #prepend>库存标签码</template>
        </el-input>
        <el-button type="primary" size="large" :loading="scanning"
          style="min-width:120px" @click="handleScan">
          确认出库
        </el-button>
      </div>

      <!-- ═══════ 库存标签预览（紧凑单行） ═══════ -->
      <div v-if="inventoryTagPreview" class="inventoryTag-preview">
        <div class="preview-row">
          <span class="preview-material">{{ inventoryTagPreview.materialCode }} {{ inventoryTagPreview.materialName }}</span>
          <span class="preview-sep">·</span>
          <span class="preview-loc">{{ inventoryTagPreview.locationName }}</span>
          <span class="preview-sep">·</span>
          <el-tag :type="inventoryTagPreview.inventoryTagStatus === 'LOCKED' ? 'danger' : 'success'" size="small">
            {{ inventoryTagPreview.inventoryTagStatus === 'LOCKED' ? '已锁定' : '空闲' }}
          </el-tag>
          <span class="preview-sep">·</span>
          <span class="preview-qty">
            剩余 <strong>{{ inventoryTagPreview.boardQty - (inventoryTagPreview.pickedQty || 0) }}</strong>
            / 总量 {{ inventoryTagPreview.boardQty }}
          </span>
        </div>
      </div>

      <!-- ═══════ 错误提示 ═══════ -->
      <el-alert v-if="errorMessage" type="error" :title="errorMessage" show-icon
        :closable="false" style="margin-top:12px" />
    </el-card>

    <el-card v-if="mode==='normal' && recommendationLines.length" class="recommendation-card" shadow="hover">
      <template #header>
        <div class="history-header">
          <span>推荐出库方案</span>
          <el-button size="small" text type="primary" :loading="loadingRecommendation" @click="loadRecommendation">
            刷新推荐
          </el-button>
        </div>
      </template>
      <el-table :data="recommendationLines" border stripe size="small">
        <el-table-column prop="lineNo" label="行号" width="70" />
        <el-table-column label="物料" min-width="180">
          <template #default="{ row }">
            {{ row.materialCode }} {{ row.materialName }}
          </template>
        </el-table-column>
        <el-table-column prop="neededQty" label="待出数量" width="100" align="right" />
        <el-table-column label="推荐库存标签" min-width="260">
          <template #default="{ row }">
            <div class="recommend-tags">
              <el-tag
                v-for="item in row.recommendations || []"
                :key="item.inventoryTagCode"
                size="small"
                effect="plain"
              >
                {{ item.inventoryTagCode }}
              </el-tag>
              <span v-if="!row.recommendations?.length" class="empty-tip">暂无推荐</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ═══════ 已扫记录 ═══════ -->
    <el-card v-if="scanHistory.length" class="history-card" shadow="hover">
      <template #header>
        <div class="history-header">
          <span>已扫记录</span>
          <el-button size="small" text type="danger" @click="scanHistory=[]">清除记录</el-button>
        </div>
      </template>
      <div class="history-list">
        <div v-for="(item, i) in scanHistory" :key="i" class="history-item">
          <span class="history-code">{{ item.inventoryTagCode }}</span>
          <span class="history-arrow">→</span>
          <span class="history-material">{{ item.materialCode }} {{ item.materialName }}</span>
          <span class="history-qty">已出 {{ item.pickedQty }}</span>
          <span class="history-time">{{ item.time }}</span>
        </div>
      </div>
    </el-card>

    <!-- ═══════ 物料清单表（底部全宽） ═══════ -->
    <!-- Force: 符合条件物料清单 -->
    <el-card v-if="mode==='force' && forceTable.length" class="material-card" shadow="hover">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <h3 style="margin:0">当前符合条件物料清单</h3>
          <span style="font-size:13px;color:#909399">{{ forcePickedCount }}/{{ forceTable.length }} 已出</span>
        </div>
      </template>
      <el-table :data="forceTable" border stripe size="small">
        <el-table-column label="出库状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row._picked ? 'success' : 'info'" size="small">
              {{ row._picked ? '已出库' : '未出库' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="库存标签码" min-width="240">
          <template #default="{ row }">
            <code>{{ row.inventoryTagCode }}</code>
            <el-button size="small" text type="primary" style="margin-left:6px" @click="copyText(row.inventoryTagCode)">
              <el-icon><DocumentCopy /></el-icon>
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="materialCode" label="物料编码" width="150" />
        <el-table-column prop="materialName" label="物料名称" min-width="160" />
        <el-table-column prop="locationName" label="库位" width="140" />
        <el-table-column prop="qty" label="数量" width="100" align="right" />
        <el-table-column label="锁状态" width="170">
          <template #default="{ row }">
            <el-tag v-if="!row.locked" type="success" size="small">空闲</el-tag>
            <el-tag v-else type="danger" size="small">
              {{ row.lockedByOutboundNo ? '已锁定(' + row.lockedByOutboundNo + ')' : '已锁定' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup>
import { nextTick, ref, watch, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Camera, DocumentCopy } from '@element-plus/icons-vue'
import { Html5Qrcode } from 'html5-qrcode'
import {
  lookupInventoryTag, pickWithOrder, pickWithOrderForce, pickNoOrder,
  fetchForceCandidates, fetchOutboundRecommendations
} from '../../api/outbound'
import {
  findRecommendedLineId,
  isRecommendedInventoryTag,
  pendingRecommendationLines
} from '../../utils/outboundRecommendation'

const route = useRoute()
const router = useRouter()
const mode = computed(() => route.query.mode || 'normal')
const orderId = computed(() => route.query.orderId ? Number(route.query.orderId) : null)
const outboundNo = computed(() => route.query.outboundNo || '')

const inventoryTagCode = ref('')
const scanning = ref(false)
const inventoryTagPreview = ref(null)
const errorMessage = ref('')
const scanInputRef = ref()

const cameraActive = ref(false)
const scannerError = ref('')
let html5QrCode = null

const scanHistory = ref([])
const forceTable = ref([])
const recommendation = ref(null)
const loadingRecommendation = ref(false)
const activeLineId = ref(null)

const forcePickedCount = computed(() => forceTable.value.filter(r => r._picked).length)
const recommendationLines = computed(() => pendingRecommendationLines(recommendation.value))

const pickedInventoryTagCodes = ref(new Set())

function markPicked(code) {
  pickedInventoryTagCodes.value = new Set([...pickedInventoryTagCodes.value, code])
}

function applyPickedStatus(table) {
  for (const row of table) {
    row._picked = pickedInventoryTagCodes.value.has(row.inventoryTagCode)
  }
}

function checkAllDone() {
  const table = forceTable.value
  if (!table.length) return
  if (table.every(r => r._picked)) {
    ElMessage.success('本单全部库存标签已出库完成！')
    setTimeout(() => router.push('/outbound/orders'), 1500)
  }
}

// Load force candidates
async function loadForceTable() {
  if (mode.value !== 'force' || !orderId.value) return
  try {
    const result = await fetchForceCandidates(orderId.value)
    const all = []
    for (const line of (result.lines || [])) {
      for (const kb of (line.inventoryTags || [])) {
        all.push({
          inventoryTagCode: kb.inventoryTagCode, materialCode: line.materialCode,
          materialName: line.materialName, locationName: kb.locationName,
          qty: kb.qty, locked: kb.locked, _picked: false
        })
      }
    }
    forceTable.value = all
    applyPickedStatus(forceTable.value)
    checkAllDone()
  } catch { forceTable.value = [] }
}

async function loadRecommendation() {
  if (mode.value !== 'normal' || !orderId.value) return
  loadingRecommendation.value = true
  try {
    recommendation.value = await fetchOutboundRecommendations(orderId.value)
    if (!recommendationLines.value.some((line) => line.outboundOrderLineId === activeLineId.value)) {
      activeLineId.value = recommendationLines.value[0]?.outboundOrderLineId || null
    }
  } catch {
    recommendation.value = null
    activeLineId.value = null
  } finally {
    loadingRecommendation.value = false
  }
}

async function copyText(text) {
  try { await navigator.clipboard.writeText(text); ElMessage.success('已复制') }
  catch { ElMessage.error('复制失败') }
}

// Watch inventoryTag code for preview
let lookupTimer = null
watch(inventoryTagCode, (code) => {
  clearTimeout(lookupTimer)
  inventoryTagPreview.value = null
  const trimmed = code.trim()
  if (!trimmed) return
  lookupTimer = setTimeout(async () => {
    try { inventoryTagPreview.value = await lookupInventoryTag(trimmed) } catch { inventoryTagPreview.value = null }
  }, 400)
})

// QR Scanner
async function handleScanner(command) {
  scannerError.value = ''
  if (command === 'camera') await startCamera()
  else if (command === 'file') openFilePicker()
}

async function startCamera() {
  if (cameraActive.value) { stopCamera(); return }
  try {
    html5QrCode = new Html5Qrcode('qr-reader')
    cameraActive.value = true
    await nextTick()
    await html5QrCode.start(
      { facingMode: 'environment' },
      { fps: 10, qrbox: { width: 250, height: 250 } },
      (decodedText) => {
        inventoryTagCode.value = decodedText
        ElMessage.success('扫描成功: ' + decodedText)
        stopCamera()
      },
      () => {}
    )
  } catch (err) {
    scannerError.value = '摄像头启动失败: ' + (err.message || err)
    cameraActive.value = false
  }
}

function stopCamera() {
  if (html5QrCode) {
    try { html5QrCode.stop().then(() => {}).catch(() => {}) } catch {}
  }
  cameraActive.value = false
}

function openFilePicker() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.onchange = async (e) => {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      const tmp = document.createElement('div')
      tmp.id = 'qr-reader-tmp'; tmp.style.display = 'none'
      document.body.appendChild(tmp)
      const h5 = new Html5Qrcode('qr-reader-tmp')
      const result = await h5.scanFile(file, true)
      document.body.removeChild(tmp)
      if (result) { inventoryTagCode.value = result; ElMessage.success('识别成功: ' + result) }
    } catch (err) {
      scannerError.value = '二维码识别失败: ' + (err.message || err)
    }
  }
  input.click()
}

async function handleScan() {
  const code = inventoryTagCode.value.trim()
  if (!code) { errorMessage.value = '请先输入库存标签码'; return }
  scanning.value = true; errorMessage.value = ''
  const payload = { inventoryTagCode: code, outboundOrderId: orderId.value || undefined }
  try {
    let result
    if (mode.value === 'no-order') result = await pickNoOrder(payload)
    else if (mode.value === 'force') result = await pickWithOrderForce(payload)
    else {
      const lineId = resolveOutboundLineId(code)
      if (!lineId) {
        errorMessage.value = '请选择出库明细行'
        return
      }
      payload.outboundOrderLineId = lineId
      if (!isRecommendedInventoryTag(recommendation.value, lineId, code)) {
        await ElMessageBox.confirm(
          '当前出库库存标签不在推荐出库方案中，是否继续按非推荐方案出库？',
          '非推荐出库确认',
          { confirmButtonText: '继续出库', cancelButtonText: '取消', type: 'warning' }
        )
        payload.confirmNonRecommended = true
      }
      result = await pickWithOrder(payload)
    }

    // Add to history
    scanHistory.value.unshift({
      inventoryTagCode: result.inventoryTagCode,
      materialCode: result.materialCode,
      materialName: result.materialName,
      pickedQty: result.pickedQty,
      time: formatDateTime(result.occurredAt)
    })
    markPicked(result.inventoryTagCode)
    inventoryTagCode.value = ''; inventoryTagPreview.value = null
    loadForceTable(); loadRecommendation()
  } catch (error) {
    if (error === 'cancel' || error?.message === 'cancel') return
    // FIFO violation: show confirmation dialog and retry
    const isFifoViolation = error.response?.status === 409
        && error.response?.data?.message?.includes('FIFO')
    if (isFifoViolation) {
      try {
        await ElMessageBox.confirm(
          error.response.data.message,
          '非 FIFO 出库确认',
          { confirmButtonText: '继续出库', cancelButtonText: '取消', type: 'warning' }
        )
        payload.confirmNonFifo = true
        scanning.value = true; errorMessage.value = ''
        const retryResult = await pickWithOrder(payload)
        scanHistory.value.unshift({
          inventoryTagCode: retryResult.inventoryTagCode,
          materialCode: retryResult.materialCode,
          materialName: retryResult.materialName,
          pickedQty: retryResult.pickedQty,
          time: formatDateTime(retryResult.occurredAt)
        })
        markPicked(retryResult.inventoryTagCode)
        inventoryTagCode.value = ''; inventoryTagPreview.value = null
        loadForceTable(); loadRecommendation()
      } catch (retryError) {
        if (retryError === 'cancel' || retryError?.message === 'cancel') return
        errorMessage.value = retryError.response?.data?.message || retryError.message || '扫码失败'
      }
    } else {
      errorMessage.value = error.response?.data?.message || error.message || '扫码失败'
    }
  } finally {
    scanning.value = false
    nextTick(() => scanInputRef.value?.focus())
  }
}

function resolveOutboundLineId(code) {
  const recommendedLineId = findRecommendedLineId(recommendation.value, code)
  if (recommendedLineId) return recommendedLineId
  const previewMaterialId = inventoryTagPreview.value?.materialId
  if (previewMaterialId) {
    const matched = recommendationLines.value.find((line) => line.materialId === previewMaterialId)
    if (matched) return matched.outboundOrderLineId
  }
  return activeLineId.value
}

function formatDateTime(value) {
  if (!value) return '-'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString('zh-CN')
}

onMounted(() => {
  nextTick(() => scanInputRef.value?.focus())
  loadForceTable()
  loadRecommendation()
})
onBeforeUnmount(() => { stopCamera(); clearTimeout(lookupTimer) })
</script>

<style scoped>
.scan-page { max-width: 960px; margin: 0 auto; }

/* ---- 上下文栏 ---- */
.context-bar {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px; padding: 16px 20px;
  background: var(--el-fill-color-light); border-radius: 8px;
}
.context-bar h2 { margin: 0; font-size: 1.3rem; }
.context-right { display: flex; align-items: center; gap: 12px; }
.context-order-no {
  font-family: 'Courier New', monospace; font-size: 0.95rem;
  color: var(--el-text-color-secondary);
}

/* ---- 扫码主卡片 ---- */
.scan-main { margin-bottom: 20px; }

/* ---- 摄像头区 ---- */
.camera-wrapper { display: flex; justify-content: center; margin-bottom: 16px; }
.camera-view {
  width: 400px; height: 350px; border-radius: 12px; overflow: hidden;
  border: 2px dashed var(--el-border-color);
}
.camera-view.active { border-color: var(--el-color-primary); }
.camera-placeholder {
  width: 400px; height: 350px; border-radius: 12px;
  border: 2px dashed var(--el-border-color-darker);
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-secondary);
}
.placeholder-icon { font-size: 3rem; margin-bottom: 12px; }
.placeholder-text { font-size: 1rem; margin-bottom: 4px; }
.placeholder-sub { font-size: 0.85rem; opacity: 0.7; }

.camera-actions {
  display: flex; justify-content: center; gap: 12px; margin-bottom: 8px;
}

/* ---- 分隔线 ---- */
.divider-label { color: var(--el-text-color-secondary); font-size: 0.85rem; }

/* ---- 手动输入 ---- */
.manual-input {
  display: flex; align-items: center; gap: 12px;
  justify-content: center; flex-wrap: wrap; margin-bottom: 12px;
}

/* ---- 库存标签预览 ---- */
.inventoryTag-preview {
  background: var(--el-color-primary-light-9); border-radius: 8px;
  padding: 10px 16px; margin: 0 0 8px;
}
.preview-row {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  font-size: 0.9rem;
}
.preview-material { font-weight: 600; }
.preview-sep { color: var(--el-text-color-placeholder); }
.preview-loc { color: var(--el-text-color-secondary); }
.preview-qty { color: var(--el-text-color-secondary); }

/* ---- 已扫记录 ---- */
.history-card { margin-bottom: 20px; }
.recommendation-card { margin-bottom: 20px; }
.history-header { display: flex; justify-content: space-between; align-items: center; }
.recommend-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.empty-tip {
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}
.history-list { max-height: 200px; overflow-y: auto; }
.history-item {
  display: flex; align-items: center; gap: 10px; padding: 6px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 0.85rem;
}
.history-item:last-child { border-bottom: none; }
.history-code {
  font-family: 'Courier New', monospace; font-weight: 600;
  color: var(--el-color-primary);
}
.history-arrow { color: var(--el-text-color-placeholder); }
.history-material { flex: 1; }
.history-qty { color: var(--el-color-success); font-weight: 600; }
.history-time { color: var(--el-text-color-placeholder); font-size: 0.8rem; }

/* ---- 物料表 ---- */
.material-card code {
  font-family: 'Courier New', monospace; font-size: 0.82rem;
}
</style>

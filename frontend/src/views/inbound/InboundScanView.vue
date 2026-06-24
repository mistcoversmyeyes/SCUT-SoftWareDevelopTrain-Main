<template>
  <section class="scan-page">
    <!-- ═══════ 上下文栏 ═══════ -->
    <div class="context-bar">
      <div class="context-left"><h2>入库扫码</h2></div>
      <div class="context-right">
        <el-tag type="primary" size="large" effect="dark">扫码入库</el-tag>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom:20px"
      title="扫描库存标签二维码，核对信息后点击确认入库。如需覆盖计划库位，请在下拉框中选择目标库位。" />

    <!-- ═══════ 扫码主卡片 ═══════ -->
    <el-card class="scan-main" shadow="hover">
      <!-- 摄像头预览 -->
      <div class="camera-wrapper">
        <div id="inbound-qr-reader" class="camera-view" :class="{ active: cameraActive }" v-show="cameraActive"></div>
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

      <el-alert v-if="scannerError" type="error" :title="scannerError" :closable="true"
        @close="scannerError=''" style="margin:12px 0" />

      <!-- ═══════ 或 — 手动输入 ═══════ -->
      <el-divider content-position="center">
        <span class="divider-label">或手动输入</span>
      </el-divider>

      <div class="manual-input">
        <el-input ref="scanInputRef" v-model="inventoryTagCode" size="large"
          placeholder="请输入库存标签码" :disabled="scanning" clearable
          style="width:260px" @keyup.enter="handleScan">
          <template #prepend>库存标签码</template>
        </el-input>
        <el-select v-model="selectedLocationId" placeholder="留空则按计划库位入库"
          clearable filterable size="large" style="width:280px">
          <el-option v-for="loc in locationOptions" :key="loc.id" :value="loc.id"
            :label="`${loc.code} ${loc.name}`" />
        </el-select>
        <el-button type="primary" size="large" :loading="scanning"
          style="min-width:120px" @click="handleScan">
          确认入库
        </el-button>
      </div>

      <!-- ═══════ 库存标签预览（紧凑单行） ═══════ -->
      <div v-if="inventoryTagPreview" class="inventoryTag-preview">
        <div class="preview-row">
          <span class="preview-material">{{ inventoryTagPreview.materialCode }} {{ inventoryTagPreview.materialName }}</span>
          <span class="preview-sep">·</span>
          <span class="preview-loc">{{ inventoryTagPreview.locationName || '—' }}</span>
          <span class="preview-sep">·</span>
          <el-tag :type="inventoryTagPreview.inventoryTagStatus === 'RECEIVED' ? 'success' : 'info'" size="small">
            {{ inventoryTagPreview.inventoryTagStatus === 'RECEIVED' ? '已入库' : inventoryTagPreview.inventoryTagStatus === 'PRINTED' ? '待入库' : inventoryTagPreview.inventoryTagStatus }}
          </el-tag>
          <span class="preview-sep">·</span>
          <span class="preview-qty">
            总量 <strong>{{ inventoryTagPreview.boardQty }}</strong>
          </span>
          <span v-if="inventoryTagPreview.inboundNo" class="preview-sep">·</span>
          <span v-if="inventoryTagPreview.inboundNo" class="preview-order">{{ inventoryTagPreview.inboundNo }}</span>
        </div>
      </div>

      <!-- ═══════ 错误提示 ═══════ -->
      <el-alert v-if="errorMessage" type="error" :title="errorMessage" show-icon
        :closable="false" style="margin-top:12px" />
    </el-card>

    <!-- ═══════ 扫码成功结果 ═══════ -->
    <el-card v-if="scanResult" class="result-card" shadow="hover">
      <template #header>
        <div class="result-header">
          <span>入库成功</span>
          <el-tag type="success" size="small">已入库</el-tag>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="库存标签码">
          <code>{{ scanResult.inventoryTagCode }}</code>
        </el-descriptions-item>
        <el-descriptions-item label="入库单号">
          {{ scanResult.inboundNo }}
        </el-descriptions-item>
        <el-descriptions-item label="物料编码">
          {{ scanResult.materialCode }}
        </el-descriptions-item>
        <el-descriptions-item label="物料名称">
          {{ scanResult.materialName }}
        </el-descriptions-item>
        <el-descriptions-item label="收货数量">
          {{ scanResult.receivedQty }}
        </el-descriptions-item>
        <el-descriptions-item label="入库库位">
          {{ scanResult.locationName }}
        </el-descriptions-item>
        <el-descriptions-item label="入库单状态">
          {{ scanResult.orderStatus }}
        </el-descriptions-item>
        <el-descriptions-item label="扫码时间">
          {{ formatDateTime(scanResult.receivedAt) }}
        </el-descriptions-item>
      </el-descriptions>
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
          <span class="history-qty">入库 {{ item.receivedQty }} 件</span>
          <span class="history-time">{{ item.time }}</span>
        </div>
      </div>
    </el-card>
  </section>
</template>

<script setup>
import { nextTick, watch, ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'
import { Html5Qrcode } from 'html5-qrcode'
import { scanInbound, lookupInventoryTagInbound } from '../../api/inventory'
import { fetchMasterDataOptions } from '../../api/masterData'

const inventoryTagCode = ref('')
const scanning = ref(false)
const scanResult = ref(null)
const errorMessage = ref('')
const scanInputRef = ref()
const selectedLocationId = ref(null)
const locationOptions = ref([])
const scanHistory = ref([])

// ---- 摄像头 ----
const cameraActive = ref(false)
const scannerError = ref('')
let html5QrCode = null

// ---- 库存标签预览 ----
const inventoryTagPreview = ref(null)
let lookupTimer = null

async function handleScan() {
  const code = inventoryTagCode.value.trim()
  if (!code) {
    errorMessage.value = '请先输入库存标签码'
    scanResult.value = null
    return
  }

  scanning.value = true
  errorMessage.value = ''
  scanResult.value = null

  try {
    const result = await scanInbound(code, selectedLocationId.value)
    scanResult.value = result
    scanHistory.value.unshift({
      inventoryTagCode: result.inventoryTagCode,
      materialCode: result.materialCode,
      materialName: result.materialName,
      receivedQty: result.receivedQty,
      time: formatDateTime(result.receivedAt)
    })
    inventoryTagCode.value = ''
    selectedLocationId.value = null
    inventoryTagPreview.value = null
  } catch (error) {
    errorMessage.value =
      error.response?.data?.message ||
      error.message ||
      '扫码失败，请检查网络或后端服务'
  } finally {
    scanning.value = false
    nextTick(() => {
      scanInputRef.value?.focus()
    })
  }
}

// 库存标签码输入 → 实时预览
watch(inventoryTagCode, (code) => {
  clearTimeout(lookupTimer)
  inventoryTagPreview.value = null
  if (errorMessage.value) errorMessage.value = ''
  const trimmed = code.trim()
  if (!trimmed) return
  lookupTimer = setTimeout(async () => {
    try {
      inventoryTagPreview.value = await lookupInventoryTagInbound(trimmed)
    } catch {
      inventoryTagPreview.value = null
    }
  }, 400)
})

// ---- QR 扫描器 ----
async function handleScanner(command) {
  scannerError.value = ''
  if (command === 'camera') await startCamera()
  else if (command === 'file') openFilePicker()
}

async function startCamera() {
  if (cameraActive.value) { stopCamera(); return }
  try {
    html5QrCode = new Html5Qrcode('inbound-qr-reader')
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
      tmp.id = 'inbound-qr-tmp'; tmp.style.display = 'none'
      document.body.appendChild(tmp)
      const h5 = new Html5Qrcode('inbound-qr-tmp')
      const result = await h5.scanFile(file, true)
      document.body.removeChild(tmp)
      if (result) {
        inventoryTagCode.value = result
        ElMessage.success('识别成功: ' + result)
      }
    } catch (err) {
      scannerError.value = '二维码识别失败: ' + (err.message || err)
    }
  }
  input.click()
}

function formatDateTime(value) {
  if (!value) return '-'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString('zh-CN')
}

onMounted(async () => {
  nextTick(() => scanInputRef.value?.focus())
  try {
    const data = await fetchMasterDataOptions()
    locationOptions.value = data.locations || []
  } catch {
    // keep empty
  }
})

onBeforeUnmount(() => {
  stopCamera()
  clearTimeout(lookupTimer)
})
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
  background: var(--el-fill-color-lighter); color: var(--el-text-color-secondary);
}
.placeholder-icon { font-size: 3rem; margin-bottom: 12px; }
.placeholder-text { font-size: 1rem; margin-bottom: 4px; }
.placeholder-sub { font-size: 0.85rem; opacity: 0.7; }

.camera-actions { display: flex; justify-content: center; gap: 12px; margin-bottom: 8px; }

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
.preview-order {
  font-family: 'Courier New', monospace; font-size: 0.82rem;
  color: var(--el-color-primary);
}

/* ---- 结果卡片 ---- */
.result-card { margin-bottom: 20px; }
.result-header { display: flex; justify-content: space-between; align-items: center; }
.result-card code {
  font-family: 'Courier New', monospace; font-size: 0.85rem;
}

/* ---- 已扫记录 ---- */
.history-card { margin-bottom: 20px; }
.history-header { display: flex; justify-content: space-between; align-items: center; }
.history-list { max-height: 240px; overflow-y: auto; }
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
</style>

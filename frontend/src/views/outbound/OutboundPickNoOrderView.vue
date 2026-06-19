<template>
  <section class="scan-page">
    <!-- ═══════ 顶部上下文栏 ═══════ -->
    <div class="context-bar">
      <div class="context-left"><h2>不带单出库</h2></div>
      <div class="context-right">
        <el-tag type="warning" size="large" effect="dark">不走单模式</el-tag>
      </div>
    </div>

    <!-- ═══════ 模式提示 ═══════ -->
    <el-alert type="warning" :closable="false" show-icon style="margin-bottom:20px"
      title="不走单模式：直接扫描看板码出库。看板如已被锁定将强制抢锁并记录审计日志。" />

    <!-- ═══════ 扫码主卡片 ═══════ -->
    <el-card class="scan-main" shadow="hover">
      <!-- 摄像头预览框 -->
      <div class="camera-wrapper">
        <div id="no-order-qr-reader" class="camera-view" :class="{ active: cameraActive }" v-show="cameraActive"></div>
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

      <!-- ═══════ 或 — 手动输入 ═══════ -->
      <el-divider content-position="center">
        <span class="divider-label">或手动输入</span>
      </el-divider>

      <div class="manual-input">
        <el-input ref="scanInputRef" v-model="kanbanCode" size="large"
          placeholder="请输入看板码" :disabled="scanning" clearable
          style="width:240px" @keyup.enter="handleScan">
          <template #prepend>看板码</template>
        </el-input>
        <el-input-number v-model="scanQty" :min="1" :step="1" :precision="0"
          size="large" placeholder="默认全量" style="width:170px" />
        <el-button type="primary" size="large" :loading="scanning"
          style="min-width:120px" @click="handleScan">
          确认出库
        </el-button>
      </div>

      <!-- ═══════ 看板预览（紧凑单行） ═══════ -->
      <div v-if="kanbanPreview" class="kanban-preview">
        <div class="preview-row">
          <span class="preview-material">{{ kanbanPreview.materialCode }} {{ kanbanPreview.materialName }}</span>
          <span class="preview-sep">·</span>
          <span class="preview-loc">{{ kanbanPreview.locationName || '—' }}</span>
          <span class="preview-sep">·</span>
          <el-tag :type="kanbanPreview.kanbanStatus === 'LOCKED' ? 'danger' : 'success'" size="small">
            {{ kanbanPreview.kanbanStatus === 'LOCKED' ? '已锁定' : kanbanPreview.kanbanStatus === 'RECEIVED' ? '空闲' : kanbanPreview.kanbanStatus }}
          </el-tag>
          <span class="preview-sep">·</span>
          <span class="preview-qty">
            剩余 <strong>{{ kanbanPreview.pickedQty != null ? (kanbanPreview.boardQty - kanbanPreview.pickedQty) : kanbanPreview.boardQty }}</strong>
            / 总量 {{ kanbanPreview.boardQty }}
          </span>
        </div>
      </div>

      <!-- ═══════ 错误提示 ═══════ -->
      <el-alert v-if="errorMessage" type="error" :title="errorMessage" show-icon
        :closable="false" style="margin-top:12px" />
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
          <span class="history-code">{{ item.kanbanCode }}</span>
          <span class="history-arrow">→</span>
          <span class="history-material">{{ item.materialCode }} {{ item.materialName }}</span>
          <span class="history-qty">已出 {{ item.pickedQty }}</span>
          <span class="history-time">{{ item.time }}</span>
        </div>
      </div>
    </el-card>

    <el-empty v-if="!kanbanPreview && !scanHistory.length && !loadingQr" description="请扫描看板二维码开始出库" />
  </section>
</template>

<script setup>
import { nextTick, watch, ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'
import { Html5Qrcode } from 'html5-qrcode'
import { lookupKanban, pickNoOrder } from '../../api/outbound'

const kanbanCode = ref('')
const scanQty = ref(undefined)
const scanning = ref(false)
const kanbanPreview = ref(null)
const errorMessage = ref('')
const scanInputRef = ref()

const cameraActive = ref(false)
const scannerError = ref('')
let html5QrCode = null

const scanHistory = ref([])
const loadingQr = ref(false)

// 看板码输入 → 实时预览
let lookupTimer = null
watch(kanbanCode, (code) => {
  clearTimeout(lookupTimer)
  kanbanPreview.value = null
  if (errorMessage.value) errorMessage.value = ''
  const trimmed = code.trim()
  if (!trimmed) return
  lookupTimer = setTimeout(async () => {
    try {
      kanbanPreview.value = await lookupKanban(trimmed)
    } catch {
      kanbanPreview.value = null
    }
  }, 400)
})

// ---- 扫码出库 ----
async function handleScan() {
  const code = kanbanCode.value.trim()
  if (!code) { errorMessage.value = '请先输入看板码'; return }
  scanning.value = true; errorMessage.value = ''
  try {
    const payload = { kanbanCode: code, qty: scanQty.value || undefined }
    const result = await pickNoOrder(payload)

    scanHistory.value.unshift({
      kanbanCode: result.kanbanCode,
      materialCode: result.materialCode,
      materialName: result.materialName,
      pickedQty: result.pickedQty,
      time: formatDateTime(result.occurredAt)
    })
    kanbanCode.value = ''; scanQty.value = undefined; kanbanPreview.value = null
  } catch (error) {
    errorMessage.value = error.response?.data?.message || error.message || '出库失败'
  } finally {
    scanning.value = false
    nextTick(() => scanInputRef.value?.focus())
  }
}

// ---- QR 扫描器 ----
async function handleScanner(command) {
  scannerError.value = ''
  if (command === 'camera') await startCamera()
  else if (command === 'file') openFilePicker()
}

async function startCamera() {
  if (cameraActive.value) { stopCamera(); return }
  try {
    html5QrCode = new Html5Qrcode('no-order-qr-reader')
    cameraActive.value = true
    await nextTick()
    await html5QrCode.start(
      { facingMode: 'environment' },
      { fps: 10, qrbox: { width: 250, height: 250 } },
      (decodedText) => {
        kanbanCode.value = decodedText
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
      tmp.id = 'no-order-qr-tmp'; tmp.style.display = 'none'
      document.body.appendChild(tmp)
      const h5 = new Html5Qrcode('no-order-qr-tmp')
      const result = await h5.scanFile(file, true)
      document.body.removeChild(tmp)
      if (result) {
        kanbanCode.value = result
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

onMounted(() => { nextTick(() => scanInputRef.value?.focus()) })
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

/* ---- 看板预览 ---- */
.kanban-preview {
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

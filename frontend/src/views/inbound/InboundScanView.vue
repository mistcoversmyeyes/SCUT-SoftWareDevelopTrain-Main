<template>
  <section class="scan-page">
    <div class="context-bar">
      <div class="context-left"><h2>入库扫码</h2></div>
      <div class="context-right">
        <el-tag type="success" size="large" effect="dark">入库收货</el-tag>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom:20px"
      title="扫描看板码完成入库收货，库存将自动增加。" />

    <el-card class="scan-main" shadow="hover">
      <!-- Camera -->
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

      <el-alert v-if="scannerError" type="error" :title="scannerError" :closable="true" @close="scannerError=''" style="margin:12px 0" />

      <el-divider content-position="center">
        <span class="divider-label">或手动输入</span>
      </el-divider>

      <div class="manual-input">
        <el-input ref="scanInputRef" v-model="kanbanCode" size="large"
          placeholder="请输入看板码" :disabled="scanning" clearable
          style="flex:1;max-width:400px" @keyup.enter="handleScan">
          <template #prepend>看板码</template>
        </el-input>
        <el-button type="primary" size="large" :loading="scanning" @click="handleScan">确认入库</el-button>
      </div>

      <!-- Kanban preview -->
      <div v-if="kanbanPreview" class="kanban-preview">
        <div class="preview-row">
          <span class="preview-material">{{ kanbanPreview.materialCode }} {{ kanbanPreview.materialName }}</span>
          <span class="preview-sep">·</span>
          <span class="preview-loc">{{ kanbanPreview.locationName }}</span>
          <span class="preview-sep">·</span>
          <el-tag :type="kanbanPreview.kanbanStatus === 'LOCKED' ? 'danger' : 'info'" size="small">
            {{ kanbanPreview.kanbanStatus }}
          </el-tag>
          <span class="preview-sep">·</span>
          <span class="preview-qty">看板数量 <strong>{{ kanbanPreview.boardQty }}</strong></span>
        </div>
      </div>

      <el-alert v-if="errorMessage" type="error" :title="errorMessage" show-icon :closable="false" style="margin-top:12px" />
    </el-card>

    <!-- Scan history -->
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
          <span class="history-qty">入 {{ item.qty }}</span>
          <span class="history-time">{{ item.time }}</span>
        </div>
      </div>
    </el-card>
  </section>
</template>

<script setup>
import { nextTick, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'
import { Html5Qrcode } from 'html5-qrcode'
import { scanInbound, lookupKanbanInbound } from '../../api/inventory'

const kanbanCode = ref('')
const scanning = ref(false)
const scanResult = ref(null)
const kanbanPreview = ref(null)
const errorMessage = ref('')
const scanInputRef = ref()

const cameraActive = ref(false)
const scannerError = ref('')
let html5QrCode = null

const scanHistory = ref([])

let lookupTimer = null
watch(kanbanCode, (code) => {
  clearTimeout(lookupTimer)
  kanbanPreview.value = null
  const trimmed = code.trim()
  if (!trimmed) return
  lookupTimer = setTimeout(async () => {
    try { kanbanPreview.value = await lookupKanbanInbound(trimmed) } catch { kanbanPreview.value = null }
  }, 400)
})

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
      (decodedText) => { kanbanCode.value = decodedText; ElMessage.success('扫描成功'); stopCamera() },
      () => {}
    )
  } catch (err) { scannerError.value = '摄像头启动失败: ' + (err.message||err); cameraActive.value = false }
}

function stopCamera() {
  if (html5QrCode) { try { html5QrCode.stop().then(()=>{}).catch(()=>{}) } catch {} }
  cameraActive.value = false
}

function openFilePicker() {
  const input = document.createElement('input')
  input.type = 'file'; input.accept = 'image/*'
  input.onchange = async (e) => {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      const tmp = document.createElement('div'); tmp.id='qr-reader-tmp'; tmp.style.display='none'
      document.body.appendChild(tmp)
      const h5 = new Html5Qrcode('qr-reader-tmp')
      const result = await h5.scanFile(file, true)
      document.body.removeChild(tmp)
      if (result) { kanbanCode.value = result; ElMessage.success('识别成功') }
    } catch (err) { scannerError.value = '识别失败: '+(err.message||err) }
  }
  input.click()
}

async function handleScan() {
  const code = kanbanCode.value.trim()
  if (!code) { errorMessage.value='请先输入看板码'; return }
  scanning.value = true; errorMessage.value = ''
  try {
    const result = await scanInbound(code)
    scanHistory.value.unshift({
      kanbanCode: result.kanbanCode,
      materialCode: result.materialCode,
      materialName: result.materialName,
      qty: result.receivedQty,
      time: fmt(result.receivedAt)
    })
    kanbanCode.value = ''; kanbanPreview.value = null
  } catch (error) {
    errorMessage.value = error.response?.data?.message || error.message || '扫码失败'
  } finally {
    scanning.value = false
    nextTick(() => scanInputRef.value?.focus())
  }
}

function fmt(v) {
  if (!v) return '-'
  const d = new Date(v)
  return Number.isNaN(d.getTime()) ? v : d.toLocaleString('zh-CN')
}

onMounted(() => { nextTick(() => scanInputRef.value?.focus()) })
onBeforeUnmount(() => { stopCamera(); clearTimeout(lookupTimer) })
</script>

<style scoped>
.scan-page { max-width:960px; margin:0 auto; }
.context-bar { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; padding:16px 20px; background:var(--el-fill-color-light); border-radius:8px; }
.context-bar h2 { margin:0; font-size:1.3rem; }

.scan-main { margin-bottom:20px; }
.camera-wrapper { display:flex; justify-content:center; margin-bottom:16px; }
.camera-view { width:400px; height:350px; border-radius:12px; overflow:hidden; border:2px dashed var(--el-border-color); }
.camera-view.active { border-color:var(--el-color-primary); }
.camera-placeholder { width:400px; height:350px; border-radius:12px; border:2px dashed var(--el-border-color-darker); display:flex; flex-direction:column; align-items:center; justify-content:center; background:var(--el-fill-color-lighter); color:var(--el-text-color-secondary); }
.placeholder-icon { font-size:3rem; margin-bottom:12px; }
.placeholder-text { font-size:1rem; margin-bottom:4px; }
.placeholder-sub { font-size:0.85rem; opacity:0.7; }
.camera-actions { display:flex; justify-content:center; gap:12px; margin-bottom:8px; }
.divider-label { color:var(--el-text-color-secondary); font-size:0.85rem; }

.manual-input { display:flex; align-items:center; gap:12px; justify-content:center; flex-wrap:wrap; margin-bottom:12px; }

.kanban-preview { background:var(--el-color-primary-light-9); border-radius:8px; padding:10px 16px; margin:0 0 8px; }
.preview-row { display:flex; align-items:center; gap:8px; flex-wrap:wrap; font-size:0.9rem; }
.preview-material { font-weight:600; }
.preview-sep { color:var(--el-text-color-placeholder); }
.preview-loc { color:var(--el-text-color-secondary); }
.preview-qty { color:var(--el-text-color-secondary); }

.history-card { margin-bottom:20px; }
.history-header { display:flex; justify-content:space-between; align-items:center; }
.history-list { max-height:200px; overflow-y:auto; }
.history-item { display:flex; align-items:center; gap:10px; padding:6px 0; border-bottom:1px solid var(--el-border-color-lighter); font-size:0.85rem; }
.history-item:last-child { border-bottom:none; }
.history-code { font-family:'Courier New',monospace; font-weight:600; color:var(--el-color-primary); }
.history-arrow { color:var(--el-text-color-placeholder); }
.history-material { flex:1; }
.history-qty { color:var(--el-color-success); font-weight:600; }
.history-time { color:var(--el-text-color-placeholder); font-size:0.8rem; }
</style>

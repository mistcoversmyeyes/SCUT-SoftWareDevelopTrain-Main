<template>
  <section class="scan-page">
    <!-- Context bar -->
    <div class="context-bar">
      <div class="context-left"><h2>带单出库</h2></div>
      <div class="context-right">
        <el-tag type="primary" size="large" effect="dark">出库单扫码</el-tag>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom:20px"
      title="扫描出库单二维码，加载锁定物料清单后跳转到扫码出库页面。" />

    <!-- Scan main card -->
    <el-card class="scan-main" shadow="hover">
      <!-- Camera -->
      <div class="camera-wrapper">
        <div id="pick-qr-reader" class="camera-view" :class="{ active: cameraActive }" v-show="cameraActive"></div>
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

      <!-- Manual input -->
      <el-divider content-position="center">
        <span class="divider-label">或手动输入</span>
      </el-divider>

      <div class="manual-input">
        <el-input v-model="orderQrCode" size="large"
          placeholder="请输入出库单号" clearable
          style="flex:1; max-width:380px" @keyup.enter="loadQrInfo">
          <template #prepend>出库单号</template>
        </el-input>
        <el-button type="primary" size="large" :loading="loadingQr" @click="loadQrInfo">开始拣货</el-button>
      </div>

      <el-alert v-if="errorMsg" type="error" :title="errorMsg" show-icon :closable="false" style="margin-top:12px" />
    </el-card>

    <!-- Pending orders (when no order loaded) -->
    <el-card v-if="!orderInfo && pendingOrders.length" class="pending-card" shadow="hover">
      <template #header><span>待处理的出库单</span></template>
      <el-table :data="pendingOrders" border stripe size="small"
        highlight-current-row @row-click="handlePendingClick" style="cursor:pointer">
        <el-table-column prop="outboundNo" label="出库单号" min-width="180" />
        <el-table-column label="供应商" min-width="140">
          <template #default="{ row }">{{ row.supplier?.name || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status==='LOCKED'?'':'warning'" size="small">
              {{ row.status==='LOCKED'?'已锁定':'拣货中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="plannedQty" label="计划量" width="100" align="right" />
        <el-table-column prop="pickedQty" label="已拣量" width="100" align="right" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="primary" size="small" text @click.stop="handlePendingClick(row)">继续拣货</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Order info + locked items (when order loaded) -->
    <template v-if="orderInfo">
      <el-card class="order-card" shadow="hover">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="出库单号">{{ orderInfo.outboundNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="orderInfo.status==='COMPLETED'?'success':'warning'">{{ orderInfo.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="供应商">{{ orderInfo.supplier?.name || '—' }}</el-descriptions-item>
          <el-descriptions-item label="计划总量">{{ orderInfo.plannedQty || 0 }}</el-descriptions-item>
          <el-descriptions-item label="已拣总量">{{ orderInfo.pickedQty || 0 }}</el-descriptions-item>
          <el-descriptions-item label="用途">{{ orderInfo.purpose || '—' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="items-card" shadow="hover">
        <template #header><span>锁定物料清单</span></template>
        <el-table :data="lockedItems" border stripe size="small" v-loading="loadingQr">
          <el-table-column label="行号" width="70">
            <template #default="{ row }">{{ row.lineNo }}</template>
          </el-table-column>
          <el-table-column prop="kanbanCode" label="看板码" min-width="200">
            <template #default="{ row }">
              <code>{{ row.kanbanCode }}</code>
              <el-button size="small" text type="primary" style="margin-left:4px" @click="copyCode(row.kanbanCode)">
                <el-icon><DocumentCopy /></el-icon>
              </el-button>
            </template>
          </el-table-column>
          <el-table-column prop="materialCode" label="物料编码" width="140" />
          <el-table-column prop="materialName" label="物料名称" min-width="150" />
          <el-table-column prop="locationName" label="库位" width="130" />
          <el-table-column prop="lockQty" label="锁定量" width="100" align="right" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.lockStatus==='LOCKED'?'warning':'info'" size="small">{{ row.lockStatus }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <div class="action-row">
        <el-button type="primary" size="large" @click="goScan('normal')">扫码出库</el-button>
        <el-button type="danger" size="large" @click="goScan('force')">强制扫码</el-button>
        <el-button type="warning" size="large" @click="handlePause">暂停拣货</el-button>
      </div>
    </template>

    <el-empty v-if="!orderInfo && !loadingQr && !errorMsg && !pendingOrders.length" description="请扫描出库单二维码开始拣货" />
  </section>
</template>

<script setup>
import { nextTick, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Camera, DocumentCopy } from '@element-plus/icons-vue'
import { Html5Qrcode } from 'html5-qrcode'
import { fetchQrInfo, fetchOutboundOrders } from '../../api/outbound'

const router = useRouter()
const route = useRoute()

const orderQrCode = ref('')
const orderInfo = ref(null)
const lockedItems = ref([])
const loadingQr = ref(false)
const errorMsg = ref('')
const pendingOrders = ref([])

const cameraActive = ref(false)
const scannerError = ref('')
let html5QrCode = null

async function handleScanner(command) {
  scannerError.value = ''
  if (command === 'camera') await startCamera()
  else if (command === 'file') openFilePicker()
}

async function startCamera() {
  if (cameraActive.value) { stopCamera(); return }
  try {
    html5QrCode = new Html5Qrcode('pick-qr-reader')
    cameraActive.value = true
    await nextTick()
    await html5QrCode.start(
      { facingMode: 'environment' },
      { fps: 10, qrbox: { width: 250, height: 250 } },
      (decodedText) => { orderQrCode.value = decodedText; ElMessage.success('已识别'); stopCamera(); loadQrInfo() },
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
      const tmp = document.createElement('div'); tmp.id='pick-qr-tmp'; tmp.style.display='none'
      document.body.appendChild(tmp)
      const h5 = new Html5Qrcode('pick-qr-tmp')
      const result = await h5.scanFile(file, true)
      document.body.removeChild(tmp)
      if (result) { orderQrCode.value = result; ElMessage.success('已识别'); loadQrInfo() }
    } catch (err) { scannerError.value = '识别失败: '+(err.message||err) }
  }
  input.click()
}

async function loadPendingOrders() {
  try { pendingOrders.value = await fetchOutboundOrders({ status: 'LOCKED,PICKING' }) }
  catch { pendingOrders.value = [] }
}

async function loadQrInfo() {
  const outboundNo = orderQrCode.value.trim()
  if (!outboundNo) { errorMsg.value = '请输入出库单号'; return }
  loadingQr.value = true; errorMsg.value = ''
  try {
    const result = await fetchQrInfo(outboundNo)
    orderInfo.value = result.order; lockedItems.value = result.lockedItems || []
  } catch (error) {
    errorMsg.value = error.response?.data?.message || '查询失败'
    orderInfo.value = null; lockedItems.value = []
  } finally { loadingQr.value = false }
}

async function handlePendingClick(row) { orderQrCode.value = row.outboundNo; await loadQrInfo() }

function goScan(mode) {
  if (!orderInfo.value) return
  router.push({ path:'/outbound/scan', query:{ mode, orderId:orderInfo.value.id, outboundNo:orderInfo.value.outboundNo } })
}

function handlePause() { ElMessage.success('拣货已暂停'); router.push('/outbound/orders') }

function copyCode(code) { navigator.clipboard.writeText(code); ElMessage.success('已复制') }

const routeOrderId = route.query.orderId
if (routeOrderId) { orderQrCode.value = String(routeOrderId); loadQrInfo() }

onMounted(() => { loadPendingOrders() })
onBeforeUnmount(() => { stopCamera() })
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

.pending-card, .order-card, .items-card { margin-bottom:20px; }
.pending-card :deep(.el-table__row:hover) { background-color:var(--el-fill-color-light); }

.items-card code { font-family:'Courier New',monospace; font-size:0.82rem; }

.action-row { display:flex; gap:16px; justify-content:center; margin:24px 0; }
</style>

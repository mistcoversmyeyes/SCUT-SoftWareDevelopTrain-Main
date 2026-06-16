<template>
  <section class="detail-page">
    <!-- ═══════ 元数据区（非打印） ═══════ -->
    <div class="meta-bar no-print">
      <el-card v-loading="loading" shadow="never">
        <el-alert v-if="errorMessage" type="error" :title="errorMessage" show-icon :closable="false" />

        <template v-if="order">
          <div class="meta-top">
            <div class="meta-info">
              <span class="meta-label">出库单号</span>
              <span class="meta-value mono">{{ order.outboundNo }}</span>
              <el-tag :type="statusType(order.status)" size="small" effect="dark">{{ statusLabel(order.status) }}</el-tag>
              <span class="meta-sep">|</span>
              <span class="meta-label">供应商</span>
              <span class="meta-value">{{ order.supplier?.name || '—' }}</span>
              <span class="meta-sep">|</span>
              <span class="meta-label">用途</span>
              <el-tag :type="purposeTag(order.purpose)" size="small" effect="plain">{{ purposeLabel(order.purpose) }}</el-tag>
            </div>
            <div class="meta-actions">
              <el-button size="small" @click="handleCopy">复制单号</el-button>
              <el-button v-if="order.status==='PICKING'" type="success" size="small" @click="handleContinuePicking">继续拣货</el-button>
              <el-button type="primary" size="small" @click="handlePrint">打印</el-button>
              <el-button type="success" size="small" @click="handleSaveImage">保存为图片</el-button>
            </div>
          </div>
          <div class="meta-sub">
            <span>来源单号: {{ order.sourceDocNo || '—' }}</span>
            <span class="meta-sep">|</span>
            <span>创建: {{ fmt(order.createdAt) }}</span>
            <span class="meta-sep">|</span>
            <span>释放: {{ fmt(order.releasedAt) }}</span>
            <span class="meta-sep">|</span>
            <span>完成: {{ fmt(order.completedAt) }}</span>
            <span class="meta-sep">|</span>
            <span>备注: {{ order.remark || '—' }}</span>
          </div>
        </template>
        <el-empty v-if="!order && !loading && !errorMessage" description="暂无数据" />
      </el-card>
    </div>

    <!-- ═══════ 拣货单（打印区） ═══════ -->
    <template v-if="order && showPickingSheet">
      <div class="picking-sheet" id="picking-sheet">
        <div class="sheet-header">
          <div class="sheet-qr" v-if="qrDataUrl">
            <img :src="qrDataUrl" alt="QR" width="120" height="120" />
          </div>
          <div class="sheet-title-area">
            <h1 class="sheet-title">出 库 拣 货 单</h1>
            <div class="sheet-meta">
              <div class="sheet-row">
                <span class="sheet-label">单号</span>
                <span class="sheet-value mono">{{ order.outboundNo }}</span>
              </div>
              <div class="sheet-row">
                <span class="sheet-label">供应商</span>
                <span class="sheet-value">{{ order.supplier?.code }} {{ order.supplier?.name }}</span>
              </div>
              <div class="sheet-row" v-if="order.sourceDocNo">
                <span class="sheet-label">来源单号</span>
                <span class="sheet-value">{{ order.sourceDocNo }}</span>
              </div>
              <div class="sheet-row">
                <span class="sheet-label">用途</span>
                <span class="sheet-value">{{ purposeLabel(order.purpose) }}</span>
              </div>
            </div>
          </div>
        </div>

        <table class="sheet-table">
          <thead>
            <tr>
              <th class="col-seq">序号</th>
              <th>物料编码</th>
              <th>物料名称</th>
              <th>库位</th>
              <th class="col-qty">计划数</th>
              <th class="col-qty">已拣数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(line, i) in order.lines" :key="line.id" :class="{ done: line.pickedQty >= line.plannedQty }">
              <td class="col-seq">{{ i + 1 }}</td>
              <td>{{ line.materialCode }}</td>
              <td>{{ line.materialName }}</td>
              <td>{{ line.locationName || '—' }}</td>
              <td class="col-qty">{{ line.plannedQty }}</td>
              <td class="col-qty">{{ line.pickedQty }}</td>
            </tr>
          </tbody>
        </table>

        <div class="sheet-summary">
          共 <strong>{{ order.lines?.length || 0 }}</strong> 种物料 &nbsp;|&nbsp;
          计划总计 <strong>{{ order.plannedQty || 0 }}</strong> &nbsp;|&nbsp;
          已拣总计 <strong>{{ order.pickedQty || 0 }}</strong>
        </div>
      </div>

      <!-- ═══════ 锁定看板明细 ═══════ -->
      <el-card v-if="lockDetails.length" class="kanban-detail-card no-print" shadow="hover">
        <template #header><span>锁定看板明细</span></template>
        <el-table :data="lockDetails" border stripe size="small">
          <el-table-column label="看板码" min-width="240">
            <template #default="{ row }">
              <code>{{ row.kanbanCode }}</code>
              <el-button size="small" text type="primary" style="margin-left:6px" @click="handleCopyCode(row.kanbanCode)">
                <el-icon><DocumentCopy /></el-icon>
              </el-button>
            </template>
          </el-table-column>
          <el-table-column prop="locationName" label="库位" width="140" />
          <el-table-column prop="lockQty" label="数量" width="100" align="right" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.lockStatus==='LOCKED'?'warning':'info'" size="small">{{ row.lockStatus }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
  </section>
</template>

<script setup>
import { computed, onBeforeMount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DocumentCopy } from '@element-plus/icons-vue'
import QRCode from 'qrcode'
import { fetchOutboundOrderById } from '../../api/outbound'
import { fetchLockDetails } from '../../api/outbound'
import { saveAsImage } from '../../composables/useSaveImage'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const order = ref(null)
const qrDataUrl = ref('')
const lockDetails = ref([])

const showPickingSheet = computed(() => {
  const s = order.value?.status
  return s === 'LOCKED' || s === 'PICKING'
})

const statusMap = { DRAFT:'草稿', LOCKED:'已锁定', PICKING:'拣货中', PARTIAL_SHIPPED:'部分发货', COMPLETED:'已完成', CANCELLED:'已取消' }
const statusTagType = { DRAFT:'info', LOCKED:'', PICKING:'warning', PARTIAL_SHIPPED:'success', COMPLETED:'success', CANCELLED:'danger' }
const purposeMap = { PRODUCTION_PICK:'生产领料', PICKING:'生产领料', RETURN:'退货', TRANSFER:'调拨', OTHER:'其他' }
const purposeTagMap = { PRODUCTION_PICK:'primary', PICKING:'primary', RETURN:'danger', TRANSFER:'warning', OTHER:'info' }

function statusType(s) { return statusTagType[s]||'info' }
function statusLabel(s) { return statusMap[s]||s||'未知' }
function purposeLabel(p) { return purposeMap[p]||p||'—' }
function purposeTag(p) { return purposeTagMap[p]||'info' }
function fmt(v) { if(!v) return '—'; const d=new Date(v); return Number.isNaN(d.getTime())?v:d.toLocaleString('zh-CN') }

function getOutboundId() { return Number(route.params.id) }

async function loadData() {
  const id = getOutboundId()
  if (!id) { errorMessage.value='出库单编号缺失'; return }
  loading.value=true; errorMessage.value=''
  try {
    order.value = await fetchOutboundOrderById(id)
    if (order.value?.outboundNo) {
      try { qrDataUrl.value = await QRCode.toDataURL(order.value.outboundNo, { width:120 }) }
      catch { qrDataUrl.value = '' }
    }
    if (showPickingSheet.value) {
      try { lockDetails.value = await fetchLockDetails(id) }
      catch { lockDetails.value = [] }
    }
  } catch(e) { errorMessage.value=e.response?.data?.message||'加载失败'; order.value=null }
  finally { loading.value=false }
}

function handleCopy() {
  if (order.value?.outboundNo) { navigator.clipboard.writeText(order.value.outboundNo); ElMessage.success('单号已复制') }
}
function handleCopyCode(code) {
  navigator.clipboard.writeText(code); ElMessage.success('已复制')
}
function handleContinuePicking() {
  router.push({ path:'/outbound/pick-with-order', query:{ orderId:order.value?.id } })
}
function handlePrint() { window.print() }

async function handleSaveImage() {
  const el = document.getElementById('picking-sheet')
  if (!el) { ElMessage.error('未找到拣货单内容'); return }
  try {
    await saveAsImage(el, order.value?.outboundNo || '出库单')
    ElMessage.success('已保存为图片')
  } catch { ElMessage.error('保存失败') }
}

onBeforeMount(()=>{ loadData() })
</script>

<style scoped>
.detail-page { max-width:960px; margin:0 auto; }
.meta-bar { margin-bottom:20px; }
.meta-bar :deep(.el-card__body) { padding:12px 16px; }
.meta-top { display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:8px; margin-bottom:4px; }
.meta-info { display:flex; align-items:center; gap:6px; flex-wrap:wrap; }
.meta-label { font-size:0.8rem; color:var(--el-text-color-secondary); }
.meta-value { font-weight:600; }
.mono { font-family:'Courier New',monospace; }
.meta-sep { color:var(--el-border-color); margin:0 4px; }
.meta-sub { display:flex; align-items:center; gap:4px; flex-wrap:wrap; font-size:0.8rem; color:var(--el-text-color-secondary); }
.meta-actions { display:flex; gap:6px; }

/* Picking sheet */
.picking-sheet {
  background:#fff; border:2px solid #333; padding:28px 24px; margin-bottom:20px;
}
.sheet-header { display:flex; gap:28px; margin-bottom:20px; padding-bottom:16px; border-bottom:2px solid #333; }
.sheet-qr { flex-shrink:0; }
.sheet-title-area { flex:1; }
.sheet-title { margin:0 0 12px; font-size:1.4rem; letter-spacing:4px; text-align:center; }
.sheet-meta { display:flex; flex-direction:column; gap:4px; }
.sheet-row { display:flex; gap:8px; }
.sheet-label { width:60px; flex-shrink:0; color:#666; }
.sheet-value { font-weight:600; }

.sheet-table { width:100%; border-collapse:collapse; margin-bottom:12px; }
.sheet-table th, .sheet-table td {
  border:1px solid #333; padding:6px 8px; font-size:0.9rem; text-align:left;
}
.sheet-table th { background:#f5f5f5; font-weight:700; }
.col-seq { width:50px; text-align:center; }
.col-qty { width:80px; text-align:right; }
.sheet-table tr.done { background:#f0f9eb; }
.sheet-summary { text-align:right; font-size:0.9rem; color:#666; }

.kanban-detail-card code { font-family:'Courier New',monospace; font-size:0.82rem; }

/* Print */
@media print {
  .no-print { display:none !important; }
  .picking-sheet { border:none; padding:0; margin:0; page-break-after:always; }
}
</style>

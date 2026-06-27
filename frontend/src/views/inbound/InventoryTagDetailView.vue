<template>
  <section class="module-shell">
    <div class="toolbar">
      <h2>库存标签详情</h2>
      <div class="toolbar-actions">
        <el-button size="default" :disabled="selectedCount === 0" @click="printSelected">
          批量打印选中 ({{ selectedCount }})
        </el-button>
        <el-button size="default" @click="printAll">全部打印</el-button>
        <el-button type="primary" size="default" @click="printInboundOrderTags">
          一键打印本单全部库存标签
        </el-button>
      </div>
    </div>

    <el-alert v-if="errorMessage" type="error" :title="errorMessage" show-icon :closable="false" />

    <!-- 按库位分组，折叠展示 -->
    <el-collapse v-if="locationGroups.length" v-model="activeGroups" class="inventoryTag-collapse">
      <el-collapse-item
        v-for="grp in locationGroups"
        :key="grp.locationId"
        :name="String(grp.locationId)"
      >
        <template #title>
          <div class="group-title-row">
            <strong>{{ grp.locationName || '未分配库位' }}</strong>
            <el-tag size="small" type="info" effect="plain">{{ grp.inventoryTags.length }} 个库存标签</el-tag>
            <span class="group-piece-sum" v-if="grp.totalPieces > 0">共 {{ grp.totalPieces }} 件</span>
          </div>
        </template>

        <div class="inventoryTag-list">
          <div v-for="(inventoryTag, i) in grp.inventoryTags" :key="inventoryTag.inventoryTagCode" class="inventoryTag-row">
            <el-checkbox v-model="inventoryTag._checked" class="no-print inventoryTag-check" />
            <article :class="['inventoryTag-card', 'label-card', { 'printing-card': isPrintable(inventoryTag, inventoryTag._globalIndex) }]">
              <div class="card-left">
                <div class="card-header-row">
                  <h3>{{ inventoryTag.inventoryTagCode }}</h3>
                </div>
                <div class="info-row">
                  <span><strong>状态</strong>
                    <el-tag :type="statusType(inventoryTag.status)" size="small">{{ statusLabel(inventoryTag.status) }}</el-tag>
                  </span>
                  <span><strong>入库单</strong> {{ inventoryTag.inboundNo }}</span>
                </div>
                <div class="info-row">
                  <span><strong>供应商</strong> {{ inventoryTag.supplierCode }} {{ inventoryTag.supplierName }}</span>
                </div>
                <div class="info-row">
                  <span><strong>物料</strong> {{ inventoryTag.materialCode }} {{ inventoryTag.materialName }}</span>
                </div>
                <div class="info-row">
                  <span><strong>库位</strong> {{ grp.locationName }}</span>
                  <span><strong>数量</strong> {{ inventoryTag.qty }}</span>
                </div>
                <div class="info-row">
                  <span><strong>容器</strong> {{ inventoryTag.containerTypeName }}</span>
                  <span><strong>装箱</strong> {{ inventoryTag._boxLabel }}</span>
                </div>
              </div>
              <div class="card-right">
                <img v-if="qrCodes[inventoryTag.inventoryTagCode]" :src="qrCodes[inventoryTag.inventoryTagCode]" alt="QR" width="80" height="80" />
              </div>
            </article>
            <div class="inventoryTag-actions">
              <el-button size="small" @click="copyCode(inventoryTag.inventoryTagCode)">复制库存标签码</el-button>
              <el-button v-if="inventoryTag.status==='PRINTED'" size="small" type="success"
                :loading="receivingIdx === inventoryTag._globalIndex" @click="receiveOne(inventoryTag)">一键入库</el-button>
              <el-button size="small" @click="printOne(inventoryTag._globalIndex)">打印此卡</el-button>
              <el-button size="small" type="success" @click="saveOne(inventoryTag)">保存为图片</el-button>
            </div>
          </div>
        </div>
      </el-collapse-item>
    </el-collapse>

    <el-empty v-else-if="!loading && !errorMessage" description="暂无库存标签数据" />
  </section>
</template>

<script setup>
import { computed, onBeforeMount, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { fetchInventoryTagsByOrderId } from '../../api/inbound'
import { scanInbound } from '../../api/inventory'
import { saveAsImage } from '../../composables/useSaveImage'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const inventoryTags = ref([])
const qrCodes = ref({})
const printingIndex = ref(-1)
const printingMode = ref('all')
const receivingIdx = ref(-1)
const activeGroups = ref([])

const statusMap = { ACTIVE:'活跃', PRINTED:'已打印', RECEIVED:'已入库', SHIPPED:'已出库', CANCELLED:'已取消' }
const statusTagType = { ACTIVE:'success', PRINTED:'warning', RECEIVED:'info', SHIPPED:'success', CANCELLED:'danger' }

function statusType(s) { return statusTagType[s] || 'info' }
function statusLabel(s) { return statusMap[s] || s || '未知' }
function getInboundId() { return Number(route.params.id) }

function computeBoxLabels(list) {
  const lineMap = {}
  for (const k of list) {
    const ln = k.lineNo ?? 1
    if (!lineMap[ln]) lineMap[ln] = []
    lineMap[ln].push(k)
  }
  for (const [ln, kans] of Object.entries(lineMap)) {
    kans.sort((a, b) => (a.inventoryTagCode || '').localeCompare(b.inventoryTagCode || ''))
    const total = kans.length
    const cap = kans[0]?.capacityQty || 0
    kans.forEach((k, idx) => {
      if (total === 1 && cap > 0 && Number(k.qty) < cap) {
        k._boxLabel = `非整箱 (${k.qty} 件)`
      } else {
        k._boxLabel = `第 ${idx + 1}/${total} 箱`
      }
    })
  }
}

function groupByLocation(list) {
  const map = {}
  for (const k of list) {
    const locId = k.locationId || 0
    const locName = k.locationName || '未分配库位'
    const key = `${locId}:${locName}`
    if (!map[key]) map[key] = { locationId: locId, locationName: locName, inventoryTags: [], totalPieces: 0 }
    map[key].inventoryTags.push(k)
    map[key].totalPieces += Number(k.qty) || 0
  }
  return Object.values(map).sort((a, b) => (a.locationName || '').localeCompare(b.locationName || ''))
}

const locationGroups = computed(() => groupByLocation(inventoryTags.value))

async function generateQrCodes(list) {
  const codes = {}
  for (const k of list) {
    try { codes[k.inventoryTagCode] = await QRCode.toDataURL(k.inventoryTagCode, { width: 80 }) }
    catch { codes[k.inventoryTagCode] = '' }
  }
  qrCodes.value = codes
}

async function loadData() {
  const id = getInboundId()
  if (!id) { errorMessage.value = '入库单编号缺失'; return }
  loading.value = true; errorMessage.value = ''
  try {
    const result = await fetchInventoryTagsByOrderId(id)
    inventoryTags.value = (result || []).map((k, i) => ({ ...k, _checked: false, _globalIndex: i }))
    computeBoxLabels(inventoryTags.value)
    if (inventoryTags.value.length) await generateQrCodes(inventoryTags.value)
    activeGroups.value = locationGroups.value.map(g => String(g.locationId))
  } catch (e) {
    errorMessage.value = e.response?.data?.message || '加载库存标签数据失败'
  } finally { loading.value = false }
}

function copyCode(code) {
  navigator.clipboard.writeText(code)
  ElMessage.success('库存标签码已复制')
}

async function receiveOne(inventoryTag) {
  receivingIdx.value = inventoryTag._globalIndex
  try { await scanInbound(inventoryTag.inventoryTagCode); ElMessage.success('入库成功'); await loadData() }
  catch (e) { ElMessage.error(e.response?.data?.message || '入库失败') }
  finally { receivingIdx.value = -1 }
}

const selectedCount = computed(() => inventoryTags.value.filter(k => k._checked).length)

function isPrintable(inventoryTag, i) {
  if (printingMode.value === 'all') return true
  if (printingMode.value === 'selected') return inventoryTag._checked
  if (printingMode.value === 'single') return printingIndex.value === i
  return false
}

function printAll() { printingMode.value = 'all'; setTimeout(() => window.print(), 100) }
function printSelected() {
  if (selectedCount.value === 0) { ElMessage.warning('请至少勾选一个库存标签'); return }
  printingMode.value = 'selected'; setTimeout(() => window.print(), 100)
}
function printOne(i) { printingMode.value = 'single'; printingIndex.value = i; setTimeout(() => window.print(), 100) }
function printInboundOrderTags() {
  router.push(`/inbound/${getInboundId()}/inventory-tags/print`)
}

async function saveOne(inventoryTag) {
  const cards = document.querySelectorAll('.inventoryTag-card')
  const el = cards[inventoryTag._globalIndex]
  if (!el) { ElMessage.error('未找到库存标签卡片'); return }
  try { await saveAsImage(el, inventoryTag.inventoryTagCode); ElMessage.success('已保存为图片') }
  catch { ElMessage.error('保存失败') }
}

function onAfterPrint() { printingMode.value = 'all'; printingIndex.value = -1 }

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

.inventoryTag-collapse { margin-bottom: 20px; }
.group-title-row { display: flex; align-items: center; gap: 12px; }
.group-piece-sum { font-size: 13px; color: #909399; }

.inventoryTag-list {
  display: flex; flex-direction: column; align-items: center; gap: 16px;
  padding: 12px 0;
}

.inventoryTag-row {
  display: flex; align-items: center; gap: 10px;
}

.inventoryTag-check { flex-shrink: 0; }

.inventoryTag-actions {
  width: 100px; flex-shrink: 0;
  display: flex; flex-direction: column; gap: 4px;
}
.inventoryTag-actions :deep(.el-button) {
  width: 100px !important; margin-left: 0 !important; margin-right: 0 !important;
  justify-content: center;
}

.inventoryTag-card {
  display: flex; width: 120mm; min-height: 160px;
  border: 1px solid #111827; background: #fff; padding: 0;
  break-inside: avoid;
  page-break-inside: avoid;
}
.label-card { border: 1px solid #111; }

.card-left {
  flex: 1; padding: 6px 10px;
  display: flex; flex-direction: column; justify-content: center; gap: 1px;
}

.card-header-row {
  display: flex; align-items: flex-start; justify-content: space-between;
  gap: 8px; margin-bottom: 3px;
}
.card-header-row h3 { margin: 0; font-size: 15px; font-weight: 700; word-break: break-all; }

.info-row { display: flex; gap: 18px; font-size: 10px; line-height: 1.5; }

.card-right {
  width: 36mm; display: flex; align-items: center; justify-content: center;
  border-left: 1px dashed #94a3b8; padding: 4px;
}

@media print {
  .toolbar, .el-alert, .inventoryTag-check, .no-print { display: none !important; }
  .inventoryTag-card { display: none; border: 1px solid #000; }
  .inventoryTag-card.printing-card { display: flex; margin: 0 auto; page-break-after: always; }
  .inventoryTag-card:last-child.printing-card { page-break-after: auto; }
  .card-right { border-left-color: #000; }
  .inventoryTag-actions { display: none; }
  :deep(.el-collapse-item__header) { display: none; }
  :deep(.el-collapse-item__content) { display: block !important; }
}
</style>

<template>
  <section class="mobile-page">
    <div class="page-hero">
      <div>
        <h2>手机出库</h2>
        <p>覆盖带单出库和不带单出库基础路径，错误提示直接透传现有后端语义。</p>
      </div>
      <el-tag type="warning">扫码出库</el-tag>
    </div>

    <section v-if="mode === 'with-order'" class="panel">
      <div class="panel-header">
        <h3>待处理出库单</h3>
        <el-button size="small" text :loading="loadingOrders" @click="loadPendingOrders">刷新</el-button>
      </div>
      <div v-if="pendingOrders.length" class="compact-list">
        <article
          v-for="order in pendingOrders"
          :key="order.id"
          class="compact-item selectable"
          :class="{ active: orderInfo?.id === order.id }"
          @click="selectOrder(order)"
        >
          <div>
            <strong>{{ order.outboundNo }}</strong>
            <p>{{ order.supplier?.name || '—' }}</p>
          </div>
          <div class="compact-meta">
            <span>{{ order.status }}</span>
            <span>{{ formatQty(order.pickedQty) }} / {{ formatQty(order.plannedQty) }}</span>
          </div>
        </article>
      </div>
      <el-empty v-else-if="!loadingOrders" description="暂无待处理出库单" />
    </section>

    <section class="panel">
      <div class="mode-row">
        <button
          v-for="option in modeOptions"
          :key="option.value"
          type="button"
          class="mode-button"
          :class="{ active: mode === option.value }"
          @click="switchMode(option.value)"
        >
          {{ option.label }}
        </button>
      </div>

      <MobileQrScanner
        reader-id="outbound"
        :label="scannerLabel"
        :disabled="loadingOrder || submitting"
        @decoded="handleOutboundScan"
      />

      <div v-if="mode === 'with-order'" class="field-grid">
        <div v-if="recommendationLines.length" class="field-block">
          <label class="field-label" for="mobile-outbound-line">出库明细行</label>
          <el-select
            id="mobile-outbound-line"
            v-model="activeLineId"
            size="large"
            placeholder="选择出库明细行"
          >
            <el-option
              v-for="line in recommendationLines"
              :key="line.outboundOrderLineId"
              :value="line.outboundOrderLineId"
              :label="`行${line.lineNo} ${line.materialCode || ''} ${line.materialName || ''}`"
            />
          </el-select>
        </div>
        <div class="field-block">
          <label class="field-label" for="mobile-outbound-order">出库单号</label>
          <el-input
            id="mobile-outbound-order"
            v-model="outboundNo"
            clearable
            size="large"
            placeholder="请输入出库单号"
            @keyup.enter="loadOrder"
          />
        </div>
        <el-button type="primary" size="large" :loading="loadingOrder" @click="loadOrder">
          加载出库单
        </el-button>
      </div>

      <div class="field-grid">
        <div class="field-block">
          <label class="field-label" for="mobile-outbound-code">库存标签码</label>
          <el-input
            id="mobile-outbound-code"
            v-model="inventoryTagCode"
            clearable
            size="large"
            placeholder="请输入库存标签码"
            @keyup.enter="submitOutbound"
          />
        </div>
        <div class="field-block">
          <label class="field-label" for="mobile-outbound-qty">数量</label>
          <el-input-number
            id="mobile-outbound-qty"
            v-model="qty"
            :min="1"
            :step="1"
            :precision="0"
            size="large"
            placeholder="留空按默认数量"
          />
        </div>
      </div>

      <div class="action-row">
        <el-button type="primary" size="large" :loading="submitting" @click="submitOutbound">
          确认出库
        </el-button>
      </div>

      <el-alert
        v-if="errorMessage"
        type="error"
        :closable="false"
        show-icon
        :title="errorMessage"
      />
    </section>

    <section v-if="orderInfo" class="panel">
      <div class="panel-header">
        <h3>出库单信息</h3>
        <el-tag type="primary">{{ orderInfo.status }}</el-tag>
      </div>
      <dl class="detail-list">
        <div>
          <dt>出库单号</dt>
          <dd>{{ orderInfo.outboundNo }}</dd>
        </div>
        <div>
          <dt>供应商</dt>
          <dd>{{ orderInfo.supplier?.name || '—' }}</dd>
        </div>
        <div>
          <dt>计划总量</dt>
          <dd>{{ formatQty(orderInfo.plannedQty) }}</dd>
        </div>
        <div>
          <dt>已拣总量</dt>
          <dd>{{ formatQty(orderInfo.pickedQty) }}</dd>
        </div>
      </dl>
      <div v-if="lockedItems.length" class="compact-list">
        <article v-for="item in lockedItems" :key="item.id || item.inventoryTagCode" class="compact-item">
          <div>
            <strong>{{ item.inventoryTagCode }}</strong>
            <p>{{ item.materialCode }} {{ item.materialName }}</p>
          </div>
          <div class="compact-meta">
            <span>{{ item.locationName || '—' }}</span>
            <span>锁定 {{ formatQty(item.lockQty) }}</span>
          </div>
        </article>
      </div>
    </section>

    <section v-if="mode === 'with-order' && recommendationLines.length" class="panel">
      <div class="panel-header">
        <h3>推荐出库方案</h3>
        <el-tag type="success">FIFO</el-tag>
      </div>
      <div class="compact-list">
        <article v-for="line in recommendationLines" :key="line.outboundOrderLineId" class="compact-item">
          <div>
            <strong>行{{ line.lineNo }} {{ line.materialCode }}</strong>
            <p>{{ line.materialName }} · 待出 {{ formatQty(line.neededQty) }}</p>
          </div>
          <div class="recommend-tags">
            <el-tag
              v-for="item in line.recommendations || []"
              :key="item.inventoryTagCode"
              size="small"
              effect="plain"
            >
              {{ item.inventoryTagCode }}
            </el-tag>
            <span v-if="!line.recommendations?.length" class="empty-tip">暂无推荐</span>
          </div>
        </article>
      </div>
    </section>

    <section v-if="preview" class="panel">
      <div class="panel-header">
        <h3>库存标签预览</h3>
        <el-tag :type="statusTagType(preview.inventoryTagStatus)">{{ preview.inventoryTagStatus }}</el-tag>
      </div>
      <dl class="detail-list">
        <div>
          <dt>物料</dt>
          <dd>{{ preview.materialCode }} {{ preview.materialName }}</dd>
        </div>
        <div>
          <dt>当前位置</dt>
          <dd>{{ preview.locationName || '—' }}</dd>
        </div>
        <div>
          <dt>总量 / 已出 / 剩余</dt>
          <dd>
            {{ formatQty(preview.boardQty) }} / {{ formatQty(preview.pickedQty || 0) }} /
            {{ formatQty(remainingQty) }}
          </dd>
        </div>
        <div>
          <dt>锁定 / 封存</dt>
          <dd>{{ holdSummary(preview) }}</dd>
        </div>
      </dl>
    </section>

    <section v-if="result" class="panel result-panel">
      <div class="panel-header">
        <h3>出库结果</h3>
        <el-tag type="success">{{ result.orderStatus || (mode === 'no-order' ? '已出库' : '完成') }}</el-tag>
      </div>
      <dl class="detail-list">
        <div>
          <dt>库存标签码</dt>
          <dd><code>{{ result.inventoryTagCode }}</code></dd>
        </div>
        <div>
          <dt>物料</dt>
          <dd>{{ result.materialCode }} {{ result.materialName }}</dd>
        </div>
        <div>
          <dt>出库数量</dt>
          <dd>{{ formatQty(result.pickedQty) }}</dd>
        </div>
        <div>
          <dt>结果状态</dt>
          <dd>{{ result.newInventoryTagStatus }}</dd>
        </div>
        <div>
          <dt>出库单号</dt>
          <dd>{{ result.outboundNo || (mode === 'no-order' ? '不带单出库' : '—') }}</dd>
        </div>
        <div>
          <dt>完成时间</dt>
          <dd>{{ formatDateTime(result.occurredAt) }}</dd>
        </div>
      </dl>
    </section>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import {
  fetchOutboundOrders,
  fetchOutboundRecommendations,
  fetchQrInfo,
  lookupInventoryTag,
  pickNoOrder,
  pickWithOrder
} from '../../api/outbound'
import MobileQrScanner from '../../components/mobile/MobileQrScanner.vue'
import { normalizeInventoryTagCode, normalizeOutboundNo } from '../../utils/scanPayload'
import {
  findRecommendedLineId,
  isRecommendedInventoryTag,
  pendingOutboundStatuses,
  pendingRecommendationLines
} from '../../utils/outboundRecommendation'

const modeOptions = [
  { value: 'with-order', label: '带单出库' },
  { value: 'no-order', label: '不带单出库' }
]

const mode = ref('with-order')
const outboundNo = ref('')
const pendingOrders = ref([])
const orderInfo = ref(null)
const lockedItems = ref([])
const recommendation = ref(null)
const activeLineId = ref(null)
const inventoryTagCode = ref('')
const qty = ref(undefined)
const preview = ref(null)
const result = ref(null)
const errorMessage = ref('')
const loadingOrder = ref(false)
const loadingOrders = ref(false)
const submitting = ref(false)

let lookupTimer = null

const remainingQty = computed(() => {
  if (!preview.value) {
    return 0
  }
  return Number(preview.value.boardQty || 0) - Number(preview.value.pickedQty || 0)
})

const scannerLabel = computed(() => {
  if (mode.value === 'with-order' && !orderInfo.value?.id) {
    return '扫描出库单二维码'
  }
  return '扫描库存标签码'
})

const recommendationLines = computed(() => pendingRecommendationLines(recommendation.value))

watch(inventoryTagCode, (value) => {
  clearTimeout(lookupTimer)
  preview.value = null
  if (errorMessage.value) {
    errorMessage.value = ''
  }
  const trimmed = value.trim()
  if (!trimmed) {
    return
  }
  lookupTimer = setTimeout(async () => {
    try {
      preview.value = await lookupInventoryTag(trimmed)
    } catch {
      preview.value = null
    }
  }, 250)
})

function switchMode(nextMode) {
  mode.value = nextMode
  errorMessage.value = ''
  result.value = null
  if (nextMode === 'no-order') {
    orderInfo.value = null
    lockedItems.value = []
    recommendation.value = null
    activeLineId.value = null
  }
}

async function handleOutboundScan(text) {
  if (mode.value === 'with-order' && !orderInfo.value?.id) {
    outboundNo.value = normalizeOutboundNo(text)
    await loadOrder()
    return
  }
  inventoryTagCode.value = normalizeInventoryTagCode(text)
}

async function loadOrder() {
  const code = outboundNo.value.trim()
  if (!code) {
    errorMessage.value = '请输入出库单号'
    return
  }

  loadingOrder.value = true
  errorMessage.value = ''
  try {
    const data = await fetchQrInfo(code)
    orderInfo.value = data.order || null
    lockedItems.value = data.lockedItems || []
    await loadRecommendation(orderInfo.value?.id)
  } catch (error) {
    orderInfo.value = null
    lockedItems.value = []
    recommendation.value = null
    activeLineId.value = null
    errorMessage.value = error.response?.data?.message || error.message || '出库单加载失败'
  } finally {
    loadingOrder.value = false
  }
}

async function loadPendingOrders() {
  loadingOrders.value = true
  try {
    pendingOrders.value = await fetchOutboundOrders({ status: pendingOutboundStatuses.join(',') })
  } catch {
    pendingOrders.value = []
  } finally {
    loadingOrders.value = false
  }
}

async function selectOrder(order) {
  outboundNo.value = order.outboundNo || ''
  orderInfo.value = order
  lockedItems.value = []
  errorMessage.value = ''
  if (outboundNo.value) {
    await loadOrder()
    return
  }
  await loadRecommendation(order.id)
}

async function loadRecommendation(orderId) {
  if (!orderId) {
    recommendation.value = null
    activeLineId.value = null
    return
  }
  try {
    recommendation.value = await fetchOutboundRecommendations(orderId)
    if (!recommendationLines.value.some((line) => line.outboundOrderLineId === activeLineId.value)) {
      activeLineId.value = recommendationLines.value[0]?.outboundOrderLineId || null
    }
  } catch {
    recommendation.value = null
    activeLineId.value = null
  }
}

async function submitOutbound() {
  const code = inventoryTagCode.value.trim()
  if (!code) {
    errorMessage.value = '请输入库存标签码'
    return
  }
  if (mode.value === 'with-order' && !orderInfo.value?.id) {
    errorMessage.value = '请先加载出库单'
    return
  }

  submitting.value = true
  errorMessage.value = ''
  result.value = null

  try {
    const payload = {
      inventoryTagCode: code,
      qty: qty.value || undefined,
      outboundOrderId: mode.value === 'with-order' ? orderInfo.value.id : undefined
    }
    if (mode.value === 'with-order') {
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
    }
    result.value = mode.value === 'with-order'
      ? await pickWithOrder(payload)
      : await pickNoOrder(payload)
    inventoryTagCode.value = ''
    qty.value = undefined
    preview.value = null
    if (mode.value === 'with-order' && outboundNo.value.trim()) {
      await loadOrder()
      await loadPendingOrders()
    }
  } catch (error) {
    if (error === 'cancel' || error?.message === 'cancel') {
      return
    }
    errorMessage.value = error.response?.data?.message || error.message || '出库失败'
  } finally {
    submitting.value = false
  }
}

function resolveOutboundLineId(code) {
  const recommendedLineId = findRecommendedLineId(recommendation.value, code)
  if (recommendedLineId) return recommendedLineId
  const previewMaterialId = preview.value?.materialId
  if (previewMaterialId) {
    const matched = recommendationLines.value.find((line) => line.materialId === previewMaterialId)
    if (matched) return matched.outboundOrderLineId
  }
  return activeLineId.value
}

function formatQty(value) {
  return value == null ? '-' : Number(value)
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString('zh-CN')
}

function holdSummary(context) {
  if (context.activeHoldType) {
    return `${context.activeHoldType}${context.activeHoldReason ? `：${context.activeHoldReason}` : ''}`
  }
  if (context.inventoryTagStatus === 'LOCKED') {
    return '已被出库锁定'
  }
  if (context.inventoryTagStatus === 'SEALED') {
    return '已封存'
  }
  return '正常'
}

function statusTagType(status) {
  if (status === 'LOCKED' || status === 'SEALED') {
    return 'danger'
  }
  if (status === 'RECEIVED') {
    return 'success'
  }
  return 'warning'
}

onMounted(() => {
  loadPendingOrders()
})

onBeforeUnmount(() => {
  clearTimeout(lookupTimer)
})
</script>

<style scoped>
.mobile-page {
  display: grid;
  gap: 16px;
}

.page-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.page-hero h2,
.panel-header h3 {
  margin: 0;
  font-size: 20px;
}

.page-hero p {
  margin: 6px 0 0;
  color: #64748b;
  line-height: 1.6;
}

.panel {
  display: grid;
  gap: 16px;
  padding: 16px;
  background: #ffffff;
  border: 1px solid #dbe4ef;
  border-radius: 8px;
}

.mode-row,
.action-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.mode-button {
  min-height: 44px;
  border: 1px solid #dbe4ef;
  border-radius: 8px;
  background: #ffffff;
  color: #475569;
}

.mode-button.active {
  color: #2563eb;
  border-color: #93c5fd;
  background: #eff6ff;
}

.field-grid,
.field-block,
.detail-list {
  display: grid;
  gap: 12px;
}

.field-label {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-list div {
  display: grid;
  gap: 4px;
}

.detail-list dt {
  font-size: 12px;
  color: #64748b;
}

.detail-list dd {
  margin: 0;
  color: #0f172a;
  word-break: break-word;
}

.compact-list {
  display: grid;
  gap: 10px;
}

.compact-item {
  display: grid;
  gap: 6px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.compact-item.selectable {
  cursor: pointer;
}

.compact-item.selectable.active {
  border-color: #2563eb;
  background: #eff6ff;
}

.compact-item p {
  margin: 4px 0 0;
  color: #475569;
}

.compact-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: #64748b;
  font-size: 13px;
}

.recommend-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.empty-tip {
  color: #94a3b8;
  font-size: 13px;
}

.result-panel {
  border-color: #bbf7d0;
  background: #f0fdf4;
}
</style>

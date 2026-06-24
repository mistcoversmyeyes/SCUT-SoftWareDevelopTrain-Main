<template>
  <section class="mobile-page">
    <div class="page-hero">
      <div>
        <h2>库存标签查询</h2>
        <p>查询生命周期、位置、数量和锁定/封存状态，支撑课堂演示。</p>
      </div>
      <el-tag>库存标签追溯</el-tag>
    </div>

    <section class="panel">
      <div class="field-block">
        <label class="field-label" for="mobile-inventory-tag-code">库存标签码</label>
        <el-input
          id="mobile-inventory-tag-code"
          v-model="inventoryTagCode"
          clearable
          size="large"
          placeholder="请输入库存标签码"
          @keyup.enter="queryInventoryTag"
        />
      </div>

      <div class="action-row">
        <el-button size="large" @click="applyDemoCode">模拟扫码</el-button>
        <el-button type="primary" size="large" :loading="loading" @click="queryInventoryTag">
          查询
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

    <section v-if="traceData" class="panel">
      <div class="panel-header">
        <h3>生命周期</h3>
        <el-tag :type="statusTagType(traceData.inventoryTagStatus)">{{ traceData.inventoryTagStatus }}</el-tag>
      </div>
      <div class="status-list">
        <span
          v-for="status in lifecycleStatuses"
          :key="status"
          class="status-chip"
          :class="{ active: traceData.inventoryTagStatus === status }"
        >
          {{ status }}
        </span>
      </div>
      <dl class="detail-list">
        <div>
          <dt>库存标签码</dt>
          <dd><code>{{ traceData.inventoryTagCode }}</code></dd>
        </div>
        <div>
          <dt>入库单号</dt>
          <dd>{{ traceData.inboundNo || previewData?.inboundNo || '—' }}</dd>
        </div>
        <div>
          <dt>物料</dt>
          <dd>{{ traceData.materialCode }} {{ traceData.materialName }}</dd>
        </div>
        <div>
          <dt>位置</dt>
          <dd>{{ traceData.locationCode || '—' }} {{ traceData.locationName || previewData?.locationName || '' }}</dd>
        </div>
        <div>
          <dt>总量 / 已出 / 剩余</dt>
          <dd>
            {{ formatQty(previewData?.boardQty) }} / {{ formatQty(previewData?.pickedQty || 0) }} /
            {{ formatQty(remainingQty) }}
          </dd>
        </div>
        <div>
          <dt>锁定 / 封存</dt>
          <dd>{{ holdSummary }}</dd>
        </div>
        <div>
          <dt>扫码时间</dt>
          <dd>{{ formatDateTime(traceData.scannedAt) }}</dd>
        </div>
        <div>
          <dt>库存流水</dt>
          <dd>{{ traceData.movementNo || '未生成' }}</dd>
        </div>
      </dl>
    </section>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { fetchInventoryTagTrace } from '../../api/inventoryTag'
import { lookupInventoryTag } from '../../api/outbound'

const lifecycleStatuses = ['PRINTED', 'RECEIVED', 'LOCKED', 'SEALED', 'SHIPPED', 'CANCELLED']

const inventoryTagCode = ref('')
const loading = ref(false)
const errorMessage = ref('')
const traceData = ref(null)
const previewData = ref(null)

const remainingQty = computed(() => {
  if (!previewData.value?.boardQty) {
    return null
  }
  return Number(previewData.value.boardQty) - Number(previewData.value.pickedQty || 0)
})

const holdSummary = computed(() => {
  if (!previewData.value) {
    return '—'
  }
  if (previewData.value.activeHoldType) {
    return `${previewData.value.activeHoldType}${previewData.value.activeHoldReason ? `：${previewData.value.activeHoldReason}` : ''}`
  }
  if (previewData.value.inventoryTagStatus === 'LOCKED') {
    return '已锁定'
  }
  if (previewData.value.inventoryTagStatus === 'SEALED') {
    return '已封存'
  }
  return '正常'
})

function applyDemoCode() {
  if (!inventoryTagCode.value.trim()) {
    inventoryTagCode.value = 'IT:v1:DEMO:QUERY'
  }
}

async function queryInventoryTag() {
  const code = inventoryTagCode.value.trim()
  if (!code) {
    errorMessage.value = '请输入库存标签码'
    return
  }

  loading.value = true
  errorMessage.value = ''
  traceData.value = null
  previewData.value = null

  try {
    traceData.value = await fetchInventoryTagTrace(code)
    try {
      previewData.value = await lookupInventoryTag(code)
    } catch {
      previewData.value = null
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.message || error.message || '查询失败'
  } finally {
    loading.value = false
  }
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

function statusTagType(status) {
  if (status === 'RECEIVED') {
    return 'success'
  }
  if (status === 'LOCKED' || status === 'SEALED' || status === 'CANCELLED') {
    return 'danger'
  }
  return 'info'
}
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

.action-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.status-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.status-chip {
  min-height: 32px;
  display: inline-flex;
  align-items: center;
  padding: 0 12px;
  border-radius: 999px;
  background: #e2e8f0;
  color: #475569;
  font-size: 12px;
}

.status-chip.active {
  background: #dbeafe;
  color: #1d4ed8;
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
</style>

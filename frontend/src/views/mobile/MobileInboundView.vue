<template>
  <section class="mobile-page">
    <div class="page-hero">
      <div>
        <h2>手机入库</h2>
        <p>支持摄像头扫码、图片识别和手工输入库存标签码。</p>
      </div>
      <el-tag type="success">扫码入库</el-tag>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="未登录访问会跳转到现有登录页；登录后自动回到当前移动端页面。"
    />

    <section class="panel">
      <MobileQrScanner
        reader-id="inbound"
        label="扫描库存标签码"
        :disabled="submitting"
        @decoded="handleInventoryTagScan"
      />

      <div class="field-grid">
        <div class="field-block">
          <label class="field-label" for="mobile-inbound-code">库存标签码</label>
          <el-input
            id="mobile-inbound-code"
            v-model="inventoryTagCode"
            clearable
            size="large"
            placeholder="请输入库存标签码"
            @keyup.enter="submitInbound"
          />
        </div>
        <div class="field-block">
          <label class="field-label" for="mobile-inbound-location">目标库位</label>
          <el-select
            id="mobile-inbound-location"
            v-model="selectedLocationId"
            clearable
            filterable
            size="large"
            placeholder="留空则按计划库位入库"
          >
            <el-option
              v-for="location in locationOptions"
              :key="location.id"
              :label="`${location.code} ${location.name}`"
              :value="location.id"
            />
          </el-select>
        </div>
      </div>

      <div class="action-row">
        <el-button type="primary" size="large" :loading="submitting" @click="submitInbound">
          提交入库
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
          <dt>入库单</dt>
          <dd>{{ preview.inboundNo || '—' }}</dd>
        </div>
        <div>
          <dt>计划库位</dt>
          <dd>{{ preview.locationName || '—' }}</dd>
        </div>
        <div>
          <dt>数量</dt>
          <dd>{{ formatQty(preview.boardQty) }}</dd>
        </div>
      </dl>
    </section>

    <section v-if="result" class="panel result-panel">
      <div class="panel-header">
        <h3>入库结果</h3>
        <el-tag type="success">{{ result.orderStatus }}</el-tag>
      </div>
      <dl class="detail-list">
        <div>
          <dt>库存标签码</dt>
          <dd><code>{{ result.inventoryTagCode }}</code></dd>
        </div>
        <div>
          <dt>收货数量</dt>
          <dd>{{ formatQty(result.receivedQty) }}</dd>
        </div>
        <div>
          <dt>物料</dt>
          <dd>{{ result.materialCode }} {{ result.materialName }}</dd>
        </div>
        <div>
          <dt>实际库位</dt>
          <dd>{{ result.actualLocationName || result.locationName || '—' }}</dd>
        </div>
        <div>
          <dt>计划库位</dt>
          <dd>{{ result.plannedLocationName || '—' }}</dd>
        </div>
        <div>
          <dt>入库时间</dt>
          <dd>{{ formatDateTime(result.receivedAt) }}</dd>
        </div>
      </dl>
    </section>
  </section>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { fetchMasterDataOptions } from '../../api/masterData'
import { lookupInventoryTagInbound, scanInbound } from '../../api/inventory'
import MobileQrScanner from '../../components/mobile/MobileQrScanner.vue'
import { normalizeInventoryTagCode } from '../../utils/scanPayload'

const inventoryTagCode = ref('')
const selectedLocationId = ref(null)
const locationOptions = ref([])
const preview = ref(null)
const result = ref(null)
const errorMessage = ref('')
const submitting = ref(false)

let lookupTimer = null

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
      preview.value = await lookupInventoryTagInbound(trimmed)
    } catch {
      preview.value = null
    }
  }, 250)
})

function handleInventoryTagScan(text) {
  inventoryTagCode.value = normalizeInventoryTagCode(text)
}

async function submitInbound() {
  const code = inventoryTagCode.value.trim()
  if (!code) {
    errorMessage.value = '请输入库存标签码'
    return
  }

  submitting.value = true
  errorMessage.value = ''
  result.value = null

  try {
    result.value = await scanInbound(code, selectedLocationId.value)
    inventoryTagCode.value = ''
    selectedLocationId.value = null
    preview.value = null
  } catch (error) {
    errorMessage.value = error.response?.data?.message || error.message || '入库失败'
  } finally {
    submitting.value = false
  }
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString('zh-CN')
}

function formatQty(value) {
  return value == null ? '-' : Number(value)
}

function statusTagType(status) {
  if (status === 'RECEIVED') {
    return 'success'
  }
  if (status === 'PRINTED') {
    return 'warning'
  }
  return 'info'
}

onMounted(async () => {
  try {
    const data = await fetchMasterDataOptions()
    locationOptions.value = data.locations || []
  } catch {
    locationOptions.value = []
  }
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

.field-grid {
  display: grid;
  gap: 14px;
}

.field-block {
  display: grid;
  gap: 8px;
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

.detail-list {
  display: grid;
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

.result-panel {
  border-color: #bbf7d0;
  background: #f0fdf4;
}
</style>

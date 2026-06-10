<template>
  <section class="module-shell">
    <el-card v-loading="loading" class="print-container">
      <template #header>
        <div class="toolbar">
          <h2>入库单打印</h2>
          <el-button type="primary" size="default" @click="printNow">打印</el-button>
        </div>
      </template>

      <el-alert v-if="errorMessage" type="error" :title="errorMessage" show-icon :closable="false" />

      <div v-if="documentData" class="print-area" ref="printAreaRef">
        <el-descriptions :column="2" border class="print-fieldset">
          <el-descriptions-item label="入库单号">{{ documentData.inboundNo }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ supplierText }}</el-descriptions-item>
          <el-descriptions-item label="来源单号">{{ documentData.sourceDocNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ documentData.status }}</el-descriptions-item>
          <el-descriptions-item label="打印时间">{{ formatDateTime(documentData.releasedAt) }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ documentData.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-table :data="documentData.lines" border stripe class="print-table" style="margin-top: 16px;">
          <el-table-column prop="lineNo" label="行号" width="80" />
          <el-table-column prop="materialCode" label="物料编码" min-width="160" />
          <el-table-column prop="materialName" label="物料名称" min-width="220" />
          <el-table-column prop="plannedQty" label="计划数量" width="120" />
          <el-table-column prop="receivedQty" label="已收数量" width="120" />
          <el-table-column prop="warehouseName" label="仓库" min-width="180" />
          <el-table-column prop="locationName" label="库位" min-width="180" />
        </el-table>

        <div class="totals">合计：{{ totalPlannedQty }} / {{ totalReceivedQty }}</div>
      </div>
    </el-card>
  </section>
</template>

<script setup>
import { computed, onBeforeMount, ref } from 'vue'
import { useRoute } from 'vue-router'
import { printInboundOrder } from '../../api/inbound'

const route = useRoute()
const loading = ref(false)
const errorMessage = ref('')
const documentData = ref(null)

const supplierText = computed(() => {
  if (!documentData.value) return '-'
  const supplier = documentData.value.supplierName
  const code = documentData.value.supplierCode
  if (!supplier && !code) return '-'
  return code && supplier ? `${code} ${supplier}` : (supplier || code)
})

const totalPlannedQty = computed(() => {
  if (!documentData.value?.lines?.length) return 0
  return documentData.value.lines.reduce((sum, item) => sum + Number(item.plannedQty), 0)
})

const totalReceivedQty = computed(() => {
  if (!documentData.value?.lines?.length) return 0
  return documentData.value.lines.reduce((sum, item) => sum + Number(item.receivedQty), 0)
})

function formatDateTime(value) {
  if (!value) {
    return '-'
  }

  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }

  return parsed.toLocaleString('zh-CN')
}

function getInboundId() {
  const rawId = route.params.id
  return Number(rawId)
}

async function loadPrintData() {
  const id = getInboundId()
  if (!id) {
    errorMessage.value = '入库单编号缺失'
    return
  }

  loading.value = true
  errorMessage.value = ''
  try {
    documentData.value = await printInboundOrder(id)
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '加载打印数据失败'
  } finally {
    loading.value = false
  }
}

function printNow() {
  window.print()
}

onBeforeMount(() => {
  loadPrintData()
})
</script>

<style scoped>
.module-shell {
  min-height: 360px;
}

h2 {
  margin: 0;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.totals {
  margin-top: 12px;
  text-align: right;
  font-weight: 600;
}

@media print {
  .toolbar {
    display: none;
  }

  .print-area {
    margin: 0;
  }

  :deep(.el-card) {
    border: none;
    box-shadow: none;
  }
}
</style>

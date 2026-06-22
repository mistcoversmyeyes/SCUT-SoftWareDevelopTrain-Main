<template>
  <section class="ai-import-page">
    <div class="page-toolbar">
      <div>
        <h2>AI 数据导入</h2>
        <p class="page-subtitle">首期固定对象：`inventory_flow_history` UTF-8 CSV</p>
      </div>
      <a class="sample-link" href="/samples/week4-inventory-flow-history-sample.csv" download>
        下载样例文件
      </a>
    </div>

    <el-alert
      title="仅支持 UTF-8 CSV，表头固定为 business_date, material_code, warehouse_code, location_code, board_code, movement_type, quantity, source_order_no, quality_status。"
      type="info"
      :closable="false"
      show-icon
    />

    <section class="upload-section">
      <el-upload
        drag
        :auto-upload="false"
        :show-file-list="false"
        accept=".csv,text/csv"
        :on-change="handleFileChange"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">拖拽 CSV 到此处，或点击选择文件</div>
      </el-upload>

      <div class="upload-actions">
        <span class="selected-file">{{ selectedFileName || '尚未选择文件' }}</span>
        <el-button type="primary" :loading="uploading" :disabled="!selectedFile" @click="submitImport">
          开始导入
        </el-button>
        <el-button :loading="loadingBatches" @click="loadBatches">刷新批次</el-button>
      </div>
    </section>

    <section v-if="lastResult" class="result-section">
      <div class="section-header">
        <h3>本次导入摘要</h3>
      </div>
      <div class="summary-grid">
        <div class="summary-item">
          <span class="summary-label">总行数</span>
          <strong>{{ lastResult.totalRows }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-label">成功</span>
          <strong class="ok">{{ lastResult.successRows }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-label">失败</span>
          <strong class="bad">{{ lastResult.failedRows }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-label">物料数</span>
          <strong>{{ lastResult.summary?.materialCount ?? 0 }}</strong>
        </div>
      </div>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="时间范围">
          {{ lastResult.summary?.businessDateStart || '-' }} ~ {{ lastResult.summary?.businessDateEnd || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="批次 ID">
          {{ lastResult.batchId }}
        </el-descriptions-item>
        <el-descriptions-item label="类型分布" :span="2">
          {{ movementTypeSummary(lastResult.summary?.movementTypeCounts || {}) }}
        </el-descriptions-item>
      </el-descriptions>

      <el-table v-if="lastResult.errors?.length" :data="lastResult.errors" stripe>
        <el-table-column prop="rowNumber" label="错误行" width="100" />
        <el-table-column prop="field" label="字段" width="140" />
        <el-table-column prop="message" label="原因" min-width="220" />
        <el-table-column prop="rejectedValue" label="原始值" min-width="220" />
      </el-table>
    </section>

    <section class="batch-section">
      <div class="section-header">
        <h3>导入批次</h3>
      </div>
      <el-table :data="batches" stripe v-loading="loadingBatches" empty-text="暂无导入批次">
        <el-table-column prop="batchId" label="批次 ID" width="110" />
        <el-table-column prop="fileName" label="文件名" min-width="200" />
        <el-table-column prop="totalRows" label="总行数" width="100" />
        <el-table-column prop="successRows" label="成功" width="90" />
        <el-table-column prop="failedRows" label="失败" width="90" />
        <el-table-column prop="importedAt" label="导入时间" min-width="180" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button text type="primary" @click="viewBatch(row.batchId)">查看记录</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="record-section">
      <div class="section-header">
        <h3>导入记录</h3>
        <span class="record-hint">{{ activeBatchId ? `当前批次: ${activeBatchId}` : '显示最近记录' }}</span>
      </div>
      <el-table :data="records" stripe v-loading="loadingRecords" empty-text="暂无导入记录">
        <el-table-column prop="rowNumber" label="源行号" width="100" />
        <el-table-column prop="businessDate" label="业务日期" width="120" />
        <el-table-column prop="materialCode" label="物料编码" min-width="160" />
        <el-table-column prop="warehouseCode" label="仓库" width="100" />
        <el-table-column prop="locationCode" label="库位" width="100" />
        <el-table-column prop="boardCode" label="看板码" min-width="200" />
        <el-table-column prop="movementType" label="流水类型" width="110" />
        <el-table-column prop="quantity" label="数量" width="100" />
        <el-table-column prop="qualityStatus" label="质量状态" width="120" />
      </el-table>
    </section>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  fetchInventoryFlowImportBatches,
  fetchInventoryFlowImportRecords,
  importInventoryFlowHistory
} from '../../api/aiWarningImport'

const selectedFile = ref(null)
const selectedFileName = ref('')
const uploading = ref(false)
const loadingBatches = ref(false)
const loadingRecords = ref(false)
const lastResult = ref(null)
const batches = ref([])
const records = ref([])
const activeBatchId = ref(null)

function handleFileChange(uploadFile) {
  selectedFile.value = uploadFile.raw || null
  selectedFileName.value = uploadFile.name || ''
}

function movementTypeSummary(counts) {
  const entries = Object.entries(counts)
  if (!entries.length) {
    return '-'
  }
  return entries.map(([type, count]) => `${type}: ${count}`).join(' / ')
}

async function submitImport() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择 CSV 文件')
    return
  }

  uploading.value = true
  try {
    const result = await importInventoryFlowHistory(selectedFile.value)
    lastResult.value = result
    activeBatchId.value = result.batchId
    await Promise.all([loadBatches(), loadRecords(result.batchId)])
    ElMessage.success(`导入完成：成功 ${result.successRows} 行，失败 ${result.failedRows} 行`)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '导入失败')
  } finally {
    uploading.value = false
  }
}

async function loadBatches() {
  loadingBatches.value = true
  try {
    batches.value = await fetchInventoryFlowImportBatches()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载导入批次失败')
  } finally {
    loadingBatches.value = false
  }
}

async function loadRecords(batchId = null) {
  loadingRecords.value = true
  activeBatchId.value = batchId
  try {
    records.value = await fetchInventoryFlowImportRecords(batchId ? { batchId } : {})
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载导入记录失败')
  } finally {
    loadingRecords.value = false
  }
}

function viewBatch(batchId) {
  loadRecords(batchId)
}

onMounted(async () => {
  await Promise.all([loadBatches(), loadRecords()])
})
</script>

<style scoped>
.ai-import-page {
  padding: 16px 20px 28px;
}

.page-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-toolbar h2 {
  margin: 0;
}

.page-subtitle {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.sample-link {
  color: var(--el-color-primary);
  text-decoration: none;
  white-space: nowrap;
}

.upload-section,
.result-section,
.batch-section,
.record-section {
  margin-top: 20px;
}

.upload-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.selected-file,
.record-hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.section-header h3 {
  margin: 0;
  font-size: 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-item {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 12px;
  min-height: 76px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.summary-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.summary-item strong {
  font-size: 24px;
  line-height: 1;
}

.summary-item .ok {
  color: var(--el-color-success);
}

.summary-item .bad {
  color: var(--el-color-danger);
}

@media (max-width: 900px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }
}

@media (max-width: 640px) {
  .page-toolbar {
    flex-direction: column;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>

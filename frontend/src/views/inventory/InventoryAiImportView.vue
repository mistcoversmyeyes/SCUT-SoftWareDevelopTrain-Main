<template>
  <section class="ai-import-page">
    <div class="page-toolbar">
      <div>
        <h2>AI 数据导入</h2>
        <p class="page-subtitle">本页仅展示规则型预警数据准备与样例风险，不训练模型。</p>
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

    <el-alert
      :title="`规则型预警数据状态：${analysisReadiness.label}`"
      :description="analysisReadiness.reason"
      :type="analysisReadiness.tone"
      :closable="false"
      show-icon
      class="status-alert"
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
        <el-button :loading="loadingAnalysis" @click="loadRiskAnalysis">刷新分析</el-button>
        <el-button type="success" :loading="loadingReport" @click="generateRiskReport">
          {{ loadingReport ? '生成中' : '生成 AI 建议报告' }}
        </el-button>
      </div>
    </section>

    <section class="result-section">
      <div class="section-header">
        <h3>AI 预警分析结果</h3>
        <span class="record-hint">{{ riskAnalysis?.direction || '内部 WMS 数据驱动的缺货、呆滞、质量风险识别' }}</span>
      </div>
      <div v-if="riskAnalysis?.summary" class="summary-grid analysis-summary">
        <div class="summary-item">
          <span class="summary-label">分析对象</span>
          <strong>{{ riskAnalysis.summary.materialLocationCount }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-label">缺货高风险</span>
          <strong class="bad">{{ riskAnalysis.summary.shortageHighCount + riskAnalysis.summary.shortageCriticalCount }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-label">呆滞高风险</span>
          <strong class="bad">{{ riskAnalysis.summary.stagnationHighCount }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-label">质量风险</span>
          <strong class="bad">{{ riskAnalysis.summary.qualityHighCount + riskAnalysis.summary.qualityExpiredCount }}</strong>
        </div>
      </div>
      <el-table :data="riskPreviewRows" stripe v-loading="loadingAnalysis" empty-text="暂无风险样例或数据未准备">
        <el-table-column prop="materialCode" label="物料编码" width="140" />
        <el-table-column prop="materialName" label="物料名称" min-width="180" />
        <el-table-column prop="availableQty" label="可用" width="90" align="right" />
        <el-table-column prop="avgDailyOutbound7d" label="7日均出库" width="110" align="right" />
        <el-table-column prop="daysOfCover" label="覆盖天数" width="100" align="right">
          <template #default="{ row }">
            {{ formatMetric(row.daysOfCover) }}
          </template>
        </el-table-column>
        <el-table-column label="缺货风险" width="120">
          <template #default="{ row }">
            <el-tag :type="tagType(row.shortageRisk.tone)">{{ row.shortageRisk.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="呆滞风险" width="120">
          <template #default="{ row }">
            <el-tag :type="tagType(row.stagnationRisk.tone)">{{ row.stagnationRisk.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="质量风险" width="120">
          <template #default="{ row }">
            <el-tag :type="tagType(row.qualityRisk?.tone)">{{ row.qualityRisk?.label || '正常' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="触发原因" min-width="320">
          <template #default="{ row }">
            <div class="reason-cell">
              <div>{{ row.shortageRisk.reason }}</div>
              <div class="sub-reason">{{ row.stagnationRisk.reason }}</div>
              <div v-if="row.qualityRisk?.reason" class="sub-reason">{{ row.qualityRisk.reason }}</div>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section ref="reportSectionRef" class="result-section">
      <div class="section-header">
        <div>
          <h3>AI 建议报告</h3>
          <span class="record-hint">{{ reportStatusText }}</span>
        </div>
        <div class="report-actions">
          <el-segmented
            v-model="reportViewMode"
            size="small"
            :options="reportViewOptions"
            :disabled="!adviceReport?.reportMarkdown"
          />
          <el-button size="small" :disabled="!adviceReport?.reportMarkdown" @click="copyReport">复制报告</el-button>
          <el-button size="small" :disabled="!adviceReport?.reportMarkdown" @click="downloadReport">下载 Markdown</el-button>
        </div>
      </div>
      <el-alert
        v-if="loadingReport"
        :title="reportLoadingTitle"
        description="DeepSeek / 大模型生成通常需要 15-30 秒，请保持页面打开。报告生成后会自动显示在本区域。"
        type="info"
        :closable="false"
        show-icon
      />
      <div v-if="adviceReport?.status === 'GENERATED'" class="report-meta">
        <span>生成时间：{{ formatDateTime(adviceReport.generatedAt) }}</span>
        <span>模型：{{ adviceReport.model || '-' }}</span>
        <span>分析对象：{{ adviceReport.summary?.materialLocationCount ?? '-' }}</span>
      </div>
      <el-alert
        v-if="adviceReport && adviceReport.status !== 'GENERATED'"
        :title="adviceReport.message"
        :description="reportFailureHint"
        :type="adviceReport.status === 'NOT_CONFIGURED' ? 'warning' : 'error'"
        :closable="false"
        show-icon
      />
      <div
        v-if="adviceReport?.reportMarkdown && reportViewMode === 'preview'"
        class="report-panel markdown-preview"
        v-html="reportHtml"
      />
      <div v-else-if="adviceReport?.reportMarkdown" class="report-panel">
        <pre>{{ adviceReport.reportMarkdown }}</pre>
      </div>
      <el-empty v-else-if="!loadingReport" description="尚未生成 AI 建议报告">
        <template #description>
          <span>点击上方“生成 AI 建议报告”后，报告会显示在这里，并可复制或下载。</span>
        </template>
      </el-empty>
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
        <el-table-column prop="boardCode" label="库存标签码" min-width="200" />
        <el-table-column prop="movementType" label="流水类型" width="110" />
        <el-table-column prop="quantity" label="数量" width="100" />
        <el-table-column prop="qualityStatus" label="质量状态" width="120" />
      </el-table>
    </section>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { fetchInventoryBalances } from '../../api/inventory'
import { fetchMaterials } from '../../api/masterData'
import {
  fetchInventoryFlowImportBatches,
  fetchInventoryFlowImportRecords,
  fetchAiInventoryRiskAnalysis,
  fetchAiInventoryRiskReport,
  importInventoryFlowHistory
} from '../../api/aiWarningImport'
import { buildInventoryMonitorRows, buildRiskPreviewRows, buildWarningDataReadiness } from '../../utils/monitoring'

const selectedFile = ref(null)
const selectedFileName = ref('')
const uploading = ref(false)
const loadingBatches = ref(false)
const loadingRecords = ref(false)
const loadingAnalysis = ref(false)
const loadingReport = ref(false)
const lastResult = ref(null)
const batches = ref([])
const records = ref([])
const activeBatchId = ref(null)
const balances = ref([])
const materials = ref([])
const riskAnalysis = ref(null)
const adviceReport = ref(null)
const reportSectionRef = ref(null)
const reportElapsedSeconds = ref(0)
const reportViewMode = ref('preview')
let reportTimer = null
const reportViewOptions = [
  { label: '预览', value: 'preview' },
  { label: '源码', value: 'source' }
]

const readiness = computed(() => buildWarningDataReadiness(batches.value, records.value))
const analysisReadiness = computed(() => {
  if (!riskAnalysis.value) {
    return readiness.value
  }
  return {
    label: riskAnalysis.value.readinessLabel,
    reason: riskAnalysis.value.readinessReason,
    tone: riskAnalysis.value.readinessCode === 'READY' ? 'success' : 'warning'
  }
})
const localRiskPreviewRows = computed(() => buildRiskPreviewRows(buildInventoryMonitorRows({
  balances: balances.value,
  materials: materials.value,
  flowRecords: records.value,
  today: new Date()
}), 8))
const riskPreviewRows = computed(() => {
  const backendRows = riskAnalysis.value?.rows || []
  if (backendRows.length) {
    return backendRows.filter(hasVisibleRisk).slice(0, 8)
  }
  return localRiskPreviewRows.value
})
const reportStatusText = computed(() => {
  if (loadingReport.value) {
    return `正在生成，已等待 ${reportElapsedSeconds.value} 秒`
  }
  if (!adviceReport.value) {
    return '调用 AI API 后生成仓储处理建议'
  }
  if (adviceReport.value.status === 'GENERATED') {
    return `已生成，可在下方查看、复制或下载`
  }
  return adviceReport.value.status
})
const reportLoadingTitle = computed(() => `AI 正在生成建议报告，已等待 ${reportElapsedSeconds.value} 秒`)
const reportFailureHint = computed(() => {
  if (!adviceReport.value || adviceReport.value.status === 'GENERATED') {
    return ''
  }
  if (adviceReport.value.status === 'NOT_CONFIGURED') {
    return '请检查后端启动进程是否读取到 WMS_AI_API_KEY。'
  }
  return '可稍后重试；若连续失败，请查看 /tmp/wms-backend.log 中的 AI API 调用错误。'
})
const reportHtml = computed(() => renderMarkdown(adviceReport.value?.reportMarkdown || ''))

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

function tagType(tone) {
  if (tone === 'danger') return 'danger'
  if (tone === 'warning') return 'warning'
  if (tone === 'success') return 'success'
  return 'info'
}

function formatMetric(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num.toFixed(1) : '-'
}

function hasVisibleRisk(row) {
  return ['WATCH', 'HIGH', 'CRITICAL', 'EXPIRED', 'DATA_UNPREPARED'].some((code) => [
    row.shortageRisk?.code,
    row.stagnationRisk?.code,
    row.qualityRisk?.code
  ].includes(code))
}

async function loadPreviewBaseData() {
  try {
    const [balanceRows, materialRows] = await Promise.all([
      fetchInventoryBalances(),
      fetchMaterials()
    ])
    balances.value = balanceRows
    materials.value = materialRows
  } catch {
    balances.value = []
    materials.value = []
  }
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
    await Promise.all([loadBatches(), loadRecords(result.batchId), loadPreviewBaseData(), loadRiskAnalysis()])
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

async function loadRiskAnalysis() {
  loadingAnalysis.value = true
  try {
    riskAnalysis.value = await fetchAiInventoryRiskAnalysis()
  } catch (error) {
    riskAnalysis.value = null
    ElMessage.error(error.response?.data?.message || '加载 AI 预警分析失败')
  } finally {
    loadingAnalysis.value = false
  }
}

async function generateRiskReport() {
  loadingReport.value = true
  adviceReport.value = null
  startReportTimer()
  scrollToReport()
  try {
    adviceReport.value = await fetchAiInventoryRiskReport()
    if (adviceReport.value.status === 'GENERATED') {
      ElMessage.success('AI 建议报告已生成')
      scrollToReport()
    } else {
      ElMessage.warning(adviceReport.value.message || 'AI 建议报告未生成')
    }
  } catch (error) {
    adviceReport.value = null
    ElMessage.error(buildReportErrorMessage(error))
  } finally {
    loadingReport.value = false
    stopReportTimer()
  }
}

function viewBatch(batchId) {
  loadRecords(batchId)
}

function startReportTimer() {
  stopReportTimer()
  reportElapsedSeconds.value = 0
  reportTimer = window.setInterval(() => {
    reportElapsedSeconds.value += 1
  }, 1000)
}

function stopReportTimer() {
  if (reportTimer) {
    window.clearInterval(reportTimer)
    reportTimer = null
  }
}

function scrollToReport() {
  window.setTimeout(() => {
    reportSectionRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, 50)
}

async function copyReport() {
  if (!adviceReport.value?.reportMarkdown) {
    return
  }
  try {
    await navigator.clipboard.writeText(adviceReport.value.reportMarkdown)
    ElMessage.success('报告已复制')
  } catch {
    ElMessage.error('复制失败，请手动选中报告内容复制')
  }
}

function downloadReport() {
  if (!adviceReport.value?.reportMarkdown) {
    return
  }
  const blob = new Blob([adviceReport.value.reportMarkdown], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `ai-inventory-risk-report-${new Date().toISOString().slice(0, 10)}.md`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

function buildReportErrorMessage(error) {
  if (error.code === 'ECONNABORTED') {
    return '生成超时，请稍后重试或查看后端日志'
  }
  return error.response?.data?.message || '生成 AI 建议报告失败'
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }
  return String(value).replace('T', ' ').slice(0, 19)
}

function renderMarkdown(markdown) {
  const lines = markdown.split(/\r?\n/)
  const html = []
  let paragraph = []
  let listItems = []
  let tableRows = []

  const flushParagraph = () => {
    if (!paragraph.length) return
    html.push(`<p>${formatInline(paragraph.join(' '))}</p>`)
    paragraph = []
  }
  const flushList = () => {
    if (!listItems.length) return
    html.push(`<ul>${listItems.map((item) => `<li>${formatInline(item)}</li>`).join('')}</ul>`)
    listItems = []
  }
  const flushTable = () => {
    if (!tableRows.length) return
    const rows = tableRows
      .filter((row) => !isMarkdownDividerRow(row))
      .map((row, index) => {
        const cells = splitMarkdownTableRow(row)
        const tag = index === 0 ? 'th' : 'td'
        return `<tr>${cells.map((cell) => `<${tag}>${formatInline(cell.trim())}</${tag}>`).join('')}</tr>`
      })
    if (rows.length) {
      html.push(`<div class="markdown-table-wrap"><table>${rows.join('')}</table></div>`)
    }
    tableRows = []
  }
  const flushBlocks = () => {
    flushParagraph()
    flushList()
    flushTable()
  }

  lines.forEach((rawLine) => {
    const line = rawLine.trim()
    if (!line) {
      flushBlocks()
      return
    }
    if (line.startsWith('|') && line.endsWith('|')) {
      flushParagraph()
      flushList()
      tableRows.push(line)
      return
    }
    flushTable()
    const heading = line.match(/^(#{1,4})\s+(.+)$/)
    if (heading) {
      flushParagraph()
      flushList()
      const level = Math.min(heading[1].length + 1, 5)
      html.push(`<h${level}>${formatInline(heading[2])}</h${level}>`)
      return
    }
    const bullet = line.match(/^[-*]\s+(.+)$/)
    if (bullet) {
      flushParagraph()
      listItems.push(bullet[1])
      return
    }
    paragraph.push(line)
  })

  flushBlocks()
  return html.join('')
}

function splitMarkdownTableRow(row) {
  return row.replace(/^\|/, '').replace(/\|$/, '').split('|')
}

function isMarkdownDividerRow(row) {
  return splitMarkdownTableRow(row).every((cell) => /^:?-{3,}:?$/.test(cell.trim()))
}

function formatInline(text) {
  return escapeHtml(text)
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

onMounted(async () => {
  await Promise.all([loadBatches(), loadRecords(), loadPreviewBaseData(), loadRiskAnalysis()])
})

onBeforeUnmount(() => {
  stopReportTimer()
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

.status-alert,
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

.report-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.report-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
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

.reason-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  line-height: 1.4;
}

.sub-reason {
  color: var(--el-text-color-secondary);
}

.report-panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  padding: 14px;
}

.report-panel pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
  font-family: inherit;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.markdown-preview {
  line-height: 1.65;
  color: var(--el-text-color-primary);
}

.markdown-preview :deep(h2),
.markdown-preview :deep(h3),
.markdown-preview :deep(h4),
.markdown-preview :deep(h5) {
  margin: 18px 0 10px;
  line-height: 1.3;
  color: var(--el-text-color-primary);
}

.markdown-preview :deep(h2) {
  margin-top: 0;
  font-size: 20px;
}

.markdown-preview :deep(h3) {
  font-size: 17px;
}

.markdown-preview :deep(h4),
.markdown-preview :deep(h5) {
  font-size: 15px;
}

.markdown-preview :deep(p) {
  margin: 8px 0;
}

.markdown-preview :deep(ul) {
  margin: 8px 0 12px;
  padding-left: 20px;
}

.markdown-preview :deep(li) {
  margin: 4px 0;
}

.markdown-preview :deep(code) {
  border-radius: 4px;
  background: var(--el-fill-color);
  padding: 1px 5px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
}

.markdown-preview :deep(.markdown-table-wrap) {
  overflow-x: auto;
  margin: 10px 0 14px;
}

.markdown-preview :deep(table) {
  width: 100%;
  min-width: 760px;
  border-collapse: collapse;
  background: var(--el-bg-color);
}

.markdown-preview :deep(th),
.markdown-preview :deep(td) {
  border: 1px solid var(--el-border-color-lighter);
  padding: 9px 10px;
  text-align: left;
  vertical-align: top;
}

.markdown-preview :deep(th) {
  background: var(--el-fill-color);
  font-weight: 600;
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

  .section-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .report-actions {
    justify-content: flex-start;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>

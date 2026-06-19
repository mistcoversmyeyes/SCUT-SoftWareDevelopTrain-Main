<template>
  <section class="module-shell">
    <el-card v-loading="loading">
      <template #header>
        <div class="toolbar">
          <h2>出库拣货</h2>
          <div class="toolbar-actions">
            <el-button @click="goBack">返回列表</el-button>
            <el-button type="warning" :loading="suspending" @click="handleSuspend">挂起拣货</el-button>
          </div>
        </div>
      </template>

      <el-alert v-if="errorMessage" type="error" :title="errorMessage" show-icon :closable="false" />

      <template v-if="order">
        <el-card shadow="never" class="info-card">
          <el-descriptions :column="3" border>
            <el-descriptions-item label="出库单号">{{ order.outboundNo }}</el-descriptions-item>
            <el-descriptions-item label="供应商（主）">
              {{ order.supplier?.code }} {{ order.supplier?.name }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusType(order.status)">{{ statusLabel(order.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="出库用途">{{ purposeLabel(order.purpose) }}</el-descriptions-item>
            <el-descriptions-item label="来源单号">{{ order.sourceDocNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注">{{ order.remark || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card shadow="never" class="section-card">
          <template #header><span>出库明细</span></template>
          <el-table :data="pendingLines" border stripe size="small" class="detail-table">
            <el-table-column prop="lineNo" label="行号" width="80" />
            <el-table-column prop="materialCode" label="物料编码" min-width="160" />
            <el-table-column prop="materialName" label="物料名称" min-width="200" />
            <el-table-column label="供应商" min-width="180">
              <template #default="{ row }">
                <template v-if="row.supplier">{{ row.supplier.code }} {{ row.supplier.name }}</template>
                <template v-else>—</template>
              </template>
            </el-table-column>
            <el-table-column prop="plannedQty" label="计划数量" width="120" align="right">
              <template #default="{ row }">{{ formatQty(row.plannedQty) }}</template>
            </el-table-column>
            <el-table-column prop="pickedQty" label="已拣数量" width="120" align="right">
              <template #default="{ row }">{{ formatQty(row.pickedQty) }}</template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never" class="section-card">
          <template #header>
            <div class="section-header">
              <span>拣货仓库推荐</span>
              <el-button size="small" @click="handleRecommend">查看 FIFO 推荐</el-button>
            </div>
          </template>
          <el-form inline>
            <el-form-item label="选择物料">
              <el-select v-model="recommendMaterialId" placeholder="选择物料" filterable clearable style="width: 260px">
                <el-option
                  v-for="line in pendingLines"
                  :key="line.materialId"
                  :label="`${line.materialCode} ${line.materialName}`"
                  :value="line.materialId"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="选择仓库">
              <el-select v-model="recommendWarehouseIds" placeholder="选择仓库" multiple filterable clearable style="width: 220px">
                <el-option
                  v-for="w in masterData.warehouses"
                  :key="w.id"
                  :label="w.name"
                  :value="w.id"
                />
              </el-select>
            </el-form-item>
          </el-form>

          <el-table v-if="recommendations.length" :data="recommendations" border stripe size="small">
            <el-table-column prop="locationName" label="库位" min-width="120" />
            <el-table-column prop="kanbanCode" label="看板码" min-width="160">
              <template #default="{ row }">
                <span>{{ row.kanbanCode }}</span>
                <el-button type="primary" link size="small" @click="copyKanban(row.kanbanCode)">
                  <el-icon><CopyDocument /></el-icon>
                </el-button>
              </template>
            </el-table-column>
            <el-table-column prop="availableQty" label="可拣数量" width="120" align="right">
              <template #default="{ row }">{{ formatQty(row.availableQty) }}</template>
            </el-table-column>
            <el-table-column prop="receivedAt" label="入库时间" width="180">
              <template #default="{ row }">{{ formatDateTime(row.receivedAt) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-else-if="!recommendations.length && recommendSearched" description="暂无推荐数据" />
        </el-card>

        <el-card shadow="never" class="section-card">
          <template #header><span>扫码出库</span></template>
          <el-form inline @submit.prevent="handleScanPick">
            <el-form-item label="拣货行">
              <el-select v-model="activeLineId" placeholder="选择出库明细行" style="width: 340px">
                <el-option
                  v-for="line in pendingLines"
                  :key="line.id"
                  :label="`行${line.lineNo} ${line.materialCode} ${line.materialName} (需${formatQty(line.plannedQty - (line.pickedQty || 0))})`"
                  :value="line.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="看板码">
              <el-input v-model="scanCode" placeholder="扫描或手输看板码" clearable />
            </el-form-item>
            <el-form-item label="数量（留空默认全量）">
              <el-input-number v-model="scanPickQty" :min="1" :max="activeLineRemaining" :step="1" :precision="0" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="scanning" native-type="submit">确认拣货</el-button>
            </el-form-item>
          </el-form>

          <el-alert v-if="scanMessage" :title="scanMessage" :type="scanMessageType" show-icon :closable="true" @close="scanMessage = ''" />
        </el-card>

        <el-card shadow="never" class="section-card">
          <template #header><span>已拣记录</span></template>
          <el-table :data="pickedRecords" border stripe size="small" v-if="pickedRecords.length">
            <el-table-column prop="kanbanCode" label="看板码" min-width="140" />
            <el-table-column prop="locationCode" label="库位" min-width="120" />
            <el-table-column prop="qty" label="数量" width="120" align="right">
              <template #default="{ row }">{{ formatQty(row.qty) }}</template>
            </el-table-column>
            <el-table-column prop="pickedAt" label="拣货时间" width="180">
              <template #default="{ row }">{{ formatDateTime(row.pickedAt) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无拣货记录" />
        </el-card>
      </template>
    </el-card>
  </section>
</template>

<script setup>
import { computed, onBeforeMount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CopyDocument } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  fetchOutboundOrderById,
  recommendPick,
  scanOutbound,
  startPicking,
  suspendPicking
} from '../../api/outbound'
import { fetchMasterDataOptions } from '../../api/masterData'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const errorMessage = ref('')
const order = ref(null)
const suspending = ref(false)
const scanning = ref(false)

const masterData = ref({
  warehouses: [],
  materials: [],
  suppliers: [],
  locations: []
})
const pickedRecords = ref([])

const recommendMaterialId = ref(undefined)
const recommendWarehouseIds = ref([])
const recommendations = ref([])
const recommendSearched = ref(false)

const scanCode = ref('')
const scanPickQty = ref(undefined)
const scanMessage = ref('')
const scanMessageType = ref('success')
const activeLineId = ref(undefined)

const pendingLines = computed(() => {
  return (order.value?.lines || []).filter(
    l => (l.plannedQty - (l.pickedQty || 0)) > 0
  )
})

const activeLine = computed(() => {
  return (order.value?.lines || []).find(l => l.id === activeLineId.value)
})

const activeLineRemaining = computed(() => {
  if (!activeLine.value) return 1
  return Math.max(1, activeLine.value.plannedQty - (activeLine.value.pickedQty || 0))
})

const statusMap = {
  DRAFT: '草稿',
  RELEASED: '已释放',
  PICKING: '拣货中',
  PARTIAL_SHIPPED: '部分发货',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

const statusTagType = {
  DRAFT: 'info',
  RELEASED: 'warning',
  PICKING: 'warning',
  PARTIAL_SHIPPED: 'success',
  COMPLETED: 'success',
  CANCELLED: 'danger'
}

const purposeMap = {
  PICKING: '生产领料',
  RETURN: '退货',
  TRANSFER: '调拨',
  OTHER: '其他'
}

function statusType(status) { return statusTagType[status] || 'info' }
function statusLabel(status) { return statusMap[status] || status || '未知' }
function purposeLabel(purpose) { return purposeMap[purpose] || purpose || '-' }

function formatDateTime(value) {
  if (!value) return '-'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleString('zh-CN')
}

function formatQty(value) {
  if (value === null || value === undefined) return '0'
  const num = Number(value)
  if (Number.isNaN(num)) return value
  return String(num)
}

function getOutboundId() {
  return Number(route.params.id)
}

async function loadData() {
  const id = getOutboundId()
  if (!id) {
    errorMessage.value = '出库单编号缺失'
    return
  }
  loading.value = true
  try {
    order.value = await fetchOutboundOrderById(id)
    if (order.value.pickedRecords) {
      pickedRecords.value = order.value.pickedRecords
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '加载出库单失败'
    order.value = null
  } finally {
    loading.value = false
  }
}

async function loadMasterData() {
  try {
    masterData.value = await fetchMasterDataOptions()
  } catch (_) {
    // silent
  }
}

async function handleRecommend() {
  if (!recommendMaterialId.value) {
    ElMessage.warning('请先选择物料')
    return
  }
  if (!recommendWarehouseIds.value.length) {
    ElMessage.warning('请至少选择一个仓库')
    return
  }
  const line = (order.value?.lines || []).find((l) => l.materialId === recommendMaterialId.value)
  if (!line) return
  const neededQty = Math.max(0, line.plannedQty - (line.pickedQty || 0))
  try {
    recommendations.value = await recommendPick({
      materialId: recommendMaterialId.value,
      warehouseIds: recommendWarehouseIds.value,
      neededQty
    })
    recommendSearched.value = true
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '推荐查询失败')
  }
}

async function handleScanPick() {
  const code = scanCode.value.trim()
  if (!code) {
    ElMessage.warning('请先输入看板码')
    return
  }
  if (!activeLineId.value) {
    ElMessage.warning('请先选择拣货行')
    return
  }

  scanning.value = true
  scanMessage.value = ''
  try {
    const result = await scanOutbound({
      kanbanCode: code,
      qty: scanPickQty.value || undefined,
      outboundOrderId: getOutboundId(),
      outboundOrderLineId: activeLineId.value
    })
    scanMessage.value = '拣货成功'
    scanMessageType.value = 'success'
    scanCode.value = ''
    scanPickQty.value = undefined

    // 记录本次拣货
    const line = order.value?.lines?.find(l => l.id === activeLineId.value)
    pickedRecords.value.push({
      kanbanCode: result.kanbanCode,
      locationCode: result.locationName,
      qty: result.pickedQty,
      materialCode: result.materialCode,
      materialName: result.materialName,
      pickedAt: result.occurredAt
    })

    await loadData()

    // 自动刷新 FIFO 推荐
    if (recommendSearched.value && recommendMaterialId.value) {
      handleRecommend()
    }

    // 检查当前行是否已拣完
    const updatedLine = order.value?.lines?.find(l => l.id === activeLineId.value)
    if (updatedLine && (updatedLine.pickedQty || 0) >= updatedLine.plannedQty) {
      activeLineId.value = undefined
    }

    if (order.value?.status === 'COMPLETED') {
      ElMessage.success('物料已全部出库')
      setTimeout(() => router.push('/outbound/orders'), 1500)
      return
    }
  } catch (error) {
    scanMessage.value = error.response?.data?.message || error.message || '拣货失败'
    scanMessageType.value = 'error'
  } finally {
    scanning.value = false
  }
}

async function handleSuspend() {
  const id = getOutboundId()
  if (!id) return
  suspending.value = true
  try {
    await suspendPicking(id)
    ElMessage.success('拣货已挂起')
    router.push('/outbound/orders')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '挂起失败')
  } finally {
    suspending.value = false
  }
}

async function copyKanban(code) {
  try {
    await navigator.clipboard.writeText(code)
    ElMessage.success('看板码已复制: ' + code)
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

function goBack() {
  router.push('/outbound/orders')
}

onBeforeMount(async () => {
  await loadMasterData()
  const id = getOutboundId()
  if (id) {
    try {
      await startPicking(id)
    } catch (_) {
      // ignore if already started
    }
  }
  await loadData()
})
</script>

<style scoped>
.module-shell { min-height: 360px; }

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.toolbar h2 { margin: 0; }
.toolbar-actions { display: flex; gap: 8px; }

.info-card { margin-bottom: 16px; }
.section-card { margin-top: 16px; }

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.detail-table { min-height: 120px; }
</style>

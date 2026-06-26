<template>
  <section class="module-shell">
    <el-card>
      <template #header>
        <div class="header-row">
          <div>
            <h2>批量入库创建</h2>
            <p>按供应商选择物料，生成待创建明细后统一设置库位。</p>
          </div>
          <el-button type="primary" @click="openSupplierStep">添加入库明细</el-button>
        </div>
      </template>

      <el-steps :active="activeStep" finish-status="success" simple>
        <el-step title="选择供应商" />
        <el-step title="选择物料与数量" />
        <el-step title="设置库位" />
        <el-step title="提交创建" />
      </el-steps>

      <el-form :model="batchForm" inline class="batch-meta-form">
        <el-form-item label="来源单号">
          <el-input v-model="batchForm.sourceDocNo" maxlength="64" clearable placeholder="可选" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="batchForm.remark" maxlength="255" clearable placeholder="可选" />
        </el-form-item>
      </el-form>

      <el-alert
        title="待创建明细按添加动作保留为独立记录；重复供应商、物料和数量不会自动合并。"
        type="info"
        show-icon
        :closable="false"
        class="section-gap"
      />

      <el-table :data="pendingLines" border stripe class="section-gap" empty-text="暂无待创建明细">
        <el-table-column prop="tempId" label="临时序号" width="100" />
        <el-table-column prop="supplierName" label="供应商" min-width="160" />
        <el-table-column prop="materialName" label="物料" min-width="180" />
        <el-table-column prop="containerTypeName" label="容器类型" min-width="160" />
        <el-table-column prop="plannedQty" label="总件数" width="100" align="right" />
        <el-table-column prop="boxCount" label="箱数" width="90" align="right" />
        <el-table-column prop="remainder" label="零头" width="90" align="right" />
        <el-table-column prop="warehouseName" label="目标仓库" min-width="150">
          <template #default="{ row }">{{ row.warehouseName || '未设置' }}</template>
        </el-table-column>
        <el-table-column prop="locationName" label="目标库位" min-width="160">
          <template #default="{ row }">{{ row.locationName || '未设置' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button text type="danger" @click="removePendingLine(row.tempId)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="summary-row">
        <span>待创建明细 {{ pendingLines.length }} 行</span>
        <span>已完成库位 {{ completeLineCount }} 行</span>
      </div>

      <div class="submit-row">
        <el-button :disabled="!pendingLines.length" @click="openLocationStep">批量设置库位</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submitBatch">
          提交创建
        </el-button>
      </div>
    </el-card>

    <el-dialog v-model="supplierDialogVisible" title="添加入库明细" width="980px" top="5vh">
      <el-form :model="selectionForm" label-width="88px">
        <el-form-item label="供应商">
          <el-select
            v-model="selectionForm.supplierId"
            filterable
            clearable
            placeholder="选择供应商"
            class="wide-control"
          >
            <el-option
              v-for="supplier in masterData.suppliers"
              :key="supplier.id"
              :value="supplier.id"
              :label="formatOption(supplier)"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <el-table :data="materialRows" border size="small" max-height="460" class="material-table">
        <el-table-column label="选择" width="70" align="center">
          <template #default="{ row }">
            <el-checkbox v-model="row.checked" :disabled="!selectionForm.supplierId" @change="onMaterialChecked(row)" />
          </template>
        </el-table-column>
        <el-table-column label="物料" min-width="220">
          <template #default="{ row }">{{ row.code }} {{ row.name }}</template>
        </el-table-column>
        <el-table-column prop="spec" label="规格型号" min-width="140" />
        <el-table-column label="容器类型" min-width="190">
          <template #default="{ row }">
            <el-select
              v-model="row.containerTypeId"
              size="small"
              placeholder="选择容器"
              :disabled="!row.checked || !row.containerOptions.length"
              @change="refreshMaterialBreakdown(row)"
            >
              <el-option
                v-for="container in row.containerOptions"
                :key="container.id"
                :value="container.id"
                :label="formatContainer(container)"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="总件数" width="150">
          <template #default="{ row }">
            <el-input-number
              v-model="row.plannedQty"
              size="small"
              :min="1"
              :precision="0"
              :step="1"
              :disabled="!row.checked"
              controls-position="right"
              @change="refreshMaterialBreakdown(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="箱数/零头" width="120" align="right">
          <template #default="{ row }">
            {{ row.boxCount || 0 }} / {{ row.remainder || 0 }}
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-space>
          <el-button @click="supplierDialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="!canAddSelection" @click="appendSelectedMaterials">
            生成待创建明细
          </el-button>
        </el-space>
      </template>
    </el-dialog>

    <el-dialog v-model="locationDialogVisible" title="批量设置库位" width="520px">
      <el-form :model="locationForm" label-width="88px">
        <el-form-item label="目标仓库">
          <el-select
            v-model="locationForm.warehouseId"
            filterable
            clearable
            placeholder="选择仓库"
            class="wide-control"
            @change="locationForm.locationId = undefined"
          >
            <el-option
              v-for="warehouse in masterData.warehouses"
              :key="warehouse.id"
              :value="warehouse.id"
              :label="formatOption(warehouse)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标库位">
          <el-select
            v-model="locationForm.locationId"
            filterable
            clearable
            placeholder="选择库位"
            class="wide-control"
            :disabled="!locationForm.warehouseId"
          >
            <el-option
              v-for="location in filteredLocations"
              :key="location.id"
              :value="location.id"
              :label="formatOption(location)"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-space>
          <el-button @click="locationDialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="!canApplyLocation" @click="applyLocationToAll">
            应用到全部待创建明细
          </el-button>
        </el-space>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { batchCreateInboundOrders } from '../../api/inbound'
import { fetchMasterDataOptions, fetchMaterialContainerTypes } from '../../api/masterData'
import { boxBreakdown, isCompleteBatchInboundLine } from '../../utils/batchInbound'

const masterData = reactive({
  suppliers: [],
  materials: [],
  warehouses: [],
  locations: []
})
const batchForm = reactive({ sourceDocNo: '', remark: '' })
const selectionForm = reactive({ supplierId: undefined })
const locationForm = reactive({ warehouseId: undefined, locationId: undefined })
const materialRows = ref([])
const pendingLines = ref([])
const supplierDialogVisible = ref(false)
const locationDialogVisible = ref(false)
const submitting = ref(false)
let tempSequence = 1

const selectedSupplier = computed(() => masterData.suppliers.find(s => s.id === selectionForm.supplierId))
const filteredLocations = computed(() => {
  if (!locationForm.warehouseId) return []
  return masterData.locations.filter(location => location.warehouseId === locationForm.warehouseId)
})
const completeLineCount = computed(() => pendingLines.value.filter(isCompleteBatchInboundLine).length)
const canSubmit = computed(() => pendingLines.value.length > 0 && pendingLines.value.every(isCompleteBatchInboundLine))
const canApplyLocation = computed(() => Boolean(locationForm.warehouseId && locationForm.locationId))
const canAddSelection = computed(() => {
  return Boolean(selectionForm.supplierId) && materialRows.value.some(row => {
    return row.checked && row.containerTypeId && Number(row.plannedQty) > 0
  })
})
const activeStep = computed(() => {
  if (!pendingLines.value.length) return supplierDialogVisible.value ? 1 : 0
  if (!canSubmit.value) return locationDialogVisible.value ? 2 : 1
  return submitting.value ? 3 : 2
})

onMounted(async () => {
  try {
    const data = await fetchMasterDataOptions()
    masterData.suppliers = data.suppliers || []
    masterData.materials = data.materials || []
    masterData.warehouses = data.warehouses || []
    masterData.locations = data.locations || []
    materialRows.value = masterData.materials.map(toMaterialRow)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载基础数据失败')
  }
})

function toMaterialRow(material) {
  return {
    ...material,
    checked: false,
    plannedQty: undefined,
    containerOptions: [],
    containerTypeId: undefined,
    capacityQty: 0,
    boxCount: 0,
    remainder: 0
  }
}

function formatOption(option) {
  if (!option) return ''
  return [option.code, option.name].filter(Boolean).join(' ')
}

function formatContainer(container) {
  const capacity = container.capacityQty ? `${container.capacityQty}件/箱` : '未设容量'
  const suffix = container.isDefault ? ' (默认)' : ''
  return `${container.containerName || container.name || container.code}${suffix} - ${capacity}`
}

function selectedContainer(row) {
  return row.containerOptions.find(container => container.id === row.containerTypeId)
}

function refreshMaterialBreakdown(row) {
  const container = selectedContainer(row)
  row.capacityQty = container?.capacityQty || 0
  const breakdown = boxBreakdown(row.plannedQty, row.capacityQty)
  row.boxCount = breakdown.boxCount
  row.remainder = breakdown.remainder
}

async function onMaterialChecked(row) {
  if (!row.checked) {
    row.plannedQty = undefined
    row.boxCount = 0
    row.remainder = 0
    return
  }
  if (row.containerOptions.length) {
    return
  }

  try {
    const types = await fetchMaterialContainerTypes(row.id)
    const list = Array.isArray(types) ? types : []
    if (!list.length) {
      ElMessage.warning('该物料未配置包装容器，请先在基础数据中配置')
      row.checked = false
      return
    }
    row.containerOptions = list
    const defaultContainer = list.find(container => container.isDefault) || list[0]
    row.containerTypeId = defaultContainer.id
    row.capacityQty = defaultContainer.capacityQty || 0
    refreshMaterialBreakdown(row)
  } catch (error) {
    row.checked = false
    ElMessage.error(error.response?.data?.message || '加载容器类型失败')
  }
}

function resetMaterialSelection() {
  materialRows.value = masterData.materials.map(toMaterialRow)
}

function openSupplierStep() {
  resetMaterialSelection()
  selectionForm.supplierId = undefined
  supplierDialogVisible.value = true
}

function appendSelectedMaterials() {
  const supplier = selectedSupplier.value
  if (!supplier) return

  const selectedRows = materialRows.value.filter(row => row.checked && row.containerTypeId && Number(row.plannedQty) > 0)
  const createdLines = selectedRows.map((row) => {
    refreshMaterialBreakdown(row)
    const container = selectedContainer(row)
    return {
      tempId: `L${tempSequence++}`,
      supplierId: supplier.id,
      supplierName: formatOption(supplier),
      materialId: row.id,
      materialName: formatOption(row),
      containerTypeId: row.containerTypeId,
      containerTypeName: formatContainer(container),
      plannedQty: Number(row.plannedQty),
      targetWarehouseId: undefined,
      targetLocationId: undefined,
      warehouseName: '',
      locationName: '',
      boxCount: row.boxCount,
      remainder: row.remainder
    }
  })

  pendingLines.value.push(...createdLines)
  supplierDialogVisible.value = false
  ElMessage.success(`已生成 ${createdLines.length} 行待创建明细`)
}

function openLocationStep() {
  if (!pendingLines.value.length) return
  locationForm.warehouseId = undefined
  locationForm.locationId = undefined
  locationDialogVisible.value = true
}

function applyLocationToAll() {
  const warehouse = masterData.warehouses.find(item => item.id === locationForm.warehouseId)
  const location = masterData.locations.find(item => item.id === locationForm.locationId)
  pendingLines.value = pendingLines.value.map(line => ({
    ...line,
    targetWarehouseId: locationForm.warehouseId,
    targetLocationId: locationForm.locationId,
    warehouseName: formatOption(warehouse),
    locationName: formatOption(location)
  }))
  locationDialogVisible.value = false
}

function removePendingLine(tempId) {
  pendingLines.value = pendingLines.value.filter(line => line.tempId !== tempId)
}

function toSubmitPayload() {
  return {
    sourceDocNo: batchForm.sourceDocNo.trim() || null,
    remark: batchForm.remark.trim() || null,
    lines: pendingLines.value.map(line => ({
      supplierId: line.supplierId,
      materialId: line.materialId,
      containerTypeId: line.containerTypeId,
      plannedQty: line.plannedQty,
      targetWarehouseId: line.targetWarehouseId,
      targetLocationId: line.targetLocationId
    }))
  }
}

async function submitBatch() {
  if (!canSubmit.value) {
    ElMessage.warning('请先为所有待创建明细设置库位')
    return
  }

  submitting.value = true
  try {
    const result = await batchCreateInboundOrders(toSubmitPayload())
    ElMessage.success(`已创建 ${result.orderCount || 0} 张入库单，${result.lineCount || pendingLines.value.length} 行明细`)
    pendingLines.value = []
    batchForm.sourceDocNo = ''
    batchForm.remark = ''
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '批量创建入库单失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.module-shell {
  min-height: 360px;
}

.header-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.header-row h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.header-row p {
  margin: 6px 0 0;
  color: #606266;
}

.batch-meta-form {
  margin-top: 18px;
}

.section-gap {
  margin-top: 16px;
}

.summary-row,
.submit-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 14px;
  margin-top: 16px;
}

.summary-row {
  color: #606266;
  font-size: 13px;
}

.wide-control {
  width: 100%;
}

.material-table {
  margin-top: 12px;
}
</style>

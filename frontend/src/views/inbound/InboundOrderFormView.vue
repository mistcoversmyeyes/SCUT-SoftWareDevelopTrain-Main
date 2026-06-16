<template>
  <el-dialog
    v-model="visibleSync"
    :title="isEditMode ? '编辑入库单' : '新建入库单'"
    width="1100px"
    top="4vh"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="98px" class="inbound-form">
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="来源单号" prop="sourceDocNo">
            <el-input v-model="form.sourceDocNo" maxlength="64" />
          </el-form-item>
        </el-col>

        <el-col :span="8">
          <el-form-item label="备注" prop="remark">
            <el-input v-model="form.remark" maxlength="255" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider>入库明细</el-divider>

      <div class="line-toolbar">
        <el-button type="primary" size="small" @click="appendLine">新增明细</el-button>
        <el-button type="success" size="small" @click="openBatchDialog">批量选择物料</el-button>
      </div>

      <el-table :data="form.lines" border size="small" class="detail-table">
        <el-table-column label="供应商" width="200">
          <template #default="{ row, $index }">
            <el-form-item
              :rules="lineRules.supplier"
              :prop="`lines.${$index}.supplierId`"
            >
              <el-select
                v-model="row.supplierId"
                placeholder="选择供应商"
                filterable
                clearable
              >
                <el-option
                  v-for="s in masterData.suppliers"
                  :key="s.id"
                  :label="`${s.code} ${s.name}`"
                  :value="s.id"
                />
              </el-select>
            </el-form-item>
          </template>
        </el-table-column>

        <el-table-column label="物料" width="200">
          <template #default="{ row, $index }">
            <el-form-item
              :rules="lineRules.material"
              :prop="`lines.${$index}.materialId`"
            >
              <el-select
                v-model="row.materialId"
                placeholder="选择物料"
                filterable
                clearable
              >
                <el-option
                  v-for="material in masterData.materials"
                  :key="material.id"
                  :label="`${material.code} ${material.name}`"
                  :value="material.id"
                />
              </el-select>
            </el-form-item>
          </template>
        </el-table-column>

        <el-table-column label="计划数量" width="140">
          <template #default="{ row, $index }">
            <el-form-item
              :rules="lineRules.qty"
              :prop="`lines.${$index}.plannedQty`"
            >
              <el-input-number
                v-model="row.plannedQty"
                :min="1"
                :precision="0"
                :step="1"
                style="width: 100%"
              />
            </el-form-item>
          </template>
        </el-table-column>

        <el-table-column label="目标仓库" width="180">
          <template #default="{ row, $index }">
            <el-form-item
              :rules="lineRules.warehouse"
              :prop="`lines.${$index}.targetWarehouseId`"
            >
              <el-select
                v-model="row.targetWarehouseId"
                placeholder="选择仓库"
                clearable
                @change="onWarehouseChange($index)"
              >
                <el-option
                  v-for="warehouse in masterData.warehouses"
                  :key="warehouse.id"
                  :label="`${warehouse.code} ${warehouse.name}`"
                  :value="warehouse.id"
                />
              </el-select>
            </el-form-item>
          </template>
        </el-table-column>

        <el-table-column label="目标库位" width="180">
          <template #default="{ row, $index }">
            <el-form-item
              :rules="lineRules.location"
              :prop="`lines.${$index}.targetLocationId`"
            >
              <el-select
                v-model="row.targetLocationId"
                placeholder="选择库位"
                clearable
                :disabled="!row.targetWarehouseId"
              >
                <el-option
                  v-for="location in filteredLocations(row.targetWarehouseId)"
                  :key="location.id"
                  :label="`${location.code} ${location.name}`"
                  :value="location.id"
                />
              </el-select>
            </el-form-item>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="100">
          <template #default="{ $index }">
            <el-button
              type="danger"
              text
              size="small"
              :disabled="form.lines.length === 1"
              @click="removeLine($index)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-form>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="visibleSync = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">
          {{ isEditMode ? '保存' : '创建' }}
        </el-button>
      </span>
    </template>
  </el-dialog>

  <!-- 批量选择物料弹窗 -->
  <el-dialog
    v-model="batchVisible"
    title="批量选择物料"
    width="900px"
    top="6vh"
    append-to-body
  >
    <el-form label-width="80px" class="batch-form">
      <el-row :gutter="16">
        <el-col :span="10">
          <el-form-item label="供应商" required>
            <el-select
              v-model="batchSupplierId"
              placeholder="选择供应商"
              filterable
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="s in masterData.suppliers"
                :key="s.id"
                :label="`${s.code} ${s.name}`"
                :value="s.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form :inline="true" :model="batchFilters" class="batch-filter-row">
        <el-form-item label="搜索">
          <el-input v-model="batchFilters.keyword" placeholder="编码/名称/规格" clearable style="width:180px" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="batchFilters.materialCode" placeholder="物料编码" clearable style="width:140px" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="batchFilters.materialName" placeholder="物料名称" clearable style="width:140px" />
        </el-form-item>
        <el-form-item label="规格">
          <el-input v-model="batchFilters.specification" placeholder="规格型号" clearable style="width:120px" />
        </el-form-item>
        <el-form-item label="容器">
          <el-select v-model="batchFilters.containerTypeId" placeholder="全部" clearable style="width:120px">
            <el-option
              v-for="ct in masterData.containerTypes"
              :key="ct.id"
              :label="ct.name"
              :value="ct.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="resetBatchFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table
        ref="batchTableRef"
        :data="batchPaginatedMaterials"
        border
        size="small"
        class="batch-material-table"
        @selection-change="onBatchSelectionChange"
        max-height="340"
      >
        <el-table-column type="selection" width="45" />
        <el-table-column prop="materialCode" label="物料编码" width="140" />
        <el-table-column prop="materialName" label="物料名称" width="180" />
        <el-table-column prop="specification" label="规格型号" width="120" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column label="容器类型" width="120">
          <template #default="{ row }">
            {{ containerTypeName(row.containerTypeId) }}
          </template>
        </el-table-column>
        <el-table-column label="供应商" width="160">
          <template #default="{ row }">
            {{ supplierName(row.supplierId) }}
          </template>
        </el-table-column>
      </el-table>

      <div class="batch-pagination">
        <el-pagination
          v-model:current-page="batchPage"
          :page-size="batchPageSize"
          :total="batchFilteredMaterials.length"
          layout="total, prev, pager, next"
          small
        />
      </div>

      <div class="batch-selected-info">
        已选 <strong>{{ batchSelectedMaterials.length }}</strong> 个物料
        （已存在于明细表的物料将自动去重）
      </div>
    </el-form>

    <template #footer>
      <el-button @click="batchVisible = false">取消</el-button>
      <el-button
        type="primary"
        :disabled="batchSelectedMaterials.length === 0 || !batchSupplierId"
        @click="confirmBatchAdd"
      >
        确认添加 ({{ batchSelectedMaterials.length }})
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchMaterials } from '../../api/masterData'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  mode: {
    type: String,
    default: 'create'
  },
  initialOrder: {
    type: Object,
    default: null
  },
  masterData: {
    type: Object,
    default: () => ({
      suppliers: [],
      materials: [],
      warehouses: [],
      locations: []
    })
  },
  onSave: {
    type: Function,
    default: null
  }
})

const emit = defineEmits(['update:visible', 'save'])

const formRef = ref()
const submitting = ref(false)
const visibleSync = ref(false)
const form = reactive({
  sourceDocNo: '',
  remark: '',
  lines: []
})

const isEditMode = computed(() => props.mode === 'edit')

const rules = {
  sourceDocNo: [{ max: 64, message: '来源单号不能超过 64 个字符', trigger: 'blur' }],
  remark: [{ max: 255, message: '备注不能超过 255 个字符', trigger: 'blur' }]
}

const lineRules = {
  supplier: [
    { required: true, message: '请选择供应商', trigger: 'change' }
  ],
  material: [
    { required: true, message: '请选择物料', trigger: 'change' }
  ],
  qty: [
    { required: true, message: '请输入计划数量', trigger: 'change' },
    {
      validator: (rule, value, callback) => {
        const num = Number(value)
        if (Number.isNaN(num) || num <= 0) {
          callback(new Error('计划数量必须大于 0'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  warehouse: [
    { required: true, message: '请选择目标仓库', trigger: 'change' }
  ],
  location: [
    { required: true, message: '请选择目标库位', trigger: 'change' }
  ]
}

watch(
  () => props.visible,
  (visible) => {
    visibleSync.value = visible
    if (visible) {
      initForm()
      if (formRef.value) {
        formRef.value.clearValidate()
      }
    }
  }
)

watch(visibleSync, (visible) => {
  emit('update:visible', visible)
})

const emptyLine = () => ({
  materialId: undefined,
  supplierId: undefined,
  plannedQty: undefined,
  targetWarehouseId: undefined,
  targetLocationId: undefined
})

function normalizeInitialOrder(order) {
  if (!order) {
    return {
      sourceDocNo: '',
      remark: '',
      lines: []
    }
  }

  return {
    sourceDocNo: order.sourceDocNo || '',
    remark: order.remark || '',
    lines: (order.lines || []).map((line) => ({
      materialId: line.materialId,
      supplierId: line.supplier?.id,
      plannedQty: line.plannedQty,
      targetWarehouseId: line.targetWarehouseId,
      targetLocationId: line.targetLocationId
    }))
  }
}

function initForm() {
  const normalized = normalizeInitialOrder(props.initialOrder)
  form.sourceDocNo = normalized.sourceDocNo
  form.remark = normalized.remark
  form.lines = normalized.lines.length ? normalized.lines : [emptyLine()]
}

function filteredLocations(warehouseId) {
  if (!warehouseId) {
    return []
  }
  return props.masterData.locations.filter((location) => location.warehouseId === warehouseId)
}

function appendLine() {
  form.lines.push(emptyLine())
}

function removeLine(index) {
  if (form.lines.length === 1) {
    return
  }
  form.lines.splice(index, 1)
}

// === 批量选择物料 ===
const batchVisible = ref(false)
const batchMaterials = ref([])
const batchSupplierId = ref(undefined)
const batchFilters = reactive({
  keyword: '',
  materialCode: '',
  materialName: '',
  specification: '',
  containerTypeId: undefined
})
const batchSelectedMaterials = ref([])
const batchTableRef = ref()
const batchPage = ref(1)
const batchPageSize = 15

const batchFilteredMaterials = computed(() => {
  let list = batchMaterials.value
  if (batchSupplierId.value) {
    list = list.filter(m => m.supplierId === batchSupplierId.value)
  }
  if (batchFilters.keyword) {
    const kw = batchFilters.keyword.toLowerCase()
    list = list.filter(m =>
      (m.materialCode || '').toLowerCase().includes(kw) ||
      (m.materialName || '').toLowerCase().includes(kw) ||
      (m.specification || '').toLowerCase().includes(kw)
    )
  }
  if (batchFilters.materialCode) list = list.filter(m => (m.materialCode || '').includes(batchFilters.materialCode))
  if (batchFilters.materialName) list = list.filter(m => (m.materialName || '').includes(batchFilters.materialName))
  if (batchFilters.specification) list = list.filter(m => (m.specification || '').includes(batchFilters.specification))
  if (batchFilters.containerTypeId) list = list.filter(m => m.containerTypeId === batchFilters.containerTypeId)
  return list
})

const batchPaginatedMaterials = computed(() => {
  const start = (batchPage.value - 1) * batchPageSize
  return batchFilteredMaterials.value.slice(start, start + batchPageSize)
})

function containerTypeName(id) {
  return (props.masterData.containerTypes || []).find(c => c.id === id)?.name || '-'
}

function supplierName(id) {
  const s = props.masterData.suppliers.find(s => s.id === id)
  return s ? `${s.code} ${s.name}` : '-'
}

function openBatchDialog() {
  if (form.lines.length > 0 && form.lines[0].supplierId && !batchSupplierId.value) {
    batchSupplierId.value = form.lines[0].supplierId
  }
  batchVisible.value = true
  batchPage.value = 1
  batchSelectedMaterials.value = []
  batchTableRef.value?.clearSelection()
  if (batchMaterials.value.length === 0) {
    fetchMaterials().then(data => { batchMaterials.value = data || [] })
  }
}

function onBatchSelectionChange(rows) {
  batchSelectedMaterials.value = rows
}

function resetBatchFilters() {
  batchFilters.keyword = ''
  batchFilters.materialCode = ''
  batchFilters.materialName = ''
  batchFilters.specification = ''
  batchFilters.containerTypeId = undefined
  batchPage.value = 1
}

watch(
  () => [batchFilters.keyword, batchFilters.materialCode, batchFilters.materialName, batchFilters.specification, batchFilters.containerTypeId],
  () => { batchPage.value = 1 }
)

watch(batchVisible, (v) => {
  if (!v) {
    batchSupplierId.value = undefined
    batchSelectedMaterials.value = []
    resetBatchFilters()
  }
})

function confirmBatchAdd() {
  const toAdd = batchSelectedMaterials.value.filter(
    m => !form.lines.some(l => l.materialId === m.id)
  )
  if (toAdd.length === 0) {
    ElMessage.info('所选物料已全部存在于明细表中')
    batchVisible.value = false
    return
  }
  // 移除占位空行（如果第一行 materialId 为空）
  if (form.lines.length === 1 && !form.lines[0].materialId) {
    form.lines = []
  }
  toAdd.forEach(m => {
    form.lines.push({
      materialId: m.id,
      supplierId: batchSupplierId.value,
      plannedQty: undefined,
      targetWarehouseId: undefined,
      targetLocationId: undefined
    })
  })
  batchVisible.value = false
  ElMessage.success(`已添加 ${toAdd.length} 个物料`)
}
// === 批量选择物料 END ===

function onWarehouseChange(index) {
  const line = form.lines[index]
  if (!line) {
    return
  }
  if (line.targetWarehouseId && line.targetLocationId) {
    const location = props.masterData.locations.find(
      (candidate) => candidate.id === line.targetLocationId
    )
    if (!location || location.warehouseId !== line.targetWarehouseId) {
      line.targetLocationId = undefined
    }
  }
}

function toPayload() {
  const lines = form.lines
    .map((line) => ({
      supplierId: line.supplierId,
      materialId: line.materialId,
      plannedQty: Number(line.plannedQty),
      targetWarehouseId: line.targetWarehouseId,
      targetLocationId: line.targetLocationId
    }))
    .filter((line) => !!line.materialId && !!line.supplierId)

  return {
    sourceDocNo: (form.sourceDocNo || '').trim() || null,
    remark: (form.remark || '').trim() || null,
    lines
  }
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  const payload = toPayload()
  if (!payload.lines.length) {
    ElMessage.warning('请至少保留一条入库明细')
    return
  }

  submitting.value = true
  try {
    if (props.onSave) {
      await props.onSave(payload, props.mode)
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.inbound-form {
  min-height: 260px;
}

.line-toolbar {
  margin: 0 0 8px;
}

.detail-table :deep(.el-input-number),
.detail-table :deep(.el-select) {
  width: 100%;
}

.detail-table :deep(.el-form-item) {
  margin-bottom: 0;
}

.detail-table :deep(.el-form-item__content) {
  margin-left: 0 !important;
}

.detail-table :deep(.el-form-item__error) {
  display: none;
}

.detail-table :deep(.el-table__cell) {
  padding: 6px 8px;
}

/* 批量选择物料弹窗 */
.batch-form {
  min-height: 200px;
}

.batch-filter-row {
  margin-top: 12px;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 6px;
}

.batch-filter-row :deep(.el-form-item) {
  margin-bottom: 6px;
}

.batch-material-table {
  margin-bottom: 8px;
}

.batch-pagination {
  display: flex;
  justify-content: flex-end;
}

.batch-selected-info {
  margin-top: 8px;
  color: #666;
  font-size: 13px;
}
</style>

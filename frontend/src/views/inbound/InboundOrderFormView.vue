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
      </div>

      <el-table :data="form.lines" border size="small" class="detail-table">
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
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

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
</style>

<template>
  <el-dialog
    v-model="visibleSync"
    :title="isEditMode ? '编辑出库单' : '新建出库单'"
    width="1050px"
    top="4vh"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="98px" class="outbound-form">
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="出库用途" prop="purpose">
            <el-select v-model="form.purpose" placeholder="请选择用途">
              <el-option label="生产领料" value="PICKING" />
              <el-option label="退货" value="RETURN" />
              <el-option label="调拨" value="TRANSFER" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="来源单号" prop="sourceDocNo">
            <el-input v-model="form.sourceDocNo" maxlength="64" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="备注" prop="remark">
            <el-input v-model="form.remark" maxlength="255" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider>出库明细</el-divider>

      <div class="line-toolbar">
        <el-button type="primary" size="small" @click="appendLine">新增明细</el-button>
      </div>

      <el-table :data="form.lines" border size="small" class="detail-table">
        <el-table-column label="供应商" width="170">
          <template #default="{ row, $index }">
            <el-form-item :rules="lineRules.supplier" :prop="`lines.${$index}.supplierId`">
              <el-select v-model="row.supplierId" placeholder="选供应商" filterable clearable>
                <el-option v-for="s in masterData.suppliers" :key="s.id"
                  :label="`${s.code} ${s.name}`" :value="s.id" />
              </el-select>
            </el-form-item>
          </template>
        </el-table-column>

        <el-table-column label="物料" width="180">
          <template #default="{ row, $index }">
            <el-form-item :rules="lineRules.material" :prop="`lines.${$index}.materialId`">
              <el-select v-model="row.materialId" placeholder="选物料" filterable clearable
                @change="onMaterialChange($index)">
                <el-option v-for="m in masterData.materials" :key="m.id"
                  :label="`${m.code} ${m.name}`" :value="m.id" />
              </el-select>
            </el-form-item>
          </template>
        </el-table-column>

        <el-table-column label="容器类型" width="190">
          <template #default="{ row, $index }">
            <el-form-item :rules="lineRules.containerType" :prop="`lines.${$index}.containerTypeId`">
              <el-select v-model="row.containerTypeId" placeholder="选容器" clearable
                :disabled="row._containerOptions && row._containerOptions.length === 1"
                @change="onContainerChange($index)" style="width:100%">
                <el-option v-for="ct in row._containerOptions" :key="ct.id" :value="ct.id"
                  :label="ct.containerName + (ct.isDefault ? ' (默认)' : '') + ' — ' + ct.capacityQty + '件/箱'" />
              </el-select>
            </el-form-item>
          </template>
        </el-table-column>

        <el-table-column label="箱数" width="110">
          <template #default="{ row, $index }">
            <el-form-item :rules="lineRules.boxCount" :prop="`lines.${$index}._boxCount`">
              <el-input-number v-model="row._boxCount" :min="1" :precision="0" :step="1"
                :disabled="!row._capacityQty" controls-position="right"
                style="width:100%" @change="onBoxCountChange($index)" />
            </el-form-item>
          </template>
        </el-table-column>

        <el-table-column label="总件数" width="100" align="right">
          <template #default="{ row }">
            <span :class="computedQty(row) > 0 ? 'qty-display' : 'qty-display-zero'">
              {{ computedQty(row) || '—' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="70">
          <template #default="{ $index }">
            <el-button type="danger" text size="small"
              :disabled="form.lines.length === 1" @click="removeLine($index)">删除</el-button>
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
import { fetchMaterialContainerTypes } from '../../api/masterData'

const props = defineProps({
  visible: { type: Boolean, default: false },
  mode: { type: String, default: 'create' },
  initialOrder: { type: Object, default: null },
  masterData: { type: Object, default: () => ({ suppliers:[], materials:[], warehouses:[], locations:[] }) },
  onSave: { type: Function, default: null }
})

const emit = defineEmits(['update:visible', 'save'])
const formRef = ref()
const submitting = ref(false)
const visibleSync = ref(false)
const form = reactive({ purpose: undefined, sourceDocNo: '', remark: '', lines: [] })
const isEditMode = computed(() => props.mode === 'edit')

const rules = {
  purpose: [{ required: true, message: '请选择出库用途', trigger: 'change' }],
  sourceDocNo: [{ max: 64, message: '来源单号不能超过 64 个字符', trigger: 'blur' }],
  remark: [{ max: 255, message: '备注不能超过 255 个字符', trigger: 'blur' }]
}

const lineRules = {
  supplier: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  material: [{ required: true, message: '请选择物料', trigger: 'change' }],
  containerType: [{ required: true, message: '请选择容器类型', trigger: 'change' }],
  boxCount: [{ required: true, message: '请填写箱数', trigger: 'change' },
    { validator: (_r, v, cb) => { const n = Number(v); cb(Number.isNaN(n)||n<1 ? new Error('至少1箱') : undefined) }, trigger: 'change' }]
}

watch(() => props.visible, (visible) => {
  visibleSync.value = visible
  if (visible) { initForm(); formRef.value?.clearValidate() }
})
watch(visibleSync, (v) => emit('update:visible', v))

const emptyLine = () => ({
  materialId: undefined, supplierId: undefined, plannedQty: undefined,
  containerTypeId: undefined, _containerOptions: [], _capacityQty: 0, _boxCount: 1
})

function normalizeInitialOrder(order) {
  if (!order) return { purpose: undefined, sourceDocNo: '', remark: '', lines: [] }
  return {
    purpose: order.purpose,
    sourceDocNo: order.sourceDocNo || '',
    remark: order.remark || '',
    lines: (order.lines || []).map(l => ({
      materialId: l.materialId,
      supplierId: l.supplier?.id,
      plannedQty: l.plannedQty,
      containerTypeId: l.containerTypeId
    }))
  }
}

function initForm() {
  const norm = normalizeInitialOrder(props.initialOrder)
  form.purpose = norm.purpose
  form.sourceDocNo = norm.sourceDocNo
  form.remark = norm.remark
  form.lines = norm.lines.length ? norm.lines.map(l => ({ ...emptyLine(), ...l })) : [emptyLine()]
  // Edit mode: load container options for each line
  if (norm.lines.length) {
    form.lines.forEach((line, i) => {
      if (line.materialId) {
        fetchMaterialContainerTypes(line.materialId).then(types => {
          const list = Array.isArray(types) ? types : []
          line._containerOptions = list
          if (list.length && line.containerTypeId) {
            const ct = list.find(t => t.id === line.containerTypeId)
            line._capacityQty = ct ? (ct.capacityQty || 0) : 0
            if (line._capacityQty > 0 && line.plannedQty) {
              line._boxCount = Math.floor(Number(line.plannedQty) / line._capacityQty) || 1
            }
          }
        })
      }
    })
  }
}

function computedQty(row) {
  const cap = row._capacityQty || 0
  const boxes = Number(row._boxCount) || 0
  if (cap <= 0 || boxes <= 0) return 0
  return boxes * cap
}

function onBoxCountChange(index) {
  const line = form.lines[index]
  if (!line) return
  line.plannedQty = computedQty(line)
}

function onContainerChange(index) {
  const line = form.lines[index]
  if (!line || !line.containerTypeId) return
  const ct = line._containerOptions?.find(t => t.id === line.containerTypeId)
  line._capacityQty = ct ? (ct.capacityQty || 0) : 0
  if (line._capacityQty > 0) {
    // For edit mode: unpack plannedQty → boxCount
    if (line.plannedQty && line._boxCount === 1) {
      const pq = Number(line.plannedQty)
      line._boxCount = Math.floor(pq / line._capacityQty) || 1
    }
    line.plannedQty = computedQty(line)
  }
}

function appendLine() { form.lines.push(emptyLine()) }
function removeLine(index) { if (form.lines.length > 1) form.lines.splice(index, 1) }

async function onMaterialChange(index) {
  const line = form.lines[index]
  if (!line) return
  if (!line.materialId) { line._containerOptions = []; line.containerTypeId = undefined; line._capacityQty = 0; return }
  try {
    const types = await fetchMaterialContainerTypes(line.materialId)
    const list = Array.isArray(types) ? types : []
    if (!list.length) {
      ElMessage.warning('该物料未配置包装容器，请先在基础数据中配置')
      line.materialId = undefined; line._containerOptions = []; line.containerTypeId = undefined; line._capacityQty = 0
      return
    }
    line._containerOptions = list
    if (list.length === 1) {
      line.containerTypeId = list[0].id
      line._capacityQty = list[0].capacityQty || 0
    } else {
      const def = list.find(t => t.isDefault)
      line.containerTypeId = def ? def.id : list[0].id
      line._capacityQty = (def || list[0]).capacityQty || 0
    }
    line.plannedQty = computedQty(line)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载容器类型失败')
    line._containerOptions = []; line.containerTypeId = undefined; line._capacityQty = 0
  }
}

function toPayload() {
  const lines = form.lines
    .filter(l => !!l.materialId && !!l.supplierId)
    .map(l => ({
      supplierId: l.supplierId,
      materialId: l.materialId,
      plannedQty: computedQty(l),
      containerTypeId: l.containerTypeId
    }))
  return {
    purpose: form.purpose,
    sourceDocNo: (form.sourceDocNo||'').trim() || null,
    remark: (form.remark||'').trim() || null,
    lines
  }
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const payload = toPayload()
  if (!payload.lines.length) { ElMessage.warning('请至少保留一条出库明细'); return }
  submitting.value = true
  try { if (props.onSave) await props.onSave(payload, props.mode) }
  finally { submitting.value = false }
}
</script>

<style scoped>
.outbound-form { min-height: 260px; }
.line-toolbar { margin: 0 0 8px; }
.detail-table :deep(.el-input-number),
.detail-table :deep(.el-select) { width: 100%; }
.detail-table :deep(.el-form-item) { margin-bottom: 0; }
.detail-table :deep(.el-form-item__content) { margin-left: 0 !important; }
.detail-table :deep(.el-form-item__error) { display: none; }
.detail-table :deep(.el-table__cell) { padding: 6px 8px; }
.qty-display { font-weight: 700; color: #409eff; }
.qty-display-zero { color: #c0c4cc; }
</style>

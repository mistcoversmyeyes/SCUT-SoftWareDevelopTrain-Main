<template>
  <section class="module-shell">
    <el-card>
      <div class="card-title-row">
        <h2>批量出库</h2>
        <el-button type="primary" @click="supplierDialogVisible = true">
          选择供应商和物料
        </el-button>
      </div>

      <el-form :model="batchForm" label-width="98px" style="margin-top:16px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="出库用途">
              <el-select v-model="batchForm.purpose" placeholder="请选择用途" clearable style="width:100%">
                <el-option label="生产领料" value="PICKING" />
                <el-option label="退货" value="RETURN" />
                <el-option label="调拨" value="TRANSFER" />
                <el-option label="其他" value="OTHER" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注">
              <el-input v-model="batchForm.remark" maxlength="255" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-alert type="info" :closable="false" show-icon style="margin:12px 0"
        title="不同供应商的物料将自动拆分为独立出库单。同一供应商的物料归入同一张出库单。" />

      <!-- 待创建明细预览 -->
      <el-table :data="pendingLines" border size="small" style="margin-top:12px">
        <el-table-column label="供应商" min-width="160">
          <template #default="{ row }">{{ row.supplierName }}</template>
        </el-table-column>
        <el-table-column label="物料" min-width="180">
          <template #default="{ row }">{{ row.materialName }}</template>
        </el-table-column>
        <el-table-column label="出库件数" width="150" align="right">
          <template #default="{ row }">{{ row.plannedQty }}</template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template #default="{ $index }">
            <el-button type="danger" text size="small" @click="removeLine($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="submit-row" v-if="pendingLines.length">
        <el-statistic title="明细行数" :value="pendingLines.length" style="margin-right:24px" />
        <el-statistic title="待建出库单数" :value="supplierGroupCount" style="margin-right:24px" />
        <el-button type="primary" size="large" :loading="submitting" :disabled="!canSubmit" @click="submitBatch">
          提交创建
        </el-button>
      </div>

      <el-empty v-else description="尚未选择物料，请点击上方按钮开始" />
    </el-card>

    <!-- 供应商→物料选择对话框 -->
    <el-dialog
      v-model="supplierDialogVisible"
      title="选择出库物料"
      width="900px"
      top="4vh"
      @opened="loadMasterData"
    >
      <el-form label-width="80px">
        <el-form-item label="供应商">
          <el-select
            v-model="selectionForm.supplierId"
            placeholder="请先选择供应商"
            filterable
            clearable
            size="large"
            style="width:320px"
            @change="onSupplierChange"
          >
            <el-option
              v-for="s in masterData.suppliers"
              :key="s.id"
              :label="`${s.code} ${s.name}`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <el-table
        v-if="selectionForm.supplierId"
        ref="materialTableRef"
        :data="materialRows"
        border
        size="small"
        max-height="400"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column label="物料编码" width="150">
          <template #default="{ row }">{{ row.code }}</template>
        </el-table-column>
        <el-table-column label="物料名称" min-width="180">
          <template #default="{ row }">{{ row.name }}</template>
        </el-table-column>
        <el-table-column label="出库件数" width="200">
          <template #default="{ row }">
            <el-input-number
              v-model="row.plannedQty"
              :min="1"
              :step="1"
              :precision="0"
              placeholder="输入出库件数"
              controls-position="right"
              style="width:100%"
              :disabled="!row._checked"
            />
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="selectionForm.supplierId && !materialRows.length && !loadingMaterials"
        :description="`该供应商暂无启用物料`"
      />

      <template #footer>
        <el-button @click="supplierDialogVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!canAddSelection" @click="appendSelectedMaterials">
          加入待创建 ({{ checkedCount }})
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { batchCreateOutboundOrders } from '../../api/outbound'
import { fetchMasterDataOptions, fetchMaterials } from '../../api/masterData'

const masterData = reactive({ suppliers: [], materials: [], warehouses: [], locations: [] })
const batchForm = reactive({ purpose: undefined, sourceDocNo: '', remark: '' })
const selectionForm = reactive({ supplierId: undefined })
const materialRows = ref([])
const pendingLines = ref([])
const supplierDialogVisible = ref(false)
const submitting = ref(false)
const loadingMaterials = ref(false)
let tempSequence = 1

const selectedSupplier = computed(() =>
  masterData.suppliers.find((s) => s.id === selectionForm.supplierId)
)

const checkedCount = computed(() =>
  materialRows.value.filter((r) => r._checked && r.plannedQty > 0).length
)

const canAddSelection = computed(() =>
  selectionForm.supplierId != null && checkedCount.value > 0
)

const supplierGroupCount = computed(() => {
  const ids = new Set(pendingLines.value.map((l) => l.supplierId))
  return ids.size
})

const canSubmit = computed(() => pendingLines.value.length > 0)

function toMaterialRow(material) {
  return {
    ...material,
    _checked: false,
    plannedQty: undefined
  }
}

async function loadMasterData() {
  if (masterData.suppliers.length) return
  try {
    const data = await fetchMasterDataOptions()
    masterData.suppliers = data.suppliers || []
  } catch { /* ignore */ }
}

function resetMaterialSelection() {
  materialRows.value = []
  if (!selectionForm.supplierId) return
  loadingMaterials.value = true
  fetchMaterials({ supplierId: selectionForm.supplierId })
    .then((list) => {
      materialRows.value = (Array.isArray(list) ? list : []).map((m) =>
        toMaterialRow({
          id: m.id,
          code: m.materialCode || m.code,
          name: m.materialName || m.name
        })
      )
    })
    .catch(() => { materialRows.value = [] })
    .finally(() => { loadingMaterials.value = false })
}

function onSupplierChange() {
  resetMaterialSelection()
}

function onSelectionChange(rows) {
  for (const r of materialRows.value) {
    r._checked = rows.includes(r)
  }
}

function appendSelectedMaterials() {
  const supplier = selectedSupplier.value
  const toAdd = materialRows.value.filter((r) => r._checked && r.plannedQty > 0)
  if (!toAdd.length) { ElMessage.warning('请勾选物料并填写出库件数'); return }

  for (const row of toAdd) {
    pendingLines.value.push({
      tempId: tempSequence++,
      supplierId: supplier.id,
      supplierName: `${supplier.code} ${supplier.name}`,
      materialId: row.id,
      materialName: `${row.code} ${row.name}`,
      plannedQty: row.plannedQty
    })
  }

  // Reset selection for next round
  materialRows.value = []
  selectionForm.supplierId = undefined
  supplierDialogVisible.value = false
  ElMessage.success(`已加入 ${toAdd.length} 条明细`)
}

function removeLine(index) {
  pendingLines.value.splice(index, 1)
}

function toSubmitPayload() {
  return {
    purpose: batchForm.purpose || 'PICKING',
    sourceDocNo: (batchForm.sourceDocNo || '').trim() || null,
    remark: (batchForm.remark || '').trim() || null,
    lines: pendingLines.value.map((l) => ({
      supplierId: l.supplierId,
      materialId: l.materialId,
      plannedQty: l.plannedQty
    }))
  }
}

async function submitBatch() {
  submitting.value = true
  try {
    const payload = toSubmitPayload()
    const result = await batchCreateOutboundOrders(payload)
    ElMessage.success(
      `批量创建完成：${result.orderCount} 张出库单，${result.lineCount} 行明细`
    )
    pendingLines.value = []
    batchForm.purpose = undefined
    batchForm.sourceDocNo = ''
    batchForm.remark = ''
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '批量创建失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.module-shell { max-width: 1100px; margin: 0 auto; }
.card-title-row {
  display: flex; justify-content: space-between; align-items: center;
}
.submit-row {
  display: flex; align-items: center; margin-top: 16px;
  padding: 12px 16px; background: var(--el-fill-color-light); border-radius: 8px;
}
</style>

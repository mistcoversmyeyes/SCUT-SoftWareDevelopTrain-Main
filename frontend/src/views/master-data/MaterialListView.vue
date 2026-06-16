<template>
  <section class="material-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <h2>物料管理</h2>
          <el-button type="primary" @click="openCreateDrawer">新建物料</el-button>
        </div>
      </template>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="物料编码">
          <el-input v-model="query.materialCode" placeholder="支持模糊输入" clearable />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input v-model="query.materialName" placeholder="支持模糊输入" clearable />
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="query.supplierId" placeholder="全部供应商" clearable filterable>
            <el-option
              v-for="s in suppliers"
              :key="s.id"
              :value="s.id"
              :label="s.supplierCode + ' ' + s.supplierName"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadMaterials">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon />

      <el-table v-loading="loading" :data="materials" border stripe size="small" class="data-table">
        <el-table-column prop="materialCode" label="物料编码" min-width="140" />
        <el-table-column prop="materialName" label="物料名称" min-width="180" />
        <el-table-column prop="specification" label="规格" min-width="140" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column label="供应商" min-width="160">
          <template #default="{ row }">
            <template v-if="row.supplier">{{ row.supplier.supplierCode }} {{ row.supplier.supplierName }}</template>
            <template v-else>—</template>
          </template>
        </el-table-column>
        <el-table-column label="容器类型" min-width="140">
          <template #default="{ row }">
            <template v-if="row.containerType">{{ row.containerType.containerName }}</template>
            <template v-else>—</template>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="(row.status === 'ACTIVE' || row.status === 'ENABLED') ? 'success' : 'danger'" effect="light">
              {{ (row.status === 'ACTIVE' || row.status === 'ENABLED') ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lowStockQty" label="低库存阈值" width="120" align="right">
          <template #default="{ row }">{{ row.lowStockQty ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="highStockQty" label="高库存阈值" width="120" align="right">
          <template #default="{ row }">{{ row.highStockQty ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" text @click="openEditDrawer(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !materials.length" description="暂无物料" />
    </el-card>

    <el-dialog
      v-model="drawerVisible"
      :title="drawerMode === 'create' ? '新建物料' : '编辑物料'"
      width="580px"
      top="6vh"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-form-item label="物料编码" prop="materialCode">
          <el-input v-model="form.materialCode" placeholder="请输入物料编码" />
        </el-form-item>
        <el-form-item label="物料名称" prop="materialName">
          <el-input v-model="form.materialName" placeholder="请输入物料名称" />
        </el-form-item>
        <el-form-item label="规格" prop="specification">
          <el-input v-model="form.specification" placeholder="请输入规格" />
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model="form.unit" placeholder="请输入单位" />
        </el-form-item>
        <el-form-item label="供应商" prop="supplierId">
          <el-select v-model="form.supplierId" placeholder="请选择供应商" filterable clearable>
            <el-option v-for="s in suppliers" :key="s.id" :value="s.id" :label="s.supplierCode + ' ' + s.supplierName" />
          </el-select>
        </el-form-item>
        <el-form-item label="容器类型" prop="containerTypeId">
          <el-select v-model="form.containerTypeId" placeholder="请选择容器类型" filterable clearable>
            <el-option v-for="ct in containerTypes" :key="ct.id" :value="ct.id" :label="ct.containerName" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择状态">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="低库存阈值" prop="lowStockQty">
          <el-input-number v-model="form.lowStockQty" :min="0" :step="1" :precision="0" style="width:100%" />
        </el-form-item>
        <el-form-item label="高库存阈值" prop="highStockQty">
          <el-input-number v-model="form.highStockQty" :min="0" :step="1" :precision="0" style="width:100%" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchSuppliers, fetchContainerTypes, fetchMaterials, createMaterial, updateMaterial } from '../../api/masterData'

const query = reactive({ materialCode: '', materialName: '', supplierId: '' })
const materials = ref([])
const suppliers = ref([])
const containerTypes = ref([])
const loading = ref(false)
const loadError = ref('')
const drawerVisible = ref(false)
const drawerMode = ref('create')
const editingId = ref(null)
const formRef = ref(null)
const saving = ref(false)

const form = reactive({
  materialCode: '', materialName: '', specification: '', unit: '',
  supplierId: null, containerTypeId: null, status: 'ACTIVE',
  lowStockQty: null, highStockQty: null
})

const formRules = {
  materialCode: [{ required: true, message: '请输入物料编码', trigger: 'blur' }],
  materialName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }]
}

function formatDateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

async function loadMaterials() {
  loading.value = true; loadError.value = ''
  try {
    materials.value = await fetchMaterials({
      materialCode: query.materialCode || undefined,
      materialName: query.materialName || undefined,
      supplierId: query.supplierId || undefined
    })
  } catch (error) {
    loadError.value = error.response?.data?.message || '物料列表加载失败'
    materials.value = []
  } finally { loading.value = false }
}

async function loadMasterData() {
  try {
    const [sl, ctl] = await Promise.all([fetchSuppliers({ pageSize: 9999 }), fetchContainerTypes({ pageSize: 9999 })])
    suppliers.value = sl; containerTypes.value = ctl
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '主数据加载失败')
  }
}

function resetFilters() { query.materialCode = ''; query.materialName = ''; query.supplierId = ''; loadMaterials() }

function resetForm() {
  form.materialCode = ''; form.materialName = ''; form.specification = ''; form.unit = ''
  form.supplierId = null; form.containerTypeId = null; form.status = 'ACTIVE'
  form.lowStockQty = null; form.highStockQty = null
}

function openCreateDrawer() { drawerMode.value = 'create'; editingId.value = null; resetForm(); drawerVisible.value = true }

function openEditDrawer(row) {
  drawerMode.value = 'edit'; editingId.value = row.id
  form.materialCode = row.materialCode; form.materialName = row.materialName
  form.specification = row.specification || ''; form.unit = row.unit || ''
  form.supplierId = row.supplierId || null; form.containerTypeId = row.containerTypeId || null
  form.status = (row.status === 'ENABLED' ? 'ACTIVE' : row.status) || 'ACTIVE'
  form.lowStockQty = row.lowStockQty ?? null; form.highStockQty = row.highStockQty ?? null
  drawerVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      materialCode: form.materialCode, materialName: form.materialName,
      specification: form.specification || undefined, unit: form.unit || undefined,
      supplierId: form.supplierId || undefined, containerTypeId: form.containerTypeId || undefined,
      status: form.status, lowStockQty: form.lowStockQty, highStockQty: form.highStockQty
    }
    if (drawerMode.value === 'edit') {
      await updateMaterial(editingId.value, payload)
      ElMessage.success('物料修改成功')
    } else {
      await createMaterial(payload)
      ElMessage.success('物料创建成功')
    }
    drawerVisible.value = false
    await loadMaterials()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败，请重试')
  } finally { saving.value = false }
}

onMounted(() => { Promise.all([loadMasterData(), loadMaterials()]) })
</script>

<style scoped>
.material-page :deep(.el-card__body) { padding-top: 12px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-header h2 { margin: 0; }
.filter-form { margin-bottom: 16px; }
.data-table { min-height: 260px; }
</style>

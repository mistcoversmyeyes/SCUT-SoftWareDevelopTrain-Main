<template>
  <section class="supplier-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <h2>供应商管理</h2>
          <el-button type="primary" @click="openCreateDrawer">新建供应商</el-button>
        </div>
      </template>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="供应商编码">
          <el-input v-model="query.supplierCode" placeholder="支持模糊输入" clearable />
        </el-form-item>
        <el-form-item label="供应商名称">
          <el-input v-model="query.supplierName" placeholder="支持模糊输入" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadSuppliers">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="loadError"
        :title="loadError"
        type="error"
        :closable="false"
        show-icon
      />

      <el-table
        v-loading="loading"
        :data="suppliers"
        border
        stripe
        size="small"
        class="data-table"
      >
        <el-table-column prop="supplierCode" label="供应商编码" min-width="140" />
        <el-table-column prop="supplierName" label="供应商名称" min-width="180" />
        <el-table-column prop="contactName" label="联系人" width="120" />
        <el-table-column prop="contactPhone" label="联系电话" width="140" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" effect="light">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-space size="small">
              <el-button type="primary" size="small" text @click="openEditDrawer(row)">编辑</el-button>
              <el-switch
                :model-value="row.status === 'ACTIVE'"
                :loading="row._statusLoading"
                size="small"
                active-text="启用"
                inactive-text="停用"
                @change="(val) => handleStatusChange(row, val)"
              />
            </el-space>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !suppliers.length" description="暂无供应商" />
    </el-card>

    <el-drawer
      v-model:visible="drawerVisible"
      :title="drawerMode === 'create' ? '新建供应商' : '编辑供应商'"
      size="400px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="100px"
        label-position="top"
      >
        <el-form-item label="供应商编码" prop="supplierCode">
          <el-input v-model="form.supplierCode" placeholder="请输入供应商编码" />
        </el-form-item>
        <el-form-item label="供应商名称" prop="supplierName">
          <el-input v-model="form.supplierName" placeholder="请输入供应商名称" />
        </el-form-item>
        <el-form-item label="联系人" prop="contactName">
          <el-input v-model="form.contactName" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择状态">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-space>
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </el-space>
      </template>
    </el-drawer>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchSuppliers, createSupplier, updateSupplier, updateSupplierStatus } from '../../api/masterData'

const query = reactive({
  supplierCode: '',
  supplierName: ''
})

const suppliers = ref([])
const loading = ref(false)
const loadError = ref('')
const drawerVisible = ref(false)
const drawerMode = ref('create')
const editingId = ref(null)
const formRef = ref(null)
const saving = ref(false)

const form = reactive({
  supplierCode: '',
  supplierName: '',
  contactName: '',
  contactPhone: '',
  status: 'ACTIVE'
})

const formRules = {
  supplierCode: [{ required: true, message: '请输入供应商编码', trigger: 'blur' }],
  supplierName: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }]
}

function formatDateTime(value) {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString()
}

async function loadSuppliers() {
  loading.value = true
  loadError.value = ''
  try {
    const payload = {
      supplierCode: query.supplierCode || undefined,
      supplierName: query.supplierName || undefined
    }
    const list = await fetchSuppliers(payload)
    suppliers.value = list
  } catch (error) {
    loadError.value = error.response?.data?.message || '供应商列表加载失败'
    suppliers.value = []
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  query.supplierCode = ''
  query.supplierName = ''
  loadSuppliers()
}

function resetForm() {
  form.supplierCode = ''
  form.supplierName = ''
  form.contactName = ''
  form.contactPhone = ''
  form.status = 'ACTIVE'
}

function openCreateDrawer() {
  drawerMode.value = 'create'
  editingId.value = null
  resetForm()
  drawerVisible.value = true
}

function openEditDrawer(row) {
  drawerMode.value = 'edit'
  editingId.value = row.id
  form.supplierCode = row.supplierCode
  form.supplierName = row.supplierName
  form.contactName = row.contactName || ''
  form.contactPhone = row.contactPhone || ''
  form.status = row.status || 'ACTIVE'
  drawerVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    const payload = {
      supplierCode: form.supplierCode,
      supplierName: form.supplierName,
      contactName: form.contactName || undefined,
      contactPhone: form.contactPhone || undefined,
      status: form.status
    }
    if (drawerMode.value === 'edit') {
      await updateSupplier(editingId.value, payload)
      ElMessage.success('供应商修改成功')
    } else {
      await createSupplier(payload)
      ElMessage.success('供应商创建成功')
    }
    drawerVisible.value = false
    await loadSuppliers()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败，请重试')
  } finally {
    saving.value = false
  }
}

async function handleStatusChange(row, val) {
  const newStatus = val ? 'ACTIVE' : 'INACTIVE'
  row._statusLoading = true
  try {
    await updateSupplierStatus(row.id, newStatus)
    ElMessage.success(newStatus === 'ACTIVE' ? '供应商已启用' : '供应商已停用')
    await loadSuppliers()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '状态更新失败')
  } finally {
    row._statusLoading = false
  }
}

onMounted(() => {
  loadSuppliers()
})
</script>

<style scoped>
.supplier-page :deep(.el-card__body) {
  padding-top: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h2 {
  margin: 0;
}

.filter-form {
  margin-bottom: 16px;
}

.data-table {
  min-height: 260px;
}
</style>

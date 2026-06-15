<template>
  <section class="container-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <h2>容器类型管理</h2>
          <el-button type="primary" @click="openCreateDrawer">新建容器类型</el-button>
        </div>
      </template>

      <el-alert
        v-if="loadError"
        :title="loadError"
        type="error"
        :closable="false"
        show-icon
      />

      <el-table
        v-loading="loading"
        :data="containerTypes"
        border
        stripe
        size="small"
        class="data-table"
      >
        <el-table-column prop="containerCode" label="容器编码" min-width="140" />
        <el-table-column prop="containerName" label="容器名称" min-width="180" />
        <el-table-column prop="capacityQty" label="容量" width="120" align="right">
          <template #default="{ row }">{{ formatQty(row.capacityQty) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" effect="light">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
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

      <el-empty v-if="!loading && !containerTypes.length" description="暂无容器类型" />
    </el-card>

    <el-drawer
      v-model:visible="drawerVisible"
      :title="drawerMode === 'create' ? '新建容器类型' : '编辑容器类型'"
      size="400px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="110px"
        label-position="top"
      >
        <el-form-item label="容器编码" prop="containerCode">
          <el-input v-model="form.containerCode" placeholder="请输入容器编码" />
        </el-form-item>
        <el-form-item label="容器名称" prop="containerName">
          <el-input v-model="form.containerName" placeholder="请输入容器名称" />
        </el-form-item>
        <el-form-item label="容量" prop="capacityQty">
          <el-input-number v-model="form.capacityQty" :min="0" :precision="3" style="width:100%" />
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
import { fetchContainerTypes, createContainerType, updateContainerType, updateContainerTypeStatus } from '../../api/masterData'

const containerTypes = ref([])
const loading = ref(false)
const loadError = ref('')
const drawerVisible = ref(false)
const drawerMode = ref('create')
const editingId = ref(null)
const formRef = ref(null)
const saving = ref(false)

const form = reactive({
  containerCode: '',
  containerName: '',
  capacityQty: 0,
  status: 'ACTIVE'
})

const formRules = {
  containerCode: [{ required: true, message: '请输入容器编码', trigger: 'blur' }],
  containerName: [{ required: true, message: '请输入容器名称', trigger: 'blur' }]
}

function formatQty(value) {
  if (value === null || value === undefined) {
    return '0'
  }
  const num = Number(value)
  if (Number.isNaN(num)) {
    return value
  }
  return num.toFixed(3)
}

async function loadContainerTypes() {
  loading.value = true
  loadError.value = ''
  try {
    const list = await fetchContainerTypes()
    containerTypes.value = list
  } catch (error) {
    loadError.value = error.response?.data?.message || '容器类型列表加载失败'
    containerTypes.value = []
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.containerCode = ''
  form.containerName = ''
  form.capacityQty = 0
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
  form.containerCode = row.containerCode
  form.containerName = row.containerName
  form.capacityQty = row.capacityQty ?? 0
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
      containerCode: form.containerCode,
      containerName: form.containerName,
      capacityQty: form.capacityQty,
      status: form.status
    }
    if (drawerMode.value === 'edit') {
      await updateContainerType(editingId.value, payload)
      ElMessage.success('容器类型修改成功')
    } else {
      await createContainerType(payload)
      ElMessage.success('容器类型创建成功')
    }
    drawerVisible.value = false
    await loadContainerTypes()
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
    await updateContainerTypeStatus(row.id, newStatus)
    ElMessage.success(newStatus === 'ACTIVE' ? '容器类型已启用' : '容器类型已停用')
    await loadContainerTypes()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '状态更新失败')
  } finally {
    row._statusLoading = false
  }
}

onMounted(() => {
  loadContainerTypes()
})
</script>

<style scoped>
.container-page :deep(.el-card__body) {
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

.data-table {
  min-height: 260px;
}
</style>

<template>
  <section class="warehouse-location-page">
    <el-card class="warehouse-card">
      <template #header>
        <div class="card-header">
          <h2>仓库管理</h2>
          <el-button type="primary" @click="openWarehouseDrawer('create')">新建仓库</el-button>
        </div>
      </template>

      <el-alert v-if="warehouseLoadError" :title="warehouseLoadError" type="error" :closable="false" show-icon />

      <el-table
        v-loading="warehouseLoading"
        :data="warehouses"
        border stripe size="small" class="data-table"
        highlight-current-row
        @row-click="handleWarehouseClick"
      >
        <el-table-column prop="warehouseCode" label="仓库编码" min-width="140" />
        <el-table-column prop="warehouseName" label="仓库名称" min-width="180" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="(row.status === 'ACTIVE' || row.status === 'ENABLED') ? 'success' : 'danger'" effect="light">
              {{ (row.status === 'ACTIVE' || row.status === 'ENABLED') ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" text @click.stop="openWarehouseDrawer('edit', row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!warehouseLoading && !warehouses.length" description="暂无仓库" />
    </el-card>

    <el-card class="location-card" v-if="selectedWarehouse">
      <template #header>
        <div class="card-header">
          <h2>库位管理 - {{ selectedWarehouse.warehouseName }}</h2>
          <el-button type="primary" @click="openLocationDrawer('create')">新建库位</el-button>
        </div>
      </template>

      <el-alert v-if="locationLoadError" :title="locationLoadError" type="error" :closable="false" show-icon />

      <el-table v-loading="locationLoading" :data="storageLocations" border stripe size="small" class="data-table">
        <el-table-column prop="locationCode" label="库位编码" min-width="140" />
        <el-table-column prop="locationName" label="库位名称" min-width="180" />
        <el-table-column prop="maxCapacity" label="库位容量" min-width="120">
          <template #default="{ row }">
            {{ row.maxCapacity != null ? row.maxCapacity : '未设置' }}
          </template>
        </el-table-column>
        <el-table-column prop="warehouseName" label="所属仓库" min-width="180" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="(row.status === 'ACTIVE' || row.status === 'ENABLED') ? 'success' : 'danger'" effect="light">
              {{ (row.status === 'ACTIVE' || row.status === 'ENABLED') ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" text @click="openLocationDrawer('edit', row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!locationLoading && !storageLocations.length" description="暂无库位" />
    </el-card>

    <el-empty v-else-if="!warehouseLoading" description="请先选择仓库" class="no-selection" />

    <!-- 仓库弹窗 -->
    <el-dialog
      v-model="warehouseDrawerVisible"
      :title="warehouseDrawerMode === 'create' ? '新建仓库' : '编辑仓库'"
      width="500px"
      top="6vh"
    >
      <el-form ref="warehouseFormRef" :model="warehouseForm" :rules="warehouseFormRules" label-width="100px">
        <el-form-item label="仓库编码" prop="warehouseCode">
          <el-input v-model="warehouseForm.warehouseCode" placeholder="请输入仓库编码" />
        </el-form-item>
        <el-form-item label="仓库名称" prop="warehouseName">
          <el-input v-model="warehouseForm.warehouseName" placeholder="请输入仓库名称" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="warehouseForm.status" placeholder="请选择状态">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="warehouseDrawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="warehouseSaving" @click="handleWarehouseSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 库位弹窗 -->
    <el-dialog
      v-model="locationDrawerVisible"
      :title="locationDrawerMode === 'create' ? '新建库位' : '编辑库位'"
      width="500px"
      top="6vh"
    >
      <el-form ref="locationFormRef" :model="locationForm" :rules="locationFormRules" label-width="100px">
        <el-form-item label="库位编码" prop="locationCode">
          <el-input v-model="locationForm.locationCode" placeholder="请输入库位编码" />
        </el-form-item>
        <el-form-item label="库位名称" prop="locationName">
          <el-input v-model="locationForm.locationName" placeholder="请输入库位名称" />
        </el-form-item>
        <el-form-item label="库位容量">
          <el-input-number v-model="locationForm.maxCapacity" :min="0" :precision="0" :step="10" placeholder="请输入库位容量" style="width: 100%" />
        </el-form-item>
        <el-form-item label="所属仓库" prop="warehouseId">
          <el-select v-model="locationForm.warehouseId" placeholder="请选择仓库" disabled>
            <el-option :value="selectedWarehouse?.id" :label="selectedWarehouse?.warehouseName" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="locationForm.status" placeholder="请选择状态">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="locationDrawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="locationSaving" @click="handleLocationSave">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchWarehouses, createWarehouse, updateWarehouse, fetchStorageLocations, createStorageLocation, updateStorageLocation } from '../../api/masterData'

const warehouses = ref([])
const warehouseLoading = ref(false)
const warehouseLoadError = ref('')
const selectedWarehouse = ref(null)

const storageLocations = ref([])
const locationLoading = ref(false)
const locationLoadError = ref('')

const warehouseDrawerVisible = ref(false)
const warehouseDrawerMode = ref('create')
const warehouseEditingId = ref(null)
const warehouseFormRef = ref(null)
const warehouseSaving = ref(false)

const warehouseForm = reactive({ warehouseCode: '', warehouseName: '', status: 'ENABLED' })
const warehouseFormRules = {
  warehouseCode: [{ required: true, message: '请输入仓库编码', trigger: 'blur' }],
  warehouseName: [{ required: true, message: '请输入仓库名称', trigger: 'blur' }]
}

const locationDrawerVisible = ref(false)
const locationDrawerMode = ref('create')
const locationEditingId = ref(null)
const locationFormRef = ref(null)
const locationSaving = ref(false)

const locationForm = reactive({ locationCode: '', locationName: '', maxCapacity: null, warehouseId: null, status: 'ENABLED' })
const locationFormRules = {
  locationCode: [{ required: true, message: '请输入库位编码', trigger: 'blur' }],
  locationName: [{ required: true, message: '请输入库位名称', trigger: 'blur' }]
}

async function loadWarehouses() {
  warehouseLoading.value = true; warehouseLoadError.value = ''
  try { warehouses.value = await fetchWarehouses() }
  catch (error) { warehouseLoadError.value = error.response?.data?.message || '仓库列表加载失败'; warehouses.value = [] }
  finally { warehouseLoading.value = false }
}

async function loadStorageLocations() {
  if (!selectedWarehouse.value) { storageLocations.value = []; return }
  locationLoading.value = true; locationLoadError.value = ''
  try { storageLocations.value = await fetchStorageLocations({ warehouseId: selectedWarehouse.value.id }) }
  catch (error) { locationLoadError.value = error.response?.data?.message || '库位列表加载失败'; storageLocations.value = [] }
  finally { locationLoading.value = false }
}

function handleWarehouseClick(row) { selectedWarehouse.value = row; loadStorageLocations() }

function resetWarehouseForm() { warehouseForm.warehouseCode = ''; warehouseForm.warehouseName = ''; warehouseForm.status = 'ENABLED' }

function openWarehouseDrawer(mode, row) {
  warehouseDrawerMode.value = mode
  if (mode === 'create') { warehouseEditingId.value = null; resetWarehouseForm() }
  else { warehouseEditingId.value = row.id; warehouseForm.warehouseCode = row.warehouseCode; warehouseForm.warehouseName = row.warehouseName; warehouseForm.status = row.status || 'ENABLED' }
  warehouseDrawerVisible.value = true
}

async function handleWarehouseSave() {
  if (!warehouseFormRef.value) { ElMessage.error('表单未就绪'); return }
  const valid = await warehouseFormRef.value.validate().catch(() => false)
  if (!valid) { ElMessage.warning('请填写必填字段'); return }
  warehouseSaving.value = true
  try {
    const payload = { warehouseCode: warehouseForm.warehouseCode, warehouseName: warehouseForm.warehouseName, status: warehouseForm.status }
    if (warehouseDrawerMode.value === 'edit') { await updateWarehouse(warehouseEditingId.value, payload); ElMessage.success('仓库修改成功') }
    else { await createWarehouse(payload); ElMessage.success('仓库创建成功') }
    warehouseDrawerVisible.value = false
    await loadWarehouses()
  } catch (error) { ElMessage.error(error.response?.data?.message || '保存失败') }
  finally { warehouseSaving.value = false }
}

function resetLocationForm() { locationForm.locationCode = ''; locationForm.locationName = ''; locationForm.maxCapacity = null; locationForm.warehouseId = selectedWarehouse.value?.id || null; locationForm.status = 'ENABLED' }

function openLocationDrawer(mode, row) {
  locationDrawerMode.value = mode
  if (mode === 'create') { locationEditingId.value = null; resetLocationForm() }
  else { locationEditingId.value = row.id; locationForm.locationCode = row.locationCode; locationForm.locationName = row.locationName; locationForm.maxCapacity = row.maxCapacity != null ? Number(row.maxCapacity) : null; locationForm.warehouseId = row.warehouseId || selectedWarehouse.value?.id; locationForm.status = row.status || 'ENABLED' }
  locationDrawerVisible.value = true
}

async function handleLocationSave() {
  if (!locationFormRef.value) { ElMessage.error('表单未就绪'); return }
  const valid = await locationFormRef.value.validate().catch(() => false)
  if (!valid) { ElMessage.warning('请填写必填字段'); return }
  locationSaving.value = true
  try {
    const payload = { locationCode: locationForm.locationCode, locationName: locationForm.locationName, warehouseId: locationForm.warehouseId, maxCapacity: locationForm.maxCapacity, status: locationForm.status }
    if (locationDrawerMode.value === 'edit') { await updateStorageLocation(locationEditingId.value, payload); ElMessage.success('库位修改成功') }
    else { await createStorageLocation(payload); ElMessage.success('库位创建成功') }
    locationDrawerVisible.value = false
    await loadStorageLocations()
  } catch (error) { ElMessage.error(error.response?.data?.message || '保存失败') }
  finally { locationSaving.value = false }
}

onMounted(() => { loadWarehouses() })
</script>

<style scoped>
.warehouse-location-page :deep(.el-card__body) { padding-top: 12px; }
.warehouse-card { margin-bottom: 20px; }
.location-card { margin-bottom: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-header h2 { margin: 0; }
.data-table { min-height: 200px; }
.no-selection { margin-top: 24px; }
</style>

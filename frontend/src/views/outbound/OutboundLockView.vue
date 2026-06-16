<template>
  <section class="lock-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <h2>锁货管理</h2>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="锁记录" name="locks">
          <el-form :model="query" inline class="filter-form">
            <el-form-item label="出库单号">
              <el-input v-model="query.outboundNo" placeholder="模糊搜索" clearable style="width: 180px" />
            </el-form-item>
            <el-form-item label="物料编码">
              <el-input v-model="query.materialCode" placeholder="模糊搜索" clearable style="width: 160px" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
                <el-option label="锁定中" value="LOCKED" />
                <el-option label="已释放" value="RELEASED" />
                <el-option label="被抢锁" value="FORCE_STOLEN" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" @click="loadLocks">查询</el-button>
              <el-button @click="resetFilters">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table
            v-loading="loading"
            :data="lockOrders"
            border
            stripe
            size="small"
            highlight-current-row
            @row-click="selectOrder"
          >
            <el-table-column prop="outboundNo" label="出库单号" min-width="160" />
            <el-table-column prop="supplierName" label="供应商" width="140" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'COMPLETED' ? 'success' : ''" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="totalLockQty" label="锁总量" width="100" align="right" />
            <el-table-column prop="totalPickedQty" label="已拣量" width="100" align="right" />
            <el-table-column label="被抢锁" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.hasForceStolen" type="danger" size="small">是</el-tag>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button type="warning" size="small" text @click.stop="handleReassign(row)">重新分配</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!loading && !lockOrders.length" description="暂无锁记录" />

          <template v-if="selectedOrderId">
            <h3 style="margin-top: 24px">锁明细 — 出库单 #{{ selectedOrderId }}</h3>
            <el-table :data="lockDetails" border stripe size="small">
              <el-table-column prop="kanbanCode" label="看板码" min-width="180" />
              <el-table-column prop="materialCode" label="物料编码" width="120" />
              <el-table-column prop="materialName" label="物料名称" min-width="140" />
              <el-table-column prop="locationName" label="库位" width="120" />
              <el-table-column prop="lockQty" label="锁定量" width="100" align="right" />
              <el-table-column prop="lockStatus" label="状态" width="100">
                <template #default="{ row: d }">
                  <el-tag v-if="d.lockStatus === 'LOCKED'" type="warning" size="small">锁定中</el-tag>
                  <el-tag v-else-if="d.lockStatus === 'FORCE_STOLEN'" type="danger" size="small">被抢锁</el-tag>
                  <el-tag v-else-if="d.lockStatus === 'RELEASED'" type="info" size="small">已释放</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120">
                <template #default="{ row: d }">
                  <el-button
                    v-if="d.lockStatus === 'LOCKED'"
                    type="danger"
                    size="small"
                    text
                    @click="handleUnlock(d)"
                  >
                    解锁
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
        </el-tab-pane>

        <el-tab-pane label="强制出库审计" name="logs">
          <el-form :model="logQuery" inline class="filter-form">
            <el-form-item label="出库单号">
              <el-input v-model="logQuery.outboundNo" placeholder="模糊搜索" clearable style="width: 180px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="logLoading" @click="loadForceLogs">查询</el-button>
            </el-form-item>
          </el-form>

          <el-table
            v-loading="logLoading"
            :data="forceLogs"
            border
            stripe
            size="small"
          >
            <el-table-column prop="kanbanCode" label="看板码" min-width="180" />
            <el-table-column prop="materialCode" label="物料编码" width="120" />
            <el-table-column prop="materialName" label="物料名称" min-width="140" />
            <el-table-column prop="qty" label="数量" width="100" align="right" />
            <el-table-column prop="originalOutboundNo" label="原出库单" width="160" />
            <el-table-column prop="stolenByOutboundNo" label="抢锁单" width="160" />
            <el-table-column prop="stolenAt" label="抢锁时间" width="180">
              <template #default="{ row }">
                {{ formatDateTime(row.stolenAt) }}
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!logLoading && !forceLogs.length" description="暂无强制出库记录" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </section>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchLockOrders,
  fetchLockDetails,
  unlockRecord,
  reassignOrder,
  fetchForceLogs
} from '../../api/outbound'

const activeTab = ref('locks')

const query = reactive({ outboundNo: '', materialCode: '', status: '' })
const lockOrders = ref([])
const loading = ref(false)
const selectedOrderId = ref(null)
const lockDetails = ref([])

const logQuery = reactive({ outboundNo: '' })
const forceLogs = ref([])
const logLoading = ref(false)

async function loadLocks() {
  loading.value = true
  try {
    lockOrders.value = await fetchLockOrders({
      outboundNo: query.outboundNo || undefined,
      materialCode: query.materialCode || undefined,
      status: query.status || undefined
    })
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载锁记录失败')
    lockOrders.value = []
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  query.outboundNo = ''
  query.materialCode = ''
  query.status = ''
  loadLocks()
}

async function selectOrder(row) {
  selectedOrderId.value = row.outboundOrderId
  try {
    lockDetails.value = await fetchLockDetails(row.outboundOrderId)
  } catch (error) {
    ElMessage.error('加载锁明细失败')
    lockDetails.value = []
  }
}

async function handleUnlock(detail) {
  try {
    await ElMessageBox.confirm('确认解锁该看板？解锁后看板将恢复到可被其他出库单锁定状态。', '确认解锁', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await unlockRecord(detail.lockId)
    ElMessage.success('解锁成功')
    if (selectedOrderId.value) {
      selectOrder({ outboundOrderId: selectedOrderId.value })
    }
    await loadLocks()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '解锁失败')
    }
  }
}

async function handleReassign(row) {
  try {
    await ElMessageBox.confirm('确认重新分配？将释放所有现有锁定并重新按 FIFO 分配。', '确认重新分配', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await reassignOrder(row.outboundOrderId)
    ElMessage.success('重新分配成功')
    await loadLocks()
    if (selectedOrderId.value === row.outboundOrderId) {
      selectOrder(row)
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '重新分配失败')
    }
  }
}

async function loadForceLogs() {
  logLoading.value = true
  try {
    forceLogs.value = await fetchForceLogs({
      outboundNo: logQuery.outboundNo || undefined
    })
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载审计日志失败')
    forceLogs.value = []
  } finally {
    logLoading.value = false
  }
}

function formatDateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

onMounted(() => {
  loadLocks()
  loadForceLogs()
})
</script>

<style scoped>
.card-header h2 { margin: 0; }
.filter-form { margin-bottom: 16px; }
</style>

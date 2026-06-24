<template>
  <section class="lock-page">
    <el-card>
      <template #header>
        <div class="card-header"><h2>锁库管理</h2></div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="锁记录" name="locks">
          <el-form :model="query" inline class="filter-form">
            <el-form-item label="锁状态">
              <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
                <el-option label="锁定中" value="LOCKED" />
                <el-option label="已释放" value="RELEASED" />
                <el-option label="被抢锁" value="FORCE_STOLEN" />
              </el-select>
            </el-form-item>
            <el-form-item label="物料编码">
              <el-input v-model="query.materialCode" placeholder="模糊搜索" clearable style="width: 160px" />
            </el-form-item>
            <el-form-item label="出库单号">
              <el-input v-model="query.outboundNo" placeholder="模糊搜索" clearable style="width: 180px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" @click="loadLocks">查询</el-button>
              <el-button @click="resetFilters">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="loading" :data="inventoryTagLocks" border stripe size="small">
            <el-table-column prop="inventoryTagCode" label="库存标签码" min-width="200">
              <template #default="{ row }">
                <code>{{ row.inventoryTagCode }}</code>
              </template>
            </el-table-column>
            <el-table-column prop="materialCode" label="物料编码" width="140" />
            <el-table-column prop="materialName" label="物料名称" min-width="160" />
            <el-table-column prop="inventoryTagStatus" label="库存标签状态" width="100">
              <template #default="{ row }">
                <el-tag :type="inventoryTagStatusType(row.inventoryTagStatus)" size="small">
                  {{ inventoryTagStatusLabel(row.inventoryTagStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="locationName" label="库位" width="140" />
            <el-table-column prop="lockQty" label="锁定量" width="90" align="right" />
            <el-table-column prop="lockStatus" label="锁状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.lockStatus === 'LOCKED'" type="warning" size="small">锁定中</el-tag>
                <el-tag v-else-if="row.lockStatus === 'FORCE_STOLEN'" type="danger" size="small">被抢锁</el-tag>
                <el-tag v-else-if="row.lockStatus === 'RELEASED'" type="info" size="small">已释放</el-tag>
                <el-tag v-else size="small">{{ row.lockStatus }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="关联出库单" width="180">
              <template #default="{ row }">
                <template v-if="row.outboundNo">
                  <el-button type="primary" size="small" text @click="goToOrder(row.outboundOrderId)">
                    {{ row.outboundNo }}
                  </el-button>
                </template>
                <template v-else>—</template>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button
                  v-if="row.lockStatus === 'LOCKED'"
                  type="danger"
                  size="small"
                  text
                  @click="handleUnlock(row)"
                >
                  解锁
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!loading && !inventoryTagLocks.length" description="暂无锁记录" />
        </el-tab-pane>

        <el-tab-pane label="人工占用" name="holds">
          <el-form :model="holdQuery" inline class="filter-form">
            <el-form-item label="类型">
              <el-select v-model="holdQuery.holdType" placeholder="全部" clearable style="width: 140px">
                <el-option label="封存" value="SEALED" />
                <el-option label="手动锁库" value="MANUAL_LOCK" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="holdQuery.status" placeholder="全部" clearable style="width: 140px">
                <el-option label="生效中" value="ACTIVE" />
                <el-option label="已释放" value="RELEASED" />
              </el-select>
            </el-form-item>
            <el-form-item label="物料编码">
              <el-input v-model="holdQuery.materialCode" placeholder="模糊搜索" clearable style="width: 160px" />
            </el-form-item>
            <el-form-item label="库存标签码">
              <el-input v-model="holdQuery.inventoryTagCode" placeholder="模糊搜索" clearable style="width: 220px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="holdLoading" @click="loadHolds">查询</el-button>
              <el-button @click="resetHoldFilters">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="holdLoading" :data="holdRecords" border stripe size="small">
            <el-table-column prop="inventoryTagCode" label="库存标签码" min-width="190">
              <template #default="{ row }"><code>{{ row.inventoryTagCode }}</code></template>
            </el-table-column>
            <el-table-column prop="materialCode" label="物料编码" width="140" />
            <el-table-column prop="materialName" label="物料名称" min-width="170" />
            <el-table-column prop="locationName" label="库位" width="140" />
            <el-table-column prop="holdType" label="占用类型" width="110">
              <template #default="{ row }">
                <el-tag :type="holdTypeTagType(row.holdType)" size="small">{{ holdTypeLabel(row.holdType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="holdQty" label="数量" width="90" align="right" />
            <el-table-column prop="inventoryTagStatus" label="库存标签状态" width="100">
              <template #default="{ row }">
                <el-tag :type="inventoryTagStatusType(row.inventoryTagStatus)" size="small">
                  {{ inventoryTagStatusLabel(row.inventoryTagStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="记录状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'warning' : 'info'" size="small">
                  {{ row.status === 'ACTIVE' ? '生效中' : row.status === 'RELEASED' ? '已释放' : row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="原因" min-width="140" />
            <el-table-column prop="remark" label="备注" min-width="160">
              <template #default="{ row }">{{ row.remark || '—' }}</template>
            </el-table-column>
            <el-table-column prop="operatorName" label="操作人" width="110" />
            <el-table-column prop="createdAt" label="创建时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column prop="releasedAt" label="释放时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.releasedAt) }}</template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!holdLoading && !holdRecords.length" description="暂无人工占用记录" />
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

          <el-table v-loading="logLoading" :data="forceLogs" border stripe size="small">
            <el-table-column prop="inventoryTagCode" label="库存标签码" min-width="180" />
            <el-table-column prop="materialCode" label="物料编码" width="120" />
            <el-table-column prop="materialName" label="物料名称" min-width="140" />
            <el-table-column prop="qty" label="数量" width="100" align="right" />
            <el-table-column prop="originalOutboundNo" label="原出库单" width="160" />
            <el-table-column prop="stolenByOutboundNo" label="抢锁单" width="160" />
            <el-table-column prop="stolenAt" label="抢锁时间" width="180">
              <template #default="{ row }">{{ formatDateTime(row.stolenAt) }}</template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!logLoading && !forceLogs.length" description="暂无强制出库记录" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchInventoryHolds } from '../../api/inventoryTag'
import { fetchForceLogs, fetchInventoryTagLocks, unlockRecord } from '../../api/outbound'

const router = useRouter()
const activeTab = ref('locks')

const query = reactive({ status: '', materialCode: '', outboundNo: '' })
const inventoryTagLocks = ref([])
const loading = ref(false)

const holdQuery = reactive({ holdType: '', status: '', materialCode: '', inventoryTagCode: '' })
const holdRecords = ref([])
const holdLoading = ref(false)

const logQuery = reactive({ outboundNo: '' })
const forceLogs = ref([])
const logLoading = ref(false)

const inventoryTagStatusMap = {
  RECEIVED: '在库',
  LOCKED: '已锁定',
  SEALED: '已封存',
  SHIPPED: '已出库',
  PRINTED: '已打印',
  CANCELLED: '已取消'
}

const inventoryTagStatusTypeMap = {
  RECEIVED: 'success',
  LOCKED: 'warning',
  SEALED: 'danger',
  SHIPPED: 'info',
  PRINTED: '',
  CANCELLED: 'info'
}

const holdTypeMap = {
  SEALED: '封存',
  MANUAL_LOCK: '手动锁库'
}

const holdTypeTagMap = {
  SEALED: 'danger',
  MANUAL_LOCK: 'warning'
}

function inventoryTagStatusLabel(status) {
  return inventoryTagStatusMap[status] || status || '—'
}

function inventoryTagStatusType(status) {
  return inventoryTagStatusTypeMap[status] || 'info'
}

function holdTypeLabel(type) {
  return holdTypeMap[type] || type || '—'
}

function holdTypeTagType(type) {
  return holdTypeTagMap[type] || 'info'
}

async function loadLocks() {
  loading.value = true
  try {
    inventoryTagLocks.value = await fetchInventoryTagLocks({
      status: query.status || undefined,
      materialCode: query.materialCode || undefined,
      outboundNo: query.outboundNo || undefined
    })
  } catch {
    inventoryTagLocks.value = []
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  query.status = ''
  query.materialCode = ''
  query.outboundNo = ''
  loadLocks()
}

async function handleUnlock(row) {
  try {
    await ElMessageBox.confirm('确认解锁该库存标签？解锁后库存标签恢复为在库状态，关联出库单将自动补锁。', '确认解锁', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await unlockRecord(row.lockId)
    ElMessage.success('解锁成功')
    await loadLocks()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '解锁失败')
    }
  }
}

function goToOrder(id) {
  if (id) {
    router.push('/outbound/' + id)
  }
}

async function loadHolds() {
  holdLoading.value = true
  try {
    holdRecords.value = await fetchInventoryHolds({
      holdType: holdQuery.holdType || undefined,
      status: holdQuery.status || undefined,
      materialCode: holdQuery.materialCode || undefined,
      inventoryTagCode: holdQuery.inventoryTagCode || undefined
    })
  } catch {
    holdRecords.value = []
  } finally {
    holdLoading.value = false
  }
}

function resetHoldFilters() {
  holdQuery.holdType = ''
  holdQuery.status = ''
  holdQuery.materialCode = ''
  holdQuery.inventoryTagCode = ''
  loadHolds()
}

async function loadForceLogs() {
  logLoading.value = true
  try {
    forceLogs.value = await fetchForceLogs({ outboundNo: logQuery.outboundNo || undefined })
  } catch {
    forceLogs.value = []
  } finally {
    logLoading.value = false
  }
}

function formatDateTime(value) {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

onMounted(() => {
  loadLocks()
  loadHolds()
  loadForceLogs()
})
</script>

<style scoped>
.card-header h2 {
  margin: 0;
}

.filter-form {
  margin-bottom: 16px;
}

code {
  font-family: 'Courier New', monospace;
  font-size: 0.85rem;
}
</style>

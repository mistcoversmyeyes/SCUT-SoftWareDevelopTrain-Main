<template>
  <section class="lock-page">
    <el-card>
      <template #header>
        <div class="card-header"><h2>锁库管理</h2></div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- ═══════ 锁记录 — 看板直列 ═══════ -->
        <el-tab-pane label="锁记录" name="locks">
          <el-form :model="query" inline class="filter-form">
            <el-form-item label="锁状态">
              <el-select v-model="query.status" placeholder="全部" clearable style="width:140px">
                <el-option label="锁定中" value="LOCKED" />
                <el-option label="已释放" value="RELEASED" />
                <el-option label="被抢锁" value="FORCE_STOLEN" />
              </el-select>
            </el-form-item>
            <el-form-item label="物料编码">
              <el-input v-model="query.materialCode" placeholder="模糊搜索" clearable style="width:160px" />
            </el-form-item>
            <el-form-item label="出库单号">
              <el-input v-model="query.outboundNo" placeholder="模糊搜索" clearable style="width:180px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" @click="loadLocks">查询</el-button>
              <el-button @click="resetFilters">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="loading" :data="kanbanLocks" border stripe size="small">
            <el-table-column prop="kanbanCode" label="看板码" min-width="200">
              <template #default="{ row }">
                <code>{{ row.kanbanCode }}</code>
              </template>
            </el-table-column>
            <el-table-column prop="materialCode" label="物料编码" width="140" />
            <el-table-column prop="materialName" label="物料名称" min-width="160" />
            <el-table-column prop="kanbanStatus" label="看板状态" width="100">
              <template #default="{ row }">
                <el-tag :type="kanbanStatusType(row.kanbanStatus)" size="small">
                  {{ kanbanStatusLabel(row.kanbanStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="locationName" label="库位" width="140" />
            <el-table-column prop="lockQty" label="锁定量" width="90" align="right" />
            <el-table-column prop="lockStatus" label="锁状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.lockStatus==='LOCKED'" type="warning" size="small">锁定中</el-tag>
                <el-tag v-else-if="row.lockStatus==='FORCE_STOLEN'" type="danger" size="small">被抢锁</el-tag>
                <el-tag v-else-if="row.lockStatus==='RELEASED'" type="info" size="small">已释放</el-tag>
                <el-tag v-else size="small">{{ row.lockStatus }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="关联出库单" width="180">
              <template #default="{ row }">
                <template v-if="row.outboundNo">
                  <el-button type="primary" size="small" text
                    @click="goToOrder(row.outboundOrderId)">
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
                <el-button v-if="row.lockStatus==='LOCKED'" type="danger" size="small" text
                  @click="handleUnlock(row)">解锁</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!loading && !kanbanLocks.length" description="暂无锁记录" />
        </el-tab-pane>

        <!-- ═══════ 强制出库审计 ═══════ -->
        <el-tab-pane label="强制出库审计" name="logs">
          <el-form :model="logQuery" inline class="filter-form">
            <el-form-item label="出库单号">
              <el-input v-model="logQuery.outboundNo" placeholder="模糊搜索" clearable style="width:180px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="logLoading" @click="loadForceLogs">查询</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="logLoading" :data="forceLogs" border stripe size="small">
            <el-table-column prop="kanbanCode" label="看板码" min-width="180" />
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
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchKanbanLocks, fetchForceLogs, unlockRecord } from '../../api/outbound'

const router = useRouter()
const activeTab = ref('locks')

const query = reactive({ status: '', materialCode: '', outboundNo: '' })
const kanbanLocks = ref([])
const loading = ref(false)

const logQuery = reactive({ outboundNo: '' })
const forceLogs = ref([])
const logLoading = ref(false)

const kanbanStatusMap = { RECEIVED:'在库', LOCKED:'已锁定', SHIPPED:'已出库', PRINTED:'已打印', CANCELLED:'已取消' }
const kanbanStatusTypeMap = { RECEIVED:'success', LOCKED:'warning', SHIPPED:'info', PRINTED:'', CANCELLED:'danger' }

function kanbanStatusLabel(s) { return kanbanStatusMap[s] || s || '—' }
function kanbanStatusType(s) { return kanbanStatusTypeMap[s] || 'info' }

async function loadLocks() {
  loading.value = true
  try {
    kanbanLocks.value = await fetchKanbanLocks({
      status: query.status || undefined,
      materialCode: query.materialCode || undefined,
      outboundNo: query.outboundNo || undefined
    })
  } catch { kanbanLocks.value = [] }
  finally { loading.value = false }
}

function resetFilters() { query.status = ''; query.materialCode = ''; query.outboundNo = ''; loadLocks() }

async function handleUnlock(row) {
  try {
    await ElMessageBox.confirm('确认解锁该看板？解锁后看板恢复为在库状态，关联出库单将自动补锁。', '确认解锁', {
      confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning'
    })
    await unlockRecord(row.lockId)
    ElMessage.success('解锁成功')
    await loadLocks()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error.response?.data?.message || '解锁失败')
  }
}

function goToOrder(id) { if (id) router.push('/outbound/' + id) }

async function loadForceLogs() {
  logLoading.value = true
  try { forceLogs.value = await fetchForceLogs({ outboundNo: logQuery.outboundNo || undefined }) }
  catch { forceLogs.value = [] }
  finally { logLoading.value = false }
}

function formatDateTime(value) {
  if (!value) return '—'
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString()
}

onMounted(() => { loadLocks(); loadForceLogs() })
</script>

<style scoped>
.card-header h2 { margin: 0; }
.filter-form { margin-bottom: 16px; }
code { font-family: 'Courier New', monospace; font-size: 0.85rem; }
</style>

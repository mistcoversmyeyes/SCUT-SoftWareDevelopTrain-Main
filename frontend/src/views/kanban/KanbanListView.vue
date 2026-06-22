<template>
  <section class="kanban-list-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <h2>看板列表</h2>
        </div>
      </template>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option
              v-for="s in statusOptions"
              :key="s.value"
              :label="s.label"
              :value="s.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="入库单号">
          <el-input v-model="query.inboundNo" placeholder="支持模糊输入" clearable />
        </el-form-item>

        <el-form-item label="物料编码">
          <el-input v-model="query.materialCode" placeholder="支持模糊输入" clearable />
        </el-form-item>

        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            clearable
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadData">查询</el-button>
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
        :data="paginatedKanbans"
        border
        stripe
        size="small"
        class="kanban-table"
      >
        <el-table-column prop="kanbanCode" label="看板码" min-width="190">
          <template #default="{ row }">
            <span class="mono">{{ row.kanbanCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="inboundNo" label="入库单号" min-width="150" />
        <el-table-column prop="materialCode" label="物料编码" min-width="140" />
        <el-table-column prop="materialName" label="物料名称" min-width="200" />
        <el-table-column prop="qty" label="总量" width="90" align="right">
          <template #default="{ row }">{{ formatQty(row.qty) }}</template>
        </el-table-column>
        <el-table-column prop="pickedQty" label="已出库" width="90" align="right">
          <template #default="{ row }">{{ formatQty(row.pickedQty) }}</template>
        </el-table-column>
        <el-table-column prop="availableQty" label="剩余可查" width="100" align="right">
          <template #default="{ row }">{{ formatQty(row.availableQty ?? row.qty) }}</template>
        </el-table-column>
        <el-table-column prop="containerTypeName" label="容器类型" min-width="110">
          <template #default="{ row }">{{ row.containerTypeName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="业务占用" min-width="170">
          <template #default="{ row }">
            <div v-if="row.activeHoldType" class="hold-cell">
              <el-tag :type="holdTypeTagType(row.activeHoldType)" effect="light">
                {{ holdTypeLabel(row.activeHoldType) }}
              </el-tag>
              <span class="hold-reason">{{ row.activeHoldReason || '未填写原因' }}</span>
            </div>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="locationName" label="库位" min-width="130" />
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>

        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button type="primary" size="small" text @click="handleView(row)">
                查看详情
              </el-button>
              <el-button type="info" size="small" text @click="handleCopyCode(row)">
                复制看板码
              </el-button>
              <el-dropdown
                v-if="availableActionsForRow(row).length"
                @command="(command) => handleActionCommand(row, command)"
              >
                <el-button type="warning" size="small" text>
                  业务操作
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="action in availableActionsForRow(row)"
                      :key="action.command"
                      :command="action.command"
                    >
                      {{ action.label }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="kanbans.length > pageSize" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 15, 20, 50]"
          :total="kanbans.length"
          layout="total, sizes, prev, pager, next, jumper"
          background
        />
      </div>

      <el-empty v-if="!loading && !kanbans.length" description="暂无看板数据" />
    </el-card>

    <el-dialog
      v-model="holdDialog.visible"
      :title="holdDialogTitle"
      width="420px"
      :close-on-click-modal="false"
      @closed="resetHoldDialog"
    >
      <el-form ref="holdFormRef" :model="holdForm" :rules="holdRules" label-width="72px">
        <el-form-item label="看板码">
          <span class="mono">{{ holdDialog.row?.kanbanCode || '—' }}</span>
        </el-form-item>
        <el-form-item label="原因" prop="reason">
          <el-input v-model="holdForm.reason" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="holdForm.remark" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="holdForm.operator" maxlength="32" placeholder="默认 web" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="holdDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="holdSubmitting" @click="submitHoldAction">确认</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  fetchKanbanList,
  manualLockKanban,
  manualUnlockKanban,
  sealKanban,
  unsealKanban
} from '../../api/kanban'

const router = useRouter()
const holdFormRef = ref()

const statusOptions = [
  { value: 'PRINTED', label: '已打印' },
  { value: 'RECEIVED', label: '已入库' },
  { value: 'LOCKED', label: '已锁定' },
  { value: 'SEALED', label: '已封存' },
  { value: 'SHIPPED', label: '已出库' },
  { value: 'CANCELLED', label: '已取消' }
]

const query = reactive({
  status: '',
  inboundNo: '',
  materialCode: ''
})

const dateRange = ref(null)
const kanbans = ref([])
const loading = ref(false)
const loadError = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const holdSubmitting = ref(false)

const holdDialog = reactive({
  visible: false,
  mode: '',
  row: null
})

const holdForm = reactive({
  reason: '',
  remark: '',
  operator: 'web'
})

const holdRules = {
  reason: [{ required: true, message: '请输入原因', trigger: 'blur' }]
}

const paginatedKanbans = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return kanbans.value.slice(start, start + pageSize.value)
})

const holdDialogTitleMap = {
  seal: '人工封存',
  unseal: '解封看板',
  'manual-lock': '手动锁库',
  'manual-unlock': '手动解锁'
}

const statusMap = {
  PRINTED: '已打印',
  RECEIVED: '已入库',
  LOCKED: '已锁定',
  SEALED: '已封存',
  SHIPPED: '已出库',
  CANCELLED: '已取消'
}

const statusTagType = {
  PRINTED: 'warning',
  RECEIVED: 'success',
  LOCKED: 'warning',
  SEALED: 'danger',
  SHIPPED: 'info',
  CANCELLED: 'info'
}

const holdTypeMap = {
  SEALED: '已封存',
  MANUAL_LOCK: '手动锁库'
}

const holdTypeTagMap = {
  SEALED: 'danger',
  MANUAL_LOCK: 'warning'
}

const holdDialogTitle = computed(() => holdDialogTitleMap[holdDialog.mode] || '业务操作')

function statusType(status) {
  return statusTagType[status] || 'info'
}

function statusLabel(status) {
  return statusMap[status] || status || '未知'
}

function holdTypeLabel(type) {
  return holdTypeMap[type] || type || '—'
}

function holdTypeTagType(type) {
  return holdTypeTagMap[type] || 'info'
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

function formatQty(value) {
  if (value === null || value === undefined) {
    return '0'
  }
  const num = Number(value)
  if (Number.isNaN(num)) {
    return value
  }
  return String(num)
}

function remainingQty(row) {
  const value = row?.availableQty ?? row?.qty ?? 0
  const num = Number(value)
  return Number.isNaN(num) ? 0 : num
}

function availableActionsForRow(row) {
  const actions = []
  if (row.activeHoldType === 'SEALED') {
    actions.push({ command: 'unseal', label: '解封' })
    return actions
  }
  if (row.activeHoldType === 'MANUAL_LOCK') {
    actions.push({ command: 'manual-unlock', label: '手动解锁' })
    return actions
  }
  if (row.status === 'RECEIVED' && remainingQty(row) > 0) {
    actions.push({ command: 'seal', label: '封存' })
    actions.push({ command: 'manual-lock', label: '手动锁库' })
  }
  return actions
}

async function loadData() {
  loading.value = true
  loadError.value = ''
  try {
    const payload = {
      status: query.status || undefined,
      inboundNo: query.inboundNo || undefined,
      materialCode: query.materialCode || undefined
    }
    if (dateRange.value) {
      payload.startDate = dateRange.value[0]
      payload.endDate = dateRange.value[1]
    }
    const list = await fetchKanbanList(payload)
    kanbans.value = list
    currentPage.value = 1
  } catch (error) {
    loadError.value = error.response?.data?.message || '看板列表加载失败'
    kanbans.value = []
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  query.status = ''
  query.inboundNo = ''
  query.materialCode = ''
  dateRange.value = null
  loadData()
}

function handleView(row) {
  router.push('/inbound/' + row.inboundOrderId + '/kanbans')
}

async function handleCopyCode(row) {
  try {
    await navigator.clipboard.writeText(row.kanbanCode)
    ElMessage.success('看板码已复制: ' + row.kanbanCode)
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

function handleActionCommand(row, command) {
  holdDialog.row = row
  holdDialog.mode = command
  holdDialog.visible = true
  holdForm.reason = ''
  holdForm.remark = ''
  holdForm.operator = 'web'
}

function resetHoldDialog() {
  holdDialog.mode = ''
  holdDialog.row = null
  holdForm.reason = ''
  holdForm.remark = ''
  holdForm.operator = 'web'
  holdFormRef.value?.clearValidate()
}

async function submitHoldAction() {
  if (!holdDialog.row || !holdDialog.mode) {
    return
  }
  await holdFormRef.value?.validate()
  holdSubmitting.value = true
  try {
    const payload = {
      reason: holdForm.reason,
      remark: holdForm.remark || undefined,
      operator: holdForm.operator || 'web'
    }
    if (holdDialog.mode === 'seal') {
      await sealKanban(holdDialog.row.kanbanId, payload)
      ElMessage.success('封存成功')
    } else if (holdDialog.mode === 'unseal') {
      await unsealKanban(holdDialog.row.kanbanId, payload)
      ElMessage.success('解封成功')
    } else if (holdDialog.mode === 'manual-lock') {
      await manualLockKanban(holdDialog.row.kanbanId, payload)
      ElMessage.success('手动锁库成功')
    } else if (holdDialog.mode === 'manual-unlock') {
      await manualUnlockKanban(holdDialog.row.kanbanId, payload)
      ElMessage.success('手动解锁成功')
    }
    holdDialog.visible = false
    await loadData()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '操作失败')
  } finally {
    holdSubmitting.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.kanban-list-page :deep(.el-card__body) {
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

.kanban-table {
  min-height: 260px;
}

.mono {
  font-family: ui-monospace, Menlo, Consolas, monospace;
}

.hold-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.hold-reason {
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}

.action-group {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>

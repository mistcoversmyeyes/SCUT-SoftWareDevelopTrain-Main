<template>
  <section class="module-shell">
    <el-card>
      <template #header>
        <h2>看板追溯</h2>
      </template>

      <el-alert v-if="fetchError" type="error" :title="fetchError" :closable="false" show-icon />

      <el-form inline class="query-form" @submit.prevent="queryTrace">
        <el-form-item label="看板码">
          <el-input v-model="kanbanCode" size="default" clearable placeholder="输入看板码" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="queryTrace">查询</el-button>
          <el-button @click="resetTrace">清空</el-button>
        </el-form-item>
      </el-form>

      <el-card v-if="traceData" class="result-card" shadow="never">
        <template #header>
          <span>追溯结果</span>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="看板码">{{ traceData.kanbanCode }}</el-descriptions-item>
          <el-descriptions-item label="看板状态">{{ traceData.kanbanStatus }}</el-descriptions-item>
          <el-descriptions-item label="所属入库单">{{ traceData.inboundNo }}</el-descriptions-item>
          <el-descriptions-item label="物料">{{ traceData.materialCode }} {{ traceData.materialName }}</el-descriptions-item>
          <el-descriptions-item label="库位">{{ traceData.locationCode }} {{ traceData.locationName }}</el-descriptions-item>
          <el-descriptions-item label="扫码时间">{{ formatDateTime(traceData.scannedAt) }}</el-descriptions-item>
          <el-descriptions-item label="库存流水">{{ traceData.movementNo || '未生成' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-empty
        v-else-if="searched"
        description="未查询到看板信息"
      />
    </el-card>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { fetchKanbanTrace } from '../../api/kanban'

const kanbanCode = ref('')
const loading = ref(false)
const fetchError = ref('')
const traceData = ref(null)
const searched = ref(false)

async function queryTrace() {
  const code = kanbanCode.value.trim()
  if (!code) {
    fetchError.value = '请输入看板码'
    return
  }

  loading.value = true
  fetchError.value = ''
  searched.value = true
  traceData.value = null

  try {
    traceData.value = await fetchKanbanTrace(code)
  } catch (error) {
    const message = error.response?.data?.message || '未查询到看板或请求异常'
    if (error.response?.status === 404) {
      traceData.value = null
    } else {
      fetchError.value = message
    }
  } finally {
    loading.value = false
  }
}

function resetTrace() {
  kanbanCode.value = ''
  traceData.value = null
  searched.value = false
  fetchError.value = ''
}

function formatDateTime(value) {
  if (!value) return '-'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleString('zh-CN')
}
</script>

<style scoped>
.module-shell {
  min-height: 360px;
}

.query-form {
  margin-bottom: 12px;
}

.result-card {
  margin-top: 12px;
}

h2 {
  margin: 0;
}
</style>

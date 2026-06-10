<template>
  <section class="module-shell inbound-scan">
    <el-card>
      <template #header>
        <h2>入库扫码</h2>
      </template>

      <el-form class="scan-form" @submit.prevent="handleScan">
        <el-form-item label="看板码">
          <el-input
            ref="scanInputRef"
            v-model="kanbanCode"
            size="large"
            placeholder="请扫描或手输看板码后按回车"
            @keyup.enter="handleScan"
            :disabled="scanning"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="default"
            :loading="scanning"
            @click="handleScan"
          >
            触发扫码
          </el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="errorMessage"
        type="error"
        :title="errorMessage"
        show-icon
        :closable="false"
      />

      <el-card v-if="scanResult" class="result-card" shadow="never">
        <template #header>
          <span>扫码成功</span>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="看板码">
            {{ scanResult.kanbanCode }}
          </el-descriptions-item>
          <el-descriptions-item label="入库单号">
            {{ scanResult.inboundNo }}
          </el-descriptions-item>
          <el-descriptions-item label="物料编码">
            {{ scanResult.materialCode }}
          </el-descriptions-item>
          <el-descriptions-item label="物料名称">
            {{ scanResult.materialName }}
          </el-descriptions-item>
          <el-descriptions-item label="收货数量">
            {{ scanResult.receivedQty }}
          </el-descriptions-item>
          <el-descriptions-item label="目标库位">
            {{ scanResult.locationName }}
          </el-descriptions-item>
          <el-descriptions-item label="入库单状态">
            {{ scanResult.orderStatus }}
          </el-descriptions-item>
          <el-descriptions-item label="扫码时间">
            {{ formatDateTime(scanResult.receivedAt) }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>
    </el-card>
  </section>
</template>

<script setup>
import { nextTick, ref } from 'vue'
import { scanInbound } from '../../api/inventory'

const kanbanCode = ref('')
const scanning = ref(false)
const scanResult = ref(null)
const errorMessage = ref('')
const scanInputRef = ref()

async function handleScan() {
  const code = kanbanCode.value.trim()
  if (!code) {
    errorMessage.value = '请先输入看板码'
    scanResult.value = null
    return
  }

  scanning.value = true
  errorMessage.value = ''
  scanResult.value = null

  try {
    scanResult.value = await scanInbound(code)
    kanbanCode.value = ''
  } catch (error) {
    errorMessage.value =
      error.response?.data?.message ||
      error.message ||
      '扫码失败，请检查网络或后端服务'
  } finally {
    scanning.value = false
    nextTick(() => {
      scanInputRef.value?.focus()
    })
  }
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }

  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }

  return parsed.toLocaleString('zh-CN')
}

nextTick(() => {
  scanInputRef.value?.focus()
})
</script>

<style scoped>
.module-shell {
  min-height: 360px;
}

.scan-form {
  margin-bottom: 16px;
}

h2 {
  margin: 0;
}

.result-card {
  margin-top: 16px;
}
</style>

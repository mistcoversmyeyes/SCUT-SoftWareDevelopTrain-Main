<template>
  <section class="module-shell outbound-scan">
    <el-card>
      <template #header>
        <h2>出库扫码</h2>
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
        <el-form-item label="出库数量（留空默认全量）">
          <el-input-number v-model="scanQty" :min="1" :step="1" :precision="0" />
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

      <el-card v-if="kanbanPreview" class="result-card" shadow="never">
        <template #header>
          <span>看板信息</span>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="看板码">
            {{ kanbanPreview.kanbanCode }}
          </el-descriptions-item>
          <el-descriptions-item label="看板状态">
            {{ kanbanPreview.kanbanStatus }}
          </el-descriptions-item>
          <el-descriptions-item label="物料编码">
            {{ kanbanPreview.materialCode }}
          </el-descriptions-item>
          <el-descriptions-item label="物料名称">
            {{ kanbanPreview.materialName }}
          </el-descriptions-item>
          <el-descriptions-item label="库位">
            {{ kanbanPreview.locationName }}
          </el-descriptions-item>
          <el-descriptions-item label="看板数量">
            {{ kanbanPreview.boardQty }}
          </el-descriptions-item>
          <el-descriptions-item label="已拣数量">
            {{ kanbanPreview.pickedQty || 0 }}
          </el-descriptions-item>
          <el-descriptions-item label="剩余数量">
            <strong>{{ kanbanPreview.boardQty - (kanbanPreview.pickedQty || 0) }}</strong>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card v-if="scanResult" class="result-card" shadow="never">
        <template #header>
          <span>扫码成功</span>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="看板码">
            {{ scanResult.kanbanCode }}
          </el-descriptions-item>
          <el-descriptions-item label="出库单号">
            {{ scanResult.outboundNo }}
          </el-descriptions-item>
          <el-descriptions-item label="物料编码">
            {{ scanResult.materialCode }}
          </el-descriptions-item>
          <el-descriptions-item label="物料名称">
            {{ scanResult.materialName }}
          </el-descriptions-item>
          <el-descriptions-item label="发货数量">
            {{ scanResult.shippedQty }}
          </el-descriptions-item>
          <el-descriptions-item label="目标库位">
            {{ scanResult.locationName }}
          </el-descriptions-item>
          <el-descriptions-item label="出库单状态">
            {{ scanResult.orderStatus }}
          </el-descriptions-item>
          <el-descriptions-item label="扫码时间">
            {{ formatDateTime(scanResult.shippedAt) }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>
    </el-card>
  </section>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'
import { scanOutbound, lookupKanban } from '../../api/outbound'

const kanbanCode = ref('')
const scanQty = ref(undefined)
const scanning = ref(false)
const scanResult = ref(null)
const kanbanPreview = ref(null)
const errorMessage = ref('')
const scanInputRef = ref()

let lookupTimer = null

watch(kanbanCode, (code) => {
  clearTimeout(lookupTimer)
  kanbanPreview.value = null
  const trimmed = code.trim()
  if (!trimmed) return
  lookupTimer = setTimeout(async () => {
    try {
      kanbanPreview.value = await lookupKanban(trimmed)
    } catch {
      kanbanPreview.value = null
    }
  }, 400)
})

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
    scanResult.value = await scanOutbound({
      kanbanCode: code,
      qty: scanQty.value || undefined
    })
    kanbanCode.value = ''
    scanQty.value = undefined
    kanbanPreview.value = null
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
  if (!value) return '-'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleString('zh-CN')
}

nextTick(() => {
  scanInputRef.value?.focus()
})
</script>

<style scoped>
.module-shell { min-height: 360px; }
.scan-form { margin-bottom: 16px; }
h2 { margin: 0; }
.result-card { margin-top: 16px; }
</style>

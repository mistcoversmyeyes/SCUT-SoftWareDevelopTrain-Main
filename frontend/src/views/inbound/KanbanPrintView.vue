<template>
  <section class="module-shell">
    <el-card v-loading="loading" class="print-container">
      <template #header>
        <div class="toolbar">
          <h2>看板打印</h2>
          <el-button type="primary" size="default" :loading="loading" @click="printNow">打印</el-button>
        </div>
      </template>

      <el-alert v-if="errorMessage" type="error" :title="errorMessage" show-icon :closable="false" />

      <div v-if="kanbans.length" class="kanban-grid">
        <article v-for="kanban in kanbans" :key="kanban.kanbanCode" class="kanban-card">
          <h3>{{ kanban.kanbanCode }}</h3>
          <p><strong>状态：</strong>{{ kanban.status }}</p>
          <p><strong>入库单号：</strong>{{ kanban.inboundNo }}</p>
          <p>
            <strong>供应商：</strong>
            {{ kanban.supplierCode }} {{ kanban.supplierName }}
          </p>
          <p>
            <strong>物料：</strong>
            {{ kanban.materialCode }} {{ kanban.materialName }}
          </p>
          <p><strong>库位：</strong>{{ kanban.locationName }}</p>
          <p><strong>数量：</strong>{{ kanban.qty }}</p>
          <p><strong>打印时间：</strong>{{ formatDateTime(kanban.printedAt) }}</p>
          <div class="qrcode-box">
            <span>二维码位</span>
            {{ kanban.kanbanCode }}
          </div>
        </article>
      </div>
    </el-card>
  </section>
</template>

<script setup>
import { onBeforeMount, ref } from 'vue'
import { useRoute } from 'vue-router'
import { printKanbans } from '../../api/inbound'

const route = useRoute()
const loading = ref(false)
const errorMessage = ref('')
const kanbans = ref([])

function getInboundId() {
  const rawId = route.params.id
  return Number(rawId)
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

async function loadData() {
  const id = getInboundId()
  if (!id) {
    errorMessage.value = '入库单编号缺失'
    return
  }

  loading.value = true
  errorMessage.value = ''
  try {
    const result = await printKanbans(id)
    kanbans.value = result || []
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '加载看板打印数据失败'
  } finally {
    loading.value = false
  }
}

function printNow() {
  window.print()
}

onBeforeMount(() => {
  loadData()
})
</script>

<style scoped>
.module-shell {
  min-height: 360px;
}

h2 {
  margin: 0;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.kanban-grid {
  margin-top: 6px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.kanban-card {
  min-height: 220px;
  border: 1px solid #111827;
  padding: 10px;
  page-break-inside: avoid;
}

.kanban-card h3 {
  margin: 0 0 8px;
  font-size: 20px;
}

.kanban-card p {
  margin: 4px 0;
}

.qrcode-box {
  margin-top: 12px;
  height: 88px;
  border: 1px dashed #94a3b8;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: #334155;
}

@media print {
  .toolbar {
    display: none;
  }

  .kanban-grid {
    grid-template-columns: repeat(2, 90mm);
    gap: 8mm;
  }

  .kanban-card {
    border: 1px solid #000;
  }

  .qrcode-box {
    border-color: #000;
  }
}
</style>

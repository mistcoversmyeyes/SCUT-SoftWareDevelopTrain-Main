<template>
  <section class="pick-no-order-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <h2>不带单出库</h2>
        </div>
      </template>

      <el-alert
        title="不带单出库将直接扫描看板码出库。如有看板已被锁定，将强制抢锁出库并记录审计日志。"
        type="warning"
        :closable="false"
        show-icon
        class="alert-block"
      />

      <div class="action-center">
        <el-button type="primary" size="large" @click="goScan">
          扫码出库
        </el-button>
      </div>
    </el-card>
  </section>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

function goScan() {
  router.push({
    path: '/outbound/scan',
    query: { mode: 'no-order' }
  })
}
</script>

<style scoped>
.card-header h2 { margin: 0; }
.alert-block { margin-bottom: 20px; }
.action-center {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}
</style>

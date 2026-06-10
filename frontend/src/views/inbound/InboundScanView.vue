<template>
  <section class="module-shell">
    <el-card>
      <template #header>
        <h2>入库扫码</h2>
      </template>
      <p>该页为入库扫码壳页，WP-07 将实现扫码输入与结果提示。</p>
      <el-alert type="info" :closable="false" title="占位说明">
        当前仅用于路由对齐。
      </el-alert>
    </el-card>
  </section>
</template>

<script setup>
</script>

<style scoped>
.module-shell {
  min-height: 360px;
}

h2 {
  margin: 0;
}
</style>

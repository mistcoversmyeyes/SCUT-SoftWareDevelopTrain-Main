<template>
  <section class="module-shell">
    <el-card>
      <template #header>
        <h2>库存追溯</h2>
      </template>
      <p>该页为库存追溯壳页，WP-07 将接管流水与历史查询。</p>
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

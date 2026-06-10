<template>
  <section class="module-shell">
    <el-card>
      <template #header>
        <h2>入库订单</h2>
      </template>
      <p>该页为入库模块壳页，WP-06 将接管真实列表与表单交互。</p>
      <el-alert type="info" :closable="false" title="占位说明">
        当前仅提供路由落地，不实现业务逻辑。
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

<template>
  <section class="placeholder-page">
    <div class="page-heading">
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
    </div>

    <el-descriptions title="示例字段" :column="2" border>
      <el-descriptions-item v-for="field in fields" :key="field" :label="field">
        演示数据
      </el-descriptions-item>
    </el-descriptions>
  </section>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    required: true
  },
  description: {
    type: String,
    required: true
  },
  fields: {
    type: Array,
    required: true
  }
})
</script>

<style scoped>
.placeholder-page {
  min-height: 360px;
  padding: 28px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.page-heading {
  margin-bottom: 24px;
}

.page-heading h2 {
  margin: 0 0 8px;
  font-size: 28px;
}

.page-heading p {
  margin: 0;
  color: #64748b;
}
</style>

<template>
  <main class="login-page">
    <section class="login-visual">
      <p class="eyebrow">SCUT 软件开发实训</p>
      <h1>汽车物流 WMS</h1>
      <p class="summary">完成 Vue3 与 Spring Boot 的登录打通，进入后台查看菜单与标签页联动。</p>
    </section>

    <section class="login-panel">
      <h2>系统登录</h2>
      <p class="hint">演示账号：admin / 123456</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" size="large" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" size="large" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
        <el-button class="login-button" type="primary" size="large" :loading="loading" @click="submitLogin">
          登录
        </el-button>
      </el-form>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref()
const loading = ref(false)
const errorMessage = ref('')
const form = reactive({
  username: 'admin',
  password: '123456'
})

const rules = {
  username: [{ required: true, message: '用户名不能为空', trigger: 'blur' }],
  password: [{ required: true, message: '密码不能为空', trigger: 'blur' }]
}

async function submitLogin() {
  errorMessage.value = ''
  await formRef.value.validate()
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    await router.push(route.query.redirect || '/dashboard')
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '登录请求失败，请确认后端服务已启动'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  background: linear-gradient(135deg, #eef6ff 0%, #f8fafc 52%, #ecfdf5 100%);
}

.login-visual {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 72px;
}

.eyebrow {
  margin: 0 0 16px;
  color: #0f766e;
  font-weight: 700;
}

.login-visual h1 {
  margin: 0;
  color: #111827;
  font-size: 56px;
  letter-spacing: 0;
}

.summary {
  max-width: 560px;
  margin: 24px 0 0;
  color: #475569;
  font-size: 18px;
  line-height: 1.8;
}

.login-panel {
  align-self: center;
  margin-right: 72px;
  padding: 32px;
  background: #ffffff;
  border: 1px solid #dbe4ef;
  border-radius: 8px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.12);
}

.login-panel h2 {
  margin: 0;
  font-size: 26px;
}

.hint {
  margin: 8px 0 24px;
  color: #64748b;
}

.login-button {
  width: 100%;
  margin-top: 18px;
}
</style>

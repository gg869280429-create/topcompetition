<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-brand">
        <el-icon :size="40" color="#2f6bed"><Trophy /></el-icon>
        <h1 class="login-title">信息工程学院竞赛管理平台</h1>
        <p class="login-subtitle">Competition Management Platform</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        class="login-form"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          class="login-button"
          :loading="loading"
          @click="handleLogin"
        >
          登录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  await formRef.value.validate()
  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #2f6bed 0%, #1e4fb8 100%);
}

.login-card {
  width: 400px;
  background-color: var(--color-bg-card);
  border-radius: var(--radius-large);
  box-shadow: var(--shadow-popover);
  padding: var(--spacing-xl);
}

.login-brand {
  text-align: center;
  margin-bottom: var(--spacing-lg);
}

.login-title {
  font-size: 20px;
  font-weight: 600;
  margin: var(--spacing-md) 0 var(--spacing-xs);
  color: var(--color-text);
}

.login-subtitle {
  font-size: var(--font-size-small);
  color: var(--color-text-placeholder);
  margin: 0;
}

.login-form {
  margin-top: var(--spacing-lg);
}

.login-button {
  width: 100%;
}
</style>

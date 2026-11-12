<template>
  <main class="login-page">
    <section class="login-art" aria-label="登录页插画">
      <div class="art-grid" aria-hidden="true"></div>
      <LoginCharacters :is-typing="isTyping" :show-password="showPassword" :password-length="form.password.length" />
    </section>
    <section class="login-form-panel" aria-labelledby="login-heading">
      <div class="login-content">
        <header class="login-heading">
          <h1 id="login-heading">欢迎回来！</h1>
          <p>登录信息工程学院竞赛管理平台</p>
        </header>
        <form class="login-form" novalidate @submit.prevent="handleLogin">
          <div class="form-field">
            <label for="login-username">账号</label>
            <input id="login-username" v-model="form.username" name="username" type="text" placeholder="请输入账号" autocomplete="username" autocapitalize="off" :spellcheck="false" :aria-invalid="!!errors.username" :aria-describedby="errors.username ? 'username-error' : undefined" :disabled="loading" @focus="isTyping = true" @blur="isTyping = false" @input="errors.username = ''" />
            <p v-if="errors.username" id="username-error" class="field-error" role="alert">{{ errors.username }}</p>
          </div>
          <div class="form-field">
            <label for="login-password">密码</label>
            <div class="password-input">
              <input id="login-password" v-model="form.password" name="password" :type="showPassword ? 'text' : 'password'" placeholder="请输入密码" autocomplete="current-password" :aria-invalid="!!errors.password" :aria-describedby="errors.password ? 'password-error' : undefined" :disabled="loading" @input="errors.password = ''" />
              <button type="button" class="password-toggle" :aria-label="showPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showPassword" @click="showPassword = !showPassword">
                <el-icon :size="20"><Hide v-if="showPassword" /><View v-else /></el-icon>
              </button>
            </div>
            <p v-if="errors.password" id="password-error" class="field-error" role="alert">{{ errors.password }}</p>
          </div>
          <div class="login-help">
            <span>使用管理员分配的账号登录</span>
            <button type="button" @click="ElMessage.info('请联系管理员重置密码')">忘记密码？</button>
          </div>
          <button type="submit" class="login-submit" :disabled="loading" :aria-label="loading ? '登录中' : '登录'" :aria-busy="loading">
            <span class="submit-default"><span v-if="loading" class="login-spinner" aria-hidden="true"></span>{{ loading ? '登录中...' : '登录' }}</span>
            <span class="submit-hover" aria-hidden="true">登录<el-icon :size="18"><Right /></el-icon></span>
          </button>
        </form>
      </div>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Hide, View, Right } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import LoginCharacters from '@/components/LoginCharacters.vue'

const router = useRouter(), userStore = useUserStore()
const loading = ref(false), showPassword = ref(false), isTyping = ref(false)
const form = reactive({ username: '', password: '' })
const errors = reactive({ username: '', password: '' })

async function handleLogin() {
  if (loading.value) return
  errors.username = form.username.trim() ? '' : '请输入账号'
  errors.password = form.password ? '' : '请输入密码'
  if (errors.username || errors.password) return
  loading.value = true
  try {
    await userStore.login({ username: form.username.trim(), password: form.password })
    ElMessage.success('登录成功')
    await router.replace('/dashboard')
  } catch {
    // 登录失败的提示由现有请求拦截器显示。
  } finally { loading.value = false }
}
</script>

<style scoped>
.login-page { --login-primary:#404eb5; display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); min-height:100vh; min-height:100svh; background:#fff; color:#0f172a; }
.login-art { position:relative; display:flex; align-items:center; justify-content:center; overflow:hidden; min-width:0; padding:48px; background:linear-gradient(135deg,#9ca3af,#6b7280,#4b5563); }
.art-grid { position:absolute; inset:0; background-image:linear-gradient(rgba(255,255,255,.05) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.05) 1px,transparent 1px); background-size:20px 20px; }
.login-art::before, .login-art::after { position:absolute; content:''; border-radius:50%; filter:blur(64px); pointer-events:none; }
.login-art::before { width:256px; height:256px; top:25%; right:25%; background:rgba(156,163,175,.2); }
.login-art::after { width:384px; height:384px; bottom:25%; left:25%; background:rgba(209,213,219,.2); }
.login-art :deep(.characters-scene) { z-index:1; }
.login-form-panel { display:flex; align-items:center; justify-content:center; min-width:0; padding:64px 40px; }
.login-content { width:100%; max-width:420px; }
.login-heading { text-align:center; margin-bottom:40px; }
.login-heading h1 { margin:0 0 8px; font-size:30px; font-weight:700; letter-spacing:-.025em; line-height:1.4; }
.login-heading p { margin:0; color:#64748b; font-size:14px; line-height:1.7; }
.login-form { display:flex; flex-direction:column; gap:20px; }
.form-field label { display:block; margin-bottom:8px; font-size:14px; font-weight:500; }
.form-field input { display:block; width:100%; height:48px; border:1px solid #e2e8f0; border-radius:9999px; padding:0 16px; background:#fff; color:#0f172a; font:inherit; font-size:14px; transition:border-color 150ms,box-shadow 150ms; }
.form-field input::placeholder { color:#64748b; }
.form-field input:focus { outline:0; border-color:var(--login-primary); box-shadow:0 0 0 3px rgba(64,78,181,.12); }
.form-field input[aria-invalid=true] { border-color:#ff4d4f; }
.form-field input:disabled { background:#f8fafc; }
.password-input { position:relative; }
.password-input input { padding-right:48px; }
.password-toggle { position:absolute; top:0; right:8px; display:flex; align-items:center; justify-content:center; width:36px; height:48px; border:0; border-radius:9999px; padding:0; background:transparent; color:#64748b; cursor:pointer; }
.password-toggle:hover { color:#0f172a; }
.field-error { margin:8px 0 0; color:#d9363e; font-size:13px; }
.login-help { display:flex; align-items:center; justify-content:space-between; gap:12px; font-size:13px; line-height:1.7; }
.login-help span { color:#64748b; }
.login-help button { flex-shrink:0; padding:0; border:0; background:transparent; color:#1677ff; font:inherit; cursor:pointer; }
.login-help button:hover { text-decoration:underline; }
.login-submit { position:relative; width:100%; height:48px; overflow:hidden; border:1px solid #e2e8f0; border-radius:9999px; padding:0 24px; background:#fff; color:#0f172a; font:inherit; font-weight:600; cursor:pointer; }
.submit-default { display:inline-flex; align-items:center; gap:8px; transition:transform 300ms,opacity 300ms; }
.submit-hover { position:absolute; inset:0; display:flex; align-items:center; justify-content:center; gap:8px; border-radius:inherit; background:var(--login-primary); color:#fff; opacity:0; transition:opacity 300ms; }
.login-submit:not(:disabled):hover .submit-default, .login-submit:not(:disabled):focus-visible .submit-default { transform:translateX(48px); opacity:0; }
.login-submit:not(:disabled):hover .submit-hover, .login-submit:not(:disabled):focus-visible .submit-hover { opacity:1; }
.login-submit:disabled { cursor:wait; background:#f8fafc; }
.password-toggle:focus-visible, .login-help button:focus-visible, .login-submit:focus-visible { outline:2px solid var(--login-primary); outline-offset:3px; }
.login-spinner { width:15px; height:15px; border:2px solid #d9ddef; border-top-color:var(--login-primary); border-radius:50%; animation:login-spin 700ms linear infinite; }
@keyframes login-spin { to { transform:rotate(360deg); } }
@media (max-width:1023px) { .login-page { grid-template-columns:1fr; } .login-art { display:none; } .login-form-panel { padding:40px 32px; } }
@media (max-width:480px) { .login-form-panel { padding:32px 24px; } .form-field input { font-size:16px; } .login-help { font-size:12px; } }
@media (prefers-reduced-motion:reduce) { .submit-default, .submit-hover, .form-field input { transition:none; } .login-spinner { animation:none; } }
</style>

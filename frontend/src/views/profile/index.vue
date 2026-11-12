<template>
  <div>
    <PageHeader title="个人信息管理" />
    <div class="profile-grid">
      <PageContainer v-loading="loading">
        <h2>基本信息</h2>
        <el-form ref="profileForm" :model="profile" :rules="profileRules" label-position="top" @submit.prevent="saveProfile">
          <el-form-item label="账号"><el-input :model-value="profile.username" disabled /></el-form-item>
          <el-form-item label="所属专业">
            <el-input :model-value="profile.majorName || '未配置，请联系管理员维护'" disabled />
          </el-form-item>
          <p class="form-hint">账号和所属专业由管理员维护。</p>
          <el-form-item label="姓名" prop="realName"><el-input v-model="profile.realName" maxlength="64" /></el-form-item>
          <el-form-item label="手机号" prop="phone"><el-input v-model="profile.phone" maxlength="32" type="tel" /></el-form-item>
          <el-form-item label="邮箱" prop="email"><el-input v-model="profile.email" maxlength="128" /></el-form-item>
          <el-button type="primary" native-type="submit" :loading="saving" :disabled="loading || !profile.id">保存个人信息</el-button>
        </el-form>
      </PageContainer>
      <PageContainer>
        <h2>修改密码</h2>
        <p class="form-hint">新密码至少8个字符，且UTF-8编码不超过72字节。修改成功后需要重新登录。</p>
        <el-form ref="passwordForm" :model="password" :rules="passwordRules" label-position="top" @submit.prevent="savePassword">
          <el-form-item label="原密码" prop="currentPassword">
            <el-input v-model="password.currentPassword" type="password" show-password autocomplete="current-password" maxlength="72" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="password.newPassword" type="password" show-password autocomplete="new-password" maxlength="72" />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input v-model="password.confirmPassword" type="password" show-password autocomplete="new-password" maxlength="72" />
          </el-form-item>
          <el-button type="primary" native-type="submit" :loading="changingPassword">修改密码并重新登录</el-button>
        </el-form>
      </PageContainer>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PageContainer from '@/components/PageContainer.vue'
import { useUserStore } from '@/store/user'
import { getStudentProfile, updateStudentProfile, changeStudentPassword } from '@/api/profile'

const router = useRouter(), userStore = useUserStore()
const profileForm = ref(), passwordForm = ref()
const loading = ref(false), saving = ref(false), changingPassword = ref(false)
const profile = reactive({ id: null, username: '', majorName: '', realName: '', phone: '', email: '' })
const password = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const profileRules = {
  realName: [{ required: true, whitespace: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ pattern: /^[0-9+()\-\s]{0,32}$/, message: '联系电话格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}
const passwordRules = {
  currentPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ validator: (_, value, done) => {
    if (!value || value.length < 8) return done(new Error('新密码至少8个字符'))
    if (new TextEncoder().encode(value).length > 72) return done(new Error('新密码的UTF-8编码不能超过72字节'))
    if (value === password.currentPassword) return done(new Error('新密码不能与原密码相同'))
    done()
  }, trigger: 'blur' }],
  confirmPassword: [{ validator: (_, value, done) => {
    done(!value || value !== password.newPassword ? new Error('两次输入的新密码不一致') : undefined)
  }, trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try { Object.assign(profile, (await getStudentProfile()).data) }
  finally { loading.value = false }
}
async function saveProfile() {
  if (saving.value || !await profileForm.value.validate().catch(() => false)) return
  saving.value = true
  try {
    const response = await updateStudentProfile({ realName: profile.realName.trim(), phone: profile.phone?.trim() || '', email: profile.email?.trim() || '' })
    Object.assign(profile, response.data)
    await userStore.fetchUserInfo()
    ElMessage.success('个人信息已保存')
  } finally { saving.value = false }
}
async function savePassword() {
  if (changingPassword.value || !await passwordForm.value.validate().catch(() => false)) return
  changingPassword.value = true
  try {
    await changeStudentPassword({ ...password })
    userStore.clearAuth()
    Object.assign(password, { currentPassword: '', newPassword: '', confirmPassword: '' })
    ElMessage.success('密码已修改，请重新登录')
    await router.replace('/login')
  } finally { changingPassword.value = false }
}
onMounted(load)
</script>

<style scoped>
.profile-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:20px; align-items:start; }
h2 { margin:0 0 20px; font-size:16px; }
.form-hint { color:var(--color-text-secondary); font-size:13px; line-height:1.7; margin:0 0 20px; }
@media (max-width:1000px) { .profile-grid { grid-template-columns:1fr; } }
</style>

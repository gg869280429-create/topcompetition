<template>
  <div class="profile-page">
    <section v-loading="loading" class="settings-panel" aria-label="个人资料设置">
      <header class="profile-summary">
        <el-avatar :size="64" class="identity-avatar">{{ (profile.realName || profile.username || '学').charAt(0) }}</el-avatar>
        <div class="identity-copy">
          <h1>{{ profile.realName || '我的资料' }}</h1>
          <p>{{ userStore.role === 'TEACHER' ? '教师' : '学生' }}<span v-if="profile.majorName" class="identity-major">{{ profile.majorName }}</span></p>
        </div>
      </header>
      <div class="settings-section">
        <div class="section-heading"><h2>基本信息</h2><p>完善个人资料，方便参赛联系</p></div>
        <dl class="settings-list">
          <div v-for="field in fields" :key="field.key" class="setting-row" :data-field="field.key">
            <dt>{{ field.label }}</dt>
            <dd :class="{ 'empty-value': !profile[field.key] }">{{ profile[field.key] || (field.key === 'majorName' ? '未配置专业' : '未填写') }}</dd>
            <el-button v-if="field.editable" link type="primary" :aria-label="`修改${field.label}`" :disabled="loading || !profile.id" @click="openEdit(field)">修改</el-button>
            <span v-else class="readonly-note">管理员维护</span>
          </div>
        </dl>
      </div>
      <div class="settings-section security-section">
        <div class="section-heading"><h2>账号安全</h2></div>
        <div class="setting-row password-row">
          <div class="security-icon"><el-icon :size="22"><Lock /></el-icon></div>
          <div class="password-copy"><h3>登录密码</h3><p>定期更新密码，保护账号安全</p></div>
          <el-button link type="primary" :disabled="loading || !profile.id" @click="openPassword">修改密码</el-button>
        </div>
      </div>
    </section>

    <el-dialog v-model="editVisible" :title="`修改${editField?.label || '资料'}`" width="min(460px, 92vw)" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" @closed="clearEdit">
      <el-form ref="editForm" :model="editModel" :rules="editRules" label-position="top" @submit.prevent="saveProfile">
        <el-form-item :label="editField?.label" prop="value">
          <el-input v-model="editModel.value" :maxlength="editField?.maxLength" :type="editField?.key === 'phone' ? 'tel' : 'text'" :placeholder="`请输入${editField?.label || ''}`" />
        </el-form-item>
        <div class="dialog-actions"><el-button :disabled="saving" @click="editVisible = false">取消</el-button><el-button type="primary" native-type="submit" :loading="saving">保存</el-button></div>
      </el-form>
    </el-dialog>

    <el-dialog v-model="passwordVisible" title="修改密码" width="min(460px, 92vw)" :close-on-click-modal="!changingPassword" :close-on-press-escape="!changingPassword" :show-close="!changingPassword" @closed="clearPassword">
      <p class="password-hint">新密码至少8个字符，修改成功后需要重新登录。</p>
      <el-form ref="passwordForm" :model="password" :rules="passwordRules" label-position="top" @submit.prevent="savePassword">
        <input class="password-username" type="text" :value="profile.username" autocomplete="username" tabindex="-1" aria-hidden="true" />
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="password.newPassword" type="password" show-password autocomplete="new-password" maxlength="72" placeholder="请输入新密码" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="password.confirmPassword" type="password" show-password autocomplete="new-password" maxlength="72" placeholder="请再次输入新密码" />
        </el-form-item>
        <div class="dialog-actions"><el-button :disabled="changingPassword" @click="passwordVisible = false">取消</el-button><el-button type="primary" native-type="submit" :loading="changingPassword">确认修改</el-button></div>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getProfile, updateProfile, changePassword } from '@/api/profile'

const router = useRouter(), userStore = useUserStore()
const editForm = ref(), passwordForm = ref(), editField = ref(null)
const loading = ref(false), saving = ref(false), changingPassword = ref(false)
const editVisible = ref(false), passwordVisible = ref(false)
const profile = reactive({ id: null, username: '', majorName: '', realName: '', phone: '', email: '' })
const editModel = reactive({ value: '' })
const password = reactive({ newPassword: '', confirmPassword: '' })
const fields = [
  { key: 'username', label: '账号' },
  { key: 'realName', label: '姓名', editable: true, maxLength: 64 },
  { key: 'majorName', label: '所属专业' },
  { key: 'phone', label: '手机号', editable: true, maxLength: 32 },
  { key: 'email', label: '邮箱', editable: true, maxLength: 128 }
]
const fieldRules = {
  realName: [{ required: true, whitespace: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ pattern: /^[0-9+()\-\s]{0,32}$/, message: '联系电话格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}
const editRules = computed(() => ({ value: fieldRules[editField.value?.key] || [] }))
const passwordRules = {
  newPassword: [{ validator: (_, value, done) => {
    if (!value || !value.trim() || value.length < 8) return done(new Error('新密码至少8个字符'))
    if (new TextEncoder().encode(value).length > 72) return done(new Error('新密码过长，请缩短后重试'))
    done()
  }, trigger: 'blur' }],
  confirmPassword: [{ validator: (_, value, done) => {
    done(!value || value !== password.newPassword ? new Error('两次输入的新密码不一致') : undefined)
  }, trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try { Object.assign(profile, (await getProfile()).data) }
  finally { loading.value = false }
}
async function openEdit(field) {
  editField.value = field
  editModel.value = profile[field.key] || ''
  editVisible.value = true
  await nextTick()
  editForm.value?.clearValidate()
}
function clearEdit() {
  editModel.value = ''
  editForm.value?.clearValidate()
}
async function saveProfile() {
  if (saving.value || !await editForm.value.validate().catch(() => false)) return
  saving.value = true
  try {
    const data = { realName: profile.realName, phone: profile.phone || '', email: profile.email || '' }
    data[editField.value.key] = editModel.value.trim()
    Object.assign(profile, (await updateProfile(data)).data)
    await userStore.fetchUserInfo()
    editVisible.value = false
    ElMessage.success('个人信息已保存')
  } finally { saving.value = false }
}
function clearPassword() {
  Object.assign(password, { newPassword: '', confirmPassword: '' })
  passwordForm.value?.clearValidate()
}
function openPassword() {
  clearPassword()
  passwordVisible.value = true
}
async function savePassword() {
  if (changingPassword.value || !await passwordForm.value.validate().catch(() => false)) return
  changingPassword.value = true
  try {
    await changePassword({ ...password })
    userStore.clearAuth()
    clearPassword()
    ElMessage.success('密码已修改，请重新登录')
    await router.replace('/login')
  } finally { changingPassword.value = false }
}
onMounted(load)
</script>

<style scoped>
.profile-page { max-width:1080px; margin:0 auto; }
.settings-panel { overflow:hidden; background:var(--color-bg-card); border:1px solid var(--color-border-light); border-radius:8px; }
.profile-summary { display:flex; align-items:center; gap:20px; padding:30px 36px; background:#f6f9fd; border-bottom:1px solid var(--color-border-light); }
.identity-avatar { flex-shrink:0; color:#3978e6; background:#e4edfc; font-size:26px; font-weight:600; }
.identity-copy { min-width:0; }
.identity-copy h1 { margin:0 0 10px; font-size:22px; font-weight:600; overflow-wrap:anywhere; }
.identity-copy p { margin:0; color:var(--color-text-secondary); font-size:14px; line-height:1.6; }
.identity-major { padding-left:16px; margin-left:16px; border-left:1px solid #d9e1eb; }
.settings-section { padding:26px 36px 12px; }
.section-heading { display:flex; align-items:baseline; flex-wrap:wrap; gap:12px; margin-bottom:8px; }
.section-heading h2 { margin:0; font-size:16px; font-weight:600; }
.section-heading p { margin:0; color:var(--color-text-secondary); font-size:13px; }
.settings-list { margin:0; }
.setting-row { display:flex; align-items:center; gap:20px; min-height:72px; border-bottom:1px solid var(--color-border-light); }
.setting-row dt { flex:0 0 104px; color:var(--color-text-secondary); font-size:14px; }
.setting-row dd { flex:1; min-width:0; margin:0; font-size:14px; line-height:1.6; overflow-wrap:anywhere; }
.setting-row dd.empty-value { color:#a8abb2; }
.readonly-note { color:var(--color-text-secondary); font-size:12px; }
.setting-row .el-button, .readonly-note { flex-shrink:0; }
.settings-list .setting-row:last-child { border-bottom:0; }
.security-section { margin-top:8px; padding-top:26px; padding-bottom:28px; border-top:1px solid var(--color-border-light); }
.password-row { border-bottom:0; gap:16px; padding-top:10px; }
.security-icon { display:flex; align-items:center; justify-content:center; width:44px; height:44px; flex-shrink:0; border-radius:50%; color:#3978e6; background:#f0f5ff; }
.password-copy { flex:1; min-width:0; }
.password-copy h3 { margin:0 0 6px; font-size:14px; font-weight:500; }
.password-copy p { margin:0; color:var(--color-text-secondary); font-size:13px; line-height:1.6; }
.dialog-actions { display:flex; justify-content:flex-end; gap:12px; margin-top:26px; }
.dialog-actions .el-button + .el-button { margin-left:0; }
.password-hint { margin:0 0 24px; color:var(--color-text-secondary); font-size:13px; line-height:1.7; }
.password-username { position:absolute; width:1px; height:1px; opacity:0; pointer-events:none; }
@media (max-width:760px) {
  .profile-summary { padding:24px 20px; gap:14px; }
  .settings-section { padding-left:20px; padding-right:20px; }
  .setting-row { display:grid; grid-template-columns:72px minmax(0,1fr); gap:10px 12px; min-height:74px; padding:14px 0; }
  .setting-row dd { grid-column:2; }
  .setting-row > .el-button, .readonly-note { grid-column:2; justify-self:start; }
  .password-row { display:flex; }
  .password-row > .el-button { margin-left:0; }
  .identity-major { display:block; padding-left:0; margin-left:0; border-left:0; }
}
</style>

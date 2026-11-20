<template>
  <span class="avatar-upload">
    <input ref="input" class="avatar-input" type="file" accept="image/png,image/jpeg" aria-label="选择头像图片" @change="upload" />
    <el-button v-if="showTrigger" link type="primary" :loading="uploading" @click="choose">更换头像</el-button>
    <span v-if="showTrigger" class="avatar-tip">PNG / JPEG，最大2MB</span>
  </span>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadAvatar } from '@/api/profile'
import { useUserStore } from '@/store/user'

defineProps({ showTrigger: { type: Boolean, default: true } })
const input = ref(), uploading = ref(false), userStore = useUserStore()
function choose() { if (!uploading.value) input.value?.click() }
async function upload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || uploading.value) return
  if (!['image/png', 'image/jpeg'].includes(file.type) || !file.size || file.size > 2 * 1024 * 1024) {
    ElMessage.warning('请选择不超过2MB的PNG或JPEG图片')
    return
  }
  uploading.value = true
  try {
    const { data } = await uploadAvatar(file)
    userStore.userInfo = { ...userStore.userInfo, avatarUrl: data.avatarUrl }
    localStorage.setItem('userInfo', JSON.stringify(userStore.userInfo))
    ElMessage.success('头像已更新')
  } catch { /* 请求错误由统一拦截器提示，保留当前头像。 */ }
  finally { uploading.value = false }
}
defineExpose({ choose })
</script>

<style scoped>
.avatar-upload { display:inline-flex; align-items:center; flex-wrap:wrap; gap:10px; }
.avatar-input { display:none; }
.avatar-tip { color:var(--color-text-secondary); font-size:12px; }
</style>

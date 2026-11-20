<template>
  <el-dialog v-model="visible" :title="initialValue.id ? '编辑新闻' : '新增新闻'" width="min(640px, 94vw)"
    :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @closed="clearPreview">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" :disabled="busy" @submit.prevent>
      <el-form-item label="标题" prop="title"><el-input v-model="form.title" maxlength="128" show-word-limit /></el-form-item>
      <el-form-item label="分类" prop="category">
        <el-select v-model="form.category" style="width:100%" :disabled="lockCategory" @change="value => { if (value !== 'NEWS') form.carouselEnabled = false }">
          <el-option v-for="(label, value) in CONTENT_CATEGORY" :key="value" :label="label" :value="value" />
        </el-select>
      </el-form-item>
      <el-form-item label="比赛官网"><el-input v-model="form.officialUrl" maxlength="2048" placeholder="https://对应大赛官网（新闻发布必填）" /></el-form-item>
      <el-form-item label="封面图片">
        <div class="cover-editor">
          <img v-if="coverPreview" :src="coverPreview" alt="新闻封面预览" class="cover-preview" />
          <div class="cover-actions">
            <el-upload :http-request="uploadCover" :show-file-list="false" accept="image/png,image/jpeg,image/webp" :disabled="busy">
              <el-button :loading="coverUploading">{{ form.coverFileId ? '更换封面图片' : '上传封面图片' }}</el-button>
            </el-upload>
            <el-button v-if="form.coverFileId" link type="danger" :disabled="busy" @click="removeCover">移除</el-button>
          </div>
          <span v-if="form.coverFileId" class="cover-hint">已关联封面 #{{ form.coverFileId }}</span>
        </div>
      </el-form-item>
      <el-form-item label="首页轮播"><el-switch v-model="form.carouselEnabled" :disabled="form.category !== 'NEWS'" /><span class="cover-hint">开启需封面图片和官网地址</span></el-form-item>
      <el-form-item label="轮播排序"><el-input-number v-model="form.sortOrder" :min="0" :max="999999" /></el-form-item>
      <el-form-item label="发布开始"><el-date-picker v-model="form.publishStartTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="不设置则立即发布" /></el-form-item>
      <el-form-item label="到期下线"><el-date-picker v-model="form.publishEndTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="不设置则长期有效" /></el-form-item>
      <template v-if="form.carouselEnabled">
        <el-form-item label="轮播开始"><el-date-picker v-model="form.carouselStartTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="不限制开始" /></el-form-item>
        <el-form-item label="轮播结束"><el-date-picker v-model="form.carouselEndTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="不限制结束" /></el-form-item>
      </template>
      <el-form-item label="正文"><el-input v-model="form.content" type="textarea" :rows="8" resize="none" placeholder="请输入正文内容" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="busy" @click="visible=false">取消</el-button>
      <el-button v-if="!publishAction" :type="canPublish ? 'default' : 'primary'" :loading="submitting" :disabled="busy" @click="save(false)">{{ canPublish ? '保存草稿' : '保存修改' }}</el-button>
      <el-button v-if="canPublish || publishAction" type="primary" :loading="submitting" :disabled="busy" @click="save(true)">{{ publishAction ? '发布竞赛和新闻' : '发布新闻' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { CONTENT_CATEGORY } from '@/constants/dict'
import { createContent, updateContent, updateContentStatus, uploadContentCover, getContentCover } from '@/api/content'

const props = defineProps({
  modelValue: Boolean,
  initialValue: { type: Object, default: () => ({}) },
  defaultCarouselWeeks: { type: Number, default: 0 },
  lockCategory: Boolean,
  publishAction: { type: Function, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const formRef = ref(), submitting = ref(false), coverUploading = ref(false), coverPreview = ref('')
const busy = computed(() => submitting.value || coverUploading.value)
const canPublish = computed(() => !props.initialValue.status || props.initialValue.status === 'DRAFT')
const defaultForm = () => ({ id:null, title:'', category:'NEWS', content:'', officialUrl:'', coverFileId:null, carouselEnabled:false, sortOrder:0, publishStartTime:null, publishEndTime:null, carouselStartTime:null, carouselEndTime:null })
const form = reactive(defaultForm())
const rules = { title:[{required:true, whitespace:true, message:'请输入标题', trigger:'blur'}], category:[{required:true, message:'请选择分类', trigger:'change'}] }
let version = 0
function clearPreview() {
  version++
  if (coverPreview.value) URL.revokeObjectURL(coverPreview.value)
  coverPreview.value = ''
}
function formatTime(value) {
  const pad = number => String(number).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth()+1)}-${pad(value.getDate())} ${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
}
watch(() => props.modelValue, async open => {
  if (!open) return
  clearPreview()
  const currentVersion = version
  const defaults = defaultForm()
  for (const key of Object.keys(defaults)) defaults[key] = props.initialValue[key] ?? defaults[key]
  Object.assign(form, defaults, { carouselEnabled: !!defaults.carouselEnabled })
  if (!form.id && props.defaultCarouselWeeks) {
    const start = new Date(), end = new Date(start)
    end.setDate(end.getDate() + props.defaultCarouselWeeks * 7)
    Object.assign(form, { carouselEnabled:true, carouselStartTime:formatTime(start), carouselEndTime:formatTime(end) })
  }
  await nextTick()
  formRef.value?.clearValidate()
  if (form.id && form.coverFileId) {
    try {
      const blob = await getContentCover(form.id)
      if (currentVersion === version && visible.value) coverPreview.value = URL.createObjectURL(blob)
    } catch { /* 请求错误由统一接口提示，仍允许更换丢失的封面。 */ }
  }
})
function safeUrl(value) {
  try { const url=new URL(value); return ['http:','https:'].includes(url.protocol) && !!url.hostname && !url.username && !url.password } catch { return false }
}
async function uploadCover(options) {
  coverUploading.value = true
  const currentVersion = version
  try {
    const response = await uploadContentCover(options.file)
    if (currentVersion === version && visible.value) {
      clearPreview()
      coverPreview.value = URL.createObjectURL(options.file)
      form.coverFileId = response.data.id
    }
    options.onSuccess?.(response)
  } catch (error) { options.onError?.(error) }
  finally { coverUploading.value = false }
}
function removeCover() {
  clearPreview()
  form.coverFileId = null
}
async function save(publish) {
  if (busy.value || !await formRef.value.validate().catch(() => false)) return
  form.officialUrl = form.officialUrl.trim()
  if ((form.officialUrl || (publish && form.category === 'NEWS')) && !safeUrl(form.officialUrl)) { ElMessage.warning('请输入有效的HTTP/HTTPS官网地址'); return }
  if (form.carouselEnabled && (!form.coverFileId || !form.officialUrl)) { ElMessage.warning('轮播需要封面和官网地址'); return }
  for (const [start, end, label] of [[form.publishStartTime,form.publishEndTime,'新闻'],[form.carouselStartTime,form.carouselEndTime,'轮播']]) {
    if (start && end && start >= end) { ElMessage.warning(`${label}开始时间必须早于结束时间`); return }
  }
  submitting.value = true
  try {
    const { id, ...payload } = form
    if (id) await updateContent(id, payload)
    else form.id = (await createContent(payload)).data
    if (publish) {
      if (props.publishAction) await props.publishAction(form.id)
      else await updateContentStatus(form.id, 'PUBLISHED')
    }
    const scheduled = form.publishStartTime && new Date(form.publishStartTime.replace(' ','T')) > new Date()
    ElMessage.success(props.publishAction ? (scheduled ? '竞赛已发布，新闻已设置定时发布' : '竞赛和新闻已发布') : publish ? (scheduled ? '已设置定时发布' : '新闻已发布') : '保存成功')
    visible.value = false
    emit('saved', { id:form.id, published:publish })
  } catch {
    if (publish && form.id && !props.initialValue.id) ElMessage.warning('新闻草稿已保存，发布未完成，可修改后重试或在新闻管理中继续处理')
  } finally { submitting.value = false }
}
onUnmounted(clearPreview)
</script>

<style scoped>
.cover-editor { width:100%; }
.cover-preview { display:block; width:100%; max-height:180px; margin-bottom:10px; object-fit:contain; background:var(--el-fill-color-light); border-radius:6px; }
.cover-actions { display:flex; align-items:center; gap:12px; }
.cover-hint { margin-left:8px; color:var(--el-text-color-secondary); font-size:12px; }
</style>

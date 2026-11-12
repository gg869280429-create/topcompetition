<template>
  <CompetitionPreview v-if="!isAdmin" />
  <div v-else>
    <PageHeader :title="isAdmin ? '新闻管理' : '新闻资讯'">
      <template #actions>
        <el-button v-if="isAdmin" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新增新闻
        </el-button>
      </template>
    </PageHeader>

    <PageContainer>
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item label="分类">
          <el-select v-model="query.category" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(label, value) in CONTENT_CATEGORY" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isAdmin" label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(label, value) in CONTENT_STATUS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="标题关键词" clearable style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="分类" width="90">
          <template #default="{ row }">
            <el-tag :type="categoryTagType(row.category)">{{ CONTENT_CATEGORY[row.category] || row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">{{ CONTENT_STATUS[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="authorName" label="作者" width="110" show-overflow-tooltip />
        <el-table-column prop="viewCount" label="浏览" width="80" />
        <el-table-column prop="publishTime" label="发布时间" width="170" />
        <el-table-column label="操作" :width="isAdmin ? 220 : 80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <template v-if="isAdmin">
              <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="row.status === 'DRAFT'" link type="success" @click="changeStatus(row, 'PUBLISHED')">发布</el-button>
              <el-button v-else link type="warning" @click="changeStatus(row, 'DRAFT')">下线</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </PageContainer>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑内容' : '新增新闻'"
      width="640px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" style="width: 100%" @change="value => { if(value !== 'NEWS') form.carouselEnabled=false }">
            <el-option v-for="(label, value) in CONTENT_CATEGORY" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="比赛官网">
          <el-input v-model="form.officialUrl" maxlength="2048" placeholder="https://对应大赛官网（新闻发布必填）" />
        </el-form-item>
        <el-form-item label="封面图片">
          <el-upload :http-request="uploadCover" :show-file-list="false" accept="image/png,image/jpeg,image/webp" :disabled="coverUploading">
            <el-button :loading="coverUploading">上传封面图片</el-button>
          </el-upload>
          <span v-if="form.coverFileId">已关联封面 #{{ form.coverFileId }}</span>
          <el-button v-if="form.coverFileId" link type="danger" @click="form.coverFileId=null">移除</el-button>
        </el-form-item>
        <el-form-item label="首页轮播"><el-switch v-model="form.carouselEnabled" :disabled="form.category !== 'NEWS'" /><span> 开启需封面图片和官网地址</span></el-form-item>
        <el-form-item label="轮播排序"><el-input-number v-model="form.sortOrder" :min="0" :max="999999" /></el-form-item>
        <el-form-item label="正文">
          <el-input v-model="form.content" type="textarea" :rows="8" placeholder="请输入正文内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="detail.title" width="640px">
      <div class="detail-meta">
        <el-tag :type="categoryTagType(detail.category)" size="small">{{ CONTENT_CATEGORY[detail.category] || detail.category }}</el-tag>
        <span class="meta-text">作者：{{ detail.authorName || '-' }}</span>
        <span class="meta-text">发布时间：{{ detail.publishTime || '-' }}</span>
        <span class="meta-text">浏览：{{ detail.viewCount || 0 }}</span>
      </div>
      <el-divider />
      <img v-if="detailCover" :src="detailCover" alt="新闻封面" class="news-cover" />
      <p v-if="safeOfficialUrl(detail.officialUrl)"><a :href="detail.officialUrl" target="_blank" rel="noopener noreferrer">比赛官网 ↗</a></p>
      <el-alert v-else-if="detail.category === 'NEWS'" title="历史新闻尚未补录官网地址" type="info" :closable="false" />
      <div class="detail-content">{{ detail.content || '（暂无正文）' }}</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted }  from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PageContainer from '@/components/PageContainer.vue'
import CompetitionPreview from '@/components/CompetitionPreview.vue'
import { useUserStore } from '@/store/user'
import {
  pageContent,
  getContent,
  createContent,
  updateContent,
  deleteContent,
  getContentCover, uploadContentCover, updateContentStatus
} from '@/api/content'
import { CONTENT_CATEGORY, CONTENT_STATUS } from '@/constants/dict'

const userStore = useUserStore()
const isAdmin = computed(() => userStore.role === 'ADMIN' || userStore.role === 'MAJOR_ADMIN')

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, category: '', status: '', keyword: '' })

const dialogVisible = ref(false)
const detailVisible = ref(false)
const formRef = ref()
const detail = ref({})
const detailCover = ref('')
const coverUploading = ref(false)
const defaultForm = () => ({ id: null, title: '', category: 'NEWS', content: '', officialUrl: '', coverFileId: null, carouselEnabled: false, sortOrder: 0 })
const form = reactive(defaultForm())
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }]
}

function categoryTagType(category) {
  if (category === 'NOTICE') return 'warning'
  if (category === 'ANNOUNCEMENT') return 'danger'
  return 'primary'
}

async function fetchList() {
  loading.value = true
  try {
    const res = await pageContent(query)
    list.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.current = 1
  fetchList()
}

function handleReset() {
  query.current = 1
  query.category = ''
  query.status = ''
  query.keyword = ''
  fetchList()
}

function resetForm() {
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  resetForm()
  Object.assign(form, { ...row, carouselEnabled: !!row.carouselEnabled })
  dialogVisible.value = true
}

function handleSubmit() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (form.officialUrl && !safeOfficialUrl(form.officialUrl)) { ElMessage.warning('请输入有效的HTTP/HTTPS官网地址'); return }
      if (form.carouselEnabled && (!form.coverFileId || !form.officialUrl)) { ElMessage.warning('轮播需要封面和官网地址'); return }
      const payload = { ...form }
      if (form.id) {
        await updateContent(form.id, payload)
      } else {
        await createContent(payload)
      }
      ElMessage.success('保存成功')
      dialogVisible.value = false
      fetchList()
    } finally {
      submitting.value = false
    }
  })
}

async function openDetail(row) {
  const res = await getContent(row.id)
  detail.value = res.data
  detailVisible.value = true
  if(detailCover.value) URL.revokeObjectURL(detailCover.value)
  detailCover.value = detail.value.coverFileId ? URL.createObjectURL(await getContentCover(detail.value.id)) : ''
  fetchList()
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除内容「${row.title}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteContent(row.id)
      ElMessage.success('删除成功')
      fetchList()
    })
    .catch(() => {})
}

function changeStatus(row, status) {
  if (status === 'PUBLISHED' && row.category === 'NEWS' && !safeOfficialUrl(row.officialUrl)) { ElMessage.warning('请先编辑补录有效的比赛官网地址'); return }
  const action = status === 'PUBLISHED' ? '发布' : '下线'
  ElMessageBox.confirm(`确定${action}内容「${row.title}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await updateContentStatus(row.id, status)
      ElMessage.success(`${action}成功`)
      fetchList()
    })
    .catch(() => {})
}

function safeOfficialUrl(value) {
  try { const url=new URL(value); return ['http:','https:'].includes(url.protocol) && !!url.hostname && !url.username && !url.password } catch { return false }
}
async function uploadCover(options) {
  coverUploading.value = true
  try { const response = await uploadContentCover(options.file); form.coverFileId = response.data.id; options.onSuccess?.(response) }
  catch (error) { options.onError?.(error) }
  finally { coverUploading.value = false }
}
onUnmounted(() => { if(detailCover.value)URL.revokeObjectURL(detailCover.value) })
onMounted(() => { if (isAdmin.value) fetchList() })
</script>

<style scoped>
.news-cover { display:block; max-width:100%; max-height:320px; object-fit:contain; margin:0 auto 16px; }
.search-form {
  margin-bottom: var(--spacing-sm);
}

.search-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--spacing-md);
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  flex-wrap: wrap;
}

.meta-text {
  color: var(--color-text-secondary);
  font-size: var(--font-size-small);
}

.detail-content {
  white-space: pre-wrap;
  line-height: 1.8;
  color: var(--color-text);
}
</style>

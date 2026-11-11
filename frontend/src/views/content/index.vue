<template>
  <div>
    <PageHeader title="内容管理">
      <template #actions>
        <el-button v-if="isAdmin" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新增内容
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
      :title="form.id ? '编辑内容' : '新增内容'"
      width="640px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="(label, value) in CONTENT_CATEGORY" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
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
      <div class="detail-content">{{ detail.content || '（暂无正文）' }}</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PageContainer from '@/components/PageContainer.vue'
import { useUserStore } from '@/store/user'
import {
  pageContent,
  getContent,
  createContent,
  updateContent,
  deleteContent,
  updateContentStatus
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
const defaultForm = () => ({ id: null, title: '', category: 'NOTICE', content: '' })
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
  Object.assign(form, { id: row.id, title: row.title, category: row.category, content: row.content })
  dialogVisible.value = true
}

function handleSubmit() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload = { title: form.title, category: form.category, content: form.content, coverFileId: null }
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
  const action = status === 'PUBLISHED' ? '发布' : '下线'
  ElMessageBox.confirm(`确定${action}内容「${row.title}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await updateContentStatus(row.id, status)
      ElMessage.success(`${action}成功`)
      fetchList()
    })
    .catch(() => {})
}

onMounted(fetchList)
</script>

<style scoped>
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

<template>
  <div>
    <PageHeader title="竞赛管理">
      <template #actions>
        <el-button v-if="isAdmin" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新增竞赛
        </el-button>
      </template>
    </PageHeader>

    <PageContainer>
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="竞赛名称"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(label, value) in COMPETITION_STATUS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="query.level" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(label, value) in COMPETITION_LEVEL" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="赛制">
          <el-select v-model="query.competitionType" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(label, value) in COMPETITION_TYPE" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table class="competition-table" v-loading="loading" :data="list" border stripe>
        <el-table-column prop="title" label="竞赛名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="级别" width="100">
          <template #default="{ row }">{{ COMPETITION_LEVEL[row.level] || row.level }}</template>
        </el-table-column>
        <el-table-column label="赛制" width="90">
          <template #default="{ row }">{{ COMPETITION_TYPE[row.competitionType] || row.competitionType }}</template>
        </el-table-column>
        <el-table-column label="所属专业" min-width="170"><template #default="{row}">{{ row.majorName || '待补录专业' }} <el-button v-if="canManage(row)" link type="primary" @click="openClassification(row)">{{ row.majorId ? '调整分类' : '补录' }}</el-button></template></el-table-column>
        <el-table-column prop="category" label="类别" width="120" show-overflow-tooltip />
        <el-table-column label="报名时间" width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.registrationStartTime || row.registrationEndTime">
              {{ row.registrationStartTime || '-' }} ~ {{ row.registrationEndTime || '-' }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="maxMembers" label="人数上限" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ COMPETITION_STATUS[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" :width="isAdmin ? 330 : 80" fixed="right">
          <template #default="{ row }">
            <template v-if="canManage(row)">
              <el-button link type="primary" :disabled="['ENDED','CANCELLED'].includes(row.status)" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="row.status === 'DRAFT'" link type="success" @click="openPublish(row)">发布</el-button>
              <el-button
                v-else-if="row.status === 'REGISTERING'"
                link
                type="warning"
                @click="changeStatus(row, 'IN_PROGRESS')"
              >开始</el-button>
              <el-button v-if="row.status === 'IN_PROGRESS'" link type="warning" @click="changeStatus(row, 'ENDED')">结束</el-button>
              <el-button v-if="!['ENDED', 'CANCELLED'].includes(row.status)" link type="danger" @click="cancelCompetition(row)">取消竞赛</el-button>
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

    <el-dialog v-model="classificationVisible" title="维护所属专业分类" width="460px">
      <el-form label-width="90px"><el-form-item label="所属专业" required><el-select v-model="classification.majorId" style="width:100%"><el-option v-for="major in majors" :key="major.id" :label="major.name" :value="major.id"/></el-select></el-form-item></el-form>
      <template #footer><el-button @click="classificationVisible=false">取消</el-button><el-button type="primary" :loading="submitting" @click="saveClassification">保存</el-button></template>
    </el-dialog>
    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑竞赛' : '新增竞赛'"
      width="640px"
      :close-on-click-modal="false"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="所属专业" prop="majorId"><el-select v-model="form.majorId" clearable placeholder="发布前请选择专业" style="width:100%"><el-option v-for="m in majors" :key="m.id" :label="m.name" :value="m.id" /></el-select></el-form-item>
        <el-form-item label="竞赛名称" prop="title">
          <el-input v-model="form.title" maxlength="128" show-word-limit />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="赛制" prop="competitionType">
              <el-select v-model="form.competitionType" style="width: 100%">
                <el-option v-for="(label, value) in COMPETITION_TYPE" :key="value" :label="label" :value="value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="级别" prop="level">
              <el-select v-model="form.level" style="width: 100%">
                <el-option v-for="(label, value) in COMPETITION_LEVEL" :key="value" :label="label" :value="value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="学科类别">
              <el-input v-model="form.category" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="主办单位">
              <el-input v-model="form.organizer" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="团队人数上限" prop="maxMembers">
              <el-input-number
                v-model="form.maxMembers"
                :min="1"
                :disabled="form.competitionType === 'PERSONAL'"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="指导老师上限" prop="maxTeachers">
              <el-input-number v-model="form.maxTeachers" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="userStore.role === 'ADMIN'" label="负责人用户ID">
          <el-input-number v-model="form.managerId" :min="1" :precision="0" controls-position="right" />
          <span class="el-upload__tip">需为启用的管理员账户，可清空。</span>
        </el-form-item>
        <el-form-item label="跨专业组队">
          <el-switch v-model="form.allowCrossMajor" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="报名时间">
          <el-date-picker
            v-model="registrationRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="截止时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="竞赛时间">
          <el-date-picker
            v-model="competitionRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="指导老师">
          <CompetitionTeacherConfig v-if="dialogVisible" v-model="form.teachers" />
        </el-form-item>
        <el-form-item label="竞赛文件">
          <el-upload accept=".pdf,.doc,.docx" :show-file-list="false" :http-request="uploadDocument" :disabled="uploadingDocument">
            <el-button :loading="uploadingDocument">上传竞赛文件</el-button>
            <template #tip><div class="el-upload__tip">支持 PDF、DOC、DOCX，最大10MB。</div></template>
          </el-upload>
          <div v-if="form.documentFileId" style="width:100%">{{ form.documentName }} <el-button link type="danger" @click="form.documentFileId = null; form.documentName = ''">移除关联</el-button></div>
        </el-form-item>
        <el-form-item label="竞赛简介">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="uploadingDocument || loadingTeachers" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="publishVisible" title="发布竞赛" width="600px" :close-on-click-modal="false" :close-on-press-escape="!publishing" :show-close="!publishing">
      <el-form label-width="110px" @submit.prevent>
        <el-form-item label="竞赛名称">{{ publishForm.competitionTitle }}</el-form-item>
        <el-form-item label="同步发布新闻"><el-switch v-model="publishForm.syncNews" :disabled="publishing" /></el-form-item>
        <template v-if="publishForm.syncNews">
          <el-form-item label="新闻标题" required><el-input v-model="publishForm.title" maxlength="128" show-word-limit :disabled="publishing" /></el-form-item>
          <el-form-item label="比赛官网" required><el-input v-model="publishForm.officialUrl" placeholder="https://对应大赛官网" maxlength="2048" :disabled="publishing" /></el-form-item>
          <el-form-item label="新闻正文"><el-input v-model="publishForm.content" type="textarea" :rows="6" :disabled="publishing" /></el-form-item>
          <el-alert title="发布后可在新闻管理中维护封面和首页轮播。" type="info" :closable="false" />
        </template>
      </el-form>
      <template #footer>
        <el-button :disabled="publishing" @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="handlePublish">确认发布</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="竞赛详情" width="640px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="竞赛名称" :span="2">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="赛制">{{ COMPETITION_TYPE[detail.competitionType] || detail.competitionType }}</el-descriptions-item>
        <el-descriptions-item label="级别">{{ COMPETITION_LEVEL[detail.level] || detail.level }}</el-descriptions-item>
        <el-descriptions-item label="所属专业">{{ detail.majorName || '待补录' }}</el-descriptions-item>
        <el-descriptions-item label="学科类别">{{ detail.category || '-' }}</el-descriptions-item>
        <el-descriptions-item label="主办单位">{{ detail.organizer || '-' }}</el-descriptions-item>
        <el-descriptions-item label="团队人数上限">{{ detail.maxMembers }}</el-descriptions-item>
        <el-descriptions-item label="指导老师上限">{{ detail.maxTeachers }}</el-descriptions-item>
        <el-descriptions-item label="跨专业组队">{{ detail.allowCrossMajor === 1 ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ COMPETITION_STATUS[detail.status] || detail.status }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.cancellationReason" label="取消原因" :span="2">{{ detail.cancellationReason }}</el-descriptions-item>
        <el-descriptions-item label="报名时间" :span="2">
          {{ (detail.registrationStartTime || '-') + ' ~ ' + (detail.registrationEndTime || '-') }}
        </el-descriptions-item>
        <el-descriptions-item label="竞赛时间" :span="2">
          {{ (detail.startTime || '-') + ' ~ ' + (detail.endTime || '-') }}
        </el-descriptions-item>
        <el-descriptions-item label="竞赛简介" :span="2">{{ detail.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ detail.createTime || '-' }}</el-descriptions-item>
      </el-descriptions>
      <CompetitionResources v-if="detailVisible" :competition="detail" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PageContainer from '@/components/PageContainer.vue'
import CompetitionTeacherConfig from '@/components/CompetitionTeacherConfig.vue'
import CompetitionResources from '@/components/CompetitionResources.vue'
import { listMajors } from '@/api/teacher'
import { pageQuota } from '@/api/guidance'
import { uploadFile } from '@/api/file'
import { useUserStore } from '@/store/user'
import {
  pageCompetition,
  getCompetition,
  createCompetition,
  updateCompetition,
  deleteCompetition,
  updateCompetitionStatus,
  publishCompetition
} from '@/api/competition'
import { updateCompetitionMajor } from '@/api/competition'
import { COMPETITION_TYPE, COMPETITION_LEVEL, COMPETITION_STATUS } from '@/constants/dict'

const route = useRoute()
const userStore = useUserStore()
const isAdmin = computed(() => userStore.role === 'ADMIN' || userStore.role === 'MAJOR_ADMIN')

function canManage(row) {
  return userStore.role === 'ADMIN' || row.publisherId === userStore.userInfo?.id || row.managerId === userStore.userInfo?.id
}

const loading = ref(false)
const submitting = ref(false)
const uploadingDocument = ref(false)
const loadingTeachers = ref(false)
const publishVisible = ref(false)
const publishing = ref(false)
const publishForm = reactive({ id: null, competitionTitle: '', syncNews: false, title: '', content: '', officialUrl: '' })

function openPublish(row) {
  Object.assign(publishForm, { id: row.id, competitionTitle: row.title, syncNews: false, title: row.title, content: row.description || '', officialUrl: '' })
  publishVisible.value = true
}

async function handlePublish() {
  if (publishing.value) return
  if (publishForm.syncNews) {
    if (!publishForm.title.trim()) { ElMessage.warning('请输入新闻标题'); return }
    try {
      const url = new URL(publishForm.officialUrl.trim())
      if (!['http:', 'https:'].includes(url.protocol) || !url.hostname || url.username || url.password) throw new Error()
    } catch { ElMessage.warning('请填写有效的 HTTP/HTTPS 比赛官网地址'); return }
  }
  publishing.value = true
  try {
    await publishCompetition(publishForm.id, {
      syncNews: publishForm.syncNews,
      ...(publishForm.syncNews ? { news: { title: publishForm.title.trim(), content: publishForm.content, officialUrl: publishForm.officialUrl.trim(), category: 'NEWS' } } : {})
    })
    ElMessage.success(publishForm.syncNews ? '竞赛和新闻已发布' : '竞赛已发布')
    publishVisible.value = false
    await fetchList()
  } catch { return }
  finally { publishing.value = false }
}
const list = ref([])
const majors = ref([])
const classificationVisible=ref(false), classification=reactive({id:null,majorId:null})
function openClassification(row){classification.id=row.id;classification.majorId=row.majorId;classificationVisible.value=true}
async function saveClassification(){
  if(!classification.majorId){ElMessage.warning('请选择专业');return}
  submitting.value=true
  try{await updateCompetitionMajor(classification.id,classification.majorId);classificationVisible.value=false;ElMessage.success('分类已保存');await fetchList()}
  finally{submitting.value=false}
}
const total = ref(0)
const query = reactive({
  current: 1,
  size: 10,
  keyword: '',
  status: '',
  competitionType: '',
  level: ''
})

const dialogVisible = ref(false)
const detailVisible = ref(false)
const formRef = ref()
const detail = ref({})
const registrationRange = ref([])
const competitionRange = ref([])

const defaultForm = () => ({
  id: null,
  managerId: null,
  majorId: null,
  title: '',
  competitionType: 'TEAM',
  level: 'SCHOOL',
  category: '',
  organizer: '',
  description: '',
  maxMembers: 3,
  maxTeachers: 1,
  allowCrossMajor: 0,
  documentFileId: null,
  documentName: '',
  teachers: []
})
const form = reactive(defaultForm())

const rules = {
  title: [{ required: true, message: '请输入竞赛名称', trigger: 'blur' }],
  competitionType: [{ required: true, message: '请选择赛制', trigger: 'change' }],
  level: [{ required: true, message: '请选择竞赛级别', trigger: 'change' }],
  maxMembers: [{ required: true, message: '请输入团队人数上限', trigger: 'blur' }],
  maxTeachers: [{ required: true, message: '请输入指导老师上限', trigger: 'blur' }]
}

function statusTagType(status) {
  if (status === 'REGISTERING') return 'success'
  if (status === 'IN_PROGRESS') return 'warning'
  return 'info'
}

async function fetchList() {
  loading.value = true
  try {
    const res = await pageCompetition(query)
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
  query.keyword = ''
  query.status = ''
  query.competitionType = ''
  query.level = ''
  fetchList()
}

let formVersion = 0
function resetForm() {
  formVersion++
  loadingTeachers.value = false
  uploadingDocument.value = false
  Object.assign(form, defaultForm())
  registrationRange.value = []
  competitionRange.value = []
  formRef.value?.clearValidate()
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row) {
  resetForm()
  const version = formVersion
  Object.assign(form, {
    id: row.id,
    managerId: row.managerId,
    majorId: row.majorId,
    title: row.title,
    competitionType: row.competitionType,
    level: row.level,
    category: row.category,
    organizer: row.organizer,
    description: row.description,
    maxMembers: row.maxMembers,
    maxTeachers: row.maxTeachers,
    allowCrossMajor: row.allowCrossMajor,
    documentFileId: row.documentFileId,
    documentName: row.documentName
  })
  registrationRange.value = [row.registrationStartTime, row.registrationEndTime].filter(Boolean)
  competitionRange.value = [row.startTime, row.endTime].filter(Boolean)
  dialogVisible.value = true
  loadingTeachers.value = true
  try {
    const res = await pageQuota({ competitionId: row.id, current: 1, size: 1000 })
    if (version === formVersion && dialogVisible.value) form.teachers = res.data.records.map(q => ({ teacherId: q.teacherId, quota: q.quota }))
  } finally { if (version === formVersion) loadingTeachers.value = false }
}

async function openDetail(row) {
  detail.value = (await getCompetition(row.id)).data
  detailVisible.value = true
}

async function uploadDocument({ file, onSuccess, onError }) {
  if (file.size > 10 * 1024 * 1024) { const error = new Error('文件不能超过10MB'); ElMessage.warning(error.message); onError(error); return }
  const version = formVersion
  uploadingDocument.value = true
  try { const res = await uploadFile(file); if (version === formVersion && dialogVisible.value) { form.documentFileId = res.data.id; form.documentName = res.data.name } onSuccess(res) }
  catch (error) { onError(error) }
  finally { if (version === formVersion) uploadingDocument.value = false }
}

function handleSubmit() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    if (!form.teachers.length || form.teachers.some(t => !t.teacherId || !t.quota)) {
      ElMessage.warning('请至少配置一位指导老师并设置名额'); return
    }
    if (!registrationRange.value || registrationRange.value.length !== 2 || registrationRange.value[0] >= registrationRange.value[1]) {
      ElMessage.warning('报名起止时间必填且开始时间必须早于截止时间'); return
    }
    submitting.value = true
    try {
      const payload = {
        title: form.title,
        managerId: form.managerId || null,
        majorId: form.majorId || null,
        competitionType: form.competitionType,
        level: form.level,
        category: form.category,
        organizer: form.organizer,
        description: form.description,
        coverFileId: null,
        documentFileId: form.documentFileId,
        teachers: form.teachers,
        registrationStartTime: registrationRange.value?.[0] || null,
        registrationEndTime: registrationRange.value?.[1] || null,
        startTime: competitionRange.value?.[0] || null,
        endTime: competitionRange.value?.[1] || null,
        maxMembers: form.maxMembers,
        maxTeachers: form.maxTeachers,
        allowCrossMajor: form.allowCrossMajor
      }
      if (form.id) {
        await updateCompetition(form.id, payload)
      } else {
        await createCompetition(payload)
      }
      ElMessage.success('保存成功')
      dialogVisible.value = false
      fetchList()
    } finally {
      submitting.value = false
    }
  })
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除竞赛「${row.title}」吗？删除后不可恢复。`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteCompetition(row.id)
      ElMessage.success('删除成功')
      fetchList()
    })
    .catch(() => {})
}

function changeStatus(row, status) {
  const action = { REGISTERING: '发布', IN_PROGRESS: '开始', ENDED: '结束' }[status]
  ElMessageBox.confirm(`确定${action}竞赛「${row.title}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await updateCompetitionStatus(row.id, status)
      ElMessage.success(`${action}成功`)
      fetchList()
    })
    .catch(() => {})
}

async function cancelCompetition(row) {
  try {
    const { value } = await ElMessageBox.prompt(`请输入取消「${row.title}」的原因，报名记录将保留并通知参赛人员。`, '取消竞赛', {
      inputValidator: value => !!value?.trim() && value.trim().length <= 1000 || '请填写1至1000字取消原因',
      confirmButtonText: '确认取消竞赛'
    })
    await updateCompetitionStatus(row.id, 'CANCELLED', value.trim())
    ElMessage.success('竞赛已取消')
    fetchList()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') throw error
  }
}

watch(() => [route.query.edit, route.query.open], async ([id]) => { if (id) openEdit((await getCompetition(Number(id))).data) })
watch(() => [route.query.detail, route.query.open], ([id]) => { if (id) openDetail({ id: Number(id) }) })
onMounted(async () => { majors.value = (await listMajors()).data; fetchList(); if (route.query.edit) openEdit((await getCompetition(Number(route.query.edit))).data); if (route.query.detail) openDetail({ id: Number(route.query.detail) }) })
</script>

<style scoped>
.competition-table { font-size:13px; }
.competition-table :deep(.el-table__fixed-right .cell), .competition-table :deep(td:last-child .cell) { white-space:nowrap; }
.competition-table :deep(.el-table__body-wrapper) { overflow-x:auto; }
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
</style>

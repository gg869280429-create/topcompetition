<template>
  <div>
    <PageHeader title="获奖管理">
      <template #actions>
        <el-button v-if="isAdmin" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>登记获奖
        </el-button>
      </template>
    </PageHeader>

    <PageContainer>
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item label="竞赛">
          <el-select v-model="query.competitionId" placeholder="全部" clearable filterable style="width: 200px">
            <el-option v-for="c in competitions" :key="c.id" :label="c.title" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(label, value) in AWARD_STATUS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="奖项">
          <el-select v-model="query.awardLevel" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(label, value) in AWARD_LEVEL" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="competitionTitle" label="竞赛名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="teamName" label="团队名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="leaderName" label="队长" width="110" show-overflow-tooltip />
        <el-table-column label="奖项等级" width="100">
          <template #default="{ row }">
            <el-tag type="warning">{{ AWARD_LEVEL[row.awardLevel] || row.awardLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ AWARD_STATUS[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="awardTime" label="获奖时间" width="170" />
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button link @click="openDetail(row.id)">详情与历史</el-button>
            <el-button v-if="row.certificateFileId" link type="primary" @click="downloadAwardCertificate(row.id)">证明</el-button>
            <template v-if="isAdmin && row.status === 'PENDING'">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="success" @click="openReview(row, 'APPROVED')">通过</el-button>
              <el-button link type="danger" @click="openReview(row, 'REJECTED')">驳回</el-button>
            </template>
            <el-button v-if="isAdmin && row.status === 'REJECTED'" link type="primary" @click="openEdit(row)">修改重提</el-button>
            <el-button v-if="isAdmin && row.status === 'APPROVED'" link type="primary" @click="openEdit(row, true)">更正重审</el-button>
            <el-button v-if="isAdmin && row.status !== 'REVOKED'" link type="danger" @click="handleRevoke(row)">撤销</el-button>
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

    <!-- 登记 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="correctionMode ? '更正并重新审核' : form.id ? '编辑并重提获奖' : '登记获奖'"
      width="520px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="竞赛" prop="competitionId">
          <el-select
            v-if="!form.id"
            v-model="form.competitionId"
            placeholder="请选择竞赛"
            filterable
            style="width: 100%"
            @change="handleCompetitionChange"
          >
            <el-option v-for="c in competitions" :key="c.id" :label="c.title" :value="c.id" />
          </el-select>
          <el-input v-else :model-value="form.competitionTitle" disabled />
        </el-form-item>
        <el-form-item label="报名团队" prop="registrationId">
          <el-select
            v-if="!form.id"
            v-model="form.registrationId"
            placeholder="请选择已通过的报名"
            filterable
            style="width: 100%"
          >
            <el-option v-for="r in registrations" :key="r.id" :label="r.teamName" :value="r.id" />
          </el-select>
          <el-input v-else :model-value="form.teamName" disabled />
        </el-form-item>
        <el-form-item label="奖项等级" prop="awardLevel">
          <el-select v-model="form.awardLevel" placeholder="请选择奖项等级" style="width: 100%">
            <el-option v-for="(label, value) in AWARD_LEVEL" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="获奖时间" prop="awardTime">
          <el-date-picker
            v-model="form.awardTime"
            type="datetime"
            placeholder="请选择获奖时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="获奖证明" prop="certificateFileId">
          <el-upload :show-file-list="false" :auto-upload="false" accept=".pdf,.doc,.docx" :on-change="uploadCertificate">
            <el-button :loading="uploading">上传 PDF / DOC / DOCX</el-button>
          </el-upload>
          <span v-if="form.certificateFileId">已上传证明 #{{ form.certificateFileId }}</span>
        </el-form-item>
        <el-form-item v-if="correctionMode" label="更正原因">
          <el-input v-model="form.reason" type="textarea" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 审核弹窗 -->
    <el-dialog
      v-model="reviewVisible"
      :title="reviewAction === 'APPROVED' ? '通过获奖' : '驳回获奖'"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form label-width="80px">
        <el-form-item label="审核意见">
          <el-input
            v-model="reviewComment"
            type="textarea"
            :rows="3"
            :placeholder="reviewAction === 'APPROVED' ? '选填' : '请填写驳回原因'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleReviewSubmit">确定</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="detailVisible" title="获奖详情与历史" width="760px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="竞赛">{{ detail.competitionTitle }}</el-descriptions-item>
          <el-descriptions-item label="团队">{{ detail.teamNameSnapshot || detail.teamName }}</el-descriptions-item>
          <el-descriptions-item label="归属专业">{{ detail.majorNameSnapshot }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ AWARD_STATUS[detail.status] }}</el-descriptions-item>
          <el-descriptions-item label="获奖日期">{{ detail.awardTime }}</el-descriptions-item>
          <el-descriptions-item label="登记人">{{ detail.registrantId }}</el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ detail.reviewTime }}</el-descriptions-item>
          <el-descriptions-item label="审核意见">{{ detail.reviewComment }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.revocationReason" label="撤销原因">{{ detail.revocationReason }}</el-descriptions-item>
        </el-descriptions>
        <p>登记时成员：{{ participantsText }}</p>
        <el-button v-if="detail.certificateFileId" @click="downloadAwardCertificate(detail.id)">下载获奖证明</el-button>
        <el-timeline style="margin-top:24px">
          <el-timeline-item v-for="item in historyRows" :key="item.id" :timestamp="item.operationTime">
            <strong>{{ operationLabel(item.operation) }} · {{ item.actorName }}</strong>
            <p v-if="item.reason">原因：{{ item.reason }}</p>
            <details><summary>查看变更前后记录</summary><pre>{{ prettyHistory(item.beforeJson) }}</pre><pre>{{ prettyHistory(item.afterJson) }}</pre></details>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PageContainer from '@/components/PageContainer.vue'
import { useUserStore } from '@/store/user'
import { pageCompetition } from '@/api/competition'
import { pageRegistration } from '@/api/registration'
import { pageAward, createAward, updateAward, reviewAward, getAward, correctAward, revokeAward, awardHistory, uploadAwardCertificate, downloadAwardCertificate } from '@/api/award'
import { AWARD_LEVEL } from '@/constants/dict'
const AWARD_STATUS = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回', REVOKED: '已撤销' }

const userStore = useUserStore()
const isAdmin = computed(() => userStore.role === 'ADMIN' || userStore.role === 'MAJOR_ADMIN')

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, competitionId: null, status: '', awardLevel: '' })

const competitions = ref([])
const registrations = ref([])

const dialogVisible = ref(false)
const formRef = ref()
const correctionMode = ref(false)
const uploading = ref(false)
const detailVisible = ref(false)
const detail = ref(null)
const historyRows = ref([])
const route = useRoute()
const participantsText = computed(() => JSON.parse(detail.value?.participantsSnapshot || '[]').map(m => `${m.name || m.userId}（${m.majorName || '未配置专业'}）`).join('、'))
const defaultForm = () => ({
  id: null,
  competitionId: null,
  competitionTitle: '',
  registrationId: null,
  teamName: '',
  awardLevel: 'FIRST',
  awardTime: null,
  certificateFileId: null,
  reason: ''
})
const form = reactive(defaultForm())
const rules = {
  competitionId: [{ required: true, message: '请选择竞赛', trigger: 'change' }],
  registrationId: [{ required: true, message: '请选择报名团队', trigger: 'change' }],
  awardLevel: [{ required: true, message: '请选择奖项等级', trigger: 'change' }],
  awardTime: [{ required: true, message: '请选择获奖时间', trigger: 'change' }],
  certificateFileId: [{ required: true, message: '请上传获奖证明', trigger: 'change' }]
}

const reviewVisible = ref(false)
const reviewId = ref(null)
const reviewAction = ref('APPROVED')
const reviewComment = ref('')

function statusTagType(status) {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'warning'
}

async function fetchList() {
  loading.value = true
  try {
    const res = await pageAward(query)
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
  query.competitionId = null
  query.status = ''
  query.awardLevel = ''
  fetchList()
}

async function loadCompetitions() {
  const res = await pageCompetition({ current: 1, size: 100 })
  competitions.value = res.data.records
}

async function handleCompetitionChange(id) {
  form.registrationId = null
  registrations.value = []
  if (!id) return
  const res = await pageRegistration({ competitionId: id, status: 'APPROVED', current: 1, size: 100 })
  registrations.value = res.data.records
}

function resetForm() {
  correctionMode.value = false
  Object.assign(form, defaultForm())
  registrations.value = []
  formRef.value?.clearValidate()
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row, correction = false) {
  resetForm()
  Object.assign(form, {
    id: row.id,
    competitionId: row.competitionId,
    competitionTitle: row.competitionTitle,
    registrationId: row.registrationId,
    teamName: row.teamName,
    awardLevel: row.awardLevel,
    awardTime: row.awardTime,
    certificateFileId: row.certificateFileId
  })
  correctionMode.value = correction
  dialogVisible.value = true
}

function handleSubmit() {
  formRef.value.validate(async (valid) => {
    if (!valid || uploading.value) return
    if (correctionMode.value && !form.reason.trim()) { ElMessage.warning('请填写更正原因'); return }
    submitting.value = true
    try {
      const payload = { registrationId: form.registrationId, awardLevel: form.awardLevel, awardTime: form.awardTime, certificateFileId: form.certificateFileId, reason: correctionMode.value ? form.reason : null }
      if (form.id) {
        if (correctionMode.value) await correctAward(form.id, payload)
        else await updateAward(form.id, payload)
      } else {
        await createAward(payload)
      }
      ElMessage.success('保存成功')
      dialogVisible.value = false
      fetchList()
    } finally {
      submitting.value = false
    }
  })
}

async function handleRevoke(row) {
  try {
    const { value } = await ElMessageBox.prompt('撤销后保留记录，不能直接恢复。请填写原因', '撤销获奖', { inputType: 'textarea', inputValidator: value => (!!value?.trim() && value.length <= 1000) || '请填写不超过1000字的原因' })
    await revokeAward(row.id, value)
    ElMessage.success('已撤销')
    fetchList()
  } catch (error) { if (error !== 'cancel' && error !== 'close') console.error(error) }
}
async function uploadCertificate(upload) {
  uploading.value = true
  try {
    const res = await uploadAwardCertificate(upload.raw)
    form.certificateFileId = res.data.id
    formRef.value?.clearValidate('certificateFileId')
    ElMessage.success('证明已上传')
  } finally { uploading.value = false }
}
async function openDetail(id) {
  const [record, history] = await Promise.all([getAward(id), awardHistory(id)])
  detail.value = record.data
  historyRows.value = history.data
  detailVisible.value = true
}
function operationLabel(value) { return { CREATE:'登记', UPDATE:'编辑', RESUBMIT:'驳回重提', CORRECT:'更正重审', REVOKE:'撤销', REVIEW:'审核', MIGRATED_BASELINE:'迁移基线' }[value] || value }
function prettyHistory(value) { return value ? JSON.stringify(JSON.parse(value), null, 2) : '无' }

function openReview(row, action) {
  reviewId.value = row.id
  reviewAction.value = action
  reviewComment.value = ''
  reviewVisible.value = true
}

async function handleReviewSubmit() {
  if (reviewAction.value === 'REJECTED' && !reviewComment.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  submitting.value = true
  try {
    await reviewAward(reviewId.value, { result: reviewAction.value, comment: reviewComment.value })
    ElMessage.success('审核完成')
    reviewVisible.value = false
    fetchList()
  } finally {
    submitting.value = false
  }
}

watch(() => [route.query.detail, route.query.open], ([id]) => { if (id) openDetail(id) })
onMounted(() => {
  fetchList()
  loadCompetitions()
  if (route.query.detail) openDetail(route.query.detail)
})
</script>

<style scoped>
pre {
  max-height: 260px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

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

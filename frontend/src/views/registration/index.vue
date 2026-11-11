<template>
  <div>
    <PageHeader :title="isStudent ? '我的报名' : isTeacher ? '指导团队详情' : '报名审核'">
      <template #actions>
        <el-button v-if="isStudent" type="primary" @click="openApply">
          <el-icon><Plus /></el-icon>报名竞赛
        </el-button>
      </template>
    </PageHeader>

    <PageContainer v-if="isStudent && invitations.length">
      <h3>我的团队邀请</h3>
      <p>接受邀请后队长仍需正式提交；确认前不占用参赛资格。</p>
      <el-table :data="invitations" border>
        <el-table-column prop="registrationId" label="报名编号" />
        <el-table-column prop="competitionTitle" label="竞赛" /><el-table-column prop="teamName" label="团队" /><el-table-column prop="inviterName" label="邀请人" />
        <el-table-column prop="status" label="邀请状态" />
        <el-table-column label="操作"><template #default="{ row }">
          <el-button v-if="row.status === 'PENDING'" link type="success" @click="respond(row, true)">接受</el-button>
          <el-button v-if="row.status === 'PENDING'" link type="danger" @click="respond(row, false)">拒绝</el-button>
        </template></el-table-column>
      </el-table>
    </PageContainer>
    <PageContainer v-if="!isTeacher">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(label, value) in REGISTRATION_STATUS" :key="value" :label="label" :value="value" />
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
        <el-table-column prop="preferredTeacherName" label="意向教师" min-width="110" />
        <el-table-column label="成员数" width="80">
          <template #default="{ row }">{{ row.members?.length || 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ REGISTRATION_STATUS[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="applyTime" label="报名时间" width="170" />
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <template v-if="isStudent && ['DRAFT', 'PENDING', 'REJECTED'].includes(row.status) && row.leaderId === currentUserId">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="handleCancel(row)">取消</el-button>
            </template>
            <template v-if="isStudent && row.leaderId === currentUserId">
              <el-button v-if="['DRAFT', 'REJECTED'].includes(row.status)" link type="success" @click="submitDraft(row)">正式提交</el-button>
              <el-button v-if="row.status === 'APPROVED'" link type="primary" @click="reasonAction(row, 'adjust')">申请调整</el-button>
              <el-button v-if="row.status === 'APPROVED'" link type="danger" @click="reasonAction(row, 'withdraw')">申请退赛</el-button>
              <el-button v-if="['CANCELLED', 'WITHDRAWN'].includes(row.status)" link type="primary" @click="reasonAction(row, 'reapply')">重新申请</el-button>
            </template>
            <template v-if="isAdmin && row.status === 'PENDING'">
              <el-button link type="success" @click="openReview(row, 'APPROVED')">通过</el-button>
              <el-button link type="danger" @click="openReview(row, 'REJECTED')">驳回</el-button>
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

    <!-- 报名 / 编辑弹窗 -->
    <el-dialog
      v-model="applyVisible"
      :title="applyForm.id ? '编辑报名' : '报名竞赛'"
      width="560px"
      :close-on-click-modal="false"
      @close="resetApplyForm"
    >
      <el-form ref="applyFormRef" :model="applyForm" :rules="applyRules" label-width="100px">
        <el-form-item label="竞赛" prop="competitionId">
          <el-select
            v-if="!applyForm.id"
            v-model="applyForm.competitionId"
            placeholder="请选择竞赛"
            style="width: 100%"
            @change="handleCompetitionChange"
          >
            <el-option v-for="c in competitions" :key="c.id" :label="c.title" :value="c.id" />
          </el-select>
          <el-input v-else :model-value="currentCompetition?.title || ''" disabled />
        </el-form-item>
        <el-form-item label="意向教师" prop="preferredTeacherId">
          <el-select v-model="applyForm.preferredTeacherId" placeholder="请选择一位意向教师" :loading="teachersLoading" :disabled="teachersLoading || !applyForm.competitionId" style="width:100%">
            <el-option v-for="t in teacherOptions" :key="t.teacherId" :value="t.teacherId" :label="`${t.teacherName}（${t.majorName || '专业未设置'}，剩余${t.remaining}个名额）`" />
          </el-select>
          <div class="tip-text">意向不占用名额，最终指导教师由管理员确认。</div>
          <div v-if="selectedTeacher" class="tip-text">擅长方向：{{ selectedTeacher.expertise || '暂未填写' }}<br />{{ selectedTeacher.biography }}</div>
        </el-form-item>
        <el-form-item label="团队名称" prop="teamName">
          <el-input v-model="applyForm.teamName" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item v-if="currentCompetition?.competitionType === 'TEAM'" label="团队成员">
          <el-select
            v-model="applyForm.memberIds"
            multiple
            filterable
            remote
            reserve-keyword
            :remote-method="searchStudents"
            :loading="studentLoading"
            placeholder="搜索并选择成员（不含队长）"
            style="width: 100%"
          >
            <el-option
              v-for="s in students"
              :key="s.id"
              :label="s.realName || s.username"
              :value="s.id"
              :disabled="s.id === currentUserId"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="currentCompetition">
          <span class="tip-text">
            赛制：{{ COMPETITION_TYPE[currentCompetition.competitionType] }}，
            团队人数上限：{{ currentCompetition.maxMembers }}（含队长）
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="teachersLoading" @click="handleApplySubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="报名详情" width="600px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="竞赛" :span="2">{{ detail.competitionTitle }}</el-descriptions-item>
        <el-descriptions-item label="团队名称" :span="2">{{ detail.teamName }}</el-descriptions-item>
        <el-descriptions-item label="队长">{{ detail.leaderName }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ REGISTRATION_STATUS[detail.status] || detail.status }}</el-descriptions-item>
        <el-descriptions-item label="意向教师">{{ detail.preferredTeacherName || '历史报名未选择' }}</el-descriptions-item>
        <el-descriptions-item label="最终指导">{{ finalTeachers || '待分配' }}</el-descriptions-item>
        <el-descriptions-item label="报名时间" :span="2">{{ detail.applyTime }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.reviewTime" label="审核时间" :span="2">{{ detail.reviewTime }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.reviewComment" label="审核意见" :span="2">{{ detail.reviewComment }}</el-descriptions-item>
        <el-descriptions-item label="团队成员" :span="2">
          <div v-for="m in detail.members" :key="m.userId" class="member-row">
            <span>{{ m.name }}</span>
            <el-tag v-if="m.memberRole === 'LEADER'" size="small" type="primary">队长</el-tag>
          </div>
        </el-descriptions-item>
      </el-descriptions>
      <h3>成员邀请记录</h3>
      <el-table :data="workflow.invitations || []" border>
        <el-table-column prop="inviteeId" label="成员编号" /><el-table-column prop="status" label="状态" />
        <el-table-column label="操作"><template #default="{ row }">
          <el-button v-if="isStudent && detail.leaderId === currentUserId && detail.status === 'DRAFT' && ['PENDING','ACCEPTED','REJECTED'].includes(row.status)" link type="danger" @click="revoke(row)">撤销</el-button>
        </template></el-table-column>
      </el-table>
      <h3>退赛申请</h3>
      <el-table :data="workflow.withdrawals || []" border>
        <el-table-column prop="reason" label="原因" /><el-table-column prop="status" label="状态" /><el-table-column prop="reviewComment" label="审核意见" />
        <el-table-column v-if="isAdmin" label="操作"><template #default="{ row }">
          <el-button v-if="row.status === 'PENDING'" link type="success" @click="withdrawalReview(row, 'APPROVED')">批准</el-button>
          <el-button v-if="row.status === 'PENDING'" link type="danger" @click="withdrawalReview(row, 'REJECTED')">驳回</el-button>
        </template></el-table-column>
      </el-table>
      <h3>报名及审核历史</h3>
      <el-table :data="workflow.history || []" border>
        <el-table-column prop="operation" label="操作" /><el-table-column prop="reason" label="原因" /><el-table-column prop="operationTime" label="时间" />
        <el-table-column label="记录"><template #default="{ row }"><el-popover width="500" trigger="click"><template #reference><el-button link>查看快照</el-button></template><pre style="max-height:300px;overflow:auto;white-space:pre-wrap">{{ row.beforeJson || row.beforeSnapshot }}
{{ row.afterJson || row.afterSnapshot }}</pre></el-popover></template></el-table-column>
      </el-table>
    </el-dialog>

    <!-- 审核弹窗 -->
    <el-dialog
      v-model="reviewVisible"
      :title="reviewAction === 'APPROVED' ? '通过报名' : '驳回报名'"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form label-width="90px">
        <el-form-item v-if="reviewAction === 'APPROVED'" label="意向教师">{{ reviewPreferredName || '历史报名未选择' }}</el-form-item>
        <el-form-item v-if="reviewAction === 'APPROVED'" label="最终教师">
          <el-select v-model="reviewTeacherId" :loading="reviewTeachersLoading" style="width:100%" placeholder="确认指导教师">
            <el-option v-for="t in reviewTeachers" :key="t.teacherId" :value="t.teacherId" :disabled="t.remaining <= 0" :label="`${t.teacherName}（剩余${t.remaining}个名额）`" />
          </el-select>
          <div v-if="reviewTeacherId && reviewPreferredId && reviewTeacherId !== reviewPreferredId" class="tip-text">已调剂教师，请在审核意见中填写原因。</div>
        </el-form-item>
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
        <el-button type="primary" :loading="submitting" :disabled="reviewTeachersLoading" @click="handleReviewSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import PageContainer from '@/components/PageContainer.vue'
import { useUserStore } from '@/store/user'
import { pageCompetition, getCompetition } from '@/api/competition'
import {
  pageRegistration,
  pageMyRegistration,
  createRegistration,
  updateRegistration,
  cancelRegistration,
  reviewRegistration, getRegistration, submitRegistration, myInvitations, respondInvitation,
  revokeInvitation, registrationHistory, adjustRegistration, reapplyRegistration, requestWithdrawal, reviewWithdrawal
} from '@/api/registration'
import { listStudents } from '@/api/user'
import { competitionTeachers, listGuidance } from '@/api/guidance'
import { COMPETITION_TYPE, REGISTRATION_STATUS } from '@/constants/dict'

const userStore = useUserStore()
const route = useRoute()
const isTeacher = computed(() => userStore.role === 'TEACHER')
const isStudent = computed(() => userStore.role === 'STUDENT')
const isAdmin = computed(() => userStore.role === 'ADMIN' || userStore.role === 'MAJOR_ADMIN')
const currentUserId = computed(() => userStore.userInfo?.id)

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, status: '' })

const applyVisible = ref(false)
const applyFormRef = ref()
const competitions = ref([])
const currentCompetition = ref(null)
const students = ref([])
const studentLoading = ref(false)
const applyForm = reactive({ id: null, competitionId: null, preferredTeacherId: null, teamName: '', memberIds: [] })
const teacherOptions = ref([])
const teachersLoading = ref(false)
const selectedTeacher = computed(() => teacherOptions.value.find(t => t.teacherId === applyForm.preferredTeacherId))
const finalTeachers = ref('')
const reviewTeachers = ref([])
const reviewTeachersLoading = ref(false)
const reviewTeacherId = ref(null)
const reviewPreferredId = ref(null)
const reviewPreferredName = ref('')

const invitations = ref([])
const workflow = ref({})
const detailVisible = ref(false)
const detail = ref({})

const reviewVisible = ref(false)
const reviewId = ref(null)
const reviewAction = ref('APPROVED')
const reviewComment = ref('')

const applyRules = {
  preferredTeacherId: [{ required: true, message: '请选择一位意向教师', trigger: 'change' }],
  competitionId: [{ required: true, message: '请选择竞赛', trigger: 'change' }],
  teamName: [{ required: true, message: '请输入团队名称', trigger: 'blur' }]
}

function statusTagType(status) {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'warning'
}

async function fetchList() {
  if (isTeacher.value) return
  loading.value = true
  try {
    const api = isStudent.value ? pageMyRegistration : pageRegistration
    const res = await api(query)
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
  query.status = ''
  fetchList()
}

async function loadCompetitions() {
  const res = await pageCompetition({ current: 1, size: 100, status: 'REGISTERING' })
  competitions.value = res.data.records
}

let teacherRequestVersion = 0
async function loadTeachers(id) {
  const version = ++teacherRequestVersion
  teachersLoading.value = true
  teacherOptions.value = []
  try { const res = await competitionTeachers(id); if (version === teacherRequestVersion) teacherOptions.value = res.data }
  finally { if (version === teacherRequestVersion) teachersLoading.value = false }
}
async function handleCompetitionChange(id) {
  applyForm.preferredTeacherId = null
  currentCompetition.value = competitions.value.find((c) => c.id === id) || null
  if (currentCompetition.value?.competitionType === 'PERSONAL') {
    applyForm.memberIds = []
  }
  await loadTeachers(id)
}

async function searchStudents(keyword) {
  studentLoading.value = true
  try {
    const res = await listStudents(keyword)
    students.value = res.data
  } finally {
    studentLoading.value = false
  }
}

let applyVersion = 0
function resetApplyForm() {
  applyVersion++
  teacherRequestVersion++
  teachersLoading.value = false
  applyForm.id = null
  applyForm.competitionId = null
  applyForm.preferredTeacherId = null
  teacherOptions.value = []
  applyForm.teamName = ''
  applyForm.memberIds = []
  currentCompetition.value = null
  students.value = []
  applyFormRef.value?.clearValidate()
}

async function openApply() {
  resetApplyForm()
  applyVisible.value = true
  const version = applyVersion
  await loadCompetitions()
  return version
}

async function openApplyWithCompetition(competitionId) {
  const version = await openApply()
  if (version !== applyVersion || !applyVisible.value) return
  applyForm.competitionId = competitionId
  await handleCompetitionChange(competitionId)
}

async function openEdit(row) {
  resetApplyForm()
  const version = applyVersion
  applyForm.id = row.id
  applyForm.competitionId = row.competitionId
  applyForm.teamName = row.teamName
  applyForm.preferredTeacherId = row.preferredTeacherId
  const existingMembers = (row.members || []).filter((m) => m.memberRole !== 'LEADER')
  applyForm.memberIds = existingMembers.map((m) => m.userId)
  students.value = existingMembers.map((m) => ({ id: m.userId, username: m.name, realName: m.name }))
  applyVisible.value = true
  const res = await getCompetition(row.competitionId)
  if (version !== applyVersion || !applyVisible.value) return
  currentCompetition.value = res.data
  await loadTeachers(row.competitionId)
}

async function loadInvitations() {
  if (isStudent.value) invitations.value = (await myInvitations()).data
}
async function openDetail(row) {
  const res = await getRegistration(row.id)
  detail.value = res.data
  workflow.value = (await registrationHistory(row.id)).data
  finalTeachers.value = ''
  detailVisible.value = true
  const guidance = await listGuidance(row.id)
  if (detail.value.id === row.id) finalTeachers.value = guidance.data.filter(t => t.status === 'ACTIVE').map(t => t.teacherName).join('、')
}
async function respond(row, accept) {
  await respondInvitation(row.id, accept)
  ElMessage.success(accept ? '已接受邀请，请等待队长正式提交' : '已拒绝邀请')
  await loadInvitations(); await fetchList()
}
async function revoke(row) {
  await revokeInvitation(row.id)
  await openDetail(detail.value); await fetchList()
}
async function submitDraft(row) {
  await submitRegistration(row.id)
  ElMessage.success('已正式提交审核'); await fetchList()
}
async function reasonAction(row, action) {
  const titles = { adjust: '申请调整将结束当前指导，释放名额，并重新确认成员及审核', withdraw: '申请退赛，由管理员审核', reapply: '重新申请仅限报名期，队友需重新确认' }
  try {
    const { value } = await ElMessageBox.prompt(titles[action], '请填写原因', { inputType: 'textarea', inputValidator: v => !!v?.trim() || '请填写原因' })
    const api = { adjust: adjustRegistration, withdraw: requestWithdrawal, reapply: reapplyRegistration }[action]
    await api(row.id, value)
    ElMessage.success('操作成功'); await fetchList()
  } catch (error) { if (error !== 'cancel' && error !== 'close') throw error }
}
async function withdrawalReview(row, result) {
  try {
    const { value } = await ElMessageBox.prompt(result === 'APPROVED' ? '批准后将结束指导并保留退赛记录' : '请填写驳回原因', '退赛审核', { inputType: 'textarea', inputValidator: v => result === 'APPROVED' || !!v?.trim() || '请填写原因' })
    await reviewWithdrawal(row.id, result, value)
    await openDetail(detail.value); await fetchList()
  } catch (error) { if (error !== 'cancel' && error !== 'close') throw error }
}

function handleApplySubmit() {
  applyFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (currentCompetition.value) {
      const { competitionType, maxMembers } = currentCompetition.value
      if (competitionType === 'PERSONAL' && applyForm.memberIds.length > 0) {
        ElMessage.warning('个人赛不能添加成员')
        return
      }
      if (competitionType === 'TEAM' && applyForm.memberIds.length + 1 > maxMembers) {
        ElMessage.warning(`团队成员数量超过上限（${maxMembers}人）`)
        return
      }
    }
    submitting.value = true
    try {
      const payload = {
        competitionId: applyForm.competitionId,
        preferredTeacherId: applyForm.preferredTeacherId,
        teamName: applyForm.teamName,
        memberIds: applyForm.memberIds
      }
      if (applyForm.id) {
        await updateRegistration(applyForm.id, payload)
      } else {
        await createRegistration(payload)
      }
      ElMessage.success(applyForm.memberIds.length ? '草稿已保存，请等待成员确认后正式提交' : '保存成功，请查看状态并正式提交')
      applyVisible.value = false
      fetchList()
    } finally {
      submitting.value = false
    }
  })
}

function handleCancel(row) {
  ElMessageBox.confirm(`确定取消报名「${row.teamName}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await cancelRegistration(row.id)
      ElMessage.success('已取消报名')
      fetchList()
    })
    .catch(() => {})
}

let reviewVersion = 0
async function openReview(row, action) {
  const version = ++reviewVersion
  reviewTeachersLoading.value = false
  reviewId.value = row.id
  reviewAction.value = action
  reviewComment.value = ''
  reviewTeacherId.value = null
  reviewPreferredId.value = row.preferredTeacherId
  reviewPreferredName.value = row.preferredTeacherName
  reviewTeachers.value = []
  reviewVisible.value = true
  if (action === 'APPROVED') {
    reviewTeachersLoading.value = true
    try {
      const res = await competitionTeachers(row.competitionId)
      if (version !== reviewVersion || !reviewVisible.value) return
      reviewTeachers.value = res.data
      if (res.data.some(t => t.teacherId === row.preferredTeacherId && t.remaining > 0)) reviewTeacherId.value = row.preferredTeacherId
    } finally { if (version === reviewVersion) reviewTeachersLoading.value = false }
  }
}

async function handleReviewSubmit() {
  if (reviewAction.value === 'APPROVED' && !reviewTeacherId.value) { ElMessage.warning('请确认最终指导教师'); return }
  if (reviewAction.value === 'APPROVED' && reviewPreferredId.value && reviewTeacherId.value !== reviewPreferredId.value && !reviewComment.value.trim()) {
    ElMessage.warning('调剂教师时请填写原因'); return
  }
  if (reviewAction.value === 'REJECTED' && !reviewComment.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  submitting.value = true
  try {
    await reviewRegistration(reviewId.value, { result: reviewAction.value, comment: reviewComment.value, teacherId: reviewTeacherId.value })
    ElMessage.success('审核完成')
    reviewVisible.value = false
    fetchList()
  } finally {
    submitting.value = false
  }
}

watch(() => [route.query.detail, route.query.open], ([id]) => { if (id) openDetail({ id: Number(id) }) })
watch(() => route.query.invitation, () => loadInvitations())
onMounted(() => {
  fetchList()
  loadInvitations()
  if (route.query.detail) openDetail({ id: Number(route.query.detail) })
  const cid = route.query.competitionId
  if (cid && isStudent.value) {
    openApplyWithCompetition(Number(cid))
  }
})
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

.tip-text {
  color: var(--color-text-secondary);
  font-size: var(--font-size-small);
}

.member-row {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-right: var(--spacing-md);
  margin-bottom: var(--spacing-xs);
}
</style>

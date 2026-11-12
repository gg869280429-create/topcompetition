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
      <p>首次报名接受邀请后由队长正式提交；已通过报名接受邀请后直接加入。待确认邀请不占用参赛资格。</p>
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
        <el-table-column label="指导老师" min-width="130"><template #default="{ row }">{{ row.guidanceTeacherNames || '待分配' }}</template></el-table-column>
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
              <el-button v-if="row.canAdjustMembers" link type="primary" @click="openRoster(row)">调整队员</el-button>
              <el-button v-if="row.status === 'APPROVED'" link type="danger" @click="reasonAction(row, 'withdraw')">申请退赛</el-button>
              <el-button v-if="['CANCELLED', 'WITHDRAWN'].includes(row.status)" link type="primary" @click="reasonAction(row, 'reapply')">重新申请</el-button>
            </template>
            <template v-if="isAdmin && row.status === 'PENDING'">
              <el-button link type="success" @click="openReview(row, 'APPROVED')">通过</el-button>
              <el-button link type="danger" @click="openReview(row, 'REJECTED')">驳回</el-button>
            </template>
            <el-button v-if="isAdmin && row.status === 'APPROVED'" link type="primary" @click="openTeacherAssignment(row)">选择指导老师</el-button>
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
    <el-dialog v-model="rosterVisible" title="调整队员" width="min(560px,95vw)" :close-on-click-modal="false" :close-on-press-escape="!rosterSaving" :show-close="!rosterSaving">
      <el-alert title="新增队员须本人接受邀请后加入，无需管理员重新审核；指导老师确认参赛后名单锁定。" type="info" :closable="false" />
      <el-form label-width="90px" style="margin-top:20px" @submit.prevent>
        <el-form-item label="团队">{{ rosterRow.teamName }}</el-form-item>
        <el-form-item label="队员">
          <el-select v-model="rosterMemberIds" multiple filterable remote :remote-method="searchRosterStudents" :loading="rosterSearching" :disabled="rosterSaving" placeholder="搜索并邀请学生，不含队长" style="width:100%"><el-option v-for="s in rosterStudents" :key="s.id" :value="s.id" :label="s.realName || s.username" :disabled="s.id === currentUserId" /></el-select>
        </el-form-item>
        <el-form-item label="调整说明"><el-input v-model="rosterReason" type="textarea" :rows="3" maxlength="1000" :disabled="rosterSaving" /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="rosterSaving" @click="rosterVisible=false">取消</el-button><el-button type="primary" :loading="rosterSaving" @click="saveRoster">保存并发送邀请</el-button></template>
    </el-dialog>

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
        <el-form-item label="指导老师" prop="preferredTeacherId">
          <el-select v-model="applyForm.preferredTeacherId" placeholder="请选择一位指导老师" :loading="teachersLoading" :disabled="teachersLoading || !applyForm.competitionId" style="width:100%">
            <el-option v-for="t in teacherOptions" :key="t.teacherId" :value="t.teacherId" :label="`${t.teacherName}（${t.majorName || '专业未设置'}，剩余${t.remaining}个名额）`" />
          </el-select>
          <div class="tip-text">指导老师由管理员审核确认，确认前不占用指导名额。</div>
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
        <el-descriptions-item label="指导老师">{{ detail.guidanceTeacherNames || '待分配' }}</el-descriptions-item>
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
          <el-button v-if="isStudent && detail.leaderId === currentUserId && ((detail.status === 'DRAFT' && ['PENDING','ACCEPTED','REJECTED'].includes(row.status)) || (detail.canAdjustMembers && ['PENDING','REJECTED'].includes(row.status)))" link type="danger" @click="revoke(row)">撤销</el-button>
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

    <el-dialog v-model="assignmentVisible" title="选择指导老师" width="540px" :close-on-click-modal="false" :close-on-press-escape="!assignmentSaving" :show-close="!assignmentSaving" @closed="assignmentVersion++">
      <el-form v-loading="assignmentLoading" label-width="100px" @submit.prevent>
        <el-form-item label="报名团队">{{ assignmentRow.teamName }}</el-form-item>
        <el-form-item label="当前指导老师">{{ assignmentCurrent.map(t => t.teacherName).join('、') || '待分配' }}</el-form-item>
        <el-form-item v-if="assignmentCurrent.length > 1" label="调整对象" required>
          <el-select v-model="assignmentForm.guidanceId" :disabled="assignmentSaving" style="width:100%"><el-option v-for="g in assignmentCurrent" :key="g.id" :value="g.id" :label="g.teacherName" /></el-select>
        </el-form-item>
        <el-form-item label="指导老师" required>
          <el-select v-model="assignmentForm.teacherId" placeholder="请选择指导老师" :disabled="assignmentSaving || !assignmentReady" style="width:100%">
            <el-option v-for="t in assignmentTeachers" :key="t.teacherId" :value="t.teacherId" :disabled="t.remaining <= 0 || assignmentCurrent.some(g => g.teacherId === t.teacherId)" :label="`${t.teacherName}（剩余${t.remaining}个名额）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="分配说明" :required="assignmentReasonRequired"><el-input v-model="assignmentForm.comment" type="textarea" :rows="3" maxlength="255" show-word-limit placeholder="调整指导老师时，请填写原因" :disabled="assignmentSaving" /></el-form-item>
        <el-alert v-if="assignmentClosed" title="竞赛已结束或已取消，不能分配或调整指导老师。" type="warning" :closable="false" />
      </el-form>
      <template #footer>
        <el-button :disabled="assignmentSaving" @click="assignmentVisible = false">取消</el-button>
        <el-button type="primary" :loading="assignmentSaving" :disabled="assignmentLoading || !assignmentReady" @click="saveTeacherAssignment">确认分配</el-button>
      </template>
    </el-dialog>

    <!-- 审核弹窗 -->
    <el-dialog
      v-model="reviewVisible"
      :title="reviewAction === 'APPROVED' ? '通过报名' : '驳回报名'"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form label-width="90px">
        <el-form-item v-if="reviewAction === 'APPROVED'" label="指导老师">
          <el-select v-model="reviewTeacherId" :loading="reviewTeachersLoading" style="width:100%" placeholder="确认指导老师">
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
  revokeInvitation, registrationHistory, reapplyRegistration, requestWithdrawal, reviewWithdrawal, updateTeamMembers
} from '@/api/registration'
import { listStudents } from '@/api/user'
import { competitionTeachers, listGuidance, assignGuidance, replaceGuidance } from '@/api/guidance'
import { COMPETITION_TYPE, REGISTRATION_STATUS } from '@/constants/dict'

const userStore = useUserStore()
const route = useRoute()
const isTeacher = computed(() => userStore.role === 'TEACHER')
const isStudent = computed(() => userStore.role === 'STUDENT')
const isAdmin = computed(() => userStore.role === 'ADMIN' || userStore.role === 'MAJOR_ADMIN')
const currentUserId = computed(() => userStore.userInfo?.id)
const rosterVisible = ref(false), rosterSaving = ref(false), rosterSearching = ref(false), rosterRow = ref({})
const rosterMemberIds = ref([]), rosterStudents = ref([]), rosterReason = ref('')
async function openRoster(row) {
  try {
    const [registration, competition, record] = await Promise.all([getRegistration(row.id), getCompetition(row.competitionId), registrationHistory(row.id)])
    if (!registration.data.canAdjustMembers || ['ENDED','CANCELLED'].includes(competition.data.status)) { ElMessage.warning('当前团队名单不能调整'); return }
    if (competition.data.competitionType !== 'TEAM') { ElMessage.warning('个人赛不能调整队员'); return }
    rosterRow.value = registration.data
    const members = (registration.data.members || []).filter(m => m.userId !== currentUserId.value)
    const pending = (record.data.invitations || []).filter(i => i.status === 'PENDING')
    rosterMemberIds.value = [...new Set([...members.map(m => m.userId), ...pending.map(i => i.inviteeId)])]
    rosterStudents.value = [...members.map(m => ({ id: m.userId, realName: m.name })), ...pending.map(i => ({ id: i.inviteeId, realName: `待确认学生 ${i.inviteeId}` }))]
    rosterReason.value = ''
    rosterVisible.value = true
  } catch { return }
}
async function searchRosterStudents(keyword) {
  rosterSearching.value = true
  try { const response = await listStudents(keyword); rosterStudents.value = [...new Map([...rosterStudents.value, ...response.data].map(s => [s.id, s])).values()] }
  finally { rosterSearching.value = false }
}
async function saveRoster() {
  if (rosterSaving.value) return
  rosterSaving.value = true
  try {
    await updateTeamMembers(rosterRow.value.id, { memberIds: rosterMemberIds.value, reason: rosterReason.value })
    rosterVisible.value = false
    ElMessage.success('名单已保存，新增队员接受邀请后加入')
    await fetchList()
    if (detailVisible.value && detail.value.id === rosterRow.value.id) await openDetail(detail.value)
  } catch { return }
  finally { rosterSaving.value = false }
}

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
const reviewTeachers = ref([])
const reviewTeachersLoading = ref(false)
const reviewTeacherId = ref(null)
const reviewPreferredId = ref(null)
const assignmentVisible = ref(false)
const assignmentLoading = ref(false)
const assignmentSaving = ref(false)
const assignmentReady = ref(false)
const assignmentClosed = ref(false)
const assignmentRow = ref({})
const assignmentCurrent = ref([])
const assignmentTeachers = ref([])
const assignmentForm = reactive({ guidanceId: null, teacherId: null, comment: '' })
const assignmentReasonRequired = computed(() => !!assignmentForm.guidanceId || !!(assignmentRow.value.preferredTeacherId && assignmentForm.teacherId && assignmentRow.value.preferredTeacherId !== assignmentForm.teacherId))
let assignmentVersion = 0

async function openTeacherAssignment(row) {
  const version = ++assignmentVersion
  assignmentRow.value = row
  assignmentCurrent.value = []
  assignmentTeachers.value = []
  Object.assign(assignmentForm, { guidanceId: null, teacherId: null, comment: '' })
  assignmentReady.value = false
  assignmentClosed.value = false
  assignmentLoading.value = true
  assignmentVisible.value = true
  try {
    const [registration, competition, current, teachers] = await Promise.all([getRegistration(row.id), getCompetition(row.competitionId), listGuidance(row.id), competitionTeachers(row.competitionId)])
    if (version !== assignmentVersion || !assignmentVisible.value) return
    assignmentRow.value = registration.data
    assignmentClosed.value = ['ENDED', 'CANCELLED'].includes(competition.data.status)
    assignmentReady.value = !assignmentClosed.value && registration.data.status === 'APPROVED'
    assignmentCurrent.value = current.data.filter(g => g.status === 'ACTIVE')
    assignmentTeachers.value = teachers.data
    assignmentForm.guidanceId = assignmentCurrent.value[0]?.id || null
    if (!assignmentForm.guidanceId && teachers.data.some(t => t.teacherId === registration.data.preferredTeacherId && t.remaining > 0)) assignmentForm.teacherId = registration.data.preferredTeacherId
  } catch { return }
  finally { if (version === assignmentVersion) assignmentLoading.value = false }
}

async function saveTeacherAssignment() {
  if (assignmentSaving.value || !assignmentReady.value) return
  if (!assignmentForm.teacherId) { ElMessage.warning('请选择指导老师'); return }
  if (assignmentReasonRequired.value && !assignmentForm.comment.trim()) { ElMessage.warning('请填写调剂原因'); return }
  assignmentSaving.value = true
  try {
    const payload = { registrationId: assignmentRow.value.id, teacherId: assignmentForm.teacherId, comment: assignmentForm.comment.trim() }
    if (assignmentForm.guidanceId) await replaceGuidance(assignmentForm.guidanceId, payload)
    else await assignGuidance(payload)
    ElMessage.success('指导老师已确认')
    assignmentVisible.value = false
    if (detailVisible.value && detail.value.id === payload.registrationId) await openDetail(detail.value)
    await fetchList()
  } catch { return }
  finally { assignmentSaving.value = false }
}

const invitations = ref([])
const workflow = ref({})
const detailVisible = ref(false)
const detail = ref({})

const reviewVisible = ref(false)
const reviewId = ref(null)
const reviewAction = ref('APPROVED')
const reviewComment = ref('')

const applyRules = {
  preferredTeacherId: [{ required: true, message: '请选择一位指导老师', trigger: 'change' }],
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
  detailVisible.value = true
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
  const titles = { withdraw: '申请退赛，由管理员审核', reapply: '重新申请仅限报名期，队友需重新确认' }
  try {
    const { value } = await ElMessageBox.prompt(titles[action], '请填写原因', { inputType: 'textarea', inputValidator: v => !!v?.trim() || '请填写原因' })
    const api = { withdraw: requestWithdrawal, reapply: reapplyRegistration }[action]
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
  if (reviewAction.value === 'APPROVED' && !reviewTeacherId.value) { ElMessage.warning('请确认指导老师'); return }
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

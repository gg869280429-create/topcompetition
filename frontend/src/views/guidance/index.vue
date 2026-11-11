<template>
  <div>
    <PageHeader title="指导管理" />

    <PageContainer>
      <!-- 教师视角：我的指导任务 -->
      <template v-if="isTeacher">
        <h3>我的指导竞赛</h3>
        <el-table :data="myCompetitions" border stripe>
          <el-table-column prop="competitionTitle" label="竞赛名称" min-width="180" />
          <el-table-column prop="quota" label="指导名额" width="110" />
          <el-table-column prop="assigned" label="已分配团队" width="120" />
          <el-table-column prop="remaining" label="剩余名额" width="110" />
        </el-table>
        <el-pagination v-model:current-page="competitionQuery.current" :page-size="competitionQuery.size" :total="competitionTotal" layout="total, prev, pager, next" @current-change="loadMyCompetitions" style="margin:16px 0" />
        <h3>我的指导团队</h3>
        <el-table v-loading="mineLoading" :data="mineList" border stripe>
          <el-table-column prop="competitionTitle" label="竞赛名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="teamName" label="团队名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="assignTime" label="分配时间" width="170" />
          <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="router.push({ path: '/registration', query: { detail: row.registrationId } })">团队详情</el-button></template></el-table-column>
        </el-table>
        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="mineQuery.current"
            v-model:page-size="mineQuery.size"
            :total="mineTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @size-change="loadMine"
            @current-change="loadMine"
          />
        </div>
      </template>

      <!-- 管理员视角：名额管理 + 师生匹配 -->
      <el-tabs v-else v-model="activeTab">
        <el-tab-pane label="指导名额" name="quota">
          <el-form :inline="true" class="search-form" @submit.prevent>
            <el-form-item label="竞赛">
              <el-select v-model="quotaCompetitionId" placeholder="请选择竞赛" filterable style="width: 260px" @change="loadQuota">
                <el-option v-for="c in competitions" :key="c.id" :label="c.title" :value="c.id" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :disabled="!quotaCompetitionId" @click="openQuotaDialog()">设置名额</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="quotaLoading" :data="quotaList" border stripe>
            <el-table-column prop="teacherName" label="教师" min-width="150" show-overflow-tooltip />
            <el-table-column prop="quota" label="指导名额" width="120" />
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button link type="primary" @click="openQuotaDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="handleDeleteQuota(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="quotaQuery.current"
              v-model:page-size="quotaQuery.size"
              :total="quotaTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadQuota"
              @current-change="loadQuota"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="师生匹配" name="match">
          <el-form :inline="true" class="search-form" @submit.prevent>
            <el-form-item label="竞赛">
              <el-select v-model="matchCompetitionId" placeholder="请选择竞赛" filterable style="width: 260px" @change="handleMatchSearch">
                <el-option v-for="c in competitions" :key="c.id" :label="c.title" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-form>
          <el-table v-loading="matchLoading" :data="matchList" border stripe>
            <el-table-column prop="teamName" label="团队名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="leaderName" label="队长" width="110" show-overflow-tooltip />
            <el-table-column label="指导教师" min-width="180">
              <template #default="{ row }">
                <template v-if="row.guidances?.length">
                  <el-tag v-for="t in row.guidances" :key="t.id" class="guidance-tag">{{ t.teacherName }}</el-tag>
                </template>
                <span v-else class="empty-text">未分配</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button link type="primary" @click="openAssignDialog(row)">分配指导</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="matchQuery.current"
              v-model:page-size="matchQuery.size"
              :total="matchTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadMatchList"
              @current-change="loadMatchList"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </PageContainer>

    <!-- 名额设置弹窗 -->
    <el-dialog v-model="quotaDialogVisible" :title="quotaForm.id ? '编辑名额' : '设置名额'" width="480px" :close-on-click-modal="false" @close="resetQuotaForm">
      <el-form ref="quotaFormRef" :model="quotaForm" :rules="quotaRules" label-width="80px">
        <el-form-item label="教师" prop="teacherId">
          <el-select
            v-if="!quotaForm.id"
            v-model="quotaForm.teacherId"
            placeholder="搜索并选择教师"
            filterable
            remote
            :remote-method="searchTeachers"
            :loading="teacherLoading"
            style="width: 100%"
          >
            <el-option v-for="t in teachers" :key="t.id" :label="t.realName || t.username" :value="t.id" />
          </el-select>
          <el-input v-else :model-value="quotaForm.teacherName" disabled />
        </el-form-item>
        <el-form-item label="名额" prop="quota">
          <el-input-number v-model="quotaForm.quota" :min="0" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quotaDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleQuotaSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配指导弹窗 -->
    <el-dialog v-model="assignDialogVisible" :title="`分配指导 - ${assignRow.teamName || ''}`" width="520px" :close-on-click-modal="false">
      <div class="assign-current">
        <div class="assign-current-title">当前指导教师</div>
        <template v-if="assignRow.guidances?.length">
          <el-tag
            v-for="t in assignRow.guidances"
            :key="t.id"
            class="guidance-tag"
          >{{ t.teacherName }}</el-tag>
        </template>
        <span v-else class="empty-text">未分配</span>
      </div>
      <el-divider />
      <el-form label-width="90px" @submit.prevent>
        <el-form-item v-if="assignRow.guidances?.length" label="调整对象">
          <el-select v-model="replaceId" style="width:100%"><el-option v-for="g in assignRow.guidances" :key="g.id" :value="g.id" :label="g.teacherName" /></el-select>
        </el-form-item>
        <el-form-item label="指导教师">
          <el-select
            v-model="assignTeacherId"
            placeholder="选择本竞赛教师"
            filterable
            :loading="assignLoading"
            style="width:100%"
          >
            <el-option v-for="t in assignTeachers" :key="t.teacherId" :label="`${t.teacherName}（剩余${t.remaining}个名额）`" :value="t.teacherId" :disabled="t.remaining <= 0 || assignRow.guidances?.some(g => g.teacherId === t.teacherId)" />
          </el-select>
        </el-form-item>
        <el-form-item label="调整原因"><el-input v-model="assignComment" type="textarea" maxlength="255" placeholder="调剂教师时必填" /></el-form-item>
        <el-button type="primary" :loading="submitting" :disabled="assignLoading" @click="handleAssign">{{ replaceId ? '确认调剂' : '确认分配' }}</el-button>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PageContainer from '@/components/PageContainer.vue'
import { useUserStore } from '@/store/user'
import { pageCompetition } from '@/api/competition'
import { pageRegistration } from '@/api/registration'
import { listTeachers } from '@/api/user'
import {
  pageQuota,
  saveQuota,
  deleteQuota,
  listGuidance,
  assignGuidance,
  replaceGuidance,
  competitionTeachers,
  pageMyGuidance,
  pageMyCompetitions
} from '@/api/guidance'

const userStore = useUserStore()
const isTeacher = computed(() => userStore.role === 'TEACHER')

const activeTab = ref('quota')
const competitions = ref([])
const submitting = ref(false)
const teacherLoading = ref(false)
const teachers = ref([])

// 教师视角
const mineLoading = ref(false)
const mineList = ref([])
const mineTotal = ref(0)
const mineQuery = reactive({ current: 1, size: 10 })
const myCompetitions = ref([])
const competitionTotal = ref(0)
const competitionQuery = reactive({ current: 1, size: 10 })
async function loadMyCompetitions() {
  const res = await pageMyCompetitions(competitionQuery)
  myCompetitions.value = res.data.records
  competitionTotal.value = res.data.total
}

// 名额管理
const quotaCompetitionId = ref(null)
const quotaLoading = ref(false)
const quotaList = ref([])
const quotaTotal = ref(0)
const quotaQuery = reactive({ current: 1, size: 10 })
const quotaDialogVisible = ref(false)
const quotaFormRef = ref()
const quotaForm = reactive({ id: null, competitionId: null, teacherId: null, teacherName: '', quota: 1 })
const quotaRules = {
  teacherId: [{ required: true, message: '请选择教师', trigger: 'change' }],
  quota: [{ required: true, message: '请输入名额', trigger: 'blur' }]
}

// 师生匹配
const matchCompetitionId = ref(null)
const matchLoading = ref(false)
const matchList = ref([])
const matchTotal = ref(0)
const matchQuery = reactive({ current: 1, size: 10 })
const assignDialogVisible = ref(false)
const assignRow = ref({})
const assignTeacherId = ref(null)
const replaceId = ref(null)
const assignComment = ref('')
const assignTeachers = ref([])
const assignLoading = ref(false)
let assignVersion = 0

async function loadCompetitions() {
  const res = await pageCompetition({ current: 1, size: 100 })
  competitions.value = res.data.records
}

// ---- 教师视角 ----
async function loadMine() {
  mineLoading.value = true
  try {
    const res = await pageMyGuidance(mineQuery)
    mineList.value = res.data.records
    mineTotal.value = res.data.total
  } finally {
    mineLoading.value = false
  }
}

// ---- 名额管理 ----
let quotaVersion = 0
async function loadQuota() {
  const version = ++quotaVersion
  if (!quotaCompetitionId.value) {
    quotaList.value = []
    quotaTotal.value = 0
    return
  }
  quotaLoading.value = true
  try {
    const res = await pageQuota({ competitionId: quotaCompetitionId.value, current: quotaQuery.current, size: quotaQuery.size })
    if (version !== quotaVersion) return
    quotaList.value = res.data.records
    quotaTotal.value = res.data.total
  } finally {
    if (version === quotaVersion) quotaLoading.value = false
  }
}

async function searchTeachers(keyword) {
  teacherLoading.value = true
  try {
    const res = await listTeachers(keyword)
    teachers.value = res.data
  } finally {
    teacherLoading.value = false
  }
}

function resetQuotaForm() {
  quotaForm.id = null
  quotaForm.competitionId = null
  quotaForm.teacherId = null
  quotaForm.teacherName = ''
  quotaForm.quota = 1
  teachers.value = []
  quotaFormRef.value?.clearValidate()
}

function openQuotaDialog(row) {
  resetQuotaForm()
  quotaForm.competitionId = row?.competitionId || quotaCompetitionId.value
  if (row) {
    quotaForm.id = row.id
    quotaForm.teacherId = row.teacherId
    quotaForm.teacherName = row.teacherName
    quotaForm.quota = row.quota
  }
  quotaDialogVisible.value = true
}

function handleQuotaSubmit() {
  quotaFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await saveQuota({
        competitionId: quotaForm.competitionId,
        teacherId: quotaForm.teacherId,
        quota: quotaForm.quota
      })
      ElMessage.success('保存成功')
      quotaDialogVisible.value = false
      loadQuota()
    } finally {
      submitting.value = false
    }
  })
}

function handleDeleteQuota(row) {
  ElMessageBox.confirm(`确定删除教师「${row.teacherName}」的名额配置吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteQuota(row.id)
      ElMessage.success('删除成功')
      loadQuota()
    })
    .catch(() => {})
}

// ---- 师生匹配 ----
function handleMatchSearch() {
  matchQuery.current = 1
  loadMatchList()
}

let matchVersion = 0
async function loadMatchList() {
  const version = ++matchVersion
  if (!matchCompetitionId.value) {
    matchList.value = []
    matchTotal.value = 0
    return
  }
  matchLoading.value = true
  try {
    const res = await pageRegistration({
      competitionId: matchCompetitionId.value,
      status: 'APPROVED',
      current: matchQuery.current,
      size: matchQuery.size
    })
    const records = res.data.records
    await Promise.all(
      records.map(async (r) => {
        const g = await listGuidance(r.id)
        r.guidances = g.data.filter(item => item.status === 'ACTIVE')
      })
    )
    if (version !== matchVersion) return
    matchList.value = records
    matchTotal.value = res.data.total
  } finally {
    if (version === matchVersion) matchLoading.value = false
  }
}

async function openAssignDialog(row) {
  const version = ++assignVersion
  assignRow.value = row
  assignTeacherId.value = null
  replaceId.value = row.guidances?.[0]?.id || null
  assignComment.value = ''
  assignTeachers.value = []
  assignDialogVisible.value = true
  assignLoading.value = true
  try {
    const res = await competitionTeachers(row.competitionId)
    if (version === assignVersion && assignDialogVisible.value) assignTeachers.value = res.data
  } finally { if (version === assignVersion) assignLoading.value = false }
}

async function handleAssign() {
  if (!assignTeacherId.value) { ElMessage.warning('请选择教师'); return }
  if ((replaceId.value || (assignRow.value.preferredTeacherId && assignRow.value.preferredTeacherId !== assignTeacherId.value)) && !assignComment.value.trim()) {
    ElMessage.warning('请填写调剂原因'); return
  }
  submitting.value = true
  try {
    const payload = { registrationId: assignRow.value.id, teacherId: assignTeacherId.value, comment: assignComment.value }
    if (replaceId.value) await replaceGuidance(replaceId.value, payload)
    else await assignGuidance(payload)
    ElMessage.success('指导安排已更新')
    assignDialogVisible.value = false
    await loadMatchList()
  } finally { submitting.value = false }
}

onMounted(async () => {
  if (isTeacher.value) {
    loadMyCompetitions()
    loadMine()
  } else {
    await loadCompetitions()
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

.guidance-tag {
  margin-right: var(--spacing-xs);
  margin-bottom: var(--spacing-xs);
}

.empty-text {
  color: var(--color-text-placeholder);
  font-size: var(--font-size-small);
}

.assign-current {
  margin-bottom: var(--spacing-xs);
}

.assign-current-title {
  margin-bottom: var(--spacing-sm);
  font-weight: 600;
}
</style>

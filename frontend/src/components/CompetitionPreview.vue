<template>
  <div>
   
      <el-empty v-if="!loading && list.length === 0" description="暂无竞赛信息" />
      <el-row v-else :gutter="16">
        <el-col v-for="c in list" :key="c.id" :xs="24" :sm="12" :md="8" :lg="6">
          <el-card class="comp-card" shadow="hover">
            <div class="comp-title">{{ c.title }}</div>
            <div class="comp-tags">
              <el-tag size="small" type="info"><MajorLabel :name="c.majorName" :major-id="c.majorId" :icon-code="c.majorIconCode" :logo-file-id="c.majorLogoFileId" /></el-tag>
              <el-tag size="small">{{ COMPETITION_LEVEL[c.level] || c.level }}</el-tag>
              <el-tag size="small" :type="c.competitionType === 'TEAM' ? 'primary' : 'success'">{{ COMPETITION_TYPE[c.competitionType] || c.competitionType }}</el-tag>
              <el-tag size="small" :type="statusTagType(c.status)">{{ COMPETITION_STATUS[c.status] || c.status }}</el-tag>
            </div>
            <div class="comp-time">
              {{ c.registrationStartTime || '-' }} ~ {{ c.registrationEndTime || '-' }}
            </div>
            <div class="comp-actions">
              <el-button size="small" @click="openDetail(c)">详情</el-button>
              <el-button v-if="canManage(c)" size="small" :disabled="['ENDED','CANCELLED'].includes(c.status)" @click="router.push({path:'/competition',query:{edit:c.id,open:Date.now()}})">编辑</el-button>
              <el-button v-if="canManage(c)" size="small" type="danger" :disabled="['ENDED','CANCELLED'].includes(c.status)" @click="cancelCompetition(c)">取消竞赛</el-button>
              <el-button v-if="isStudent && c.status === 'REGISTERING'" size="small" type="success" @click="goApply(c)">报名参赛</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-pagination
        v-model:current-page="query.current"
        :page-size="query.size"
        :total="total"
        :page-sizes="[12, 24, 48]"
        :pager-count="5"
        layout="total, sizes, prev, pager, next, jumper"
        @update:page-size="changePageSize"
        @change="fetchList"
      />

    <el-dialog v-model="detailVisible" title="竞赛详情" width="50%">
      <CompetitionDetailContent v-if="detailVisible" :competition="detail" />
    </el-dialog>
  </div>
</template>

<script setup>
import MajorLabel from '@/components/MajorLabel.vue'
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import PageContainer from '@/components/PageContainer.vue'
import { useUserStore } from '@/store/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { updateCompetitionStatus, pageCompetition, getCompetition } from '@/api/competition'
import CompetitionDetailContent from '@/components/CompetitionDetailContent.vue'
import { COMPETITION_TYPE, COMPETITION_LEVEL, COMPETITION_STATUS } from '@/constants/dict'

defineProps({ title: { type: String, default: '竞赛预览' } })

const userStore = useUserStore()
const router = useRouter()

const isStudent = computed(() => userStore.role === 'STUDENT')

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 12 })

function changePageSize(size) {
  query.size = size
  query.current = 1
}

const detailVisible = ref(false)
const detail = ref({})
const hoverDetail = ref({})

async function loadHoverDetail(c) {
  hoverDetail.value = c
  const res = await getCompetition(c.id)
  if (hoverDetail.value.id === c.id) hoverDetail.value = res.data
}

function canManage(c) { return userStore.role === 'ADMIN' || (userStore.role === 'MAJOR_ADMIN' && (c.publisherId === userStore.userInfo?.id || c.managerId === userStore.userInfo?.id)) }
async function cancelCompetition(c) {
 try {
  const { value } = await ElMessageBox.prompt(`填写取消「${c.title}」的原因`, '取消竞赛', { inputValidator: v => !!v?.trim() && v.trim().length <= 1000 || '请填写1至1000字原因' })
  await ElMessageBox.confirm('确认取消竞赛并通知相关人员？', '确认取消', { type:'warning' })
  await updateCompetitionStatus(c.id, 'CANCELLED', value.trim()); ElMessage.success('已取消竞赛'); await fetchList()
 } catch(e) { if(e !== 'cancel' && e !== 'close') throw e }
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

async function openDetail(c) {
  try {
    detail.value = (await getCompetition(c.id)).data
    detailVisible.value = true
  } catch { await fetchList() }
}

function goApply(c) {
  router.push({ path: '/registration', query: { competitionId: c.id } })
}

onMounted(fetchList)
</script>

<style scoped>

.comp-card {
  margin-bottom: var(--spacing-md);
}

.comp-card :deep(.el-card__body) {
  box-sizing: border-box;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.comp-title,
.comp-time,
.comp-desc {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 0;
}

.comp-title {
  font-size: var(--font-size-large);
  font-weight: 600;
  margin-bottom: var(--spacing-sm);
  line-height: 1.4;
}

.comp-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-xs);
  flex-shrink: 0;
  align-content: flex-start;
  overflow: hidden;
  margin-bottom: var(--spacing-sm);
}

.comp-tags :deep(.el-tag) {
  max-width: 100%;
  min-width: 0;
}

.comp-tags :deep(.el-tag__content),
.comp-tags :deep(.major-label > span) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.comp-time {
  font-size: var(--font-size-small);
  line-height: 1.5;
  margin-bottom: var(--spacing-sm);
}

.comp-desc {
  font-size: var(--font-size-small);
  line-height: 1.5;
  margin-bottom: var(--spacing-sm);
}

.comp-actions {
  align-content: flex-end;
  display: flex;
  gap: var(--spacing-xs);
  flex-wrap: wrap;
}

.comp-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.comp-hover-detail {
  max-height: min(60vh, 560px);
  overflow-y: auto;
  padding-right: 4px;
}

.comp-hover-heading {
  margin: 0 0 12px;
  font-size: var(--font-size-large);
  color: var(--color-text-primary);
}

</style>

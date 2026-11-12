<template>
  <div>
    <PageHeader :title="title" />
    <PageContainer v-loading="loading">
      <el-empty v-if="!loading && list.length === 0" description="暂无竞赛信息" />
      <el-row v-else :gutter="16">
        <el-col v-for="c in list" :key="c.id" :xs="24" :sm="12" :md="8" :lg="6">
          <el-card class="comp-card" shadow="hover">
            <div class="comp-title">{{ c.title }}</div>
            <div class="comp-tags">
              <el-tag size="small" type="info">{{ c.majorName || '待补录专业' }}</el-tag>
              <el-tag size="small">{{ COMPETITION_LEVEL[c.level] || c.level }}</el-tag>
              <el-tag size="small" type="info">{{ COMPETITION_TYPE[c.competitionType] || c.competitionType }}</el-tag>
              <el-tag size="small" :type="statusTagType(c.status)">{{ COMPETITION_STATUS[c.status] || c.status }}</el-tag>
            </div>
            <div v-if="c.registrationStartTime || c.registrationEndTime" class="comp-time">
              报名：{{ c.registrationStartTime || '-' }} ~ {{ c.registrationEndTime || '-' }}
            </div>
            <div class="comp-desc">{{ c.description || '暂无简介' }}</div>
            <div class="comp-actions">
              <el-button size="small" @click="openDetail(c)">详情</el-button>
              <el-button v-if="canManage(c)" size="small" :disabled="['ENDED','CANCELLED'].includes(c.status)" @click="router.push({path:'/competition',query:{edit:c.id,open:Date.now()}})">编辑</el-button>
              <el-button v-if="canManage(c)" size="small" type="danger" :disabled="['ENDED','CANCELLED'].includes(c.status)" @click="cancelCompetition(c)">取消竞赛</el-button>
              <el-button v-if="isStudent && c.status === 'REGISTERING'" size="small" type="success" @click="goApply(c)">报名参赛</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </PageContainer>

    <el-dialog v-model="detailVisible" title="竞赛详情" width="600px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="竞赛名称" :span="2">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="赛制">{{ COMPETITION_TYPE[detail.competitionType] || detail.competitionType }}</el-descriptions-item>
        <el-descriptions-item label="级别">{{ COMPETITION_LEVEL[detail.level] || detail.level }}</el-descriptions-item>
        <el-descriptions-item label="所属专业">{{ detail.majorName || '待补录' }}</el-descriptions-item>
        <el-descriptions-item label="学科类别">{{ detail.category || '-' }}</el-descriptions-item>
        <el-descriptions-item label="主办单位">{{ detail.organizer || '-' }}</el-descriptions-item>
        <el-descriptions-item label="团队人数上限">{{ detail.maxMembers }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ COMPETITION_STATUS[detail.status] || detail.status }}</el-descriptions-item>
        <el-descriptions-item label="报名时间" :span="2">
          {{ (detail.registrationStartTime || '-') + ' ~ ' + (detail.registrationEndTime || '-') }}
        </el-descriptions-item>
        <el-descriptions-item label="竞赛简介" :span="2">{{ detail.description || '-' }}</el-descriptions-item>
      </el-descriptions>
      <CompetitionResources v-if="detailVisible" :competition="detail" />
    </el-dialog>


  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import PageContainer from '@/components/PageContainer.vue'
import { useUserStore } from '@/store/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { updateCompetitionStatus, pageCompetition } from '@/api/competition'
import CompetitionResources from '@/components/CompetitionResources.vue'
import { COMPETITION_TYPE, COMPETITION_LEVEL, COMPETITION_STATUS } from '@/constants/dict'

defineProps({ title: { type: String, default: '竞赛预览' } })

const userStore = useUserStore()
const router = useRouter()

const isStudent = computed(() => userStore.role === 'STUDENT')

const loading = ref(false)
const list = ref([])

const detailVisible = ref(false)
const detail = ref({})

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
    const res = await pageCompetition({ current: 1, size: 100 })
    list.value = res.data.records
  } finally {
    loading.value = false
  }
}

function openDetail(c) {
  detail.value = c
  detailVisible.value = true
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

.comp-title {
  font-size: var(--font-size-large);
  font-weight: 600;
  margin-bottom: var(--spacing-sm);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.comp-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-xs);
  margin-bottom: var(--spacing-sm);
}

.comp-time {
  font-size: var(--font-size-small);
  color: var(--color-text-secondary);
  margin-bottom: var(--spacing-sm);
}

.comp-desc {
  font-size: var(--font-size-small);
  color: var(--color-text-secondary);
  height: 36px;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  margin-bottom: var(--spacing-sm);
}

.comp-actions {
  display: flex;
  gap: var(--spacing-xs);
  flex-wrap: wrap;
}

</style>

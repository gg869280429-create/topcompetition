<template>
  <div>
    <PageHeader title="首页" />
    <PageContainer v-loading="loading">
      <el-empty v-if="!loading && list.length === 0" description="暂无竞赛信息" />
      <el-row v-else :gutter="16">
        <el-col v-for="c in list" :key="c.id" :xs="24" :sm="12" :md="8" :lg="6">
          <el-card class="comp-card" shadow="hover">
            <div class="comp-title">{{ c.title }}</div>
            <div class="comp-tags">
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
import { pageCompetition } from '@/api/competition'
import CompetitionResources from '@/components/CompetitionResources.vue'
import { COMPETITION_TYPE, COMPETITION_LEVEL, COMPETITION_STATUS } from '@/constants/dict'

const userStore = useUserStore()
const router = useRouter()

const isStudent = computed(() => userStore.role === 'STUDENT')

const loading = ref(false)
const submitting = ref(false)
const list = ref([])

const detailVisible = ref(false)
const detail = ref({})

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
}

</style>

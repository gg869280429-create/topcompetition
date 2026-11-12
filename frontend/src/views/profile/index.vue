<template>
  <div class="personal-center">
    <PageHeader :title="isTeacher ? '个人中心' : '个人信息'" />
    <template v-if="isTeacher">
      <el-tabs v-model="tab" @tab-change="loadTab">
        <el-tab-pane label="个人资料" name="profile"><ProfileSettings /></el-tab-pane>
        <el-tab-pane label="指导名额" name="quota">
          <GuidanceQuotaSummary v-if="tab === 'quota'" />
          <PageContainer v-loading="loading">
            <el-table :data="quotas" border stripe><el-table-column prop="competitionTitle" label="竞赛" min-width="180"/><el-table-column prop="remaining" label="剩余指导名额" min-width="130"/><el-table-column prop="assigned" label="正在指导名额" min-width="130"/><el-table-column prop="quota" label="总共指导名额" min-width="130"/></el-table>
            <el-pagination v-model:current-page="quotaPage" :total="quotaTotal" :page-size="10" layout="total,prev,pager,next" @current-change="loadQuotas" />
          </PageContainer>
        </el-tab-pane>
        <el-tab-pane label="历史获奖" name="awards">
          <PageContainer v-loading="loading">
            <el-table :data="awards" border stripe><el-table-column prop="competitionTitle" label="竞赛" min-width="170"/><el-table-column prop="achievementName" label="获奖成果" min-width="170"/><el-table-column label="奖项" min-width="100"><template #default="{row}">{{ AWARD_LEVEL[row.awardLevel] || row.awardLevel }}</template></el-table-column><el-table-column prop="awardTime" label="获奖日期" min-width="170"/><el-table-column label="操作" width="90"><template #default="{row}"><el-button link type="primary" @click="router.push({path:'/award',query:{detail:row.id}})">查看详情</el-button></template></el-table-column></el-table>
            <el-pagination v-model:current-page="awardPage" :total="awardTotal" :page-size="10" layout="total,prev,pager,next" @current-change="loadAwards" />
          </PageContainer>
        </el-tab-pane>
        <el-tab-pane label="竞赛费用申报审批" name="expenses"><PageContainer><el-empty description="敬请期待" /></PageContainer></el-tab-pane>
      </el-tabs>
    </template>
    <ProfileSettings v-else />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import PageHeader from '@/components/PageHeader.vue'
import PageContainer from '@/components/PageContainer.vue'
import ProfileSettings from '@/components/ProfileSettings.vue'
import GuidanceQuotaSummary from '@/components/GuidanceQuotaSummary.vue'
import { pageMyCompetitions } from '@/api/guidance'
import request from '@/api/request'
import { AWARD_LEVEL } from '@/constants/dict'
const router = useRouter(), store = useUserStore(), isTeacher = computed(() => store.role === 'TEACHER')
const tab = ref('profile'), loading = ref(false), quotas = ref([]), awards = ref([])
const quotaPage = ref(1), quotaTotal = ref(0), awardPage = ref(1), awardTotal = ref(0)
async function loadQuotas() {
  loading.value = true
  try { const response = await pageMyCompetitions({ current: quotaPage.value, size: 10 }); quotas.value = response.data.records; quotaTotal.value = response.data.total }
  finally { loading.value = false }
}
async function loadAwards() {
  loading.value = true
  try { const response = await request.get('/award/page', { params: { current: awardPage.value, size: 10, status: 'APPROVED' } }); awards.value = response.data.records; awardTotal.value = response.data.total }
  finally { loading.value = false }
}
async function loadTab(value) {
  if (value === 'quota') await loadQuotas()
  if (value === 'awards') await loadAwards()
}
</script>

<style scoped>
.personal-center { max-width:1080px; margin:0 auto; }
.el-pagination { margin-top:20px; }
</style>

<template>
  <div>
    <PageHeader title="统计中心" />
    <PageContainer v-loading="loading">
      <el-alert title="报名统计不含待成员确认的草稿；有效参赛仅计审核通过且竞赛未取消的团队；获奖仅计审核通过且未撤销记录。" type="info" :closable="false" style="margin-bottom:16px" />
      <el-row :gutter="16" class="card-row">
        <el-col v-for="card in cards" :key="card.label" :xs="12" :sm="8" :md="4">
          <div class="stat-card">
            <div class="stat-card-label">{{ card.label }}</div>
            <div class="stat-card-value">{{ card.value }}</div>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <el-card shadow="never" class="chart-card">
            <template #header>竞赛状态分布</template>
            <div ref="statusRef" class="chart"></div>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-card shadow="never" class="chart-card">
            <template #header>获奖等级分布</template>
            <div ref="awardRef" class="chart"></div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="24">
          <el-card shadow="never" class="chart-card">
            <template #header>各专业报名分布</template>
            <div ref="majorRef" class="chart chart-tall"></div>
          </el-card>
        </el-col>
      </el-row>
    </PageContainer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import PageHeader from '@/components/PageHeader.vue'
import PageContainer from '@/components/PageContainer.vue'
import { getStatisticsOverview } from '@/api/statistics'
import { COMPETITION_STATUS, AWARD_LEVEL } from '@/constants/dict'

echarts.use([BarChart, PieChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const loading = ref(false)
const overview = ref({})
const statusRef = ref()
const awardRef = ref()
const majorRef = ref()

const cards = computed(() => [
  { label: '竞赛总数', value: overview.value.competitionCount ?? 0 },
  { label: '报名团队数', value: overview.value.registrationCount ?? 0 },
  { label: '报名人数（按竞赛去重）', value: overview.value.registrationPeopleCount ?? 0 },
  { label: '有效参赛团队数', value: overview.value.effectiveRegistrationCount ?? 0 },
  { label: '获奖成果数', value: overview.value.awardCount ?? 0 },
  { label: '获奖团队数', value: overview.value.awardTeamCount ?? 0 },
  { label: '获奖学生数', value: overview.value.awardStudentCount ?? 0 },
  { label: '教师指导成果数', value: overview.value.teacherAchievementCount ?? 0 },
  { label: '学生人数', value: overview.value.studentCount ?? 0 },
  { label: '教师人数', value: overview.value.teacherCount ?? 0 },
  { label: '指导中团队', value: overview.value.guidanceCount ?? 0 }
])

let charts = []

function pieOption(list, dict) {
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '44%'],
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b} {d}%' },
        data: (list || []).map((i) => ({
          name: dict ? dict[i.name] || i.name : i.name,
          value: i.value
        }))
      }
    ]
  }
}

function barOption(list) {
  const data = list || []
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '3%', containLabel: true },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: data.map((i) => i.name), inverse: true },
    series: [
      {
        type: 'bar',
        data: data.map((i) => i.value),
        barMaxWidth: 28,
        itemStyle: { color: '#409EFF', borderRadius: [0, 4, 4, 0] }
      }
    ]
  }
}

function renderCharts() {
  charts.forEach((c) => c.dispose())
  charts = []

  const status = echarts.init(statusRef.value)
  status.setOption(pieOption(overview.value.competitionStatus, COMPETITION_STATUS))
  charts.push(status)

  const award = echarts.init(awardRef.value)
  award.setOption(pieOption(overview.value.awardLevel, AWARD_LEVEL))
  charts.push(award)

  const major = echarts.init(majorRef.value)
  major.setOption(barOption(overview.value.registrationMajor))
  charts.push(major)
}

function handleResize() {
  charts.forEach((c) => c.resize())
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getStatisticsOverview()
    overview.value = res.data
    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  charts.forEach((c) => c.dispose())
})
</script>

<style scoped>
.card-row {
  margin-bottom: var(--spacing-md);
}

.stat-card {
  background-color: var(--color-bg-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-base);
  padding: var(--spacing-lg);
  margin-bottom: var(--spacing-md);
}

.stat-card-label {
  font-size: var(--font-size-small);
  color: var(--color-text-secondary);
  margin-bottom: var(--spacing-sm);
}

.stat-card-value {
  font-size: 28px;
  font-weight: 600;
  color: var(--color-primary);
  line-height: 1;
}

.chart-card {
  margin-bottom: var(--spacing-md);
}

.chart {
  width: 100%;
  height: 320px;
}

.chart-tall {
  height: 360px;
}
</style>

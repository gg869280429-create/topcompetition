<template>
  <div class="competition-detail-content">
    <el-descriptions class="competition-detail" :column="2" border>
      <el-descriptions-item label="竞赛名称" :span="2">{{ competition.title }}</el-descriptions-item>
      <el-descriptions-item label="赛制">{{ COMPETITION_TYPE[competition.competitionType] || competition.competitionType }}</el-descriptions-item>
      <el-descriptions-item label="级别">{{ COMPETITION_LEVEL[competition.level] || competition.level }}</el-descriptions-item>
      <el-descriptions-item label="所属专业"><MajorLabel :name="competition.majorName" :major-id="competition.majorId" :icon-code="competition.majorIconCode" :logo-file-id="competition.majorLogoFileId" placeholder="待补录" /></el-descriptions-item>
      <el-descriptions-item label="学科类别">{{ competition.category || '-' }}</el-descriptions-item>
      <el-descriptions-item label="主办单位">{{ competition.organizer || '-' }}</el-descriptions-item>
      <el-descriptions-item label="团队人数上限">{{ competition.maxMembers }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ COMPETITION_STATUS[competition.status] || competition.status }}</el-descriptions-item>
      <el-descriptions-item label="报名时间" :span="2">
        {{ (competition.registrationStartTime || '-') + ' ~ ' + (competition.registrationEndTime || '-') }}
      </el-descriptions-item>
      <el-descriptions-item label="竞赛简介" :span="2">{{ competition.description || '-' }}</el-descriptions-item>
    </el-descriptions>
    <CompetitionResources :competition="competition" />
  </div>
</template>

<script setup>
import MajorLabel from '@/components/MajorLabel.vue'
import CompetitionResources from '@/components/CompetitionResources.vue'
import { COMPETITION_TYPE, COMPETITION_LEVEL, COMPETITION_STATUS } from '@/constants/dict'

defineProps({ competition: { type: Object, required: true } })
</script>

<style scoped>
.competition-detail-content {
  overflow-wrap: anywhere;
}

.competition-detail :deep(.el-descriptions__label) {
  width: 112px;
  white-space: nowrap;
}

.competition-detail :deep(.el-descriptions__content) {
  white-space: pre-wrap;
}
</style>

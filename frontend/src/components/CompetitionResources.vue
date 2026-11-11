<template>
  <div v-loading="loading" class="resources">
    <div class="resource-title">竞赛文件</div>
    <el-button v-if="competition.documentFileId" link type="primary" :loading="downloading" @click="download">{{ competition.documentName || '下载竞赛文件' }}</el-button>
    <span v-else>暂未上传</span>
    <div class="resource-title">竞赛指导教师</div>
    <el-empty v-if="!loading && !teachers.length" description="暂未配置指导教师" :image-size="50" />
    <div v-for="t in teachers" :key="t.teacherId" class="teacher">
      <div><strong>{{ t.teacherName }}</strong> · {{ t.majorName || '专业未设置' }} · 剩余 {{ t.remaining }} / {{ t.quota }} 个团队名额</div>
      <div>擅长方向：{{ t.expertise || '暂未填写' }}</div>
      <div v-if="t.biography" class="biography">{{ t.biography }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { competitionTeachers } from '@/api/guidance'
import { downloadCompetitionDocument } from '@/api/file'
const props = defineProps({ competition: { type: Object, required: true } })
const loading = ref(false)
const downloading = ref(false)
const teachers = ref([])
let requestVersion = 0
watch(() => props.competition.id, async (id) => {
  const version = ++requestVersion
  teachers.value = []
  if (!id) return
  loading.value = true
  try {
    const res = await competitionTeachers(id)
    if (version === requestVersion) teachers.value = res.data
  } finally { if (version === requestVersion) loading.value = false }
}, { immediate: true })
async function download() {
  downloading.value = true
  try { await downloadCompetitionDocument(props.competition.id, props.competition.documentName) }
  finally { downloading.value = false }
}
</script>

<style scoped>
.resource-title { font-weight: 600; margin: 18px 0 10px; }
.teacher { padding: 12px 0; border-bottom: 1px solid var(--color-border-light); line-height: 1.8; }
.biography { white-space: pre-wrap; color: var(--color-text-secondary); }
</style>

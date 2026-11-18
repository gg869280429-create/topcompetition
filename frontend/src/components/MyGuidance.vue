<template>
<div><PageContainer>
        <GuidanceQuotaSummary ref="quotaSummaryRef" />
        <h3>我的指导竞赛</h3>
        <el-table :data="myCompetitions" border stripe>
          <el-table-column prop="competitionTitle" label="竞赛名称" min-width="180" />
          <el-table-column prop="remaining" label="剩余指导名额" min-width="130" />
          <el-table-column prop="assigned" label="正在指导名额" min-width="130" />
          <el-table-column prop="quota" label="总共指导名额" min-width="130" />
        </el-table>
        <el-pagination v-model:current-page="competitionQuery.current" :page-size="competitionQuery.size" :total="competitionTotal" layout="total, prev, pager, next" @current-change="loadMyCompetitions" style="margin:16px 0" />
        <h3>我的指导团队</h3>
        <el-table v-loading="mineLoading" :data="mineList" border stripe>
          <el-table-column prop="competitionTitle" label="竞赛名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="teamName" label="团队名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="assignTime" label="分配时间" width="170" />
          <el-table-column label="状态" width="100"><template #default="{ row }">{{ row.status === 'COMPLETED' ? '已完成' : '指导中' }}</template></el-table-column>
          <el-table-column label="操作" min-width="300"><template #default="{ row }">
            <el-button link type="primary" @click="router.push({ path: '/registration', query: { detail: row.registrationId } })">团队详情</el-button>
            <el-button v-if="row.status === 'ACTIVE'" link type="success" @click="finish(row)">完成指导</el-button>
            <el-button link type="primary" @click="openResults(row)">登记学生结果</el-button>
          </template></el-table-column>
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

</PageContainer>
    <el-dialog v-model="resultsVisible" title="登记参赛结果" width="min(95vw,860px)" :close-on-click-modal="false" :close-on-press-escape="!resultSaving" :show-close="!resultSaving">
      <el-alert title="保存结果不会自动退赛或生成获奖申报。获奖后请前往获奖记录填写申报及上传材料。" type="info" :closable="false" />
      <div v-if="resultCompetitionType === 'TEAM'" class="team-result-panel">
        <h3>团队统一登记</h3>
        <el-form label-width="90px" @submit.prevent>
          <el-form-item label="是否参赛"><el-select v-model="teamResult.participated" :disabled="resultSaving" placeholder="请选择" @change="!teamResult.participated && (teamResult.won = false)"><el-option label="确认参赛" :value="true"/><el-option label="未参赛" :value="false"/></el-select></el-form-item>
          <el-form-item v-if="teamResult.participated" label="是否获奖"><el-select v-model="teamResult.won" :disabled="resultSaving" placeholder="请选择"><el-option label="获奖" :value="true"/><el-option label="未获奖" :value="false"/></el-select></el-form-item>
          <el-form-item label="备注"><el-input v-model="teamResult.remark" type="textarea" :rows="2" maxlength="1000" :disabled="resultSaving" /></el-form-item>
          <el-form-item><el-button type="primary" :loading="resultSaving" :disabled="teamResult.participated == null || (teamResult.participated && teamResult.won == null)" @click="saveTeamResult">保存整队结果</el-button></el-form-item>
        </el-form>
      </div>
      <h3>个人明细</h3>
      <p class="tip-text">共 {{ studentResults.length }} 人，已登记参赛 {{ studentResults.filter(s => s.participated).length }} 人，已登记获奖 {{ studentResults.filter(s => s.participated && s.won).length }} 人。{{ resultRosterLocked ? '参赛名单已锁定。' : '确认参赛后将锁定名单，队长不能再调整队员。' }}</p>
      <el-table v-loading="resultSaving" :data="studentResults" style="margin-top:16px">
        <el-table-column prop="name" label="学生" min-width="100" />
        <el-table-column label="是否参赛" width="130"><template #default="{row}"><el-select v-model="row.participated" @change="!row.participated && (row.won = false)"><el-option label="参赛" :value="true"/><el-option label="未参赛" :value="false"/></el-select></template></el-table-column>
        <el-table-column label="是否获奖" width="130"><template #default="{row}"><el-select v-if="row.participated" v-model="row.won"><el-option label="获奖" :value="true"/><el-option label="未获奖" :value="false"/></el-select><span v-else>—</span></template></el-table-column>
        <el-table-column label="备注" min-width="160"><template #default="{row}"><el-input v-model="row.remark" maxlength="1000"/></template></el-table-column>
        <el-table-column label="操作" width="80"><template #default="{row}"><el-button link type="primary" :disabled="row.participated == null || (row.participated && row.won == null)" @click="saveResult(row)">保存</el-button></template></el-table-column>
      </el-table>
      <template #footer><el-button :disabled="resultSaving" @click="resultsVisible=false">关闭</el-button><el-button type="primary" :disabled="resultSaving" @click="resultsVisible=false; router.push('/award')">前往成果申报</el-button></template>
    </el-dialog>
</div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageContainer from '@/components/PageContainer.vue'
import GuidanceQuotaSummary from '@/components/GuidanceQuotaSummary.vue'
import { getCompetition } from '@/api/competition'
import { getRegistration } from '@/api/registration'
import { pageMyGuidance, completeGuidance, guidanceResults, saveParticipationResult, saveTeamParticipationResult, pageMyCompetitions } from '@/api/guidance'
const router=useRouter()
const mineLoading=ref(false),mineList=ref([]),mineTotal=ref(0)
const mineQuery=reactive({current:1,size:10})
const quotaSummaryRef = ref()
const myCompetitions = ref([])
const competitionTotal = ref(0)
const competitionQuery = reactive({ current: 1, size: 10 })
const resultsVisible = ref(false), studentResults = ref([]), resultGuidanceId = ref(null)
const resultCompetitionType = ref(''), resultRosterLocked = ref(false), resultSaving = ref(false)
const teamResult = reactive({ participated: null, won: null, remark: '' })
async function finish(row) {
  try { await ElMessageBox.confirm('确认完成指导？完成后将释放该团队占用的一个名额，仍可登记结果和申报获奖。', '完成指导', { type: 'warning' }) }
  catch (e) { if (e === 'cancel' || e === 'close') return; throw e }
  await completeGuidance(row.id)
  ElMessage.success('指导已完成，名额已释放')
  await Promise.all([loadMine(), loadMyCompetitions(), quotaSummaryRef.value?.refresh()])
}
async function openResults(row) {
  const [res, competition, registration] = await Promise.all([guidanceResults(row.id), getCompetition(row.competitionId), getRegistration(row.registrationId)])
  resultGuidanceId.value = row.id
  resultCompetitionType.value = competition.data.competitionType
  resultRosterLocked.value = !!registration.data.rosterLockedAt || row.status === 'COMPLETED'
  Object.assign(teamResult, { participated: null, won: null, remark: '' })
  studentResults.value = res.data
  resultsVisible.value = true
}
async function saveResult(row) {
  if (resultSaving.value) return
  resultSaving.value = true
  try {
    if (row.participated && !resultRosterLocked.value) await ElMessageBox.confirm('确认参赛后将锁定团队名单，队长不能再调整队员。是否继续？', '确认参赛', { type: 'warning' })
    await saveParticipationResult(resultGuidanceId.value, row.studentId, { participated: row.participated, won: row.participated ? row.won : false, remark: row.remark })
    if (row.participated) resultRosterLocked.value = true
    studentResults.value = (await guidanceResults(resultGuidanceId.value)).data
    ElMessage.success('结果已保存')
  } catch { return }
  finally { resultSaving.value = false }
}
async function saveTeamResult() {
  if (resultSaving.value) return
  resultSaving.value = true
  try {
    await ElMessageBox.confirm(teamResult.participated ? '将为全体队员保存参赛及获奖情况，同时锁定名单。是否继续？' : '将为全体队员保存未参赛结果。是否继续？', '整队登记', { type: 'warning' })
    await saveTeamParticipationResult(resultGuidanceId.value, { participated: teamResult.participated, won: teamResult.participated ? teamResult.won : false, remark: teamResult.remark })
    if (teamResult.participated) resultRosterLocked.value = true
    studentResults.value = (await guidanceResults(resultGuidanceId.value)).data
    ElMessage.success('整队结果已保存，个人明细已同步')
  } catch { return }
  finally { resultSaving.value = false }
}
async function loadMyCompetitions() {
  const res = await pageMyCompetitions(competitionQuery)
  myCompetitions.value = res.data.records
  competitionTotal.value = res.data.total
}

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

onMounted(()=>Promise.all([loadMine(),loadMyCompetitions()]))
</script>

<style scoped>
.team-result-panel { margin-top:20px; padding:16px 20px; background:var(--el-fill-color-light); border-radius:8px; }
.tip-text { font-size:13px; color:var(--el-text-color-secondary); line-height:1.7; }
.pagination-wrapper { display:flex; justify-content:flex-end; margin-top:16px; }
</style>

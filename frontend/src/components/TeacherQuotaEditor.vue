<template>
 <section v-loading="loading"><h3>竞赛指导名额</h3>
 <p class="quota-tip">名额按团队计算，不能低于正在指导的团队数。每行单独保存。</p>
 <el-table :data="rows" border>
  <el-table-column prop="competitionTitle" label="竞赛" min-width="150"/>
  <el-table-column prop="assigned" label="正在指导" width="85"/>
  <el-table-column prop="remaining" label="剩余" width="65"/>
  <el-table-column label="总名额" width="150"><template #default="{row}"><el-input-number v-model="row.quota" :min="row.assigned" :max="10000" :disabled="closed(row) || saving" controls-position="right" style="width:125px" /></template></el-table-column>
  <el-table-column label="操作" width="80"><template #default="{row}"><el-button link type="primary" :disabled="closed(row) || saving" @click="save(row)">保存名额</el-button></template></el-table-column>
 </el-table>
 <el-form inline class="quota-add" @submit.prevent="add">
  <el-form-item label="新增竞赛"><el-select v-model="addition.competitionId" filterable remote :remote-method="searchCompetitions" @visible-change="v=>v && searchCompetitions('')" placeholder="搜索竞赛" style="width:220px"><el-option v-for="c in competitions" :key="c.id" :value="c.id" :label="c.title" /></el-select></el-form-item>
  <el-form-item label="总名额"><el-input-number v-model="addition.quota" :min="0" :max="10000" controls-position="right" style="width:125px" /></el-form-item>
  <el-button :loading="saving" :disabled="!addition.competitionId" @click="add">添加名额</el-button>
 </el-form>
 </section>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { teacherQuotaDetails, saveQuota } from '@/api/guidance'
import { pageCompetition } from '@/api/competition'
const props=defineProps({teacherId:{type:Number,required:true}})
const rows=ref([]),competitions=ref([]),loading=ref(false),saving=ref(false)
const addition=reactive({competitionId:null,quota:1})
const closed=row=>['ENDED','CANCELLED'].includes(row.competitionStatus)
async function load(){loading.value=true;try{rows.value=(await teacherQuotaDetails(props.teacherId)).data}finally{loading.value=false}}
async function searchCompetitions(keyword){const response=await pageCompetition({current:1,size:50,keyword});competitions.value=response.data.records.filter(c=>!['ENDED','CANCELLED'].includes(c.status) && !rows.value.some(q=>q.competitionId===c.id))}
async function save(row){saving.value=true;try{await saveQuota({competitionId:row.competitionId,teacherId:props.teacherId,quota:row.quota});ElMessage.success('指导名额已保存');await load()}finally{saving.value=false}}
async function add(){if(!addition.competitionId)return;await save(addition);addition.competitionId=null;await searchCompetitions('')}
onMounted(load)
</script>
<style scoped>.quota-tip{font-size:13px;color:var(--el-text-color-secondary)}.quota-add{margin-top:18px}</style>

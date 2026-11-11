<template>
  <div>
    <PageHeader title="教师库"><template #actions><el-button type="primary" @click="open()">新增教师</el-button></template></PageHeader>
    <PageContainer>
      <el-form inline @submit.prevent="search">
        <el-form-item label="教师"><el-input v-model="query.keyword" placeholder="姓名或用户名" clearable /></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button></el-form-item>
      </el-form>
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="realName" label="姓名" min-width="100" />
        <el-table-column prop="username" label="登录账号" min-width="120" />
        <el-table-column prop="majorName" label="所属专业" min-width="150" />
        <el-table-column prop="expertise" label="擅长方向" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="140"><template #default="{ row }"><el-button link type="primary" @click="detail(row)">详情</el-button><el-button link type="primary" @click="open(row)">编辑</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" style="margin-top: 16px; justify-content: flex-end" />
    </PageContainer>
    <el-dialog v-model="visible" :title="form.id ? '编辑教师' : '新增教师'" width="560px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="登录账号" prop="username"><el-input v-model="form.username" maxlength="64" /></el-form-item>
        <el-form-item :label="form.id ? '重设密码' : '初始密码'" prop="password"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" :placeholder="form.id ? '留空保持原密码' : '至少8个字符'" /></el-form-item>
        <el-form-item label="姓名" prop="realName"><el-input v-model="form.realName" maxlength="64" /></el-form-item>
        <el-form-item label="所属专业" prop="majorId"><el-select v-model="form.majorId" style="width:100%"><el-option v-for="m in allowedMajors" :key="m.id" :label="m.name" :value="m.id" /></el-select></el-form-item>
        <el-form-item label="擅长方向"><el-input v-model="form.expertise" maxlength="255" /></el-form-item>
        <el-form-item label="教师简介"><el-input v-model="form.biography" type="textarea" :rows="4" maxlength="4000" show-word-limit /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="detailVisible" :title="`教师详情 · ${selectedTeacher.realName || selectedTeacher.username || ''}`" width="850px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="所属专业">{{ selectedTeacher.majorName || '未配置' }}</el-descriptions-item>
        <el-descriptions-item label="擅长方向">{{ selectedTeacher.expertise || '—' }}</el-descriptions-item>
        <el-descriptions-item label="简介" :span="2">{{ selectedTeacher.biography || '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="quotaDetails" style="margin-top:20px">
        <el-table-column prop="competitionTitle" label="竞赛" min-width="160"/>
        <el-table-column prop="quota" label="总名额" width="85"/>
        <el-table-column prop="assigned" label="占用" width="75"/>
        <el-table-column prop="remaining" label="剩余" width="75"/>
        <el-table-column label="增加总名额" min-width="220"><template #default="{row}"><el-input-number v-model="row.increase" :min="1" :max="10000" size="small" style="width:110px"/><el-button link type="primary" :disabled="['ENDED','CANCELLED'].includes(row.competitionStatus)" @click="increase(row)">增加</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!quotaDetails.length" description="尚未配置竞赛指导名额"/>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { teacherQuotaDetails, increaseTeacherQuota } from '@/api/guidance'
import PageHeader from '@/components/PageHeader.vue'
import PageContainer from '@/components/PageContainer.vue'
import { pageTeacher, listMajors, createTeacher, updateTeacher } from '@/api/teacher'
import { useUserStore } from '@/store/user'
const user = useUserStore()
const rows = ref([]), total = ref(0), loading = ref(false), saving = ref(false), visible = ref(false), majors = ref([]), formRef = ref()
const query = reactive({ keyword: '', current: 1, size: 10 })
const empty = () => ({ id: null, username: '', password: '', realName: '', majorId: null, expertise: '', biography: '', status: 1 })
const form = reactive(empty())
const detailVisible = ref(false), selectedTeacher = ref({}), quotaDetails = ref([])
async function detail(row) {
  const res = await teacherQuotaDetails(row.id)
  selectedTeacher.value = row
  quotaDetails.value = res.data.map(q => ({ ...q, increase: 1 }))
  detailVisible.value = true
}
async function increase(row) {
  try { await ElMessageBox.confirm(`确认给该竞赛增加 ${row.increase} 个指导总名额？`, '增加名额', { type: 'warning' }) }
  catch (e) { if (e === 'cancel' || e === 'close') return; throw e }
  await increaseTeacherQuota(selectedTeacher.value.id, row.competitionId, row.increase)
  ElMessage.success('总名额已增加')
  await detail(selectedTeacher.value)
}
const allowedMajors = computed(() => majors.value.filter(m => user.role !== 'MAJOR_ADMIN' || m.id === user.userInfo?.majorId))
const rules = {
  username: [{ required: true, message: '请输入登录账号', trigger: 'blur' }, { pattern: /^[a-zA-Z0-9_]+$/, message: '仅支持字母、数字和下划线', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  majorId: [{ required: true, message: '请选择专业', trigger: 'change' }],
  password: [{ validator: (_, value, done) => {
    if ((!form.id && !value) || (value && value.length < 8)) done(new Error('密码至少8个字符'))
    else done()
  }, trigger: 'blur' }]
}
async function load() { loading.value = true; try { const res = await pageTeacher(query); rows.value = res.data.records; total.value = res.data.total } finally { loading.value = false } }
function search() { query.current = 1; load() }
async function open(row) { Object.assign(form, empty(), row || {}, { password: '' }); if (!row && user.role === 'MAJOR_ADMIN') form.majorId = user.userInfo?.majorId; visible.value = true; await nextTick(); formRef.value?.clearValidate() }
async function save() {
  if (!await formRef.value.validate().catch(() => false)) return
  saving.value = true
  try { const payload = { ...form }; if (!payload.password) delete payload.password; if (form.id) await updateTeacher(form.id, payload); else await createTeacher(payload); ElMessage.success('保存成功'); visible.value = false; await load() }
  finally { saving.value = false }
}
onMounted(async () => { const res = await listMajors(); majors.value = res.data; await load() })
</script>

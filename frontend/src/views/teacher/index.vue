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
        <el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="primary" @click="open(row)">编辑</el-button></template></el-table-column>
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
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PageContainer from '@/components/PageContainer.vue'
import { pageTeacher, listMajors, createTeacher, updateTeacher } from '@/api/teacher'
import { useUserStore } from '@/store/user'
const user = useUserStore()
const rows = ref([]), total = ref(0), loading = ref(false), saving = ref(false), visible = ref(false), majors = ref([]), formRef = ref()
const query = reactive({ keyword: '', current: 1, size: 10 })
const empty = () => ({ id: null, username: '', password: '', realName: '', majorId: null, expertise: '', biography: '', status: 1 })
const form = reactive(empty())
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

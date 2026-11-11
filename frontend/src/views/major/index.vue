<template>
 <div><PageHeader title="专业管理"><template #actions><el-button type="primary" @click="open()">新增专业</el-button></template></PageHeader>
 <PageContainer><el-form inline @submit.prevent="load"><el-form-item label="名称"><el-input v-model="keyword" clearable /></el-form-item><el-button @click="load">查询</el-button></el-form>
 <el-table v-loading="loading" :data="rows" border><el-table-column prop="id" label="专业ID" width="100"/><el-table-column prop="name" label="专业名称"/><el-table-column label="操作" width="160"><template #default="{row}"><el-button link type="primary" @click="open(row)">修改名称</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column></el-table>
 </PageContainer><el-dialog v-model="visible" :title="form.id ? '修改专业名称' : '新增专业'" width="440px"><el-form @submit.prevent="save"><el-form-item label="专业名称"><el-input v-model="form.name" maxlength="64" show-word-limit /></el-form-item></el-form><template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template></el-dialog></div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PageContainer from '@/components/PageContainer.vue'
import { listMajor, createMajor, updateMajor, deleteMajor } from '@/api/major'
const rows=ref([]), keyword=ref(''), loading=ref(false), visible=ref(false), saving=ref(false)
const form=reactive({id:null,name:''})
async function load(){loading.value=true;try{rows.value=(await listMajor({keyword:keyword.value})).data}finally{loading.value=false}}
function open(row){form.id=row?.id||null;form.name=row?.name||'';visible.value=true}
async function save(){if(!form.name.trim()){ElMessage.warning('请输入专业名称');return} saving.value=true;try{if(form.id)await updateMajor(form.id,{name:form.name.trim()});else await createMajor({name:form.name.trim()});visible.value=false;ElMessage.success('保存成功');await load()}finally{saving.value=false}}
async function remove(row){try{await ElMessageBox.confirm(`确认删除专业「${row.name}」？有关联记录时无法删除。`,'删除专业',{type:'warning'});await deleteMajor(row.id);ElMessage.success('删除成功');await load()}catch(e){if(e!=='cancel'&&e!=='close')throw e}}
onMounted(load)
</script>

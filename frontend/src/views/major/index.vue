<template>
 <div><PageHeader title="专业管理"><template #actions><el-button type="primary" @click="open()">新增专业</el-button></template></PageHeader>
 <PageContainer><el-form inline @submit.prevent="load"><el-form-item label="名称"><el-input v-model="keyword" clearable /></el-form-item><el-button @click="load">查询</el-button></el-form>
 <el-table v-loading="loading" :data="rows" border><el-table-column prop="id" label="专业ID" width="100"/><el-table-column prop="name" label="专业名称"><template #default="{row}"><MajorLabel :name="row.name" :major-id="row.id" :icon-code="row.iconCode" :logo-file-id="row.logoFileId" /></template></el-table-column><el-table-column label="操作" width="160"><template #default="{row}"><el-button link type="primary" @click="open(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column></el-table>
 </PageContainer><el-dialog v-model="visible" :title="form.id ? '编辑专业' : '新增专业'" width="440px"><el-form @submit.prevent="save"><el-form-item label="专业名称"><el-input v-model="form.name" maxlength="64" show-word-limit /></el-form-item>
 <el-form-item label="专业图标"><el-select v-model="form.iconCode" clearable placeholder="按专业名称自动匹配"><el-option v-for="item in presets" :key="item.code" :value="item.code" :label="item.label"><span style="display:flex;align-items:center;gap:8px"><el-icon><component :is="icons[item.code]" /></el-icon>{{ item.label }}</span></el-option></el-select></el-form-item>
 <el-form-item label="自定义Logo"><el-upload :http-request="upload" :show-file-list="false" accept="image/png,image/jpeg,image/webp" :disabled="uploading"><el-button :loading="uploading">上传Logo</el-button></el-upload><el-button v-if="form.logoFileId" link type="danger" @click="clearLogo">移除</el-button></el-form-item>
 <div style="margin:16px 0"><img v-if="preview" :src="preview" alt="专业Logo预览" style="width:64px;height:64px;object-fit:contain"/><MajorLabel v-else :name="form.name" :major-id="form.id" :icon-code="form.iconCode" :logo-file-id="form.logoFileId" placeholder="专业图标预览" /></div>
 <p style="font-size:12px;color:var(--el-text-color-secondary)">支持PNG、JPEG、WEBP，最大10MB。自定义Logo优先展示。</p>
 </el-form><template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template></el-dialog></div>
</template>
<script setup>
import {Monitor,Cpu,Tools,SetUp,Coin,Brush,House,FirstAidKit,Reading,ChatDotRound,Bowl,Sunny,Collection} from '@element-plus/icons-vue'
import request from '@/api/request'
import MajorLabel from '@/components/MajorLabel.vue'
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PageContainer from '@/components/PageContainer.vue'
import { listMajor, createMajor, updateMajor, deleteMajor } from '@/api/major'
const rows=ref([]), keyword=ref(''), loading=ref(false), visible=ref(false), saving=ref(false)
const form=reactive({id:null,name:'',iconCode:null,logoFileId:null})
const uploading=ref(false),preview=ref('')
const icons={Monitor,Cpu,Tools,SetUp,Coin,Brush,House,FirstAidKit,Reading,ChatDotRound,Bowl,Sunny,Collection}
const presets=[['Monitor','计算机'],['Cpu','电子通信'],['Tools','机械制造'],['SetUp','自动化'],['Coin','经济管理'],['Brush','艺术设计'],['House','建筑土木'],['FirstAidKit','医疗护理'],['Reading','教育'],['ChatDotRound','语言'],['Bowl','食品旅游'],['Sunny','农林'],['Collection','通用学科']].map(([code,label])=>({code,label}))
function releasePreview(){if(preview.value)URL.revokeObjectURL(preview.value);preview.value=''}
function clearLogo(){form.logoFileId=null;releasePreview()}
async function upload(options){uploading.value=true;try{const body=new FormData();body.append('file',options.file);const res=await request.post('/file/major-logo',body);form.logoFileId=res.data.id;releasePreview();preview.value=URL.createObjectURL(options.file);options.onSuccess?.(res)}catch(e){options.onError?.(e)}finally{uploading.value=false}}
onUnmounted(releasePreview)
async function load(){loading.value=true;try{rows.value=(await listMajor({keyword:keyword.value})).data}finally{loading.value=false}}
function open(row){form.id=row?.id||null;form.name=row?.name||'';form.iconCode=row?.iconCode||null;form.logoFileId=row?.logoFileId||null;releasePreview();visible.value=true}
async function save(){if(uploading.value)return;if(!form.name.trim()){ElMessage.warning('请输入专业名称');return} saving.value=true;try{if(form.id)await updateMajor(form.id,{name:form.name.trim(),iconCode:form.iconCode||null,logoFileId:form.logoFileId});else await createMajor({name:form.name.trim(),iconCode:form.iconCode||null,logoFileId:form.logoFileId});visible.value=false;ElMessage.success('保存成功');await load()}finally{saving.value=false}}
async function remove(row){try{await ElMessageBox.confirm(`确认删除专业「${row.name}」？有关联记录时无法删除。`,'删除专业',{type:'warning'});await deleteMajor(row.id);ElMessage.success('删除成功');await load()}catch(e){if(e!=='cancel'&&e!=='close')throw e}}
onMounted(load)
</script>

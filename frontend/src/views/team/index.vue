<template>
  <div>
    <PageHeader title="我的组队"><template #actions><el-button type="primary" @click="openCreate">创建队伍</el-button></template></PageHeader>
    <PageContainer v-if="invitations.length">
      <h3>待处理邀请</h3>
      <el-table :data="invitations" border><el-table-column prop="name" label="队伍"/><el-table-column prop="inviterName" label="邀请人"/><el-table-column label="操作" width="200"><template #default="{row}"><el-button link @click="openDetail(row.teamId)">查看</el-button><el-button link type="success" :disabled="busy" @click="respond(row.teamId,true)">接受</el-button><el-button link type="danger" :disabled="busy" @click="respond(row.teamId,false)">拒绝</el-button></template></el-table-column></el-table>
      <p class="team-hint">接受组队后，确认的队长可使用当前名单报名多个团队赛；每次报名独立保存名单，并通知参赛成员。</p>
    </PageContainer>
    <PageContainer v-loading="loading">
      <el-empty v-if="!teams.length" description="还没有队伍，创建队伍或接受同学的邀请"/>
      <el-table v-else :data="teams" border stripe>
        <el-table-column prop="name" label="队伍名称" min-width="180"/>
        <el-table-column label="状态" width="120"><template #default="{row}"><el-tag :type="row.status==='READY'?'success':'info'">{{ statusNames[row.status] }}</el-tag></template></el-table-column>
        <el-table-column prop="memberCount" label="已加入人数" width="120"/>
        <el-table-column label="队长" min-width="130"><template #default="{row}">{{ row.captainName || (row.nominatedCaptainId ? '等待本人确认' : '尚未指定') }}</template></el-table-column>
        <el-table-column label="操作" min-width="250"><template #default="{row}"><el-button link type="primary" @click="openDetail(row.id)">队伍详情</el-button><el-button v-if="row.nominatedCaptainId===userId" link type="success" @click="openDetail(row.id)">确认队长</el-button><el-button v-if="row.status==='READY' && row.captainId===userId" link type="success" @click="goRegister(row)">报名团队赛</el-button></template></el-table-column>
      </el-table>
    </PageContainer>
    <el-dialog v-model="createVisible" title="创建队伍" width="min(540px,94vw)" :close-on-click-modal="false" :show-close="!busy">
      <el-form ref="createFormRef" :model="createForm" label-position="top" :rules="{name:[{required:true,whitespace:true,message:'请输入队伍名称',trigger:'blur'}]}">
        <el-form-item label="队伍名称" prop="name"><el-input v-model="createForm.name" maxlength="128" /></el-form-item>
        <el-form-item label="邀请学生"><el-select v-model="createForm.memberIds" multiple filterable remote :remote-method="search" :loading="searching" placeholder="搜索姓名或账号，邀请学生" style="width:100%"><el-option v-for="s in students" :key="s.id" :label="`${s.realName || s.username}（${s.username}）`" :value="s.id" :disabled="s.id===userId" /></el-select></el-form-item>
        <p class="team-hint">你将作为创建人加入队伍。成员接受邀请后，再指定并由本人确认队长。</p>
      </el-form>
      <template #footer><el-button :disabled="busy" @click="createVisible=false">取消</el-button><el-button type="primary" :loading="busy" @click="saveCreate">创建并邀请</el-button></template>
    </el-dialog>
    <el-dialog v-model="detailVisible" title="队伍详情" width="min(900px,96vw)" :close-on-click-modal="false" :show-close="!busy">
      <div v-loading="detailLoading">
        <h3>{{ detail.name }} <el-tag>{{ statusNames[detail.status] }}</el-tag></h3>
        <el-alert v-if="detail.status!=='DISBANDED'" type="info" :closable="false" title="名单变更后需重新指定并确认队长；已报名的竞赛名单保持独立。" />
        <div v-if="detail.nominatedCaptainId===userId" class="team-actions"><span>创建人指定你担任队长，是否确认？</span><el-button type="success" :disabled="busy" @click="captainResponse(true)">接受队长</el-button><el-button :disabled="busy" @click="captainResponse(false)">拒绝担任</el-button></div>
        <el-table :data="detail.members || []" border style="margin-top:16px">
          <el-table-column prop="name" label="姓名" min-width="110"/><el-table-column prop="majorName" label="专业" min-width="110"/>
          <el-table-column label="身份" width="140"><template #default="{row}">{{ row.userId===detail.captainId?'队长':row.userId===detail.creatorId?'创建人':'队员' }}</template></el-table-column>
          <el-table-column label="状态" width="110"><template #default="{row}">{{ memberNames[row.status] }}</template></el-table-column>
          <el-table-column label="操作" min-width="180"><template #default="{row}"><template v-if="detail.status!=='DISBANDED' && detail.creatorId===userId && row.userId!==userId && ['INVITED','ACCEPTED'].includes(row.status)"><el-button link type="danger" :disabled="busy" @click="remove(row)">{{ row.status==='INVITED'?'撤销邀请':'移除成员' }}</el-button></template><el-button v-if="detail.status!=='DISBANDED' && detail.creatorId===userId && row.status==='ACCEPTED'" link type="primary" :disabled="busy || hasPending" @click="nominate(row)">指定队长</el-button></template></el-table-column>
        </el-table>
        <template v-if="isJoined && detail.status!=='DISBANDED'">
          <el-form class="invite-form" label-position="top" @submit.prevent="invite">
            <el-form-item label="邀请新成员"><el-select v-model="inviteIds" multiple filterable remote :remote-method="search" :loading="searching" style="width:100%" placeholder="搜索并邀请同学"><el-option v-for="s in students" :key="s.id" :value="s.id" :label="`${s.realName || s.username}（${s.username}）`" :disabled="s.id===userId || detail.members?.some(m=>m.userId===s.id && ['ACCEPTED','INVITED'].includes(m.status))" /></el-select></el-form-item>
            <el-button type="primary" :loading="busy" :disabled="!inviteIds.length" native-type="submit">发送邀请</el-button>
          </el-form>
          <p v-if="hasPending" class="team-hint">仍有学生未确认邀请，请等待响应或由创建人撤销邀请，再指定队长。</p>
        </template>
        <el-collapse v-if="detail.history?.length" style="margin-top:20px"><el-collapse-item title="组队操作历史" name="history"><el-table :data="detail.history" size="small"><el-table-column prop="actorName" label="操作人"/><el-table-column prop="operation" label="操作"/><el-table-column prop="operationTime" label="时间" min-width="180"/></el-table></el-collapse-item></el-collapse>
      </div>
      <template #footer>
        <el-button v-if="detail.status!=='DISBANDED' && detail.creatorId===userId" type="danger" plain :disabled="busy" @click="disband">解散队伍</el-button>
        <el-button v-else-if="isJoined && detail.status!=='DISBANDED'" type="danger" plain :disabled="busy" @click="leave">退出队伍</el-button>
        <el-button :disabled="busy" @click="detailVisible=false">关闭</el-button>
        <el-button v-if="detail.status==='READY' && detail.captainId===userId" type="primary" :disabled="busy" @click="goRegister(detail)">报名团队赛</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import PageHeader from '@/components/PageHeader.vue'
import PageContainer from '@/components/PageContainer.vue'
import { listStudents } from '@/api/user'
import { myTeams, teamInvitations, getTeam, createTeam, inviteTeamMembers, respondTeamInvitation, nominateCaptain, confirmCaptain, removeTeamMember, disbandTeam } from '@/api/team'
const route=useRoute(),router=useRouter(),store=useUserStore(),userId=computed(()=>store.userInfo?.id)
const teams=ref([]),invitations=ref([]),loading=ref(false),busy=ref(false),students=ref([]),searching=ref(false)
const detail=ref({}),detailVisible=ref(false),detailLoading=ref(false),inviteIds=ref([])
const createVisible=ref(false),createFormRef=ref(),createForm=reactive({name:'',memberIds:[]})
const statusNames={FORMING:'组队中',READY:'可报名',DISBANDED:'已解散'},memberNames={INVITED:'待确认',ACCEPTED:'已加入',REJECTED:'已拒绝',REVOKED:'已撤销',LEFT:'已退出'}
const isJoined=computed(()=>detail.value.members?.some(m=>m.userId===userId.value && m.status==='ACCEPTED'))
const hasPending=computed(()=>detail.value.members?.some(m=>m.status==='INVITED'))
async function load(){loading.value=true;try{const [a,b]=await Promise.all([myTeams(),teamInvitations()]);teams.value=a.data;invitations.value=b.data}finally{loading.value=false}}
let searchVersion=0
async function search(keyword){const version=++searchVersion;searching.value=true;try{const r=await listStudents(keyword);if(version===searchVersion)students.value=r.data}finally{if(version===searchVersion)searching.value=false}}
function openCreate(){Object.assign(createForm,{name:'',memberIds:[]});students.value=[];createVisible.value=true;createFormRef.value?.clearValidate()}
async function openDetail(id){detailLoading.value=true;try{detail.value=(await getTeam(id)).data;inviteIds.value=[];students.value=[];detailVisible.value=true}finally{detailLoading.value=false}}
async function run(action,message){if(busy.value)return;busy.value=true;try{await action();ElMessage.success(message);await load();if(detailVisible.value)await openDetail(detail.value.id)}catch{return}finally{busy.value=false}}
async function saveCreate(){if(busy.value || !await createFormRef.value.validate().catch(()=>false))return;await run(async()=>{const r=await createTeam({...createForm});createVisible.value=false;await openDetail(r.data)},'队伍已创建，邀请已发送')}
async function respond(id,accepted){await run(async()=>{await respondTeamInvitation(id,accepted);if(!accepted && detail.value.id===id)detailVisible.value=false},accepted?'已加入队伍':'已拒绝邀请')}
async function invite(){await run(()=>inviteTeamMembers(detail.value.id,inviteIds.value),'邀请已发送，需重新确认队长')}
async function nominate(row){try{await ElMessageBox.confirm(`指定「${row.name}」担任队长？需要本人确认后才能报名。`,'指定队长',{confirmButtonText:'确定',cancelButtonText:'取消'})}catch{return}await run(()=>nominateCaptain(detail.value.id,row.userId),'已发送队长确认通知')}
async function captainResponse(accepted){await run(()=>confirmCaptain(detail.value.id,accepted),accepted?'已确认担任队长':'已拒绝担任队长')}
async function remove(row){try{await ElMessageBox.confirm('确认移除成员或撤销邀请？已提交的竞赛报名不会改变。','调整组队',{confirmButtonText:'确定',cancelButtonText:'取消'})}catch{return}await run(()=>removeTeamMember(detail.value.id,row.userId),'组队名单已更新')}
async function leave(){try{await ElMessageBox.confirm('确认退出组队？已提交报名不受影响。','退出队伍',{confirmButtonText:'确定',cancelButtonText:'取消'})}catch{return}await run(async()=>{await removeTeamMember(detail.value.id,userId.value);detailVisible.value=false},'已退出队伍')}
async function disband(){try{await ElMessageBox.confirm('确认解散组队？保留组队历史及已提交的各次报名。','解散队伍',{confirmButtonText:'确定',cancelButtonText:'取消'})}catch{return}await run(()=>disbandTeam(detail.value.id),'队伍已解散')}
function goRegister(team){router.push({path:'/registration',query:{team:team.id,open:Date.now()}})}
watch(()=>[route.query.detail,route.query.open],([id])=>{if(id)openDetail(Number(id))})
onMounted(async()=>{await load();if(route.query.detail)await openDetail(Number(route.query.detail))})
</script>
<style scoped>
h3{margin:0 0 16px}.team-hint{font-size:13px;color:var(--color-text-secondary);line-height:1.7}.team-actions{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin:20px 0}.invite-form{margin-top:24px}
</style>

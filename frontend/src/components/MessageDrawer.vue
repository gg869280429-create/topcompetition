<template>
  <el-drawer
    :model-value="modelValue"
    title="消息中心"
    size="520px"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @open="onOpen"
  >
    <div class="msg-toolbar">
      <span>未读消息：<b class="unread-count">{{ unread }}</b> 条</span>
      <div class="msg-actions">
        <el-button v-if="isAdmin" size="small" type="primary" @click="openSend">发送消息</el-button>
        <el-button size="small" :disabled="unread === 0" @click="handleMarkAllRead">全部已读</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="list" size="small">
      <el-table-column label="标题" min-width="180">
        <template #default="{ row }">
          <span :class="{ 'unread-title': row.isRead === 0 }" class="msg-title" @click="openDetail(row)">{{ row.title }}</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="72">
        <template #default="{ row }">
          <el-tag :type="typeTagType(row.type)" size="small">{{ MESSAGE_TYPE[row.type] || row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sendTime" label="时间" width="150" />
      <el-table-column label="操作" width="64" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20]"
        layout="total, prev, pager, next"
        @size-change="fetchList"
        @current-change="fetchList"
      />
    </div>

    <el-dialog v-model="detailVisible" :title="detail.title" width="480px" append-to-body>
      <div class="detail-meta">
        <span>发送人：{{ detail.senderName }}</span>
        <span>时间：{{ detail.sendTime }}</span>
        <el-tag :type="typeTagType(detail.type)" size="small">{{ MESSAGE_TYPE[detail.type] || detail.type }}</el-tag>
      </div>
      <el-divider />
      <div class="detail-content">{{ detail.content || '（无内容）' }}</div>
      <template #footer><el-button v-if="['REGISTRATION','AWARD','COMPETITION','INVITATION','TEAM'].includes(detail.bizType) && detail.bizId" type="primary" @click="openBusiness">查看相关业务</el-button></template>
    </el-dialog>

    <el-dialog v-model="sendVisible" title="发送消息" width="480px" append-to-body :close-on-click-modal="false" @closed="resetSendForm">
      <el-form ref="sendFormRef" :model="sendForm" :rules="sendRules" label-width="80px">
        <el-form-item label="接收人" prop="receiverId">
          <el-select
            v-model="sendForm.receiverId"
            placeholder="搜索并选择接收人"
            filterable
            remote
            :remote-method="searchUsers"
            :loading="userLoading"
            style="width: 100%"
          >
            <el-option v-for="u in users" :key="u.id" :label="`${u.realName || u.username}（${u.username}）`" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="sendForm.type" style="width: 100%">
            <el-option v-for="(label, value) in MESSAGE_TYPE" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="sendForm.title" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="sendForm.content" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sendVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSendSubmit">发送</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getRegistration, myInvitations } from '@/api/registration'
import { getAward } from '@/api/award'
import { getCompetition } from '@/api/competition'
import { getTeam } from '@/api/team'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { listUsers } from '@/api/user'
import { pageMyMessage, getMessage, markAllRead, deleteMessage, sendMessage } from '@/api/message'
import { MESSAGE_TYPE } from '@/constants/dict'

const props = defineProps({
  modelValue: Boolean,
  unread: { type: Number, default: 0 }
})
const emit = defineEmits(['update:modelValue', 'refresh'])

const router = useRouter()
const userStore = useUserStore()
const isAdmin = computed(() => userStore.role === 'ADMIN' || userStore.role === 'MAJOR_ADMIN')

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10 })

const detailVisible = ref(false)
const detail = ref({})

const sendVisible = ref(false)
const sendFormRef = ref()
const users = ref([])
const userLoading = ref(false)
const sendForm = reactive({ receiverId: null, title: '', content: '', type: 'SYSTEM' })
const sendRules = {
  receiverId: [{ required: true, message: '请选择接收人', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }]
}

function typeTagType(type) {
  if (type === 'AUDIT') return 'warning'
  if (type === 'NOTICE') return 'primary'
  return 'info'
}

function onOpen() {
  fetchList()
}

async function fetchList() {
  loading.value = true
  try {
    const res = await pageMyMessage(query)
    list.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  const res = await getMessage(row.id)
  detail.value = res.data
  detailVisible.value = true
  fetchList()
  emit('refresh')
}

async function openBusiness() {
  const { bizType, bizId } = detail.value
  let path
  let query = { detail: bizId, open: Date.now() }
  if (bizType === 'REGISTRATION') { await getRegistration(bizId); path = '/registration' }
  else if (bizType === 'TEAM') { await getTeam(bizId); path = '/team' }
  else if (bizType === 'AWARD') { await getAward(bizId); path = '/award' }
  else if (bizType === 'COMPETITION') { await getCompetition(bizId); path = '/competition' }
  else if (bizType === 'INVITATION') {
    const result = await myInvitations()
    if (!result.data.some(item => item.id === bizId)) { ElMessage.warning('该邀请不属于当前账号'); return }
    path = '/registration'; query = { invitation: bizId }
  } else return
  await router.push({ path, query })
  detailVisible.value = false
  emit('update:modelValue', false)
}

async function handleMarkAllRead() {
  await markAllRead()
  ElMessage.success('已全部标记为已读')
  fetchList()
  emit('refresh')
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除消息「${row.title}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteMessage(row.id)
      ElMessage.success('删除成功')
      fetchList()
      emit('refresh')
    })
    .catch(() => {})
}

async function searchUsers(keyword) {
  userLoading.value = true
  try {
    const res = await listUsers(keyword)
    users.value = res.data
  } finally {
    userLoading.value = false
  }
}

function resetSendForm() {
  sendForm.receiverId = null
  sendForm.title = ''
  sendForm.content = ''
  sendForm.type = 'SYSTEM'
  users.value = []
  sendFormRef.value?.clearValidate()
}

function openSend() {
  resetSendForm()
  sendVisible.value = true
}

function handleSendSubmit() {
  sendFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await sendMessage({
        receiverId: sendForm.receiverId,
        title: sendForm.title,
        content: sendForm.content,
        type: sendForm.type
      })
      ElMessage.success('发送成功')
      sendVisible.value = false
      fetchList()
      emit('refresh')
    } finally {
      submitting.value = false
    }
  })
}
</script>

<style scoped>
.msg-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-md);
  color: var(--color-text-secondary);
}

.msg-actions {
  display: flex;
  gap: var(--spacing-xs);
}

.unread-count {
  color: var(--color-primary);
  font-weight: 600;
}

.msg-title {
  cursor: pointer;
}

.unread-title {
  font-weight: 600;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--spacing-md);
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  flex-wrap: wrap;
  color: var(--color-text-secondary);
  font-size: var(--font-size-small);
}

.detail-content {
  white-space: pre-wrap;
  line-height: 1.8;
  color: var(--color-text);
}
</style>

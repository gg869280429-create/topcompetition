<template>
  <div class="teacher-config">
    <div v-for="(row, index) in modelValue" :key="index" class="teacher-row">
      <el-select :model-value="row.teacherId" filterable placeholder="选择教师" @update:model-value="change(index, 'teacherId', $event)">
        <el-option v-for="t in teachers" :key="t.id" :label="t.realName || t.username" :value="t.id"
          :disabled="modelValue.some((r, i) => i !== index && r.teacherId === t.id)" />
      </el-select>
      <el-input-number :model-value="row.quota" :min="1" aria-label="可指导团队数" @update:model-value="change(index, 'quota', $event)" />
      <el-button type="danger" link @click="emit('update:modelValue', modelValue.filter((_, i) => i !== index))">移除</el-button>
    </div>
    <el-button @click="emit('update:modelValue', [...modelValue, { teacherId: null, quota: 1 }])">添加指导教师</el-button>
    <div class="hint">为每位教师设置可指导的团队数量，教师无需自行报名。</div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listTeachers } from '@/api/user'
import { useUserStore } from '@/store/user'
const props = defineProps({ modelValue: { type: Array, required: true } })
const emit = defineEmits(['update:modelValue'])
const teachers = ref([])
const user = useUserStore()
function change(index, key, value) {
  emit('update:modelValue', props.modelValue.map((row, i) => i === index ? { ...row, [key]: value } : row))
}
onMounted(async () => {
  const res = await listTeachers()
  teachers.value = res.data.filter(t => user.role !== 'MAJOR_ADMIN' || t.majorId === user.userInfo?.majorId)
})
</script>

<style scoped>
.teacher-config { width: 100%; }
.teacher-row { display: flex; gap: 8px; margin-bottom: 8px; align-items: center; }
.teacher-row .el-select { flex: 1; min-width: 120px; }
.teacher-row .el-input-number { width: 130px; }
.hint { color: var(--color-text-secondary); font-size: 12px; line-height: 1.6; margin-top: 8px; }
@media (max-width: 600px) { .teacher-row { flex-wrap: wrap; } }
</style>

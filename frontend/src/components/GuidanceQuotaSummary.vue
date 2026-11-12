<template>
  <div v-loading="loading" class="quota-summary" aria-label="我的指导名额">
    <div v-for="item in items" :key="item.key" class="quota-stat"><span>{{ item.label }}</span><strong>{{ summary[item.key] }}</strong></div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { myQuotaSummary } from '@/api/guidance'
const summary = reactive({ remaining: 0, assigned: 0, total: 0 }), loading = ref(false)
const items = [{ key: 'remaining', label: '剩余指导名额' }, { key: 'assigned', label: '正在指导名额' }, { key: 'total', label: '总共指导名额' }]
async function refresh() {
  loading.value = true
  try { Object.assign(summary, (await myQuotaSummary()).data) }
  finally { loading.value = false }
}
defineExpose({ refresh })
onMounted(refresh)
</script>

<style scoped>
.quota-summary { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:16px; margin-bottom:24px; }
.quota-stat { padding:20px 24px; border:1px solid var(--color-border-light); background:var(--color-bg-card); border-radius:8px; }
.quota-stat span { display:block; font-size:13px; color:var(--color-text-secondary); }
.quota-stat strong { display:block; margin-top:12px; font-size:30px; font-weight:600; color:var(--color-text); }
@media(max-width:600px) { .quota-summary { gap:8px; }.quota-stat { padding:16px 10px; }.quota-stat span { font-size:12px; }.quota-stat strong { font-size:24px; } }
</style>

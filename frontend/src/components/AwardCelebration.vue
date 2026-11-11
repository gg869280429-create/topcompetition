<template>
  <el-dialog v-model="visible" title="恭喜获奖" width="min(92vw, 540px)" :close-on-click-modal="false" @closed="showNext">
    <div class="celebration"><span v-for="n in 30" :key="n" class="spark" :style="{'--angle': `${n * 12}deg`, '--delay':`${n % 6 * .1}s`}">✦</span>
      <div class="trophy">🏆</div><h2>{{ current.competition_title }}</h2>
      <p>{{ current.achievement_name }} · {{ levels[current.award_level] || current.award_level }}</p>
      <p>{{ current.team_name_snapshot }} · {{ current.award_time }}</p>
    </div>
    <template #footer><el-button type="primary" @click="visible = false">收下这份喜悦</el-button></template>
  </el-dialog>
</template>
<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import request from '@/api/request'
import { AWARD_LEVEL as levels } from '@/constants/dict'
const visible = ref(false), current = ref({})
let queue = [], timer, busy = false, stopped = false
async function showNext() {
  if (busy || visible.value || stopped) return
  busy = true
  try {
    while (queue.length && !stopped) {
      const item = queue.shift()
      const claimed = await request.post(`/award/${item.id}/celebrate`)
      if (claimed.data) { current.value = item; visible.value = true; break }
    }
  } finally { busy = false }
}
async function poll() {
  if (visible.value || busy || stopped) return
  try { queue = (await request.get('/award/celebrations')).data; await showNext() } catch { /* 后续轮询重试 */ }
}
onMounted(() => { poll(); timer = setInterval(poll, 15000) })
onUnmounted(() => { stopped = true; clearInterval(timer) })
</script>
<style scoped>
.celebration { position:relative; text-align:center; padding:32px 12px; overflow:hidden; min-height:210px; }
.trophy { font-size:56px; }
h2 { font-size:22px; position:relative; } p { position:relative; color:var(--el-text-color-regular); }
.spark { position:absolute; top:45%; left:50%; color:#e9a62f; animation:burst 1.6s var(--delay) ease-out 2 both; }
@keyframes burst { from { opacity:1; transform:rotate(var(--angle)) translateX(10px); } to { opacity:0; transform:rotate(var(--angle)) translateX(210px); } }
@media (prefers-reduced-motion:reduce) { .spark { animation:none; display:none; } }
</style>

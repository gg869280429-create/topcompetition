<template>
  <el-carousel v-if="news.length" height="280px" :interval="5000" class="news-carousel" aria-label="竞赛新闻轮播">
    <el-carousel-item v-for="item in news" :key="item.id">
      <a :href="item.officialUrl" target="_blank" rel="noopener noreferrer" class="news-slide">
        <img :src="item.coverUrl" :alt="item.title" />
        <div class="news-caption">{{ item.title }} <span>比赛官网 ↗</span></div>
      </a>
    </el-carousel-item>
  </el-carousel>
</template>
<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { contentCarousel, getContentCover } from '@/api/content'
const news = ref([])
let timer, stopped = false, loading = false
function release(items) { items.forEach(item => URL.revokeObjectURL(item.coverUrl)) }
async function refresh() {
  if (loading || stopped) return
  loading = true
  try {
    const response = await contentCarousel()
    const next = (await Promise.all(response.data.map(async item => {
      try {
        const url = new URL(item.officialUrl)
        if (!['http:','https:'].includes(url.protocol) || url.username || url.password) return null
        return { ...item, coverUrl: URL.createObjectURL(await getContentCover(item.id)) }
      } catch { return null }
    }))).filter(Boolean)
    if (stopped) release(next)
    else { const previous=news.value; news.value=next; release(previous) }
  } finally { loading=false }
}
function visible() { if (!document.hidden) refresh() }
onMounted(() => { refresh(); timer=setInterval(refresh,15000); document.addEventListener('visibilitychange',visible) })
onUnmounted(() => { stopped=true; clearInterval(timer); document.removeEventListener('visibilitychange',visible); release(news.value) })
</script>
<style scoped>
.news-carousel { margin-bottom:20px; border-radius:8px; overflow:hidden; }
.news-slide { display:block; height:100%; position:relative; color:white; }
.news-slide img { width:100%; height:100%; object-fit:cover; }
.news-caption { position:absolute; bottom:0; left:0; right:0; padding:18px 24px; background:rgba(0,0,0,.65); font-size:20px; }
.news-caption span { font-size:14px; margin-left:16px; }
</style>

<template>
  <section v-loading="loading && !news.length" aria-label="竞赛新闻轮播">
    <el-carousel v-if="news.length" :interval="5000" height="400px" class="news-carousel" arrow="always" trigger="click" :pause-on-hover="true">
      <el-carousel-item v-for="item in news" :key="item.id">
        <article class="news-slide">
          <a :href="item.officialUrl" target="_blank" rel="noopener noreferrer" :aria-label="`${item.title}：前往比赛官网`" class="cover-link">
            <img :src="item.coverUrl" :alt="item.title" />
          </a>
          <div class="news-caption">
            <span class="news-date">{{ item.publishTime || '竞赛新闻' }}</span>
            <h2>{{ item.title }}</h2>
            <p>{{ item.content || '了解竞赛安排、参赛要求与最新动态。' }}</p>
            <div class="news-actions">
              <el-button type="primary" @click="openDetail(item)">查看详情</el-button>
              <a :href="item.officialUrl" target="_blank" rel="noopener noreferrer">比赛官网 ↗</a>
            </div>
          </div>
        </article>
      </el-carousel-item>
    </el-carousel>
    <el-empty v-else-if="!loading" :description="failed ? '新闻暂时加载失败，请稍后刷新' : '暂无竞赛新闻，敬请期待'" />
  </section>

  <el-dialog v-model="detailVisible" :title="detail.title || '竞赛新闻详情'" width="min(760px, 92vw)" @closed="clearDetail">
    <div v-loading="detailLoading" class="news-detail">
      <template v-if="detail.id">
        <div class="detail-meta">{{ detail.authorName || '管理员' }} · {{ detail.publishTime || '-' }}</div>
        <img v-if="detailCover" :src="detailCover" :alt="detail.title" class="detail-cover" />
        <div class="detail-content">{{ detail.content || '暂无正文' }}</div>
        <p v-if="safeOfficialUrl(detail.officialUrl)"><a :href="detail.officialUrl" target="_blank" rel="noopener noreferrer">比赛官网 ↗</a></p>
      </template>
    </div>
  </el-dialog>
</template>
<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { contentCarousel, getContent, getContentCover } from '@/api/content'
const news = ref([])
const loading = ref(false), failed = ref(false)
const detailVisible = ref(false), detailLoading = ref(false), detail = ref({}), detailCover = ref('')
let timer, stopped = false, detailVersion = 0
function release(items) { items.forEach(item => URL.revokeObjectURL(item.coverUrl)) }
function safeOfficialUrl(value) {
  try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password }
  catch { return false }
}
function clearDetail() {
  detailVersion++
  if (detailCover.value) URL.revokeObjectURL(detailCover.value)
  detailCover.value = ''
  detail.value = {}
  detailLoading.value = false
}
async function openDetail(item) {
  clearDetail()
  const version = detailVersion
  detailVisible.value = true
  detailLoading.value = true
  try {
    const response = await getContent(item.id)
    if (stopped || version !== detailVersion || !detailVisible.value) return
    detail.value = response.data
    if (detail.value.coverFileId) {
      const cover = await getContentCover(item.id)
      if (!stopped && version === detailVersion && detailVisible.value) detailCover.value = URL.createObjectURL(cover)
    }
  } catch {
    if (version === detailVersion) detailVisible.value = false
  } finally { if (version === detailVersion) detailLoading.value = false }
}
async function refresh() {
  if (loading.value || stopped) return
  loading.value = true
  try {
    const response = await contentCarousel()
    const next = (await Promise.all(response.data.map(async item => {
      try {
        if (!safeOfficialUrl(item.officialUrl)) return null
        return { ...item, coverUrl: URL.createObjectURL(await getContentCover(item.id)) }
      } catch { return null }
    }))).filter(Boolean)
    if (stopped) release(next)
    else { const previous=news.value; news.value=next; release(previous) }
    failed.value = false
  } catch { failed.value = true }
  finally { loading.value=false }
}
function visible() { if (!document.hidden) refresh() }
onMounted(() => { refresh(); timer=setInterval(refresh,15000); document.addEventListener('visibilitychange',visible) })
onUnmounted(() => { stopped=true; clearInterval(timer); document.removeEventListener('visibilitychange',visible); release(news.value); clearDetail() })
</script>
<style scoped>
.news-carousel { margin-bottom:20px; border-radius:8px; overflow:hidden; }
.news-slide { height:100%; position:relative; color:white; background:#182b4d; }
.cover-link { display:block; height:100%; }
.cover-link img { width:100%; height:100%; object-fit:cover; }
.news-caption { position:absolute; bottom:0; left:0; right:0; padding:24px 48px 38px; background:rgba(0,0,0,.68); pointer-events:none; }
.news-date { font-size:12px; opacity:.85; }
.news-caption h2 { margin:8px 0; font-size:26px; line-height:1.3; }
.news-caption p { margin:0 0 16px; font-size:14px; line-height:1.7; overflow:hidden; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; }
.news-actions { display:flex; align-items:center; gap:20px; }
.news-actions a, .news-actions button { pointer-events:auto; }
.news-actions a { color:white; text-decoration:underline; }
.news-detail { min-height:100px; }
.detail-meta { margin-bottom:16px; color:var(--color-text-secondary); }
.detail-cover { display:block; max-width:100%; max-height:320px; margin:0 auto 20px; object-fit:contain; }
.detail-content { white-space:pre-wrap; line-height:1.8; overflow-wrap:anywhere; }
@media (max-width:600px) {
  .news-caption { padding:20px 24px 36px; }
  .news-caption h2 { font-size:20px; }
}
</style>

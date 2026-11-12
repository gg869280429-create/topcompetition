<template>
  <section v-loading="loading && !news.length" aria-label="竞赛新闻轮播">
    <el-carousel v-if="news.length" :interval="5000" class="news-carousel" arrow="always" trigger="click" :pause-on-hover="true" @change="activeIndex = $event">
      <el-carousel-item v-for="item in news" :key="item.id">
        <article class="news-slide">
          <a :href="item.officialUrl" target="_blank" rel="noopener noreferrer" :aria-label="`${item.title}：前往比赛官网`" class="cover-link">
            <img :src="item.coverUrl" :alt="item.title" />
          </a>
        </article>
      </el-carousel-item>
    </el-carousel>
    <div v-if="activeNews" class="news-info">
      <h2 :title="activeNews.title">{{ activeNews.title }}</h2>
      <el-button link type="primary" @click="openDetail(activeNews)">查看详情</el-button>
    </div>
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
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { contentCarousel, getContent, getContentCover } from '@/api/content'
const news = ref([])
const activeIndex = ref(0)
const activeNews = computed(() => news.value[activeIndex.value] || news.value[0])
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
.news-carousel { overflow:hidden; background:#f5f5f5; }
.news-carousel :deep(.el-carousel__container) { width:100%; height:auto; aspect-ratio:1226 / 460; min-height:220px; max-height:460px; }
.news-slide, .cover-link { display:block; height:100%; }
.cover-link img { display:block; width:100%; height:100%; object-fit:contain; }
.cover-link:focus-visible { outline:2px solid var(--el-color-primary); outline-offset:-3px; }
.news-carousel :deep(.el-carousel__arrow) { width:40px; height:68px; border-radius:0; background:transparent; color:#777; font-size:24px; }
.news-carousel :deep(.el-carousel__arrow:hover), .news-carousel :deep(.el-carousel__arrow:focus-visible) { background:rgba(0,0,0,.45); color:#fff; }
.news-carousel :deep(.el-carousel__arrow--left) { left:0; }
.news-carousel :deep(.el-carousel__arrow--right) { right:0; }
.news-carousel :deep(.el-carousel__indicators--horizontal) { left:auto; right:26px; bottom:16px; transform:none; }
.news-carousel :deep(.el-carousel__indicator--horizontal) { padding:4px; }
.news-carousel :deep(.el-carousel__button) { width:10px; height:10px; box-sizing:border-box; border:2px solid rgba(255,255,255,.65); border-radius:50%; background:#888; opacity:1; }
.news-carousel :deep(.el-carousel__indicator.is-active .el-carousel__button) { background:#fff; border-color:#888; }
.news-info { display:flex; align-items:center; justify-content:space-between; gap:20px; padding:16px 20px; background:var(--color-bg-card); }
.news-info h2 { min-width:0; margin:0; color:var(--color-text); font-size:15px; font-weight:500; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.news-info .el-button { flex-shrink:0; }
.news-detail { min-height:100px; }
.detail-meta { margin-bottom:16px; color:var(--color-text-secondary); }
.detail-cover { display:block; max-width:100%; max-height:320px; margin:0 auto 20px; object-fit:contain; }
.detail-content { white-space:pre-wrap; line-height:1.8; overflow-wrap:anywhere; }
@media (max-width:600px) {
  .news-carousel :deep(.el-carousel__container) { min-height:200px; }
  .news-carousel :deep(.el-carousel__indicators--horizontal) { right:12px; bottom:10px; }
  .news-info { padding:12px; gap:12px; }
}
</style>

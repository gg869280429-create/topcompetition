<template>
  <div v-if="isReader">
    <PageHeader title="首页" />
    <NewsCarousel />
  </div>
  <CompetitionPreview v-else title="首页" />
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/store/user'
import PageHeader from '@/components/PageHeader.vue'
import NewsCarousel from '@/components/NewsCarousel.vue'
import CompetitionPreview from '@/components/CompetitionPreview.vue'

const userStore = useUserStore()
const isReader = computed(() => ['STUDENT', 'TEACHER'].includes(userStore.role))
</script>

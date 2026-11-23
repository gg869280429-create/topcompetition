<template>
  <NewsCarousel v-if="isReader" />
  <CompetitionPreview v-if="!isReader && !isTeacher" title="首页" />
  <template v-if="isTeacher">
    <MyGuidance />
  </template>
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/store/user'
import NewsCarousel from '@/components/NewsCarousel.vue'
import CompetitionPreview from '@/components/CompetitionPreview.vue'
import MyGuidance from '@/components/MyGuidance.vue'

const userStore = useUserStore()
const isReader = computed(() => ['STUDENT'].includes(userStore.role))
const isTeacher = computed(() => userStore.role === 'TEACHER')
</script>

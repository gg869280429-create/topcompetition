<template>
  <div class="paginated-list">
    <slot :items="pageItems" />
    <el-pagination
      v-model:current-page="current"
      :page-size="size"
      :total="items.length"
      :page-sizes="[10, 20, 50]"
      :pager-count="5"
      layout="total, sizes, prev, pager, next, jumper"
      @update:page-size="changeSize"
    />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  items: { type: Array, required: true },
  resetKey: { type: [String, Number], default: '' }
})
const current = ref(1)
const size = ref(10)
const pageItems = computed(() => props.items.slice((current.value - 1) * size.value, current.value * size.value))

function changeSize(value) {
  size.value = value
  current.value = 1
}

watch(() => props.resetKey, () => { current.value = 1 })
watch(() => props.items.length, total => {
  current.value = Math.min(current.value, Math.max(1, Math.ceil(total / size.value)))
})
</script>

<style scoped>
.paginated-list { min-width: 0; }
.el-pagination {
  margin-top: 16px;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px 0;
}
</style>

<template>
  <div class="page-header">
    <div class="page-header-title">
      <slot name="title">{{ title }}</slot>
    </div>
    <div class="page-header-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    default: ''
  }
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-md);
  min-height: 32px;
}

.page-header-title {
  font-size: var(--font-size-large);
  font-weight: 600;
  color: var(--color-text);
}

.page-header-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}
</style>

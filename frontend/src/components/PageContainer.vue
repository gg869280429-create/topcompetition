<template>
  <div class="page-container">
    <slot />
  </div>
</template>

<script setup>
</script>

<style scoped>
.page-container {
  background-color: var(--color-bg-card);
  border-radius: var(--radius-base);
  box-shadow: var(--shadow-card);
  padding: var(--spacing-md);
  min-height: 100%;
}
</style>

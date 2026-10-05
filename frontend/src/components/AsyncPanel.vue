<template>
  <section class="panel">
    <p v-if="loading" class="muted" role="status">Loading…</p>
    <div v-else-if="error" class="error-box" role="alert">
      <p>{{ error }}</p>
      <button class="button button-secondary" type="button" @click="$emit('retry')">Try again</button>
    </div>
    <slot v-else />
  </section>
</template>

<script setup lang="ts">
defineProps<{ loading: boolean; error: string }>()
defineEmits<{ retry: [] }>()
</script>

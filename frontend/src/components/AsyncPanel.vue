<template>
  <section class="panel">
    <p v-if="loading" class="muted" role="status">{{ $t('common.loading') }}</p>
    <div v-else-if="error" class="error-box" role="alert">
      <p>{{ error }}</p>
      <button class="button button-secondary" type="button" :id="retryId" @click="$emit('retry')">{{ $t('common.retry') }}</button>
    </div>
    <slot v-else />
  </section>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ loading: boolean; error: string; retryId?: string }>(), { retryId: undefined })
defineEmits<{ retry: [] }>()
</script>

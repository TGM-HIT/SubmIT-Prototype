<template>
  <div class="pagination">
    <span>{{ $t('common.pageRecords', { current: page.page + 1, total: Math.max(page.totalPages, 1), count: page.totalElements }) }}</span>
    <div class="actions">
      <button class="button button-secondary button-small" :disabled="page.page === 0" @click="$emit('page', page.page - 1)">{{ $t('common.previous') }}</button>
      <button class="button button-secondary button-small" :disabled="page.page + 1 >= page.totalPages" @click="$emit('page', page.page + 1)">{{ $t('common.next') }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Page } from '../../api/client'
defineProps<{ page: Page<unknown> }>()
defineEmits<{ page: [number] }>()
</script>

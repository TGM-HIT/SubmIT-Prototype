<template>
  <div class="pagination">
    <span id="pager-page-status" :data-page="page.page">{{ $t('common.pageRecords', { current: number(page.page + 1), total: number(Math.max(page.totalPages, 1)), count: number(page.totalElements) }) }}</span>
    <div class="actions">
      <button id="pager-previous" class="button button-secondary button-small" type="button" :disabled="page.page === 0" @click="$emit('page', page.page - 1)">{{ $t('common.previous') }}</button>
      <button id="pager-next" class="button button-secondary button-small" type="button" :disabled="page.page + 1 >= page.totalPages" @click="$emit('page', page.page + 1)">{{ $t('common.next') }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Page } from '../../api/client'
import { formatNumber } from '../../i18n'
defineProps<{ page: Page<unknown> }>()
defineEmits<{ page: [number] }>()
const number = formatNumber
</script>

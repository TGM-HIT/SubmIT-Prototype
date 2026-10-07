<template>
  <div class="modal-backdrop" :style="{ zIndex }">
    <section
      ref="dialog"
      class="modal-dialog"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="titleId"
      tabindex="-1"
      @keydown="handleKeydown"
    >
      <header class="modal-header">
        <h2 :id="titleId">{{ title }}</h2>
        <button class="button button-secondary button-small" type="button" aria-label="Close dialog" @click="$emit('close')">Close</button>
      </header>
      <div class="modal-body"><slot /></div>
    </section>
  </div>
</template>

<script lang="ts">
let bodyOverflow = ''
let openDialogs = 0

function openModal(): number {
  if (openDialogs === 0) {
    bodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
  }
  openDialogs += 1
  return 50 + openDialogs * 10
}

function closeModal(): void {
  openDialogs -= 1
  if (openDialogs === 0) document.body.style.overflow = bodyOverflow
}
</script>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, useId } from 'vue'

const props = defineProps<{ title: string; returnFocus?: HTMLElement | null; fallbackFocus?: HTMLElement | null }>()
const emit = defineEmits<{ close: [] }>()

const titleId = useId()
const dialog = ref<HTMLElement | null>(null)
const zIndex = ref(50)
const previousFocus = props.returnFocus ?? (document.activeElement instanceof HTMLElement ? document.activeElement : null)

onMounted(async () => {
  zIndex.value = openModal()
  await nextTick()
  const firstControl = dialog.value?.querySelector<HTMLElement>(focusableSelector)
  ;(firstControl ?? dialog.value)?.focus()
})

onBeforeUnmount(() => {
  closeModal()
  void nextTick(() => {
    const target = previousFocus?.isConnected
      ? previousFocus
      : previousFocus?.id
        ? document.getElementById(previousFocus.id)
        : null
    if (target?.isConnected) {
      target.focus()
      return
    }

    const remainingDialogs = [...document.querySelectorAll<HTMLElement>('[role="dialog"]')]
    const activeDialog = remainingDialogs.sort((left, right) =>
      Number(right.parentElement?.style.zIndex ?? 0) - Number(left.parentElement?.style.zIndex ?? 0))[0]
    const fallback = activeDialog?.querySelector<HTMLElement>(focusableSelector)
      ?? activeDialog
      ?? (props.fallbackFocus?.isConnected ? props.fallbackFocus : null)
    fallback?.focus()
  })
})

const focusableSelector = 'a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])'

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    emit('close')
    return
  }
  if (event.key !== 'Tab' || !dialog.value) return

  const focusable = [...dialog.value.querySelectorAll<HTMLElement>(focusableSelector)]
    .filter((element) => element.getClientRects().length > 0)
  if (!focusable.length) {
    event.preventDefault()
    dialog.value.focus()
    return
  }

  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && (document.activeElement === first || !dialog.value.contains(document.activeElement))) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && (document.activeElement === last || !dialog.value.contains(document.activeElement))) {
    event.preventDefault()
    first.focus()
  }
}
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 1rem;
  background: rgb(16 25 43 / 58%);
}

.modal-dialog {
  display: flex;
  flex-direction: column;
  width: min(100%, 68rem);
  max-height: calc(100dvh - 2rem);
  min-width: 0;
  overflow: hidden;
  border: 1px solid #e4e9f1;
  border-radius: 14px;
  background: #f5f7fb;
  box-shadow: 0 24px 80px rgb(16 36 70 / 24%);
}

.modal-header {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #e3e8f0;
  background: white;
}

.modal-header h2 {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}

.modal-body {
  min-height: 0;
  overflow: auto;
  padding: 1.25rem;
  overflow-wrap: anywhere;
}

@media (max-width: 600px) {
  .modal-backdrop {
    padding: 0.5rem;
  }

  .modal-dialog {
    max-height: calc(100dvh - 1rem);
  }

  .modal-header,
  .modal-body {
    padding: 0.875rem;
  }
}
</style>

<template>
  <div class="account-name-editor">
    <button
      v-if="!editing"
      ref="editButton"
      class="button button-secondary button-small"
      type="button"
      aria-label="Edit name"
      @click="beginEdit"
    >Edit name</button>
    <form v-else class="name-edit-form" @submit.prevent="save">
      <div class="field">
        <label class="label" :for="inputId">Account name</label>
        <input :id="inputId" v-model="draft" class="input" autocomplete="name" required />
      </div>
      <p class="muted">1–120 characters after trimming.</p>
      <p v-if="error" class="error-box" role="alert">{{ error }}</p>
      <div class="actions">
        <button class="button button-small" type="submit" :disabled="saving">Save name</button>
        <button class="button button-secondary button-small" type="button" :disabled="saving" @click="cancel">Cancel</button>
      </div>
    </form>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import type { components } from '../../api/schema'
import { userApi } from '../../api/services'

const props = defineProps<{ account: components['schemas']['UserResponse'] }>()
const emit = defineEmits<{
  saved: [account: components['schemas']['UserResponse']]
}>()
const editButton = ref<HTMLButtonElement | null>(null)
const editing = ref(false)
const saving = ref(false)
const draft = ref('')
const error = ref('')
const inputId = `account-name-${props.account.id}`

function beginEdit(): void {
  draft.value = props.account.displayName ?? ''
  error.value = ''
  editing.value = true
}

async function closeEditor(): Promise<void> {
  editing.value = false
  await nextTick()
  editButton.value?.focus()
}

async function cancel(): Promise<void> {
  error.value = ''
  await closeEditor()
}

async function save(): Promise<void> {
  const displayName = draft.value.trim()
  if (!displayName || Array.from(displayName).length > 120) {
    error.value = 'Account name must contain 1 to 120 characters after trimming.'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const updated = await userApi.changeName(props.account.id ?? '', displayName)
    emit('saved', updated)
    await closeEditor()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Unable to save the account name.'
  } finally {
    saving.value = false
  }
}
</script>

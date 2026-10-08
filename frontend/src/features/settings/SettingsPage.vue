<template>
  <section class="page">
    <p class="page-intro">Manage your personal account name. Your username and role are used to identify your account and cannot be changed here.</p>
    <article class="panel settings-panel">
      <p class="eyebrow">ACCOUNT NAME</p>
      <p class="settings-saved-name"><span class="muted">Saved account name</span><strong>{{ auth.user?.displayName }}</strong></p>
      <form @submit.prevent="save">
        <div class="field">
          <label class="label" for="settings-account-name">Account name</label>
          <input
            id="settings-account-name"
            ref="nameInput"
            v-model="draft"
            class="input"
            autocomplete="name"
            aria-describedby="account-name-guidance"
          />
          <span id="account-name-guidance" class="muted">1–120 characters after trimming.</span>
        </div>
        <p v-if="error" class="error-box" role="alert">{{ error }}</p>
        <p v-if="success" class="success-note" role="status">{{ success }}</p>
        <div class="actions">
          <button class="button" type="submit" :disabled="saving">Save changes</button>
          <button class="button button-secondary" type="button" :disabled="saving" @click="cancel">Cancel</button>
        </div>
      </form>
    </article>
  </section>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import type { components } from '../../api/schema'
import { userApi } from '../../api/services'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const nameInput = ref<HTMLInputElement | null>(null)
const draft = ref(auth.user?.displayName ?? '')
const error = ref('')
const success = ref('')
const saving = ref(false)

watch(() => auth.user?.displayName, (name) => {
  if (name !== undefined) draft.value = name
})

async function focusName(): Promise<void> {
  await nextTick()
  nameInput.value?.focus()
}

async function cancel(): Promise<void> {
  draft.value = auth.user?.displayName ?? ''
  error.value = ''
  success.value = ''
  await focusName()
}

async function save(): Promise<void> {
  const displayName = draft.value.trim()
  if (!displayName || Array.from(displayName).length > 120) {
    error.value = 'Account name must contain 1 to 120 characters after trimming.'
    success.value = ''
    return
  }

  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const updated: components['schemas']['UserResponse'] = await userApi.changeName(auth.user?.id ?? '', displayName)
    auth.updateUser(updated)
    draft.value = updated.displayName ?? displayName
    success.value = 'Account name saved successfully.'
    await focusName()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Unable to save the account name.'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.settings-panel { max-width: 680px; }
.settings-saved-name { display: grid; gap: 5px; margin: 0 0 22px; }
.settings-saved-name strong { font-size: 16px; overflow-wrap: anywhere; }
.settings-panel .error-box { margin: 14px 0; }
</style>

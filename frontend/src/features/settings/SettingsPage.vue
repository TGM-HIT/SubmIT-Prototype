<template>
  <section class="page">
    <p class="page-intro">{{ $t('settings.intro') }}</p>
    <article class="panel settings-panel">
      <p class="eyebrow">{{ $t('settings.accountNameHeading') }}</p>
      <p class="settings-saved-name"><span class="muted">{{ $t('settings.savedName') }}</span><strong>{{ auth.user?.displayName }}</strong></p>
      <form @submit.prevent="saveName">
        <div class="field">
          <label class="label" for="settings-account-name">{{ $t('settings.accountName') }}</label>
          <input
            id="settings-account-name"
            ref="nameInput"
            v-model="draft"
            class="input"
            autocomplete="name"
            aria-describedby="account-name-guidance"
          />
          <span id="account-name-guidance" class="muted">{{ $t('settings.nameGuidance') }}</span>
        </div>
        <p v-if="error" class="error-box" role="alert">{{ error }}</p>
        <p v-if="success" class="success-note" role="status">{{ success }}</p>
        <div class="actions">
          <button class="button" type="submit" :disabled="saving">{{ $t('settings.save') }}</button>
          <button class="button button-secondary" type="button" :disabled="saving" @click="cancelName">{{ $t('settings.cancel') }}</button>
        </div>
      </form>
      <hr />
      <form @submit.prevent="saveLanguage">
        <p class="eyebrow">{{ $t('settings.languageHeading') }}</p>
        <p class="muted">{{ $t('settings.languageIntro') }}</p>
        <div class="field">
          <label class="label" for="settings-language">{{ $t('settings.language') }}</label>
          <select id="settings-language" ref="languageInput" v-model="languageDraft" class="select">
            <option value="en">{{ $t('settings.english') }}</option>
            <option value="de">{{ $t('settings.german') }}</option>
          </select>
        </div>
        <p v-if="languageError" class="error-box" role="alert">{{ languageError }}</p>
        <p v-if="languageSuccess" class="success-note" role="status">{{ languageSuccess }}</p>
        <div class="actions">
          <button class="button" type="submit" :disabled="languageSaving">{{ $t('settings.saveLanguage') }}</button>
          <button class="button button-secondary" type="button" :disabled="languageSaving" @click="cancelLanguage">{{ $t('settings.cancel') }}</button>
        </div>
      </form>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { components } from '../../api/schema'
import { userApi } from '../../api/services'
import { ApiError } from '../../api/client'
import { useAuthStore } from '../../stores/auth'
import type { Language } from '../../i18n'
import { i18n } from '../../i18n'

const auth = useAuthStore()
const nameInput = ref<HTMLInputElement | null>(null)
const languageInput = ref<HTMLSelectElement | null>(null)
const draft = ref(auth.user?.displayName ?? '')
const savedName = ref(auth.user?.displayName ?? '')
const nameErrorState = ref<{ key?: string; backend?: string } | null>(null)
const nameSuccessState = ref(false)
const error = computed(() => nameErrorState.value?.backend
  ?? (nameErrorState.value?.key ? String(i18n.global.t(nameErrorState.value.key)) : ''))
const success = computed(() => nameSuccessState.value ? String(i18n.global.t('settings.nameSaved')) : '')
const saving = ref(false)
const languageDraft = ref<Language>(auth.user?.language ?? 'en')
const languageErrorState = ref<{ key?: string; backend?: string } | null>(null)
const languageSuccessState = ref(false)
const languageError = computed(() => languageErrorState.value?.backend
  ?? (languageErrorState.value?.key ? String(i18n.global.t(languageErrorState.value.key)) : ''))
const languageSuccess = computed(() => languageSuccessState.value
  ? String(i18n.global.t('settings.languageSaved')) : '')
const languageSaving = ref(false)

watch(() => auth.user?.displayName, (name) => {
  if (name !== undefined) {
    if (draft.value === savedName.value) draft.value = name
    savedName.value = name
  }
})
watch(() => auth.user?.language, (language) => {
  if (language === 'en' || language === 'de') languageDraft.value = language
})

async function focusName(): Promise<void> {
  await nextTick()
  nameInput.value?.focus()
}

async function cancelName(): Promise<void> {
  draft.value = auth.user?.displayName ?? ''
  nameErrorState.value = null
  nameSuccessState.value = false
  await focusName()
}

async function saveName(): Promise<void> {
  const displayName = draft.value.trim()
  if (!displayName || Array.from(displayName).length > 120) {
    nameErrorState.value = { key: 'settings.invalidName' }
    nameSuccessState.value = false
    return
  }

  saving.value = true
  nameErrorState.value = null
  nameSuccessState.value = false
  try {
    const updated: components['schemas']['UserResponse'] = await userApi.changeName(auth.user?.id ?? '', displayName)
    auth.updateUser(updated)
    draft.value = updated.displayName ?? displayName
    nameSuccessState.value = true
    await focusName()
  } catch (cause) {
    nameErrorState.value = cause instanceof ApiError && cause.source === 'backend'
      ? { backend: cause.message }
      : { key: 'settings.nameSaveFailed' }
  } finally {
    saving.value = false
  }
}

async function focusLanguage(): Promise<void> {
  await nextTick()
  languageInput.value?.focus()
}

async function cancelLanguage(): Promise<void> {
  languageDraft.value = auth.user?.language ?? 'en'
  languageErrorState.value = null
  languageSuccessState.value = false
  await focusLanguage()
}

async function saveLanguage(): Promise<void> {
  if (languageDraft.value !== 'en' && languageDraft.value !== 'de') {
    languageErrorState.value = { key: 'settings.languageSaveFailed' }
    languageSuccessState.value = false
    return
  }
  languageSaving.value = true
  languageErrorState.value = null
  languageSuccessState.value = false
  try {
    const updated: components['schemas']['UserResponse'] =
      await userApi.changeLanguage(auth.user?.id ?? '', languageDraft.value)
    auth.updateUser(updated)
    languageDraft.value = updated.language
    languageSuccessState.value = true
    await focusLanguage()
  } catch (cause) {
    languageErrorState.value = cause instanceof ApiError && cause.source === 'backend'
      ? { backend: cause.message }
      : { key: 'settings.languageSaveFailed' }
  } finally {
    languageSaving.value = false
  }
}
</script>

<style scoped>
.settings-panel { max-width: 680px; }
.settings-saved-name { display: grid; gap: 5px; margin: 0 0 22px; }
.settings-saved-name strong { font-size: 16px; overflow-wrap: anywhere; }
.settings-panel .error-box { margin: 14px 0; }
</style>

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
import { nextTick, ref, watch } from 'vue'
import type { components } from '../../api/schema'
import { userApi } from '../../api/services'
import { useAuthStore } from '../../stores/auth'
import type { Language } from '../../i18n'
import { i18n } from '../../i18n'

const auth = useAuthStore()
const nameInput = ref<HTMLInputElement | null>(null)
const languageInput = ref<HTMLSelectElement | null>(null)
const draft = ref(auth.user?.displayName ?? '')
const error = ref('')
const success = ref('')
const saving = ref(false)
const languageDraft = ref<Language>(auth.user?.language ?? 'en')
const languageError = ref('')
const languageSuccess = ref('')
const languageSaving = ref(false)

watch(() => auth.user?.displayName, (name) => {
  if (name !== undefined) draft.value = name
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
  error.value = ''
  success.value = ''
  await focusName()
}

async function saveName(): Promise<void> {
  const displayName = draft.value.trim()
  if (!displayName || Array.from(displayName).length > 120) {
    error.value = String(i18n.global.t('settings.invalidName'))
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
    success.value = String(i18n.global.t('settings.nameSaved'))
    await focusName()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(i18n.global.t('settings.nameSaveFailed'))
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
  languageError.value = ''
  languageSuccess.value = ''
  await focusLanguage()
}

async function saveLanguage(): Promise<void> {
  if (languageDraft.value !== 'en' && languageDraft.value !== 'de') {
    languageError.value = String(i18n.global.t('settings.languageSaveFailed'))
    languageSuccess.value = ''
    return
  }
  languageSaving.value = true
  languageError.value = ''
  languageSuccess.value = ''
  try {
    const updated: components['schemas']['UserResponse'] =
      await userApi.changeLanguage(auth.user?.id ?? '', languageDraft.value)
    auth.updateUser(updated)
    languageDraft.value = updated.language
    languageSuccess.value = String(i18n.global.t('settings.languageSaved'))
    await focusLanguage()
  } catch (cause) {
    languageError.value = cause instanceof Error
      ? cause.message
      : String(i18n.global.t('settings.languageSaveFailed'))
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

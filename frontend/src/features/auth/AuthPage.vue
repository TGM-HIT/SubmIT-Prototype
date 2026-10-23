<template>
  <main class="auth-screen">
    <section class="auth-art">
      <RouterLink class="brand" to="/">Subm<span>IT</span></RouterLink>
      <div>
        <h1>{{ $t('auth.headline') }}</h1>
        <p>{{ $t('auth.intro') }}</p>
      </div>
      <small class="muted">{{ $t('auth.lifecycle') }}</small>
    </section>
    <section class="auth-card-wrap">
      <form class="auth-card" @submit.prevent="submit">
        <h2>{{ mode === 'login' ? $t('auth.welcome') : $t('auth.createTitle') }}</h2>
        <p>{{ mode === 'login' ? $t('auth.loginIntro') : $t('auth.registerIntro') }}</p>
        <div class="field">
          <label class="label" for="username">{{ $t('auth.username') }}</label>
          <input id="username" v-model.trim="username" class="input" autocomplete="username" required minlength="3" maxlength="80" />
        </div>
        <div v-if="mode === 'register'" class="field">
          <label class="label" for="display-name">{{ $t('auth.accountName') }}</label>
          <input id="display-name" v-model="displayName" class="input" autocomplete="name" required />
          <span class="muted">{{ $t('auth.accountNameHelp') }}</span>
        </div>
        <div class="field">
          <label class="label" for="password">{{ $t('auth.password') }}</label>
          <input id="password" v-model="password" class="input" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" required :minlength="mode === 'register' ? 12 : 1" maxlength="128" />
          <span v-if="mode === 'register'" class="muted">{{ $t('auth.passwordHelp') }}</span>
        </div>
        <div v-if="task.error.value" class="error-box" role="alert">{{ task.error.value }}</div>
        <button class="button" type="submit" :disabled="task.loading.value">{{ task.loading.value ? $t('auth.wait') : mode === 'login' ? $t('auth.signIn') : $t('auth.createAccount') }}</button>
        <p class="auth-switch">
          {{ mode === 'login' ? $t('auth.newHere') : $t('auth.alreadyHaveAccount') }}
          <RouterLink :to="mode === 'login' ? '/register' : '/login'">{{ mode === 'login' ? $t('auth.register') : $t('auth.signIn') }}</RouterLink>
        </p>
      </form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useTask } from '../../composables/useTask'
import { i18n } from '../../i18n'

defineProps<{ mode: 'login' | 'register' }>()
const username = ref('')
const password = ref('')
const displayName = ref('')
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const task = useTask()

async function submit(): Promise<void> {
  const mode = String(route.name)
  const done = await task.run(
    () => mode === 'register'
      ? auth.register({ username: username.value, password: password.value, displayName: displayName.value })
      : auth.login({ username: username.value, password: password.value }),
    String(i18n.global.t('auth.signedIn')),
  )
  if (done === undefined && task.error.value) return
  await router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : '/')
}
</script>

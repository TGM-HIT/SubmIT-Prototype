<template>
  <main class="auth-screen">
    <section class="auth-art">
      <RouterLink class="brand" to="/">Project<span>Ideas</span></RouterLink>
      <div>
        <h1>Make good ideas happen.</h1>
        <p>One place for students and teachers to shape, review and deliver meaningful school projects.</p>
      </div>
      <small class="muted">A reviewed, collaborative project lifecycle</small>
    </section>
    <section class="auth-card-wrap">
      <form class="auth-card" @submit.prevent="submit">
        <h2>{{ mode === 'login' ? 'Welcome back' : 'Create your student account' }}</h2>
        <p>{{ mode === 'login' ? 'Sign in to continue to your project workspace.' : 'An administrator must assign your class before you can sign in.' }}</p>
        <div class="field">
          <label class="label" for="username">Username</label>
          <input id="username" v-model.trim="username" class="input" autocomplete="username" required minlength="3" maxlength="80" />
        </div>
        <div v-if="mode === 'register'" class="field">
          <label class="label" for="display-name">Account name</label>
          <input id="display-name" v-model="displayName" class="input" autocomplete="name" required />
          <span class="muted">Shown in your workspace. Leading and trailing spaces are removed; up to 120 characters.</span>
        </div>
        <div class="field">
          <label class="label" for="password">Password</label>
          <input id="password" v-model="password" class="input" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" required :minlength="mode === 'register' ? 12 : 1" maxlength="128" />
          <span v-if="mode === 'register'" class="muted">Use at least 12 characters.</span>
        </div>
        <div v-if="task.error.value" class="error-box" role="alert">{{ task.error.value }}</div>
        <p v-if="task.success.value" class="success-note" role="status">{{ task.success.value }}</p>
        <button class="button" type="submit" :disabled="task.loading.value">{{ task.loading.value ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account' }}</button>
        <p class="auth-switch">
          {{ mode === 'login' ? 'New to the platform?' : 'Already have an account?' }}
          <RouterLink :to="mode === 'login' ? '/register' : '/login'">{{ mode === 'login' ? 'Register' : 'Sign in' }}</RouterLink>
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
  if (mode === 'register') {
    await task.run(
      () => auth.register({ username: username.value, password: password.value, displayName: displayName.value }),
      'Account created. An administrator must assign your class before you can sign in.',
    )
    return
  }
  const done = await task.run(() => auth.login({ username: username.value, password: password.value }),
    'Signed in successfully.')
  if (done === undefined) return
  await router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : '/')
}
</script>

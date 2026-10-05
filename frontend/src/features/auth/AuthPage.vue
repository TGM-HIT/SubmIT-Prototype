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
        <p>{{ mode === 'login' ? 'Sign in to continue to your project workspace.' : 'Registration creates a student account.' }}</p>
        <div class="field">
          <label class="label" for="username">Username</label>
          <input id="username" v-model.trim="username" class="input" autocomplete="username" required minlength="3" maxlength="80" />
        </div>
        <div class="field">
          <label class="label" for="password">Password</label>
          <input id="password" v-model="password" class="input" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" required :minlength="mode === 'register' ? 12 : 1" maxlength="128" />
          <span v-if="mode === 'register'" class="muted">Use at least 12 characters.</span>
        </div>
        <div v-if="task.error.value" class="error-box" role="alert">{{ task.error.value }}</div>
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
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const task = useTask()

async function submit(): Promise<void> {
  const mode = String(route.name)
  const done = await task.run(
    () => mode === 'register'
      ? auth.register({ username: username.value, password: password.value })
      : auth.login({ username: username.value, password: password.value }),
    'Signed in successfully.',
  )
  if (done === undefined && task.error.value) return
  await router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : '/')
}
</script>

<template>
  <div class="app-frame">
    <aside class="sidebar">
      <RouterLink class="brand" to="/">Subm<span>IT</span></RouterLink>
      <p class="eyebrow">WORKSPACE</p>
      <nav aria-label="Main navigation" class="nav-list">
        <RouterLink
          v-for="item in navigation"
          :key="item.to"
          :to="item.to"
          :active-class="item.to === '/' ? 'overview-ancestor-route' : 'router-link-active'"
          class="nav-link"
        >
          <span aria-hidden="true">{{ item.icon }}</span>{{ item.label }}
        </RouterLink>
      </nav>
      <div class="sidebar-bottom">
        <RouterLink to="/archive" class="nav-link"><span aria-hidden="true">▣</span>Archive management</RouterLink>
        <RouterLink v-if="auth.user" to="/settings" class="nav-link"><span aria-hidden="true">⚙</span>User Settings</RouterLink>
        <div v-if="auth.user" class="profile">
          <div class="avatar" aria-hidden="true">{{ (auth.user.displayName ?? 'A').slice(0, 1).toUpperCase() }}</div>
          <div class="profile-meta"><strong>{{ auth.user.displayName }}</strong><small>{{ auth.user.username }} · {{ auth.user.role }}</small></div>
          <button class="icon-button" aria-label="Sign out" title="Sign out" @click="signOut">⇥</button>
        </div>
      </div>
    </aside>
    <main id="main-content" class="main-area">
      <header class="topbar">
        <div><p class="eyebrow">SCHOOL PROJECTS</p><h1>{{ pageTitle }}</h1></div>
        <span class="role-chip">{{ auth.user?.role }}</span>
      </header>
      <RouterView />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const allItems = [
  { to: '/', label: 'Overview', icon: '⌂', roles: ['STUDENT', 'TEACHER', 'ADMIN'] },
  { to: '/ideas', label: 'Ideas & reviews', icon: '✧', roles: ['STUDENT', 'TEACHER', 'ADMIN'] },
  { to: '/applications', label: 'Applications', icon: '☷', roles: ['STUDENT', 'TEACHER', 'ADMIN'] },
  { to: '/allocation', label: 'Allocation', icon: '⇄', roles: ['TEACHER', 'ADMIN'] },
  { to: '/formal-application', label: 'Group application', icon: '▤', roles: ['STUDENT', 'TEACHER'] },
  { to: '/grading', label: 'Rubrics & progress', icon: '◷', roles: ['STUDENT', 'TEACHER', 'ADMIN'] },
  { to: '/archive', label: 'Project archive', icon: '▣', roles: ['STUDENT', 'TEACHER', 'ADMIN'] },
  { to: '/admin', label: 'Administration', icon: '⚙', roles: ['ADMIN'] },
]
const navigation = computed(() => allItems.filter((item) =>
  Boolean(auth.user?.role && item.roles.includes(auth.user.role))))
const pageTitle = computed(() => String(route.meta.title ?? {
  dashboard: 'Overview',
  ideas: 'Ideas & reviews',
  applications: 'Applications',
  allocation: 'Allocation',
  'formal-application': 'Group application',
  grading: 'Rubrics & progress',
  archive: 'Project archive',
  admin: 'Administration',
  settings: 'User Settings',
}[String(route.name)] ?? 'SubmIT'))
watch(() => auth.isAuthenticated, (signedIn) => {
  if (!signedIn && route.name !== 'public-archive' && route.name !== 'login' && route.name !== 'register') {
    void router.replace('/login')
  }
})

function signOut(): void {
  auth.logout()
  void router.replace('/login')
}
</script>

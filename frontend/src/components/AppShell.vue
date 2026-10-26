<template>
  <div class="app-frame">
    <aside class="sidebar">
      <RouterLink class="brand" to="/">Subm<span>IT</span></RouterLink>
      <p class="eyebrow">{{ $t('navigation.workspace') }}</p>
      <nav :aria-label="$t('navigation.main')" class="nav-list">
        <RouterLink
          v-for="item in navigation"
          :key="item.to"
          :to="item.to"
          :active-class="item.to === '/' ? 'overview-ancestor-route' : 'router-link-active'"
          class="nav-link"
        >
          <span aria-hidden="true">{{ item.icon }}</span><span class="nav-label">{{ item.label }}</span>
        </RouterLink>
      </nav>
      <div class="sidebar-bottom">
        <RouterLink to="/archive" class="nav-link"><span aria-hidden="true">▣</span>{{ $t('navigation.archiveManagement') }}</RouterLink>
        <RouterLink v-if="auth.user" to="/settings" class="nav-link"><span aria-hidden="true">⚙</span>{{ $t('navigation.settings') }}</RouterLink>
        <div v-if="auth.user" class="profile">
          <div class="avatar" aria-hidden="true">{{ (auth.user.displayName ?? 'A').slice(0, 1).toUpperCase() }}</div>
          <div class="profile-meta"><strong>{{ auth.user.displayName }}</strong><small>{{ auth.user.username }} · {{ roleLabel(auth.user.roles) }}</small></div>
          <button class="icon-button" :aria-label="$t('navigation.signOut')" :title="$t('navigation.signOut')" @click="signOut">⇥</button>
        </div>
      </div>
    </aside>
    <main id="main-content" class="main-area">
      <header class="topbar">
        <div><p class="eyebrow">{{ $t('navigation.schoolProjects') }}</p><h1>{{ pageTitle }}</h1></div>
        <span class="role-chip">{{ auth.user ? roleLabel(auth.user.roles) : '' }}</span>
      </header>
      <RouterView />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { i18n } from '../i18n'
import type { Role } from '../api/client'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const allItems = [
  { to: '/', key: 'navigation.overview', icon: '⌂', roles: ['STUDENT', 'TEACHER', 'ADMIN'] },
  { to: '/ideas', key: 'navigation.ideas', icon: '✧', roles: ['STUDENT', 'TEACHER', 'ADMIN'] },
  { to: '/applications', key: 'navigation.applications', icon: '☷', roles: ['STUDENT', 'TEACHER', 'ADMIN'] },
  { to: '/allocation', key: 'navigation.allocation', icon: '⇄', roles: ['TEACHER', 'ADMIN'] },
  { to: '/formal-application', key: 'navigation.formal', icon: '▤', roles: ['STUDENT', 'TEACHER'] },
  { to: '/grading', key: 'navigation.grading', icon: '◷', roles: ['STUDENT', 'TEACHER', 'ADMIN'] },
  { to: '/archive', key: 'navigation.archive', icon: '▣', roles: ['STUDENT', 'TEACHER', 'ADMIN'] },
  { to: '/admin', key: 'navigation.administration', icon: '⚙', roles: ['ADMIN'] },
]
const navigation = computed(() => allItems.filter((item) =>
  item.roles.some((role) => canAccessRole(role))).map((item) => ({
    ...item,
    label: String(i18n.global.t(item.key)),
  })))
const pageTitleKey = computed(() => ({
  dashboard: 'navigation.overview',
  ideas: 'navigation.ideas',
  applications: 'navigation.applications',
  allocation: 'navigation.allocation',
  'formal-application': 'navigation.formal',
  grading: 'navigation.grading',
  archive: 'navigation.archive',
  admin: 'navigation.administration',
  settings: 'navigation.settings',
}[String(route.name)] ?? 'navigation.overview'))
const pageTitle = computed(() => String(i18n.global.t(pageTitleKey.value)))
function roleLabel(roles: string[]): string {
  return roles.map((role) => {
    const key = ({ STUDENT: 'navigation.student', TEACHER: 'navigation.teacher', ADMIN: 'navigation.administrator' } as Record<string, string>)[role]
    return key ? String(i18n.global.t(key)) : role
  }).join(', ')
}
function canAccessRole(role: string): boolean {
  return auth.user?.roles.includes(role as Role) ?? false
}
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

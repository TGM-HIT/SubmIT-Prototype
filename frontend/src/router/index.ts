import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AppShell from '../components/AppShell.vue'
import AuthPage from '../features/auth/AuthPage.vue'
import DashboardPage from '../features/dashboard/DashboardPage.vue'
import IdeasPage from '../features/ideas/IdeasPage.vue'
import ApplicationsPage from '../features/applications/ApplicationsPage.vue'
import AllocationPage from '../features/allocation/AllocationPage.vue'
import FormalApplicationPage from '../features/formal/FormalApplicationPage.vue'
import GradingPage from '../features/grading/GradingPage.vue'
import RubricsPage from '../features/rubrics/RubricsPage.vue'
import ArchivePage from '../features/archive/ArchivePage.vue'
import AdminPage from '../features/admin/AdminPage.vue'
import SettingsPage from '../features/settings/SettingsPage.vue'
import TemplatesPage from '../features/templates/TemplatesPage.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: AuthPage, props: { mode: 'login' } },
    { path: '/register', name: 'register', component: AuthPage, props: { mode: 'register' } },
    { path: '/archive/:projectId', name: 'public-archive', component: ArchivePage, props: { publicOnly: true } },
    {
      path: '/',
      component: AppShell,
      children: [
        { path: '', name: 'dashboard', component: DashboardPage },
        { path: 'ideas', name: 'ideas', component: IdeasPage },
        { path: 'templates', name: 'templates', component: TemplatesPage },
        { path: 'applications', name: 'applications', component: ApplicationsPage },
        { path: 'allocation', name: 'allocation', component: AllocationPage },
        { path: 'formal-application', name: 'formal-application', component: FormalApplicationPage },
        { path: 'grading', name: 'grading', component: GradingPage },
        { path: 'rubrics', name: 'rubrics', component: RubricsPage },
        { path: 'archive', name: 'archive', component: ArchivePage, props: { publicOnly: false } },
        { path: 'admin', name: 'admin', component: AdminPage },
        { path: 'settings', name: 'settings', component: SettingsPage },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  if (to.name === 'public-archive' || to.name === 'login' || to.name === 'register') return true
  const auth = useAuthStore()
  if (!auth.isAuthenticated) return { name: 'login', query: { redirect: to.fullPath } }
  return true
})

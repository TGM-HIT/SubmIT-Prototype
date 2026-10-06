<template>
  <section class="page">
    <p class="page-intro">Manage user roles and inspect access-controlled audit history. Administrator actions are recorded on the server; role changes take effect only if accepted there.</p>
    <div class="tab-list" role="tablist" aria-label="Administration panels">
      <button class="tab-button" role="tab" :aria-selected="activeTab === 'users'" @click="activeTab = 'users'">Users & roles</button>
      <button class="tab-button" role="tab" :aria-selected="activeTab === 'audit'" @click="activeTab = 'audit'">Audit history</button>
    </div>
    <section v-if="activeTab === 'users'" class="panel">
      <div class="page-heading"><div><h2 class="section-title">Accounts</h2><p class="muted">Registration always creates a student account. Change roles only when authorized by school policy.</p></div><button class="button button-secondary" @click="loadUsers(0)">Refresh</button></div>
      <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="loadUsers(users.page)">
        <div v-if="users.content.length" class="data-list">
          <article v-for="user in users.content" :key="user.id" class="data-row">
            <div class="row-main"><h3>{{ user.username }}</h3><p>Account {{ user.id }}</p><span class="status-badge">{{ user.role }}</span></div>
            <form class="toolbar" @submit.prevent="changeRole(user.id ?? '')">
              <div class="field"><label class="label" :for="`role-${user.id}`">Role</label><select :id="`role-${user.id}`" v-model="roleSelections[user.id ?? '']" class="select" @change="clearRoleError(user.id ?? '')"><option value="STUDENT">Student</option><option value="TEACHER">Teacher</option><option value="ADMIN">Administrator</option></select></div>
              <button class="button button-secondary button-small" :disabled="roleSelections[user.id ?? ''] === user.role">Update role</button>
            </form>
            <p v-if="roleErrors[user.id ?? '']" class="error-box" role="alert">{{ roleErrors[user.id ?? ''] }}</p>
          </article>
        </div>
        <p v-else class="empty">No accounts are visible.</p>
        <Pager :page="users" @page="loadUsers" />
      </AsyncPanel>
    </section>
    <section v-else class="panel">
      <div class="page-heading"><div><h2 class="section-title">Audit history</h2><p class="muted">Visible records depend on server-side role and resource permissions.</p></div><button class="button button-secondary" @click="loadAudit(0)">Refresh</button></div>
      <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="loadAudit(audit.page)">
        <div v-if="audit.content.length" class="data-list">
          <article v-for="(entry, index) in audit.content" :key="String(entry.id ?? index)" class="data-row">
            <div class="row-main"><h3>{{ entry.action ?? entry.eventType ?? 'Recorded event' }}</h3><p>{{ entry.occurredAt ?? entry.createdAt ?? entry.timestamp ?? 'Timestamp unavailable' }}</p><p>{{ entry.entityType ?? 'Resource' }} {{ entry.entityId ?? '' }} · actor {{ entry.actorId ?? 'not supplied' }}</p></div><span class="status-badge">{{ entry.status ?? 'Recorded' }}</span>
          </article>
        </div>
        <p v-else class="empty">No audit events are available on this page.</p>
        <Pager :page="audit" @page="loadAudit" />
      </AsyncPanel>
    </section>
    <p v-if="task.success.value" class="success-note" role="status">{{ task.success.value }}</p>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { components } from '../../api/schema'
import type { Page } from '../../api/client'
import { adminApi } from '../../api/services'
import { useTask } from '../../composables/useTask'
import AsyncPanel from '../../components/AsyncPanel.vue'
import Pager from './Pager.vue'

type Data = Record<string, unknown>
const task = useTask()
const roleTask = useTask()
const activeTab = ref<'users' | 'audit'>('users')
const users = ref<Page<components['schemas']['UserResponse']>>(emptyPage())
const audit = ref<Page<Data>>(emptyPage())
const roleSelections = reactive<Record<string, components['schemas']['UserResponse']['role']>>({})
const roleErrors = reactive<Record<string, string>>({})
function emptyPage<T>(): Page<T> { return { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } }
function clearRoleError(id: string): void {
  delete roleErrors[id]
}
async function loadUsers(page = 0): Promise<void> {
  await task.run(async () => {
    users.value = await adminApi.users(page)
    for (const user of users.value.content) {
      if (user.id && user.role) roleSelections[user.id] = user.role
    }
  })
}
async function loadAudit(page = 0): Promise<void> {
  await task.run(async () => { audit.value = await adminApi.audit(page) })
}
async function changeRole(id: string): Promise<void> {
  const role = roleSelections[id]
  if (!role || !confirm(`Change this account's role to ${role}?`)) return
  clearRoleError(id)
  const result = await roleTask.run(() => adminApi.role(id, role), 'Account role updated.')
  if (result) {
    await loadUsers(users.value.page)
  } else {
    roleErrors[id] = roleTask.error.value
  }
}
onMounted(() => void loadUsers())
</script>

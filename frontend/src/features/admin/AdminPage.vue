<template>
  <section class="page">
    <p class="page-intro">Manage user roles and inspect access-controlled audit history. Administrator actions are recorded on the server; role changes take effect only if accepted there.</p>
    <div class="tab-list" role="tablist" aria-label="Administration panels">
      <button class="tab-button" role="tab" :aria-selected="activeTab === 'users'" @click="activeTab = 'users'">Users & roles</button>
      <button class="tab-button" role="tab" :aria-selected="activeTab === 'audit'" @click="activeTab = 'audit'">Audit history</button>
    </div>
    <section v-if="activeTab === 'users'" class="panel">
      <div class="page-heading"><div><h2 id="accounts-heading" ref="accountsHeading" class="section-title">Accounts</h2><p class="muted">Registration always creates a student account. Change roles only when authorized by school policy.</p></div><button class="button button-secondary" @click="loadUsers(0)">Refresh</button></div>
      <form class="toolbar account-filters" @submit.prevent>
        <div class="field">
          <label class="label" for="account-role-filter">Role</label>
          <select id="account-role-filter" :value="roleFilter" class="select" @change="applyRoleFilter">
            <option value="">All roles</option>
            <option value="STUDENT">Student</option>
            <option value="TEACHER">Teacher</option>
            <option value="ADMIN">Administrator</option>
          </select>
        </div>
        <div class="field account-search-field">
          <label class="label" for="account-search">Search username and name</label>
          <input id="account-search" v-model="searchInput" class="input" type="search" placeholder="Username or name" @input="scheduleSearch">
        </div>
        <button class="button button-secondary button-small" type="button" @click="clearFilters">Clear filters</button>
      </form>
      <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="loadUsers(users.page)">
        <div v-if="users.content.length" class="data-list">
          <article v-for="user in users.content" :key="user.id" class="data-row">
            <div class="row-main"><h3>{{ user.displayName }}</h3><p>Username {{ user.username }}</p><p>Account {{ user.id }}</p><span class="status-badge">{{ user.role }}</span></div>
            <button :id="`edit-account-${user.id}`" class="button button-secondary button-small" type="button" :aria-label="`Edit ${user.displayName} (${user.username})`" @click="openEditor(user, $event)">Edit</button>
          </article>
        </div>
        <p v-else class="empty">{{ hasActiveFilters ? 'No accounts match the selected filters.' : 'No accounts are visible.' }}</p>
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

    <ModalDialog v-if="editingUser" :title="`Edit ${editingUser.displayName} account (${editingUser.username})`" :return-focus="editTrigger" :fallback-focus="accountsHeading" @close="closeEditor">
      <form class="edit-account-form" @submit.prevent="saveAccount">
        <div class="field">
          <label class="label" for="account-name">Account name</label>
          <input id="account-name" v-model="nameDraft" class="input" autocomplete="name" required>
        </div>
        <p class="muted">1–120 characters after trimming.</p>
        <div class="field">
          <label class="label" for="account-role">Role</label>
          <select id="account-role" v-model="roleDraft" class="select">
            <option value="STUDENT">Student</option>
            <option value="TEACHER">Teacher</option>
            <option value="ADMIN">Administrator</option>
          </select>
        </div>
        <p v-if="editError" class="error-box" role="alert">{{ editError }}</p>
        <div class="actions">
          <button class="button button-small" type="submit" :disabled="saving || !hasChanges">Save</button>
          <button class="button button-secondary button-small" type="button" :disabled="saving" @click="closeEditor">Cancel</button>
        </div>
      </form>
    </ModalDialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { components } from '../../api/schema'
import type { Page } from '../../api/client'
import { adminApi, userApi } from '../../api/services'
import { useTask } from '../../composables/useTask'
import AsyncPanel from '../../components/AsyncPanel.vue'
import ModalDialog from '../../components/ModalDialog.vue'
import Pager from './Pager.vue'

type Data = Record<string, unknown>
type User = components['schemas']['UserResponse']
type AccountRole = NonNullable<User['role']>
const task = useTask()
const activeTab = ref<'users' | 'audit'>('users')
const users = ref<Page<User>>(emptyPage())
const audit = ref<Page<Data>>(emptyPage())
const roleFilter = ref<AccountRole | ''>('')
const searchInput = ref('')
const activeSearch = ref('')
const editingUser = ref<User | null>(null)
const editTrigger = ref<HTMLElement | null>(null)
const accountsHeading = ref<HTMLElement | null>(null)
const nameDraft = ref('')
const roleDraft = ref<AccountRole>('STUDENT')
const originalName = ref('')
const originalRole = ref<AccountRole>('STUDENT')
const editError = ref('')
const saving = ref(false)
let searchDebounce: ReturnType<typeof setTimeout> | undefined
const hasActiveFilters = computed(() => roleFilter.value !== '' || activeSearch.value !== '')
const hasChanges = computed(() => nameDraft.value.trim() !== originalName.value || roleDraft.value !== originalRole.value)

function emptyPage<T>(): Page<T> { return { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } }
function clearSearchDebounce(): void {
  if (searchDebounce !== undefined) clearTimeout(searchDebounce)
  searchDebounce = undefined
}
function scheduleSearch(): void {
  clearSearchDebounce()
  searchDebounce = setTimeout(() => {
    activeSearch.value = searchInput.value.trim()
    void loadUsers(0)
  }, 300)
}
function applyRoleFilter(event: Event): void {
  clearSearchDebounce()
  roleFilter.value = (event.target as HTMLSelectElement).value as AccountRole | ''
  activeSearch.value = searchInput.value.trim()
  void loadUsers(0)
}
function clearFilters(): void {
  clearSearchDebounce()
  roleFilter.value = ''
  searchInput.value = ''
  activeSearch.value = ''
  void loadUsers(0)
}
async function loadUsers(page = 0): Promise<void> {
  await task.run(async () => {
    users.value = await adminApi.users(page, roleFilter.value || undefined, activeSearch.value || undefined)
  })
}
async function loadAudit(page = 0): Promise<void> {
  await task.run(async () => { audit.value = await adminApi.audit(page) })
}
function openEditor(user: User, event: MouseEvent): void {
  editingUser.value = user
  editTrigger.value = event.currentTarget as HTMLElement
  originalName.value = user.displayName ?? ''
  originalRole.value = user.role ?? 'STUDENT'
  nameDraft.value = originalName.value
  roleDraft.value = originalRole.value
  editError.value = ''
}
function closeEditor(): void {
  if (saving.value) return
  finishEditing()
}
function finishEditing(): void {
  editingUser.value = null
  editError.value = ''
}
function updateVisibleAccount(updated: User): void {
  const index = users.value.content.findIndex((user) => user.id === updated.id)
  if (index >= 0) users.value.content[index] = updated
}
function errorMessage(cause: unknown): string {
  return cause instanceof Error ? cause.message : 'Unable to save account changes.'
}
async function saveAccount(): Promise<void> {
  if (!editingUser.value || !hasChanges.value) return
  const displayName = nameDraft.value.trim()
  if (!displayName || Array.from(displayName).length > 120) {
    editError.value = 'Account name must contain 1 to 120 characters after trimming.'
    return
  }
  const account = editingUser.value
  const id = account.id ?? ''
  const nameChanged = displayName !== originalName.value
  const roleChanged = roleDraft.value !== originalRole.value
  if (roleChanged && !confirm(`Change this account's role to ${roleDraft.value}?`)) return

  saving.value = true
  editError.value = ''
  let nameSaved = false
  try {
    if (nameChanged) {
      const updated = await userApi.changeName(id, displayName)
      nameSaved = true
      originalName.value = updated.displayName ?? displayName
      updateVisibleAccount({ ...account, ...updated, displayName: originalName.value })
    }
    if (roleChanged) await adminApi.role(id, roleDraft.value)
    await loadUsers(users.value.page)
    task.success.value = 'Account updated.'
    finishEditing()
  } catch (cause) {
    const message = errorMessage(cause)
    editError.value = nameSaved
      ? `Account name was saved, but the role change failed: ${message}`
      : message
  } finally {
    saving.value = false
  }
}
onMounted(() => void loadUsers())
onBeforeUnmount(clearSearchDebounce)
</script>

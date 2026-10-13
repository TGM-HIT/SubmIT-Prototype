<template>
  <section class="page">
    <p class="page-intro">Manage user roles and inspect access-controlled audit history. Administrator actions are recorded on the server; role changes take effect only if accepted there.</p>
    <div class="tab-list" role="tablist" aria-label="Administration panels">
      <button class="tab-button" role="tab" :aria-selected="activeTab === 'users'" @click="activeTab = 'users'">Users & roles</button>
      <button class="tab-button" role="tab" :aria-selected="activeTab === 'catalogues'" @click="activeTab = 'catalogues'; void loadCatalogues()">Classes & subjects</button>
      <button class="tab-button" role="tab" :aria-selected="activeTab === 'audit'" @click="activeTab = 'audit'">Audit history</button>
    </div>
    <section v-if="activeTab === 'users'" class="panel">
      <div class="page-heading"><div><h2 id="accounts-heading" ref="accountsHeading" class="section-title">Accounts</h2><p class="muted">Registration creates a student account with the default class; administrators can reassign its class and subjects.</p></div><button class="button button-secondary" @click="loadUsers(0)">Refresh</button></div>
      <form class="toolbar account-filters" @submit.prevent>
        <div class="field">
          <label class="label" for="account-role-filter">Role</label>
          <select id="account-role-filter" :value="roleFilter" class="select filter-control" @change="applyRoleFilter">
            <option value="">All roles</option>
            <option value="STUDENT">Student</option>
            <option value="TEACHER">Teacher</option>
            <option value="ADMIN">Administrator</option>
          </select>
        </div>
        <div class="field account-search-field">
          <label class="label" for="account-search">Search username and name</label>
          <input id="account-search" v-model="searchInput" class="input filter-control" type="search" placeholder="Username or name" @input="scheduleSearch">
        </div>
        <button class="button button-secondary button-small filter-control" type="button" @click="clearFilters">Clear filters</button>
      </form>
      <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="loadUsers(users.page)">
        <div v-if="users.content.length" class="data-list">
          <article v-for="user in users.content" :key="user.id" class="data-row">
            <div class="row-main"><h3>{{ user.displayName }}</h3><p>Username {{ user.username }}</p><p>Account {{ user.id }}</p><p v-if="user.role === 'STUDENT'">Class: {{ user.className }} · Elective subjects: {{ user.electiveSubjectNames?.join(', ') || 'None' }}</p><span class="status-badge">{{ user.role }}</span></div>
            <button :id="`edit-account-${user.id}`" class="button button-secondary button-small" type="button" :aria-label="`Edit ${user.displayName} (${user.username})`" @click="openEditor(user, $event)">Edit</button>
          </article>
        </div>
        <p v-else class="empty">{{ hasActiveFilters ? 'No accounts match the selected filters.' : 'No accounts are visible.' }}</p>
        <Pager :page="users" @page="loadUsers" />
      </AsyncPanel>
    </section>
    <section v-else-if="activeTab === 'catalogues'" class="panel">
      <div class="page-heading"><div><h2 class="section-title">Classes and elective subjects</h2><p class="muted">Shared catalogues are used for student assignments. Entries may be renamed but not deleted.</p></div><button class="button button-secondary" type="button" @click="loadCatalogues">Refresh</button></div>
      <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="loadCatalogues">
        <div class="grid grid-2">
          <section class="panel"><h3 class="section-title">Classes</h3>
            <form class="toolbar" @submit.prevent="createCatalogue('class')"><label class="sr-only" for="new-class">New class name</label><input id="new-class" v-model="newClassName" class="input" maxlength="100" placeholder="New class name" required><button class="button button-small" type="submit" :disabled="catalogueSaving">Add class</button></form>
            <div v-for="entry in classes" :key="entry.id" class="data-row"><template v-if="editingCatalogue?.kind === 'class' && editingCatalogue.id === entry.id"><input v-model="catalogueDraft" class="input" maxlength="100" :aria-label="`Rename class ${entry.name}`"><button class="button button-small" type="button" @click="saveCatalogue">Save</button><button class="button button-secondary button-small" type="button" @click="editingCatalogue = null">Cancel</button></template><template v-else><span>{{ entry.name }}</span><button class="button button-secondary button-small" type="button" @click="editCatalogue('class', entry.id ?? 'unknown', entry.name ?? '')">Rename</button></template></div>
            <p v-if="!classes.length" class="empty">No classes have been added.</p>
          </section>
          <section class="panel"><h3 class="section-title">Elective subjects</h3>
            <form class="toolbar" @submit.prevent="createCatalogue('subject')"><label class="sr-only" for="new-subject">New subject name</label><input id="new-subject" v-model="newSubjectName" class="input" maxlength="100" placeholder="New subject name" required><button class="button button-small" type="submit" :disabled="catalogueSaving">Add subject</button></form>
            <div v-for="entry in subjects" :key="entry.id" class="data-row"><template v-if="editingCatalogue?.kind === 'subject' && editingCatalogue.id === entry.id"><input v-model="catalogueDraft" class="input" maxlength="100" :aria-label="`Rename subject ${entry.name}`"><button class="button button-small" type="button" @click="saveCatalogue">Save</button><button class="button button-secondary button-small" type="button" @click="editingCatalogue = null">Cancel</button></template><template v-else><span>{{ entry.name }}</span><button class="button button-secondary button-small" type="button" @click="editCatalogue('subject', entry.id ?? 'unknown', entry.name ?? '')">Rename</button></template></div>
            <p v-if="!subjects.length" class="empty">No elective subjects have been added.</p>
          </section>
        </div>
        <p v-if="catalogueError" class="error-box" role="alert">{{ catalogueError }}</p>
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
      <form class="edit-account-form" novalidate @submit.prevent="saveAccount">
        <div class="field">
          <label class="label" for="account-name">Account name</label>
          <input id="account-name" v-model="nameDraft" class="input" autocomplete="name">
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
        <template v-if="roleDraft === 'STUDENT'">
          <div class="field">
            <label class="label" for="student-class">Class</label>
            <select id="student-class" v-model="classDraft" class="select" required>
              <option value="" disabled>Select a class…</option>
              <option v-for="entry in classes" :key="entry.id" :value="entry.id ?? ''">{{ entry.name }}</option>
            </select>
          </div>
          <fieldset class="field"><legend class="label">Elective subjects (up to four)</legend>
            <label v-for="entry in subjects" :key="entry.id" class="checkbox-row"><input v-model="subjectDraft" type="checkbox" :value="entry.id ?? ''" :disabled="subjectDraft.length >= 4 && !subjectDraft.includes(entry.id ?? '')">{{ entry.name }}</label>
            <p v-if="subjectDraft.length >= 4" class="muted">The four-subject limit has been reached.</p>
          </fieldset>
        </template>
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
import { adminApi, studentDetailsApi, userApi } from '../../api/services'
import { useTask } from '../../composables/useTask'
import AsyncPanel from '../../components/AsyncPanel.vue'
import ModalDialog from '../../components/ModalDialog.vue'
import Pager from './Pager.vue'

type Data = Record<string, unknown>
type User = components['schemas']['UserResponse']
type AccountRole = NonNullable<User['role']>
const task = useTask()
const activeTab = ref<'users' | 'catalogues' | 'audit'>('users')
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
const classes = ref<components['schemas']['CatalogueEntryResponse'][]>([])
const subjects = ref<components['schemas']['CatalogueEntryResponse'][]>([])
const classDraft = ref('')
const subjectDraft = ref<string[]>([])
const originalClassId = ref('')
const originalSubjectIds = ref<string[]>([])
const newClassName = ref('')
const newSubjectName = ref('')
const catalogueDraft = ref('')
const editingCatalogue = ref<{ kind: 'class' | 'subject'; id: string } | null>(null)
const catalogueError = ref('')
const catalogueSaving = ref(false)
let searchDebounce: ReturnType<typeof setTimeout> | undefined
const hasActiveFilters = computed(() => roleFilter.value !== '' || activeSearch.value !== '')
const hasChanges = computed(() => nameDraft.value.trim() !== originalName.value
  || roleDraft.value !== originalRole.value
  || (roleDraft.value === 'STUDENT'
    && (classDraft.value !== originalClassId.value
      || [...subjectDraft.value].sort().join(',') !== [...originalSubjectIds.value].sort().join(','))))

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
async function openEditor(user: User, event: MouseEvent): Promise<void> {
  editingUser.value = user
  editTrigger.value = event.currentTarget as HTMLElement
  originalName.value = user.displayName ?? ''
  originalRole.value = user.role ?? 'STUDENT'
  nameDraft.value = originalName.value
  roleDraft.value = originalRole.value
  classDraft.value = ''
  subjectDraft.value = []
  originalClassId.value = ''
  originalSubjectIds.value = []
  editError.value = ''
  try {
    const [classPage, subjectPage, details] = await Promise.all([
      studentDetailsApi.classes(), studentDetailsApi.subjects(),
      user.role === 'STUDENT' && user.id ? studentDetailsApi.get(user.id) : Promise.resolve(null),
    ])
    classes.value = classPage.content
    subjects.value = subjectPage.content
    classDraft.value = details?.classId ?? classes.value.find((entry) => entry.name === 'No class / unassigned')?.id ?? ''
    subjectDraft.value = [...(details?.electiveSubjectIds ?? [])]
    originalClassId.value = classDraft.value
    originalSubjectIds.value = [...subjectDraft.value]
  } catch (cause) {
    editError.value = errorMessage(cause)
  }
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
  const assignmentChanged = roleDraft.value === 'STUDENT'
    && (roleChanged || classDraft.value !== originalClassId.value
      || [...subjectDraft.value].sort().join(',') !== [...originalSubjectIds.value].sort().join(','))
  if (assignmentChanged && (!classDraft.value || subjectDraft.value.length > 4)) {
    editError.value = !classDraft.value ? 'Select exactly one class.' : 'Select no more than four elective subjects.'
    return
  }
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
    if (assignmentChanged) {
      await studentDetailsApi.assign(id, { classId: classDraft.value, electiveSubjectIds: subjectDraft.value })
    }
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
async function loadCatalogues(): Promise<void> {
  await task.run(async () => {
    const [classPage, subjectPage] = await Promise.all([studentDetailsApi.classes(), studentDetailsApi.subjects()])
    classes.value = classPage.content
    subjects.value = subjectPage.content
  })
}
function editCatalogue(kind: 'class' | 'subject', id: string, name: string): void {
  editingCatalogue.value = { kind, id }
  catalogueDraft.value = name
  catalogueError.value = ''
}
async function createCatalogue(kind: 'class' | 'subject'): Promise<void> {
  const name = (kind === 'class' ? newClassName.value : newSubjectName.value).trim()
  if (!name || name.length > 100) {
    catalogueError.value = 'Names must contain 1 to 100 characters.'
    return
  }
  catalogueSaving.value = true
  catalogueError.value = ''
  try {
    if (kind === 'class') {
      await studentDetailsApi.createClass({ name })
      newClassName.value = ''
    } else {
      await studentDetailsApi.createSubject({ name })
      newSubjectName.value = ''
    }
    await loadCatalogues()
    task.success.value = `${kind === 'class' ? 'Class' : 'Subject'} created.`
  } catch (cause) {
    catalogueError.value = errorMessage(cause)
  } finally {
    catalogueSaving.value = false
  }
}
async function saveCatalogue(): Promise<void> {
  if (!editingCatalogue.value) return
  const { kind, id } = editingCatalogue.value
  const name = catalogueDraft.value.trim()
  if (!name || name.length > 100) {
    catalogueError.value = 'Names must contain 1 to 100 characters.'
    return
  }
  try {
    if (kind === 'class') await studentDetailsApi.renameClass(id, { name })
    else await studentDetailsApi.renameSubject(id, { name })
    editingCatalogue.value = null
    await loadCatalogues()
  } catch (cause) {
    catalogueError.value = errorMessage(cause)
  }
}
onMounted(() => { void loadUsers(); void loadCatalogues() })
onBeforeUnmount(clearSearchDebounce)
</script>

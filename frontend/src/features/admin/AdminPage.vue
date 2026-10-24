<template>
  <section class="page">
    <p class="page-intro">{{ $t('admin.intro') }}</p>
    <div class="tab-list" role="tablist" :aria-label="$t('admin.tablist')">
      <button class="tab-button" role="tab" :aria-selected="activeTab === 'users'" @click="activeTab = 'users'">{{ $t('admin.tabs.users') }}</button>
      <button class="tab-button" role="tab" :aria-selected="activeTab === 'catalogues'" @click="activeTab = 'catalogues'; void loadCatalogues()">{{ $t('admin.tabs.catalogues') }}</button>
      <button class="tab-button" role="tab" :aria-selected="activeTab === 'audit'" @click="activeTab = 'audit'">{{ $t('admin.tabs.audit') }}</button>
    </div>
    <section v-if="activeTab === 'users'" class="panel">
      <div class="page-heading"><div><h2 id="accounts-heading" ref="accountsHeading" class="section-title">{{ $t('admin.accounts') }}</h2><p class="muted">{{ $t('admin.registration') }}</p></div><button class="button button-secondary" @click="loadUsers(0)">{{ $t('admin.refresh') }}</button></div>
      <form class="toolbar account-filters" @submit.prevent>
        <div class="field">
          <label class="label" for="account-role-filter">{{ $t('admin.role') }}</label>
          <select id="account-role-filter" :value="roleFilter" class="select filter-control" @change="applyRoleFilter">
            <option value="">{{ $t('admin.allRoles') }}</option>
            <option value="STUDENT">{{ $t('admin.userRoles.STUDENT') }}</option>
            <option value="TEACHER">{{ $t('admin.userRoles.TEACHER') }}</option>
            <option value="ADMIN">{{ $t('admin.userRoles.ADMIN') }}</option>
          </select>
        </div>
        <div class="field account-search-field">
          <label class="label" for="account-search">{{ $t('admin.searchLabel') }}</label>
          <input id="account-search" v-model="searchInput" class="input filter-control" type="search" :placeholder="$t('admin.searchPlaceholder')" @input="scheduleSearch">
        </div>
        <button class="button button-secondary button-small filter-control" type="button" @click="clearFilters">{{ $t('admin.clearFilters') }}</button>
      </form>
      <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="loadUsers(users.page)">
        <div v-if="users.content.length" class="data-list">
          <article v-for="user in users.content" :key="user.id" class="data-row">
            <div class="row-main"><h3>{{ user.displayName }}</h3><p>{{ $t('admin.username') }} {{ user.username }}</p><p>{{ $t('admin.account') }} {{ user.id }}</p><p v-if="user.role === 'STUDENT'">{{ $t('admin.class') }} {{ user.className }} · {{ $t('admin.subjects') }} {{ user.electiveSubjectNames?.join(', ') || $t('admin.none') }}</p><span class="status-badge">{{ user.role ? $t(`admin.userRoles.${user.role}`) : '' }}</span></div>
            <button :id="`edit-account-${user.id}`" class="button button-secondary button-small" type="button" :aria-label="$t('admin.editAccount', { name: user.displayName, username: user.username })" @click="openEditor(user, $event)">{{ $t('admin.edit') }}</button>
          </article>
        </div>
        <p v-else class="empty">{{ hasActiveFilters ? $t('admin.noMatches') : $t('admin.noneVisible') }}</p>
        <Pager :page="users" @page="loadUsers" />
      </AsyncPanel>
    </section>
    <section v-else-if="activeTab === 'catalogues'" class="panel">
      <div class="page-heading"><div><h2 class="section-title">{{ $t('admin.classesHeading') }}</h2><p class="muted">{{ $t('admin.catalogueHelp') }}</p></div><button class="button button-secondary" type="button" @click="loadCatalogues">{{ $t('admin.refresh') }}</button></div>
      <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="loadCatalogues">
        <div class="grid grid-2">
          <section class="panel"><h3 class="section-title">{{ $t('admin.classes') }}</h3>
            <form class="toolbar" @submit.prevent="createCatalogue('class')"><label class="sr-only" for="new-class">{{ $t('admin.newClass') }}</label><input id="new-class" v-model="newClassName" class="input" maxlength="100" :placeholder="$t('admin.newClass')" required><button class="button button-small" type="submit" :disabled="catalogueSaving">{{ $t('admin.addClass') }}</button></form>
            <div v-for="entry in classes" :key="entry.id" class="data-row"><template v-if="editingCatalogue?.kind === 'class' && editingCatalogue.id === entry.id"><input v-model="catalogueDraft" class="input" maxlength="100" :aria-label="$t('admin.renameClass', { name: entry.name })"><button class="button button-small" type="button" @click="saveCatalogue">{{ $t('admin.save') }}</button><button class="button button-secondary button-small" type="button" @click="editingCatalogue = null">{{ $t('admin.cancel') }}</button></template><template v-else><span>{{ entry.name }}</span><button class="button button-secondary button-small" type="button" @click="editCatalogue('class', entry.id ?? 'unknown', entry.name ?? '')">{{ $t('admin.rename') }}</button></template></div>
            <p v-if="!classes.length" class="empty">{{ $t('admin.noClasses') }}</p>
          </section>
          <section class="panel"><h3 class="section-title">{{ $t('admin.subjectsHeading') }}</h3>
            <form class="toolbar" @submit.prevent="createCatalogue('subject')"><label class="sr-only" for="new-subject">{{ $t('admin.newSubject') }}</label><input id="new-subject" v-model="newSubjectName" class="input" maxlength="100" :placeholder="$t('admin.newSubject')" required><button class="button button-small" type="submit" :disabled="catalogueSaving">{{ $t('admin.addSubject') }}</button></form>
            <div v-for="entry in subjects" :key="entry.id" class="data-row"><template v-if="editingCatalogue?.kind === 'subject' && editingCatalogue.id === entry.id"><input v-model="catalogueDraft" class="input" maxlength="100" :aria-label="$t('admin.renameSubject', { name: entry.name })"><button class="button button-small" type="button" @click="saveCatalogue">{{ $t('admin.save') }}</button><button class="button button-secondary button-small" type="button" @click="editingCatalogue = null">{{ $t('admin.cancel') }}</button></template><template v-else><span>{{ entry.name }}</span><button class="button button-secondary button-small" type="button" @click="editCatalogue('subject', entry.id ?? 'unknown', entry.name ?? '')">{{ $t('admin.rename') }}</button></template></div>
            <p v-if="!subjects.length" class="empty">{{ $t('admin.noSubjects') }}</p>
          </section>
        </div>
        <p v-if="catalogueError" class="error-box" role="alert">{{ catalogueError }}</p>
      </AsyncPanel>
    </section>
    <section v-else class="panel">
      <div class="page-heading"><div><h2 class="section-title">{{ $t('admin.auditHeading') }}</h2><p class="muted">{{ $t('admin.auditHelp') }}</p></div><button class="button button-secondary" @click="loadAudit(0)">{{ $t('admin.refresh') }}</button></div>
      <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="loadAudit(audit.page)">
        <div v-if="audit.content.length" class="data-list">
          <article v-for="(entry, index) in audit.content" :key="String(entry.id ?? index)" class="data-row">
            <div class="row-main"><h3>{{ entry.action ?? entry.eventType ?? $t('admin.auditEvent') }}</h3><p>{{ timestamp(entry.occurredAt ?? entry.createdAt ?? entry.timestamp) }}</p><p>{{ entry.entityType ?? $t('admin.resource') }} {{ entry.entityId ?? '' }} · {{ $t('admin.actor') }} {{ entry.actorId ?? $t('admin.actorNotSupplied') }}</p></div><span class="status-badge">{{ entry.status ?? $t('admin.recorded') }}</span>
          </article>
        </div>
        <p v-else class="empty">{{ $t('admin.noAudit') }}</p>
        <Pager :page="audit" @page="loadAudit" />
      </AsyncPanel>
    </section>
    <p v-if="task.success.value" class="success-note" role="status">{{ task.success.value }}</p>

    <ModalDialog v-if="editingUser" :title="$t('admin.editAccount', { name: editingUser.displayName, username: editingUser.username })" :return-focus="editTrigger" :fallback-focus="accountsHeading" @close="closeEditor">
      <form class="edit-account-form" novalidate @submit.prevent="saveAccount">
        <div class="field">
          <label class="label" for="account-name">{{ $t('admin.nameLabel') }}</label>
          <input id="account-name" v-model="nameDraft" class="input" autocomplete="name">
        </div>
        <p class="muted">{{ $t('admin.nameGuidance') }}</p>
        <div class="field">
          <label class="label" for="account-role">{{ $t('admin.role') }}</label>
          <select id="account-role" v-model="roleDraft" class="select">
            <option value="STUDENT">{{ $t('admin.userRoles.STUDENT') }}</option>
            <option value="TEACHER">{{ $t('admin.userRoles.TEACHER') }}</option>
            <option value="ADMIN">{{ $t('admin.userRoles.ADMIN') }}</option>
          </select>
        </div>
        <template v-if="roleDraft === 'STUDENT'">
          <div class="field">
            <label class="label" for="student-class">{{ $t('admin.class') }}</label>
            <select id="student-class" v-model="classDraft" class="select" required>
              <option value="" disabled>{{ $t('admin.selectClass') }}</option>
              <option v-for="entry in classes" :key="entry.id" :value="entry.id ?? ''">{{ entry.name }}</option>
            </select>
          </div>
          <fieldset class="field"><legend class="label">{{ $t('admin.electiveLimit') }}</legend>
            <label v-for="entry in subjects" :key="entry.id" class="checkbox-row"><input v-model="subjectDraft" type="checkbox" :value="entry.id ?? ''" :disabled="subjectDraft.length >= 4 && !subjectDraft.includes(entry.id ?? '')">{{ entry.name }}</label>
            <p v-if="subjectDraft.length >= 4" class="muted">{{ $t('admin.limitReached') }}</p>
          </fieldset>
        </template>
        <p v-if="editError" class="error-box" role="alert">{{ editError }}</p>
        <div class="actions">
          <button class="button button-small" type="submit" :disabled="saving || !hasChanges">{{ $t('admin.save') }}</button>
          <button class="button button-secondary button-small" type="button" :disabled="saving" @click="closeEditor">{{ $t('admin.cancel') }}</button>
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
import { i18n } from '../../i18n'
import { formatTimestamp } from '../../i18n'
import { ApiError } from '../../api/client'

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
function timestamp(value: unknown): string {
  if (typeof value !== 'string' || !value) return String(i18n.global.t('admin.timestampUnavailable'))
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : formatTimestamp(date)
}
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
  return cause instanceof ApiError && cause.source === 'backend'
    ? cause.message : String(i18n.global.t('admin.accountSaveFailed'))
}
async function saveAccount(): Promise<void> {
  if (!editingUser.value || !hasChanges.value) return
  const displayName = nameDraft.value.trim()
  if (!displayName || Array.from(displayName).length > 120) {
    editError.value = String(i18n.global.t('admin.invalidName'))
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
    editError.value = String(i18n.global.t(classDraft.value ? 'admin.tooManySubjects' : 'admin.chooseClass'))
    return
  }
  if (roleChanged && !confirm(String(i18n.global.t('admin.changeRoleConfirm', {
    role: i18n.global.locale.value === 'en'
      ? roleDraft.value
      : i18n.global.t(`admin.userRoles.${roleDraft.value}`),
  })))) return

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
    task.success.value = String(i18n.global.t('admin.saved'))
    finishEditing()
  } catch (cause) {
    const message = errorMessage(cause)
    editError.value = nameSaved
      ? String(i18n.global.t('admin.nameSavedRoleFailed', { message }))
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
    catalogueError.value = String(i18n.global.t('admin.invalidCatalogue'))
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
    task.success.value = String(i18n.global.t('admin.created', {
      name: i18n.global.t(`admin.names.${kind}`),
    }))
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
    catalogueError.value = String(i18n.global.t('admin.invalidCatalogue'))
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

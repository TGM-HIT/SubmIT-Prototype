<template>
  <section class="panel stack applying-workspace" :aria-label="$t('applyingGroups.ariaLabel')">
    <h3 ref="heading" tabindex="-1">{{ $t('applyingGroups.heading') }}</h3>
    <p>{{ $t('applyingGroups.intro') }}</p>
    <p v-if="locked" class="notice">{{ $t('applyingGroups.locked') }}</p>
    <p v-if="loading" role="status">{{ $t('applyingGroups.loading') }}</p>
    <div v-if="error" class="error-box" role="alert">{{ error }} <button type="button" class="button button-secondary" @click="load()">{{ $t('applyingGroups.retry') }}</button></div>
    <button v-if="student" ref="primary" data-shared-action="create" type="button" class="button" :disabled="busy || loading || !!error || locked || hasMembership" @click="create">
      {{ $t('applyingGroups.create') }}
    </button>
    <p v-if="!loading && !error && !groups.content.length" class="empty">{{ $t('applyingGroups.empty') }}</p>
    <article v-for="group in groups.content" :key="group.id" :data-applying-group="group.id" class="panel stack">
      <h4 :id="`group-${group.id}`" tabindex="-1">{{ $t('applyingGroups.group') }} · {{ groupStatus(group.status) }} · {{ $t('applyingGroups.revision', { revision: number(group.revision) }) }}</h4>
      <p v-if="group.status === 'DISSOLVED'" class="notice">{{ $t('applyingGroups.dissolved') }}</p>
      <p v-else-if="group.status === 'SUBMITTED'" class="success-note">{{ $t('applyingGroups.submitted') }}</p>
      <p v-else class="notice">{{ $t('applyingGroups.draft') }}</p>
      <ul :aria-label="$t('applyingGroups.roster')">
        <li v-for="member in group.members" :key="member.studentId">
          {{ member.displayName }}<strong v-if="member.self"> ({{ $t('applyingGroups.you') }})</strong>
          <span v-if="member.creator"> · {{ $t('applyingGroups.creator') }}</span> · {{ member.approved ? $t('applyingGroups.approved') : $t('applyingGroups.approvalRequired') }}
        </li>
      </ul>
      <ol :aria-label="$t('applyingGroups.ranking')"><li v-for="preference in group.preferences" :key="preference.projectId">{{ preference.projectTitle }}</li></ol>
      <p v-if="!group.preferences.length" class="muted">{{ $t('applyingGroups.noRanking') }}</p>
      <div v-if="student && !locked && !group.locked && group.status !== 'DISSOLVED'" class="stack">
        <div v-if="group.invited" class="stack">
          <p>{{ $t('applyingGroups.acceptGuidance') }}</p>
          <div class="actions"><button data-shared-action="accept" class="button" :disabled="busy" @click="mutate(group, 'ACCEPT', {}, $event)">{{ $t('applyingGroups.accept') }}</button>
            <button class="button button-secondary" :disabled="busy" @click="mutate(group, 'DECLINE', {}, $event)">{{ $t('applyingGroups.decline') }}</button></div>
        </div>
        <div v-if="group.members.some(member => member.self)" class="actions">
          <button data-shared-action="approve" class="button" :disabled="busy || group.members.some(member => member.self && member.approved)" @click="mutate(group, 'APPROVE', {}, $event)">{{ $t('applyingGroups.approve', { revision: number(group.revision) }) }}</button>
          <button v-if="!group.manager" data-shared-action="leave" class="button button-danger" :disabled="busy" @click="confirm(group, 'LEAVE', $event)">{{ $t('applyingGroups.leave') }}</button>
        </div>
        <template v-if="group.manager">
          <form class="stack" @submit.prevent="search(group, 0)">
            <label class="label" :for="`lookup-${group.id}`">{{ $t('applyingGroups.inviteSearchLabel') }}</label>
            <input :id="`lookup-${group.id}`" v-model="queries[group.id]" class="input" />
            <button data-shared-action="search" type="submit" class="button button-secondary" :disabled="busy">{{ $t('applyingGroups.search') }}</button>
          </form>
          <p v-if="lookupLoading" role="status">{{ $t('applyingGroups.lookupLoading') }}</p>
          <div v-if="lookupErrors[group.id]" role="alert" class="error-box">{{ lookupErrors[group.id] }}</div>
          <template v-if="lookups[group.id]">
            <p v-if="!lookups[group.id]?.content.length" class="empty">{{ $t('applyingGroups.noMatchingStudents') }}</p>
            <ul><li v-for="candidate in lookups[group.id]?.content" :key="candidate.id">
              {{ candidate.displayName }} <button type="button" class="button button-secondary button-small"
                :data-student-name="candidate.displayName" :disabled="busy || group.members.some(member => member.studentId === candidate.id)"
                @click="mutate(group, 'INVITE', { studentId: candidate.id }, $event)">{{ $t('applyingGroups.invite', { name: candidate.displayName }) }}</button>
            </li></ul>
            <div class="actions"><button class="button button-secondary" :disabled="lookups[group.id]!.page === 0" @click="search(group, lookups[group.id]!.page - 1)">{{ $t('applyingGroups.previousStudents') }}</button>
              <span>{{ $t('applyingGroups.studentPage', { current: number(lookups[group.id]!.page + 1), total: number(Math.max(1, lookups[group.id]!.totalPages)) }) }}</span>
              <button class="button button-secondary" :disabled="lookups[group.id]!.page + 1 >= lookups[group.id]!.totalPages" @click="search(group, lookups[group.id]!.page + 1)">{{ $t('applyingGroups.nextStudents') }}</button></div>
          </template>
          <div v-if="invitations[group.id]" class="stack">
            <h5>{{ $t('applyingGroups.pendingInvitations') }}</h5>
            <p v-if="!invitations[group.id]?.content.length">{{ $t('applyingGroups.noPendingInvitations') }}</p>
            <ul><li v-for="invite in invitations[group.id]?.content" :key="invite.studentId">{{ invite.displayName }}
              <button class="button button-secondary button-small" :disabled="busy" @click="mutate(group, 'REVOKE', { studentId: invite.studentId }, $event)">{{ $t('applyingGroups.revoke', { name: invite.displayName }) }}</button></li></ul>
            <div class="actions"><button class="button button-secondary" :disabled="invitations[group.id]!.page === 0" @click="loadInvitations(group, invitations[group.id]!.page - 1)">{{ $t('applyingGroups.previousInvitations') }}</button>
              <span>{{ $t('applyingGroups.invitationPage', { current: number(invitations[group.id]!.page + 1), total: number(Math.max(1, invitations[group.id]!.totalPages)) }) }}</span>
              <button class="button button-secondary" :disabled="invitations[group.id]!.page + 1 >= invitations[group.id]!.totalPages" @click="loadInvitations(group, invitations[group.id]!.page + 1)">{{ $t('applyingGroups.nextInvitations') }}</button></div>
          </div>
          <form class="stack" @submit.prevent="saveRanking(group, $event)">
            <h5>{{ $t('applyingGroups.editRanking') }}</h5>
            <p>{{ $t('applyingGroups.rankingChanged') }}</p>
            <ol><li v-for="(id, index) in rankings[group.id] ?? []" :key="id">{{ title(id) }}
              <button type="button" class="button button-secondary button-small" :disabled="index === 0" @click="move(group.id, index, -1)">{{ $t('applyingGroups.moveUp', { title: title(id) }) }}</button>
              <button type="button" class="button button-secondary button-small" :disabled="index + 1 === rankings[group.id]!.length" @click="move(group.id, index, 1)">{{ $t('applyingGroups.moveDown', { title: title(id) }) }}</button>
              <button type="button" class="button button-secondary button-small" @click="rankings[group.id]!.splice(index, 1)">{{ $t('applyingGroups.remove', { title: title(id) }) }}</button>
            </li></ol>
            <label class="label" :for="`shared-choice-${group.id}`">{{ $t('applyingGroups.addPreference') }}</label>
            <select :id="`shared-choice-${group.id}`" v-model="choices[group.id]" class="select"><option value="">{{ $t('applyingGroups.chooseProject') }}</option>
              <option v-for="project in period.projectTitles ?? []" :key="project.projectId" :value="project.projectId" :disabled="rankings[group.id]?.includes(project.projectId ?? '')">{{ project.title }}</option></select>
            <button data-shared-action="add-choice" class="button button-secondary" type="button" :disabled="!choices[group.id]" @click="addChoice(group.id)">{{ $t('applyingGroups.addChoice') }}</button>
            <button data-shared-action="save-ranking" class="button" type="submit" :disabled="busy || (rankings[group.id]?.length ?? 0) < 3">{{ $t('applyingGroups.saveRanking') }}</button>
          </form>
          <div class="actions">
            <button data-shared-action="submit" class="button" :disabled="busy || group.status === 'SUBMITTED' || group.members.length < 2 || group.preferences.length < 3 || group.members.some(member => !member.approved)" @click="confirm(group, 'SUBMIT', $event)">{{ $t('applyingGroups.submit') }}</button>
            <button v-if="group.status === 'SUBMITTED'" data-shared-action="withdraw" class="button button-danger" :disabled="busy" @click="confirm(group, 'WITHDRAW', $event)">{{ $t('applyingGroups.withdraw') }}</button>
            <button data-shared-action="dissolve" class="button button-danger" :disabled="busy" @click="confirm(group, 'DISSOLVE', $event)">{{ $t('applyingGroups.dissolve') }}</button>
          </div>
          <label class="label" :for="`transfer-${group.id}`">{{ $t('applyingGroups.transferLabel') }}</label>
          <select :id="`transfer-${group.id}`" v-model="transfers[group.id]" class="select"><option value="">{{ $t('applyingGroups.chooseCreator') }}</option>
            <option v-for="member in group.members.filter(member => !member.creator)" :key="member.studentId" :value="member.studentId">{{ member.displayName }}</option></select>
          <button class="button button-secondary" :disabled="busy || !transfers[group.id]" @click="mutate(group, 'TRANSFER', { studentId: transfers[group.id] }, $event)">{{ $t('applyingGroups.transfer') }}</button>
        </template>
      </div>
    </article>
    <div class="actions" v-if="groups.totalPages > 1">
      <button class="button button-secondary" :disabled="loading || groups.page === 0" @click="load(groups.page - 1)">{{ $t('applyingGroups.previousGroups') }}</button>
      <span>{{ $t('applyingGroups.groupPage', { current: number(groups.page + 1), total: number(groups.totalPages), count: number(groups.totalElements) }) }}</span>
      <button class="button button-secondary" :disabled="loading || groups.page + 1 >= groups.totalPages" @click="load(groups.page + 1)">{{ $t('applyingGroups.nextGroups') }}</button>
    </div>
    <p v-if="success" role="status" class="success-note">{{ success }}</p>
    <ModalDialog v-if="confirmation" :title="$t('applyingGroups.confirmTitle')" :return-focus="confirmation.trigger" :fallback-focus="primary ?? heading" @close="cancel">
      <h4>{{ $t(`applyingGroups.action.${confirmation.action}`) }}</h4>
      <p v-if="confirmation.action === 'SUBMIT'">{{ $t('applyingGroups.submitConfirm') }}</p>
      <p v-else>{{ $t('applyingGroups.endEligibilityConfirm') }}</p>
      <div class="actions"><button ref="cancelControl" data-shared-action="cancel" type="button" class="button button-secondary" @click="cancel">{{ $t('applyingGroups.cancelChange') }}</button>
        <button data-shared-action="confirm" type="button" class="button button-danger" @click="confirmed">{{ $t('applyingGroups.confirmChange') }}</button></div>
    </ModalDialog>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import type { components, operations } from '../../api/schema'
import type { Page } from '../../api/client'
import { applyingGroupsApi, ideasApi } from '../../api/services'
import { i18n } from '../../i18n'
import { formatNumber } from '../../i18n'
import { ApiError } from '../../api/client'
import { useAuthStore } from '../../stores/auth'
import ModalDialog from '../../components/ModalDialog.vue'

type Group = components['schemas']['GroupResponse']
type Member = components['schemas']['MemberResponse']
type Candidate = components['schemas']['StudentLookupResponse']
type Action = operations['changeApplyingGroup']['parameters']['path']['action']
type Change = components['schemas']['ChangeRequest']
const props = defineProps<{ period: components['schemas']['ApplicationPeriodResponse'] }>()
const emit = defineEmits<{ changed: [] }>()
const auth = useAuthStore()
const t = i18n.global.t.bind(i18n.global)
const number = formatNumber
function groupStatus(status: string): string {
  const key = `applyingGroups.states.${status.toLowerCase()}`
  return i18n.global.te(key) ? String(i18n.global.t(key)) : status
}
const student = computed(() => auth.user?.role === 'STUDENT')
const now = ref(Date.now())
const clock = setInterval(() => { now.value = Date.now() }, 1000)
onUnmounted(() => clearInterval(clock))
const locked = computed(() => props.period.status !== 'OPEN'
  || now.value < new Date(props.period.opensAt ?? '').getTime()
  || now.value >= new Date(props.period.closesAt ?? '').getTime())
const empty = <T>(): Page<T> => ({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const groups = ref<Page<Group>>(empty())
const loading = ref(false), busy = ref(false), error = ref(''), success = ref(''), lookupLoading = ref(false)
const queries = reactive<Record<string, string>>({}), choices = reactive<Record<string, string>>({}), transfers = reactive<Record<string, string>>({})
const rankings = reactive<Record<string, string[]>>({})
const serverRankings = reactive<Record<string, string[]>>({})
const lookups = reactive<Record<string, Page<Candidate>>>({}), lookupErrors = reactive<Record<string, string>>({})
const invitations = reactive<Record<string, Page<Member>>>({})
const primary = ref<HTMLButtonElement>(), heading = ref<HTMLHeadingElement>(), cancelControl = ref<HTMLButtonElement>()
const confirmation = ref<{ group: Group, action: Action, trigger: HTMLElement | null }>()
const hasMembership = ref(false)
const message = (cause: unknown) => cause instanceof ApiError ? cause.message : t('applyingGroups.requestFailed')
const title = (id: string) => props.period.projectTitles?.find(project => project.projectId === id)?.title ?? t('applyingGroups.unknownProject')

async function load(page = groups.value.page): Promise<void> {
  loading.value = true; error.value = ''
  try {
    const response = await applyingGroupsApi.list(props.period.id ?? '', page)
    const membershipPages = [response]
    if (student.value) for (let other = 0; other < response.totalPages; other += 1) {
      if (other !== page) membershipPages.push(await applyingGroupsApi.list(props.period.id ?? '', other))
    }
    hasMembership.value = membershipPages.some(result => result.content.some(group =>
      group.status !== 'DISSOLVED' && group.members.some(member => member.self)))
    groups.value = response
    for (const group of response.content) {
      const nextRanking = group.preferences.flatMap(preference => preference.projectId ? [preference.projectId] : [])
      if (!rankings[group.id] || JSON.stringify(rankings[group.id]) === JSON.stringify(serverRankings[group.id])) {
        rankings[group.id] = nextRanking
      }
      serverRankings[group.id] = nextRanking
      if (group.manager) await loadInvitations(group, invitations[group.id]?.page ?? 0)
    }
  } catch (cause) { error.value = message(cause) }
  finally { loading.value = false }
}
async function loadInvitations(group: Group, page: number): Promise<void> {
  try { invitations[group.id] = await applyingGroupsApi.invitations(props.period.id ?? '', group.id, page) }
  catch (cause) { error.value = message(cause) }
}
async function search(group: Group, page: number): Promise<void> {
  lookupLoading.value = true; lookupErrors[group.id] = ''
  try { lookups[group.id] = await ideasApi.lookupStudents(queries[group.id] ?? '', page) }
  catch (cause) { lookupErrors[group.id] = message(cause) }
  finally { lookupLoading.value = false }
}
async function create(): Promise<void> {
  busy.value = true; error.value = ''; success.value = ''
  try {
    const group = await applyingGroupsApi.create(props.period.id ?? '')
    await load(0); await nextTick()
    document.getElementById(`group-${group.id}`)?.focus()
    emit('changed')
  } catch (cause) { error.value = message(cause) }
  finally { busy.value = false }
}
async function mutate(group: Group, action: Action, fields: Partial<Change>, event?: Event, trigger?: HTMLElement | null): Promise<void> {
  const focus = trigger ?? (event?.currentTarget instanceof HTMLElement ? event.currentTarget : null)
  busy.value = true; error.value = ''; success.value = ''
  try {
    const updated = await applyingGroupsApi.change(props.period.id ?? '', group.id, action, { revision: group.revision, ...fields })
    if (action === 'RANK') rankings[group.id] = updated.preferences.flatMap(preference => preference.projectId ? [preference.projectId] : [])
    await load()
    success.value = t('applyingGroups.actionSuccess', { action: t(`applyingGroups.action.${action}`) })
    busy.value = false
    emit('changed'); await nextTick()
    if (['LEAVE', 'DISSOLVE', 'DECLINE'].includes(action)) (primary.value ?? heading.value)?.focus()
    else if (['INVITE', 'REVOKE'].includes(action)) document.getElementById(`lookup-${group.id}`)?.focus()
    else if (focus?.isConnected && !focus.matches(':disabled')) focus.focus()
    else document.getElementById(`group-${group.id}`)?.focus()
  } catch (cause) { error.value = message(cause) }
  finally { busy.value = false }
}
function addChoice(id: string): void {
  const choice = choices[id]
  if (choice && !rankings[id]?.includes(choice)) (rankings[id] ??= []).push(choice)
  choices[id] = ''
}
function move(id: string, index: number, delta: number): void {
  const list = rankings[id]
  if (!list || index + delta < 0 || index + delta >= list.length) return
  const item = list.splice(index, 1)[0]
  if (item) list.splice(index + delta, 0, item)
}
async function saveRanking(group: Group, event: Event): Promise<void> {
  await mutate(group, 'RANK', { projectIds: rankings[group.id] ?? [] }, event)
}
async function confirm(group: Group, action: Action, event: Event): Promise<void> {
  confirmation.value = { group, action, trigger: event.currentTarget instanceof HTMLElement ? event.currentTarget : null }
  await nextTick(); cancelControl.value?.focus()
}
function cancel(): void {
  const trigger = confirmation.value?.trigger
  confirmation.value = undefined
  trigger?.focus()
}
async function confirmed(): Promise<void> {
  const pending = confirmation.value
  confirmation.value = undefined
  if (pending) await mutate(pending.group, pending.action, { confirm: true }, undefined, pending.trigger)
}
onMounted(() => load())
</script>

<style scoped>
.applying-workspace { overflow-wrap: anywhere; min-width: 0; }
.applying-workspace li { margin-block: .5rem; }
</style>

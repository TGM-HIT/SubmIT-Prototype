<template>
  <section class="panel stack applying-workspace" aria-label="Apply together">
    <h3 ref="heading" tabindex="-1">Apply together · shared ranked application</h3>
    <p>The accepted applying group stays together. Allocation may add students to form a final group of 3–5.
      This is separate from the post-allocation Formal proposal.</p>
    <p v-if="locked" class="notice">This period is locked. No invitations, consent or application changes are allowed.</p>
    <p v-if="loading" role="status">Loading shared applications and invitations…</p>
    <div v-if="error" class="error-box" role="alert">{{ error }} <button type="button" class="button button-secondary" @click="load()">Retry shared applications</button></div>
    <button v-if="student" ref="primary" data-shared-action="create" type="button" class="button" :disabled="busy || loading || !!error || locked || hasMembership" @click="create">
      Create applying group
    </button>
    <p v-if="!loading && !error && !groups.content.length" class="empty">No shared applications or invitations.</p>
    <article v-for="group in groups.content" :key="group.id" :data-applying-group="group.id" class="panel stack">
      <h4 :id="`group-${group.id}`" tabindex="-1">Applying group · {{ group.status }} · Revision {{ group.revision }}</h4>
      <p v-if="group.status === 'DISSOLVED'" class="notice">This group was dissolved. Every member must apply again. Earlier solo submissions are not restored.</p>
      <p v-else-if="group.status === 'SUBMITTED'" class="success-note">Effective shared application submitted for every accepted member.</p>
      <p v-else class="notice">Draft: not eligible for allocation. Existing solo submissions remain eligible only if they have not previously been replaced.</p>
      <ul aria-label="Accepted roster and revision approvals">
        <li v-for="member in group.members" :key="member.studentId">
          {{ member.displayName }}<strong v-if="member.self"> (you)</strong>
          <span v-if="member.creator"> · Creator</span> · {{ member.approved ? 'Approved this revision' : 'Approval required' }}
        </li>
      </ul>
      <ol aria-label="Current shared ranking"><li v-for="preference in group.preferences" :key="preference.projectId">{{ preference.projectTitle }}</li></ol>
      <p v-if="!group.preferences.length" class="muted">No shared ranking yet. Submission requires at least three distinct accepted projects.</p>
      <div v-if="student && !locked && !group.locked && group.status !== 'DISSOLVED'" class="stack">
        <div v-if="group.invited" class="stack">
          <p>Review the roster and ranking before accepting. Acceptance is not approval of this or future revisions.</p>
          <div class="actions"><button data-shared-action="accept" class="button" :disabled="busy" @click="mutate(group, 'ACCEPT', {}, $event)">Accept invitation</button>
            <button class="button button-secondary" :disabled="busy" @click="mutate(group, 'DECLINE', {}, $event)">Decline invitation</button></div>
        </div>
        <div v-if="group.members.some(member => member.self)" class="actions">
          <button data-shared-action="approve" class="button" :disabled="busy || group.members.some(member => member.self && member.approved)" @click="mutate(group, 'APPROVE', {}, $event)">Approve revision {{ group.revision }}</button>
          <button v-if="!group.manager" data-shared-action="leave" class="button button-danger" :disabled="busy" @click="confirm(group, 'LEAVE', $event)">Leave applying group</button>
        </div>
        <template v-if="group.manager">
          <form class="stack" @submit.prevent="search(group, 0)">
            <label class="label" :for="`lookup-${group.id}`">Find students to invite by display name</label>
            <input :id="`lookup-${group.id}`" v-model="queries[group.id]" class="input" />
            <button data-shared-action="search" type="submit" class="button button-secondary" :disabled="busy">Search students</button>
          </form>
          <p v-if="lookupLoading" role="status">Loading student lookup…</p>
          <div v-if="lookupErrors[group.id]" role="alert" class="error-box">{{ lookupErrors[group.id] }}</div>
          <template v-if="lookups[group.id]">
            <p v-if="!lookups[group.id]?.content.length" class="empty">No matching students.</p>
            <ul><li v-for="candidate in lookups[group.id]?.content" :key="candidate.id">
              {{ candidate.displayName }} <button type="button" class="button button-secondary button-small"
                :data-student-name="candidate.displayName" :disabled="busy || group.members.some(member => member.studentId === candidate.id)"
                @click="mutate(group, 'INVITE', { studentId: candidate.id }, $event)">Invite {{ candidate.displayName }}</button>
            </li></ul>
            <div class="actions"><button class="button button-secondary" :disabled="lookups[group.id]!.page === 0" @click="search(group, lookups[group.id]!.page - 1)">Previous students</button>
              <span>Student page {{ lookups[group.id]!.page + 1 }} of {{ Math.max(1, lookups[group.id]!.totalPages) }}</span>
              <button class="button button-secondary" :disabled="lookups[group.id]!.page + 1 >= lookups[group.id]!.totalPages" @click="search(group, lookups[group.id]!.page + 1)">Next students</button></div>
          </template>
          <div v-if="invitations[group.id]" class="stack">
            <h5>Pending invitations</h5>
            <p v-if="!invitations[group.id]?.content.length">No pending invitations.</p>
            <ul><li v-for="invite in invitations[group.id]?.content" :key="invite.studentId">{{ invite.displayName }}
              <button class="button button-secondary button-small" :disabled="busy" @click="mutate(group, 'REVOKE', { studentId: invite.studentId }, $event)">Revoke invitation to {{ invite.displayName }}</button></li></ul>
            <div class="actions"><button class="button button-secondary" :disabled="invitations[group.id]!.page === 0" @click="loadInvitations(group, invitations[group.id]!.page - 1)">Previous invitations</button>
              <span>Invitation page {{ invitations[group.id]!.page + 1 }} of {{ Math.max(1, invitations[group.id]!.totalPages) }}</span>
              <button class="button button-secondary" :disabled="invitations[group.id]!.page + 1 >= invitations[group.id]!.totalPages" @click="loadInvitations(group, invitations[group.id]!.page + 1)">Next invitations</button></div>
          </div>
          <form class="stack" @submit.prevent="saveRanking(group, $event)">
            <h5>Edit shared ranking</h5>
            <p>Saving an actual change reopens submission and invalidates every approval.</p>
            <ol><li v-for="(id, index) in rankings[group.id] ?? []" :key="id">{{ title(id) }}
              <button type="button" class="button button-secondary button-small" :disabled="index === 0" @click="move(group.id, index, -1)">Move {{ title(id) }} up</button>
              <button type="button" class="button button-secondary button-small" :disabled="index + 1 === rankings[group.id]!.length" @click="move(group.id, index, 1)">Move {{ title(id) }} down</button>
              <button type="button" class="button button-secondary button-small" @click="rankings[group.id]!.splice(index, 1)">Remove {{ title(id) }}</button>
            </li></ol>
            <label class="label" :for="`shared-choice-${group.id}`">Add shared preference</label>
            <select :id="`shared-choice-${group.id}`" v-model="choices[group.id]" class="select"><option value="">Choose accepted project…</option>
              <option v-for="project in period.projectTitles ?? []" :key="project.projectId" :value="project.projectId" :disabled="rankings[group.id]?.includes(project.projectId ?? '')">{{ project.title }}</option></select>
            <button data-shared-action="add-choice" class="button button-secondary" type="button" :disabled="!choices[group.id]" @click="addChoice(group.id)">Add shared choice</button>
            <button data-shared-action="save-ranking" class="button" type="submit" :disabled="busy || (rankings[group.id]?.length ?? 0) < 3">Save shared ranking</button>
          </form>
          <div class="actions">
            <button data-shared-action="submit" class="button" :disabled="busy || group.status === 'SUBMITTED' || group.members.length < 2 || group.preferences.length < 3 || group.members.some(member => !member.approved)" @click="confirm(group, 'SUBMIT', $event)">Submit shared application</button>
            <button v-if="group.status === 'SUBMITTED'" data-shared-action="withdraw" class="button button-danger" :disabled="busy" @click="confirm(group, 'WITHDRAW', $event)">Withdraw shared application</button>
            <button data-shared-action="dissolve" class="button button-danger" :disabled="busy" @click="confirm(group, 'DISSOLVE', $event)">Dissolve applying group</button>
          </div>
          <label class="label" :for="`transfer-${group.id}`">Transfer creator role to an accepted member</label>
          <select :id="`transfer-${group.id}`" v-model="transfers[group.id]" class="select"><option value="">Choose new creator…</option>
            <option v-for="member in group.members.filter(member => !member.creator)" :key="member.studentId" :value="member.studentId">{{ member.displayName }}</option></select>
          <button class="button button-secondary" :disabled="busy || !transfers[group.id]" @click="mutate(group, 'TRANSFER', { studentId: transfers[group.id] }, $event)">Transfer ownership</button>
        </template>
      </div>
    </article>
    <div class="actions" v-if="groups.totalPages > 1">
      <button class="button button-secondary" :disabled="loading || groups.page === 0" @click="load(groups.page - 1)">Previous shared applications</button>
      <span>Page {{ groups.page + 1 }} of {{ groups.totalPages }} · {{ groups.totalElements }} shared applications and invitations</span>
      <button class="button button-secondary" :disabled="loading || groups.page + 1 >= groups.totalPages" @click="load(groups.page + 1)">Next shared applications</button>
    </div>
    <p v-if="success" role="status" class="success-note">{{ success }}</p>
    <ModalDialog v-if="confirmation" title="Confirm shared application change" :return-focus="confirmation.trigger" :fallback-focus="primary ?? heading" @close="cancel">
      <h4>Confirm {{ confirmation.action.toLowerCase() }}</h4>
      <p v-if="confirmation.action === 'SUBMIT'">This atomically replaces every accepted member's individual submission. Every member must have approved this exact roster and ranking revision.</p>
      <p v-else>Shared eligibility will end. Affected students must apply again or renew approval and resubmit; earlier solo submissions are never restored.</p>
      <div class="actions"><button ref="cancelControl" data-shared-action="cancel" type="button" class="button button-secondary" @click="cancel">Cancel change</button>
        <button data-shared-action="confirm" type="button" class="button button-danger" @click="confirmed">Confirm change</button></div>
    </ModalDialog>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import type { components, operations } from '../../api/schema'
import type { Page } from '../../api/client'
import { applyingGroupsApi, ideasApi } from '../../api/services'
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
const message = (cause: unknown) => cause instanceof Error ? cause.message : 'The request failed. Retry without losing your input.'
const title = (id: string) => props.period.projectTitles?.find(project => project.projectId === id)?.title ?? 'Unknown project'

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
    success.value = `${action.toLowerCase()} completed. Effective application and consent state refreshed.`
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

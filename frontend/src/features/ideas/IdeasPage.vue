<template>
  <section class="page">
    <div class="page-heading">
      <div>
        <p class="page-intro">{{ $t('ideas.intro') }}</p>
      </div>
      <button v-if="canAuthor" id="new-idea-button" class="button" type="button" @click="startCreate">{{ $t('ideas.newIdea') }}</button>
    </div>

    <form v-if="editorOpen" class="panel stack" @submit.prevent="saveIdea">
      <div class="page-heading"><h2 class="section-title">{{ editingId ? $t('ideas.updateIdea') : $t('ideas.createDraft') }}</h2><button class="button button-secondary button-small" type="button" @click="editorOpen = false">{{ $t('ideas.close') }}</button></div>
      <div class="grid grid-2">
        <div class="field"><label class="label" for="idea-title">{{ $t('ideas.titleLimit') }}</label><input id="idea-title" v-model="ideaForm.title" class="input" maxlength="200" required /><span class="metric">{{ number(ideaForm.title.length) }}/200</span></div>
        <div class="field"><label class="label" for="school-year">{{ $t('ideas.schoolYear') }}</label><select id="school-year" v-model="ideaForm.schoolYear" class="select" required><option value="" disabled>{{ $t('ideas.selectYear') }}</option><option v-for="year in schoolYears" :key="year" :value="year">{{ year }}</option><option v-if="ideaForm.schoolYear && !schoolYears.includes(ideaForm.schoolYear)" :value="ideaForm.schoolYear">{{ ideaForm.schoolYear }}</option></select></div>
      </div>
      <div class="field"><label class="label" for="idea-description">{{ $t('ideas.descriptionLimit') }}</label><textarea id="idea-description" v-model="ideaForm.description" class="textarea" maxlength="5000" required /><span class="metric">{{ number(ideaForm.description.length) }}/5,000</span></div>
      <div class="field"><label class="label" for="idea-outcome">{{ $t('ideas.intendedOutcomeLimit') }}</label><textarea id="idea-outcome" v-model="ideaForm.intendedOutcome" class="textarea" maxlength="2000" required /><span class="metric">{{ number(ideaForm.intendedOutcome.length) }}/2,000</span></div>
      <fieldset v-if="!editingId && isStudent" class="panel stack collaborator-picker">
        <legend class="form-section-title">{{ $t('ideas.collaboratorsCount', { current: number(createOwners.length + 1) }) }}</legend>
        <label class="label" for="collaborator-lookup">{{ $t('ideas.findStudents') }}</label>
        <input id="collaborator-lookup" v-model="studentQuery" class="input" autocomplete="off" :placeholder="$t('ideas.searchStudents')" />
        <p v-if="studentLookupLoading" class="muted" role="status">{{ $t('ideas.searching') }}</p>
        <p v-else-if="studentLookupError" class="field-error" role="alert">{{ studentLookupError }} <button class="button button-secondary button-small" type="button" @click="searchStudents">{{ $t('common.retry') }}</button></p>
        <p v-else-if="studentQuery && !studentLookup.length" class="muted">{{ $t('ideas.noStudents') }}</p>
        <div v-if="studentLookup.length" class="choice-list"><button v-for="student in studentLookup" :key="student.id" :data-student-name="student.displayName" class="button button-secondary button-small" type="button" :disabled="createOwners.length >= 4 || createOwners.some(owner => owner.id === student.id)" @click="addCreateOwner(student)">{{ student.displayName }} · {{ $t('ideas.add') }}</button></div>
        <p v-if="createOwners.length > 0" class="muted">{{ createOwners.map(owner => owner.displayName).join(', ') }} <button v-for="owner in createOwners" :key="owner.id" type="button" class="text-button" :aria-label="`${$t('ideas.remove')} ${owner.displayName}`" @click="removeCreateOwner(owner.id)">{{ $t('ideas.remove') }}</button></p>
        <p v-if="createOwners.length >= 4" class="muted">{{ $t('ideas.maxOwners') }}</p>
      </fieldset>
      <div class="grid grid-2">
        <div class="field"><label class="label" for="scope">{{ $t('ideas.scopeLabel') }}</label><textarea id="scope" v-model="scopeText" class="textarea" required /><span class="muted">{{ $t('ideas.scopeHelp') }}</span></div>
        <div class="field"><label class="label" for="learning">{{ $t('ideas.learningLabel') }}</label><textarea id="learning" v-model="learningText" class="textarea" /><span class="muted">{{ $t('ideas.learningHelp') }}</span></div>
      </div>
      <div v-if="task.error.value" class="error-box" role="alert">{{ task.error.value }}</div>
      <div class="actions"><button class="button" :disabled="task.loading.value">{{ task.loading.value ? $t('ideas.saving') : $t('ideas.saveDraft') }}</button><button class="button button-secondary" type="button" @click="editorOpen = false">{{ $t('ideas.cancel') }}</button></div>
    </form>

    <ModalDialog v-if="reviewOpen && selectedIdea" :title="isSpecReview ? $t('ideas.reviewSpecification') : $t('ideas.reviewIdea')" :return-focus="reviewReturnFocus" :fallback-focus="reviewFallbackFocus" @close="closeReview">
      <form class="stack" @submit.prevent="submitReview">
        <p class="muted">{{ $t('ideas.ratingHelp') }}</p>
        <div class="grid grid-2">
          <div class="field"><label class="label" for="decision">{{ $t('ideas.decision') }}</label><select id="decision" v-model="reviewForm.decision" class="select"><option v-if="isSpecReview" value="ACCEPT_FOR_APPLICATIONS">{{ $t('ideas.acceptForApplications') }}</option><option v-else value="APPROVE_FOR_SPECIFICATION">{{ $t('ideas.approveSpecification') }}</option><option value="REQUEST_REVISION">{{ $t('ideas.requestRevision') }}</option><option value="DECLINE">{{ $t('ideas.decline') }}</option></select></div>
          <div class="field"><label class="label" for="recommendation">{{ $t('ideas.recommendation') }}</label><input id="recommendation" v-model="reviewForm.recommendation" class="input" maxlength="2000" /></div>
        </div>
        <div class="field"><label class="label" for="feedback">{{ $t('ideas.feedbackRequired') }}</label><textarea id="feedback" v-model="reviewForm.feedback" class="textarea" maxlength="5000" required @input="feedbackError = ''" /><span v-if="feedbackError" class="field-error" role="alert">{{ feedbackError }}</span></div>
        <div class="grid grid-3">
          <div v-for="rating in ratings" :key="rating.key" class="field"><label class="label" :for="rating.key">{{ rating.label }} (1–5)</label><select :id="rating.key" v-model="reviewForm[rating.key]" class="select"><option value="">{{ $t('ideas.notRated') }}</option><option v-for="value in 5" :key="value" :value="value">{{ value }}</option></select></div>
        </div>
        <div v-if="task.error.value" class="error-box" role="alert">{{ task.error.value }}</div>
        <div class="actions"><button class="button" :disabled="task.loading.value">{{ $t('ideas.recordDecision') }}</button><button class="button button-secondary" type="button" @click="closeReview">{{ $t('ideas.cancel') }}</button></div>
      </form>
    </ModalDialog>

    <div class="panel stack">
      <div class="toolbar">
        <div class="field"><label class="label" for="status-filter">{{ $t('ideas.lifecycleStatus') }}</label><select id="status-filter" v-model="statusFilter" class="select"><option value="">{{ $t('ideas.allVisibleStatuses') }}</option><option v-for="status in statuses" :key="status" :value="status">{{ readable(status) }}</option></select></div>
        <div class="field"><label class="label" for="year-filter">{{ $t('ideas.schoolYear') }}</label><select id="year-filter" v-model="yearFilter" class="select"><option value="">{{ $t('ideas.allSchoolYears') }}</option><option v-for="year in schoolYears" :key="year" :value="year">{{ year }}</option></select></div>
        <button id="ideas-refresh-list" class="button button-secondary" type="button" @click="load(0)">{{ $t('ideas.refreshList') }}</button>
      </div>
      <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="load(page.page)">
        <div v-if="visibleIdeas.length" class="data-list">
          <article v-for="idea in visibleIdeas" :key="idea.id" class="data-row">
            <div class="row-main">
              <h3>{{ idea.title }}</h3>
              <span v-if="idea.teacherIdea" class="status-badge teacher-idea-label">{{ $t('ideas.teacherIdea') }}</span>
              <p>{{ idea.description }}</p>
              <p>{{ $t('ideas.schoolYear') }} {{ idea.schoolYear }} <span v-if="canSeeOwner"> · {{ $t('ideas.submittedBy') }} {{ idea.ownerDisplayName }} · {{ $t('ideas.ownersLabel') }} {{ (idea.owners ?? []).map(owner => canSeeOwner ? `${owner.displayName} — ${$t('ideas.ownerClass')} ${owner.className || $t('ideas.notAssigned')}; ${$t('ideas.electiveSubjects')} ${owner.electiveSubjectNames?.join(', ') || $t('ideas.none')}` : owner.displayName).join(', ') || idea.ownerDisplayName }}</span></p>
              <p v-if="isTeacher && idea.ownSupervisionPreference" class="muted supervision-summary">
                <strong>{{ $t('ideas.yourSupervisionPreference') }}</strong> {{ preferenceLabel(idea.ownSupervisionPreference) }}
              </p>
              <span class="status-badge" :class="`status-${idea.status?.toLowerCase()}`">{{ readable(idea.status) }}</span>
            </div>
            <div class="actions">
              <button :id="`details-button-${idea.id}`" data-review-focus-fallback class="button button-secondary button-small" type="button" @click="openDetails(idea, $event)">{{ $t('ideas.detailsHistory') }}</button>
              <button v-if="isTeacher && canReview(idea) && !isCreator(idea)" :id="`review-button-${idea.id}`" class="button button-small" type="button" @click="startReview(idea, $event)">{{ $t('ideas.review') }}</button>
              <span v-if="isTeacher && canReview(idea) && isCreator(idea)" class="muted">{{ $t('ideas.cannotReviewOwn') }}</span>
              <button v-if="isAuthor(idea) && editable(idea) && !hasWithdrawnSpecification(idea)" class="button button-secondary button-small" type="button" @click="editIdea(idea)">{{ $t('ideas.edit') }}</button>
              <button v-if="isAuthor(idea) && editable(idea) && !hasWithdrawnSpecification(idea)" class="button button-small" type="button" @click="submitIdea(idea, $event)">{{ $t('ideas.submit') }}</button>
              <button v-if="isAuthor(idea) && canWithdraw(idea)" class="button button-danger button-small" type="button" @click="withdrawIdea(idea)">{{ $t('ideas.withdraw') }}</button>
            </div>
          </article>
        </div>
        <p v-else class="empty">{{ $t('ideas.noMatches') }}</p>
        <div class="pagination"><span>{{ $t('ideas.pageSubmissions', { current: number(page.page + 1), total: number(Math.max(page.totalPages, 1)), count: number(page.totalElements) }) }}</span><div class="actions"><button class="button button-secondary button-small" :disabled="page.page === 0" @click="load(page.page - 1)">{{ $t('ideas.previous') }}</button><button class="button button-secondary button-small" :disabled="page.page + 1 >= page.totalPages" @click="load(page.page + 1)">{{ $t('ideas.next') }}</button></div></div>
      </AsyncPanel>
    </div>

    <ModalDialog v-if="detailsOpen && selectedIdea" :title="$t('ideas.detailsTitle')" :return-focus="detailsReturnFocus" @close="closeDetails">
      <section class="stack">
      <div class="page-heading"><div><h2 class="section-title">{{ selectedIdea.title }} · {{ $t('ideas.history') }}</h2><span v-if="selectedIdea.teacherIdea" class="status-badge teacher-idea-label">{{ $t('ideas.teacherIdea') }}</span><p class="muted">{{ $t('ideas.decisionsVisible') }}</p></div><button id="close-details-button" class="button button-secondary button-small" @click="closeDetails">{{ $t('ideas.closeDetails') }}</button></div>
      <section class="owner-section" :aria-label="$t('ideas.ideaOwners')">
        <h3 class="form-section-title">{{ $t('ideas.owners', { count: number(selectedIdea.owners?.length ?? 1) }) }}</h3>
        <ul class="owner-list"><li v-for="owner in selectedIdea.owners ?? fallbackOwners(selectedIdea)" :key="owner.id">{{ owner.displayName }}<span v-if="owner.creator"> · {{ $t('ideas.creator') }}</span><span v-if="canSeeOwner"> · {{ $t('ideas.ownerClass') }} {{ owner.className || $t('ideas.notAssigned') }} · {{ $t('ideas.electiveSubjects') }} {{ owner.electiveSubjectNames?.join(', ') || $t('ideas.none') }}</span>
          <button v-if="isCreator(selectedIdea) && !owner.creator" class="button button-danger button-small" type="button" :aria-label="`${$t('ideas.remove')} ${owner.displayName}`" @click="requestOwnerConfirmation('remove', selectedIdea, owner.id, $event)">{{ $t('ideas.remove') }}</button>
        </li></ul>
        <template v-if="isCreator(selectedIdea) && isStudent">
          <label class="label" for="details-collaborator-lookup">{{ $t('ideas.addStudent') }}</label>
          <input id="details-collaborator-lookup" v-model="studentQuery" class="input" autocomplete="off" :placeholder="$t('ideas.searchByDisplayName')" />
          <p v-if="studentLookupLoading" class="muted" role="status">{{ $t('ideas.searching') }}</p>
          <p v-else-if="studentLookupError" class="field-error" role="alert">{{ studentLookupError }} <button class="button button-secondary button-small" type="button" @click="searchStudents">{{ $t('common.retry') }}</button></p>
          <p v-else-if="studentQuery && !studentLookup.length" class="muted">{{ $t('ideas.noStudents') }}</p>
          <div class="choice-list"><button v-for="student in studentLookup" :key="student.id" :data-student-name="student.displayName" class="button button-secondary button-small" type="button" :disabled="(selectedIdea.owners?.length ?? 1) >= 5 || selectedIdea.owners?.some(owner => owner.id === student.id)" @click="addOwner(selectedIdea, student)">{{ student.displayName }} · {{ $t('ideas.add') }}</button></div>
          <p v-if="(selectedIdea.owners?.length ?? 1) >= 5" class="muted">{{ $t('ideas.maxOwners') }}</p>
        </template>
        <button v-else-if="isStudent && isOwner(selectedIdea)" id="leave-idea-button" class="button button-danger button-small" type="button" @click="requestOwnerConfirmation('leave', selectedIdea, undefined, $event)">{{ $t('ideas.leaveIdea') }}</button>
      </section>
      <div class="grid grid-2">
        <div><p class="eyebrow">{{ $t('ideas.projectDescription') }}</p><p class="muted">{{ selectedIdea.description }}</p><p><strong>{{ $t('ideas.intendedOutcome') }}</strong><br />{{ selectedIdea.intendedOutcome }}</p><p><strong>{{ $t('ideas.initialScope') }}</strong></p><ul class="muted"><li v-for="point in selectedIdea.scopePoints ?? []" :key="point">{{ point }}</li></ul></div>
        <div><p class="eyebrow">{{ $t('ideas.lifecycleTitle') }}</p><p><span class="status-badge">{{ readable(selectedIdea.status) }}</span></p><p class="notice">{{ lifecycleMessage(selectedIdea.status) }}</p><div v-if="selectedIdea.specification"><strong>{{ $t('ideas.specification') }}</strong><p class="muted">{{ $t('ideas.objectives') }} {{ selectedIdea.specification.objectives?.join(' · ') }}</p><p class="muted">{{ $t('ideas.scopeLabel') }} {{ selectedIdea.specification.scope }}</p></div></div>
      </div>
      <h3 class="form-section-title">{{ $t('ideas.teacherDecisionHistory') }}</h3>
      <div v-if="selectedIdea.reviews?.length" class="data-list"><article v-for="review in selectedIdea.reviews" :key="review.id" class="data-row"><div class="row-main"><h3>{{ readable(review.decision) }}</h3><p>{{ review.feedback }}</p><p class="muted">{{ $t('ideas.teacher') }} {{ review.teacherDisplayName }}<template v-if="review.reviewType === 'SPECIFICATION'"> · {{ $t('ideas.professionalRatings') }}</template></p><p v-if="recordedRatings(review).length" class="muted">{{ $t('ideas.ratings') }} {{ recordedRatings(review).join(' · ') }}</p></div></article></div>
      <p v-else class="muted">{{ $t('ideas.noDecisions') }}</p>
      <h3 v-if="selectedIdea.supervisionPreferences?.length" class="form-section-title">{{ $t('ideas.recordedPreferences') }}</h3>
      <div v-for="entry in selectedIdea.supervisionPreferences ?? []" :key="entry.id" class="choice-item"><span>{{ entry.teacherDisplayName }} · {{ preferenceLabel(entry.preference) }}</span><span class="muted">{{ entry.note }}</span></div>
      <div v-if="isAuthor(selectedIdea) && canSpecify(selectedIdea)" class="actions"><button id="develop-specification" class="button" @click="startSpecification">{{ selectedIdea.status === 'WITHDRAWN' ? $t('ideas.editSpecification') : $t('ideas.developSpecification') }}</button><button v-if="selectedIdea.status === 'IDEA_APPROVED_FOR_SPECIFICATION'" id="submit-specification" class="button button-secondary" @click="submitSpecification">{{ $t('ideas.submitSpecification') }}</button></div>
      <div v-if="isTeacher && canReview(selectedIdea) && !isCreator(selectedIdea)" class="actions"><button id="review-from-details" class="button" type="button" @click="openReview($event)">{{ selectedIdea.status === 'SPECIFICATION_SUBMITTED' ? $t('ideas.reviewSpecificationAction') : $t('ideas.reviewIdeaAction') }}</button></div>
      <p v-if="isTeacher && canReview(selectedIdea) && isCreator(selectedIdea)" class="muted" role="note">{{ $t('ideas.cannotReviewOwn') }}</p>
      <form v-if="isTeacher && !['IDEA_DRAFT','WITHDRAWN','DECLINED'].includes(selectedIdea.status ?? '')" class="panel stack" @submit.prevent="savePreference">
        <h3 class="form-section-title">{{ $t('ideas.yourPreference') }}</h3>
        <div class="grid grid-2"><div class="field"><label class="label" for="preference">{{ $t('ideas.preference') }}</label><select id="preference" v-model="preference" class="select"><option value="WANTS_TO_SUPERVISE">{{ $t('ideas.wantsToSupervise') }}</option><option value="WILLING_TO_SUPERVISE">{{ $t('ideas.willingToSupervise') }}</option><option value="NOT_WILLING_TO_SUPERVISE">{{ $t('ideas.notWillingToSupervise') }}</option><option value="NOT_AVAILABLE">{{ $t('ideas.notAvailable') }}</option></select></div><div class="field"><label class="label" for="preference-note">{{ $t('ideas.optionalNote') }}</label><input id="preference-note" v-model="preferenceNote" class="input" maxlength="2000" /></div></div><button class="button button-secondary" :disabled="task.loading.value">{{ $t('ideas.savePreference') }}</button>
      </form>
      <form v-if="isAdmin" class="panel stack" @submit.prevent="assignSupervisor">
        <h3 class="form-section-title">{{ $t('ideas.confirmSupervisor') }}</h3><p class="muted">{{ $t('ideas.supervisorHelp') }}</p>
        <p v-if="selectedIdea.confirmedSupervisorDisplayName" class="muted">{{ $t('ideas.currentSupervisor') }} {{ selectedIdea.confirmedSupervisorDisplayName }}</p>
        <div class="field"><label class="label" for="teacher-id">{{ $t('ideas.teacher') }}</label><select id="teacher-id" v-model="teacherId" class="select" required @change="refusalAcknowledged = false"><option value="">{{ $t('ideas.selectTeacher') }}</option><optgroup v-for="group in teacherGroups" :key="group.key" :label="group.label"><option v-for="entry in group.teachers" :key="entry.id" :value="entry.id">{{ entry.displayName }}</option></optgroup></select></div>
        <div v-if="selectedTeacherRefusal" class="notice refusal-warning" role="alert">
          <strong>{{ $t('ideas.declinedToSupervise', { teacher: selectedTeacherRefusal.teacher.displayName }) }}</strong>
          <span v-if="selectedTeacherRefusal.note"> {{ selectedTeacherRefusal.note }}</span>
          <label class="refusal-acknowledgment"><input v-model="refusalAcknowledged" type="checkbox"> <span>{{ $t('ideas.refusalAcknowledgment') }}</span></label>
        </div>
        <p v-if="task.error.value" class="error-box" role="alert">{{ task.error.value }}</p>
        <div class="actions"><button class="button button-secondary" :disabled="!teacherId || (selectedTeacherRefusal !== null && !refusalAcknowledged)">{{ $t('ideas.confirmSupervisor') }}</button><button v-if="teacherId" class="button button-secondary" type="button" @click="cancelSupervisorSelection">{{ $t('ideas.cancel') }}</button></div>
      </form>
      <form v-if="specEditorOpen" class="panel stack" @submit.prevent="saveSpecification">
        <h2 class="section-title">{{ $t('ideas.fullSpecification', { title: selectedIdea.title }) }}</h2>
        <div class="notice">{{ $t('ideas.specificationApprovalHelp') }}</div>
        <div class="grid grid-2"><TextListField v-model="specForm.objectives" :label="$t('ideas.objectives')" /><TextListField v-model="specForm.intendedUsers" :label="$t('applications.intendedUsers')" /><TextListField v-model="specForm.requirements" :label="$t('applications.projectRequirements')" /><TextListField v-model="specForm.deliverables" :label="$t('applications.deliverables')" /></div>
        <div class="field"><label class="label" for="spec-scope">{{ $t('ideas.proposedScope') }}</label><textarea id="spec-scope" v-model="specForm.scope" class="textarea" maxlength="5000" required /></div>
        <TextListField v-model="specForm.dependencies" :label="$t('ideas.resourcesDependencies')" />
        <div v-if="task.error.value" class="error-box" role="alert">{{ task.error.value }}</div>
        <div class="actions"><button class="button" :disabled="task.loading.value">{{ $t('ideas.saveSpecification') }}</button><button class="button button-secondary" type="button" @click="specEditorOpen = false">{{ $t('ideas.cancel') }}</button></div>
      </form>
      </section>
    </ModalDialog>

    <ModalDialog v-if="ownerConfirmation" :title="ownerConfirmation.type === 'leave' ? $t('ideas.leaveIdeaTitle') : $t('ideas.removeCollaboratorTitle')" :return-focus="ownerConfirmationReturnFocus" @close="ownerConfirmation = null">
      <p class="muted">{{ ownerConfirmation.type === 'leave' ? $t('ideas.leaveIdeaHelp') : $t('ideas.removeCollaboratorHelp') }}</p>
      <div class="actions"><button id="confirm-owner-change" class="button button-danger" type="button" @click="confirmOwnerChange">{{ ownerConfirmation.type === 'leave' ? $t('ideas.leaveIdea') : $t('ideas.removeCollaborator') }}</button><button class="button button-secondary" type="button" @click="ownerConfirmation = null">{{ $t('ideas.cancel') }}</button></div>
    </ModalDialog>
    <p v-if="task.success.value" class="success-note" role="status">{{ task.success.value }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import type { components } from '../../api/schema'
import { adminApi, ideasApi, periodsApi } from '../../api/services'
import { ApiError } from '../../api/client'
import type { Page } from '../../api/client'
import { useAuthStore } from '../../stores/auth'
import { useTask } from '../../composables/useTask'
import { i18n } from '../../i18n'
import { formatNumber } from '../../i18n'
import ModalDialog from '../../components/ModalDialog.vue'
import AsyncPanel from '../../components/AsyncPanel.vue'
import TextListField from './TextListField.vue'

type Idea = components['schemas']['IdeaResponse']
type User = components['schemas']['UserResponse']
type StudentChoice = components['schemas']['StudentLookupResponse']
type Owner = components['schemas']['IdeaOwnerResponse']
type IdeaOverview = Idea & { ownSupervisionPreference?: string }
type IdeaInput = components['schemas']['IdeaRequest']
type Review = components['schemas']['ReviewResponse']
type ReviewRequest = components['schemas']['ReviewRequest']
type RatingKey = 'workSize' | 'curriculumRelevance' | 'technicalComplexity' | 'feasibility' | 'learningValue' | 'resourceNeeds' | 'riskAndDependencies'
const ratings = computed(() => ([
  'workSize', 'curriculumRelevance', 'technicalComplexity', 'feasibility',
  'learningValue', 'resourceNeeds', 'riskAndDependencies',
] as RatingKey[]).map((key) => ({ key, label: String(i18n.global.t(`ideas.ratingLabels.${key}`)) })))
const statuses = ['IDEA_DRAFT','IDEA_SUBMITTED','IDEA_NEEDS_REVISION','IDEA_APPROVED_FOR_SPECIFICATION','SPECIFICATION_SUBMITTED','SPECIFICATION_NEEDS_REVISION','ACCEPTED_FOR_APPLICATIONS','DECLINED','WITHDRAWN']
const auth = useAuthStore()
const task = useTask()
const number = formatNumber
const isStudent = computed(() => auth.user?.role === 'STUDENT')
const isTeacher = computed(() => auth.user?.role === 'TEACHER')
const isAdmin = computed(() => auth.user?.role === 'ADMIN')
const canAuthor = computed(() => isStudent.value || isTeacher.value)
const canSeeOwner = computed(() => !isStudent.value)
const page = ref<Page<IdeaOverview>>({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const schoolYears = ref<string[]>([])
const statusFilter = ref('')
const yearFilter = ref('')
const selectedIdea = ref<Idea | null>(null)
const detailsOpen = ref(false)
const detailsReturnFocus = ref<HTMLElement | null>(null)
const editorOpen = ref(false)
const specEditorOpen = ref(false)
const reviewOpen = ref(false)
const feedbackError = ref('')
const reviewReturnFocus = ref<HTMLElement | null>(null)
const reviewFallbackFocus = ref<HTMLElement | null>(null)
const editingId = ref('')
const isSpecReview = ref(false)
const preference = ref<components['schemas']['SupervisionPreferenceRequest']['preference']>('WILLING_TO_SUPERVISE')
const preferenceNote = ref('')
const teacherId = ref('')
const refusalAcknowledged = ref(false)
const teachers = ref<User[]>([])
const createOwners = ref<StudentChoice[]>([])
const studentQuery = ref('')
const studentLookup = ref<StudentChoice[]>([])
const studentLookupLoading = ref(false)
const studentLookupError = ref('')
let studentLookupRequest = 0
const ownerConfirmation = ref<{ type: 'remove' | 'leave'; idea: Idea; studentId?: string } | null>(null)
const ownerConfirmationReturnFocus = ref<HTMLElement | null>(null)
let ownerAccessPoll: number | undefined
let ownerAccessCheck: Promise<void> | null = null
const scopeText = ref('')
const learningText = ref('')
const ideaForm = reactive({ title: '', description: '', intendedOutcome: '', schoolYear: '' })
const specForm = reactive({ objectives: [] as string[], intendedUsers: [] as string[], requirements: [] as string[], deliverables: [] as string[], scope: '', dependencies: [] as string[] })
const reviewForm = reactive<Record<RatingKey | 'decision' | 'feedback' | 'recommendation', string | number>>({
  decision: 'APPROVE_FOR_SPECIFICATION', feedback: '', recommendation: '',
  workSize: '', curriculumRelevance: '', technicalComplexity: '', feasibility: '', learningValue: '', resourceNeeds: '', riskAndDependencies: '',
})
const visibleIdeas = computed(() => page.value.content.filter((idea) => !yearFilter.value || idea.schoolYear === yearFilter.value))
const preferenceForTeacher = computed(() => new Map(
  (selectedIdea.value?.supervisionPreferences ?? []).map((entry) => [entry.teacherId, entry]),
))
const teacherGroups = computed(() => {
  const groups = [
    { key: 'WANTS_TO_SUPERVISE', label: String(i18n.global.t('ideas.preferenceGroups.wants')) },
    { key: 'WILLING_TO_SUPERVISE', label: String(i18n.global.t('ideas.preferenceGroups.willing')) },
    { key: 'NO_PREFERENCE', label: String(i18n.global.t('ideas.preferenceGroups.noPreference')) },
    { key: 'NOT_WILLING', label: String(i18n.global.t('ideas.preferenceGroups.notWilling')) },
  ]
  return groups.map((group) => ({
    ...group,
    teachers: teachers.value
      .map((teacher) => ({ ...teacher, preferenceEntry: preferenceForTeacher.value.get(teacher.id ?? '') }))
      .filter(({ preferenceEntry }) => {
        const value = preferenceEntry?.preference
        if (group.key === 'WANTS_TO_SUPERVISE') return value === 'WANTS_TO_SUPERVISE'
        if (group.key === 'WILLING_TO_SUPERVISE') return value === 'WILLING_TO_SUPERVISE'
        if (group.key === 'NO_PREFERENCE') return !value
        return value === 'NOT_WILLING_TO_SUPERVISE' || value === 'NOT_AVAILABLE'
      })
      .sort((first, second) => (first.displayName ?? '').localeCompare(second.displayName ?? '')),
  })).filter((group) => group.teachers.length > 0)
})
const selectedTeacherRefusal = computed(() => {
  const teacher = teachers.value.find((entry) => entry.id === teacherId.value)
  const preferenceEntry = preferenceForTeacher.value.get(teacherId.value)
  if (!teacher || !['NOT_WILLING_TO_SUPERVISE', 'NOT_AVAILABLE'].includes(preferenceEntry?.preference ?? '')) return null
  return { teacher, note: preferenceEntry?.note }
})

function fallbackOwners(idea: Idea): Owner[] {
  return [{ id: idea.ownerId, displayName: idea.ownerDisplayName, creator: true }]
}
function isOwner(idea: Idea): boolean {
  return Boolean(auth.user?.id && (idea.owners ?? fallbackOwners(idea)).some(owner => owner.id === auth.user?.id))
}
function isCreator(idea: Idea): boolean {
  return Boolean(auth.user?.id && idea.creatorId === auth.user.id)
}
function isAuthor(idea: Idea): boolean {
  return isStudent.value ? isOwner(idea) : isTeacher.value && isCreator(idea)
}
async function searchStudents(): Promise<void> {
  const requestId = ++studentLookupRequest
  const query = studentQuery.value
  studentLookup.value = []
  studentLookupLoading.value = true
  studentLookupError.value = ''
  try {
    const result = await ideasApi.lookupStudents(query)
    if (requestId !== studentLookupRequest || query !== studentQuery.value) return
    const ownedIds = selectedIdea.value?.owners?.map(owner => owner.id) ?? []
    studentLookup.value = result.content.filter(student => student.id !== auth.user?.id && !ownedIds.includes(student.id))
  } catch (error) {
    if (requestId !== studentLookupRequest) return
    studentLookupError.value = error instanceof ApiError ? error.message : String(i18n.global.t('ideas.searchFailed'))
  } finally {
    if (requestId === studentLookupRequest) studentLookupLoading.value = false
  }
}
function addCreateOwner(student: StudentChoice): void {
  if (createOwners.value.length < 4 && !createOwners.value.some(owner => owner.id === student.id)) createOwners.value.push(student)
}
function removeCreateOwner(id?: string): void { createOwners.value = createOwners.value.filter(owner => owner.id !== id) }
async function addOwner(idea: Idea, student: StudentChoice): Promise<void> {
  if (!idea.id || !isCreator(idea)) return
  const result = await task.run(() => ideasApi.addOwner(idea.id!, student.id ?? ''), String(i18n.global.t('ideas.collaboratorAdded')))
  if (result) {
    selectedIdea.value = result
    await load(page.value.page)
    studentQuery.value = ''
    await searchStudents()
    await nextTick()
    document.getElementById('details-collaborator-lookup')?.focus()
  }
}
function stopOwnerAccessPoll(): void {
  if (ownerAccessPoll !== undefined) window.clearInterval(ownerAccessPoll)
  ownerAccessPoll = undefined
}
function requestOwnerConfirmation(type: 'remove' | 'leave', idea: Idea, studentId?: string, event?: MouseEvent): void {
  stopOwnerAccessPoll()
  ownerConfirmationReturnFocus.value = event?.currentTarget instanceof HTMLElement ? event.currentTarget : null
  ownerConfirmation.value = { type, idea, studentId }
}
async function confirmOwnerChange(): Promise<void> {
  const pending = ownerConfirmation.value
  if (!pending?.idea.id) return
  stopOwnerAccessPoll()
  if (ownerAccessCheck) await ownerAccessCheck
  if (!detailsOpen.value) return
  if (pending.type === 'remove' && pending.studentId) {
    const result = await task.run(() => ideasApi.removeOwner(pending.idea.id!, pending.studentId!), String(i18n.global.t('ideas.collaboratorRemoved')))
    if (result) {
      selectedIdea.value = result
      ownerConfirmation.value = null
      await load(page.value.page)
      task.success.value = String(i18n.global.t('ideas.collaboratorRemoved'))
      await nextTick()
      document.getElementById('details-collaborator-lookup')?.focus()
    }
    return
  }
  if (pending.type === 'leave') {
    const result = await task.run(async () => { await ideasApi.leave(pending.idea.id!); return true })
    if (result) {
      ownerConfirmation.value = null
      detailsOpen.value = false
      selectedIdea.value = null
      await load(page.value.page)
      task.success.value = String(i18n.global.t('ideas.leftIdea'))
      await nextTick()
      if (isStudent.value) document.getElementById('new-idea-button')?.focus()
      else document.getElementById('status-filter')?.focus()
    }
  }
}
function readable(value?: string): string {
  if (!value) return String(i18n.global.t('common.unknown'))
  const key = `ideas.states.${value.toLowerCase()}`
  return i18n.global.te(key)
    ? String(i18n.global.t(key))
    : value.replaceAll('_', ' ').toLowerCase().replace(/^./, (char) => char.toUpperCase())
}
function preferenceLabel(value?: string): string {
  const key = value === 'NOT_WILLING_TO_SUPERVISE' ? 'ideas.preferences.notWilling'
    : value === 'NOT_AVAILABLE' ? 'ideas.preferences.unavailable'
      : value === 'WANTS_TO_SUPERVISE' ? 'ideas.preferences.wants'
        : value === 'WILLING_TO_SUPERVISE' ? 'ideas.preferences.willing'
          : 'ideas.preferences.noPreference'
  return String(i18n.global.t(key))
}
function recordedRatings(review: Review): string[] {
  if (review.reviewType !== 'IDEA') return []
  return ratings.value.flatMap(({ key, label }) => {
    const value = review[key]
    return typeof value === 'number' && Number.isInteger(value) ? [`${label}: ${value}/5`] : []
  })
}
function editable(idea: Idea): boolean {
  return ['IDEA_DRAFT','IDEA_NEEDS_REVISION','WITHDRAWN'].includes(idea.status ?? '')
}
function hasWithdrawnSpecification(idea: Idea): boolean {
  return idea.status === 'WITHDRAWN' && Boolean(idea.specification)
}
function focusListFallback(id: string): void {
  const target = document.getElementById(`details-button-${id}`)
    ?? document.getElementById('status-filter')
    ?? document.getElementById('ideas-refresh-list')
  target?.focus()
}
function canWithdraw(idea: Idea): boolean {
  return !['WITHDRAWN','DECLINED','ALLOCATED','FINALIZED'].includes(idea.status ?? '')
}
function canSpecify(idea: Idea): boolean {
  return ['IDEA_APPROVED_FOR_SPECIFICATION','SPECIFICATION_NEEDS_REVISION'].includes(idea.status ?? '')
    || (idea.status === 'WITHDRAWN' && Boolean(idea.specification))
}
function canReview(idea: Idea): boolean {
  return ['IDEA_SUBMITTED', 'SPECIFICATION_SUBMITTED'].includes(idea.status ?? '')
}
function lifecycleMessage(status?: string): string {
  if (status === 'IDEA_APPROVED_FOR_SPECIFICATION') return String(i18n.global.t('ideas.lifecycle.approved'))
  if (status === 'ACCEPTED_FOR_APPLICATIONS') return String(i18n.global.t('ideas.lifecycle.accepted'))
  if (status === 'SPECIFICATION_SUBMITTED') return String(i18n.global.t('ideas.lifecycle.submitted'))
  return String(i18n.global.t('ideas.lifecycle.unavailable'))
}
async function load(index = 0): Promise<void> {
  await task.run(async () => {
    const result = await ideasApi.list(index, statusFilter.value || undefined)
    const teacherId = auth.user?.id
    if (!isTeacher.value || !teacherId) {
      if (isStudent.value) {
        const content = await Promise.all(result.content.map(async (idea): Promise<IdeaOverview> => {
          if (idea.status !== 'WITHDRAWN' || !idea.id) return idea
          try {
            const details = await ideasApi.get(idea.id)
            return { ...idea, specification: details.specification }
          } catch {
            return idea
          }
        }))
        page.value = { ...result, content }
        return
      }
      page.value = result
      return
    }

    const content = await Promise.all(result.content.map(async (idea): Promise<IdeaOverview> => {
      if (!idea.id) return idea
      try {
        const details = await ideasApi.get(idea.id)
        const preference = details.supervisionPreferences?.find((entry) =>
          entry.teacherId === teacherId
          && ['WANTS_TO_SUPERVISE', 'WILLING_TO_SUPERVISE', 'NOT_WILLING_TO_SUPERVISE', 'NOT_AVAILABLE'].includes(entry.preference ?? ''),
        )?.preference
        return { ...idea, ownSupervisionPreference: preference }
      } catch {
        return idea
      }
    }))
    page.value = { ...result, content }
  })
}
async function reloadReviewedIdea(idea: Idea): Promise<void> {
  const filter = statusFilter.value || undefined
  const refreshedPage = await task.run(async () => {
    const firstPage = await ideasApi.list(0, filter)
    if (firstPage.content.some((entry) => entry.id === idea.id)) return firstPage

    for (let index = 1; index < firstPage.totalPages; index += 1) {
      const result = await ideasApi.list(index, filter)
      if (result.content.some((entry) => entry.id === idea.id)) return result
    }

    // Keep the just-reviewed item reachable even if list ordering shifts during pagination.
    return {
      ...firstPage,
      content: [idea, ...firstPage.content.filter((entry) => entry.id !== idea.id).slice(0, firstPage.size - 1)],
    }
  })
  if (refreshedPage) page.value = refreshedPage
}
async function selectIdea(idea: Idea): Promise<void> {
  const record = await task.run(() => ideasApi.get(idea.id ?? ''))
  if (record) selectedIdea.value = record
}
async function openDetails(idea: Idea, event: MouseEvent): Promise<void> {
  detailsReturnFocus.value = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  selectedIdea.value = null
  await selectIdea(idea)
  if (selectedIdea.value) {
    teacherId.value = ''
    refusalAcknowledged.value = false
    if (isAdmin.value) await loadTeachers()
    detailsOpen.value = true
  }
}
async function loadTeachers(): Promise<void> {
  const first = await task.run(() => adminApi.users(0, 'TEACHER', undefined, 100))
  if (!first) return
  const remaining = await Promise.all(Array.from({ length: Math.max(0, first.totalPages - 1) }, (_, index) =>
    task.run(() => adminApi.users(index + 1, 'TEACHER', undefined, 100))))
  teachers.value = [...first.content, ...remaining.flatMap((page) => page?.content ?? [])]
}
function closeDetails(): void {
  detailsOpen.value = false
  selectedIdea.value = null
  specEditorOpen.value = false
}
async function startReview(idea: Idea, event: MouseEvent): Promise<void> {
  const trigger = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  reviewReturnFocus.value = trigger?.closest('.data-row')?.querySelector<HTMLElement>('[data-review-focus-fallback]') ?? null
  reviewFallbackFocus.value = reviewReturnFocus.value
  selectedIdea.value = null
  await selectIdea(idea)
  if (selectedIdea.value && canReview(selectedIdea.value)) openReview()
}
function closeReview(): void {
  reviewOpen.value = false
  if (!detailsOpen.value) selectedIdea.value = null
}
function startCreate(): void {
  editingId.value = ''
  Object.assign(ideaForm, { title: '', description: '', intendedOutcome: '', schoolYear: schoolYears.value[0] ?? '' })
  scopeText.value = ''
  learningText.value = ''
  createOwners.value = []
  studentQuery.value = ''
  studentLookup.value = []
  editorOpen.value = true
}
function editIdea(idea: Idea): void {
  editingId.value = idea.id ?? ''
  Object.assign(ideaForm, { title: idea.title ?? '', description: idea.description ?? '', intendedOutcome: idea.intendedOutcome ?? '', schoolYear: idea.schoolYear ?? '' })
  scopeText.value = idea.scopePoints?.join('\n') ?? ''
  learningText.value = idea.learningTopics?.join('\n') ?? ''
  editorOpen.value = true
}
function listFrom(text: string): string[] {
  return text.split('\n').map((entry) => entry.trim()).filter(Boolean)
}
async function saveIdea(): Promise<void> {
  const input: IdeaInput = { ...ideaForm, scopePoints: listFrom(scopeText.value), learningTopics: listFrom(learningText.value), collaboratorIds: editingId.value ? [] : createOwners.value.map(owner => owner.id ?? '') }
  const wasEditing = Boolean(editingId.value)
  const result = await task.run(() => wasEditing ? ideasApi.update(editingId.value, input) : ideasApi.create(input), String(i18n.global.t('ideas.ideaSaved')))
  if (!result) return
  editorOpen.value = false
  if (wasEditing) {
    await load(page.value.page)
    return
  }
  await load(0)
  if (!result.id || page.value.content.some((idea) => idea.id === result.id)) return
  for (let index = 1; index < page.value.totalPages; index += 1) {
    await load(index)
    if (page.value.content.some((idea) => idea.id === result.id) || task.error.value) return
  }
}
async function submitIdea(idea: Idea, event?: MouseEvent): Promise<void> {
  void event
  if (!confirm(String(i18n.global.t('ideas.submitConfirm')))) return
  const result = await task.run(() => ideasApi.submit(idea.id ?? ''), String(i18n.global.t('ideas.ideaSubmitted')))
  if (result) {
    await load(page.value.page)
    await nextTick()
    focusListFallback(idea.id ?? '')
  }
}
async function withdrawIdea(idea: Idea): Promise<void> {
  if (!confirm(String(i18n.global.t('ideas.withdrawConfirm')))) return
  const result = await task.run(() => ideasApi.withdraw(idea.id ?? ''), String(i18n.global.t('ideas.ideaWithdrawn')))
  if (result) await load(page.value.page)
}
function startSpecification(): void {
  const specification = selectedIdea.value?.specification
  Object.assign(specForm, {
    objectives: specification?.objectives ?? [], intendedUsers: specification?.intendedUsers ?? [],
    requirements: specification?.requirements ?? [], deliverables: specification?.deliverables ?? [],
    scope: specification?.scope ?? '', dependencies: specification?.dependencies ?? [],
  })
  specEditorOpen.value = true
}
async function saveSpecification(): Promise<void> {
  if (!selectedIdea.value?.id) return
  const ideaId = selectedIdea.value.id
  const result = await task.run(() => ideasApi.specification(selectedIdea.value!.id!, { ...specForm }), String(i18n.global.t('ideas.specificationSaved')))
  if (result) {
    specEditorOpen.value = false
    detailsOpen.value = false
    await selectIdea({ id: ideaId } as Idea)
    await load(page.value.page)
    await nextTick()
    focusListFallback(ideaId)
  }
}
async function submitSpecification(): Promise<void> {
  if (!selectedIdea.value?.id || !confirm(String(i18n.global.t('ideas.submitSpecificationConfirm')))) return
  const ideaId = selectedIdea.value.id
  const result = await task.run(() => ideasApi.submitSpecification(selectedIdea.value!.id!), String(i18n.global.t('ideas.specificationSubmitted')))
  if (result) {
    detailsOpen.value = false
    await selectIdea({ id: ideaId } as Idea)
    await load(page.value.page)
    await nextTick()
    focusListFallback(ideaId)
  }
}
function openReview(event?: MouseEvent): void {
  if (!selectedIdea.value) return
  if (event) {
    reviewReturnFocus.value = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
    reviewFallbackFocus.value = null
  }
  isSpecReview.value = selectedIdea.value?.status === 'SPECIFICATION_SUBMITTED'
  reviewForm.decision = isSpecReview.value ? 'ACCEPT_FOR_APPLICATIONS' : 'APPROVE_FOR_SPECIFICATION'
  reviewForm.feedback = ''
  reviewForm.recommendation = ''
  for (const { key } of ratings.value) reviewForm[key] = ''
  feedbackError.value = ''
  reviewOpen.value = true
}
async function submitReview(): Promise<void> {
  if (!selectedIdea.value?.id) return
  const feedback = String(reviewForm.feedback).trim()
  if (!feedback) {
    feedbackError.value = String(i18n.global.t('ideas.feedbackError'))
    return
  }
  const result = await task.run(() => {
    if (isSpecReview.value) {
      const body: ReviewRequest = {
        decision: String(reviewForm.decision),
        feedback,
        recommendation: String(reviewForm.recommendation),
      }
      for (const { key } of ratings.value) {
        const rating = Number(reviewForm[key])
        if (Number.isInteger(rating) && rating >= 1 && rating <= 5) body[key] = rating
      }
      return ideasApi.review(selectedIdea.value!.id!, body, true)
    }
    const body: ReviewRequest = {
      decision: String(reviewForm.decision),
      feedback,
      recommendation: String(reviewForm.recommendation),
    }
    for (const { key } of ratings.value) {
      const rating = Number(reviewForm[key])
      if (Number.isInteger(rating) && rating >= 1 && rating <= 5) body[key] = rating
    }
    return ideasApi.review(selectedIdea.value!.id!, body, false)
  }, String(i18n.global.t('ideas.reviewDecisionRecorded')))
  if (result) {
    const idea = selectedIdea.value!
    await selectIdea(idea)
    if (isSpecReview.value) {
      await load(page.value.page)
      reviewOpen.value = false
      if (!detailsOpen.value) selectedIdea.value = null
      return
    }

    const updatedStatus = selectedIdea.value?.status
    if (statusFilter.value && updatedStatus && statusFilter.value !== updatedStatus) {
      // Follow the idea to its new status so it stays in the list and keeps its focus target.
      skipFilterReload = true
      statusFilter.value = updatedStatus
    }
    await reloadReviewedIdea(selectedIdea.value ?? idea)
    reviewOpen.value = false
    detailsOpen.value = false
    selectedIdea.value = null
    await nextTick()
    document.getElementById(`details-button-${idea.id}`)?.focus()
  }
}
async function savePreference(): Promise<void> {
  if (!selectedIdea.value?.id) return
  const idea = selectedIdea.value
  const result = await task.run(() => ideasApi.supervision(idea.id!, preference.value, preferenceNote.value), String(i18n.global.t('ideas.preferenceSaved')))
  if (result) {
    await selectIdea(idea)
    await load(page.value.page)
  }
}
async function assignSupervisor(): Promise<void> {
  if (!selectedIdea.value?.id) return
  const refusal = selectedTeacherRefusal.value !== null
  if (refusal && !refusalAcknowledged.value) return
  const idea = selectedIdea.value
  const result = await task.run(
    () => ideasApi.assignSupervisor(idea.id!, teacherId.value, refusal && refusalAcknowledged.value),
    String(i18n.global.t('ideas.supervisorConfirmed')),
  )
  if (!result) return
  teacherId.value = ''
  refusalAcknowledged.value = false
  await selectIdea(idea)
  await load(page.value.page)
  task.success.value = String(i18n.global.t('ideas.supervisorConfirmed'))
  await nextTick()
  document.getElementById('teacher-id')?.focus()
}
async function cancelSupervisorSelection(): Promise<void> {
  teacherId.value = ''
  refusalAcknowledged.value = false
  await nextTick()
  document.getElementById('teacher-id')?.focus()
}
let skipFilterReload = false
watch(statusFilter, () => {
  if (skipFilterReload) {
    skipFilterReload = false
    return
  }
  void load(0)
})
watch(studentQuery, () => { void searchStudents() })
watch(() => Boolean(detailsOpen.value && isStudent.value && selectedIdea.value?.id && !isCreator(selectedIdea.value) && !ownerConfirmation.value), (active) => {
  stopOwnerAccessPoll()
  if (!active || !selectedIdea.value?.id) return
  const ideaId = selectedIdea.value.id
  ownerAccessPoll = window.setInterval(() => {
    if (ownerAccessCheck) return
    ownerAccessCheck = (async () => {
      try {
        await ideasApi.get(ideaId)
      } catch (error) {
        if (!(error instanceof ApiError) || ![403, 404].includes(error.status)) return
        closeDetails()
        await load(page.value.page)
        await nextTick()
        document.getElementById('new-idea-button')?.focus()
      }
    })().finally(() => { ownerAccessCheck = null })
  }, 2000)
})
onUnmounted(stopOwnerAccessPoll)

onMounted(async () => {
  const [years] = await Promise.all([
    task.run(() => periodsApi.years()),
    load(),
  ])
  if (years) schoolYears.value = years.content
})
</script>

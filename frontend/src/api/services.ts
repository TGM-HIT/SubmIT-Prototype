import type { components, operations } from './schema'
import { ApiError, pageQuery, request } from './client'
import { i18n } from '../i18n'

type IdeaRequest = components['schemas']['IdeaRequest']
type IdeaResponse = components['schemas']['IdeaResponse']
type ApplicationPeriodRequest = components['schemas']['ApplicationPeriodRequest']
type ApplicationPeriodResponse = components['schemas']['ApplicationPeriodResponse']
type StudentApplicationRequest = components['schemas']['StudentApplicationRequest']
type StudentApplicationResponse = components['schemas']['StudentApplicationResponse']
type FormalApplicationRequest = components['schemas']['FormalApplicationRequest']
type FormalApplicationResponse = components['schemas']['FormalApplicationResponse']
type FormalApplicationRosterResponse = components['schemas']['FormalApplicationRosterResponse']
type ReviewRequest = components['schemas']['ReviewRequest']
type ReviewResponse = components['schemas']['ReviewResponse']
type SpecificationRequest = components['schemas']['SpecificationRequest']
type SpecificationResponse = components['schemas']['SpecificationResponse']
type SupervisionPreferenceRequest = components['schemas']['SupervisionPreferenceRequest']
type SupervisionPreferenceResponse = components['schemas']['SupervisionPreferenceResponse']
type SupervisorRequest = components['schemas']['SupervisorRequest']
type SupervisorResponse = components['schemas']['SupervisorResponse']
type CatalogueProjectResponse = components['schemas']['CatalogueProjectResponse']
type AllocationRunResponse = components['schemas']['AllocationRunResponse']
type AllocationPriorityRequest = components['schemas']['AllocationPriorityRequest']
type AllocationPriorityResponse = components['schemas']['AllocationPriorityResponse']
type FormalReviewResponse = components['schemas']['FormalReviewResponse']
type RubricResponse = components['schemas']['RubricResponse']
type PublishRubricRequest = components['schemas']['PublishRubricRequest']
type StudentGradeRequest = components['schemas']['StudentGradeRequest']
type GroupGradeRequest = components['schemas']['GroupGradeRequest']
type GradeResponse = components['schemas']['GradeResponse']
type AssessmentRubricSelection = components['schemas']['AssessmentRubricSelection']
type AssessmentRubricResponse = components['schemas']['AssessmentRubricResponse']
type GradingMember = components['schemas']['GradingMember']
type MetricRequest = components['schemas']['MetricRequest']
type MetricResponse = components['schemas']['MetricResponse']
type FinalGradeHistory = components['schemas']['FinalGradeHistory']
type FinalGradesRequest = components['schemas']['FinalGradesRequest']
type FinalGradesResponse = components['schemas']['FinalGradesResponse']
type CompletionResponse = components['schemas']['CompletionResponse']
type ArtifactResponse = components['schemas']['ArtifactResponse']
type UserResponse = components['schemas']['UserResponse']
type DisplayNameRequest = components['schemas']['DisplayNameRequest']
type LanguageRequest = components['schemas']['LanguageRequest']
type CatalogueEntryRequest = components['schemas']['CatalogueEntryRequest']
type CatalogueEntryResponse = components['schemas']['CatalogueEntryResponse']
type StudentDetailsRequest = components['schemas']['StudentDetailsRequest']
type StudentDetailsResponse = components['schemas']['StudentDetailsResponse']
type Page<T extends { content?: unknown[] }> = Required<Omit<T, 'content'>> & {
  content: NonNullable<T['content']>
}
type IdeaListFilters = Pick<NonNullable<operations['list_1']['parameters']['query']>, 'schoolYear' | 'ownerId'>

function json<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  return request<T>(path, { method, ...(body === undefined ? {} : { body }) })
}

function reviewIdea(
  id: string,
  input: ReviewRequest,
  specification?: false,
): Promise<ReviewResponse>
function reviewIdea(
  id: string,
  input: ReviewRequest,
  specification: true,
): Promise<ReviewResponse>
function reviewIdea(
  id: string,
  input: ReviewRequest,
  specification = false,
): Promise<ReviewResponse> {
  const path = specification ? 'specification/reviews' : 'reviews'
  return json<ReviewResponse>(`/api/ideas/${encodeURIComponent(id)}/${path}`, 'POST', input)
}

export const applyingGroupsApi = {
  list(periodId: string, page = 0): Promise<Page<components['schemas']['PageResponseGroupResponse']>> {
    return json(`/api/application-periods/${encodeURIComponent(periodId)}/applying-groups?${pageQuery(page)}`)
  },
  create(periodId: string): Promise<components['schemas']['GroupResponse']> {
    return json(`/api/application-periods/${encodeURIComponent(periodId)}/applying-groups`, 'POST')
  },
  change(periodId: string, id: string, action: NonNullable<operations['changeApplyingGroup']['parameters']['path']>['action'],
    input: components['schemas']['ChangeRequest']): Promise<components['schemas']['GroupResponse']> {
    return json(`/api/application-periods/${encodeURIComponent(periodId)}/applying-groups/${encodeURIComponent(id)}/actions/${action}`,
      'POST', input)
  },
  invitations(periodId: string, id: string, page = 0): Promise<Page<components['schemas']['PageResponseMemberResponse']>> {
    return json(`/api/application-periods/${encodeURIComponent(periodId)}/applying-groups/${encodeURIComponent(id)}/invitations?${pageQuery(page)}`)
  },
}

export const ideasApi = {
  async createPdf(metadata: { title: string; schoolYear: string; collaboratorIds: string[] }, file: File): Promise<IdeaResponse> {
    const body = new FormData()
    body.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }))
    body.append('file', file, file.name)
    return request<IdeaResponse>('/api/ideas/pdf', { method: 'POST', body })
  },
  async updatePdf(id: string, metadata: { title: string; schoolYear: string }, file: File): Promise<IdeaResponse> {
    const body = new FormData()
    body.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }))
    body.append('file', file, file.name)
    return request<IdeaResponse>(`/api/ideas/${encodeURIComponent(id)}/pdf`, { method: 'PUT', body })
  },
  async saveSpecificationPdf(id: string, file: File): Promise<SpecificationResponse> {
    const body = new FormData()
    body.append('file', file, file.name)
    return request<SpecificationResponse>(`/api/ideas/${encodeURIComponent(id)}/specification/pdf`, { method: 'PUT', body })
  },
  pdf(id: string): Promise<Blob> {
    return request<Blob>(`/api/ideas/${encodeURIComponent(id)}/pdf`)
  },
  specificationPdf(id: string): Promise<Blob> {
    return request<Blob>(`/api/ideas/${encodeURIComponent(id)}/specification/pdf`)
  },
  list(
    page: number,
    status?: string,
    size = 20,
    filters?: IdeaListFilters,
  ): Promise<Page<components['schemas']['PageResponseIdeaResponse']>> {
    const query = new URLSearchParams(pageQuery(page, size))
    if (status) query.set('status', status)
    if (filters?.schoolYear !== undefined) query.set('schoolYear', filters.schoolYear)
    if (filters?.ownerId !== undefined) query.set('ownerId', filters.ownerId)
    return json(`/api/ideas?${query}`)
  },
  lookupStudents(query: string, page = 0): Promise<Page<components['schemas']['PageResponseStudentLookupResponse']>> {
    const params = new URLSearchParams(pageQuery(page, 20))
    if (query.trim()) params.set('q', query.trim())
    return json(`/api/users/students?${params}`)
  },
  addOwner(id: string, studentId: string): Promise<IdeaResponse> {
    return json(`/api/ideas/${encodeURIComponent(id)}/owners`, 'POST', { studentId })
  },
  removeOwner(id: string, studentId: string): Promise<IdeaResponse> {
    return json(`/api/ideas/${encodeURIComponent(id)}/owners/${encodeURIComponent(studentId)}`, 'DELETE')
  },
  leave(id: string): Promise<void> {
    return json(`/api/ideas/${encodeURIComponent(id)}/owners/me`, 'DELETE')
  },
  get(id: string): Promise<IdeaResponse> {
    return json(`/api/ideas/${encodeURIComponent(id)}`)
  },
  create(input: IdeaRequest): Promise<IdeaResponse> {
    return json('/api/ideas', 'POST', input)
  },
  update(id: string, input: IdeaRequest): Promise<IdeaResponse> {
    return json(`/api/ideas/${encodeURIComponent(id)}`, 'PUT', input)
  },
  submit(id: string): Promise<IdeaResponse> {
    return json(`/api/ideas/${encodeURIComponent(id)}/submit`, 'POST')
  },
  withdraw(id: string): Promise<IdeaResponse> {
    return json(`/api/ideas/${encodeURIComponent(id)}/withdraw`, 'POST')
  },
  specification(id: string, input: SpecificationRequest): Promise<SpecificationResponse> {
    return json<SpecificationResponse>(`/api/ideas/${encodeURIComponent(id)}/specification`, 'PUT', input)
  },
  submitSpecification(id: string): Promise<SpecificationResponse> {
    return json<SpecificationResponse>(`/api/ideas/${encodeURIComponent(id)}/specification/submit`, 'POST')
  },
  review: reviewIdea,
  supervision(
    id: string,
    preference: SupervisionPreferenceRequest['preference'],
    note: string,
  ): Promise<SupervisionPreferenceResponse> {
    const body: SupervisionPreferenceRequest = { preference, note }
    return json<SupervisionPreferenceResponse>(
      `/api/ideas/${encodeURIComponent(id)}/supervision-preference`, 'PUT', body)
  },
  assignSupervisor(id: string, teacherId: string, acknowledgeRefusal = false): Promise<SupervisorResponse> {
    const body: SupervisorRequest = { teacherId, acknowledgeRefusal }
    return json<SupervisorResponse>(`/api/ideas/${encodeURIComponent(id)}/supervisor`, 'PUT', body)
  },
}

export const periodsApi = {
  cataloguePdf(periodId: string, projectId: string, specification = false): Promise<Blob> {
    const suffix = specification ? '/specification/pdf' : '/pdf'
    return request<Blob>(`/api/application-periods/${encodeURIComponent(periodId)}/projects/${encodeURIComponent(projectId)}${suffix}`)
  },
  list(page: number): Promise<Page<components['schemas']['PageResponseApplicationPeriodResponse']>> {
    return json(`/api/application-periods?${pageQuery(page)}`)
  },
  create(input: ApplicationPeriodRequest): Promise<ApplicationPeriodResponse> {
    return json('/api/application-periods', 'POST', input)
  },
  update(id: string, input: ApplicationPeriodRequest): Promise<ApplicationPeriodResponse> {
    return json(`/api/application-periods/${encodeURIComponent(id)}`, 'PUT', input)
  },
  years(page = 0): Promise<Page<components['schemas']['PageResponseString']>> {
    return json(`/api/school-years?${pageQuery(page)}`)
  },
  myApplication(periodId: string): Promise<StudentApplicationResponse> {
    return json(`/api/application-periods/${encodeURIComponent(periodId)}/applications/me`)
  },
  projectDetails(periodId: string, projectId: string): Promise<CatalogueProjectResponse> {
    return json<CatalogueProjectResponse>(
      `/api/application-periods/${encodeURIComponent(periodId)}/catalogue/${encodeURIComponent(projectId)}`)
  },
  applications(periodId: string, page: number): Promise<Page<components['schemas']['PageResponseStudentApplicationResponse']>> {
    return json(`/api/application-periods/${encodeURIComponent(periodId)}/applications?${pageQuery(page)}`)
  },
  createApplication(periodId: string, input: StudentApplicationRequest): Promise<StudentApplicationResponse> {
    return json(`/api/application-periods/${encodeURIComponent(periodId)}/applications`, 'POST', input)
  },
  updateApplication(id: string, input: StudentApplicationRequest): Promise<StudentApplicationResponse> {
    return json(`/api/applications/${encodeURIComponent(id)}`, 'PUT', input)
  },
  withdrawApplication(id: string): Promise<void> {
    return json(`/api/applications/${encodeURIComponent(id)}`, 'DELETE')
  },
  pins(periodId: string, page: number): Promise<Page<components["schemas"]["PageResponseAllocationPinResponse"]>> {
    return json(`/api/application-periods/${encodeURIComponent(periodId)}/allocation-pins?${pageQuery(page)}`)
  },
  savePin(periodId: string, studentId: string, projectId: string, acknowledgeUnranked = false): Promise<components["schemas"]["AllocationPinResponse"]> {
    return json(`/api/application-periods/${encodeURIComponent(periodId)}/allocation-pins/${encodeURIComponent(studentId)}`, "PUT", { projectId, acknowledgeUnranked })
  },
  removePin(periodId: string, studentId: string): Promise<void> {
    return json(`/api/application-periods/${encodeURIComponent(periodId)}/allocation-pins/${encodeURIComponent(studentId)}`, "DELETE")
  },
  priorityProjects(periodId: string): Promise<AllocationPriorityResponse> {
    return json(`/api/application-periods/${encodeURIComponent(periodId)}/priority-projects`)
  },
  savePriorityProjects(periodId: string, input: AllocationPriorityRequest): Promise<AllocationPriorityResponse> {
    return json(`/api/application-periods/${encodeURIComponent(periodId)}/priority-projects`, 'PUT', input)
  },
  runs(periodId: string, page: number): Promise<Page<components['schemas']['AllocationRunPageResponse']>> {
    return json(`/api/application-periods/${encodeURIComponent(periodId)}/allocation-runs?${pageQuery(page)}`)
  },
  startRun(periodId: string): Promise<AllocationRunResponse> {
    return json<AllocationRunResponse>(`/api/application-periods/${encodeURIComponent(periodId)}/allocation-runs`, 'POST')
  },
}

export const allocationApi = {
  getRun(id: string): Promise<AllocationRunResponse> {
    return json<AllocationRunResponse>(`/api/allocation-runs/${encodeURIComponent(id)}`)
  },
  finalize(id: string): Promise<AllocationRunResponse> {
    return json<AllocationRunResponse>(`/api/allocation-runs/${encodeURIComponent(id)}/finalize`, 'POST')
  },
}

export const formalApi = {
  list(
    query = '',
    supervisedOnly = true,
    page = 0,
    size = 20,
  ): Promise<Page<components['schemas']['PageResponseFormalApplicationSummaryResponse']>> {
    const params = new URLSearchParams(pageQuery(page, size))
    if (query.trim()) params.set('q', query.trim())
    params.set('supervisedOnly', String(supervisedOnly))
    return json(`/api/formal-applications?${params}`)
  },
  eligibleProjects(page = 0, size = 100): Promise<Page<components['schemas']['PageResponseEligibleFormalProjectResponse']>> {
    return json(`/api/formal-applications/eligible-projects?${pageQuery(page, size)}`)
  },
  roster(projectId: string): Promise<FormalApplicationRosterResponse> {
    return json(`/api/projects/${encodeURIComponent(projectId)}/formal-application-roster`)
  },
  get(projectId: string): Promise<FormalApplicationResponse> {
    return json(`/api/projects/${encodeURIComponent(projectId)}/formal-applications`)
  },
  create(projectId: string, input: FormalApplicationRequest): Promise<FormalApplicationResponse> {
    return json(`/api/projects/${encodeURIComponent(projectId)}/formal-applications`, 'POST', input)
  },
  update(id: string, input: FormalApplicationRequest): Promise<FormalApplicationResponse> {
    return json(`/api/formal-applications/${encodeURIComponent(id)}`, 'PUT', input)
  },
  submit(id: string): Promise<FormalApplicationResponse> {
    return json(`/api/formal-applications/${encodeURIComponent(id)}/submit`, 'POST')
  },
  review(id: string, input: ReviewRequest): Promise<FormalReviewResponse> {
    return json<FormalReviewResponse>(`/api/formal-applications/${encodeURIComponent(id)}/reviews`, 'POST', input)
  },
}

export const gradingApi = {
  rubrics(page = 0): Promise<Page<components['schemas']['PageResponseRubricSummary']>> {
    return json(`/api/grading-rubrics?${pageQuery(page)}`)
  },
  rubric(id: string): Promise<RubricResponse> {
    return json<RubricResponse>(`/api/grading-rubrics/${encodeURIComponent(id)}`)
  },
  activeRubric(): Promise<RubricResponse> {
    return json<RubricResponse>('/api/grading-rubrics/active')
  },
  publishRubric(input: PublishRubricRequest): Promise<RubricResponse> {
    return json<RubricResponse>('/api/grading-rubrics', 'POST', input)
  },
  assessmentRubrics(projectId: string): Promise<AssessmentRubricResponse> {
    return json(`/api/projects/${encodeURIComponent(projectId)}/assessment-rubrics`)
  },
  setAssessmentRubrics(projectId: string, input: AssessmentRubricSelection): Promise<AssessmentRubricResponse> {
    return json(`/api/projects/${encodeURIComponent(projectId)}/assessment-rubrics`, 'PUT', input)
  },
  gradingMembers(projectId: string): Promise<GradingMember[]> {
    return json(`/api/projects/${encodeURIComponent(projectId)}/grading-members`)
  },
  grades(projectId: string, page = 0, size = 20, filters?: {
    studentId?: string
    assessmentType?: 'ROUTINE' | 'FINAL'
    rubricId?: string
  }): Promise<Page<components['schemas']['PageResponseGradeResponse']>> {
    const query = new URLSearchParams(pageQuery(page, size))
    if (filters?.studentId) query.set('studentId', filters.studentId)
    if (filters?.assessmentType) query.set('assessmentType', filters.assessmentType)
    if (filters?.rubricId) query.set('rubricId', filters.rubricId)
    return json(`/api/projects/${encodeURIComponent(projectId)}/student-grades?${query}`)
  },
  recordGrade(projectId: string, input: StudentGradeRequest): Promise<GradeResponse> {
    return json<GradeResponse>(`/api/projects/${encodeURIComponent(projectId)}/student-grades`, 'POST', input)
  },
  reviseGrade(projectId: string, gradeId: string, input: components['schemas']['GradeRevisionRequest']): Promise<GradeResponse> {
    return json<GradeResponse>(`/api/projects/${encodeURIComponent(projectId)}/student-grades/${encodeURIComponent(gradeId)}/revisions`,
      'POST', input)
  },
  groupGrades(projectId: string, page = 0): Promise<Page<components['schemas']['PageResponseGradeResponse']>> {
    return json(`/api/projects/${encodeURIComponent(projectId)}/group-grades?${pageQuery(page)}`)
  },
  recordGroupGrade(projectId: string, input: GroupGradeRequest): Promise<GradeResponse> {
    return json<GradeResponse>(`/api/projects/${encodeURIComponent(projectId)}/group-grades`, 'POST', input)
  },
  metrics(projectId: string, page = 0): Promise<Page<components['schemas']['PageResponseMetricResponse']>> {
    return json(`/api/projects/${encodeURIComponent(projectId)}/metrics?${pageQuery(page)}`)
  },
  recordMetric(projectId: string, input: MetricRequest): Promise<MetricResponse> {
    return json<MetricResponse>(`/api/projects/${encodeURIComponent(projectId)}/metrics`, 'POST', input)
  },
  finalGrade(projectId: string): Promise<FinalGradeHistory> {
    return json<FinalGradeHistory>(`/api/projects/${encodeURIComponent(projectId)}/final-grade`)
  },
  finalStudentGrade(projectId: string, studentId: string): Promise<FinalGradeHistory> {
    return json<FinalGradeHistory>(
      `/api/projects/${encodeURIComponent(projectId)}/students/${encodeURIComponent(studentId)}/final-grade`)
  },
  setFinalGrade(projectId: string, input: FinalGradesRequest): Promise<FinalGradesResponse> {
    return json<FinalGradesResponse>(`/api/projects/${encodeURIComponent(projectId)}/final-grade`, 'PUT', input)
  },
}

export const archiveApi = {
  publicList(projectId: string, page = 0): Promise<Page<components['schemas']['PageResponsePublicArtifactResponse']>> {
    return request(`/api/public/projects/${encodeURIComponent(projectId)}/archive?${pageQuery(page)}`, { public: true })
  },
  publicContent(projectId: string, artifactId: string): Promise<Blob> {
    return request(`/api/public/projects/${encodeURIComponent(projectId)}/archive/${encodeURIComponent(artifactId)}`,
      { public: true })
  },
  list(projectId: string, page = 0): Promise<Page<components['schemas']['PageResponseArtifactResponse']>> {
    return json(`/api/projects/${encodeURIComponent(projectId)}/artifacts?${pageQuery(page)}`)
  },
  upload(projectId: string, form: FormData): Promise<ArtifactResponse> {
    return request(`/api/projects/${encodeURIComponent(projectId)}/artifacts`, { method: 'POST', body: form })
  },
  content(id: string): Promise<Blob> {
    return request(`/api/artifacts/${encodeURIComponent(id)}/content`)
  },
  remove(id: string): Promise<void> {
    return json(`/api/artifacts/${encodeURIComponent(id)}`, 'DELETE')
  },
  publicationReview(id: string, decision: string, note: string): Promise<ArtifactResponse> {
    return json(`/api/artifacts/${encodeURIComponent(id)}/publication-review`, 'POST', { decision, note })
  },
  complete(projectId: string): Promise<CompletionResponse> {
    return json<CompletionResponse>(`/api/projects/${encodeURIComponent(projectId)}/complete`, 'POST')
  },
}

export const userApi = {
  changeName(id: string, displayName: string): Promise<UserResponse> {
    const body: DisplayNameRequest = { displayName }
    return json(`/api/users/${encodeURIComponent(id)}`, 'PATCH', body)
  },
  changeLanguage(id: string, language: NonNullable<UserResponse['language']>): Promise<UserResponse> {
    const body: LanguageRequest = { language }
    return json(`/api/users/${encodeURIComponent(id)}/language`, 'PATCH', body)
  },
}

export const adminApi = {
  users(page = 0, role?: UserResponse['roles'][number], query?: string, size = 20): Promise<Page<components['schemas']['PageResponseUserResponse']>> {
    const params = new URLSearchParams(pageQuery(page))
    if (role) params.set('role', role)
    if (query?.trim()) params.set('q', query.trim())
    params.set('size', String(size))
    return json(`/api/users?${params}`)
  },
  role(id: string, roles: UserResponse['roles']): Promise<UserResponse> {
    return json(`/api/users/${encodeURIComponent(id)}/role`, 'PATCH', { roles })
  },
  async audit(page = 0): Promise<Page<components['schemas']['PageResponseAuditEntryResponse']>> {
    const result = await json<components['schemas']['PageResponseAuditEntryResponse']>(
      `/api/audit?${pageQuery(page)}`)
    const validSources = ['EVENT_TIME', 'CURRENT', 'UNAVAILABLE']
    const validActorSources = [...validSources, 'SYSTEM']
    const valid = result && Array.isArray(result.content)
      && Number.isInteger(result.page) && result.page >= 0
      && Number.isInteger(result.size) && result.size > 0
      && Number.isInteger(result.totalElements) && result.totalElements >= 0
      && Number.isInteger(result.totalPages) && result.totalPages >= 0
      && result.content.every((entry) => entry !== null
        && typeof entry.actorUsername === 'string'
        && entry.actorUsername.length > 0
        && typeof entry.action === 'string' && entry.action.length > 0
        && typeof entry.entityType === 'string' && entry.entityType.length > 0
        && typeof entry.entityId === 'string' && entry.entityId.length > 0
        && typeof entry.occurredAt === 'string' && !Number.isNaN(Date.parse(entry.occurredAt))
        && validActorSources.includes(entry.actorDetailsSource)
        && validSources.includes(entry.resourceLabelSource)
        && (entry.actorId === undefined || typeof entry.actorId === 'string')
        && (entry.actorDisplayName === undefined
          || (typeof entry.actorDisplayName === 'string' && entry.actorDisplayName.length > 0))
        && (entry.actorRole === undefined || ['STUDENT', 'TEACHER', 'ADMIN'].includes(entry.actorRole))
        && (entry.resourceLabel === undefined
          || (typeof entry.resourceLabel === 'string' && entry.resourceLabel.length > 0))
        && ((entry.actorDetailsSource === 'EVENT_TIME' || entry.actorDetailsSource === 'CURRENT')
          ? (entry.actorDisplayName !== undefined && entry.actorRole !== undefined)
          : (entry.actorDisplayName === undefined && entry.actorRole === undefined))
        && (entry.actorDetailsSource !== 'SYSTEM'
          || (entry.actorUsername === 'SYSTEM' && entry.actorId === undefined))
        && (entry.actorDetailsSource === 'SYSTEM' || entry.actorUsername !== 'SYSTEM')
        && ((entry.resourceLabelSource === 'EVENT_TIME' || entry.resourceLabelSource === 'CURRENT')
          ? entry.resourceLabel !== undefined : entry.resourceLabel === undefined))
    if (!valid) {
      throw new ApiError(String(i18n.global.t('admin.auditMalformed')), 200, undefined, 'backend')
    }
    return result as Page<components['schemas']['PageResponseAuditEntryResponse']>
  },
}

export const studentDetailsApi = {
  classes(page = 0): Promise<Page<components['schemas']['PageResponseCatalogueEntryResponse']>> {
    return json(`/api/student-classes?${pageQuery(page, 100)}`)
  },
  subjects(page = 0): Promise<Page<components['schemas']['PageResponseCatalogueEntryResponse']>> {
    return json(`/api/elective-subjects?${pageQuery(page, 100)}`)
  },
  createClass(input: CatalogueEntryRequest): Promise<CatalogueEntryResponse> {
    return json('/api/student-classes', 'POST', input)
  },
  renameClass(id: string, input: CatalogueEntryRequest): Promise<CatalogueEntryResponse> {
    return json(`/api/student-classes/${encodeURIComponent(id)}`, 'PATCH', input)
  },
  createSubject(input: CatalogueEntryRequest): Promise<CatalogueEntryResponse> {
    return json('/api/elective-subjects', 'POST', input)
  },
  renameSubject(id: string, input: CatalogueEntryRequest): Promise<CatalogueEntryResponse> {
    return json(`/api/elective-subjects/${encodeURIComponent(id)}`, 'PATCH', input)
  },
  get(studentId: string): Promise<StudentDetailsResponse> {
    return json(`/api/users/${encodeURIComponent(studentId)}/student-details`)
  },
  assign(studentId: string, input: StudentDetailsRequest): Promise<StudentDetailsResponse> {
    return json(`/api/users/${encodeURIComponent(studentId)}/student-details`, 'PUT', input)
  },
}

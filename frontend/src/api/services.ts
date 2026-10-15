import type { components } from './schema'
import { pageQuery, request } from './client'

type IdeaRequest = components['schemas']['IdeaRequest']
type IdeaResponse = components['schemas']['IdeaResponse']
type ApplicationPeriodRequest = components['schemas']['ApplicationPeriodRequest']
type ApplicationPeriodResponse = components['schemas']['ApplicationPeriodResponse']
type StudentApplicationRequest = components['schemas']['StudentApplicationRequest']
type StudentApplicationResponse = components['schemas']['StudentApplicationResponse']
type FormalApplicationRequest = components['schemas']['FormalApplicationRequest']
type FormalApplicationResponse = components['schemas']['FormalApplicationResponse']
type ReviewRequest = components['schemas']['ReviewRequest']
type ArtifactResponse = components['schemas']['ArtifactResponse']
type PublicArtifactResponse = components['schemas']['PublicArtifactResponse']
type UserResponse = components['schemas']['UserResponse']
type DisplayNameRequest = components['schemas']['DisplayNameRequest']
type CatalogueEntryRequest = components['schemas']['CatalogueEntryRequest']
type CatalogueEntryResponse = components['schemas']['CatalogueEntryResponse']
type StudentDetailsRequest = components['schemas']['StudentDetailsRequest']
type StudentDetailsResponse = components['schemas']['StudentDetailsResponse']
type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number }
type Data = Record<string, unknown>

function json<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  return request<T>(path, { method, ...(body === undefined ? {} : { body }) })
}

export const ideasApi = {
  list(page: number, status?: string, size = 20): Promise<Page<IdeaResponse>> {
    const query = new URLSearchParams(pageQuery(page, size))
    if (status) query.set('status', status)
    return json(`/api/ideas?${query}`)
  },
  lookupStudents(query: string, page = 0): Promise<Page<components['schemas']['StudentLookupResponse']>> {
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
  specification(id: string, input: Data): Promise<unknown> {
    return json(`/api/ideas/${encodeURIComponent(id)}/specification`, 'PUT', input)
  },
  submitSpecification(id: string): Promise<unknown> {
    return json(`/api/ideas/${encodeURIComponent(id)}/specification/submit`, 'POST')
  },
  review(id: string, input: ReviewRequest, specification = false): Promise<unknown> {
    const path = specification ? 'specification/reviews' : 'reviews'
    return json(`/api/ideas/${encodeURIComponent(id)}/${path}`, 'POST', input)
  },
  supervision(id: string, preference: string, note: string): Promise<unknown> {
    return json(`/api/ideas/${encodeURIComponent(id)}/supervision-preference`, 'PUT', { preference, note })
  },
  assignSupervisor(id: string, teacherId: string, acknowledgeRefusal = false): Promise<unknown> {
    return json(`/api/ideas/${encodeURIComponent(id)}/supervisor`, 'PUT', { teacherId, acknowledgeRefusal })
  },
}

export const periodsApi = {
  list(page: number): Promise<Page<ApplicationPeriodResponse>> {
    return json(`/api/application-periods?${pageQuery(page)}`)
  },
  create(input: ApplicationPeriodRequest): Promise<ApplicationPeriodResponse> {
    return json('/api/application-periods', 'POST', input)
  },
  update(id: string, input: ApplicationPeriodRequest): Promise<ApplicationPeriodResponse> {
    return json(`/api/application-periods/${encodeURIComponent(id)}`, 'PUT', input)
  },
  years(page = 0): Promise<Page<string>> {
    return json(`/api/school-years?${pageQuery(page)}`)
  },
  myApplication(periodId: string): Promise<StudentApplicationResponse> {
    return json(`/api/application-periods/${encodeURIComponent(periodId)}/applications/me`)
  },
  projectDetails(periodId: string, projectId: string): Promise<Data> {
    return json(`/api/application-periods/${encodeURIComponent(periodId)}/catalogue/${encodeURIComponent(projectId)}`)
  },
  applications(periodId: string, page: number): Promise<Page<StudentApplicationResponse>> {
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
  pins(periodId: string, page: number): Promise<Page<components["schemas"]["AllocationPinResponse"]>> {
    return json(`/api/application-periods/${encodeURIComponent(periodId)}/allocation-pins?${pageQuery(page)}`)
  },
  savePin(periodId: string, studentId: string, projectId: string, acknowledgeUnranked = false): Promise<components["schemas"]["AllocationPinResponse"]> {
    return json(`/api/application-periods/${encodeURIComponent(periodId)}/allocation-pins/${encodeURIComponent(studentId)}`, "PUT", { projectId, acknowledgeUnranked })
  },
  removePin(periodId: string, studentId: string): Promise<void> {
    return json(`/api/application-periods/${encodeURIComponent(periodId)}/allocation-pins/${encodeURIComponent(studentId)}`, "DELETE")
  },
  runs(periodId: string, page: number): Promise<Page<Data>> {
    return json(`/api/application-periods/${encodeURIComponent(periodId)}/allocation-runs?${pageQuery(page)}`)
  },
  startRun(periodId: string): Promise<Data> {
    return json(`/api/application-periods/${encodeURIComponent(periodId)}/allocation-runs`, 'POST')
  },
}

export const allocationApi = {
  getRun(id: string): Promise<Data> {
    return json(`/api/allocation-runs/${encodeURIComponent(id)}`)
  },
  finalize(id: string): Promise<Data> {
    return json(`/api/allocation-runs/${encodeURIComponent(id)}/finalize`, 'POST')
  },
}

export const formalApi = {
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
  review(id: string, input: ReviewRequest): Promise<unknown> {
    return json(`/api/formal-applications/${encodeURIComponent(id)}/reviews`, 'POST', input)
  },
}

export const gradingApi = {
  rubrics(page = 0): Promise<Page<Data>> {
    return json(`/api/grading-rubrics?${pageQuery(page)}`)
  },
  activeRubric(): Promise<Data> {
    return json('/api/grading-rubrics/active')
  },
  publishRubric(input: Data): Promise<Data> {
    return json('/api/grading-rubrics', 'POST', input)
  },
  grades(projectId: string, page = 0): Promise<Page<Data>> {
    return json(`/api/projects/${encodeURIComponent(projectId)}/student-grades?${pageQuery(page)}`)
  },
  recordGrade(projectId: string, input: Data): Promise<Data> {
    return json(`/api/projects/${encodeURIComponent(projectId)}/student-grades`, 'POST', input)
  },
  groupGrades(projectId: string, page = 0): Promise<Page<Data>> {
    return json(`/api/projects/${encodeURIComponent(projectId)}/group-grades?${pageQuery(page)}`)
  },
  recordGroupGrade(projectId: string, input: Data): Promise<Data> {
    return json(`/api/projects/${encodeURIComponent(projectId)}/group-grades`, 'POST', input)
  },
  metrics(projectId: string, page = 0): Promise<Page<Data>> {
    return json(`/api/projects/${encodeURIComponent(projectId)}/metrics?${pageQuery(page)}`)
  },
  recordMetric(projectId: string, input: Data): Promise<Data> {
    return json(`/api/projects/${encodeURIComponent(projectId)}/metrics`, 'POST', input)
  },
  finalGrade(projectId: string): Promise<Data> {
    return json(`/api/projects/${encodeURIComponent(projectId)}/final-grade`)
  },
  finalStudentGrade(projectId: string, studentId: string): Promise<Data> {
    return json(`/api/projects/${encodeURIComponent(projectId)}/students/${encodeURIComponent(studentId)}/final-grade`)
  },
  setFinalGrade(projectId: string, input: Data): Promise<Data> {
    return json(`/api/projects/${encodeURIComponent(projectId)}/final-grade`, 'PUT', input)
  },
}

export const archiveApi = {
  publicList(projectId: string, page = 0): Promise<Page<PublicArtifactResponse>> {
    return request(`/api/public/projects/${encodeURIComponent(projectId)}/archive?${pageQuery(page)}`, { public: true })
  },
  publicContent(projectId: string, artifactId: string): Promise<Blob> {
    return request(`/api/public/projects/${encodeURIComponent(projectId)}/archive/${encodeURIComponent(artifactId)}`,
      { public: true })
  },
  list(projectId: string, page = 0): Promise<Page<ArtifactResponse>> {
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
  complete(projectId: string): Promise<Data> {
    return json(`/api/projects/${encodeURIComponent(projectId)}/complete`, 'POST')
  },
}

export const userApi = {
  changeName(id: string, displayName: string): Promise<UserResponse> {
    const body: DisplayNameRequest = { displayName }
    return json(`/api/users/${encodeURIComponent(id)}`, 'PATCH', body)
  },
}

export const adminApi = {
  users(page = 0, role?: NonNullable<UserResponse['role']>, query?: string, size = 20): Promise<Page<UserResponse>> {
    const params = new URLSearchParams(pageQuery(page))
    if (role) params.set('role', role)
    if (query?.trim()) params.set('q', query.trim())
    params.set('size', String(size))
    return json(`/api/users?${params}`)
  },
  role(id: string, role: UserResponse['role']): Promise<UserResponse> {
    return json(`/api/users/${encodeURIComponent(id)}/role`, 'PATCH', { role })
  },
  audit(page = 0): Promise<Page<Data>> {
    return json(`/api/audit?${pageQuery(page)}`)
  },
}

export const studentDetailsApi = {
  classes(page = 0): Promise<Page<CatalogueEntryResponse>> {
    return json(`/api/student-classes?${pageQuery(page, 100)}`)
  },
  subjects(page = 0): Promise<Page<CatalogueEntryResponse>> {
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

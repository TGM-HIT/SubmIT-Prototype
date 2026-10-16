# Dev-16. Document accepted-catalogue and audit read endpoints

Draft from Compliance-02-20261016-0814: needs Ticket Author refinement before implementation

## Intent

Make the backend API specification identify the implemented student catalogue-detail and audit-list read endpoints, including their access restrictions, so callers can discover them from the documented API contract.

Source: Compliance-02-20261016-0814, report finding 1: `backend/SPEC.md:32` says “students may request the current accepted idea and specification for any project in that `OPEN` catalogue by period and project ID” without the route, while `ApplicationPeriodController.java:63` maps `GET /api/application-periods/{periodId}/catalogue/{projectId}`. `backend/SPEC.md:156` describes “Paginated read-only audit resources” for “authorized users” without routes or role names, while `AuditController.java:23,29` maps `GET /api/audit` and `GET /api/audit/{entityType}/{entityId}` and `AuditService.java:36-51` denies `STUDENT`.

## Scope and contract

- Update `backend/SPEC.md` to document the exact method and path for the student catalogue-detail endpoint, its authenticated `STUDENT` restriction, and its open-period/catalogue eligibility.
- Document `GET /api/audit` and `GET /api/audit/{entityType}/{entityId}` as paginated read-only endpoints available to authorized staff (`TEACHER` and `ADMIN`) and forbidden to `STUDENT`.
- Keep endpoint behavior, response schemas, and application permissions unchanged. Do not edit generated `contracts/openapi.yaml`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- No automated regression test applies; this ticket documents existing read endpoints and does not change behavior.

Standard project checks:

- Documentation-only change: no project checks required.

## Acceptance criteria (the Evaluator judges)

- `backend/SPEC.md` names the exact method and path for all three endpoints and states the implemented access restrictions and key availability conditions.
- The documented behavior agrees with `ApplicationPeriodController`, `ApplicationPeriodService`, `AuditController`, and `AuditService`.
- No application behavior, API schema, or unrelated documentation changes.
- No check is claimed that was not performed.

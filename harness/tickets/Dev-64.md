# Dev-64. Document exact catalogue rename routes

Draft from Compliance-03-20261101-1758: needs Ticket Author refinement before implementation

## Intent

The backend API specification describes creating and renaming student-class and elective-subject catalogue entries, but does not give the item URI templates for the two PATCH operations. API consumers therefore cannot determine the documented route despite the implementation exposing exact paths.

## Source (when applicable)

Source: `Compliance-03-20261101-1758`, report Finding 5; quoted evidence: “Administrators create entries with `POST` and rename entries with `PATCH` on these catalogue resources”; the clause omits the exact item routes present in the two controller mappings. Repository revision: `882a1217cd6981e381668a612d1a0978d8a123df`; `backend/SPEC.md:224-226`; `backend/src/main/java/at/ac/tgm/submit/user/StudentDetailsController.java:40-44,56-60`. The ignored generated OpenAPI has the same two route templates; it is referenced by path only because it is not tracked at this revision.

## Scope and contract

- Document the exact existing PATCH routes `/api/student-classes/{id}` and `/api/elective-subjects/{id}` in the student-class and elective-subject catalogue section of `backend/SPEC.md`.
- State their existing method, item-path, request-body and relevant validation behavior consistently with the controller and request DTOs; explicitly state that these rename operations have no query parameters.
- Preserve the existing collection GET routes, resource behavior, access rules, naming constraints, and generic POST documentation. Do not change application code, annotations, generated artifacts, or product policy.
- Documentation-only deliverable; no scope-gate allowance is required.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- No automated test applies to a documentation-only correction.

Standard project checks:

- Documentation-only changes: no checks required; verify the Markdown format and that the cited existing route templates match the controller.

## One-time manual verification (in addition to automated tests)

- Not applicable: this ticket changes only the written API route documentation and does not change runtime behavior.

## Acceptance criteria (the Evaluator judges)

- `backend/SPEC.md` explicitly documents `PATCH /api/student-classes/{id}` and `PATCH /api/elective-subjects/{id}` as the existing rename routes.
- The documented request bodies, validation constraints, access rules, and absence of query parameters agree with the current API sources; no endpoint behavior or generated file is changed.
- Standard documentation-only checks are complete and no unrelated content is changed.

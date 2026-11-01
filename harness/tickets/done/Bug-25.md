# Bug-25. Align allocation lifecycle specifications with synchronous execution

Draft from Compliance-02-20261021-2331, refined after owner decision to keep allocation synchronous

## Intent

The specifications describe `ALLOCATING` as an application-period status and include an “Applications closed / allocation running” lifecycle stage. The application-period implementation does not produce or expose that status: allocation runs execute synchronously in a transaction, after which the period is set to `ALLOCATED` on success or `CLOSED` on failure. The separate allocation-run record is created internally as `RUNNING`, but the request does not return until processing completes, so clients cannot observe that transient state through the API.

The owner confirmed that allocation remains synchronous and selected the documentation-alignment option. The period lifecycle specifications must therefore stop requiring an externally observable `ALLOCATING` state; this ticket does not change the execution model or application behavior.

## Scope and contract

- Specification-only change to [`docs/SPEC.md`](../../../docs/SPEC.md) and [`backend/SPEC.md`](../../../backend/SPEC.md). Review allocation lifecycle statements in [`frontend/SPEC.md`](../../../frontend/SPEC.md) and change it only if any statement implies that clients observe an in-progress period status.
- In `backend/SPEC.md`, remove `ALLOCATING` from the application-period status set. Document the period statuses as `DRAFT`, `OPEN`, `CLOSED`, `ALLOCATED`, and `FINALIZED`, with the existing lifecycle meanings preserved.
- In `docs/SPEC.md`, replace the “Applications closed / allocation running” lifecycle stage with a stage describing that applications close and the initial allocation is processed synchronously. Make clear that the externally visible period state transitions to `ALLOCATED` after a successful run or remains/becomes `CLOSED` after a failed run; the period does not expose an intermediate `ALLOCATING` state.
- Preserve the distinct allocation-run status lifecycle. The run may be stored internally as `RUNNING` while the synchronous transaction executes, then becomes `COMPLETED` or `FAILED`; this ticket does not remove or rename `RUNNING` for allocation-run records.
- Preserve the existing automatic initial-run behavior, early and replacement-run semantics, failure behavior, and finalization. Do not change backend/frontend application code, API responses, OpenAPI annotations, or generated API types.
- Coordinate specification edits with Bug-24, which also changes application-period lifecycle wording in the same specification files. Apply this clarification after or alongside Bug-24's edits, preserving its confirmed deadline/read boundaries and avoiding duplicated or contradictory lifecycle text.
- Scope-gate allowances: `docs/SPEC.md` and `backend/SPEC.md`. `frontend/SPEC.md` is also an allowed specification path if the review finds that a clarification is needed.

## Mechanical checks (the Evaluator re-runs)

- No automated tests apply: the confirmed behavior change is limited to specifications, and implementation/API behavior must remain unchanged. Check the specification edits against `AllocationEarlyRunApiTest` and `ApplyingGroupApiTest` behavior; do not change or weaken those tests.
- Documentation/specification-only work requires no project build, test, frontend typecheck, or OpenAPI generation. Do not edit generated artifacts.

## One-time manual verification (in addition to automated tests)

- None required. This ticket only reconciles specification wording with existing synchronous behavior; it makes no UI or runtime change.

## Acceptance criteria (the Evaluator judges)

- The application-period status list in `backend/SPEC.md` no longer includes `ALLOCATING`.
- The lifecycle wording in `docs/SPEC.md` describes synchronous initial allocation processing and the resulting visible period status without implying an externally observable in-progress period state.
- `frontend/SPEC.md` contains no contradictory requirement to display an `ALLOCATING` period state. If it needs no edit, leave it unchanged.
- The documentation continues to distinguish application-period status from allocation-run status: do not remove `RUNNING` as the allocation-run's internal transient status, and retain the documented completed and failed run outcomes.
- Automatic initial runs, early runs, replacement runs, allocation results, and finalization behavior remain otherwise unchanged. The existing `AllocationEarlyRunApiTest` and relevant `ApplyingGroupApiTest` scenarios remain the implementation baseline and are not weakened.
- Only the applicable specification files change; there is no application-code, test, API-contract, or generated-type change. No unrelated cleanup is included, generated artifacts are not hand-edited, and no check is claimed that was not performed.

## Optional: Specification changes

- Review all occurrences concerning application-period states, deadline-triggered initial runs, allocation-run states, and allocation completion in [`docs/SPEC.md`](../../../docs/SPEC.md), [`backend/SPEC.md`](../../../backend/SPEC.md), and [`frontend/SPEC.md`](../../../frontend/SPEC.md), not only the named lifecycle paragraph. Update the two contradictory requirements above and ensure the three specifications contain no conflicting lifecycle description.
- No OpenAPI regeneration is required because API annotations, request/response models, and validation are unchanged.

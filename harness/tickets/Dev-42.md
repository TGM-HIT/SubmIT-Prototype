# Dev-42. Align backend learning-topic validation wording with the shared idea rule

Draft from Compliance-03-20261022-0744: needs Ticket Author refinement before implementation

## Intent

The backend specification lists learning topics among the required idea-request fields. The shared product rule, which takes precedence, includes learning topics only "if known". The implementation and the generated contract follow the shared rule and treat them as optional. A reader of the backend specification therefore gets a requirement that contradicts both the shared rule and the API.

Source: Compliance-03-20261022-0744, report finding 2:

- `backend/SPEC.md:49` says "Idea requests validate the required title, description, intended outcome, initial scope points, learning topics, and school year."
- `docs/SPEC.md:45` says "The expected learning topics or skills involved, if known."
- `contracts/openapi.yaml:4866-4874` documents `IdeaRequest.learningTopics` as "Optional learning topics; at most 50 entries." with `minItems: 0`, and the `IdeaRequest` required list at `contracts/openapi.yaml:4892-4897` omits it. This matches `backend/src/main/java/ac/at/tgm/submit/web/ApiDtos.java:144-147`.

## Scope and contract

- Edit `backend/SPEC.md:49` so that learning topics are optional for idea requests, consistent with `docs/SPEC.md:45`. Title, description, intended outcome, initial scope points, and school year stay required. The full-specification sentence on the same line stays unchanged.
- Review `frontend/SPEC.md:55`, which says an idea submission contains learning topics. Change it only if it states or implies that learning topics are mandatory.
- Check related statements in `backend/SPEC.md` and `frontend/SPEC.md` for contradictions about learning topics.
- Do not change `docs/SPEC.md`, code, annotations, tests, generated contracts, or types. Do not add limits that the specifications do not already state.

## Mechanical checks (the Evaluator re-runs)

This is a documentation-only change, so no automated tests apply and no checks are required.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** search `backend/SPEC.md` and `frontend/SPEC.md` for "learning topic". Record each remaining occurrence in the handoff and confirm that none makes learning topics mandatory.

## Acceptance criteria (the Evaluator judges)

- `backend/SPEC.md` no longer lists learning topics as required for idea requests. The other required idea fields remain required.
- `frontend/SPEC.md` is consistent with optional learning topics.
- No other requirement text changes, and the specification change is declared in the handoff.
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it (for example, tracked `frontend/src/api/schema.d.ts` may need to be committed, while ignored `contracts/openapi.yaml` must not be committed); no check is claimed that was not performed.

## Optional: Specification changes

`backend/SPEC.md` may change, and `frontend/SPEC.md` may change if its wording implies a mandatory field. Nothing needs regenerating. Declare the specification change in the handoff.

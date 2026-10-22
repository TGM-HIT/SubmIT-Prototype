# Dev-42. Align backend learning-topic validation wording with the shared idea rule

Draft from Compliance-03-20261022-0744: refined for implementation and evaluation

## Intent

The backend specification currently lists learning topics as required in an idea request, while the shared product specification says they are included only “if known.” The API already treats `learningTopics` as optional. This contradiction can mislead backend implementors and reviewers about the request contract.

Source: Compliance-03-20261022-0744, report finding 2; `docs/SPEC.md` section 4; `backend/SPEC.md` idea-request description; `IdeaRequest.learningTopics` in `backend/src/main/java/ac/at/tgm/submit/web/ApiDtos.java`.

## Scope and contract

- Documentation-only: revise the idea-request sentence in `backend/SPEC.md` to state that title, description, intended outcome, initial scope points, and school year are required, while learning topics are optional.
- Keep `docs/SPEC.md` unchanged. Its phrase “if known” already expresses optional learning topics.
- Keep `frontend/SPEC.md` unchanged. Its statement that an idea submission contains learning topics does not state that they are mandatory; the frontend form labels them optional and includes them in requests when supplied.
- Do not add validation limits or other API rules to the specification in this ticket. The current generated API contract already documents the optional field and its existing constraints.
- Check all learning-topic references in `backend/SPEC.md` and `frontend/SPEC.md` for contradictions. The response/catalogue references to learning topics describe returned data, not required request fields, and should remain unchanged.
- Exclusions: no changes to code, tests, `docs/SPEC.md`, `frontend/SPEC.md`, API annotations, generated contracts/types, or developer/user guides. Nothing needs regeneration.

## Mechanical checks (the Evaluator re-runs)

- Documentation-only change: no automated test applies, because this ticket changes only backend specification wording and does not change API behavior.
- Search `backend/SPEC.md` and `frontend/SPEC.md` for “learning topic” and confirm the request wording describes optional input, the frontend wording does not make it mandatory, and response/catalogue references remain accurate.
- Compare the revised backend request wording with `docs/SPEC.md` section 4 and the existing `IdeaRequest.learningTopics` API description; confirm the requiredness matches and no unrelated validation limits are introduced.
- No project build or test checks are required.

## One-time manual verification

- None required; the documentation consistency review is specified under Mechanical checks.

## Acceptance criteria (the Evaluator judges)

- `backend/SPEC.md` clearly distinguishes required idea-request fields from optional learning topics.
- `docs/SPEC.md` and `frontend/SPEC.md` remain unchanged; the final wording is consistent with both and with the existing `IdeaRequest` API contract.
- No unrelated requirement text or other file changes.
- Standard (all tickets): existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository conventions require it; no check is claimed that was not performed.

## Specification changes

Only `backend/SPEC.md` may change. Declare that change in the handoff. No API or generated-contract regeneration is needed.

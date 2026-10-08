# Bug-06. Replace a teacher's existing supervision preference

## Intent

A teacher can currently record supervision preferences repeatedly for the same
idea, creating multiple entries for that teacher. Each teacher should have only
one current supervision preference per idea, including its associated full
specification.

## Scope and contract

- Treat `PUT /api/ideas/{id}/supervision-preference` as an upsert keyed by the
  idea and authenticated teacher. A later request replaces that teacher's
  existing preference rather than creating another entry.
- Ensure the one-preference-per-teacher-and-idea rule is maintained in
  persistence, including when repeated writes overlap.
- In authorized idea responses, return no more than one supervision-preference
  entry for each teacher and idea, reflecting that teacher's current recorded
  preference.
- Preserve independent preferences recorded by other teachers for the same
  idea. Keep the existing preference choices, optional note, authorization,
  endpoint, and request/response contract unchanged.
- Do not change the append-only behavior of idea and specification review
  history, supervisor assignment, or any other lifecycle behavior.
- No API shape, validation, or generated contract changes are required.

## Mechanical checks (the Evaluator re-runs)

Durable backend API regression tests (join the build):

- Through the public API, submit one teacher's preference for an idea, submit a
  different preference for the same teacher and idea, then retrieve the
  authorized idea response. Verify it contains exactly one preference for
  that teacher and that it reflects the later submission.
- Verify a second teacher can independently record a preference for the same
  idea and that updating the first teacher's preference does not replace or
  duplicate the second teacher's entry.
- Exercise overlapping writes for one teacher and idea through the public API
  and verify persistence still contains exactly one current preference after
  both requests complete.
- Verify a non-teacher remains unable to record a supervision preference.

Standard backend checks, from `backend/`:

- `./gradlew test`
- `./gradlew checkstyleMain`

No OpenAPI generation or frontend API type generation applies because the
endpoint and its request/response contract remain unchanged.

## One-time manual verification

No separate manual verification applies; the durable API regression tests
exercise the write-and-read behavior through the public HTTP surface.

## Acceptance criteria

- For any idea, an authenticated teacher has at most one current supervision
  preference entry. Submitting a later preference replaces the existing one
  instead of adding another.
- The authorized idea response exposes the current preference once for that
  teacher, and other teachers' preferences remain independent.
- The rule holds for persisted writes, including overlapping repeated writes;
  it is not enforced only by filtering duplicate entries from responses.
- Existing authorization, preference choices, optional-note behavior, review
  history, supervisor assignment, and endpoint contract remain unchanged.
- The standard backend checks pass; no frontend/API shape changes or generated
  API artifacts are introduced; existing behavior outside this scope is
  unchanged, and no check is claimed that was not performed.

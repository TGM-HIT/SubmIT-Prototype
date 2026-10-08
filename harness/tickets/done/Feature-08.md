# Feature-08. Show account names in Ideas & reviews

## Intent

The Ideas & reviews interface identifies people by opaque account IDs, making it difficult to recognize who submitted an idea, reviewed it, or expressed interest in supervising it. Show each person's account display name instead.

## Scope and contract

- In the authenticated Ideas & reviews page and its Details & history modal, show account display names instead of IDs for:
  - The idea submitter in the overview list.
  - Teachers in review-history entries.
  - Teachers with recorded supervision preferences.
  - Eligible teachers in the administrator's Confirm supervisor selector.
- Use the account's current `displayName`, not the username. Do not render the account ID in these user-facing identity labels. Preference status, review context, and any associated note remain visible.
- Preserve IDs in API contracts and internal identity matching where they are needed; the user-facing labels must use names. Do not change idea ownership, preference, review, or supervisor-assignment behavior.
- Supply the names through the existing authorized idea-list/detail response data or another narrowly scoped authorized response. Extend no caller's ability to see an identity: names must be available only wherever that caller is already authorized to see the corresponding user reference. Do not expose these names through public archive or unauthenticated responses, or add a general user-directory endpoint for this display.
- Keep stored review/preference/ownership records and their historical IDs unchanged; display the current account name associated with each referenced account.
- Update [frontend/SPEC.md](../../frontend/SPEC.md) and [backend/SPEC.md](../../backend/SPEC.md) minimally to describe the display behavior and the authorized response data. No shared product-specification change is required.
- This changes backend response models and the generated API contract. Update backend annotations/models, regenerate `contracts/openapi.yaml` using `./gradlew generateOpenApi`, review the generated diff, and regenerate frontend API types using `npm run api:types`. Do not edit generated files manually.

## Mechanical checks (the Evaluator re-runs)

Durable automated regression tests (join the build):

- Backend public-API tests verify authorized idea-list and idea-detail responses include the correct account display names for the submitter, each review author, and each teacher with a supervision preference, while preserving their IDs and existing role-based visibility.
- Backend tests verify name data is not made available through public archive responses and that existing authorization failures remain unchanged.
- Frontend tests verify the Ideas & reviews overview, review history, recorded-preference list, and administrator Confirm supervisor selector display the associated account display names instead of IDs. Cover student, teacher, and administrator contexts where the corresponding references are visible, and verify current preference/review context remains present.

Standard project checks:

- Backend, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend, from `frontend/`: `npm run typecheck`, `npm run test`, and `npm run build`.
- API contract/type generation: from `backend/`, run `./gradlew generateOpenApi` and review the `contracts/openapi.yaml` diff; from `frontend/`, run `npm run api:types` and review the generated types.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** Use the documented local browser preview and testing-profile accounts in [frontend/DEV-GUIDELINES.md](../../frontend/DEV-GUIDELINES.md) and [backend/DEV-GUIDELINES.md](../../backend/DEV-GUIDELINES.md). With an idea that has a submitter, review history, and supervision-preference entries, inspect the Ideas & reviews overview, Details & history modal, and (as an administrator) Confirm supervisor selector. Confirm each visible person is identified by account display name rather than UUID and that preference/decision context remains clear. Capture the affected states at desktop and mobile widths outside version control and investigate/report browser-console and failed-request errors.
- If the testing profile does not contain the required idea/review/preference data, create it through the existing UI or make a minimal reusable testing-profile preview extension with smoke coverage; do not hard-code production credentials or use non-testing data.

For UI-visible work, use the local browser preview documented in [frontend/DEV-GUIDELINES.md](../../frontend/DEV-GUIDELINES.md), not one-off scripts:

- Authenticate with documented testing-profile accounts; do not hard-code credentials.
- Capture desktop and mobile viewports for the affected states; store screenshots outside version control.
- Investigate any console or failed-request errors and report them.
- Keep fixtures and test data isolated to the testing profile.

## Acceptance criteria (the Evaluator judges)

- In Ideas & reviews, an authorized viewer sees the account display name, not an opaque ID, for the submitter, review authors, and teachers with recorded supervision preferences; administrators also see names in the eligible-teacher selector.
- The displayed name belongs to the referenced account and is its current display name. Existing IDs remain usable for internal matching and API identity references, but are not shown in these interface labels.
- Existing review, supervision preference, and supervisor-assignment actions, status/context/note display, and role/resource authorization remain unchanged.
- Identity-name availability does not broaden access to user identities and is not exposed in public archive or unauthenticated responses.
- The affected views remain usable at desktop and mobile widths without horizontal overflow.
- Applicable frontend/backend regression tests and standard checks pass; the generated OpenAPI contract and frontend API types are regenerated and reviewed, never hand-edited; existing behavior outside this scope is unchanged, with no unperformed check claimed.

## Optional: Specification changes

Update [frontend/SPEC.md](../../frontend/SPEC.md) for the Ideas & reviews labels and [backend/SPEC.md](../../backend/SPEC.md) for the corresponding authorized response data. Declare each change in the implementor handoff. No change to [docs/SPEC.md](../../docs/SPEC.md) is required.

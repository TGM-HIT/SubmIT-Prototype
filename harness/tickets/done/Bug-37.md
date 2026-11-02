# Bug-37. Declare nonblank Idea PDF metadata strings in OpenAPI

## Intent

The generated OpenAPI request schema allows empty `title` and `schoolYear` values for `IdeaPdfMetadataRequest`, even though the backend rejects blank values. Contract-based clients therefore accept requests that runtime validation rejects.

## Source (when applicable)

Source: Compliance-03-20261101-1758, report finding 2, “`IdeaPdfMetadataRequest.title` and `schoolYear` are required nonblank values at runtime, but the generated schema emits `minLength: 0`”; repository revision: `34b765460f516d8e27e4bbd33e79d57480d9140a`; `backend/src/main/java/at/ac/tgm/submit/web/ApiDtos.java:174-185`; regenerated `contracts/openapi.yaml` shows `minLength: 0` for both properties at `IdeaPdfMetadataRequest.properties.title` and `.schoolYear` (the generated YAML is ignored and not revisioned).

## Scope and contract

- **Product-owner decision (2026-11-02):** Markus Schabel selected option A in `harness/runs/Bug-37-20261102-1231/human-acceptance.md`, rationale: "It should not be 0.", and confirmed continuation in chat. Permit adding `min = 1` to the two existing `@Size` declarations for metadata title/year while retaining `@NotBlank` and existing maxima. This narrowly overrides the schema-annotation-only and unchanged-Bean-Validation-declaration constraints below; runtime accepted/rejected inputs must remain equivalent, proven by M2/M3. No schema customizer or other runtime change is authorized.

- Correct only the OpenAPI minimum-length declarations for `ApiDtos.IdeaPdfMetadataRequest.title` and `.schoolYear`, preferably using the existing field-level `@Schema(minLength = 1)` pattern on the adjacent `IdeaRequest`. Keep the existing Bean Validation annotations; do not alter global generator configuration when local annotations suffice.
- The same metadata schema is used by multipart `POST /api/ideas/pdf` (creation, `201`) and `PUT /api/ideas/{id}/pdf` (replacement, `200`). Preserve both operations' `metadata` reference to `IdeaPdfMetadataRequest`, required `metadata` and `file` parts, and existing response/status/security declarations. This ticket does not change an API response matrix.

| Request property | Required generated constraint |
| --- | --- |
| `title` | Required, non-null string; `minLength: 1`, `maxLength: 200`; exact existing description `Manually entered idea title; at most 200 characters.` |
| `schoolYear` | Required, non-null string; `minLength: 1`, `maxLength: 20`; exact existing description `Manually selected school year; at most 20 characters.` |
| `collaboratorIds` | Optional array; preserve its complete implementation-entry schema, including `minItems: 0`, `maxItems: 4`, `uniqueItems: true`, string items with `format: uuid`, and description `Optional student collaborator IDs; creator is not included; maximum four.` Do not add a required or nullability restriction. |

- Keep the exact metadata property set `title`, `schoolYear`, `collaboratorIds` and required set `title`, `schoolYear`; preserve its schema-level description. No response schema changes are intended: preserve `IdeaResponse`, related list/catalogue/specification responses, and the shared error schema.
- Runtime semantics remain unchanged: `PdfRequestValidator.ideaMetadata` deserializes and validates the metadata before `IdeaPdfService` writes it. `@NotBlank` rejects missing, null, empty, and whitespace-only title/year; `@Size` limits the supplied strings before service trimming. Accepted title/year values are trimmed when stored. `minLength: 1` excludes empty strings in OpenAPI but does not encode the full whitespace-only rule; do not add a pattern, change descriptions, trim before validation, or claim client-side schema validation now duplicates `@NotBlank`.
- Restrict deliverables to the two schema annotations and relevant backend contract/public HTTP regressions, plus tracked frontend types if generation changes them. Do not change controllers, validators, services, authorization, error handling, upload limits, lifecycle rules, JSON narrative schemas, product SPEC wording, tooling, or dependencies. Bug-38 owns upload-limit/status-description corrections on these PDF operations; it is independent, not a prerequisite, and its work must not be folded into Bug-37.
- Preservation baseline: capture the current generated schemas and the two multipart operations at implementation entry, not the historical Source revision. Compare parsed JSON/YAML mappings without regard to key order, retaining array order and every value. After replacing only the two post-change `minLength` values with their captured pre-change values for comparison, require equality of the complete `components.schemas` and the two named operations. No other normalization is allowed; textual identity is not required. This bounded comparison excludes top-level generated server URLs, so random generation ports do not require a waiver or tooling change. Retain the original pre/post snapshots and comparison result under the ignored run's `evidence/` directory.
- Regenerate from annotations with `./gradlew generateOpenApi` from `backend/` and review the contract diff. `contracts/openapi.yaml` is ignored: never hand-edit or commit it. From `frontend/`, run `npm run api:types`; commit `frontend/src/api/schema.d.ts` only if regeneration changes this tracked file. An unchanged TypeScript output is acceptable because TypeScript string types do not encode string-length constraints. All required deliverable locations are already permitted by the scope gate; no temporary allowance is needed.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- **M1 — Exact generated metadata contract:** extend `OpenApiContractTest.pdfNarrativeAlternativesAndPrivateBinaryResponsesAreExplicit` or add a focused public `/v3/api-docs` test. Assert the complete metadata property and required sets, both non-null string types, the two minimum/maximum lengths, and exact field/schema descriptions. Assert the complete `collaboratorIds` schema against an independent expected value, including its optionality, bounds, uniqueness, and UUID item format. Check both multipart operations still reference this metadata schema and require the same two parts. Check `IdeaResponse` and representative existing JSON request/response constraints remain unchanged; derive test expectations independently of the production annotations/customizer.
- **M2 — Invalid metadata on both write routes:** extend `IdeaPdfApiTest` with public multipart requests using the existing valid small PDF fixture and a testing-profile student. For each of `title` and `schoolYear` separately, hold the other field valid and test omitted, explicit null, empty, whitespace-only, and over-maximum input (201 or 21 ASCII characters respectively). Exercise both POST creation and PUT replacement of an owned editable draft. Every case returns shared JSON `400` (`status: 400`, `error: Bad Request`, `message: PDF idea metadata is invalid.`). Retain no-side-effect checks: rejected creates add no idea/audit record; rejected replacements preserve metadata, lifecycle, owner set, PDF bytes, and audit history. Reuse existing snapshot/readback helpers without testing private validator methods.
- **M3 — Valid metadata boundaries and trimming:** on both write routes with the same actor, valid PDF, editable lifecycle, and collaborator setup held constant, test nonblank ASCII strings of length 1 and at each field's exact maximum (200/20), plus ordinary strings with surrounding spaces whose raw lengths are within those maxima. POST returns `201`, PUT returns `200`; response and subsequent authorized detail reads contain the expected title/year, trimmed for padded inputs. PDF readback remains the uploaded bytes. Omitted `collaboratorIds` remains accepted; retain existing owner-preservation and student/teacher upload tests. This contrasts the invalid matrix with accepted inputs without changing runtime behavior.
- **M4 — Bounded generated preservation and source review:** perform the exact implementation-entry comparison specified above, requiring only the two authorized minimum lengths to vary. Inspect the complete source diff to verify production changes are schema annotations only, not validation or behavior changes. Retain pre/post snapshots, commands, actual results, and conclusions in run evidence. This is an Implementor-performed comparison independently repeated by the Evaluator, complementing durable M1-M3 tests; no new test tooling is required.

Standard project checks:

- From `backend/`: `./gradlew compileJava compileTestJava --rerun-tasks`, `./gradlew test`, and `./gradlew checkstyleMain`.
- From `backend/`: `./gradlew generateOpenApi`, review the generated contract; from `frontend/`: `npm run api:types`.
- If tracked generated types change, from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

- No browser or human-only verification applies. Generated-contract and public multipart HTTP tests cover the affected constraints. The Implementor and independent Evaluator perform M4's source/snapshot comparison and retain its evidence; do not rerun a Compliance audit or extend browser fixtures for this schema-only correction.

## Acceptance criteria (the Evaluator judges)

- **AC1 (M1):** generated `IdeaPdfMetadataRequest` has exactly the specified properties and required set; title/year are non-null strings with `minLength: 1`, existing maxima, and exact existing descriptions. Both multipart write operations reference it with unchanged required parts.
- **AC2 (M1, M3, M4):** `collaboratorIds` remains optional with its complete existing array/item constraints, and no response or unrelated request schema changes. The implementation-entry comparison permits only the two named minimum-length changes.
- **AC3 (M2):** missing, null, blank, and overlong metadata on each write route still returns the existing shared `400` without creating or changing idea/PDF/audit state.
- **AC4 (M3, M4):** minimum/exact-maximum and valid padded inputs succeed on creation and replacement, preserving existing trimming, uploaded bytes, and ownership behavior; no runtime validation or permission rule changes.
- **AC5 (standard checks):** applicable compilation, backend tests, Checkstyle, OpenAPI/type generation, and conditional frontend checks pass. Generated artifacts are regenerated and reviewed, never hand-edited; ignored OpenAPI output is not committed and changed tracked types are committed. No unperformed check is claimed.

## Current-state triage

Re-read the metadata record, both PDF write controllers, `PdfRequestValidator`, `IdeaPdfService`, and existing contract/API tests before editing. Capture the implementation-entry generated schema; the historical Source note is unchanged provenance, not a current line map. Existing PDF tests prove valid uploads and invalid files, not the new per-field metadata matrix unless their actual assertions establish it. A documented no-op is acceptable only if all acceptance criteria, durable scenarios, preservation comparison, and applicable checks already hold; record the unchanged HEAD and diff.

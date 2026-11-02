# Dev-70. Review and salvage the Dev-62 public HTTP regressions

## Intent

Recover the two known Evaluation-4 regression candidates without accepting the failed traceability inventory or rewriting product behavior.

## Source

Source: owner-authorized recovery of [Dev-62](../Dev-62.md); [failed handoff-2](../../runs/Dev-62-20261102-1537/handoff-2.md), [run log](../../runs/Dev-62-20261102-1537/run.log), and archived [Evaluation 4](../../runs/archive/Compliance-02-20261101-1603/evaluation-4.md), E2b/E2c. Authoring HEAD: `bcbdc9ddae51c7d2fc842a3050397af4924848f5`. Reported passing checks are evidence leads, not an independent acceptance.

## Scope and contract

- **Owner preservation update (2026-11-02):** the owner reports moving the seven partial files into [the failed run's evidence directory](../../runs/Dev-62-20261102-1537/evidence), retaining their basenames. All seven saved paths exist at refinement time. For Dev-70, this supersedes the pending preservation/isolation authorization described in Dev-62; do not request the same preservation decision again or require a separate worktree solely for these candidates. This records the owner's custody transfer, not an independently verified pre-move byte comparison.

  | Saved file under the evidence directory | Original repository path |
  | --- | --- |
  | `ArtifactServiceTest.java` | `backend/src/test/java/at/ac/tgm/submit/archive/ArtifactServiceTest.java` |
  | `ProjectWorkApprovalApiTest.java` | `backend/src/test/java/at/ac/tgm/submit/archive/ProjectWorkApprovalApiTest.java` |
  | `DEV-GUIDELINES.md` | `frontend/DEV-GUIDELINES.md` |
  | `package.json` | `frontend/package.json` |
  | `spec-test-traceability.md` | `docs/spec-test-traceability.md` |
  | `check-spec-test-traceability.mjs` | `frontend/scripts/check-spec-test-traceability.mjs` |
  | `spec-test-traceability.smoke.mjs` | `frontend/scripts/spec-test-traceability.smoke.mjs` |

- **Operational prerequisite:** ordinary clean-tree preflight still applies. Moving the four tracked files can leave tracked deletions; their original paths must contain the approved committed versions before dispatch, not the saved partial candidates. Inspect status and resolve any remaining tracked deletions or unrelated authoring changes through owner-authorized preparation, without automatically restoring, staging, committing or discarding work under this ticket. This refinement performs no restoration. Record the actual clean implementation-entry revision; setup/closeout SHAs from the failed run contain no implementation.
- Treat all seven saved files as read-only evidence: record their paths and checksums before salvage and verify them unchanged afterward. Do not move them back, edit them, delete them or force-add ignored evidence. Compare the two saved backend files against their original paths at the clean entry revision to identify candidate changes. Review only those changes, plus existing relevant tests and the completion/grading requirements in the three approved SPEC files. Apply only reviewed, necessary regression changes to the two original backend test paths. Preserve the other five saved files without importing them into this deliverable.
- First inspect existing assertions, including `ArtifactServiceTest.completionRequiresFinalGradeAndEveryArtifactTypeAndPublicMetadataIsMinimal`, `GradingServiceTest.otherRubricVersionRequiresExplicitTransition`, and current grading HTTP tests. Do not duplicate sufficient evidence or infer coverage from a name.
- Review/salvage `completionRequiresEachArtifactTypeWithFinalGradeAndApprovedApplicationPresent`: authorized teacher, active project, approved formal application, final grade present, all other prerequisites held constant; omit `WEBSITE_ARCHIVE`, `POSTER`, and `SOURCE_DATA` independently. Exercise `POST /api/projects/{projectId}/complete`; require rejection and unchanged persisted project state, with an all-types-present allowed control. A success with a grade does not prove rejection without one; retain only the narrower final-grade assertion the evidence actually proves.
- Review/salvage `publishedRubricTransitionPreservesPriorAssessmentThroughPublicHistoryApi`: record assessment under A, publish/select B through supported authorized operations, read public authenticated assessment history, and assert the same assessment identity, A association, grade, criterion and recorded evidence survive. Separately prove an allowed new B write uses B. Transition/new-write checks alone are not preservation proof.
- Existing service tests are evidence leads; new behavioral regressions use public HTTP per `backend/DEV-GUIDELINES.md`. Test setup may use existing fixtures; assertions must establish the HTTP behavior and persisted outcome.
- No production runtime, policy, SPEC, generated output, frontend, inventory, ticket or ledger edits. Inventory adoption belongs to the classification/evidence tickets. A no-op is acceptable if inspected existing tests already satisfy every required assertion and checks pass.
- If an exact expectation fails, retain controlled setup, expectation, actual result and reproducing command/output in run evidence; hand off a precise finding for the owning classification/evidence ticket to persist. Stop that regression's salvage, do not fix product code, weaken expectations or merge failing/skipped/disabled tests. Do not claim this ticket accepted with an unresolved required scenario; request bounded follow-up authoring through Dev-62.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- **H1 — Completion omissions and control:** the three independent missing-type cases and allowed all-types-present case above; verify final grade and approved application are actually present, HTTP rejection and unchanged state, not just an exception from setup.
- **H2 — History preservation and new write:** the A-to-B scenario above, inspecting exact history assertions independently of transition/new-write assertions.
- **H3 — Preservation/failure review:** verify clean-entry provenance and the saved-to-original mapping above; compare the two saved backend candidates and final allowed test diffs against that entry revision. Verify all seven saved-file checksums are unchanged and no runtime/expectation weakening, failing placeholder or unrelated partial was imported. If H1/H2 fails, verify the reproduction and honest blocked handoff instead of inventing a pass.

Standard checks, from `backend/`: `./gradlew compileJava compileTestJava --rerun-tasks`, `./gradlew test`, `./gradlew checkstyleMain`. No frontend/application/browser checks or API generation apply.

## One-time manual verification

No browser walkthrough required. **Implementor-performed:** retain a scenario-to-method/assertion table, saved-file paths/checksums and clean-entry provenance in the run handoff. **Independent Evaluator:** inspect each setup and assertion for H1/H2 and verify H3 preservation; the earlier handoff's reported green suite is not sufficient.

## Acceptance criteria

- **AC1 (H1):** each omission is isolated with the other prerequisites constant, rejects completion without persisting it, and has an allowed control.
- **AC2 (H2):** existing assessment identity/version/values survive the transition, separately from a new B write.
- **AC3 (H3):** only reviewed test work is salvaged into the original two backend paths; all seven saved evidence files remain unchanged, clean-entry provenance is recorded, and no incomplete inventory is accepted. A reproduced defect blocks the affected acceptance and is handed off separately, not silently fixed.
- **AC4 (H1–H3, standard checks):** applicable checks pass and are accurately reported; no unrelated changes or generated edits. Handoff names the exact methods, setups, assertions, tested revision and reusable clause/evidence leads for Dev-72/Dev-73.

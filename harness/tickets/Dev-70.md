# Dev-70. Review and salvage the Dev-62 public HTTP regressions

## Intent

Recover the two known Evaluation-4 regression candidates without accepting the failed traceability inventory or rewriting product behavior.

## Source

Source: owner-authorized recovery of [Dev-62](Dev-62.md); [failed handoff-2](../runs/Dev-62-20261102-1537/handoff-2.md), [run log](../runs/Dev-62-20261102-1537/run.log), and archived [Evaluation 4](../runs/archive/Compliance-02-20261101-1603/evaluation-4.md), E2b/E2c. Authoring HEAD: `bcbdc9ddae51c7d2fc842a3050397af4924848f5`. Reported passing checks are evidence leads, not an independent acceptance.

## Scope and contract

- **Operational prerequisite:** before any recovery implementation run, the coordinator must obtain separate owner authorization for preservation/isolation of the exact seven dirty paths enumerated in Dev-62's recovery startup section. Preparation did not perform isolation. Preserve every partial until individually reviewed or safely isolated; do not discard, broadly stash/reset, stage unrelated work, or bypass normal clean-tree preflight. Record original bytes/provenance and the isolated baseline. Setup/closeout SHAs contain no implementation.
- Review only the retained changes in `backend/src/test/java/at/ac/tgm/submit/archive/ArtifactServiceTest.java` and `backend/src/test/java/at/ac/tgm/submit/archive/ProjectWorkApprovalApiTest.java`, plus existing relevant tests and the completion/grading requirements in the three approved SPEC files. Apply only reviewed, necessary regression changes to the isolated recovery workstream. Preserve the other five partial paths without importing them into this deliverable.
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
- **H3 — Preservation/failure review:** compare only the two allowed test diffs with their isolated entry revision and retained partial provenance. Ensure no runtime/expectation weakening, failing placeholder or unrelated partial was imported. If H1/H2 fails, verify the reproduction and honest blocked handoff instead of inventing a pass.

Standard checks, from `backend/`: `./gradlew compileJava compileTestJava --rerun-tasks`, `./gradlew test`, `./gradlew checkstyleMain`. No frontend/application/browser checks or API generation apply.

## One-time manual verification

No browser walkthrough required. **Implementor-performed:** retain a scenario-to-method/assertion table and the isolation provenance in the run handoff. **Independent Evaluator:** inspect each setup and assertion for H1/H2; the earlier handoff's reported green suite is not sufficient.

## Acceptance criteria

- **AC1 (H1):** each omission is isolated with the other prerequisites constant, rejects completion without persisting it, and has an allowed control.
- **AC2 (H2):** existing assessment identity/version/values survive the transition, separately from a new B write.
- **AC3 (H3):** only reviewed test work is salvaged, all other partials/evidence remain preserved, and no incomplete inventory is accepted. A reproduced defect blocks the affected acceptance and is handed off separately, not silently fixed.
- **AC4 (H1–H3, standard checks):** applicable checks pass and are accurately reported; no unrelated changes or generated edits. Handoff names the exact methods, setups, assertions, tested revision and reusable clause/evidence leads for Dev-72/Dev-73.

# Dev-29. Clarify the unresolved Java package rename proposal

Draft from Compliance-05-20261018-2017: needs Ticket Author refinement before implementation

## Intent

The legacy ideas list proposes renaming `java.com.flightlog` to `da-db`, but the source tree contains Java packages under `com.flightlog` and no source reference to `java.com.flightlog`. The proposal does not identify a verified replacement target.

Source: `Compliance-05-20261018-2017`, report finding 2: `list-of-bugs.md:7` says “Rename java.com.flightlog to da-db.” The audit's `git grep -nF 'java.com.flightlog' -- backend/src backend/build.gradle` returned no matches; current source declares `package com.flightlog` (for example, `backend/src/main/java/com/flightlog/grading/FinalGradeInputs.java:1`).

## Scope and contract

- Obtain and record the intended disposition of the line 7 proposal before attempting a rename.
- The disposition must choose whether to retarget the entry to a verified source name, convert it to plain text that does not claim a source identifier, or accept it as a legacy idea pending further review.
- Do not infer a replacement or rename any package, artifact, or repository path before the decision is recorded.
- If a rename is confirmed, refine a separate implementation scope that identifies the exact package/artifact targets and compatibility requirements.

## Mechanical checks (the Evaluator re-runs)

Documentation-only or harness-only changes: no application checks required. Verify the decision is recorded and every stated replacement target resolves in the repository tree. Run `git diff --check` and format exactly the changed Markdown files.

## One-time manual verification (in addition to automated tests)

- Human-only: an authorized project owner records whether to retarget, convert to plain text, or accept the legacy idea. No choice is preferred by this ticket.

## Acceptance criteria (the Evaluator judges)

- The authorized disposition is recorded without presenting an unverified name as a correct replacement.
- If a target is named, it resolves in the repository tree; otherwise, the entry is explicitly plain text or accepted as a legacy idea.
- No package or artifact rename is implemented under this decision-only ticket.
- No unperformed check or decision is claimed.

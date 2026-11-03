# Dev-66. Retarget historical evidence links to archived run records

## Intent

Three completed tickets link to human-acceptance and evaluation records using their original run paths, but those run directories have been archived under `harness/runs/archive/`. Retarget the links to the existing archived records so readers can reach the same preserved decisions and evaluation.

## Source

Compliance-04-20261101-2012, Finding 3, reported the original targets as missing: `harness/runs/Dev-44-20261025-1637/human-acceptance.md`, `harness/runs/Dev-39-20261025-0933/human-acceptance.md`, and `harness/runs/Dev-39-20261025-0933/evaluation-1.md`. Refinement verified the corresponding files at the matching paths beneath `harness/runs/archive/`; the archived run inventory identifies both run directories. These run records are ignored local archive artifacts, not tracked repository files.

## Scope and contract

- Change only the affected Markdown link destinations in:
  - `harness/tickets/done/Dev-44.md`: `../../runs/Dev-44-20261025-1637/human-acceptance.md` → `../../runs/archive/Dev-44-20261025-1637/human-acceptance.md`.
  - `harness/tickets/done/Dev-54.md`: `../../runs/Dev-44-20261025-1637/human-acceptance.md` → `../../runs/archive/Dev-44-20261025-1637/human-acceptance.md`.
  - `harness/tickets/done/Dev-53.md`: retarget both `../../runs/Dev-39-20261025-0933/human-acceptance.md` and `../../runs/Dev-39-20261025-0933/evaluation-1.md` to the corresponding paths under `../../runs/archive/Dev-39-20261025-0933/`.
- Preserve the existing link labels, the `#part-b--decisions-needed` anchor on the Dev-53 human-decision link, and all surrounding evidence claims and decisions.
- The archived targets exist in this workspace but are ignored under `harness/runs/`; do not describe them as tracked or commit, move, rewrite, recreate, or force-add any run artifact. The intended disposition for all three references is **retarget to the matching archived record**. Do not convert these links to plain text or treat the targets as unresolved.
- Do not change any other ticket text, archive README or run record, Compliance-04 finding, plan row, or unrelated path.
- Scope-gate allowances: `harness/tickets/done/Dev-44.md`, `harness/tickets/done/Dev-53.md`, `harness/tickets/done/Dev-54.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- No automated test applies: this is a bounded link-destination correction; verify the exact targets and anchor directly as listed below.

Standard project checks:

- Documentation-only change; no backend or frontend project tests apply.
- Verify each of the three exact archived target files exists with `test -f`, and verify the Dev-53 anchor `part-b--decisions-needed` matches the archived human-acceptance heading.
- Verify the three archive targets remain ignored local files (`git check-ignore -q`) and are not tracked (`git ls-files --error-unmatch` must not resolve them); do not characterize them as tracked targets.
- Search the three named completed tickets for the old run paths and confirm no affected link remains. Review the diff to confirm only the four specified destinations changed.
- Format only the changed Markdown files with the repository Markdown formatter and run `git diff --check`.

## One-time manual verification (in addition to automated tests)

- None. Filesystem existence, anchor matching, and destination-only diff review are deterministic checks.

## Acceptance criteria (the Evaluator judges)

- The Dev-44 and Dev-54 human-acceptance links resolve to the existing `Dev-44-20261025-1637` archived record.
- Both Dev-53 links resolve to the existing `Dev-39-20261025-0933` archived records; its decision link retains a valid `#part-b--decisions-needed` anchor.
- All four references use the intended retarget disposition. Their targets are reported accurately as ignored local archive files, not as tracked files or portable repository assets.
- Link labels, evidence meaning, owner decisions, evaluation content, and all surrounding ticket requirements remain unchanged. No historical run record is altered, fabricated, committed, or force-added.
- Changes are limited to the four link destinations in the three named tickets; the plan and unrelated files remain unchanged.
- Standard documentation-only ticket requirements are met; no project tests or other unperformed checks are claimed.

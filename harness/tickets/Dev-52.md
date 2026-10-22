# Dev-52. Anchor line-numbered ticket sources to a Git revision

## Intent

Tickets can cite source files by line number, but line numbers may identify different text after later edits. Without a commit reference, implementors and evaluators cannot reliably determine which repository state the cited lines describe. Both the Ticket Author and Auditor workflows create tickets, and the shared ticket template does not currently define source-revision metadata.

Source at repository HEAD `6351e42e1578c20cec899828be0f0b688b8898b1`: `harness/roles/ticket-author.md:5,25`, `harness/roles/auditor.md:55,59`, and `harness/templates/ticket.md:1-5`.

## Scope and contract

- Update `harness/templates/ticket.md`, `harness/roles/ticket-author.md`, and `harness/roles/auditor.md` so every agent creating a ticket records the exact, full commit ID returned by `git rev-parse HEAD` when the ticket is created, if the ticket cites any file by line number or line range.
- Define a consistent ticket citation convention that places the full commit ID in the same source note as the line-numbered file references and makes clear that those citations refer to that repository revision. Preserve any existing source context, such as an Auditor's run ID and report entry.
- Capture the current HEAD immediately before authoring the new ticket and ensure its line-numbered citations identify content at that revision. Do not abbreviate, infer, or substitute a run-start SHA or a later commit for the captured HEAD.
- Tickets that cite files without line numbers do not need a commit ID under this rule. Do not add revision metadata to existing tickets or change their citations as part of this work.
- Do not change ticket IDs, plan workflow, audit baseline/report requirements, or other ticket-authoring behavior.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- None apply. The change updates ticket-authoring documentation; the required citation rule is verified against the shared template and both documented ticket-creation workflows.

Standard project checks:

- Documentation-only or harness-only change: no project build or test run applies. Run `make format-markdown FILES="harness/templates/ticket.md harness/roles/ticket-author.md harness/roles/auditor.md"` and `git diff --check`.
- Review the template and both role contracts together. Confirm they consistently require the unshortened current HEAD from `git rev-parse HEAD` when a newly created ticket cites a file line or line range, associate the SHA with those citations, and preserve the Auditor's existing run/report source context.
- Search `harness/roles/` for ticket-creation instructions and verify that the template and role requirements cover every documented ticket-creation path; confirm no unrelated agent or ticket workflow is changed.
- Confirm the rule does not require a commit ID for path-only references and does not retrofit or revise existing tickets.

## One-time manual verification (in addition to automated tests)

- None required; the authoring paths and citation convention are directly reviewable in their documented contracts.

## Acceptance criteria (the Evaluator judges)

- Every documented agent workflow that creates a new ticket requires the exact full output of `git rev-parse HEAD` when that ticket cites one or more file line numbers or ranges, and the ticket associates that revision with those citations.
- The template and Ticket Author and Auditor contracts use one consistent convention; Auditor tickets retain their run ID, report entry, and quoted evidence while identifying the repository revision for any line-numbered file evidence.
- Tickets that contain no line-numbered file citations are not required to include a commit ID; existing tickets are not retroactively edited.
- No ticket status, ticket workflow, audit baseline/report rule, or unrelated authoring behavior changes.
- Standard (all tickets): existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository conventions require it; no check is claimed that was not performed.

# Dev-28. Reconcile and index the legacy bug list

Draft from Compliance-05-20261018-2017: needs Ticket Author refinement before implementation

## Intent

The four unresolved entries in `list-of-bugs.md` do not have corresponding entries in the plan's ticket ledger. The file also has no navigable link from the root README or plan, so readers cannot tell whether these proposals remain active, were superseded, or need a product decision.

Source: `Compliance-05-20261018-2017`, report finding 1: `list-of-bugs.md:3` says “Allow an allocation period to be modified later on (at least the dates, maybe add additional projects).”; lines 5, 7, and 9 list a documentation run with screenshots, renaming `java.com.flightlog` to `da-db`, and a DA-Excel run. The audit searched open and completed tickets and found no corresponding tickets. The link scan found no incoming Markdown link to `list-of-bugs.md` from `README.md` or `harness/plan.md`.

## Scope and contract

- Review each of the four entries in `list-of-bugs.md` against the current plan, open tickets, completed tickets, and repository state.
- For each entry, either map it to an appropriate ticket with a concrete scope, or record an explicit disposition that preserves it as an idea, marks it superseded, or identifies the product decision still needed.
- If `list-of-bugs.md` remains the active inventory, add a navigable Markdown link from the root `README.md` or `harness/plan.md`; if its content is moved or retired, ensure the surviving source is indexed instead.
- Scope-gate allowances: `list-of-bugs.md` is the only required scope-gate allowance.
- Preserve unresolved requests; do not infer or implement product policy, delete entries, or prescribe a product outcome.
- Update the list and ticket ledger only as needed to make the chosen disposition and any ticket links unambiguous.

## Mechanical checks (the Evaluator re-runs)

Documentation-only or harness-only changes: no application checks required. Verify that each original entry has an explicit disposition and that every linked ticket resolves. Run `git diff --check` and format exactly the changed Markdown files.

## One-time manual verification (in addition to automated tests)

No manual verification is required; no UI, API, or application behavior changes are in scope.

## Acceptance criteria (the Evaluator judges)

- Each of the four original entries has a clear, reviewable disposition and is either linked to its ticket or explicitly retained without an implementation ticket.
- Any retained active inventory has a navigable entry point from the root README or plan.
- No entry is silently deleted or converted into an unstated product requirement.
- Any ticket created is recorded in the matching plan group and has scope supported by the entry and any required product decision.
- No application behavior changes or unverified checks are claimed.

# Dev-21. Resolve bug-register intake dispositions

Draft from Compliance-05-20261016-0850: needs Ticket Author refinement before implementation

## Intent

Give the four unindexed entries in `list-of-bugs.md` an explicit planning disposition without turning undecided ideas into implementation requirements. The register's title can suggest active defects, but none of its four headings identifies a ticket or approved scope.

Source: `Compliance-05-20261016-0850`, report finding 4. `list-of-bugs.md:3,13,15,17` requests later period editing, a screenshot-based documentation run, a package rename, and a real DA-Excel exercise. Its line 5 says “Decision needed before a ticket can be written,” while `backend/SPEC.md:31` already documents updating dates/catalogue until finalization. `harness/checks/Compliance-05.md:20` asks for parity of open entries with tickets; no matching ticket was established in the open or done directories. Existing period-creation, labels, early-run, pin and preview-tooling tickets do not establish these four requested outcomes.

## Scope and contract

- **Decision needed before implementation:** determine the disposition of each entry: actionable requirement needing a separately refined ticket, intentionally deferred idea, already covered work with an evidence-backed reference, or no longer pursued work. Do not prescribe which disposition is correct.
- For the period-edit entry, retain its existing A/B/C decision options and ask what gap remains beyond the approved update API. Do not infer UI/status/reopening policy from the heading or declare it complete because PUT exists.
- The competing statements are the register's request to “Allow an allocation period to be modified later on” and its explicit unresolved decision, versus the existing approved administrator update API until finalization. The SPEC settles that existing API, not the additional intent.
- Obtain user scope decisions for the documentation walkthrough, rename, and DA-Excel exercise before writing implementation requirements; do not access real student files or invent data/privacy requirements in this intake task.
- Record confirmed dispositions and traceable ticket links in `list-of-bugs.md`; preserve the entries and their history. Do not delete sections, implement features, change product specifications, or mark work done without evidence.
- Ticket Author refinement must resolve the decision before removing this draft's implementation block. Additional implementation tickets, if authorized, must use their appropriate group and next available ID.
- Existing reference targets verified: `list-of-bugs.md`, `backend/SPEC.md`, `harness/plan.md`, and both ticket directories. No replacement target is asserted for any undecided entry.
- Scope-gate allowances: `list-of-bugs.md`. Ticket/index authoring follows the author contract, not an Implementor editing the ledger.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- None applies: this is a documentation-intake decision and indexing task, not application behavior.

Standard project checks:

- Documentation-only change: no application build/test run required. Verify each retained entry has an explicit confirmed disposition, every supplied ticket link resolves and agrees with the plan, and `git diff --check` passes. Format exactly changed Markdown.

## One-time manual verification (in addition to automated tests)

- **Human-only:** the user confirms the intended disposition and remaining scope for each entry. Record the exact decisions and dates; do not answer on the user's behalf. No browser or real-data walkthrough is required by this intake ticket.

## Acceptance criteria (the Evaluator judges)

- All four headings have a user-confirmed, dated disposition; actionable work has authorized, refined ticket mapping, or remains explicitly blocked on a stated decision.
- Any already-covered assertion quotes the corresponding accepted scope and identifies the resolving ticket/ledger row; similarity of names alone is insufficient.
- The first entry's desired change beyond the existing API is explicit before implementation is authorized; the other three entries acquire no invented product requirements.
- Entries are preserved, references resolve, no application/specification behavior changes, and no real student data is accessed.
- No check or user decision is claimed that was not performed or given.

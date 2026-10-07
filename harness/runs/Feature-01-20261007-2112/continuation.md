# Continuation of Feature-01-20261007-2033

- Failed run: `Feature-01-20261007-2033` (`harness/runs/Feature-01-20261007-2033/`, ended failed after three revise cycles)
- Baseline SHA: `9f14c3c6ea13a3b42cc4aae4e15338f124d9051b` (parent of first deliverable commit `d4e1116`)
- Prior head SHA: `a554fd74d8c7c3e1f32fb1e882335a8377bb8a46`
- Prior deliverable commits: `d4e1116e74e10315871354613a93631ad060fa91`, `0ea96c2b0af81433d413bf77cea55e815ad8c6e5`, `a554fd74d8c7c3e1f32fb1e882335a8377bb8a46`
- Ticket-revision commit: `b4991a77bdbde856209e4c52f1125082dd8c038f`
- Consecutive prior failed continuations of this ticket: 0

## Unresolved findings carried over (quoted from evaluation-3.md)

1. "`IdeaRules.optionalIdeaRatings` accepts an explicitly supplied JSON `null` because it skips `NullNode` values. An absent rating should be optional, but a present null should be rejected. Add a public API regression test that verifies rejection without changing the idea or adding a history entry."
   - Revised ticket: made explicit (scope bullet 2, mechanical checks, acceptance criterion 6). Still required.
2. "Older review records with `REQUEST_REVISION` or `DECLINE` and no `reviewType` are mapped to `UNKNOWN`. The frontend only renders ratings for `IDEA` reviews, hiding recorded ratings on existing idea-review entries. ... Add backward-compatible identification of legacy idea reviews ... and test rated legacy idea and specification entries."
   - Revised ticket: DROPPED. Legacy data is out of scope; no legacy-handling code or tests. Entries without a recognizable review type must only not break history display; full-spec reviews recorded by current code must render as before.

Earlier findings (focus after a status-filter change; focus for an idea beyond page 0) were addressed by commits 0ea96c2 and a554fd7; the revised ticket makes both cases explicit mechanical checks and acceptance criteria, so the Implementor must confirm they are met and tested at HEAD.
Prior verdicts and SPEC-change acceptances do not carry over; the revised ticket defines the requirements.

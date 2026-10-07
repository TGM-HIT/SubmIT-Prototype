## What was done

- First revision-pass findings are recorded here. No production code or SPEC.md files were changed; the implementation commit remains `372bb13f8e42916123f6bcd9ca0058b9428db799`.
- Attempted to prepare genuine Formal Applications state without backend changes: registered three students and a teacher through the public account flow, approved three projects through the idea/specification workflow, created a period, submitted student preferences while it was open, closed it, and started allocation.
- The period reached `ALLOCATED`, but the allocation-start response and allocation-run list serialized as generic `ObjectNode` metadata (boolean/type fields, with no usable run ID or assignment result). Without a run ID I could not inspect or finalize the allocation; finalization is what persists the project-group records. There was no existing test fixture or alternate public group-creation path available. A valid formal-application create request for one of the candidate projects returned HTTP 409, `An allocated project group is required.` This is the genuine blocker to rendering the conditional teacher decision dropdown, not an attempt to simulate that state.
- The real teacher Formal Applications route rendered zero selects at 1280×900 and 375×812. The narrow view had no horizontal overflow. No claim is made that the conditional decision dropdown was verified.
- On the live Allocation view, desktop Tab reached the period selector with the visible 3px focus outline. ArrowDown+Enter did not change its selected period in desktop headless Chrome. At 375×812 mobile emulation, ArrowDown+Enter changed the selected period from `CLOSED` to `ALLOCATED`.
- On the live Administration view, desktop Tab reached a role selector with the visible 3px focus outline. ArrowDown+Enter changed a student role selection from `STUDENT` to `TEACHER` both at desktop and 375×812 mobile emulation. I did not submit the role update.

## What was verified

- Live manual checks were performed against the locally running frontend and backend in Chrome 152 headless, using real registered accounts and application data.
- The Formal Applications creation endpoint returned the group-required conflict described above; allocation responses did not expose an ID/result usable to finalize a group.
- Allocation and Administration keyboard-selection outcomes and visible Tab focus are recorded above. No frontend source changed, so frontend automated checks were not rerun for this revision pass; the original handoff records the passing `npm run typecheck`, `npm test`, and `npm run build` results.

## What's uncertain

- Formal Applications' conditional decision dropdown remains unverified because this backend exposes no usable allocation-run identifier/result through the exercised API responses, preventing group finalization. I deliberately left backend behavior unchanged because this is a frontend-only ticket.
- The narrow-width checks used Chrome mobile emulation, not a physical mobile device. Desktop native-select behavior was observed in headless Chrome; Allocation option selection did not respond there, while Administration selection did.
- No other ticket scope was changed. The ticket is not marked done.

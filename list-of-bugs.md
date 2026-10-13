# List of bugs and ideas

## Allow an allocation period to be modified later on (at least the dates, maybe add additional projects).

Open question for this entry: `backend/SPEC.md` already has `PUT /api/application-periods/{id}`, which lets an administrator update dates or the project catalogue until the period is finalized. It is not yet clear what is missing (the administrator UI, behaviour in particular statuses, or something else). Decision needed before a ticket can be written:

- A (recommended): UI and API both allow editing dates and adding or removing catalogue projects until finalization. The ticket defines the effect in each status (for example moving the deadline of an `OPEN` period, and changes once the period is `CLOSED` or `ALLOCATED`). Existing allocation runs stay unchanged; later runs use the updated period.
- B: As A, but only while the period is `DRAFT` or `OPEN`.
- C: Something else (including dates only, without projects).

Re-opening a closed period by moving the deadline into the future is out of scope unless decided otherwise.

## Update auditor to be orchestrated.

## Allow learner for compliance checks

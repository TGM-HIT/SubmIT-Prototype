## What was done

- Investigated the repository for an existing, supported way to render a submitted group application. No production code, API behavior, SPEC.md file, run log, or plan was changed. The original implementation remains at `372bb13f8e42916123f6bcd9ca0058b9428db799`; this revision has no code commit.
- The only production path found for creating `project_group` records is completing and finalizing an allocation. `AllocationService.finalizeRun` creates those records from a completed run's assignments; `FormalApplicationRules.groupMembers` requires one before an application can be created. There is no separate public group-creation endpoint.
- Found no reusable application fixture, seed-data script, demo/dev seeder, or active testing profile. `application.properties` configures an in-memory H2 database with no SQL initialization; `BootstrapAdminConfiguration` only optionally creates an administrator. `compose.yaml` has no fixture or seed setup. The listed backend tests are not a running-app fixture: the grading and archive tests directly insert `project_group` records through `RecordStore`, isolated from the real frontend route. The planned Dev-01 testing-profile work is not implemented and in any case describes test accounts, not allocated groups.
- The previous live attempt through the normal student/project/application/allocation workflow remains the only observed end-to-end setup attempt. It reached `ALLOCATED`, but its allocation responses yielded no usable run ID or assignment result; submitting a formal application returned HTTP 409, “An allocated project group is required.” No alternative supported path was found that avoids the missing finalized group.

## What was verified

- Inspected application configuration and all files under `backend/src/main/resources` and `backend/src/test`; searched the backend/frontend sources, compose configuration, and developer documentation for profiles, seeders, fixtures, initialization hooks, and group/formal-application routes.
- Inspected `AllocationController`, `AllocationService`, `FormalApplicationController`, `FormalApplicationService`, `FormalApplicationRules`, and the Formal Application page. The teacher decision selector renders only when the loaded application is `SUBMITTED`; the backend requires an allocated group to author that application.
- The previous revision's recorded real-app checks found zero selects on the Formal Applications route at 1280×900 and 375×812 because no submitted group application existed. The route itself therefore did not verify the conditional selector, focus, or keyboard selection.
- Checked for an already-running local frontend/backend during this pass; neither `localhost:5173` nor `localhost:8080` was serving the app. No new live-route capture was possible without first producing valid group state.
- No source changed, so no frontend checks were rerun. No backend or API behavior was changed.

## What's uncertain

- The submitted Formal Applications selector remains unverified at desktop and narrow-mobile widths, including visible keyboard focus and actual selection. The requested acceptance check is still incomplete; this handoff does not claim otherwise.
- There is a mismatch between the prior observed allocation response and the current source: `AllocationService.response` adds an `id`, `status`, and `periodId` to the run response. The cause of the previously reported unusable `ObjectNode` response was not established. Resolving that runtime/API discrepancy or changing backend/API behavior is outside this frontend-only revision.
- I deliberately left all backend, frontend, plan, and run-log files unchanged; only this requested revision handoff was added. Bug-03 is not marked done.

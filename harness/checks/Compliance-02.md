# Compliance-02. Documentation and code parity

## Purpose

The specifications and guides describe behavior, endpoints, validation, and tooling that the code must implement. When code or tests change without the documents, or the reverse, the documents stop being a reliable contract. This check compares documented claims with what the code, tests, and configuration actually do.

## Inspect

- `backend/SPEC.md` against backend controllers, services, entities, validation annotations, security configuration, and seed or profile configuration under `backend/src/`.
- `frontend/SPEC.md` against routes, views, and components under `frontend/src/`.
- `docs/SPEC.md` rules that are enforced in code (for example the four-subject limit, the default class, public archive exclusions), against the service layer and response mappers.
- `backend/DEV-GUIDELINES.md` and `frontend/DEV-GUIDELINES.md` against `backend/build.gradle`, `frontend/package.json`, `frontend/scripts/`, `Makefile`, `compose.yaml`, and the seeded fixtures.
- Source of truth for behavior is the approved specification; the code is the thing being checked.

## Topics to check

1. Every endpoint described in `backend/SPEC.md` exists with the documented method, path, and role restrictions, and no implemented endpoint is missing from it.
2. Documented validation rules (limits, requiredness, uniqueness, trim and blank handling) are enforced by the code.
3. Documented authorization rules (who may read or mutate what) match the service-layer checks and the tests that cover them.
4. The public archive and student-facing responses expose none of the fields the specs declare private.
5. Documented lifecycle statuses and transitions match the enums and service logic.
6. Documented frontend routes, role-specific views, and states exist and are reachable by the documented roles.
7. Documented profiles, fixture accounts, and seed contents match the fixture code.
8. Documented commands, scripts, and prerequisites exist and match their definitions.
9. Every `SPEC` rule that states "must", "never", or an explicit limit has at least one test that asserts it; list those without one.

## Finding and fix rules

- A finding is a documented claim the code or tests contradict, or an implemented behavior that the documents never mention.
- Open a `Bug-` ticket when the code contradicts an approved specification. Open a `Dev-` ticket when only documentation or tooling descriptions are stale. Never change code, and never weaken the specification to match the code.
- Trivially fixable: a stale script name, path, or fixture name verified against the repository.

## Commands and verification

- Read-only: `grep`/`git ls-files` over the source, plus `git log` to see which side changed last.
- Do not run the full test suites. When a topic depends on test coverage, inspect the test sources and name the test class and method you found, or state that none exists.

## Report requirements

- A table with columns `Documented claim`, `Source (file and line)`, `Code or test evidence`, `Result`.

## Cadence

After every closed Feature or Bug ticket, and at least monthly.

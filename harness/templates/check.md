# <check-id>. <Short title of the recurring check>

## Purpose

<State what this check protects against and why it is run repeatedly. One short paragraph.>

## Inspect

- <List the exact documents, code locations, generated artifacts, and tools in scope.>
- <State the source-of-truth order for this check when it differs from `harness/roles/auditor.md`.>

## Topics to check

1. <Each topic is one concrete question with a verifiable answer. Every topic needs a result in the report, even when there is no discrepancy.>

## Finding and fix rules

- <What counts as a finding. Which defects are trivial and may be fixed directly. Which always need a ticket. Which are `decision needed`.>

## Commands and verification

- <Read-only commands the Auditor runs, with the working directory. Name any prerequisite (for example a running generation step). Do not require commands that modify tracked files other than the trivial documentation fixes.>

## Report requirements

- <Anything this check adds to the standard report headings, such as a matrix layout.>

## Cadence

<Suggested rerun interval and the events that should trigger an extra run (for example after a feature closes or after a SPEC change). The cadence is advice; runs are started manually.>

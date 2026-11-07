# Shared verification helpers

`verify.py` provides small, read-only Python 3 commands for recurring report and structured-data checks. Python is the common language because the selected operations need only the standard library, already available in the repository's Python-based checks. The archived JavaScript/YAML comparison was not promoted: its allowed API differences and counts are ticket-specific, so using Node and `js-yaml` would preserve historical policy rather than provide a reusable operation. No Node exception or third-party dependency is needed.

## Prerequisites and tests

- Python 3.10 or later (standard library only).
- No repository history, archive, credential, local environment file, Docker daemon, browser, network service, or Node installation is needed.
- Run `python3 scripts/verify.py --help` for the CLI and subcommand help.
- Run `python3 scripts/test_verify.py` or `make verification-tools-test` for the isolated self-tests. `make check` includes the same dependency-free tests alongside its existing project checks.

## `junit-summary`

Summarize one or more JUnit XML report files and validate optional caller-supplied test totals. These examples assume synthetic inputs in the two distinct `verification-example/run-alpha/` and `verification-example/run-beta/` folders:

```sh
python3 scripts/verify.py junit-summary \
  --expect-tests 2 --expect-failures 0 --expect-errors 0 --expect-skipped 0 \
  "verification-example/run-alpha/TEST-example.xml"

python3 scripts/verify.py junit-summary \
  --expect-tests 1 --expect-failures 0 --expect-errors 0 --expect-skipped 0 \
  "verification-example/run-beta/TEST-example.xml"
```

Pass multiple report paths as positional arguments to aggregate them. The command requires at least one regular, non-symlink XML file. It accepts a `testsuite` or `testsuites` root, counts test cases and nested failure, error, and skipped markers, and rejects malformed XML or suite counters inconsistent with the test cases. The report list is sorted by the supplied path for deterministic output. The JSON result has `reports` (one entry per input), aggregate `totals` (`tests`, `failures`, `errors`, and `skipped`), the effective `expectations`, and `expectations_match`.

By default, failures, errors, and skipped tests are expected to be zero. `--expect-tests` optionally asserts an exact test count; each of `--expect-failures`, `--expect-errors`, and `--expect-skipped` can explicitly set the corresponding expected count, including a nonzero count. An empty `testsuite` is valid only when its counters consistently say zero.

## `json-compare`

Compare two strict UTF-8 JSON files without printing their values. These examples use separate synthetic inputs from the two run-like folders above:

```sh
python3 scripts/verify.py json-compare \
  "verification-example/run-alpha/before.json" \
  "verification-example/run-alpha/after.json" \
  --expect equal

python3 scripts/verify.py json-compare \
  "verification-example/run-beta/before.json" \
  "verification-example/run-beta/after.json" \
  --expect different
```

`--expect` is required and accepts `equal` or `different`. JSON object key order does not matter; array order does. Booleans are distinct from numbers. A difference is reported as a JSON Pointer path and a kind (`added`, `removed`, or `changed`); values are never included. The JSON result contains `equal`, `expected`, `expectation_match`, and the ordered `differences` list. Duplicate object keys and non-standard `NaN`/infinity constants are rejected.

To check the negative exit path deliberately, repeat the alpha comparison with `--expect different`; because those synthetic documents are equal, it exits with code `1` and reports the expectation mismatch.

## Exit codes and safety

- `0`: inputs were read and all requested expectations matched.
- `1`: a valid comparison or report did not match the caller's expectation.
- `2`: missing, unreadable, malformed, symlink, non-regular, or otherwise invalid input; command-line usage errors also use argparse's exit code `2`.

Both operations are read-only: they do not execute caller input, spawn processes, open environment files, create evidence, or overwrite outputs. Inputs must be regular files and symlinks are refused. Diagnostics do not echo input contents. JSON comparison output includes caller-supplied file paths and changed object property names, so do not pass sensitive paths or confidential property names if those must not appear in output. The JUnit summary is about report contents only; it does not prove which command, runtime settings, source revision, or test process produced a report.

Use the repository's existing Gradle, frontend, Docker, preview, and `scripts/check-change-scope.py` tools for those workflows. These helpers are not a build/test runner, API contract policy, archive scanner, path-authorization gate, evidence copier, or substitute for inspecting report provenance.

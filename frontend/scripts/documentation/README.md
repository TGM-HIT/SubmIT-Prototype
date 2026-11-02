# Reproducible local guides

Run from the repository root:

```sh
make doc LANGUAGE=en
make doc LANGUAGE=de
```

Make, the existing Java/Gradle and Node setup, installed frontend dependencies, and Chrome/Chromium are required. Do not start either service first. The command owns a fresh testing backend, Vite and its Chrome profiles, and cleans them on success, failure or interruption. It refuses occupied ports and existing preview sessions. See [the development guide](../../DEV-GUIDELINES.md#full-lifecycle-local-documentation).

No credential setup is required: the runner defaults to the existing public testing-profile fixture password for its isolated local backend. `PREVIEW_PASSWORD` remains an optional environment override; an empty value uses the default. Never supply production credentials or include passwords in generated guides or audit evidence.

## Sources and outputs

- `locales/en.json` and `locales/de.json` contain the independently authored English and German guide prose, chapters, captions, meaningful alt text and synthetic example content.
- `scenario.mjs` is the single language-independent lifecycle and capture sequence. It uses the existing preview tool's real Chrome/CDP interactions, screenshots, error collector and process ownership. Actual control labels identify UI actions; translating application labels is not part of this tool.
- `guides.mjs` renders the locale's three role guides and entry points, checks local links/anchors, checks every required checkpoint and decodes PNG payloads with CRC/dimension checks at the declared desktop 1440 × 1000 viewport.
- `run.mjs` validates input before service startup, selects only this JVM's existing minimal testing fixtures, owns ignored staging, and promotes a fully validated output tree only after capture and cleanup succeed.
- `documentation.smoke.mjs` joins `npm run preview:test`, using the existing Node runner without new libraries. Mocked pixels/process failures are confined to bounded unit tests; the public generation command never uses mock screenshots.

`docs/user-guide/README.md`, `docs/user-guide/en/`, `docs/user-guide/de/`, the role guide Markdown files and their `images/*.png` are local, ignored outputs. Never add or force-add them. Temporary browser profiles, staging, reports and failure evidence remain in ignored `frontend/.preview/`, outside the generated tree. Reports omit passwords, sessions and bearer tokens.

## Story and preparation

The main project is created through student controls, then reviewed by a non-owner teacher, returned once, revised, resubmitted, approved for specification and separately accepted. Account/profile and administrative catalogue controls are exercised. Administration configures a period and supervisor; three students save rankings before a short future deadline. Bounded polling observes the unchanged scheduler's initial non-early allocation. The administrator inspects and finalizes a valid group, which submits its formal proposal. A teacher approves it, explicitly binds routine and final rubric versions through `/grading`, records an all-criteria routine subset, a reasoned final-criterion revision and a new final criterion, then records group/metric inputs and separately confirms overall final grades with rationale. Members upload synthetic files privately; a teacher reviews the public subset and completes the project. A fresh anonymous browser verifies the archive and approved downloads, excluding a private sentinel.

The report identifies all non-browser preparation:

1. Two supporting teacher-authored, accepted projects, through existing HTTP create/submit/specification/review operations with a different reviewing teacher. They are ranking alternatives, never substituted for the main idea.
2. A valid initial rubric before `/grading` reads published-version summaries. Administration publishes the next immutable version through the UI; the teacher then selects both project phases explicitly in the grading page. No global-active fallback is used.
3. Initial ranking records for each of the three students before `/applications/me` reads, followed by actual UI reordering and ranking saves. The existing create operation persists these records as `SUBMITTED`, not a fictitious `DRAFT` application state; the report records that status. No new application lifecycle is introduced.
4. Initial final-grade records before concurrent grading reads, followed by actual teacher UI confirmation and explicit rationale. No calculation formula is configured or invented.

No direct database mutation, new backend endpoint, scheduler override, ordinary fixture change or permission bypass is used.

## Another language

1. Add `locales/<language>.json` using an explicit two-letter language identifier (optionally a region, such as `pt-BR`). Supply the same complete shape as `en` or `de`, with independently authored title, overview, role-guide introductions, chapter text, image captions/alt text and synthetic examples. Keep the stable checkpoint keys, role filenames and cross-guide anchor/link contract.
2. Translate the content and example files, not `scenario.mjs`. UI control names must remain the application's actual labels, with explanations in the documentation language. Do not add credentials or real personal data.
3. Run `make doc LANGUAGE=<language>`. The shared scenario captures that locale's synthetic example and renders its guides and screenshots exclusively under `docs/user-guide/<language>/`. Successful generation retains previously generated other languages; failed generation leaves the whole prior set intact. Missing/incomplete locales fail without falling back to German.
4. Run `npm run preview:test` from `frontend/`. Its temporary second-language configuration proves shared capture orchestration and separate output paths; it is not a second published translation.
5. Run the real root command twice for repeatability and inspect all generated guides/images. Compare stable checkpoint/reference inventories in `frontend/.preview/documentation-report.json`, not random IDs, timestamps or byte-identical screenshots. Review the actual language and UI framing manually.

On normal errors or Ctrl-C, owned processes and temporary profiles are removed. The previous guide set remains intact. See the development guide for crash-only stale-lock recovery. Never remove another run's session, lock or processes.

For harness evidence, optionally set `DOCUMENTATION_REPORT` to an ignored `harness/runs/<run>/evidence/<name>.json` path when invoking the root command. This additionally retains the credential-free stage/actor/record/checkpoint report there; it does not move generated guide images out of the ignored guide tree.

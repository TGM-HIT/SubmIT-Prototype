import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { execFileSync, spawnSync } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';
import test from 'node:test';
import { REPORT, SPECS, STATUSES } from './check-spec-test-traceability.mjs';

const script = fileURLToPath(new URL('./check-spec-test-traceability.mjs', import.meta.url));
const digest = (s) => createHash('sha256').update(s).digest('hex');
const java = `class FixtureTest {
  @Test
  void firstCase() {
    createAuthorisedFixture();
    assertEquals("present", lookup());
    assertTrue(hasRole());
  }
  @Test
  void secondCase() {
    createSecondFixture();
    assertTrue(isVisible());
  }
}
`;
const vitest = `it('renders the expected row', () => {
  const rows = makeRows();
  expect(rows).toEqual(['row']);
});
test('another row', () => {
  const second = makeSecond();
  expect(second).toBe(true);
});
`;
const testPath = 'backend/src/test/example/FixtureTest.java';
const vitestPath = 'frontend/src/example/Fixture.test.ts';
const logPath = 'harness/runs/Fixture-1/evidence/passing.log';
const sourceTexts = [
  'A student must be authorized.\nRole membership is checked before access.\n',
  'A teacher may review a submission.\nAn owned obligation.\nA demonstrated failure.\n',
  'Interface description.\nA teacher must not review their own idea.\nMigration is out of scope.\n',
];
const partitions = [
  { id: 'shared', kind: 'classification', sourcePath: SPECS[0], historyPrefix: 'S' },
  { id: 'backend', kind: 'classification', sourcePath: SPECS[1], historyPrefix: 'B' },
  { id: 'frontend', kind: 'classification', sourcePath: SPECS[2], historyPrefix: 'F' },
  { id: 'wave-1', kind: 'evidence', recordIds: ['C-1', 'C-2'] },
];
const report = '| S001.01 — old shared |\n| B001.01 — old backend |\n| F001.01 — old frontend |\n';
function write(root, file, content) {
  fs.mkdirSync(path.dirname(path.join(root, file)), { recursive: true });
  fs.writeFileSync(path.join(root, file), content);
}
function git(root, args) {
  return execFileSync('git', args, { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
}
function fixture(t) {
  const root = fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(), 'spec-traceability-')));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  SPECS.forEach((file, i) => write(root, file, sourceTexts[i]));
  write(root, testPath, java);
  write(root, vitestPath, vitest);
  write(root, REPORT, report);
  write(root, 'harness/tickets/Dev-62.md', '# Review owner\n');
  // Synthetic only: real excluded paths are never used by the fixture.
  write(root, 'bugs-and-ideas.md', 'ROOT SENTINEL MUST NOT BE OPENED');
  write(root, 'docs/user-guide/sentinel.md', 'DIRECTORY SENTINEL MUST NOT BE OPENED');
  git(root, ['init', '-q']);
  git(root, ['add', '--', ...SPECS, testPath, vitestPath]);
  git(root, ['-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', '-c', 'commit.gpgsign=false', 'commit', '-qm', 'Fixture source and test identity']);
  const revision = git(root, ['rev-parse', 'HEAD']);
  const log = `Revision: ${revision}\nCommand: fixture-runner firstCase and renders the expected row\nExit code: 0\n`;
  write(root, logPath, log);
  const execution = { revision, command: 'fixture-runner firstCase and renders the expected row',
    exitCode: 0, logPath, sha256: digest(log) };
  const javaEvidence = { kind: 'java', file: testPath, locator: 'firstCase', revision,
    setupExcerpt: 'createAuthorisedFixture();', assertionExcerpt: 'assertEquals("present", lookup());',
    rationale: 'This assertion checks authorization for this clause.', execution };
  const vitestEvidence = { kind: 'vitest', file: vitestPath, locator: 'renders the expected row', revision,
    setupExcerpt: 'const rows = makeRows();', assertionExcerpt: "expect(rows).toEqual(['row']);",
    rationale: 'This assertion checks the visible review row.', execution };
  function record(id, n, line, classification, extra) {
    return { id, sourcePath: SPECS[n], sourceRevision: revision, startLine: line, endLine: line,
      excerptOffset: 0, excerpt: sourceTexts[n].split('\n')[line - 1], sourceOrder: [line, line, 0],
      atom: `Single clause ${id}`, classification, rationale: `Source review rationale for ${id}`, ...extra };
  }
  const f = { root, config: { version: 1, sourceRevision: revision, partitions: structuredClone(partitions) },
    records: [
      record('C-1', 0, 1, 'normative', { status: 'covered', evidence: javaEvidence }),
      record('C-1B', 0, 2, 'normative', { status: 'covered',
        evidence: { ...javaEvidence, assertionExcerpt: 'assertTrue(hasRole());', rationale: 'A distinct role-membership assertion supports this distinct clause.' } }),
      record('C-2', 1, 1, 'normative', { status: 'covered', evidence: vitestEvidence }),
      record('T-1', 1, 2, 'normative', { status: 'delegated', reason: 'Separately owned obligation.', finding: 'harness/tickets/Dev-62.md' }),
      record('B-1', 1, 3, 'normative', { status: 'behavioral-failure', reason: 'Fixture finding describes a demonstrated failure.', finding: '#finding-failure' }),
      record('D-1', 2, 1, 'descriptive', { reason: 'Context only, no normative obligation.' }),
      record('N-1', 2, 2, 'normative', { status: 'unresolved-test-gap', reason: 'Reviewed test gap.', finding: '#finding-gap' }),
      record('D-2', 2, 3, 'scope-exclusion', { reason: 'Explicitly excludes migration.' }),
    ],
    history: [
      { legacyId: 'S001.01', disposition: 'split', currentIds: ['C-1', 'C-1B'], rationale: 'Split independently stated clauses.', currentTextJustification: 'The current shared source states both authorization and membership.' },
      { legacyId: 'B001.01', disposition: 'matched', currentIds: ['C-2'], rationale: 'Same review clause.', currentTextJustification: 'The current backend review clause remains.' },
      { legacyId: 'F001.01', disposition: 'descriptive', currentIds: ['D-1'], rationale: 'Context is not an obligation.', currentTextJustification: 'Current frontend text describes the interface.' },
    ] };
  save(f);
  return f;
}
function save(f, overrideCounts) {
  const counts = {
    classifications: Object.fromEntries(['normative', 'descriptive', 'scope-exclusion'].map((key) => [key, f.records.filter((r) => r.classification === key).length])),
    statuses: Object.fromEntries(STATUSES.map((key) => [key, f.records.filter((r) => r.classification === 'normative' && r.status === key).length])),
    historical: f.history.length,
  };
  const fence = (label, rows) => `\`\`\`${label}\n${rows.map((r) => JSON.stringify(r)).join('\n')}\n\`\`\`\n`;
  write(f.root, 'docs/spec-test-traceability.md', '# Synthetic inventory\n\n'
    + '## Finding gap\nReviewed gap.\n## Finding failure\nDemonstrated fixture failure.\n## Queue remaining\nUnreviewed owner queue.\n'
    + fence('traceability-config-jsonl', [f.config]) + fence('traceability-jsonl', f.records)
    + fence('traceability-history-jsonl', f.history) + fence('traceability-counts-jsonl', [overrideCounts ?? counts]));
}
function run(f, partition, guard = false) {
  // Child-process exit is checked, not merely a caught rejection reported as success.
  const runner = `import {verify} from ${JSON.stringify(pathToFileURL(script).href)};
    try { verify(${JSON.stringify(f.root)}, ${JSON.stringify({ partition, historyDigest: digest(report) })}); }
    catch(e) { console.error('spec-test-traceability: '+e.message); process.exitCode=1; }`;
  const args = [];
  if (guard) {
    const guardPath = path.join(f.root, 'guard.mjs');
    write(f.root, 'guard.mjs', `
      import fs from 'node:fs';
      import {syncBuiltinESMExports} from 'node:module';
      const forbidden = p => String(p).includes('/docs/user-guide') || String(p).endsWith('/bugs-and-ideas.md');
      for (const name of ['readFileSync','readFile','readdirSync','readdir','opendirSync','opendir','openSync','open']) {
        const original=fs[name];
        fs[name]=function(p,...args) {
          if(forbidden(p)) throw Error('Forbidden fixture open/traversal: '+p);
          if(name==='readFileSync' && String(p).startsWith(${JSON.stringify(f.root)})) console.error('READ '+p);
          return original.call(this,p,...args);
        };
      }
      for (const name of ['readFile','readdir','opendir','open']) {
        const original=fs.promises[name];
        fs.promises[name]=function(p,...args) {
          if(forbidden(p)) throw Error('Forbidden fixture async open/traversal: '+p);
          return original.call(this,p,...args);
        };
      }
      syncBuiltinESMExports();
    `);
    args.push('--import', pathToFileURL(guardPath).href);
  }
  return spawnSync(process.execPath, [...args, '--input-type=module', '-e', runner], { encoding: 'utf8' });
}
function passes(f, partition, guard = false) {
  save(f);
  const result = run(f, partition, guard);
  assert.equal(result.status, 0, result.stderr);
  return result;
}
function rejects(f, phrase, partition, overrideCounts) {
  save(f, overrideCounts);
  const result = run(f, partition);
  assert.equal(result.status, 1, result.stdout + result.stderr);
  assert.match(result.stderr, phrase);
  return result;
}
function unreviewed(f, index = 0, queue = false) {
  const r = f.records[index];
  delete r.evidence;
  r.status = 'evidence-not-yet-reviewed';
  r.reason = 'No evidence judgment has been made.';
  if (queue) r.queue = { id: 'Q-1', owner: 'Dev-62 coordinator for Ticket Author routing',
    link: '#queue-remaining', reason: 'Route this frozen clause in a bounded wave.' };
  else r.owner = 'harness/tickets/Dev-62.md';
}

function commitTests(f) {
  git(f.root, ['add', '--', testPath, vitestPath]);
  git(f.root, ['-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', '-c', 'commit.gpgsign=false', 'commit', '-qm', 'Fixture test delta']);
  const revision = git(f.root, ['rev-parse', 'HEAD']);
  const command = 'fixture-runner firstCase and renders the expected row';
  const log = `Revision: ${revision}\nCommand: ${command}\nExit code: 0\n`;
  write(f.root, logPath, log);
  for (const r of f.records.filter((row) => row.evidence)) {
    r.evidence.revision = revision;
    r.evidence.execution = { revision, command, exitCode: 0, logPath, sha256: digest(log) };
  }
}

test('T1/T4: atomic records, reused exact tests, all linked statuses, non-normative totals and bounded reads', (t) => {
  const f = fixture(t);
  const result = passes(f, undefined, true);
  assert.match(result.stdout, /STRICT final structural pass/);
  const summary = JSON.parse(result.stdout.slice(0, result.stdout.indexOf('\nSTRICT')));
  assert.equal(summary.classifications.normative, 6);
  assert.equal(summary.statuses.covered, 3);
  assert.equal(summary.statuses.delegated, 1);
  assert.equal(summary.statuses['behavioral-failure'], 1);
  assert.equal(summary.statuses['unresolved-test-gap'], 1);
  for (const file of [...SPECS, testPath, vitestPath, REPORT, logPath, 'docs/spec-test-traceability.md']) assert.ok(result.stderr.includes(`READ ${path.join(f.root, file)}`), file);
  assert.doesNotMatch(result.stderr, /SENTINEL|Forbidden fixture/);
});

test('T2: duplicate/grouped IDs, aliases, source order and exact ranges', (t) => {
  const cases = [
    [(f) => { f.records[1].id = 'C-1'; }, /Duplicate record id/],
    [(f) => { f.records[0].id = 'C-1~C-2'; }, /Invalid\/grouped/],
    [(f) => { f.records[0].id = 'S001.01-S003.01'; }, /Invalid\/grouped/],
    [(f) => { f.records[0].clauseIds = ['C-1', 'C-2']; }, /Invalid\/grouped/],
    [(f) => { f.records[0].aliases = ['C-2']; f.records[0].aliasRationale = 'Correction'; }, /ambiguous\/duplicate alias/],
    [(f) => { f.records[0].sourceOrder = [2, 2, 0]; }, /sourceOrder must equal/],
    [(f) => { f.records.reverse(); }, /not in source order/],
    [(f) => { f.records[0].excerpt = 'stale text'; }, /source excerpt does not exactly match/],
    [(f) => { f.records[0].startLine = 2; f.records[0].endLine = 2; }, /source excerpt does not exactly match/],
    [(f) => { f.records[0].excerptOffset = 1; }, /source excerpt does not exactly match/],
    [(f) => { f.records[0].sourceRevision = 'a'.repeat(40); }, /source revision differs/],
    [(f) => { f.config.sourceRevision = 'HEAD'; }, /full sourceRevision/],
  ];
  for (const [mutate, phrase] of cases) { const f = fixture(t); mutate(f); rejects(f, phrase); }
  const alias = fixture(t);
  alias.records[0].aliases = ['OLD-1'];
  alias.records[0].aliasRationale = 'An explicit correction preserves the old locator.';
  passes(alias);
});

test('T2: real Git source and test identity, not merely SHA-shaped strings', (t) => {
  const changedSource = fixture(t);
  write(changedSource.root, SPECS[0], sourceTexts[0].replace('student', 'learner'));
  rejects(changedSource, /current bytes differ from captured revision/);
  const changedTest = fixture(t);
  write(changedTest.root, testPath, java + '// changed test\n');
  rejects(changedTest, /current bytes differ from captured revision/);
  const missingRevision = fixture(t);
  missingRevision.records[0].evidence.revision = 'a'.repeat(40);
  rejects(missingRevision, /unavailable commit\/blob/);
});

test('T2: method/title identity and wrong-method assertions', (t) => {
  const cases = [
    [(f) => { f.records[0].evidence.locator = 'doesNotExist'; }, /exact unambiguous test method\/title not found/],
    [(f) => { f.records[2].evidence.locator = 'missing title'; }, /exact unambiguous test method\/title not found/],
    [(f) => { f.records[0].evidence.assertionExcerpt = 'assertTrue(isVisible());'; }, /assertionExcerpt is absent/],
    [(f) => { f.records[2].evidence.assertionExcerpt = 'expect(second).toBe(true);'; }, /assertionExcerpt is absent/],
    [(f) => { f.records[0].evidence.setupExcerpt = 'createSecondFixture();'; }, /setupExcerpt is absent/],
    [(f) => { delete f.records[0].evidence.rationale; }, /clause-specific rationale/],
    [(f) => { f.records[1].evidence.rationale = f.records[0].evidence.rationale; }, /reused test requires a distinct/],
  ];
  for (const [mutate, phrase] of cases) { const f = fixture(t); mutate(f); rejects(f, phrase); }
});

test('T2: lexical test identity excludes comments, ambiguous titles and absent annotations', (t) => {
  const commentedJava = fixture(t);
  write(commentedJava.root, testPath, `/* ${java} */\nclass FixtureTest {}`);
  commitTests(commentedJava);
  rejects(commentedJava, /exact unambiguous test method\/title not found/);
  const unannotated = fixture(t);
  write(unannotated.root, testPath, java.replaceAll('@Test', ''));
  commitTests(unannotated);
  rejects(unannotated, /exact unambiguous test method\/title not found/);
  const commentedVitest = fixture(t);
  write(commentedVitest.root, vitestPath, `/* ${vitest} */`);
  commitTests(commentedVitest);
  rejects(commentedVitest, /exact unambiguous test method\/title not found/);
  const duplicateTitle = fixture(t);
  write(duplicateTitle.root, vitestPath, vitest + vitest);
  commitTests(duplicateTitle);
  rejects(duplicateTitle, /exact unambiguous test method\/title not found/);
  const skipped = fixture(t);
  write(skipped.root, vitestPath, vitest.replace("it('renders", "it.skip('renders"));
  commitTests(skipped);
  rejects(skipped, /exact unambiguous test method\/title not found/);
});

test('T2: execution provenance is tied to actual reviewed revision and retained bytes', (t) => {
  const cases = [
    [(f) => { delete f.records[0].evidence.execution; }, /passing execution provenance/],
    [(f) => { f.records[0].evidence.execution.revision = 'b'.repeat(40); }, /passing execution provenance/],
    [(f) => { f.records[0].evidence.execution.exitCode = 1; }, /passing execution provenance/],
    [(f) => { f.records[0].evidence.execution.sha256 = '0'.repeat(64); }, /log digest\/identity\/result mismatch/],
    [(f) => { write(f.root, logPath, 'Command: changed\nExit code: 0\n'); }, /log digest\/identity\/result mismatch/],
  ];
  for (const [mutate, phrase] of cases) { const f = fixture(t); mutate(f); rejects(f, phrase); }
});

test('T2: invalid combinations, missing durable owners/findings and forbidden link targets', (t) => {
  const cases = [
    [(f) => { f.records[0].status = 'unknown'; }, /invalid normative status/],
    [(f) => { f.records[5].status = 'covered'; }, /non-normative records/],
    [(f) => { delete f.records[5].reason; }, /non-normative records/],
    [(f) => { f.records[0].finding = '#finding-gap'; }, /incompatible non-covered fields/],
    [(f) => { delete f.records[6].finding; }, /Missing durable owner\/finding/],
    [(f) => { f.records[6].finding = '#absent'; }, /Invalid durable/],
    [(f) => { f.records[3].finding = '#finding-gap'; }, /Invalid durable owner ticket/],
    [(f) => { unreviewed(f); delete f.records[0].owner; }, /exactly one owner ticket/],
    [(f) => { unreviewed(f, 0, true); f.records[0].owner = ''; }, /exactly one owner ticket/],
    [(f) => { unreviewed(f); f.records[0].finding = '#finding-gap'; }, /exactly one owner ticket/],
    [(f) => { unreviewed(f); f.records[0].owner = 'harness/tickets/Dev-9999.md'; }, /Missing input/],
    [(f) => { unreviewed(f, 0, true); f.records[0].queue.owner = 'someone'; }, /invalid Dev-62 bounded-review queue/],
    [(f) => { f.records[6].finding = 'bugs-and-ideas.md'; }, /Invalid durable/],
    [(f) => { f.records[0].evidence.file = 'backend/src/test/../../../../docs/user-guide/sentinel.md'; }, /allowed Java\/Vitest test path/],
  ];
  for (const [mutate, phrase] of cases) { const f = fixture(t); mutate(f); rejects(f, phrase, 'shared'); }
});

test('T2: exact historical ID set, mappings, cardinalities and counts', (t) => {
  const cases = [
    [(f) => { f.history[0].legacyId = 'S999.99'; }, /Unmatched\/duplicate historical ID/],
    [(f) => { f.history[1].legacyId = f.history[0].legacyId; }, /Unmatched\/duplicate historical ID/],
    [(f) => { f.history[0].currentIds = ['MISSING']; }, /unresolved current record/],
    [(f) => { f.history[0].currentIds = ['C-1']; }, /invalid split\/matched cardinality/],
    [(f) => { f.history[0].disposition = 'merged'; }, /merged mapping needs explicit peer/],
    [(f) => { f.history[2].currentIds = ['C-1']; }, /conflicts with current classification/],
    [(f) => { delete f.history[0].currentTextJustification; }, /justification/],
    [(f) => { write(f.root, REPORT, report.replace('S001.01', 'S999.99')); }, /Archived report SHA-256 mismatch/],
  ];
  for (const [mutate, phrase] of cases) { const f = fixture(t); mutate(f); rejects(f, phrase); }
  const f = fixture(t);
  rejects(f, /Declared counts differ/, undefined, { classifications: {}, statuses: {}, historical: 999 });
});

test('T1: explicit many-to-one historical merges and obsolete current-text dispositions', (t) => {
  const merged = fixture(t);
  for (const h of merged.history.slice(0, 2)) {
    h.disposition = 'merged';
    h.currentIds = ['C-2'];
    h.rationale = 'These synthetic old rows are duplicate descriptions of the same review clause.';
    h.currentTextJustification = 'The current teacher review clause is the canonical retained obligation.';
  }
  passes(merged);
  const obsolete = fixture(t);
  obsolete.history[2] = { legacyId: 'F001.01', disposition: 'obsolete', currentIds: ['D-2'],
    rationale: 'The old synthetic migration assumption is retired.',
    currentTextJustification: 'The current source explicitly excludes migration.' };
  passes(obsolete);
});

test('T3: staged unreviewed has no covered/missing/defective claim, strict exits 1 for the intended predicate', (t) => {
  for (const queue of [false, true]) {
    const f = fixture(t);
    unreviewed(f, 0, queue);
    const staged = passes(f, 'shared');
    assert.match(staged.stdout, /STAGED structural pass.*NOT final validation or coverage/);
    assert.match(staged.stdout, /"evidence-not-yet-reviewed": 1/);
    assert.match(staged.stdout, /"covered": 2/);
    assert.match(staged.stdout, /"unresolved-test-gap": 1/);
    assert.match(staged.stdout, /"behavioral-failure": 1/);
    const strict = rejects(f, /STRICT final rejection/);
    assert.match(strict.stdout, /"historical": 0/);
    assert.match(strict.stdout, /"unreviewed": 1/);
    t.diagnostic(`Synthetic ${queue ? 'queue' : 'ticket'} ownership: staged child exit ${staged.status}; strict child exit ${strict.status}; intended STRICT final rejection, complete source/history and unreviewed=1.`);
    passes(f, 'wave-1');
  }
});

test('T3: staged classification is exact, global outstanding remains visible, malformed outside is rejected', (t) => {
  const outside = fixture(t);
  outside.records = outside.records.filter((r) => !['T-1', 'B-1'].includes(r.id));
  outside.history = outside.history.filter((h) => h.legacyId !== 'B001.01');
  const staged = passes(outside, 'shared');
  assert.match(staged.stdout, /"B": 1/);
  assert.match(staged.stdout, /"characters": [1-9]/);
  assert.doesNotMatch(staged.stdout, /STRICT final structural pass/);
  rejects(outside, /STRICT final rejection/);
  rejects(outside, /requires complete global source\/history/, 'wave-1');
  outside.records.find((r) => r.id === 'N-1').status = 'invalid-outside';
  rejects(outside, /invalid normative status/, 'shared');

  const omittedSource = fixture(t);
  omittedSource.records.splice(1, 1);
  omittedSource.history[0].currentIds = ['C-1'];
  omittedSource.history[0].disposition = 'matched';
  rejects(omittedSource, /Staged partition shared incomplete: [1-9]/, 'shared');
  rejects(omittedSource, /STRICT final rejection/);
  const omittedHistory = fixture(t);
  omittedHistory.history.shift();
  rejects(omittedHistory, /Staged partition shared incomplete: 0 source characters.*1 historical IDs/, 'shared');
  rejects(omittedHistory, /STRICT final rejection/);
  const invalidPartition = fixture(t);
  invalidPartition.config.partitions[0].sourcePath = SPECS[2];
  rejects(invalidPartition, /Missing exact classification partition/,'shared');
  const missingEvidence = fixture(t);
  missingEvidence.config.partitions[3].recordIds.push('MISSING');
  rejects(missingEvidence, /invalid exact evidence partition IDs/, 'wave-1');
  const nonNormativeEvidence = fixture(t);
  nonNormativeEvidence.config.partitions[3].recordIds.push('D-1');
  rejects(nonNormativeEvidence, /invalid exact evidence partition IDs/, 'wave-1');
  const duplicatedEvidence = fixture(t);
  duplicatedEvidence.config.partitions[3].recordIds.push('C-1');
  rejects(duplicatedEvidence, /invalid exact evidence partition IDs/, 'wave-1');
  const duplicateQueue = fixture(t);
  unreviewed(duplicateQueue, 0, true);
  unreviewed(duplicateQueue, 1, true);
  rejects(duplicateQueue, /duplicate bounded-review queue ID/, 'shared');
  rejects(fixture(t), /Unknown declared partition/, 'not-declared');
});

test('T4: traversal and symlink escapes are rejected before opening excluded synthetic sentinels', (t) => {
  const escape = fixture(t);
  escape.records[0].evidence.file = 'backend/src/test/../../../../bugs-and-ideas.md';
  save(escape);
  const result = run(escape, 'shared', true);
  assert.equal(result.status, 1);
  assert.doesNotMatch(result.stderr, /Forbidden fixture open|ROOT SENTINEL/);
  const symlink = fixture(t);
  fs.unlinkSync(path.join(symlink.root, testPath));
  fs.symlinkSync(path.join(symlink.root, 'docs/user-guide/sentinel.md'), path.join(symlink.root, testPath));
  save(symlink);
  const linked = run(symlink, 'shared', true);
  assert.equal(linked.status, 1);
  assert.match(linked.stderr, /Symlink input forbidden/);
  assert.doesNotMatch(linked.stderr, /Forbidden fixture open|DIRECTORY SENTINEL/);
});

test('T5: production CLI has no pin override or silent staged bypass; npm wiring and guidance match', (t) => {
  const f = fixture(t);
  for (const args of [['--staged'], ['--partition', 'shared'], ['--ignore-errors'], ['--history-digest', digest(report)]]) {
    const result = spawnSync(process.execPath, [script, '--root', f.root, ...args], { encoding: 'utf8' });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /requires both|Unknown\/incomplete argument/);
  }
  const pinned = spawnSync(process.execPath, [script, '--root', f.root], { encoding: 'utf8' });
  assert.equal(pinned.status, 1);
  assert.match(pinned.stderr, /Archived report SHA-256 mismatch/);
  const frontend = path.resolve(path.dirname(script), '..');
  const pkg = JSON.parse(fs.readFileSync(path.join(frontend, 'package.json'), 'utf8'));
  assert.equal(pkg.scripts.test, 'npm run test:spec-traceability && vitest run');
  assert.equal(pkg.scripts['test:spec-traceability'], 'node --test scripts/spec-test-traceability.smoke.mjs');
  assert.equal(pkg.scripts['check:spec-traceability'], 'node scripts/check-spec-test-traceability.mjs');
  assert.equal(pkg.scripts['check:spec-traceability:staged'], 'node scripts/check-spec-test-traceability.mjs --staged');
  const doc = fs.readFileSync(path.join(frontend, '../docs/spec-test-traceability.md'), 'utf8');
  const guide = fs.readFileSync(path.join(frontend, 'DEV-GUIDELINES.md'), 'utf8');
  for (const command of ['npm run check:spec-traceability', 'npm run check:spec-traceability:staged -- --partition shared',
    'npm run test:spec-traceability']) assert.ok(doc.includes(command), command);
  for (const phrase of ['structural', 'evidence-not-yet-reviewed', 'strict', 'staged']) assert.ok(guide.includes(phrase), phrase);
});

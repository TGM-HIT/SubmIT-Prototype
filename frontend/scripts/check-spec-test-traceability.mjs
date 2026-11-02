#!/usr/bin/env node

// Bounded structural validation, not normative extraction or proof of assertion sufficiency.
import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

export const SPECS = ['docs/SPEC.md', 'backend/SPEC.md', 'frontend/SPEC.md'];
export const STATUSES = ['covered', 'unresolved-test-gap', 'behavioral-failure', 'delegated',
  'evidence-not-yet-reviewed'];
const CLASSES = ['normative', 'descriptive', 'scope-exclusion'];
export const REPORT = 'harness/runs/archive/Compliance-02-20261101-1603/report.md';
// The CLI always pins the preserved report, never a self-declared row count.
const REPORT_SHA256 = '017c1718f9b8ad64e42aaa7294f16826019e57ed80351091a93e7faf2f40e13c';
const fail = (message) => { throw new Error(message); };
const text = (value) => typeof value === 'string' && value.trim().length > 0;
const sha = (value) => createHash('sha256').update(value).digest('hex');
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const singleId = (id) => typeof id === 'string' && /^[A-Za-z][A-Za-z0-9]*(?:[.-][A-Za-z0-9]+)*$/.test(id)
  && !/[SBF]\d{3}\.\d{2}-[SBF]?\d{3}\.\d{2}/.test(id);
const unique = (values) => new Set(values).size === values.length;
const fullRevision = (value) => typeof value === 'string' && /^[a-f0-9]{40}$/.test(value);

function blocks(markdown, label) {
  const marker = `\`\`\`${label}\n`;
  if (markdown.split(marker).length !== 2) fail(`Expected exactly one ${label} fence`);
  const tail = markdown.split(marker)[1];
  const end = tail.indexOf('\n```');
  if (end < 0) fail(`Unclosed ${label} fence`);
  return tail.slice(0, end).split('\n').filter((line) => line.trim()).map((line, index) => {
    try { return JSON.parse(line); } catch { return fail(`${label}:${index + 1}: invalid JSON`); }
  });
}

function singleton(markdown, label) {
  const rows = blocks(markdown, label);
  if (rows.length !== 1) fail(`${label} requires exactly one object`);
  return rows[0];
}

function safePath(root, relative) {
  if (typeof relative !== 'string' || relative.split('/').some((part) => !part || part === '.' || part === '..')
      || relative.includes('\\') || path.isAbsolute(relative)
      || relative === 'bugs-and-ideas.md' || relative.startsWith('docs/user-guide/')) {
    fail(`Forbidden/non-canonical input path: ${relative}`);
  }
  // Reject symlinks before opening them, including directory components.
  let current = root;
  for (const component of relative.split('/')) {
    current = path.join(current, component);
    if (!fs.existsSync(current)) fail(`Missing input: ${relative}`);
    if (fs.lstatSync(current).isSymbolicLink()) fail(`Symlink input forbidden: ${relative}`);
  }
  return current;
}

function read(root, relative) {
  return fs.readFileSync(safePath(root, relative), 'utf8');
}

function gitBlob(root, revision, file) {
  if (!fullRevision(revision)) fail(`${file}: revision must be a full captured Git SHA`);
  try {
    const resolved = execFileSync('git', ['rev-parse', '--verify', `${revision}^{commit}`],
      { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
    if (resolved !== revision) fail('not a commit');
    return execFileSync('git', ['show', `${revision}:${file}`],
      { cwd: root, encoding: 'utf8', maxBuffer: 8 * 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe'] });
  } catch {
    return fail(`${file}: unavailable commit/blob at ${revision}`);
  }
}

function currentAtRevision(root, file, revision) {
  const current = read(root, file);
  if (current !== gitBlob(root, revision, file)) fail(`${file}: current bytes differ from captured revision ${revision}`);
  return current;
}

// Java lexical masking keeps offsets/braces while excluding comments and strings.
function javaMask(source) {
  return source.replace(/"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\n]*|\/\*[\s\S]*?\*\//g,
    (token) => token.replace(/[^\n]/g, ' '));
}

function javaBody(source, locator) {
  if (!/^[A-Za-z_$][\w$]*$/.test(locator)) return null;
  const mask = javaMask(source);
  const escaped = locator.replace(/\$/g, '\\$');
  const matches = [...mask.matchAll(new RegExp(`\\bvoid\\s+${escaped}\\s*\\([^{};]*\\)\\s*(?:throws\\s+[^{};]+)?\\{`, 'g'))];
  if (matches.length !== 1) return null;
  const match = matches[0];
  const previousBoundary = Math.max(mask.lastIndexOf('}', match.index), mask.lastIndexOf('{', match.index),
    mask.lastIndexOf(';', match.index));
  if (!/@(?:Test|ParameterizedTest|RepeatedTest|TestFactory)\b/.test(mask.slice(previousBoundary + 1, match.index))) return null;
  const open = mask.indexOf('{', match.index);
  let depth = 1;
  for (let i = open + 1; i < mask.length; i += 1) {
    if (mask[i] === '{') depth += 1;
    if (mask[i] === '}' && --depth === 0) return source.slice(open + 1, i);
  }
  return null;
}

function vitestBody(source, locator, file) {
  const ast = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true);
  if (ast.parseDiagnostics.length) fail(`${file}: test source does not parse`);
  const matches = [];
  function visit(node) {
    if (ts.isCallExpression(node) && ts.isIdentifier(node.expression)
        && ['it', 'test'].includes(node.expression.text)) {
      const [title, callback] = node.arguments;
      if (title && ts.isStringLiteralLike(title) && title.text === locator && callback
          && (ts.isArrowFunction(callback) || ts.isFunctionExpression(callback)) && ts.isBlock(callback.body)) {
        matches.push(source.slice(callback.body.getStart(ast) + 1, callback.body.end - 1));
      }
    }
    ts.forEachChild(node, visit);
  }
  visit(ast);
  return matches.length === 1 ? matches[0] : null;
}

function evidence(root, record) {
  const e = record.evidence;
  if (!e || !text(e.locator) || !text(e.rationale)) fail(`${record.id}: covered evidence needs locator and clause-specific rationale`);
  if (!(e.kind === 'java' && /^backend\/src\/test\/.+Test\.java$/.test(e.file))
      && !(e.kind === 'vitest' && /^frontend\/src\/.+\.(?:test|spec)\.[cm]?[jt]sx?$/.test(e.file))) {
    fail(`${record.id}: covered evidence must cite an allowed Java/Vitest test path`);
  }
  safePath(root, e.file);
  const source = currentAtRevision(root, e.file, e.revision);
  const body = e.kind === 'java' ? javaBody(source, e.locator) : vitestBody(source, e.locator, e.file);
  if (body === null) fail(`${record.id}: exact unambiguous test method/title not found: ${e.locator}`);
  for (const key of ['setupExcerpt', 'assertionExcerpt']) {
    if (!text(e[key]) || !body.includes(e[key])) fail(`${record.id}: ${key} is absent from the cited test body`);
  }
  const run = e.execution;
  if (!run || run.revision !== e.revision || !fullRevision(run.revision)
      || !text(run.command) || run.command.includes('\n') || run.exitCode !== 0
      || !/^harness\/runs\/[A-Za-z0-9-]+\/evidence\/[A-Za-z0-9._-]+\.(?:txt|log|md|json)$/.test(run.logPath)) {
    fail(`${record.id}: passing execution provenance required at the reviewed test revision`);
  }
  const log = read(root, run.logPath);
  if (sha(log) !== run.sha256 || !log.split('\n').includes(`Revision: ${run.revision}`)
      || !log.split('\n').includes(`Command: ${run.command}`) || !log.split('\n').includes('Exit code: 0')) {
    fail(`${record.id}: execution log digest/identity/result mismatch`);
  }
}

function headingIds(markdown) {
  return new Set([...markdown.matchAll(/^##\s+(.+)$/gm)].map((m) =>
    m[1].trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')));
}

function link(root, markdown, target, ticketOnly = false) {
  if (typeof target !== 'string') fail('Missing durable owner/finding link');
  if (!ticketOnly && /^#[a-z0-9-]+$/.test(target) && headingIds(markdown).has(target.slice(1))) return;
  if (!/^harness\/tickets\/(?:done\/)?(?:Dev|Bug|Feature|Optimization)-\d+\.md$/.test(target)) {
    fail(`Invalid durable ${ticketOnly ? 'owner ticket' : 'finding/ticket'} link: ${target}`);
  }
  safePath(root, target); // Existence only: never follow arbitrary Markdown links.
}

function counts(records, history) {
  return {
    classifications: Object.fromEntries(CLASSES.map((key) => [key, records.filter((r) => r.classification === key).length])),
    statuses: Object.fromEntries(STATUSES.map((key) => [key, records.filter((r) => r.classification === 'normative' && r.status === key).length])),
    historical: history.length,
  };
}

// historyDigest is a library seam for isolated synthetic fixtures; the CLI has no override.
export function verify(root, { partition, historyDigest = REPORT_SHA256 } = {}) {
  root = fs.realpathSync(root);
  const markdown = read(root, 'docs/spec-test-traceability.md');
  const config = singleton(markdown, 'traceability-config-jsonl');
  const records = blocks(markdown, 'traceability-jsonl');
  const history = blocks(markdown, 'traceability-history-jsonl');
  const declared = singleton(markdown, 'traceability-counts-jsonl');
  if (config.version !== 1 || !fullRevision(config.sourceRevision)) fail('Config needs version=1 and full sourceRevision');
  const sources = SPECS.map((file) => currentAtRevision(root, file, config.sourceRevision));
  const report = read(root, REPORT);
  if (sha(report) !== historyDigest) fail('Archived report SHA-256 mismatch; historical identity is not self-declared');
  const oldIds = [...report.matchAll(/^\| ([SBF]\d{3}\.\d{2}) — /gm)].map((m) => m[1]);
  if (!oldIds.length || !unique(oldIds)) fail('Archived report has missing/duplicate historical row IDs');
  const historicalSet = new Set(oldIds);
  const ids = new Set();
  for (const r of records) {
    if (!singleId(r.id) || 'clauseIds' in r || !text(r.atom) || r.atom.includes('~')) fail(`Invalid/grouped record ID or atom: ${r.id}`);
    if (ids.has(r.id)) fail(`Duplicate record id: ${r.id}`);
    ids.add(r.id);
  }
  const aliases = new Set();
  const queueIds = new Set();
  const evidenceRationales = new Map();
  const coverage = sources.map((s) => new Uint8Array(s.length));
  let previousOrder;
  for (const r of records) {
    const n = SPECS.indexOf(r.sourcePath);
    if (n < 0 || !CLASSES.includes(r.classification) || !text(r.rationale)) fail(`${r.id}: source/classification/rationale required`);
    if (r.sourceRevision !== config.sourceRevision) fail(`${r.id}: source revision differs from captured config`);
    if (r.aliases !== undefined) {
      if (!Array.isArray(r.aliases) || !r.aliases.length || !text(r.aliasRationale)) fail(`${r.id}: aliases require explicit correction rationale`);
      for (const alias of r.aliases) {
        if (!singleId(alias) || ids.has(alias) || aliases.has(alias)) fail(`${r.id}: ambiguous/duplicate alias ${alias}`);
        aliases.add(alias);
      }
    }
    const source = sources[n];
    const lines = source.split('\n');
    if (!Number.isInteger(r.startLine) || !Number.isInteger(r.endLine) || r.startLine < 1
        || r.endLine < r.startLine || r.endLine > lines.length || !text(r.excerpt)
        || !Number.isInteger(r.excerptOffset) || r.excerptOffset < 0) fail(`${r.id}: invalid exact source range`);
    const start = lines.slice(0, r.startLine - 1).reduce((sum, line) => sum + line.length + 1, 0) + r.excerptOffset;
    if (r.excerptOffset > lines[r.startLine - 1].length
        || source.slice(start, start + r.excerpt.length) !== r.excerpt
        || r.startLine + r.excerpt.split('\n').length - 1 !== r.endLine) {
      fail(`${r.id}: source excerpt does not exactly match its captured range/offset`);
    }
    if (!same(r.sourceOrder, [r.startLine, r.endLine, r.excerptOffset])) fail(`${r.id}: sourceOrder must equal [startLine,endLine,excerptOffset]`);
    const order = [n, ...r.sourceOrder, r.id];
    if (previousOrder) {
      let comparison = 0;
      for (let i = 0; i < order.length && comparison === 0; i += 1) comparison = order[i] < previousOrder[i] ? -1 : order[i] > previousOrder[i] ? 1 : 0;
      if (comparison < 0) fail(`${r.id}: records are not in source order`);
    }
    previousOrder = order;
    coverage[n].fill(1, start, start + r.excerpt.length);
    if (r.classification !== 'normative') {
      if (!text(r.reason) || ['status', 'evidence', 'finding', 'owner', 'queue'].some((key) => key in r)) {
        fail(`${r.id}: non-normative records need reason and cannot carry normative status/evidence/ownership`);
      }
      continue;
    }
    if (!STATUSES.includes(r.status)) fail(`${r.id}: invalid normative status`);
    if (r.status === 'covered') {
      if (['reason', 'finding', 'owner', 'queue'].some((key) => key in r)) fail(`${r.id}: covered status has incompatible non-covered fields`);
      evidence(root, r);
      const identity = `${r.evidence.file}::${r.evidence.locator}`;
      const rationales = evidenceRationales.get(identity) ?? new Set();
      if (rationales.has(r.evidence.rationale)) fail(`${r.id}: reused test requires a distinct clause-specific rationale`);
      rationales.add(r.evidence.rationale);
      evidenceRationales.set(identity, rationales);
    } else {
      if ('evidence' in r || !text(r.reason)) fail(`${r.id}: non-covered records require reason and no coverage evidence`);
      if (r.status === 'evidence-not-yet-reviewed') {
        if ('finding' in r || ('owner' in r) === ('queue' in r)) fail(`${r.id}: unreviewed needs exactly one owner ticket or bounded queue entry, not a finding`);
        if ('owner' in r) link(root, markdown, r.owner, true);
        else {
          if (!r.queue || !singleId(r.queue.id) || r.queue.owner !== 'Dev-62 coordinator for Ticket Author routing'
              || !text(r.queue.reason)) fail(`${r.id}: invalid Dev-62 bounded-review queue entry`);
          if (queueIds.has(r.queue.id)) fail(`${r.id}: duplicate bounded-review queue ID ${r.queue.id}`);
          queueIds.add(r.queue.id);
          link(root, markdown, r.queue.link);
          if (!r.queue.link.startsWith('#')) fail(`${r.id}: queue must link its durable local entry`);
        }
      } else {
        if ('owner' in r || 'queue' in r) fail(`${r.id}: reviewed gap/failure/delegated must use finding, not unreviewed ownership`);
        link(root, markdown, r.finding, r.status === 'delegated');
      }
    }
  }
  const reconciled = new Set();
  for (const h of history) {
    if (!historicalSet.has(h.legacyId) || reconciled.has(h.legacyId)) fail(`Unmatched/duplicate historical ID: ${h.legacyId}`);
    reconciled.add(h.legacyId);
    if (!['matched', 'split', 'merged', 'descriptive', 'scope-exclusion', 'obsolete'].includes(h.disposition)
        || !text(h.rationale) || !text(h.currentTextJustification) || !Array.isArray(h.currentIds)
        || !h.currentIds.length || !unique(h.currentIds)) fail(`${h.legacyId}: invalid historical disposition/mapping/justification`);
    for (const id of h.currentIds) if (!ids.has(id)) fail(`${h.legacyId}: unresolved current record ${id} (aliases are not canonical targets)`);
    if ((h.disposition === 'split' && h.currentIds.length < 2)
        || (h.disposition === 'matched' && h.currentIds.length !== 1)) fail(`${h.legacyId}: invalid split/matched cardinality`);
    if (['descriptive', 'scope-exclusion'].includes(h.disposition)
        && h.currentIds.some((id) => records.find((r) => r.id === id).classification !== h.disposition)) {
      fail(`${h.legacyId}: historical disposition conflicts with current classification`);
    }
    if (h.disposition === 'merged' && !history.some((other) => other !== h && other.disposition === 'merged'
        && same(other.currentIds, h.currentIds))) fail(`${h.legacyId}: merged mapping needs explicit peer rows with identical targets`);
  }
  const derived = counts(records, history);
  // Compare keys and values, not insertion order.
  function canonical(value) {
    if (value && typeof value === 'object' && !Array.isArray(value)) return Object.fromEntries(Object.keys(value).sort().map((key) => [key, canonical(value[key])]));
    return value;
  }
  if (!same(canonical(declared), canonical(derived))) fail(`Declared counts differ from derived counts: ${JSON.stringify(derived)}`);
  const missingSource = SPECS.map((file, i) => {
    let characters = 0;
    let firstLine;
    let line = 1;
    for (let j = 0; j < sources[i].length; j += 1) {
      if (!/\s/.test(sources[i][j]) && !coverage[i][j]) { characters += 1; firstLine ??= line; }
      if (sources[i][j] === '\n') line += 1;
    }
    return { file, characters, firstLine };
  });
  const missingHistory = oldIds.filter((id) => !reconciled.has(id));
  const fixed = [
    { id: 'shared', kind: 'classification', sourcePath: SPECS[0], historyPrefix: 'S' },
    { id: 'backend', kind: 'classification', sourcePath: SPECS[1], historyPrefix: 'B' },
    { id: 'frontend', kind: 'classification', sourcePath: SPECS[2], historyPrefix: 'F' },
  ];
  if (!Array.isArray(config.partitions) || config.partitions.length < 3) fail('Declare the three exact classification partitions');
  for (const p of fixed) if (!config.partitions.some((entry) => same(canonical(entry), canonical(p)))) fail(`Missing exact classification partition: ${p.id}`);
  if (!unique(config.partitions.map((p) => p.id))) fail('Duplicate partition ID');
  for (const p of config.partitions.filter((p) => !fixed.some((f) => f.id === p.id))) {
    if (!singleId(p.id) || p.kind !== 'evidence' || !Array.isArray(p.recordIds) || !p.recordIds.length
        || !unique(p.recordIds) || p.recordIds.some((id) => !ids.has(id)
          || records.find((r) => r.id === id).classification !== 'normative')) fail(`${p.id}: invalid exact evidence partition IDs`);
  }
  const summary = { records: records.length, ...derived, outstanding: {
    source: missingSource, historical: missingHistory.length,
    historyByPrefix: Object.fromEntries(['S', 'B', 'F'].map((prefix) => [prefix, missingHistory.filter((id) => id.startsWith(prefix)).length])),
    unreviewed: derived.statuses['evidence-not-yet-reviewed'],
  } };
  process.stdout.write(`${JSON.stringify(summary, null, 2)}\n`);
  if (partition) {
    const selected = config.partitions.find((p) => p.id === partition);
    if (!selected) fail(`Unknown declared partition: ${partition}`);
    if (selected.kind === 'classification') {
      const missing = missingSource.find((s) => s.file === selected.sourcePath);
      if (missing.characters || missingHistory.some((id) => id.startsWith(selected.historyPrefix))) {
        fail(`Staged partition ${partition} incomplete: ${missing.characters} source characters (first line ${missing.firstLine ?? 'none'}), ${missingHistory.filter((id) => id.startsWith(selected.historyPrefix)).length} historical IDs`);
      }
    } else if (missingSource.some((s) => s.characters) || missingHistory.length) {
      fail(`Evidence partition ${partition} requires complete global source/history classification first`);
    }
    process.stdout.write(`STAGED structural pass: ${partition}; all available records checked. NOT final validation or coverage.\n`);
  } else {
    if (missingSource.some((s) => s.characters) || missingHistory.length || summary.outstanding.unreviewed) {
      fail('STRICT final rejection: incomplete source/history reconciliation or evidence-not-yet-reviewed; see outstanding totals above');
    }
    process.stdout.write('STRICT final structural pass; semantic classification, atomization and evidence sufficiency still require independent review.\n');
  }
  return summary;
}

function main(argv) {
  let root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
  let staged = false;
  let partition;
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--root' && argv[i + 1]) root = path.resolve(argv[++i]);
    else if (argv[i] === '--staged' && !staged) staged = true;
    else if (argv[i] === '--partition' && argv[i + 1] && !partition) partition = argv[++i];
    else fail(`Unknown/incomplete argument: ${argv[i]}`);
  }
  if (staged !== Boolean(partition)) fail('Staged validation requires both --staged and --partition <declared-id>');
  verify(root, { partition });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { main(process.argv.slice(2)); } catch (error) {
    process.stderr.write(`spec-test-traceability: ${error.message}\n`);
    process.exitCode = 1;
  }
}

import test from 'node:test'
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const checker = path.join(frontend, 'scripts', 'check-deprecated-apis.mjs')

async function makeProject(files) {
  const root = await mkdtemp(path.join(tmpdir(), 'dev33-deprecation-'))
  for (const [relative, contents] of Object.entries(files)) {
    const destination = path.join(root, relative)
    await mkdir(path.dirname(destination), { recursive: true })
    await writeFile(destination, contents)
  }
  return root
}

function runChecker(root) {
  return spawnSync('npm', ['run', 'check:deprecated', '--', '--root', root], {
    cwd: frontend,
    encoding: 'utf8',
  })
}

const tsconfig = JSON.stringify({
  compilerOptions: {
    target: 'ESNext',
    module: 'ESNext',
    moduleResolution: 'Bundler',
    strict: true,
    allowJs: true,
    checkJs: true,
    noEmit: true,
  },
})

const api = `
/** @deprecated Use currentType instead. */
export interface RetiredType {}
export interface CurrentType {}
/** @deprecated Use replacement instead. */
export function retired(): void {}
export function replacement(): void {}
/** @deprecated Use replacement instead. */
export const retiredValue = (): void => {}
/** @deprecated Use CurrentClass instead. */
export class RetiredClass {}
export class CurrentClass {}
export class Parent {
  /** @deprecated Use currentMember instead. */
  legacyMember(): void {}
  currentMember(): void {}
}
export class Child extends Parent {}
export function choose(value: string): string
/** @deprecated Use the string overload. */
export function choose(value: number): string
export function choose(value: string | number): string { return String(value) }
`

test('resolves direct, aliased, inherited, type, and selected deprecated overload references', async (context) => {
  const root = await makeProject({
    'tsconfig.json': tsconfig,
    'api.ts': api,
    'direct.ts': `
import { retired, retired as renamed, retiredValue, RetiredClass, Child, RetiredType, choose, replacement } from './api.js'
retired()
renamed()
retiredValue()
new RetiredClass()
const retiredTypeValue: RetiredType = {}
const inherited = new Child()
inherited.legacyMember()
inherited.currentMember()
choose('supported overload')
choose(42)
replacement()
void retiredTypeValue
`,
  })
  context.after(() => rm(root, { recursive: true, force: true }))

  const result = runChecker(root)
  assert.equal(result.status, 1, result.stdout + result.stderr)
  assert.equal((result.stderr.match(/deprecated API 'retired'/g) ?? []).length, 2, result.stderr)
  assert.match(result.stderr, /direct\.ts:\d+:\d+: deprecated API 'RetiredType'/)
  assert.match(result.stderr, /direct\.ts:\d+:\d+: deprecated API 'legacyMember'/)
  assert.match(result.stderr, /direct\.ts:\d+:\d+: deprecated API 'retiredValue'/)
  assert.match(result.stderr, /direct\.ts:\d+:\d+: deprecated API 'RetiredClass'/)
  assert.match(result.stderr, /direct\.ts:\d+:\d+: deprecated API 'choose'/)
  assert.doesNotMatch(result.stderr, /supported overload/)
  assert.equal((result.stderr.match(/deprecated API/g) ?? []).length, 7, result.stderr)
})

test('analyzes Vue script and Node .mjs source through the public checker command', async (context) => {
  const root = await makeProject({
    'tsconfig.json': tsconfig,
    'api.ts': api,
    'Component.vue': `<template><div /></template>
<script setup lang="ts">
import { retired as vueAlias, replacement } from './api.js'
vueAlias()
replacement()
</script>
`,
    'tool.mjs': `import { retired as nodeAlias, replacement } from './api.js'
nodeAlias()
replacement()
`,
  })
  context.after(() => rm(root, { recursive: true, force: true }))

  const result = runChecker(root)
  assert.equal(result.status, 1, result.stdout + result.stderr)
  assert.match(result.stderr, /Component\.vue<script setup>:\d+:\d+: deprecated API 'retired'/)
  assert.match(result.stderr, /tool\.mjs:\d+:\d+: deprecated API 'retired'/)
  assert.equal((result.stderr.match(/deprecated API/g) ?? []).length, 2, result.stderr)
})

test('rejects malformed and unresolved in-scope inputs instead of omitting analysis', async (context) => {
  const root = await makeProject({
    'tsconfig.json': tsconfig,
    'broken.ts': `import { missing } from './not-present.js'\nmissing()\nconst invalid: = 1\n`,
    'Broken.vue': `<script setup lang="ts">\nconst invalid: = 1\n</script>\n`,
  })
  context.after(() => rm(root, { recursive: true, force: true }))

  const result = runChecker(root)
  assert.equal(result.status, 1, result.stdout + result.stderr)
  assert.match(result.stderr, /broken\.ts:\d+:\d+: syntax cannot be analyzed/)
  assert.match(result.stderr, /broken\.ts:.*cannot resolve declarations: Cannot find module/)
  assert.match(result.stderr, /Broken\.vue<script setup>:\d+:\d+: syntax cannot be analyzed/)
})

test('fails explicitly when a tracked source file is no longer readable', async (context) => {
  const root = await makeProject({
    'tsconfig.json': tsconfig,
    'missing.ts': `export const analyzed = true\n`,
  })
  execFileSync('git', ['init', '-q'], { cwd: root })
  execFileSync('git', ['add', 'tsconfig.json', 'missing.ts'], { cwd: root })
  await rm(path.join(root, 'missing.ts'))
  context.after(() => rm(root, { recursive: true, force: true }))

  const result = runChecker(root)
  assert.equal(result.status, 1, result.stdout + result.stderr)
  assert.match(result.stderr, /ANALYSIS ERROR: cannot read included source missing\.ts:/)
})

test('does not scan ignored build/cache or dependency output', async (context) => {
  const root = await makeProject({
    '.gitignore': `build/\nnode_modules/\ndocs/generated/\n`,
    'tsconfig.json': tsconfig,
    'safe.ts': `export const supported = true\n`,
    'build/ignored.ts': `/** @deprecated */ export function ignored() {}\nignored()\n`,
    'node_modules/pkg/index.mjs': `/** @deprecated */ export function ignored() {}\nignored()\n`,
    'docs/generated/guide.mjs': `/** @deprecated */ export function ignored() {}\nignored()\n`,
  })
  execFileSync('git', ['init', '-q'], { cwd: root })
  context.after(() => rm(root, { recursive: true, force: true }))

  const result = runChecker(root)
  assert.equal(result.status, 0, result.stdout + result.stderr)
  assert.match(result.stdout, /Checked 1 owned source files/)
})

test('the standard frontend typecheck is wired to the failing public deprecation command', async (context) => {
  const root = await makeProject({
    'tsconfig.json': tsconfig,
    'deprecated.mjs': `/** @deprecated Replace this call. */\nfunction oldApi() {}\noldApi()\n`,
  })
  context.after(() => rm(root, { recursive: true, force: true }))

  const packageJson = JSON.parse(await readFile(path.join(frontend, 'package.json'), 'utf8'))
  assert.equal(packageJson.scripts.typecheck, 'npm run check:deprecated && vue-tsc --noEmit')
  await writeFile(path.join(root, 'package.json'), JSON.stringify({
    private: true,
    scripts: {
      'check:deprecated': `node "${checker}" --root "${root}"`,
      typecheck: packageJson.scripts.typecheck,
    },
  }))
  const result = spawnSync('npm', ['run', 'typecheck'], { cwd: root, encoding: 'utf8' })
  assert.equal(result.status, 1, result.stdout + result.stderr)
  assert.match(result.stderr, /deprecated\.mjs:\d+:\d+: deprecated API 'oldApi'/)
  assert.doesNotMatch(result.stdout, /> vue-tsc --noEmit/)
})

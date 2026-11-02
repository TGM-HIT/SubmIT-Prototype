#!/usr/bin/env node
import { mkdir, mkdtemp, open, readFile, rm, writeFile, cp } from 'node:fs/promises'
import { renameSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { findChrome, parseArgs, run } from '../preview.mjs'
import { loadLocale, renderGuides, validateGuides } from './guides.mjs'
import { Lifecycle } from './scenario.mjs'

export const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')

export async function generate(language, {
  root = repo, env = process.env, load = loadLocale, preflight = findChrome,
  services = run, scenarioClass = Lifecycle, validate = validateGuides,
} = {}) {
  // Validate all input before starting services, acquiring resources or touching previous outputs.
  const locale = await load(language)
  // Public testing-profile fixture credential, never a production account password.
  const password = env.PREVIEW_PASSWORD || 'testing-password'
  const evidenceReport = env.DOCUMENTATION_REPORT ? path.resolve(root, env.DOCUMENTATION_REPORT) : undefined
  if (evidenceReport && (!evidenceReport.startsWith(path.join(root, 'harness/runs') + path.sep) ||
      !evidenceReport.includes(`${path.sep}evidence${path.sep}`) || !evidenceReport.endsWith('.json'))) {
    throw new Error('DOCUMENTATION_REPORT must name an ignored harness/runs/<run>/evidence/*.json file.')
  }
  await preflight(env.CHROME_BIN)
  const preview = path.join(root, 'frontend/.preview')
  await mkdir(preview, { recursive: true })
  const lockPath = path.join(preview, 'documentation.lock')
  let lock
  try {
    lock = await open(lockPath, 'wx')
  } catch (error) {
    throw new Error(`Documentation session occupied: ${lockPath}. Do not stop another run. ${error.code}`)
  }
  let work
  let interrupted = false
  const interrupt = () => { interrupted = true }
  const checkInterrupted = () => {
    if (interrupted) throw new Error('Documentation interrupted; previous guide set preserved.')
  }
  // Preview owns capture cancellation; keep ownership/cleanup alive during rendering and validation too.
  process.on('SIGINT', interrupt)
  process.on('SIGTERM', interrupt)
  const report = { language, viewport: { width: 1440, height: 1000 }, status: 'in-progress' }
  try {
    await lock.writeFile(JSON.stringify({ pid: process.pid }))
    work = await mkdtemp(path.join(preview, 'documentation-'))
    const staging = path.join(work, 'guides')
    try {
      await cp(path.join(root, 'docs/user-guide'), staging, { recursive: true })
    } catch (error) {
      if (error.code !== 'ENOENT') throw error
    }
    await rm(path.join(staging, language), { recursive: true, force: true })
    await mkdir(path.join(staging, language, 'images'), { recursive: true })
    const options = parseArgs(['--path', '/login', '--viewport', 'desktop', '--output-dir', work])
    options.profileRoot = work
    const scenario = new scenarioClass({
      locale, password, backendUrl: `http://127.0.0.1:${options.backendPort}`,
      frontendUrl: `http://127.0.0.1:${options.frontendPort}`, output: staging, work, report, options,
    })
    checkInterrupted()
    await services(options, {
      serviceEnv: {
        DADB_TESTING_MINIMAL_FIXTURES: 'true',
        DADB_TESTING_ALLOCATION_PIN_PREVIEW: 'false',
        DADB_BOOTSTRAPADMIN_USERNAME: '',
        DADB_BOOTSTRAPADMIN_PASSWORD: '',
        SPRING_DATASOURCE_URL: 'jdbc:h2:mem:documentation;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
        SPRING_DATASOURCE_USERNAME: 'sa',
        SPRING_DATASOURCE_PASSWORD: '',
      },
      prepare: (context) => { checkInterrupted(); return scenario.prepare(context) },
      capture: (context) => scenario.capture(context),
    })
    checkInterrupted()
    await renderGuides(staging, locale)
    report.inventory = await validate(staging, language, { secrets: [password], workspace: root })
    checkInterrupted()
    report.status = 'passed'
    // Validate everything before the first rename; restore the previous tree if installation fails.
    const destination = path.join(root, 'docs/user-guide')
    const previous = path.join(work, 'previous')
    await mkdir(path.dirname(destination), { recursive: true })
    let moved = false
    try {
      checkInterrupted()
      // No signal/event turn between moving the old tree and installing/restoring its replacement.
      renameSync(destination, previous)
      moved = true
    } catch (error) {
      if (error.code !== 'ENOENT') throw error
    }
    try {
      renameSync(staging, destination)
    } catch (error) {
      if (moved) renameSync(previous, destination)
      throw error
    }
    await rm(previous, { recursive: true, force: true })
    console.log(`Generated ${path.relative(root, destination)}/README.md (${language}).`)
  } catch (error) {
    report.status = 'failed'
    report.failure ??= { message: error.message }
    throw error
  } finally {
    // Reports contain IDs/selectors/statuses, never sessions, passwords or bearer tokens.
    try {
      if (work) {
        await writeFile(path.join(preview, 'documentation-report.json'), JSON.stringify(report, null, 2))
        if (evidenceReport) {
          await mkdir(path.dirname(evidenceReport), { recursive: true })
          await writeFile(evidenceReport, JSON.stringify(report, null, 2))
        }
        // Retain failure evidence outside generated guides, but never profiles or credential state.
        try {
          const image = await readFile(path.join(work, 'failure-desktop.png'))
          await writeFile(path.join(preview, 'documentation-failure-desktop.png'), image)
        } catch (error) {
          if (error.code !== 'ENOENT') throw error
        }
      }
    } finally {
      try {
        if (work) await rm(work, { recursive: true, force: true })
        await lock.close()
        await rm(lockPath, { force: true })
      } finally {
        process.off('SIGINT', interrupt)
        process.off('SIGTERM', interrupt)
      }
    }
    console.log('Documentation-owned services/browser profiles cleaned up. Report: frontend/.preview/documentation-report.json')
  }
  return report
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    if (process.argv.length !== 4 || process.argv[2] !== '--language') {
      throw new Error('Usage: make doc LANGUAGE=en|de')
    }
    await generate(process.argv[3])
  } catch (error) {
    console.error(`Documentation failed: ${error.message}`)
    process.exitCode = 1
  }
}

#!/usr/bin/env node
import { execFileSync } from 'node:child_process'
import { readFile, readdir, realpath, stat } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import ts from 'typescript'
import { parse as parseVue } from 'vue/compiler-sfc'
import { fileURLToPath, pathToFileURL } from 'node:url'

const SOURCE_EXTENSIONS = new Set(['.ts', '.tsx', '.mts', '.cts', '.js', '.jsx', '.mjs', '.cjs', '.vue'])
const EXCLUDED_DIRECTORIES = new Set([
  '.git',
  '.vite',
  'build',
  'coverage',
  'dist',
  'node_modules',
])
const VUE_SHIM = `
declare module '*.vue' {
  const component: import('vue').DefineComponent
  export default component
}
`

function inside(directory, file) {
  const relative = path.relative(directory, file)
  return relative === '' || (!relative.startsWith(`..${path.sep}`) && relative !== '..' && !path.isAbsolute(relative))
}

async function walkSources(root, directory = root) {
  const result = []
  const entries = await readdir(directory, { withFileTypes: true })
  for (const entry of entries) {
    const fullPath = path.join(directory, entry.name)
    if (entry.isDirectory()) {
      if (!EXCLUDED_DIRECTORIES.has(entry.name)) result.push(...await walkSources(root, fullPath))
    } else if (entry.isFile() && SOURCE_EXTENSIONS.has(path.extname(entry.name))) {
      result.push(fullPath)
    }
  }
  return result
}

async function ownedSources(root) {
  try {
    const gitRoot = await realpath(execFileSync('git', ['rev-parse', '--show-toplevel'], {
      cwd: root,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    }).trim())
    const canonicalRoot = await realpath(root)
    const rootRelative = path.relative(gitRoot, canonicalRoot)
    if (rootRelative === '..' || rootRelative.startsWith(`..${path.sep}`) || path.isAbsolute(rootRelative)) {
      return (await walkSources(root)).sort()
    }
    const trackedAndUnignored = execFileSync('git', [
      'ls-files',
      '--cached',
      '--others',
      '--exclude-standard',
      '--',
      rootRelative || '.',
    ], { cwd: gitRoot, encoding: 'utf8' })
      .split(/\r?\n/)
      .filter(Boolean)
      .map((entry) => path.resolve(gitRoot, entry))
      .filter((entry) => inside(canonicalRoot, entry) && SOURCE_EXTENSIONS.has(path.extname(entry)))
    if (trackedAndUnignored.length > 0) return [...new Set(trackedAndUnignored)].sort()
  } catch (error) {
    if (error?.status !== 128 && error?.code !== 'ENOENT') throw error
  }
  return (await walkSources(root)).sort()
}

function parseConfig(root, failures) {
  const configPath = path.join(root, 'tsconfig.json')
  let config
  try {
    config = ts.readConfigFile(configPath, ts.sys.readFile)
  } catch (error) {
    failures.push(`cannot read ${configPath}: ${error.message}`)
    return {
      options: {},
    }
  }
  if (config.error) {
    failures.push(`cannot parse ${configPath}: ${ts.flattenDiagnosticMessageText(config.error.messageText, '\n')}`)
    return { options: {} }
  }
  const parsed = ts.parseJsonConfigFileContent(config.config, ts.sys, root, undefined, configPath)
  for (const diagnostic of parsed.errors) {
    failures.push(`cannot analyze ${configPath}: ${ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n')}`)
  }
  return parsed
}

function scriptKindFor(fileName) {
  const extension = path.extname(fileName).toLowerCase()
  if (extension === '.tsx' || extension === '.jsx') return ts.ScriptKind.TSX
  if (extension === '.js' || extension === '.jsx' || extension === '.mjs' || extension === '.cjs') {
    return ts.ScriptKind.JS
  }
  return ts.ScriptKind.TS
}

function fileNameForVueScript(fileName, language, index) {
  const extension = language === 'tsx' ? '.tsx' : language === 'jsx' ? '.jsx'
    : language === 'js' || language === 'javascript' ? '.js' : '.ts'
  return `${fileName}.__dev33_script_${index}${extension}`
}

function sourcePosition(sourceFile, node) {
  const position = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile))
  return `${position.line + 1}:${position.character + 1}`
}

function getSymbol(checker, node) {
  let symbol = checker.getSymbolAtLocation(node)
  if (symbol && (symbol.flags & ts.SymbolFlags.Alias) !== 0) {
    try {
      symbol = checker.getAliasedSymbol(symbol)
    } catch {
      return undefined
    }
  }
  return symbol
}

function deprecatedTag(tags) {
  return tags.find((tag) => tag.name.toLowerCase() === 'deprecated')
}

function isDeclarationName(node, parent) {
  if (!parent || parent.name !== node) return false
  return ts.isVariableDeclaration(parent)
    || ts.isParameter(parent)
    || ts.isFunctionDeclaration(parent)
    || ts.isFunctionExpression(parent)
    || ts.isClassDeclaration(parent)
    || ts.isClassExpression(parent)
    || ts.isInterfaceDeclaration(parent)
    || ts.isTypeAliasDeclaration(parent)
    || ts.isEnumDeclaration(parent)
    || ts.isEnumMember(parent)
    || ts.isPropertyDeclaration(parent)
    || ts.isMethodDeclaration(parent)
    || ts.isMethodSignature(parent)
    || ts.isPropertySignature(parent)
    || ts.isGetAccessorDeclaration(parent)
    || ts.isSetAccessorDeclaration(parent)
    || ts.isTypeParameterDeclaration(parent)
    || ts.isModuleDeclaration(parent)
    || ts.isImportClause(parent)
    || ts.isImportSpecifier(parent)
    || ts.isNamespaceImport(parent)
    || ts.isImportEqualsDeclaration(parent)
    || ts.isBindingElement(parent)
    || ts.isLabeledStatement(parent)
    || ts.isPropertyAssignment(parent)
    || ts.isShorthandPropertyAssignment(parent)
}

function declarationTag(symbol, checker) {
  if (!symbol) return undefined
  return deprecatedTag(symbol.getJsDocTags(checker))
}

function hasOverloadDeclarations(symbol) {
  const signatures = (symbol?.getDeclarations() ?? []).filter((declaration) =>
    ts.isFunctionDeclaration(declaration)
    || ts.isMethodDeclaration(declaration)
    || ts.isMethodSignature(declaration)
    || ts.isCallSignatureDeclaration(declaration)
    || ts.isConstructSignatureDeclaration(declaration))
  return signatures.length > 1
}

function signatureTag(signature) {
  if (!signature) return undefined
  const declaration = signature.getDeclaration()
  if (declaration) {
    const tag = ts.getJSDocTags(declaration).find((candidate) =>
      candidate.tagName.escapedText.toString().toLowerCase() === 'deprecated')
    return tag ? { name: 'deprecated', text: tag.comment ?? '' } : undefined
  }
  return deprecatedTag(signature.getJsDocTags())
}

function tagText(tag) {
  if (!tag?.text) return ''
  if (typeof tag.text === 'string') return tag.text.trim()
  return tag.text.map((part) => part.text).join('').trim()
}

function displaySymbol(symbol, fallback) {
  return symbol?.getName?.() || fallback
}

function analyzeProgram(program, sourceRecords, checker) {
  const findings = []
  const failures = []
  const rootSourceFiles = new Set(sourceRecords.map(({ virtualFile }) => path.resolve(virtualFile)))
  const sourceByResolvedPath = new Map(sourceRecords.map((record) => [path.resolve(record.virtualFile), record]))
  const checkedDiagnostics = new Set()
  for (const sourceFile of program.getSourceFiles()) {
    if (!rootSourceFiles.has(path.resolve(sourceFile.fileName))) continue
    for (const diagnostic of program.getSyntacticDiagnostics(sourceFile)) {
      const record = sourceByResolvedPath.get(path.resolve(sourceFile.fileName))
      const text = ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n')
      const location = diagnostic.start === undefined
        ? ''
        : `${record?.displayPath ?? sourceFile.fileName}:${sourcePosition(sourceFile, {
          getStart: () => diagnostic.start,
        })}: `
      failures.push(`${location}syntax cannot be analyzed: ${text}`)
    }
  }

  const unresolvedCodes = new Set([2304, 2305, 2307, 2552, 2580, 2688, 2694, 2724, 2792, 7016])
  for (const sourceFile of program.getSourceFiles()) {
    if (!rootSourceFiles.has(path.resolve(sourceFile.fileName))) continue
    const record = sourceByResolvedPath.get(path.resolve(sourceFile.fileName))
    for (const diagnostic of program.getSemanticDiagnostics(sourceFile)) {
      if (!unresolvedCodes.has(diagnostic.code)) continue
      const message = ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n')
      const position = diagnostic.start === undefined ? '' : `${sourcePosition(sourceFile, {
        getStart: () => diagnostic.start,
      })}: `
      failures.push(`${record?.displayPath ?? sourceFile.fileName}:${position}cannot resolve declarations: ${message}`)
    }

    function report(node, symbol, tag) {
      if (!tag) return
      const position = sourcePosition(sourceFile, node)
      const displayPath = record?.displayPath ?? sourceFile.fileName
      const canonicalName = displaySymbol(symbol, node.getText(sourceFile))
      const message = tagText(tag)
      const key = `${displayPath}:${position}:${canonicalName}`
      if (checkedDiagnostics.has(key)) return
      checkedDiagnostics.add(key)
      findings.push(`${displayPath}:${position}: deprecated API '${canonicalName}'${message ? ` — ${message}` : ''}`)
    }

    function visit(node) {
      if (ts.isExportSpecifier(node)) {
        const exported = node.propertyName ?? node.name
        const symbol = getSymbol(checker, exported)
        report(exported, symbol, declarationTag(symbol, checker))
      } else if (ts.isCallExpression(node) || ts.isNewExpression(node)) {
        const signature = checker.getResolvedSignature(node)
        const tag = signatureTag(signature)
        const targetSymbol = getSymbol(checker, node.expression)
        const targetTag = declarationTag(targetSymbol, checker)
        if (tag) {
          report(node.expression, targetSymbol, tag)
        } else if (targetTag && (!signature || !hasOverloadDeclarations(targetSymbol))) {
          report(node.expression, targetSymbol, targetTag)
        }
      } else if (ts.isPropertyAccessExpression(node) || ts.isElementAccessExpression(node)) {
        const parent = node.parent
        const isCallTarget = (ts.isCallExpression(parent) || ts.isNewExpression(parent))
          && parent.expression === node
        if (!isCallTarget) {
          const symbol = getSymbol(checker, node)
          report(node, symbol, declarationTag(symbol, checker))
        }
      } else if (ts.isQualifiedName(node) || ts.isIdentifier(node)) {
        const parent = node.parent
        const isMemberName = ts.isIdentifier(node)
          && ((ts.isPropertyAccessExpression(parent) && parent.name === node)
            || (ts.isQualifiedName(parent) && (parent.left === node || parent.right === node))
            || ((ts.isCallExpression(parent) || ts.isNewExpression(parent)) && parent.expression === node))
        const isDeclaration = ts.isIdentifier(node) && isDeclarationName(node, parent)
        const isImportExportName = ts.isIdentifier(node)
          && (ts.isImportDeclaration(parent)
            || ts.isExportDeclaration(parent)
            || ts.isImportClause(parent)
            || ts.isImportSpecifier(parent)
            || ts.isNamespaceImport(parent)
            || ts.isImportEqualsDeclaration(parent)
            || ts.isExportSpecifier(parent))
        if (!isMemberName && !isDeclaration && !isImportExportName) {
          const symbol = getSymbol(checker, node)
          report(node, symbol, declarationTag(symbol, checker))
        }
      }
      ts.forEachChild(node, visit)
    }

    visit(sourceFile)
  }
  return { failures, findings }
}

export async function checkDeprecatedApis({ root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..') } = {}) {
  let projectRoot = path.resolve(root)
  const failures = []
  let rootStats
  try {
    rootStats = await stat(projectRoot)
  } catch (error) {
    return { failures: [`cannot access source root ${projectRoot}: ${error.message}`], findings: [], fileCount: 0 }
  }
  if (!rootStats.isDirectory()) {
    return { failures: [`source root is not a directory: ${projectRoot}`], findings: [], fileCount: 0 }
  }
  try {
    projectRoot = await realpath(projectRoot)
  } catch (error) {
    return { failures: [`cannot resolve source root ${projectRoot}: ${error.message}`], findings: [], fileCount: 0 }
  }
  const parsed = parseConfig(projectRoot, failures)
  const files = (await ownedSources(projectRoot)).filter((file) => !file.endsWith('.d.ts') || !file.includes(`${path.sep}node_modules${path.sep}`))
  if (files.length === 0) failures.push(`no owned TypeScript, Vue, or JavaScript source files found under ${projectRoot}`)

  const virtualSources = new Map()
  const sourceRecords = []
  for (const fileName of files) {
    let contents
    try {
      contents = await readFile(fileName, 'utf8')
    } catch (error) {
      failures.push(`cannot read included source ${path.relative(projectRoot, fileName)}: ${error.message}`)
      continue
    }
    if (path.extname(fileName).toLowerCase() !== '.vue') {
      sourceRecords.push({ virtualFile: fileName, displayPath: path.relative(projectRoot, fileName) })
      continue
    }
    const parsedVue = parseVue(contents, { filename: fileName })
    if (parsedVue.errors.length > 0) {
      for (const error of parsedVue.errors) {
        const message = typeof error === 'string' ? error : error.message
        failures.push(`${path.relative(projectRoot, fileName)}: Vue SFC cannot be parsed: ${message}`)
      }
      continue
    }
    const blocks = [parsedVue.descriptor.script, parsedVue.descriptor.scriptSetup].filter(Boolean)
    for (let index = 0; index < blocks.length; index += 1) {
      const block = blocks[index]
      const language = (block.lang || 'js').toLowerCase()
      if (!['js', 'javascript', 'jsx', 'ts', 'typescript', 'tsx'].includes(language)) {
        failures.push(`${path.relative(projectRoot, fileName)}: unsupported Vue script language '${language}'`)
        continue
      }
      let script = block.content
      let scriptPath = fileName
      if (block.src) {
        const externalPath = path.resolve(path.dirname(fileName), block.src)
        scriptPath = externalPath
        if (files.includes(externalPath)) continue
        try {
          script = await readFile(externalPath, 'utf8')
        } catch (error) {
          failures.push(`${path.relative(projectRoot, fileName)}: cannot read Vue script src '${block.src}': ${error.message}`)
          continue
        }
      }
      const virtualFile = fileNameForVueScript(scriptPath, language, index)
      virtualSources.set(path.resolve(virtualFile), script)
      sourceRecords.push({
        virtualFile,
        displayPath: `${path.relative(projectRoot, fileName)}<${block === parsedVue.descriptor.script ? 'script' : 'script setup'}>`,
      })
    }
  }

  const configOptions = parsed.options ?? {}
  const compilerOptions = {
    ...configOptions,
    allowJs: true,
    checkJs: true,
    noEmit: true,
    skipLibCheck: true,
    target: configOptions.target ?? ts.ScriptTarget.ESNext,
    module: configOptions.module ?? ts.ModuleKind.ESNext,
    moduleResolution: configOptions.moduleResolution ?? ts.ModuleResolutionKind.Bundler,
  }
  const shimPath = path.join(projectRoot, '__dev33_vue_module_shim__.d.ts')
  virtualSources.set(path.resolve(shimPath), VUE_SHIM)
  const rootNames = [...files.filter((fileName) => path.extname(fileName).toLowerCase() !== '.vue'),
    ...sourceRecords.map((record) => record.virtualFile),
    shimPath]
  const host = ts.createCompilerHost(compilerOptions)
  const defaultGetSourceFile = host.getSourceFile.bind(host)
  const defaultFileExists = host.fileExists.bind(host)
  const defaultReadFile = host.readFile.bind(host)
  host.getSourceFile = (fileName, languageVersion, onError, shouldCreateNewSourceFile) => {
    const source = virtualSources.get(path.resolve(fileName))
    if (source !== undefined) {
      return ts.createSourceFile(fileName, source, languageVersion, true, scriptKindFor(fileName))
    }
    return defaultGetSourceFile(fileName, languageVersion, onError, shouldCreateNewSourceFile)
  }
  host.fileExists = (fileName) => virtualSources.has(path.resolve(fileName)) || defaultFileExists(fileName)
  host.readFile = (fileName) => virtualSources.get(path.resolve(fileName)) ?? defaultReadFile(fileName)
  try {
    const program = ts.createProgram({ rootNames, options: compilerOptions, host })
    const result = analyzeProgram(program, sourceRecords, program.getTypeChecker())
    failures.push(...result.failures)
    return {
      failures,
      findings: result.findings,
      fileCount: files.length,
      scriptCount: sourceRecords.length,
    }
  } catch (error) {
    failures.push(`TypeScript program could not analyze included sources: ${error.stack ?? error.message}`)
    return { failures, findings: [], fileCount: files.length, scriptCount: sourceRecords.length }
  }
}

function parseArgs(args) {
  let root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
  for (let index = 0; index < args.length; index += 1) {
    if (args[index] === '--root' && args[index + 1]) {
      root = path.resolve(args[++index])
    } else {
      throw new Error(`Unknown or incomplete argument: ${args[index]}`)
    }
  }
  return { root }
}

async function main() {
  let options
  try {
    options = parseArgs(process.argv.slice(2))
  } catch (error) {
    console.error(error.message)
    process.exitCode = 2
    return
  }
  const result = await checkDeprecatedApis(options)
  for (const failure of result.failures) console.error(`ANALYSIS ERROR: ${failure}`)
  for (const finding of result.findings) console.error(finding)
  if (result.failures.length > 0 || result.findings.length > 0) {
    process.exitCode = 1
    return
  }
  console.log(`Checked ${result.fileCount} owned source files (${result.scriptCount} TypeScript/JavaScript script units); no deprecated API use or analysis failures.`)
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  await main()
}

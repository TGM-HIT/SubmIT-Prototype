import { readFile, writeFile, mkdir, stat, readdir } from 'node:fs/promises'
import path from 'node:path'
import { inflateSync } from 'node:zlib'

export const checkpoints = [
  'workspace', 'accounts', 'idea-draft', 'idea-submitted', 'teacher-review',
  'idea-revision', 'specification', 'accepted', 'period', 'preferences',
  'allocation', 'group', 'formal', 'formal-approved', 'rubric',
  'assessment', 'metrics', 'own-grades', 'final-grade', 'artifact-review', 'public-archive',
]
export const roles = ['schueler', 'lehrkraefte', 'administration']
export const desktop = { width: 1440, height: 1000 }

export function validatePng(png) {
  if (png.length < 67 || !png.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))) {
    throw new Error('Invalid PNG signature')
  }
  let offset = 8
  const pixels = []
  let header, ended = false
  while (offset + 12 <= png.length) {
    const length = png.readUInt32BE(offset)
    const end = offset + 12 + length
    if (end > png.length) throw new Error('Truncated PNG')
    const chunk = png.subarray(offset + 4, end - 4)
    let crc = 0xffffffff
    for (const byte of chunk) {
      crc ^= byte
      for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0)
    }
    if (((crc ^ 0xffffffff) >>> 0) !== png.readUInt32BE(end - 4)) throw new Error('Corrupt PNG checksum')
    const kind = chunk.toString('ascii', 0, 4)
    if (kind === 'IHDR') header = chunk.subarray(4)
    if (kind === 'IDAT') pixels.push(chunk.subarray(4))
    if (kind === 'IEND') { ended = true; offset = end; break }
    offset = end
  }
  if (!ended || offset !== png.length || !header || header.length !== 13 || !pixels.length) throw new Error('Incomplete PNG')
  if (header.readUInt32BE(0) !== desktop.width || header.readUInt32BE(4) !== desktop.height) {
    throw new Error('Wrong desktop dimensions')
  }
  // Chrome writes non-interlaced 8-bit RGB/RGBA captures. Decode, do not merely trust a PNG extension.
  const channels = header[9] === 2 ? 3 : header[9] === 6 ? 4 : 0
  if (header[8] !== 8 || !channels || header[12] !== 0) throw new Error('Unsupported screenshot PNG encoding')
  const raw = inflateSync(Buffer.concat(pixels), { maxOutputLength: (desktop.width * 4 + 1) * desktop.height })
  if (raw.length !== (desktop.width * channels + 1) * desktop.height) throw new Error('Invalid PNG pixel payload')
  return true
}

export function validateLocale(locale, language) {
  if (!/^[a-z]{2}(?:-[A-Z]{2})?$/.test(language ?? '') || locale?.language !== language) {
    throw new Error('Usage: make doc LANGUAGE=en|de — select an explicitly supported language.')
  }
  for (const key of ['title', 'overview', 'example']) {
    if (!locale[key] || typeof locale[key] !== 'object' && typeof locale[key] !== 'string') {
      throw new Error(`Incomplete language ${language}: ${key}`)
    }
    for (const key of [
      'registrationDisplayName', 'className', 'subjectName', 'title', 'description', 'outcome', 'scope', 'learning', 'revision', 'feedback', 'approval', 'acceptance',
      'specification', 'supportTitles', 'period', 'formalTopic', 'situation', 'objective', 'memberTopics',
      'concern', 'result', 'milestone', 'formalFeedback', 'rubricName', 'categoryName', 'criterionName',
      'groupCriterionName', 'metricName', 'evidence', 'metricNote', 'rationale', 'draftRationale',
      'artifactTitles', 'artifactDescription', 'publicationNote', 'publicWebsite', 'publicPoster', 'publicData',
    ]) {
      if (!locale.example[key]) throw new Error(`Incomplete language ${language}: example.${key}`)
    }
    for (const [key, value] of Object.entries(locale.example)) {
      if (['specification', 'supportTitles', 'memberTopics', 'artifactTitles'].includes(key)) continue
      if (typeof value !== 'string' || !value.trim()) throw new Error(`Incomplete language ${language}: example.${key}`)
    }
    for (const [key, count] of [['supportTitles', 2], ['memberTopics', 3], ['artifactTitles', 3]]) {
      const values = locale.example[key]
      if (!Array.isArray(values) || values.length !== count || values.some((value) => typeof value !== 'string' || !value.trim())) {
        throw new Error(`Incomplete language ${language}: example.${key}`)
      }
    }
    for (const key of ['objectives', 'intendedUsers', 'requirements', 'deliverables', 'dependencies']) {
      const values = locale.example.specification[key]
      if (!Array.isArray(values) || !values.length || values.some((value) => typeof value !== 'string' || !value.trim())) {
        throw new Error(`Incomplete language ${language}: specification.${key}`)
      }
    }
    if (typeof locale.example.specification.scope !== 'string' || !locale.example.specification.scope.trim()) {
      throw new Error(`Incomplete language ${language}: specification.scope`)
    }
  }
  for (const role of roles) {
    if (!locale.guides?.[role]?.title || !locale.guides[role].intro || !locale.guides[role].chapters?.length) {
      throw new Error(`Incomplete language ${language}: ${role}`)
    }
  }
  const images = new Set()
  for (const guide of Object.values(locale.guides)) {
    for (const chapter of guide.chapters) {
      if (!/^[a-z-]+$/.test(chapter.id) || !chapter.title || !chapter.text) {
        throw new Error(`Incomplete language ${language}: chapter`)
      }
      for (const image of chapter.images ?? []) {
        if (!checkpoints.includes(image.key) || !image.alt || !image.caption) {
          throw new Error(`Incomplete language ${language}: image ${image.key}`)
        }
        images.add(image.key)
      }
    }
  }
  if (checkpoints.some((key) => !images.has(key))) throw new Error(`Incomplete language ${language}: checkpoint inventory`)
  return locale
}

export async function loadLocale(language, directory = new URL('./locales/', import.meta.url)) {
  if (!/^[a-z]{2}(?:-[A-Z]{2})?$/.test(language ?? '')) {
    throw new Error('Usage: make doc LANGUAGE=en|de — LANGUAGE is required.')
  }
  let locale
  try {
    locale = JSON.parse(await readFile(new URL(`${language}.json`, directory), 'utf8'))
  } catch (error) {
    throw new Error(`Unsupported or invalid language "${language}". Usage: make doc LANGUAGE=en|de. ${error.code ?? error.message}`)
  }
  return validateLocale(locale, language)
}

export async function renderGuides(root, locale) {
  const languageRoot = path.join(root, locale.language)
  await mkdir(languageRoot, { recursive: true })
  const languages = (await readdir(root, { withFileTypes: true })).filter((entry) =>
    entry.isDirectory() && /^[a-z]{2}(?:-[A-Z]{2})?$/.test(entry.name)).map((entry) => entry.name).sort()
  await writeFile(path.join(root, 'README.md'), `# ${locale.title}\n\n` +
    languages.map((language) => `- [${language}](${language}/README.md)`).join('\n') + '\n')
  await writeFile(path.join(languageRoot, 'README.md'),
    `# ${locale.title}\n\n${locale.overview}\n\n` +
    roles.map((role) => `- [${locale.guides[role].title}](${role}.md)`).join('\n') + '\n')
  for (const role of roles) {
    const guide = locale.guides[role]
    let text = `# ${guide.title}\n\n[${locale.title}](README.md)\n\n${guide.intro}\n\n`
    text += guide.chapters.map((chapter) => `- [${chapter.title}](#${chapter.id})`).join('\n') + '\n\n'
    for (const chapter of guide.chapters) {
      text += `<a id="${chapter.id}"></a>\n\n## ${chapter.title}\n\n${chapter.text}\n\n`
      for (const image of chapter.images ?? []) {
        text += `![${image.alt}](images/${image.key}.png)\n\n${image.caption}\n\n`
      }
    }
    await writeFile(path.join(languageRoot, `${role}.md`), text)
  }
}

export async function validateGuides(root, language, { secrets = [], workspace = '' } = {}) {
  const files = ['README.md', `${language}/README.md`, ...roles.map((role) => `${language}/${role}.md`)]
  const references = new Set()
  for (const file of files) {
    const text = await readFile(path.join(root, file), 'utf8')
    if ((workspace && text.includes(workspace)) || secrets.filter(Boolean).some((secret) => text.includes(secret)) ||
        /(?:file:\/\/|\/Users\/|\/home\/|Bearer\s)/.test(text)) {
      throw new Error(`Unsafe generated content: ${file}`)
    }
    for (const match of text.matchAll(/!?\[[^\]]*\]\(([^)]+)\)/g)) {
      const [target, anchor] = match[1].split('#')
      if (/^(?:https?:|\/|[A-Z]:)/i.test(target)) throw new Error(`Nonlocal guide link: ${match[1]}`)
      const resolved = path.resolve(root, path.dirname(file), target || path.basename(file))
      if (!resolved.startsWith(path.resolve(root) + path.sep)) throw new Error(`Escaping guide link: ${target}`)
      if (!(await stat(resolved)).isFile()) throw new Error(`Missing guide target: ${target}`)
      if (anchor) {
        const linkedText = await readFile(resolved, 'utf8')
        if (!linkedText.includes(`id="${anchor}"`)) throw new Error(`Missing guide anchor: ${match[1]}`)
      }
      if (target.endsWith('.png')) {
        references.add(path.basename(target, '.png'))
        const png = await readFile(resolved)
        try { validatePng(png) } catch (error) { throw new Error(`Invalid PNG ${target}: ${error.message}`) }
      }
    }
  }
  if (checkpoints.some((key) => !references.has(key)) || references.size !== checkpoints.length) {
    throw new Error('Guide image checkpoint inventory is incomplete')
  }
  return { files, checkpoints: [...references].sort(), viewport: desktop }
}

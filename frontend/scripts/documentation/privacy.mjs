import assert from 'node:assert/strict'

export function assertPublicBoundary({ catalogue, dom, downloads, sentinels, privateArtifactId }) {
  assert.ok(Array.isArray(catalogue.content))
  assert.equal(catalogue.content.length, 3)
  assert.deepEqual(catalogue.content.map((entry) => entry.type).sort(), ['POSTER', 'SOURCE_DATA', 'WEBSITE_ARCHIVE'])
  assert.equal(downloads.length, 3)
  for (const entry of catalogue.content) {
    assert.deepEqual(Object.keys(entry).sort(), ['id', 'size', 'type'], 'Public metadata must use the independent public allowlist')
    assert.equal(typeof entry.id, 'string')
    assert.equal(typeof entry.type, 'string')
    assert.equal(typeof entry.size, 'number')
    assert.ok(entry.size > 0)
    assert.notEqual(entry.id, privateArtifactId, 'Private sentinel must not be listed')
  }
  const strings = []
  const visit = (value) => {
    if (typeof value === 'string') strings.push(value)
    else if (Array.isArray(value)) value.forEach(visit)
    else if (value && typeof value === 'object') Object.values(value).forEach(visit)
  }
  visit(catalogue)
  strings.push(dom, ...downloads)
  for (const forbidden of [...sentinels, privateArtifactId]) {
    assert.equal(typeof forbidden, 'string')
    assert.ok(forbidden.length > 0)
    assert.ok(strings.every((value) => !value.includes(forbidden)),
      `Private sentinel or artifact ID in anonymous surface: ${forbidden}`)
  }
}

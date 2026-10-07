import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const styles = readFileSync(resolve(process.cwd(), 'src/styles.css'), 'utf8')

describe('shared form and responsive layout styles', () => {
  it('gives native select and input controls a shared 40px height and prevents grid-field offsets', () => {
    expect(styles).toMatch(/\.input,\s*\.select\s*\{\s*height:\s*40px;/)
    expect(styles).toMatch(/\.grid\s*>\s*\.field\s*\+\s*\.field\s*\{\s*margin-top:\s*0;/)
    expect(styles).toMatch(/\.grid\s*\+\s*\.field,\s*\.grid\s*\+\s*\.label\s*\{\s*margin-top:\s*14px;/)
    expect(styles).toMatch(/\.field\s*\{\s*min-width:\s*0;\s*display:\s*grid;\s*align-content:\s*start;/)
    expect(styles).toMatch(/:focus-visible\s*\{\s*outline:\s*3px solid #3c76e8;/)
  })

  it('keeps Overview groups stretched and the form grids responsive at narrow widths', () => {
    expect(styles).toMatch(/\.overview-stats,\s*\.overview-workflow\s*\{\s*align-items:\s*stretch;/)
    expect(styles).toMatch(/\.grid\s*>\s*\.panel\s*\+\s*\.panel\s*\{\s*margin-top:\s*0;/)
    expect(styles).toMatch(/\.grid-2\s*\{\s*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\);/)
    expect(styles).toMatch(/\.grid-3\s*\{\s*grid-template-columns:\s*repeat\(3,\s*minmax\(0,\s*1fr\)\);/)
    expect(styles).toMatch(/@media\s*\(max-width:\s*560px\)\s*\{[^}]*\.grid-2,\s*\.grid-3\s*\{\s*grid-template-columns:\s*1fr;/)
  })
})

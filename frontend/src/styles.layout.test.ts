import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const styles = readFileSync(resolve(process.cwd(), 'src/styles.css'), 'utf8')
const selectViews = [
  'features/ideas/IdeasPage.vue',
  'features/applications/ApplicationsPage.vue',
  'features/allocation/AllocationPage.vue',
  'features/formal/FormalApplicationPage.vue',
  'features/archive/ArchivePage.vue',
  'features/grading/GradeFields.vue',
  'features/grading/GradingPage.vue',
  'features/admin/AdminPage.vue',
]

describe('shared form and responsive layout styles', () => {
  it('marks the Administration filter controls as one shared height group', () => {
    const admin = readFileSync(resolve(process.cwd(), 'src/features/admin/AdminPage.vue'), 'utf8')
    const roleFilter = admin.match(/<select id="account-role-filter"[^>]*>/)?.[0]
    const searchInput = admin.match(/<input id="account-search"[^>]*>/)?.[0]
    expect(roleFilter).toMatch(/\bclass="[^"]*\bfilter-control\b/)
    expect(searchInput).toMatch(/\bclass="[^"]*\bfilter-control\b/)
    const clearButton = admin.match(/<button\b[^>]*>Clear filters<\/button>/)?.[0]
    expect(clearButton).toMatch(/\bclass="[^"]*\bfilter-control\b/)
  })

  it('gives native select and input controls a shared 40px height and prevents grid-field offsets', () => {
    expect(styles).toMatch(/\.input,\s*\.select\s*\{\s*height:\s*40px;/)
    expect(styles).toMatch(/\.grid\s*>\s*\.field\s*\+\s*\.field\s*\{\s*margin-top:\s*0;/)
    expect(styles).toMatch(/\.grid\s*\+\s*\.field,\s*\.grid\s*\+\s*\.label\s*\{\s*margin-top:\s*14px;/)
    expect(styles).toMatch(/\.field\s*\{\s*min-width:\s*0;\s*display:\s*grid;\s*align-content:\s*start;/)
    expect(styles).toMatch(/:focus-visible\s*\{\s*outline:\s*3px solid #3c76e8;/)
  })

  it('gives shared selectors adjacent form-control typography, borders, spacing, disabled, and focus styles', () => {
    expect(styles).toMatch(/\.input,\s*\.select,\s*\.textarea\s*\{\s*width:\s*100%;\s*border:\s*1px solid #d8dfeb;\s*color:\s*#1c2738;\s*background-color:\s*#fff;\s*border-radius:\s*8px;\s*padding:\s*10px 12px;\s*font-size:\s*13px;\s*line-height:\s*1\.4;/)
    expect(styles).toMatch(/\.select\s*\{\s*appearance:\s*none;\s*padding-right:\s*36px;/)
    expect(styles).toMatch(/\.input:disabled,\s*\.select:disabled,\s*\.textarea:disabled\s*\{\s*color:\s*#727f91;\s*background-color:\s*#f2f4f8;/)
    expect(styles).toMatch(/@media\s*\(forced-colors:\s*active\)\s*\{\s*\.select\s*\{\s*appearance:\s*auto;\s*background-image:\s*none;/)
  })

  it('uses the shared select class for native dropdowns across every ticket-scoped feature', () => {
    for (const view of selectViews) {
      const source = readFileSync(resolve(process.cwd(), 'src', view), 'utf8')
      const tags = source.match(/<select\b[^>]*>/g) ?? []
      expect(tags.length, `${view} should render native dropdowns`).toBeGreaterThan(0)
      for (const tag of tags) {
        expect(tag, `${view}: ${tag}`).toMatch(/\bclass="[^"]*\bselect\b[^"]*"/)
      }
    }
  })

  it('keeps Overview groups stretched and the form grids responsive at narrow widths', () => {
    expect(styles).toMatch(/\.overview-stats,\s*\.overview-workflow\s*\{\s*align-items:\s*stretch;/)
    expect(styles).toMatch(/\.grid\s*>\s*\.panel\s*\+\s*\.panel\s*\{\s*margin-top:\s*0;/)
    expect(styles).toMatch(/\.grid-2\s*\{\s*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\);/)
    expect(styles).toMatch(/\.grid-3\s*\{\s*grid-template-columns:\s*repeat\(3,\s*minmax\(0,\s*1fr\)\);/)
    expect(styles).toMatch(/@media\s*\(max-width:\s*560px\)\s*\{[^}]*\.grid-2,\s*\.grid-3\s*\{\s*grid-template-columns:\s*1fr;/)
  })
})

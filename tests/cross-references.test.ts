import { describe, it, expect } from 'vitest'
import { readFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'

const ROOT = join(import.meta.dirname, '..')

function read(filename: string): string {
  const path = join(ROOT, filename)
  if (!existsSync(path)) return ''
  return readFileSync(path, 'utf-8')
}

describe('Cross-references between spec and adapters', () => {
  const agents = read('AGENTS.md')

  const adapters = [
    { file: 'CLAUDE.md', name: 'Claude Code' },
    { file: 'CODEX.md', name: 'Codex CLI' },
    { file: 'GEMINI.md', name: 'Gemini CLI' },
    { file: '.cursorrules', name: 'Cursor' },
    { file: '.factory/AGENTS.md', name: 'Factory Droid' },
  ]

  for (const adapter of adapters) {
    it(`${adapter.name} adapter should reference AGENTS.md`, () => {
      const content = read(adapter.file)
      expect(content).toContain('AGENTS.md')
    })
  }

  it('AGENTS.md should mention all 5 harnesses', () => {
    expect(agents).toContain('factory_droid')
    expect(agents).toContain('claude_code')
    expect(agents).toContain('cursor')
    expect(agents).toContain('codex')
    expect(agents).toContain('gemini')
  })

  it('AGENTS.md should reference all adapter config files', () => {
    expect(agents).toContain('.factory/AGENTS.md')
    expect(agents).toContain('CLAUDE.md')
    expect(agents).toContain('.cursorrules')
    expect(agents).toContain('CODEX.md')
    expect(agents).toContain('GEMINI.md')
  })
})

describe('AGENTS.md internal consistency', () => {
  const agents = read('AGENTS.md')

  it('should define the Prime Directive', () => {
    expect(agents).toContain('Prime Directive')
  })

  it('should define task complexity levels T0-T4', () => {
    for (const level of ['T0', 'T1', 'T2', 'T3', 'T4']) {
      expect(agents).toContain(level)
    }
  })

  it('should define 4 memory layers (L1-L4)', () => {
    expect(agents).toContain('L1')
    expect(agents).toContain('L4')
    expect(agents).toContain('Working')
    expect(agents).toContain('Episodic')
    expect(agents).toContain('Semantic')
    expect(agents).toContain('Procedural')
  })

  it('should define verification chain steps', () => {
    expect(agents).toContain('type_check')
    expect(agents).toContain('lint')
    expect(agents).toContain('unit_tests')
    expect(agents).toContain('security_scan')
  })

  it('should define cost budgets per complexity', () => {
    expect(agents).toMatch(/budget_per_task/)
  })

  it('should define circuit breaker triggers', () => {
    expect(agents.toLowerCase()).toContain('circuit')
    expect(agents).toContain('consecutive_failures')
  })

  it('should mention all code quality sections', () => {
    expect(agents).toContain('CODE QUALITY')
    expect(agents).toContain('TypeScript')
    expect(agents).toContain('Python')
    expect(agents).toContain('Rust')
  })

  it('should contain SPARC methodology', () => {
    expect(agents).toContain('SPARC')
    expect(agents).toContain('Specification')
    expect(agents).toContain('Pseudocode')
    expect(agents).toContain('Architecture')
    expect(agents).toContain('Refinement')
    expect(agents).toContain('Completion')
  })
})

describe('Repository governance files', () => {
  it('should have a LICENSE file', () => {
    expect(existsSync(join(ROOT, 'LICENSE'))).toBe(true)
  })

  it('should have a CHANGELOG.md', () => {
    expect(existsSync(join(ROOT, 'CHANGELOG.md'))).toBe(true)
  })

  it('should have a SECURITY.md', () => {
    expect(existsSync(join(ROOT, '.github', 'SECURITY.md'))).toBe(true)
  })

  it('should have a stale workflow', () => {
    expect(existsSync(join(ROOT, '.github', 'workflows', 'stale.yml'))).toBe(true)
  })

  it('should have a CodeQL workflow', () => {
    expect(existsSync(join(ROOT, '.github', 'workflows', 'codeql.yml'))).toBe(true)
  })

  it('should have tsconfig.json', () => {
    expect(existsSync(join(ROOT, 'tsconfig.json'))).toBe(true)
  })

  it('should have vitest.config.ts', () => {
    expect(existsSync(join(ROOT, 'vitest.config.ts'))).toBe(true)
  })

  it('should have markdownlint config', () => {
    expect(existsSync(join(ROOT, '.markdownlint-cli2.jsonc'))).toBe(true)
  })

  it('LICENSE should contain copyright', () => {
    const content = read('LICENSE')
    expect(content).toContain('Copyright')
    expect(content).toContain('aegntic')
  })

  it('CHANGELOG should follow keep-a-changelog format', () => {
    const content = read('CHANGELOG.md')
    expect(content).toContain('## [3.0.0]')
    expect(content).toMatch(/Added|Changed|Fixed|Removed|Security/)
  })

  it('SECURITY.md should have reporting instructions', () => {
    const content = read('.github/SECURITY.md')
    expect(content.toLowerCase()).toContain('reporting')
    expect(content.toLowerCase()).toContain('vulnerability')
  })
})

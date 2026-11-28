import { readFileSync } from 'node:fs'
import { expect, expectTypeOf, test } from 'vitest'
import {
  PARAMETER_PLACES,
  PROBLEM_CODE_PATTERN,
  TRACE_ID_PATTERN,
  type ProblemParameter,
} from './problems.ts'

// The generated types carry no `pattern`, so the two patterns problems.ts holds are pinned here to
// the contract the types come from: a change to either fails this test, and is then made in
// problems.ts too (ADR-161).

const CONTRACT = readFileSync(
  new URL('../../../../../contracts/client-api/v1/openapi.yaml', import.meta.url),
  'utf8',
)

/** The lines of one schema under `components.schemas`, which the contract indents by four spaces. */
function schemaBlock(name: string): string[] {
  const lines = CONTRACT.split(/\r?\n/)
  const start = lines.indexOf(`    ${name}:`)
  expect(start, `the contract has no schema ${name}`).toBeGreaterThan(-1)
  const rest = lines.slice(start + 1)
  const end = rest.findIndex((line) => /^ {4}\S/.test(line))
  return end === -1 ? rest : rest.slice(0, end)
}

/** The `pattern` a schema states for one of its properties, without the YAML quotes. */
function patternOf(schema: string, property: string): string {
  const block = schemaBlock(schema)
  const start = block.findIndex((line) => line.trimEnd() === `        ${property}:`)
  expect(start, `${schema} has no property ${property}`).toBeGreaterThan(-1)
  const rest = block.slice(start + 1)
  const end = rest.findIndex((line) => /^ {8}\S/.test(line))
  const pattern = (end === -1 ? rest : rest.slice(0, end))
    .map((line) => /^\s+pattern: '(.*)'$/.exec(line))
    .find((match) => match !== null)
  expect(pattern, `${schema}.${property} states no pattern`).toBeDefined()
  return pattern![1]
}

test('GivenTheContract_WhenAProblemsCodeIsDescribed_ThenItsPatternIsTheOneProblemsHolds', () => {
  expect(patternOf('Problem', 'code')).toBe(PROBLEM_CODE_PATTERN.source)
  expect(patternOf('ProblemItem', 'code')).toBe(PROBLEM_CODE_PATTERN.source)
})

test('GivenTheContract_WhenAProblemsTraceIdIsDescribed_ThenItsPatternIsTheOneProblemsHolds', () => {
  expect(patternOf('Problem', 'traceId')).toBe(TRACE_ID_PATTERN.source)
})

test('GivenTheGeneratedUnion_WhenThePlacesAreListed_ThenNoneIsMissing', () => {
  expectTypeOf<Exclude<ProblemParameter['in'], (typeof PARAMETER_PLACES)[number]>>().toBeNever()
  expect(new Set(PARAMETER_PLACES).size).toBe(PARAMETER_PLACES.length)
})

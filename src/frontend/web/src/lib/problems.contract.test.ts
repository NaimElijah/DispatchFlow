import contract from '../../../../../contracts/client-api/v1/openapi.yaml?raw'
import { expect, test } from 'vitest'
import { PROBLEM_CODE_PATTERN, TRACE_ID_PATTERN } from './problems.ts'

// problems.ts types the contract's Problem by hand until @dispatchflow/api-client generates it
// (#148, ADR-153). This test pins the parts of the contract that problems.ts reads, so a change to
// them fails here, and is then made in problems.ts too. #148 deletes it with the hand-written types.

/** The lines of one schema under `components.schemas`, which the contract indents by four spaces. */
function schemaBlock(name: string): string[] {
  const lines = contract.split(/\r?\n/)
  const start = lines.indexOf(`    ${name}:`)
  expect(start, `the contract has no schema ${name}`).toBeGreaterThan(-1)
  const rest = lines.slice(start + 1)
  const end = rest.findIndex((line) => /^ {4}\S/.test(line))
  return end === -1 ? rest : rest.slice(0, end)
}

/** The entries of a list or the keys of a map that sits directly under `key` in a schema block. */
function entriesUnder(block: string[], key: 'required' | 'properties' | 'enum'): string[] {
  const start = block.findIndex(
    (line) => line.trimEnd() === `      ${key}:` || line.trim() === `${key}:`,
  )
  expect(start, `no ${key} in the block`).toBeGreaterThan(-1)
  const indent = block[start].length - block[start].trimStart().length
  const entries: string[] = []
  for (const line of block.slice(start + 1)) {
    const depth = line.length - line.trimStart().length
    if (line.trim() === '' || depth <= indent) {
      break
    }
    const item = /^\s*- (\S+)$/.exec(line)
    const property = /^\s*([A-Za-z]+):/.exec(line)
    if (item !== null && depth === indent + 2) {
      entries.push(item[1])
    } else if (property !== null && depth === indent + 2) {
      entries.push(property[1])
    }
  }
  return entries.sort()
}

test('GivenTheContractsProblem_WhenItsFieldsAreRead_ThenTheyAreTheOnesTheHandWrittenTypeHolds', () => {
  const problem = schemaBlock('Problem')

  expect(entriesUnder(problem, 'required')).toEqual(
    ['type', 'title', 'status', 'code', 'params', 'traceId'].sort(),
  )
  // `detail` is in the contract but never set by the core, so the type leaves it out.
  expect(entriesUnder(problem, 'properties')).toEqual(
    ['type', 'title', 'status', 'detail', 'instance', 'code', 'params', 'traceId', 'errors'].sort(),
  )
})

/** The `pattern` the contract gives a property directly under a schema block's `properties`. */
function patternOf(block: string[], property: string): string | undefined {
  const start = block.indexOf(`        ${property}:`)
  expect(start, `no property ${property}`).toBeGreaterThan(-1)
  for (const line of block.slice(start + 1)) {
    if (/^ {8}\S/.test(line)) {
      return undefined
    }
    const pattern = /^ {10}pattern: '(.*)'$/.exec(line)
    if (pattern !== null) {
      return pattern[1]
    }
  }
  return undefined
}

test('GivenTheContractsCodeAndTraceIdPatterns_WhenRead_ThenTheGuardChecksTheSameOnes', () => {
  const problem = schemaBlock('Problem')

  expect(patternOf(problem, 'code')).toBe(PROBLEM_CODE_PATTERN.source)
  expect(patternOf(problem, 'traceId')).toBe(TRACE_ID_PATTERN.source)
  expect(patternOf(schemaBlock('ProblemItem'), 'code')).toBe(PROBLEM_CODE_PATTERN.source)
})

test('GivenTheContractsProblemItem_WhenItsFieldsAreRead_ThenTheyAreTheOnesTheHandWrittenTypeHolds', () => {
  const item = schemaBlock('ProblemItem')

  expect(entriesUnder(item, 'required')).toEqual(['code'])
  expect(entriesUnder(item, 'properties')).toEqual(['code', 'parameter', 'params', 'pointer'])
})

test('GivenTheContractsProblemParameter_WhenItsPlacesAreRead_ThenTheyAreTheOnesTheGuardAccepts', () => {
  const parameter = schemaBlock('ProblemParameter')

  expect(entriesUnder(parameter, 'required')).toEqual(['in', 'name'])
  expect(
    entriesUnder(parameter.slice(parameter.findIndex((line) => line.trim() === 'in:')), 'enum'),
  ).toEqual(['cookie', 'header', 'path', 'query'])
})

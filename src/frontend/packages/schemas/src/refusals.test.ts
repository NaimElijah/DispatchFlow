import { expect, test } from 'vitest'
import { z } from 'zod'
import { exampleSchema } from './example.ts'
import { refusalOf } from './refusals.ts'

const SENTINEL = 'translated by the error map'

// A message set in a schema wins over the error map an app passes when it parses, and would reach
// the screen untranslated (NFR-143). Every refusal of a form left entirely wrong must come from the
// map.
test('GivenTheExampleFormLeftWrong_WhenParsedWithAnErrorMap_ThenEveryMessageComesFromTheMap', () => {
  const result = exampleSchema.safeParse(
    {
      name: '',
      notes: 'x'.repeat(501),
      phone: '123',
      depot: '',
      date: '2026-02-30',
      window: { start: '08:00', end: '' },
      weightKg: 0,
      volumeLitres: 1.234,
      serviceMinutes: 0,
      priority: 9,
    },
    { error: () => SENTINEL },
  )

  expect(result.success).toBe(false)
  const issues = result.error?.issues ?? []
  expect(issues.map((issue) => issue.path.join('.')).sort()).toEqual([
    'date',
    'depot',
    'name',
    'notes',
    'phone',
    'priority',
    'serviceMinutes',
    'volumeLitres',
    'weightKg',
    'window',
  ])
  for (const issue of issues) {
    expect(issue.message, issue.path.join('.')).toBe(SENTINEL)
  }
})

test('GivenZodsOwnChecksInAFeaturesSchema_WhenRefused_ThenTheyMapOntoTheSameCodes', () => {
  const codes = (schema: z.ZodType, value: unknown) =>
    schema
      .safeParse(value, { error: (issue) => JSON.stringify(refusalOf(issue)) })
      .error?.issues.map((issue) => JSON.parse(issue.message) as unknown)

  expect(codes(z.string().max(3), 'abcd')).toEqual([
    { code: 'TEXT_TOO_LONG', params: { maxLength: 3 } },
  ])
  expect(codes(z.string().min(1), '')).toEqual([{ code: 'REQUIRED', params: {} }])
  expect(codes(z.number().min(2), 1)).toEqual([{ code: 'TOO_SMALL', params: { min: 2 } }])
  expect(codes(z.number(), 'seven')).toEqual([{ code: 'WRONG_FORMAT', params: {} }])
  expect(codes(z.enum(['a', 'b']), 'c')).toEqual([{ code: 'INVALID_VALUE', params: {} }])
})

test('GivenACustomCheckThatNamesNoKnownCode_WhenRefused_ThenTheMapFailsLoudly', () => {
  const schema = z.string().refine(() => false, { params: { code: 'MADE_UP' } })

  // Zod runs the error map when the issues are first read, not when it parses.
  const result = schema.safeParse('x', { error: (issue) => refusalOf(issue).code })

  expect(() => result.error?.issues).toThrow(/MADE_UP/)
})

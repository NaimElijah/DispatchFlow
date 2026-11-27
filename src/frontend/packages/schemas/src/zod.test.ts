import { expect, test, vi } from 'vitest'

// ADR-069's Content Security Policy refuses code compiled at run time, and the browser reports even
// a refusal Zod catches. This file imports the package only inside its test, once `Function` is
// watched: a static import would build the schemas, and run Zod's probe, before anything watched.
test('GivenThePackagesSchemas_WhenBuiltAndParsed_ThenZodCompilesNoCode', async () => {
  const compiled: unknown[] = []
  const watched = new Proxy(Function, {
    apply: (target, thisArg, args: string[]) => {
      compiled.push(args)
      return Reflect.apply(target, thisArg, args)
    },
    construct: (target, args: string[], newTarget: NewableFunction) => {
      compiled.push(args)
      return Reflect.construct(target, args, newTarget) as object
    },
  })
  vi.stubGlobal('Function', watched)
  try {
    const { exampleSchema } = await import('./index.ts')
    const parsed = exampleSchema.safeParse({
      name: 'Ada Lovelace',
      notes: '',
      phone: '0501234567',
      depot: 'depot-north',
      date: '2026-11-26',
      window: { start: '08:00', end: '12:30' },
      weightKg: 12.5,
      volumeLitres: 0.25,
      serviceMinutes: 15,
      priority: 3,
    })
    expect(parsed.success).toBe(true)
  } finally {
    vi.unstubAllGlobals()
  }

  expect(compiled).toEqual([])
})

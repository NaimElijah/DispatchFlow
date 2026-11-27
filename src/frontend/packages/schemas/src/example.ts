import { z } from './zod.ts'
import {
  choice,
  durationMinutes,
  localDate,
  optionalNumber,
  optionalText,
  optionalTimeWindow,
  phoneNumber,
  requiredText,
  volumeLitres,
  weightKg,
  wholeNumber,
} from './fields.ts'

/** The longest name a stop's contact can have: the shared kernel's limit for a name. */
const NAME_LENGTH = 100

/** The longest note the example takes: the shared kernel's limit for a note. */
const NOTE_LENGTH = 500

/** The example's priority runs from the most important, 1, to the least, 5. */
const PRIORITY = { min: 1, max: 5 } as const

/**
 * A form that uses every building block once, for the web app's example-form test, which shows that
 * a form is a schema, a few fields and one save call. No screen uses it and nothing saves with it:
 * copy its shape, not the schema. A real form's schema sits beside it, named for what it saves. Its
 * keys are the request body's, so a 400's pointer names the field it refused.
 */
export const exampleSchema = z.object({
  name: requiredText(NAME_LENGTH),
  notes: optionalText(NOTE_LENGTH),
  phone: phoneNumber,
  depot: choice,
  date: localDate,
  window: optionalTimeWindow,
  weightKg,
  volumeLitres,
  serviceMinutes: optionalNumber(durationMinutes),
  priority: wholeNumber(PRIORITY.min, PRIORITY.max),
})

/** What the example form holds while it is filled in. */
export type ExampleFormValues = z.input<typeof exampleSchema>

/**
 * The Zod 4 schemas of both apps' forms (TS §10, TS §12): the building blocks every form's schema is
 * made of, and each form's own schema beside them. A schema checks shape for quick feedback and
 * raises the server's codes, never a message, so the app translates every refusal from
 * `@dispatchflow/i18n`; the server stays the authority on every rule.
 *
 * @module @dispatchflow/schemas
 */
export { exampleSchema, type ExampleFormValues } from './example.ts'
export {
  choice,
  durationMinutes,
  localDate,
  optionalNumber,
  optionalPhoneNumber,
  optionalText,
  optionalTimeWindow,
  phoneNumber,
  requiredText,
  timeOfDay,
  timeWindow,
  volumeLitres,
  weightKg,
  wholeNumber,
} from './fields.ts'
export { refusal, refusalOf, schemaCodes, type SchemaCode, type SchemaRefusal } from './refusals.ts'

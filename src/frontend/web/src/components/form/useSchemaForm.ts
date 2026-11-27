import { refusalOf } from '@dispatchflow/schemas'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMemo } from 'react'
import { useForm, type FieldValues, type UseFormProps, type UseFormReturn } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import type { z } from 'zod'
import { fieldErrorText } from '../problemText.ts'

/** A form's schema: a Zod object over the form's values, from `@dispatchflow/schemas`. */
export type FormSchema = z.ZodType<FieldValues, FieldValues>

/** A React Hook Form form over a schema's values, which submits the values the schema parsed. */
export type SchemaFormApi<TSchema extends FormSchema> = UseFormReturn<
  z.input<TSchema>,
  unknown,
  z.output<TSchema>
>

/**
 * A React Hook Form form checked by a schema from `@dispatchflow/schemas` (TS §10). Each refusal
 * the schema raises reaches its field already translated, with its next step, from the same texts
 * as the server's refusals: the schema names a code, and this form's error map words it.
 *
 * @param schema the form's schema; keep it at module scope, as the package exports it
 * @param options React Hook Form's options, such as the `defaultValues` the view was read with
 */
export function useSchemaForm<TSchema extends FormSchema>(
  schema: TSchema,
  options: Omit<UseFormProps<z.input<TSchema>, unknown, z.output<TSchema>>, 'resolver'> = {},
): SchemaFormApi<TSchema> {
  const { t } = useTranslation('problems')
  const resolver = useMemo(
    () =>
      zodResolver(schema, {
        error: (issue) => {
          const { code, params } = refusalOf(issue)
          return fieldErrorText(t, code, params)
        },
      }),
    [schema, t],
  )
  return useForm<z.input<TSchema>, unknown, z.output<TSchema>>({ ...options, resolver })
}

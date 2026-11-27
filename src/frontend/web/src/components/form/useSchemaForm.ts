import { refusalOf } from '@dispatchflow/schemas'
import { zodResolver } from '@hookform/resolvers/zod'
import { useCallback, useMemo, useState } from 'react'
import { useForm, type FieldValues, type UseFormProps, type UseFormReturn } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import type { z } from 'zod'
import { fieldErrorText } from '../../lib/problemText.ts'
import { createFormResets, type FormResets } from './formResets.ts'

/** A form's schema: a Zod object over the form's values, from `@dispatchflow/schemas`. */
export type FormSchema = z.ZodType<FieldValues, FieldValues>

/**
 * A React Hook Form form over a schema's values, which submits the values the schema parsed, and
 * records its resets for `SchemaForm` to hand its fields.
 */
export type SchemaFormApi<TSchema extends FormSchema> = UseFormReturn<
  z.input<TSchema>,
  unknown,
  z.output<TSchema>
> & {
  /** Each `reset` and `resetField`, for the fields that keep state of their own. */
  readonly resets: FormResets
}

/**
 * A React Hook Form form checked by a schema from `@dispatchflow/schemas` (TS §10). Each refusal
 * the schema raises reaches its field already translated, with its next step, from the same texts
 * as the server's refusals: the schema names a code, and this form's error map words it. Focus on a
 * refusal is `SchemaForm`'s, which moves it in reading order, so React Hook Form moves none.
 *
 * Its `reset` and `resetField` record each reset in `resets`, for a field such as a time field:
 * React Hook Form tells a field nothing of a reset that keeps its value, and a reset run during a
 * save leaves even `submitCount` at zero, as it is before a first save. A record read again reaches
 * the form through `reset`, never React Hook Form's `values` option, which resets uncounted.
 *
 * Like React Hook Form's own, the form is the same object on every render, and so are its `reset`
 * and `resetField`, so an effect that resets the form when its view changes runs once per view.
 *
 * @param schema the form's schema; keep it at module scope, as the package exports it
 * @param options React Hook Form's options, such as the `defaultValues` the view was read with
 */
export function useSchemaForm<TSchema extends FormSchema>(
  schema: TSchema,
  options: Omit<
    UseFormProps<z.input<TSchema>, unknown, z.output<TSchema>>,
    'resolver' | 'shouldFocusError' | 'values'
  > = {},
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
  const form = useForm<z.input<TSchema>, unknown, z.output<TSchema>>({
    ...options,
    resolver,
    shouldFocusError: false,
  })
  const [resets] = useState(createFormResets)
  const { reset, resetField } = form
  const countedReset = useCallback<typeof reset>(
    (values, keepStateOptions) => {
      reset(values, keepStateOptions)
      resets.resetAll()
    },
    [reset, resets],
  )
  const countedResetField = useCallback<typeof resetField>(
    (name, options) => {
      resetField(name, options)
      resets.resetPath(name)
    },
    [resetField, resets],
  )
  return useMemo(
    () => ({
      ...form,
      // React Hook Form keeps one form object and puts a new `formState` on it every render.
      get formState() {
        return form.formState
      },
      reset: countedReset,
      resetField: countedResetField,
      resets,
    }),
    [form, countedReset, countedResetField, resets],
  )
}

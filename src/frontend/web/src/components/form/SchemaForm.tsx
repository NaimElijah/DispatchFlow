import { Stack } from '@mantine/core'
import { useEffect, useMemo, useRef, type ReactNode } from 'react'
import type { FieldValues, UseFormReturn } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import type { z } from 'zod'
import type { RequestError, RequestFailure } from '../../lib/problems.ts'
import { ProblemMessage } from '../ProblemMessage.tsx'
import { fieldErrorText } from '../problemText.ts'
import { planProblem, type FieldFor } from './planProblem.ts'
import { SchemaFormStateContext } from './schemaFormState.ts'

/** A save as the form watches it: a TanStack Query mutation, such as `useVersionedSave`'s. */
export interface FormSave {
  readonly isPending: boolean
  readonly error: RequestError | null
}

/** What a form is made of. */
export interface SchemaFormProps<TValues extends FieldValues, TParsed> {
  /** The form, from `useSchemaForm`. */
  readonly form: UseFormReturn<TValues, unknown, TParsed>
  /** The schema the form was made with, whose keys say which of a 400's pointers are its fields. */
  readonly schema: z.ZodType
  /** Sends the values the schema parsed, such as `save.mutate({ body: values, etag })`. */
  readonly onSubmit: (values: TParsed) => void
  /** The save the form sends: the form shows its pending state and its failure. */
  readonly save: FormSave
  /** Maps a rule's code to its field, for the refusals the core gives no pointer for. */
  readonly fieldFor?: FieldFor<TValues>
  /** Reloads the record the form changes, offered when someone else changed it first. */
  readonly onReload?: () => void
  readonly children: ReactNode
}

/**
 * A form over a schema, whose refusals show the way every form's do (NFR-69). The schema's own
 * refusals sit on their fields as the user submits. The core's go onto the fields they name, and
 * what names no field goes into the error summary at the top, which then takes focus so a keyboard
 * or screen-reader user meets it first. The user's input stays in the form whatever the failure.
 * Try again, offered when the core was busy or out of reach, sends the same values again.
 *
 * A submission while the save is on its way is ignored, so a double press sends one write.
 */
export function SchemaForm<TValues extends FieldValues, TParsed>({
  form,
  schema,
  onSubmit,
  save,
  fieldFor,
  onReload,
  children,
}: SchemaFormProps<TValues, TParsed>) {
  const { t } = useTranslation('problems')
  const failure: RequestFailure | undefined = save.error?.failure
  const plan = useMemo(
    () =>
      failure?.kind === 'refused' ? planProblem(failure.problem, schema, fieldFor) : undefined,
    [failure, schema, fieldFor],
  )
  const summary = useRef<HTMLDivElement>(null)
  const applied = useRef<RequestFailure | undefined>(undefined)

  useEffect(() => {
    // Once per failure: a plan rebuilt for a new `fieldFor` must not set the errors again.
    if (failure === undefined || applied.current === failure) {
      return
    }
    applied.current = failure
    for (const { path, code, params } of plan?.onFields ?? []) {
      form.setError(path, { type: 'server', message: fieldErrorText(t, code, params) })
    }
    summary.current?.focus()
  }, [failure, plan, form, t])

  const submit = form.handleSubmit((values) => {
    if (!save.isPending) {
      onSubmit(values)
    }
  })

  return (
    <SchemaFormStateContext value={{ pending: save.isPending }}>
      <form noValidate onSubmit={(event) => void submit(event)}>
        <Stack gap="md">
          {failure !== undefined && (
            <ProblemMessage
              ref={summary}
              failure={failure}
              items={plan?.items}
              onField={plan?.onField}
              onReload={onReload}
              onRetry={() => void submit()}
            />
          )}
          {children}
        </Stack>
      </form>
    </SchemaFormStateContext>
  )
}

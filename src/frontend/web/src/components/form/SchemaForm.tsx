import { Stack } from '@mantine/core'
import { useEffect, useMemo, useRef, type BaseSyntheticEvent, type ReactNode } from 'react'
import { useFormState, type FieldValues, type UseFormReturn } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import type { z } from 'zod'
import { RequestError, type RequestFailure } from '../../lib/problems.ts'
import { ProblemMessage } from '../ProblemMessage.tsx'
import { fieldErrorText } from '../../lib/problemText.ts'
import { planProblem, type FieldFor } from './planProblem.ts'
import { SchemaFormStateContext } from './schemaFormState.ts'

/**
 * A control the schema refused, as a field marks it: the inputs and buttons a keyboard can reach,
 * never a hidden input that only carries a value, nor the wrapper Mantine also marks.
 */
const REFUSED_CONTROL = [
  'input[aria-invalid="true"]:not([type="hidden"])',
  'textarea[aria-invalid="true"]',
  'select[aria-invalid="true"]',
  'button[aria-invalid="true"]',
].join(', ')

/** A save as the form watches it: a TanStack Query mutation, such as `useVersionedSave`'s. */
export interface FormSave {
  readonly isPending: boolean
  /** The save's last failure: a `RequestError` for a failed call, any other error for a bug. */
  readonly error: Error | null
  /** Forgets the save's last failure, so its message goes once the screen reloads the record. */
  readonly reset: () => void
}

/** What a form is made of. */
export interface SchemaFormProps<TValues extends FieldValues, TParsed> {
  /** The form, from `useSchemaForm`, whose resets the form's fields are told of. */
  readonly form: UseFormReturn<TValues, unknown, TParsed> & { readonly resetCount: number }
  /** The schema the form was made with, whose keys say which of a 400's pointers are its fields. */
  readonly schema: z.ZodType
  /**
   * Sends the values the schema parsed and returns the save's promise, such as
   * `save.mutateAsync({ body: values, etag })`, so the form holds a second press until it settles.
   * A save's own failure shows through `save.error`, so the form settles it quietly.
   */
  readonly onSubmit: (values: TParsed) => Promise<unknown>
  /** The save the form sends: the form shows its pending state and its failure. */
  readonly save: FormSave
  /** Maps a rule's code to its field, for the refusals the core gives no pointer for. */
  readonly fieldFor?: FieldFor<TValues>
  /**
   * Reloads the record the form changes, offered when someone else changed it first or the core
   * failed. The form drops the failure's message first.
   */
  readonly onReload?: () => void
  /** The form's fields and its save button. */
  readonly children: ReactNode
}

/**
 * A form over a schema, whose refusals show the way every form's do (NFR-69). The schema's own
 * refusals sit on their fields as the user submits, and focus moves to the first refused control in
 * reading order, a window's own refusal included. The core's go onto the fields they name, and
 * what names no field goes into the error summary at the top, which then takes focus so a keyboard
 * or screen-reader user meets it first. The user's input stays in the form whatever the failure.
 * Try again, offered when the core was busy or out of reach, sends the same values again.
 *
 * A submission while the save is on its way is ignored, so a double press sends one write, even
 * two presses closer together than TanStack Query reports the first as pending. The form asks the
 * browser not to remember what it holds: its values are records the server sent, which must not
 * outlive the session (ADR-076).
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
  // A bug is no failure the user can act on: it shows nothing here, and fails loudly elsewhere.
  const failure: RequestFailure | undefined =
    save.error instanceof RequestError ? save.error.failure : undefined
  const plan = useMemo(
    () =>
      failure?.kind === 'refused' ? planProblem(failure.problem, schema, fieldFor) : undefined,
    [failure, schema, fieldFor],
  )
  const formElement = useRef<HTMLFormElement>(null)
  const summary = useRef<HTMLDivElement>(null)
  const applied = useRef<RequestFailure | undefined>(undefined)
  const sending = useRef(false)

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

  const { submitCount, isSubmitSuccessful } = useFormState({ control: form.control })
  useEffect(() => {
    // React Hook Form would focus in the order fields registered, which skips a refusal of a group
    // such as a window; the first refused control in reading order is the one a user meets first.
    if (submitCount === 0 || isSubmitSuccessful) {
      return
    }
    formElement.current?.querySelector<HTMLElement>(REFUSED_CONTROL)?.focus()
  }, [submitCount, isSubmitSuccessful])

  // Built on each press rather than while rendering: its callback reads the `sending` flag, which
  // only an event may touch.
  const submit = (event?: BaseSyntheticEvent) =>
    form.handleSubmit(async (values) => {
      if (sending.current || save.isPending) {
        return
      }
      sending.current = true
      try {
        await onSubmit(values)
      } catch (error) {
        // The save's failure is already the save's state, which the summary shows; anything else
        // is a bug, and fails loudly.
        if (!(error instanceof RequestError)) {
          throw error
        }
      } finally {
        sending.current = false
      }
    })(event)

  return (
    <SchemaFormStateContext value={{ pending: save.isPending, resetCount: form.resetCount }}>
      <form
        ref={formElement}
        noValidate
        autoComplete="off"
        onSubmit={(event) => void submit(event)}
      >
        <Stack gap="md">
          {failure !== undefined && (
            <ProblemMessage
              ref={summary}
              operation="write"
              failure={failure}
              items={plan?.items}
              onField={plan?.onField}
              onReload={
                onReload === undefined
                  ? undefined
                  : () => {
                      save.reset()
                      onReload()
                    }
              }
              onRetry={() => void submit()}
            />
          )}
          {children}
        </Stack>
      </form>
    </SchemaFormStateContext>
  )
}

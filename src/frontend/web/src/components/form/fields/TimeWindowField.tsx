import { Fieldset, Input } from '@mantine/core'
import { useId } from 'react'
import {
  get,
  useFormState,
  type FieldError,
  type FieldPathByValue,
  type FieldValues,
} from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import type { FieldProps } from './fieldProps.ts'
import { TimeField } from './TimeField.tsx'
import classes from './TimeWindowField.module.css'

/** A time window as the form holds it: its two ends, `HH:mm` each, or empty for none. */
export interface TimeWindowValue {
  readonly start: string
  readonly end: string
}

/** What a time-window field takes: the window's name, and a legend in place of a label. */
export interface TimeWindowFieldProps<TValues extends FieldValues, TParsed = TValues> extends Omit<
  FieldProps<TValues, TimeWindowValue, TParsed>,
  'label' | 'description'
> {
  /** What the window is for, already translated, such as "Opening hours". */
  readonly legend: string
}

/**
 * A time window bound to the form, from one time to another: a group named by its legend, with a
 * From and a To time field. An error on the window as a whole, such as an end before the start or
 * only one of the two ends, shows once under the pair, tied to the group, and marks both ends.
 */
export function TimeWindowField<TValues extends FieldValues, TParsed = TValues>({
  control,
  name,
  legend,
  required,
}: TimeWindowFieldProps<TValues, TParsed>) {
  const { t } = useTranslation('common')
  const errorId = useId()
  const { errors } = useFormState({ control, name })
  const windowError: FieldError | undefined = get(errors, name)
  const error = windowError?.message
  // The window's own path joined with an end's key is that end's path in the same form.
  const end = (key: keyof TimeWindowValue) => `${name}.${key}` as FieldPathByValue<TValues, string>

  return (
    <Fieldset legend={legend} aria-describedby={error === undefined ? undefined : errorId}>
      <div className={classes.ends}>
        <TimeField
          control={control}
          name={end('start')}
          label={t('timeWindow.start')}
          required={required}
          invalid={error !== undefined}
        />
        <TimeField
          control={control}
          name={end('end')}
          label={t('timeWindow.end')}
          required={required}
          invalid={error !== undefined}
        />
      </div>
      {error !== undefined && (
        <Input.Error id={errorId} mt="xs">
          {error}
        </Input.Error>
      )}
    </Fieldset>
  )
}

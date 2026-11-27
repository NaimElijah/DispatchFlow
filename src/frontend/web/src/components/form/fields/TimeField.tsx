import { TimePicker } from '@mantine/dates'
import { useId } from 'react'
import { useController, type FieldValues } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import type { FieldProps } from './fieldProps.ts'

/** What a time field takes beyond every field's props. */
export interface TimeFieldProps<TValues extends FieldValues, TParsed = TValues> extends FieldProps<
  TValues,
  string,
  TParsed
> {
  /** Marks the field invalid when an error of a group around it, such as its window's, covers it. */
  readonly invalid?: boolean
}

/**
 * A time of day bound to the form, on a 24-hour clock in both languages (NFR-68): Mantine's time
 * picker, with a box for the hours and one for the minutes. Typing `0830` fills both, and the arrow
 * keys step each. The form holds `HH:mm`, or an empty text until both boxes are filled.
 *
 * The boxes run hours then minutes from left to right in both directions, as a clock is read. Each
 * box is named after the field, and both are tied to the field's error, which Mantine itself ties
 * only to the picker's outer element.
 */
export function TimeField<TValues extends FieldValues, TParsed = TValues>({
  control,
  name,
  label,
  description,
  required,
  invalid = false,
}: TimeFieldProps<TValues, TParsed>) {
  const { t } = useTranslation('common')
  const id = useId()
  const {
    field: { ref, value, onChange, onBlur },
    fieldState,
  } = useController({ control, name })
  const error = fieldState.error?.message
  // Mantine's own ids for the error and the description, from the id given to the picker.
  const describedBy =
    [error === undefined ? undefined : `${id}-error`, description && `${id}-description`]
      .filter(Boolean)
      .join(' ') || undefined
  const boxProps = {
    'aria-describedby': describedBy,
    'aria-invalid': error !== undefined || invalid,
  }

  return (
    <TimePicker
      id={id}
      label={label}
      description={description}
      required={required}
      error={error ?? invalid}
      value={value ?? ''}
      onChange={onChange}
      onBlur={onBlur}
      hoursRef={ref}
      hoursInputLabel={t('timeOfDay.hours', { label })}
      minutesInputLabel={t('timeOfDay.minutes', { label })}
      hoursInputProps={boxProps}
      minutesInputProps={boxProps}
      // A time reads hours then minutes from left to right in Hebrew too, so its boxes never flip.
      attributes={{ fieldsGroup: { dir: 'ltr' } }}
    />
  )
}

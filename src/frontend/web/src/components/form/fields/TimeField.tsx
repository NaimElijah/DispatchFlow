import { TimePicker } from '@mantine/dates'
import { useId, useState } from 'react'
import { useController, type FieldPath, type FieldValues } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import type { FieldProps } from './fieldProps.ts'

/** A group around a time field, such as its time window, whose own refusal covers the field. */
export interface TimeFieldGroup<TValues extends FieldValues> {
  /** The group's path, checked again as the field changes once the form was first sent. */
  readonly name: FieldPath<TValues>
  /** The ID of the group's refusal while it has one: it marks the field invalid and describes it. */
  readonly errorId: string | undefined
}

/** What a time field takes beyond every field's props. */
export interface TimeFieldProps<TValues extends FieldValues, TParsed = TValues> extends FieldProps<
  TValues,
  string,
  TParsed
> {
  /** The group the field sits in, when one has a refusal of its own. */
  readonly group?: TimeFieldGroup<TValues>
}

/**
 * A time of day bound to the form, on a 24-hour clock in both languages (NFR-68): Mantine's time
 * picker, with a box for the hours and one for the minutes. Typing `0830` fills both, and the arrow
 * keys step each. The form holds `HH:mm`, or an empty text until both boxes are filled.
 *
 * The boxes run hours then minutes from left to right in both directions, as a clock is read:
 * Mantine sets the boxes' `dir` to `ltr` itself. Each
 * box is named after the field, and both are tied to the field's error, which Mantine itself ties
 * only to the picker's outer element, and to the refusal of the group it sits in.
 */
export function TimeField<TValues extends FieldValues, TParsed = TValues>({
  control,
  name,
  label,
  description,
  required,
  group,
}: TimeFieldProps<TValues, TParsed>) {
  const { t } = useTranslation('common')
  const id = useId()
  const {
    field: { ref, value, onChange, onBlur },
    fieldState,
  } = useController({
    control,
    name,
    // Once the form was sent, a change re-checks the group, so its refusal goes once put right.
    rules: group === undefined ? undefined : { deps: group.name },
  })
  // Mantine's picker sends an empty text as soon as one box is emptied, and reads an empty text
  // coming back as a reset that empties every box. While the empty text is the picker's own, it
  // gets none back, so the other box keeps what was typed; one the form sets, as a reset does,
  // still empties both.
  const [sentEmpty, setSentEmpty] = useState(false)
  const time: string = value ?? ''
  const error = fieldState.error?.message
  // Mantine's own ids for the error and the description, from the id given to the picker.
  const groupErrorId = group?.errorId
  const describedBy =
    [
      error === undefined ? undefined : `${id}-error`,
      groupErrorId,
      description && `${id}-description`,
    ]
      .filter(Boolean)
      .join(' ') || undefined
  const invalid = error !== undefined || groupErrorId !== undefined
  const boxProps = { 'aria-describedby': describedBy, 'aria-invalid': invalid }

  return (
    <TimePicker
      id={id}
      label={label}
      description={description}
      required={required}
      error={error ?? invalid}
      value={time === '' && sentEmpty ? undefined : time}
      onChange={(next: string) => {
        setSentEmpty(next === '')
        onChange(next)
      }}
      onBlur={onBlur}
      hoursRef={ref}
      hoursInputLabel={t('timeOfDay.hours', { label })}
      minutesInputLabel={t('timeOfDay.minutes', { label })}
      hoursInputProps={boxProps}
      minutesInputProps={boxProps}
    />
  )
}

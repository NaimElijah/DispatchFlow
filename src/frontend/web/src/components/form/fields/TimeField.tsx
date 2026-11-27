import { TimePicker } from '@mantine/dates'
import { useId, useLayoutEffect, useRef, useState, useSyncExternalStore } from 'react'
import { useController, type FieldPath, type FieldValues } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useSchemaFormState } from '../schemaFormState.ts'
import type { FieldProps } from './fieldProps.ts'

/** A group around a time field, such as its time window, whose own refusal covers the field. */
export interface TimeFieldGroup<TValues extends FieldValues> {
  /** The group's path, checked again as the field changes once the form was first sent. */
  readonly name: FieldPath<TValues>
  /** The ID of the group's refusal while it has one, which marks the field invalid and says why. */
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
 * Mantine sets the boxes' `dir` to `ltr` itself. Each box is named after the field, and both are
 * tied to the field's error, which Mantine itself ties only to the picker's outer element, and to
 * the refusal of the group it sits in.
 *
 * It sits in a `SchemaForm`, which tells it of the form's resets: a reset to the empty text the
 * field already holds changes no value, so the picker would keep a half-typed time. It throws
 * outside one, as `useSchemaFormState` does.
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
  // gets none back, so the other box keeps what was typed.
  const [sentEmpty, setSentEmpty] = useState(false)
  // The picker remounts, on the form's value, on each reset that reaches the field; focus that was
  // in it moves to the new hours box, whose id stays the same so a notice can find it again.
  const { resets } = useSchemaFormState()
  const resetCount = useSyncExternalStore(resets.subscribe, () => resets.countFor(name))
  const hoursId = `${id}-hours`
  const [resetsSeen, setResetsSeen] = useState(resetCount)
  if (resetsSeen !== resetCount) {
    setResetsSeen(resetCount)
    setSentEmpty(false)
  }
  const focused = useRef(false)
  const hoursBox = useRef<HTMLInputElement | null>(null)
  useLayoutEffect(() => {
    if (focused.current) {
      hoursBox.current?.focus()
    }
  }, [resetCount])
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
      key={resetCount}
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
      onFocus={() => {
        focused.current = true
      }}
      onBlur={() => {
        focused.current = false
        onBlur()
      }}
      hoursRef={(box: HTMLInputElement | null) => {
        hoursBox.current = box
        ref(box)
      }}
      hoursInputLabel={t('timeOfDay.hours', { label })}
      minutesInputLabel={t('timeOfDay.minutes', { label })}
      hoursInputProps={{ ...boxProps, id: hoursId }}
      labelProps={{ htmlFor: hoursId }}
      minutesInputProps={boxProps}
    />
  )
}

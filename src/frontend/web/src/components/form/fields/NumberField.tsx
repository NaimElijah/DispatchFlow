import { unitSymbol, type Unit } from '@dispatchflow/formatters'
import { NumberInput } from '@mantine/core'
import { useState } from 'react'
import { useController, type FieldValues } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useLanguage } from '../../useLanguage.ts'
import type { FieldProps } from './fieldProps.ts'

/** What a number field takes beyond every field's props. */
export interface NumberFieldProps<
  TValues extends FieldValues,
  TParsed = TValues,
> extends FieldProps<TValues, number, TParsed> {
  /** The value's unit, named with the label, such as kilograms for a weight. */
  readonly unit?: Unit
  /** The decimals the field takes: 2 for a weight or a volume, none for a count or a duration. */
  readonly decimals?: number
  readonly min?: number
  readonly max?: number
}

/** What the input shows, as Mantine reports it: a number, or text such as `12.` or `0.0`. */
type Typed = number | string

/** The number a typed value means, if any: `12.` means 12, and an empty box means none. */
function numberOf(typed: Typed): number | undefined {
  if (typeof typed === 'number') {
    return typed
  }
  const value = typed.trim() === '' ? Number.NaN : Number(typed)
  return Number.isFinite(value) ? value : undefined
}

/**
 * A number field bound to the form: a weight in kilograms, a volume in litres, a duration, or a
 * plain whole number. The unit is part of its label, so a screen reader reads it with the field. It
 * takes no negative number and no more decimals than `decimals`.
 *
 * The form holds the number, or nothing while the box is empty. The box keeps what the user typed
 * while it still means the form's number, so `12.` stays on screen on the way to `12.5`. Only a new
 * number reaches the form: Mantine rewrites `1.50` as `1.5` when the user leaves the box, and that
 * must not re-run the field's check and wipe a refusal the server gave.
 */
export function NumberField<TValues extends FieldValues, TParsed = TValues>({
  control,
  name,
  label,
  description,
  required,
  unit,
  decimals = 0,
  min,
  max,
}: NumberFieldProps<TValues, TParsed>) {
  const { t } = useTranslation('common')
  const language = useLanguage()
  const { field, fieldState } = useController({ control, name })
  const [typed, setTyped] = useState<Typed>('')
  const value: number | undefined = field.value
  const shown = typed !== '' && numberOf(typed) === value ? typed : (value ?? '')

  return (
    <NumberInput
      {...field}
      value={shown}
      onChange={(next) => {
        setTyped(next)
        const number = numberOf(next)
        if (number !== value) {
          field.onChange(number)
        }
      }}
      label={
        unit === undefined ? label : t('withUnit', { label, unit: unitSymbol(unit, language) })
      }
      description={description}
      required={required}
      error={fieldState.error?.message}
      decimalScale={decimals}
      allowDecimal={decimals > 0}
      allowNegative={false}
      min={min}
      max={max}
      hideControls
    />
  )
}

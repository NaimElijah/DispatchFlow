import { Textarea } from '@mantine/core'
import { useController, type FieldValues } from 'react-hook-form'
import type { FieldProps } from './fieldProps.ts'

/** The rows a multi-line field shows before it scrolls: enough for a note to drivers. */
const VISIBLE_ROWS = 3

/** A multi-line text field bound to the form, such as a customer's notes for drivers. */
export function TextAreaField<TValues extends FieldValues, TParsed = TValues>({
  control,
  name,
  label,
  description,
  required,
}: FieldProps<TValues, string, TParsed>) {
  const { field, fieldState } = useController({ control, name })

  return (
    <Textarea
      {...field}
      value={field.value ?? ''}
      rows={VISIBLE_ROWS}
      label={label}
      description={description}
      required={required}
      error={fieldState.error?.message}
    />
  )
}

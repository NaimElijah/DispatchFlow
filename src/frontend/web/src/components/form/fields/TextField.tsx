import { TextInput } from '@mantine/core'
import { useController, type FieldValues } from 'react-hook-form'
import type { FieldProps } from './fieldProps.ts'

/**
 * A one-line text field bound to the form. Its label names it, and its error, under it, is tied to
 * it through `aria-describedby` and marks it invalid (NFR-46). It asks for no autofill, inside a
 * form or out of one: a browser reads a field named `name` as the user's own, and would keep what
 * it saw, a customer's record the server sent (ADR-076).
 */
export function TextField<TValues extends FieldValues, TParsed = TValues>({
  control,
  name,
  label,
  description,
  required,
}: FieldProps<TValues, string, TParsed>) {
  const { field, fieldState } = useController({ control, name })

  return (
    <TextInput
      {...field}
      value={field.value ?? ''}
      autoComplete="off"
      label={label}
      description={description}
      required={required}
      error={fieldState.error?.message}
    />
  )
}

import { TextInput } from '@mantine/core'
import { parsePhoneNumberFromString } from 'libphonenumber-js/min'
import { useState } from 'react'
import { useController, type FieldValues } from 'react-hook-form'
import type { FieldProps } from './fieldProps.ts'

/** A number typed without a country code is Israeli. */
const PHONE_REGION = 'IL'

/**
 * A valid number as people write it: an Israeli one in its national form, `050-123-4567`, another
 * country's in its international form. Anything else stays as typed, for the schema to refuse.
 */
function writtenForm(typed: string): string {
  const phone = parsePhoneNumberFromString(typed, PHONE_REGION)
  if (phone === undefined || !phone.isValid()) {
    return typed
  }
  return phone.country === PHONE_REGION ? phone.formatNational() : phone.formatInternational()
}

/**
 * A phone number bound to the form, written left to right in both languages, so a `+972` keeps its
 * plus sign in front. While the user types, it shows the number as typed, so the cursor never
 * jumps; once they leave the field, it shows a valid number written out properly. The form holds
 * the number as typed: writing it out changes how it looks, not the value, so it never re-runs the
 * field's check and never wipes a refusal the server gave. The server parses the number again and
 * is the authority on it (FR-377, TS §10).
 *
 * It asks for no autofill: `tel` means the user's own number, so the browser would offer the
 * dispatcher's own in a customer's field, and would keep the customers' it was given.
 */
export function PhoneField<TValues extends FieldValues, TParsed = TValues>({
  control,
  name,
  label,
  description,
  required,
}: FieldProps<TValues, string, TParsed>) {
  const { field, fieldState } = useController({ control, name })
  const { onBlur } = field
  const [editing, setEditing] = useState(false)
  const value: string = field.value ?? ''

  return (
    <TextInput
      {...field}
      value={editing ? value : writtenForm(value)}
      onFocus={() => setEditing(true)}
      onBlur={() => {
        setEditing(false)
        onBlur()
      }}
      type="tel"
      inputMode="tel"
      dir="ltr"
      label={label}
      description={description}
      required={required}
      error={fieldState.error?.message}
    />
  )
}

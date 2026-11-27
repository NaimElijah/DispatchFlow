import type { Control, FieldPathByValue, FieldValues } from 'react-hook-form'

/**
 * What every field takes: the form's `control` and the field's `name`, which is type-checked
 * against the form's values so a field only binds a value of its own kind, and its words.
 *
 * @typeParam TValues the form's values
 * @typeParam TValue the kind of value the field holds, such as `string` for a text field
 * @typeParam TParsed what the form's schema parses its values into
 */
export interface FieldProps<TValues extends FieldValues, TValue, TParsed = TValues> {
  readonly control: Control<TValues, unknown, TParsed>
  readonly name: FieldPathByValue<TValues, TValue>
  /** The field's label, already translated. */
  readonly label: string
  /** A hint under the label, already translated, such as "1 is the most important". */
  readonly description?: string
  /** Marks the field as one the form needs; the schema is what refuses it empty. */
  readonly required?: boolean
}

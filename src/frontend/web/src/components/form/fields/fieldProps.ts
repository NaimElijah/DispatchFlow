import type {
  Control,
  FieldPath,
  FieldPathByValue,
  FieldPathValue,
  FieldValues,
} from 'react-hook-form'

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

/**
 * The path of a value a number field can hold: a required number, which the schema refuses empty,
 * or an optional one that takes `null`, which the field writes while its box is empty. A path whose
 * value is `number | undefined` and never `null` is left out: its schema would refuse an emptied
 * box as missing, on a field the user may leave empty. Make an optional number with
 * `@dispatchflow/schemas`' `optionalNumber`.
 *
 * @typeParam TValues the form's values
 */
export type NumberFieldPath<TValues extends FieldValues> = {
  [Path in FieldPath<TValues>]: [FieldPathValue<TValues, Path>] extends [number | null | undefined]
    ? [FieldPathValue<TValues, Path>] extends [number]
      ? Path
      : null extends FieldPathValue<TValues, Path>
        ? Path
        : never
    : never
}[FieldPath<TValues>]

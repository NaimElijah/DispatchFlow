import { Select } from '@mantine/core'
import { useMemo } from 'react'
import { useController, type FieldValues } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import type { FieldProps } from './fieldProps.ts'

/** One choice of a select field: the value the form holds, and the words the user sees. */
export interface SelectOption {
  readonly value: string
  /** Already translated, or the record's own name, such as a depot's. */
  readonly label: string
}

/** What a select field takes beyond every field's props. */
export interface SelectFieldProps<
  TValues extends FieldValues,
  TParsed = TValues,
> extends FieldProps<TValues, string, TParsed> {
  /** The choices, in the order the list shows them. */
  readonly options: readonly SelectOption[]
}

/**
 * A choice from a list, bound to the form, such as a vehicle's depot. The user can type to narrow
 * the list, and walks it with the arrow keys. Each option's words sit in `<bdi>`, since a name may
 * mix Hebrew with numbers or Latin letters (NFR-136). The form holds the chosen value, or an empty
 * text before one is chosen.
 */
export function SelectField<TValues extends FieldValues, TParsed = TValues>({
  control,
  name,
  label,
  description,
  required,
  options,
}: SelectFieldProps<TValues, TParsed>) {
  const { t } = useTranslation('common')
  const { field, fieldState } = useController({ control, name })
  const data = useMemo(() => options.map(({ value, label }) => ({ value, label })), [options])
  const { onChange } = field
  const value: string = field.value ?? ''

  return (
    <Select
      {...field}
      value={value === '' ? null : value}
      onChange={(chosen) => onChange(chosen ?? '')}
      data={data}
      searchable
      allowDeselect={false}
      nothingFoundMessage={t('select.nothingFound')}
      renderOption={({ option }) => <bdi>{option.label}</bdi>}
      label={label}
      description={description}
      required={required}
      error={fieldState.error?.message}
    />
  )
}

import { DatePickerInput } from '@mantine/dates'
import { useController, type FieldValues } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import type { FieldProps } from './fieldProps.ts'

/** How the field writes its date: DD/MM/YYYY in both languages (NFR-68), in Day.js's tokens. */
const SHOWN_AS = 'DD/MM/YYYY'

/**
 * A calendar date bound to the form, such as a delivery date: Mantine's date picker, which opens a
 * month to choose a day from, by mouse or with the arrow keys. It shows the date as DD/MM/YYYY and
 * names its months in the interface language, with Israel's week (`DatesLanguage`). The form holds
 * the date as the API takes it, `YYYY-MM-DD`, or an empty text before one is chosen. A calendar
 * date has no time and no zone, so none is applied to it.
 */
export function DateField<TValues extends FieldValues, TParsed = TValues>({
  control,
  name,
  label,
  description,
  required,
}: FieldProps<TValues, string, TParsed>) {
  const { t } = useTranslation('common')
  const { field, fieldState } = useController({ control, name })
  const { onChange } = field
  const value: string = field.value ?? ''

  return (
    <DatePickerInput
      {...field}
      value={value === '' ? null : value}
      onChange={(chosen) => onChange(chosen ?? '')}
      valueFormat={SHOWN_AS}
      ariaLabels={{
        monthLevelControl: t('calendar.monthLevelControl'),
        yearLevelControl: t('calendar.yearLevelControl'),
        nextMonth: t('calendar.nextMonth'),
        previousMonth: t('calendar.previousMonth'),
        nextYear: t('calendar.nextYear'),
        previousYear: t('calendar.previousYear'),
        nextDecade: t('calendar.nextDecade'),
        previousDecade: t('calendar.previousDecade'),
      }}
      label={label}
      description={description}
      required={required}
      error={fieldState.error?.message}
    />
  )
}

/**
 * The English strings of the shared components every area's screens are built from: form fields,
 * the save button, the table and the date pickers. The source of the namespace's shape, which
 * `he/common.ts` must match key for key.
 *
 * `withUnit` names a number field with its unit, and `timeOfDay` names a time field's two boxes
 * after the field, so a screen reader says which time they set.
 */
export const common = {
  save: 'Save',
  saved: 'Saved.',
  reload: 'Reload',
  tryAgain: 'Try again',
  withUnit: '{{label}} ({{unit}})',
  timeOfDay: {
    hours: '{{label}}, hours',
    minutes: '{{label}}, minutes',
  },
  timeWindow: {
    start: 'From',
    end: 'To',
  },
  select: {
    nothingFound: 'Nothing matches',
  },
  calendar: {
    monthLevelControl: 'Choose a month',
    yearLevelControl: 'Choose a year',
    nextMonth: 'Next month',
    previousMonth: 'Previous month',
    nextYear: 'Next year',
    previousYear: 'Previous year',
    nextDecade: 'Next decade',
    previousDecade: 'Previous decade',
  },
} as const

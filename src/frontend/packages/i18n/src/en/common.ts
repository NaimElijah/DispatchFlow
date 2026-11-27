/**
 * The English strings of the shared components every area's screens are built from: form fields,
 * the save button, the table and the date pickers. The source of the namespace's shape, which
 * `he/common.ts` must match key for key.
 *
 * In `timeOnDate`, `<date>` marks where the screen puts the date's own element, so each language
 * keeps its own word order around it.
 */
export const common = {
  save: 'Save',
  saved: 'Saved.',
  reload: 'Reload',
  tryAgain: 'Try again',
  timeOfDay: {
    hours: 'Hours',
    minutes: 'Minutes',
  },
  timeWindow: {
    start: 'From',
    end: 'To',
  },
  timeOnDate: '{{time}} on <date>{{date}}</date>',
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

import type { common as english } from '../en/common.ts'
import type { Strings } from '../strings.ts'

/** The Hebrew strings of the shared components, key for key with `en/common.ts`. */
export const common: Strings<typeof english> = {
  save: 'שמירה',
  close: 'סגירה',
  reload: 'טעינה מחדש',
  tryAgain: 'לנסות שוב',
  withUnit: '{{label}} ({{unit}})',
  timeOfDay: {
    hours: '{{label}}, שעות',
    minutes: '{{label}}, דקות',
  },
  timeWindow: {
    start: 'משעה',
    end: 'עד שעה',
  },
  select: {
    nothingFound: 'אין התאמות',
  },
  calendar: {
    monthLevelControl: 'בחירת חודש',
    yearLevelControl: 'בחירת שנה',
    nextMonth: 'החודש הבא',
    previousMonth: 'החודש הקודם',
    nextYear: 'השנה הבאה',
    previousYear: 'השנה הקודמת',
    nextDecade: 'העשור הבא',
    previousDecade: 'העשור הקודם',
  },
}

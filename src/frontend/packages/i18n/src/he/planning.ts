import type { planning as english } from '../en/planning.ts'
import type { Strings } from '../strings.ts'

/** The Hebrew strings of the Planning area, key for key with `en/planning.ts`. */
export const planning: Strings<typeof english> = {
  heading: 'תוכנית ליום <date>{{date}}</date>',
  title: 'תוכנית ליום {{date}}',
}

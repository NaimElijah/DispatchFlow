/**
 * The English strings of the Planning area. The source of the namespace's shape, which
 * `he/planning.ts` must match.
 *
 * In `heading`, `<date>` marks where the screen puts the date's own element, so each language keeps
 * its own word order around it; `title` is the same words as plain text, for the browser tab.
 */
export const planning = {
  heading: 'Plan for <date>{{date}}</date>',
  title: 'Plan for {{date}}',
} as const

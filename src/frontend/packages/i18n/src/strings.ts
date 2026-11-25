/**
 * The shape of a namespace's strings in a language other than English: every key the English
 * namespace has, at the same depth, each holding any string. Typing the Hebrew object with it makes
 * a missing or extra key a compile error.
 */
export type Strings<T> = {
  readonly [K in keyof T]: T[K] extends string ? string : Strings<T[K]>
}

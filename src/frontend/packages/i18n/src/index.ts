/**
 * DispatchFlow's interface strings in its two languages, English and Hebrew, for both apps
 * (ADR-089, TS §12): data, types and the {@link isLanguage} guard. Each app sets up i18next itself.
 *
 * Each area has its own namespace, with one file per language: `en/<area>.ts` defines the
 * namespace's keys, and `he/<area>.ts`, typed against it, must have the same ones. The `shell`
 * namespace is each app's frame: its navigation, its not-found and error pages.
 *
 * @module @dispatchflow/i18n
 */
import { access as englishAccess } from './en/access.ts'
import { planning as englishPlanning } from './en/planning.ts'
import { shell as englishShell } from './en/shell.ts'
import { access as hebrewAccess } from './he/access.ts'
import { planning as hebrewPlanning } from './he/planning.ts'
import { shell as hebrewShell } from './he/shell.ts'
import type { Strings } from './strings.ts'

/** The interface languages, as BCP 47 codes. No other language is built (ADR-089). */
export const languages = ['en', 'he'] as const

/** One of {@link languages}. */
export type Language = (typeof languages)[number]

/**
 * Tells whether a value is one of {@link languages}, for values from outside the code, such as a
 * stored setting.
 */
export function isLanguage(value: unknown): value is Language {
  return languages.some((language) => language === value)
}

/**
 * Each language's name in that language, shown the same way on every screen so that a reader
 * finds their own language whatever the screen's language is.
 */
export const languageNames = {
  en: 'English',
  he: 'עברית',
} as const satisfies Record<Language, string>

/** Every namespace's strings, by language and then by namespace, in i18next's resource shape. */
export const resources = {
  en: { shell: englishShell, access: englishAccess, planning: englishPlanning },
  he: { shell: hebrewShell, access: hebrewAccess, planning: hebrewPlanning },
} as const satisfies Record<Language, object>

/**
 * The shape of one language's resources, every string widened to `string`, for typing an i18next
 * instance's keys: a translation is whichever language is on, so it is never typed as the English.
 */
export type Resources = Strings<(typeof resources)['en']>

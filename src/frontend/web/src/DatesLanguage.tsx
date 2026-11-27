// Mantine's date pickers name months and days through Day.js, which needs each language's names
// loaded once. This is the one file that imports Day.js: the app's own dates go through Intl and
// @dispatchflow/formatters (TS §10, ADR-153), and eslint.config.js refuses it anywhere else.
import 'dayjs/locale/he'
import { DatesProvider } from '@mantine/dates'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

/** Israel's week starts on Sunday, in Day.js's numbering of the days from 0. */
const SUNDAY = 0

/** Friday and Saturday, Israel's weekend. */
const WEEKEND = [5, 6] as const

/**
 * Sets Mantine's date pickers to the interface language, with Israel's week: it starts on Sunday,
 * and Friday and Saturday are the weekend. The pickers follow a change of language at once.
 */
export function DatesLanguage({ children }: { children: ReactNode }) {
  const { i18n } = useTranslation()

  return (
    <DatesProvider
      settings={{ locale: i18n.language, firstDayOfWeek: SUNDAY, weekendDays: [...WEEKEND] }}
    >
      {children}
    </DatesProvider>
  )
}

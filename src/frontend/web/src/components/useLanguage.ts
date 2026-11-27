import { isLanguage, type Language } from '@dispatchflow/i18n'
import { useTranslation } from 'react-i18next'
import { defaultLanguage } from '../lib/languageSetting.ts'

/**
 * The interface language, for what formats by language rather than through a string, such as a
 * quantity's unit. It follows a change of language, as `useTranslation` does.
 */
export function useLanguage(): Language {
  const { i18n } = useTranslation()
  // i18next only ever runs in one of the interface languages; the guard types it as one.
  return isLanguage(i18n.language) ? i18n.language : defaultLanguage
}

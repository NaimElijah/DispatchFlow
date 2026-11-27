import { isLanguage, type Language } from '@dispatchflow/i18n'
import { useTranslation } from 'react-i18next'

/**
 * The interface language, for what formats by language rather than through a string, such as a
 * quantity's unit. It follows a change of language, as `useTranslation` does.
 *
 * @throws Error when i18next runs in another language, which `createI18n`'s `supportedLngs` rules out
 */
export function useLanguage(): Language {
  const { i18n } = useTranslation()
  if (!isLanguage(i18n.language)) {
    throw new Error(`i18next runs in "${i18n.language}", which is no interface language`)
  }
  return i18n.language
}

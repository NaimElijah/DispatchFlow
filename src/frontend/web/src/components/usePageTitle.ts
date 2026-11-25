import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'

/**
 * Names the browser tab after the page, as "<page> · DispatchFlow", so each page has its own title
 * (WCAG 2.2 SC 2.4.2, NFR-46). The title follows a change of language.
 *
 * @param page the page's own title, already translated
 */
export function usePageTitle(page: string): void {
  const { t } = useTranslation('shell')
  const title = t('pageTitle', { page })

  useEffect(() => {
    document.title = title
  }, [title])
}

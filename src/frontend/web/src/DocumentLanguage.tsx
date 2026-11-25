import { useDirection } from '@mantine/core'
import { useLayoutEffect } from 'react'
import { useTranslation } from 'react-i18next'

/**
 * Keeps the page's language and reading direction in step with the interface language: `<html
 * lang>`, and through Mantine's `DirectionProvider` both its components' direction and `<html dir>`.
 * Renders nothing.
 *
 * A layout effect runs before the browser paints, so a change of direction, or a remembered Hebrew
 * at start, never shows a frame laid out the other way.
 */
export function DocumentLanguage() {
  const { i18n } = useTranslation()
  const { setDirection } = useDirection()
  const { language } = i18n

  useLayoutEffect(() => {
    document.documentElement.lang = language
    setDirection(i18n.dir(language))
  }, [i18n, language, setDirection])

  return null
}

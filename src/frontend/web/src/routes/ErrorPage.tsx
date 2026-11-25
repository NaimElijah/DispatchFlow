import { Button, Stack, Text, Title } from '@mantine/core'
import { useRouter, type ErrorComponentProps } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { usePageTitle } from '../components/usePageTitle.ts'

/**
 * What shows when a page, or the shell around it, throws while rendering or loading: a translated
 * message and a way to try again, never the error, its message or its stack (NFR-143).
 *
 * The error itself is not lost: React and the router report it to the console, and error reports
 * go to GlitchTip once it is set up (ADR-079).
 */
export function ErrorPage({ reset }: ErrorComponentProps) {
  const { t } = useTranslation('shell')
  const router = useRouter()
  usePageTitle(t('error.title'))

  function tryAgain() {
    reset()
    // A reload that fails again lands back on this page, through the same boundary.
    void router.invalidate()
  }

  return (
    <Stack gap="sm" align="flex-start">
      <Title order={1}>{t('error.title')}</Title>
      <Text>{t('error.body')}</Text>
      <Button onClick={tryAgain}>{t('error.retry')}</Button>
    </Stack>
  )
}

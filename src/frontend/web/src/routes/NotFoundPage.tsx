import { Stack, Text, Title } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import { TextLink } from '../components/TextLink.tsx'
import { usePageTitle } from '../components/usePageTitle.ts'

/**
 * The page for an address no route matches, such as an unknown path or a plan date that is not a
 * real date. It shows inside the shell, with a way back to the planning screen.
 */
export function NotFoundPage() {
  const { t } = useTranslation('shell')
  usePageTitle(t('notFound.title'))

  return (
    <Stack gap="sm" align="flex-start">
      <Title order={1}>{t('notFound.title')}</Title>
      <Text>{t('notFound.body')}</Text>
      <TextLink to="/">{t('notFound.home')}</TextLink>
    </Stack>
  )
}

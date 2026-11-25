import { Stack, Text, Title } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import { usePageTitle } from './usePageTitle.ts'

/** What an area's page shows until the ticket that builds its screen replaces it. */
export interface NotBuiltPageProps {
  /** The page's title, already translated. */
  title: string
}

/** A page with its title and a line saying its screen is not built yet. */
export function NotBuiltPage({ title }: NotBuiltPageProps) {
  const { t } = useTranslation('shell')
  usePageTitle(title)

  return (
    <Stack gap="sm">
      <Title order={1}>{title}</Title>
      <Text>{t('notBuilt')}</Text>
    </Stack>
  )
}

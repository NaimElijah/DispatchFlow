import { useTranslation } from 'react-i18next'
import { NotBuiltPage } from '../../components/NotBuiltPage.tsx'

/** The Fleet area's page. Its depots, vehicles, drivers and shifts are not built yet. */
export function FleetPage() {
  const { t } = useTranslation('shell')
  return <NotBuiltPage title={t('navigation.fleet')} />
}

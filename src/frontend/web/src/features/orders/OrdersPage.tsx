import { useTranslation } from 'react-i18next'
import { NotBuiltPage } from '../../components/NotBuiltPage.tsx'

/** The Orders area's page. Its customers and orders are not built yet. */
export function OrdersPage() {
  const { t } = useTranslation('shell')
  return <NotBuiltPage title={t('navigation.orders')} />
}

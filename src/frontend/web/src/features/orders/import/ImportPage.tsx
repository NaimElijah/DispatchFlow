import { useTranslation } from 'react-i18next'
import { NotBuiltPage } from '../../../components/NotBuiltPage.tsx'

/** The import page, where an orders file is uploaded and previewed (#224). Not built yet. */
export function ImportPage() {
  const { t } = useTranslation('shell')
  return <NotBuiltPage title={t('navigation.import')} />
}

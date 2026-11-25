import { isLanguage, languageNames, languages, type Language } from '@dispatchflow/i18n'
import { Radio, Stack, Title } from '@mantine/core'
import { useRouteContext } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { usePageTitle } from '../../components/usePageTitle.ts'
import { defaultLanguage } from '../../lib/languageSetting.ts'

/**
 * The Settings page: for now, the interface language (FR-124). The choice turns the whole app at
 * once and is kept for later visits in this browser, until FR-124 keeps it on the account.
 */
export function SettingsPage() {
  const { t, i18n } = useTranslation('access')
  const { languageSetting } = useRouteContext({ from: '__root__' })
  usePageTitle(t('settings.title'))
  const current = isLanguage(i18n.language) ? i18n.language : defaultLanguage

  function choose(value: string) {
    if (!isLanguage(value)) {
      throw new Error(`The language choice offered "${value}", which is not an interface language`)
    }
    languageSetting.save(value)
    // The strings are bundled, so the change completes at once and its promise cannot fail.
    void i18n.changeLanguage(value)
  }

  return (
    <Stack gap="md" align="flex-start">
      <Title order={1}>{t('settings.title')}</Title>
      <Radio.Group label={t('settings.language')} value={current} onChange={choose}>
        <Stack gap="xs" mt="xs">
          {languages.map((language: Language) => (
            <Radio
              key={language}
              value={language}
              // Each language's name in that language, so a reader finds theirs (WCAG 3.1.2).
              label={<span lang={language}>{languageNames[language]}</span>}
            />
          ))}
        </Stack>
      </Radio.Group>
    </Stack>
  )
}

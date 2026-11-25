import '@mantine/core/styles.css'
import '@fontsource-variable/heebo'
import './styles/theme.css'
import { QueryClient } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App.tsx'
import { browserStorage, createLanguageSetting } from './lib/languageSetting.ts'
import { createI18n } from './i18n.ts'
import { createAppRouter } from './router.ts'

const root = document.getElementById('root')
if (root === null) {
  throw new Error('index.html has no element with id "root" to render the web app into')
}

const languageSetting = createLanguageSetting(browserStorage())
const i18n = createI18n(languageSetting.read())
// Server data lives only here, in memory, and is never persisted (NFR-269, ADR-076).
const queryClient = new QueryClient()
const router = createAppRouter({ queryClient, now: () => new Date(), languageSetting })

createRoot(root).render(
  <StrictMode>
    <App router={router} i18n={i18n} queryClient={queryClient} />
  </StrictMode>,
)

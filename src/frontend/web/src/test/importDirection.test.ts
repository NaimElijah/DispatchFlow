import { ESLint } from 'eslint'
import { expect, test } from 'vitest'

// The project's own eslint.config.js, read from the web app's folder, where Vitest runs.
const eslint = new ESLint()

/** Lints one import line as if it sat in the given file, and says whether the import rule refused it. */
async function isRefused(filePath: string, specifier: string): Promise<boolean> {
  const [result] = await eslint.lintText(`import '${specifier}'\n`, { filePath })
  return result.messages.some((message) => message.ruleId === 'no-restricted-imports')
}

// Loading the configuration and its plugins takes a few seconds the first time.
const LINT_TIMEOUT = 30_000

test.each([
  ['src/routes/AppLayout.tsx', '../features/planning/routes.ts'],
  ['src/routes/AppLayout.tsx', '../features'],
  ['src/routes/AppLayout.tsx', '../routeTree.ts'],
  ['src/routes/AppLayout.tsx', '../test/renderApp.tsx'],
  ['src/features/fleet/FleetPage.tsx', '../../router.ts'],
  ['src/features/fleet/FleetPage.tsx', '../orders/routes.ts'],
  ['src/features/orders/import/ImportPage.tsx', '../../planning/planDate.ts'],
  ['src/components/NotBuiltPage.tsx', '../routes/shellRoute.ts'],
  ['src/components/map/BaseMap.tsx', '../../features/planning/planDate.ts'],
  ['src/lib/languageSetting.ts', '../components/TextLink.tsx'],
  ['src/lib/languageSetting.ts', '../i18n.ts'],
  ['src/components/form/fields/DateField.tsx', '../../../AppProviders.tsx'],
  ['src/features/planning/PlanPage.tsx', 'dayjs'],
  ['src/components/form/fields/DateField.tsx', 'dayjs/locale/he'],
  ['src/lib/versionedSave.ts', 'dayjs'],
  ['src/AppProviders.tsx', 'dayjs'],
])(
  'GivenAFileAt_%s_WhenItImports_%s_ThenLintRefusesTheImport',
  async (filePath, specifier) => {
    expect(await isRefused(filePath, specifier)).toBe(true)
  },
  LINT_TIMEOUT,
)

test.each([
  ['src/features/fleet/routes.ts', '../../routes/shellRoute.ts'],
  ['src/features/orders/import/ImportPage.tsx', '../OrdersPage.tsx'],
  ['src/features/planning/PlanPage.tsx', '../../components/usePageTitle.ts'],
  ['src/features/access/SettingsPage.tsx', '@dispatchflow/i18n'],
  ['src/routes/rootRoute.ts', '../lib/languageSetting.ts'],
  ['src/routes/NotFoundPage.tsx', '../components/TextLink.tsx'],
  ['src/routes/ErrorPage.test.tsx', '../test/renderApp.tsx'],
  ['src/routeTree.ts', './features/planning/routes.ts'],
  ['src/DatesLanguage.tsx', 'dayjs/locale/he'],
  ['src/components/form/fields/DateField.test.tsx', 'dayjs'],
])(
  'GivenAFileAt_%s_WhenItImports_%s_ThenLintAllowsTheImport',
  async (filePath, specifier) => {
    expect(await isRefused(filePath, specifier)).toBe(false)
  },
  LINT_TIMEOUT,
)

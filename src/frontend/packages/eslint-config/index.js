/**
 * DispatchFlow's shared ESLint flat configs, for ESLint 10 only. The phone app runs ESLint 9
 * through eslint-config-expo and keeps its own configuration until it joins the workspace; #147
 * decides how this package will load under ESLint 9 then.
 *
 * @module @dispatchflow/eslint-config
 */
import js from '@eslint/js'
import reactHooks from 'eslint-plugin-react-hooks'
import { reactRefresh } from 'eslint-plugin-react-refresh'
import { defineConfig } from 'eslint/config'
import globals from 'globals'
import tseslint from 'typescript-eslint'

/**
 * The rules for a React app that Vite bundles: ESLint's and typescript-eslint's recommended
 * rules, the rules of hooks, and the Fast Refresh export rule, over every `.ts` and `.tsx` file,
 * with the browser's globals. `dangerouslySetInnerHTML` is refused: translated text is not
 * HTML-escaped (React escapes what it renders as text), so markup built from it could carry a
 * user's input into the page.
 */
export const reactVite = defineConfig({
  files: ['**/*.{ts,tsx}'],
  extends: [
    js.configs.recommended,
    tseslint.configs.recommended,
    reactHooks.configs.flat.recommended,
    // TanStack Router's createLink wraps a component as memo does, and returns a component.
    reactRefresh.configs.vite({ extraHOCs: ['createLink'] }),
  ],
  languageOptions: {
    globals: globals.browser,
  },
  rules: {
    'no-restricted-syntax': [
      'error',
      {
        selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']",
        message:
          'Render text as text, or markup through react-i18next Trans: translations are not HTML-escaped.',
      },
    ],
  },
})

/**
 * ESLint's and typescript-eslint's recommended rules over every `.ts` file, for a package of plain
 * TypeScript that both apps import: no React rules, and no browser or Node globals.
 */
export const typescript = defineConfig({
  files: ['**/*.ts'],
  extends: [js.configs.recommended, tseslint.configs.recommended],
})

/** ESLint's recommended rules over every `.js` file that Node runs, with Node's globals. */
export const node = defineConfig({
  files: ['**/*.js'],
  extends: [js.configs.recommended],
  languageOptions: {
    globals: globals.node,
  },
})

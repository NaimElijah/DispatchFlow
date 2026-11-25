/**
 * DispatchFlow's shared ESLint flat configs, for ESLint 10 only. The phone app runs ESLint 9
 * through eslint-config-expo and keeps its own configuration until it joins the workspace; #147
 * decides how this package will load under ESLint 9 then.
 *
 * @module @dispatchflow/eslint-config
 */
import js from '@eslint/js'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import { defineConfig } from 'eslint/config'
import globals from 'globals'
import tseslint from 'typescript-eslint'

/**
 * The rules for a React app that Vite bundles: ESLint's and typescript-eslint's recommended
 * rules, the rules of hooks, and the Fast Refresh export rule, over every `.ts` and `.tsx` file,
 * with the browser's globals.
 */
export const reactVite = defineConfig({
  files: ['**/*.{ts,tsx}'],
  extends: [
    js.configs.recommended,
    tseslint.configs.recommended,
    reactHooks.configs.flat.recommended,
    reactRefresh.configs.vite,
  ],
  languageOptions: {
    globals: globals.browser,
  },
})

/** ESLint's recommended rules over every `.js` file that Node runs, with Node's globals. */
export const node = defineConfig({
  files: ['**/*.js'],
  extends: [js.configs.recommended],
  languageOptions: {
    globals: globals.node,
  },
})

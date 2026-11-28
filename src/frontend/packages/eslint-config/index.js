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

/** What a writer of HTML into the page is told, whichever way it writes it. */
const HTML_SINK_MESSAGE =
  'Render text as text, or markup through react-i18next Trans: translations are not HTML-escaped, and text from a user or the server would become markup.'

/**
 * The rules for a React app that Vite bundles: ESLint's and typescript-eslint's recommended
 * rules, the rules of hooks, and the Fast Refresh export rule, over every `.ts`, `.tsx` and `.jsx`
 * file, with the browser's globals.
 *
 * Writing HTML into the page is refused, in each form short of a computed property name:
 * `dangerouslySetInnerHTML` as an attribute or as an object's key, an assignment to `innerHTML` or
 * `outerHTML`, `insertAdjacentHTML`, and `document.write`. Translated text is not HTML-escaped
 * (React escapes what it renders as text), so markup built from it could carry a user's input into
 * the page. `no-restricted-syntax` takes one list: a later config that sets it replaces this one.
 */
export const reactVite = defineConfig({
  files: ['**/*.{ts,tsx,jsx}'],
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
      ...[
        "JSXAttribute[name.name='dangerouslySetInnerHTML']",
        "Property[key.name='dangerouslySetInnerHTML'], Property[key.value='dangerouslySetInnerHTML']",
        'AssignmentExpression > MemberExpression.left[property.name=/^(innerHTML|outerHTML)$/]',
        "CallExpression[callee.property.name='insertAdjacentHTML']",
        "CallExpression[callee.object.name='document'][callee.property.name=/^write(ln)?$/]",
      ].map((selector) => ({ selector, message: HTML_SINK_MESSAGE })),
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

/**
 * The rules that read the types, over every `.ts` and `.tsx` file of the package or app whose
 * folder is given: typescript-eslint's project service gives each file its own tsconfig's types.
 *
 * `switch-exhaustiveness-check` fails a `switch` over a union that misses one of its members, a
 * `default` included, so a generated union's new `kind` can't fall through unseen (ADR-155, Risk;
 * ADR-161).
 *
 * @param {string} tsconfigRootDir the folder of the package's own `eslint.config.js`, its
 *     `import.meta.dirname`
 * @param {string[]} [outsideEveryTsconfig] the `.ts` files that no tsconfig of the package
 *     includes, such as a `vitest.config.ts`, which are read with TypeScript's defaults. A file a
 *     tsconfig includes is never listed: typescript-eslint refuses one that is in both.
 */
export function typeChecked(tsconfigRootDir, outsideEveryTsconfig = []) {
  return defineConfig({
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      parserOptions: {
        projectService: { allowDefaultProject: outsideEveryTsconfig },
        tsconfigRootDir,
      },
    },
    rules: {
      '@typescript-eslint/switch-exhaustiveness-check': [
        'error',
        { considerDefaultExhaustiveForUnions: false, requireDefaultForNonUnion: false },
      ],
    },
  })
}

/** ESLint's recommended rules over every `.js` file that Node runs, with Node's globals. */
export const node = defineConfig({
  files: ['**/*.js'],
  extends: [js.configs.recommended],
  languageOptions: {
    globals: globals.node,
  },
})

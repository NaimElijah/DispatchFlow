import { typeChecked, typescript } from '@dispatchflow/eslint-config'
import { defineConfig, globalIgnores } from 'eslint/config'

const TYPES_ONLY =
  'Take only types from the generated file: the web dev image runs without it, generating nothing (ADR-161).'

export default defineConfig([
  globalIgnores(['src/generated']),
  typescript,
  typeChecked(import.meta.dirname),
  {
    files: ['src/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        { patterns: [{ group: ['**/generated/*'], allowTypeImports: true, message: TYPES_ONLY }] },
      ],
      // `import { type paths }` passes the rule above, but leaves `import './generated/…'` behind,
      // which Vite's transformer keeps: write `import type` instead.
      '@typescript-eslint/no-import-type-side-effects': 'error',
    },
  },
])

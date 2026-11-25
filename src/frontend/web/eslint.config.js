import { reactVite } from '@dispatchflow/eslint-config'
import { defineConfig, globalIgnores } from 'eslint/config'

/**
 * Bans imports from the named folders of `src/`, so the folders depend one way (ADR-143):
 * `src/` itself → `features/` → `routes/` → `components/` → `lib/`.
 *
 * @param {string[]} folders the folders the files may not import from
 */
function noImportsFrom(folders) {
  return {
    'no-restricted-imports': [
      'error',
      {
        patterns: folders.map((folder) => ({
          regex: `(^|/)${folder}/`,
          message: `Imports point from src/ to features/, routes/, components/ and lib/, in that order, never back: this file may not import from ${folder}/ (ADR-143).`,
        })),
      },
    ],
  }
}

export default defineConfig([
  globalIgnores(['dist']),
  reactVite,
  { files: ['src/routes/**'], rules: noImportsFrom(['features']) },
  { files: ['src/components/**'], rules: noImportsFrom(['features', 'routes']) },
  { files: ['src/lib/**'], rules: noImportsFrom(['features', 'routes', 'components']) },
])

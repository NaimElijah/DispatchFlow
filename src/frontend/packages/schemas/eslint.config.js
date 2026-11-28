import { typeChecked, typescript } from '@dispatchflow/eslint-config'
import { defineConfig } from 'eslint/config'

const ZOD_MESSAGE =
  "Take z from './zod.ts', which turns off Zod's run-time compiling before a schema is built (ADR-069)."

export default defineConfig([
  typescript,
  typeChecked(import.meta.dirname),
  {
    files: ['**/*.ts'],
    ignores: ['src/zod.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          paths: [{ name: 'zod', allowTypeImports: true, message: ZOD_MESSAGE }],
          patterns: [{ group: ['zod/*'], allowTypeImports: true, message: ZOD_MESSAGE }],
        },
      ],
    },
  },
])

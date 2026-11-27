import { typescript } from '@dispatchflow/eslint-config'
import { defineConfig } from 'eslint/config'

export default defineConfig([
  typescript,
  {
    files: ['**/*.ts'],
    ignores: ['src/zod.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: 'zod',
              allowTypeImports: true,
              message:
                "Take z from './zod.ts', which turns off Zod's run-time compiling before a schema is built (ADR-069).",
            },
          ],
        },
      ],
    },
  },
])

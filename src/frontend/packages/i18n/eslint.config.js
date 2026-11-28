import { typeChecked, typescript } from '@dispatchflow/eslint-config'
import { defineConfig } from 'eslint/config'

export default defineConfig([typescript, typeChecked(import.meta.dirname)])

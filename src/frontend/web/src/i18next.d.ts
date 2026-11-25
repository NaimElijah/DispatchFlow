import type { Resources } from '@dispatchflow/i18n'

// Types every `t()` key against the bundled strings: a key that no namespace has fails tsc.
declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'shell'
    resources: Resources
  }
}

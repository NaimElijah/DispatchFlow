import type { Language } from '@dispatchflow/i18n'

/** Where the example form saves: a path no real operation has. */
export const EXAMPLE_PATH = '/api/v1/examples/e1'

/**
 * The example form's own strings. A real form's live in `@dispatchflow/i18n`, in its area's
 * namespace; the example ships no screen, so its strings stay with the tests.
 */
export const EXAMPLE_STRINGS = {
  en: {
    name: 'Contact name',
    notes: 'Notes for drivers',
    phone: 'Phone',
    depot: 'Depot',
    date: 'Delivery date',
    window: 'Delivery window',
    weightKg: 'Weight',
    volumeLitres: 'Volume',
    serviceMinutes: 'Service time',
    priority: 'Priority',
    priorityHint: '1 is the most important',
    saved: 'The example is saved.',
  },
  he: {
    name: 'שם איש הקשר',
    notes: 'הערות לנהגים',
    phone: 'טלפון',
    depot: 'מחסן',
    date: 'תאריך חלוקה',
    window: 'חלון חלוקה',
    weightKg: 'משקל',
    volumeLitres: 'נפח',
    serviceMinutes: 'זמן שירות',
    priority: 'עדיפות',
    priorityHint: '1 היא החשובה ביותר',
    saved: 'הדוגמה נשמרה.',
  },
} as const satisfies Record<Language, Record<string, string>>

/** The depots the example offers, with names that mix Hebrew and numbers. */
export const EXAMPLE_DEPOTS = [
  { value: 'depot-north', label: 'מחסן צפון 2' },
  { value: 'depot-south', label: 'מחסן דרום 7' },
] as const

/** The delivery date the example opens on, so its calendar opens on a known month. */
export const EXAMPLE_DATE = '2026-11-26'

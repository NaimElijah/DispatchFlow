import type { Language } from '@dispatchflow/i18n'

/**
 * The units a quantity is shown in: weights in kilograms and volumes in litres (NFR-68, BR-50), and
 * durations in minutes or seconds.
 */
export type Unit = 'kg' | 'litres' | 'minutes' | 'seconds'

/** Each unit's name in `Intl.NumberFormat`'s sanctioned list. */
const INTL_UNITS = {
  kg: 'kilogram',
  litres: 'liter',
  minutes: 'minute',
  seconds: 'second',
} as const satisfies Record<Unit, string>

/** Weights and volumes carry at most two decimals (DL §3.3), and nothing shown carries more. */
const MAX_FRACTION_DIGITS = 2

const formats = new Map<string, Intl.NumberFormat>()

function formatOf(unit: Unit, language: Language): Intl.NumberFormat {
  const key = `${unit}:${language}`
  let format = formats.get(key)
  if (format === undefined) {
    format = new Intl.NumberFormat(language, {
      style: 'unit',
      unit: INTL_UNITS[unit],
      unitDisplay: 'short',
      maximumFractionDigits: MAX_FRACTION_DIGITS,
    })
    formats.set(key, format)
  }
  return format
}

/**
 * Writes a quantity with its unit's short symbol in the interface language, rounded to at most two
 * decimals: `1,234.5 kg`, `1,234.5 ק״ג`.
 *
 * @throws RangeError when the value is not a finite number
 */
export function formatQuantity(value: number, unit: Unit, language: Language): string {
  if (!Number.isFinite(value)) {
    throw new RangeError(`${value} is not a quantity that can be shown`)
  }
  return formatOf(unit, language).format(value)
}

/**
 * The unit's short symbol in the interface language, as {@link formatQuantity} writes it beside a
 * value: for a field that shows the unit beside the number the user types.
 *
 * @throws Error when `Intl` gives the unit no symbol in the language, which no supported runtime does
 */
export function unitSymbol(unit: Unit, language: Language): string {
  const symbol = formatOf(unit, language)
    .formatToParts(1)
    .filter((part) => part.type === 'unit')
    .map((part) => part.value)
    .join('')
  if (symbol === '') {
    throw new Error(`Intl gave no symbol for the unit ${unit} in ${language}`)
  }
  return symbol
}

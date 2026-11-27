/**
 * How both apps show dates, times and quantities: in Israel's time, `Asia/Jerusalem`, whatever the
 * device's own zone (ADR-088), as DD/MM/YYYY and on a 24-hour clock in both interface languages,
 * with weights in kilograms and volumes in litres (NFR-68, ADR-089, TS §12).
 *
 * Display only, through `Intl`. Date arithmetic, such as the time left in a window, uses date-fns 4
 * once a screen needs it (TS §10).
 *
 * @module @dispatchflow/formatters
 */
export {
  formatInstantDate,
  formatInstantDateTime,
  formatInstantTime,
  formatInstantTimeOn,
  formatLocalDate,
  formatLocalTime,
  formatTimeRange,
  isLocalDate,
  isLocalTime,
  ISRAEL_TIME_ZONE,
  israelDateOf,
  type TimeOnDate,
} from './dates.ts'
export { formatQuantity, unitSymbol, type Unit } from './quantities.ts'

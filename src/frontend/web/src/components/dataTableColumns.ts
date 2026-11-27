import {
  createColumnHelper,
  tableFeatures,
  type ColumnDef,
  type ColumnHelper,
  type RowData,
} from '@tanstack/react-table'

/**
 * The TanStack Table features every shared table registers: the core alone for now. A screen that
 * needs sorting or filtering adds its feature here, so every table offers it the same way.
 */
export const dataTableFeatures = tableFeatures({})

/** The features of {@link dataTableFeatures}, which a table's column definitions are typed with. */
export type DataTableFeatures = typeof dataTableFeatures

/**
 * A table's columns, as {@link createDataTableColumns}'s `columns` returns them. Keep them at module
 * scope or memoised: a new array on every render rebuilds the table's rows.
 */
export type DataTableColumns<TRow extends RowData> = ReadonlyArray<
  // eslint-disable-next-line @typescript-eslint/no-explicit-any -- A table's columns hold values of different types; TanStack's own column helper types such a list with `any` too.
  ColumnDef<DataTableFeatures, TRow, any>
>

/**
 * The helper a screen defines a table's columns with: `accessor('name', { header })` for a column of
 * text, or with its own `cell` for anything else, such as a link or a status in words.
 */
export function createDataTableColumns<TRow extends RowData>(): ColumnHelper<
  DataTableFeatures,
  TRow
> {
  return createColumnHelper<DataTableFeatures, TRow>()
}

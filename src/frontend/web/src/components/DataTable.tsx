import { Table } from '@mantine/core'
import { useTable, type CellContext, type RowData } from '@tanstack/react-table'
import {
  dataTableFeatures,
  type DataTableColumns,
  type DataTableFeatures,
} from './dataTableColumns.ts'

/**
 * How a column with no `cell` of its own shows its value: as text inside `<bdi>`, so an address or
 * a name with numbers keeps its reading order whatever the table's direction (NFR-136). A missing
 * value, such as an anonymized customer's name, shows nothing.
 */
function textCell<TRow extends RowData>({
  getValue,
}: CellContext<DataTableFeatures, TRow, unknown>) {
  const value = getValue()
  return value === null || value === undefined ? null : <bdi>{String(value)}</bdi>
}

/** What a table shows. */
export interface DataTableProps<TRow extends RowData> {
  /** The table's name, which screen readers read first, such as "Refused rows". */
  readonly caption: string
  /** The columns, from `createDataTableColumns`, at module scope or memoised. */
  readonly columns: DataTableColumns<TRow>
  /** The rows, in the order the table shows them. */
  readonly rows: readonly TRow[]
  /** A row's stable ID, such as its order's ID or its row number, so React keeps each row's place. */
  readonly getRowId: (row: TRow) => string
  /** What the table says when it has no row, such as "Every row was imported." */
  readonly emptyText: string
}

/**
 * The one table of row lists, such as an import's refused rows (FR-10), a route's stop list and the
 * flagged addresses: a real `<table>`, with a caption and column headers, laid out in the interface
 * language's direction. TanStack Table 9 builds its rows; the markup is ours, so right to left and
 * accessibility stay in our hands (TS §10).
 */
export function DataTable<TRow extends RowData>({
  caption,
  columns,
  rows,
  getRowId,
  emptyText,
}: DataTableProps<TRow>) {
  const table = useTable({
    features: dataTableFeatures,
    columns,
    data: rows,
    getRowId,
    defaultColumn: { cell: textCell },
  })
  const tableRows = table.getRowModel().rows

  return (
    <Table captionSide="top">
      <Table.Caption>{caption}</Table.Caption>
      <Table.Thead>
        {table.getHeaderGroups().map((group) => (
          <Table.Tr key={group.id}>
            {group.headers.map((header) => (
              <Table.Th key={header.id} scope="col" colSpan={header.colSpan}>
                {header.isPlaceholder ? null : <table.FlexRender header={header} />}
              </Table.Th>
            ))}
          </Table.Tr>
        ))}
      </Table.Thead>
      <Table.Tbody>
        {tableRows.length === 0 ? (
          <Table.Tr>
            <Table.Td colSpan={table.getAllLeafColumns().length}>{emptyText}</Table.Td>
          </Table.Tr>
        ) : (
          tableRows.map((row) => (
            <Table.Tr key={row.id}>
              {row.getAllCells().map((cell) => (
                <Table.Td key={cell.id}>
                  <table.FlexRender cell={cell} />
                </Table.Td>
              ))}
            </Table.Tr>
          ))
        )}
      </Table.Tbody>
    </Table>
  )
}

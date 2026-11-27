import { screen, within } from '@testing-library/react'
import { formatTimeRange } from '@dispatchflow/formatters'
import { describe, expect, test } from 'vitest'
import { renderComponent } from '../test/renderComponent.tsx'
import { DataTable } from './DataTable.tsx'
import { createDataTableColumns } from './dataTableColumns.ts'

/** A refused import row, as #224's table shows one. */
interface Row {
  readonly row: number
  readonly customer: string | null
  readonly address: string
}

const helper = createDataTableColumns<Row>()
const columns = helper.columns([
  helper.accessor('row', { header: 'Row' }),
  helper.accessor('customer', { header: 'Customer' }),
  helper.accessor('address', { header: 'Address' }),
])

/**
 * NFR-136's sample strings: Hebrew with numbers, a phone with its plus sign, Latin inside Hebrew,
 * and an order reference that mixes letters and digits.
 */
const MIXED_DIRECTION_SAMPLES = [
  'הרצל 12, דירה 4, באר שבע',
  '+972 50-123-4567',
  'קניון BIG, כניסה B',
  'ORD-2026-0042',
] as const

const rows: Row[] = MIXED_DIRECTION_SAMPLES.map((address, index) => ({
  row: index + 2,
  customer: index === 0 ? null : `לקוח ${index}`,
  address,
}))

function renderTable(language: 'en' | 'he' = 'en', shown: readonly Row[] = rows) {
  return renderComponent(
    <DataTable
      caption="Refused rows"
      columns={columns}
      rows={shown}
      getRowId={(row) => String(row.row)}
      emptyText="Every row was imported."
    />,
    { language },
  )
}

describe('DataTable', () => {
  test('GivenRows_WhenShown_ThenTheyAreARealTableWithItsCaptionAndColumnHeaders', () => {
    renderTable()

    const table = screen.getByRole('table', { name: 'Refused rows' })
    expect(
      within(table)
        .getAllByRole('columnheader')
        .map((header) => header.textContent),
    ).toEqual(['Row', 'Customer', 'Address'])
    // A header row, then one row per refused row.
    expect(within(table).getAllByRole('row')).toHaveLength(rows.length + 1)
    expect(within(table).getByRole('cell', { name: 'ORD-2026-0042' })).toBeDefined()
  })

  test('GivenAHebrewAddressWithNumbers_WhenShown_ThenItSitsInABdi', () => {
    renderTable('he')

    for (const sample of MIXED_DIRECTION_SAMPLES) {
      const shown = screen.getByText(sample)
      expect(shown.tagName, sample).toBe('BDI')
    }
  })

  test('GivenAColumnOfDecimalsOrDatesWithNoCellOfItsOwn_WhenShown_ThenTheTableFailsNamingTheColumn', () => {
    interface Delivery {
      readonly id: string
      readonly weightKg: number
      readonly due: Date
    }
    const deliveries = createDataTableColumns<Delivery>()
    const delivery: Delivery = { id: 'd1', weightKg: 12.5, due: new Date('2026-11-27T08:00:00Z') }

    for (const key of ['weightKg', 'due'] as const) {
      expect(
        () =>
          renderComponent(
            <DataTable
              caption="Deliveries"
              columns={deliveries.columns([deliveries.accessor(key, { header: key })])}
              rows={[delivery]}
              getRowId={(row) => row.id}
              emptyText="No delivery."
            />,
          ),
        key,
      ).toThrow(`"${key}"`)
    }
  })

  test('GivenATimeWindowInAHebrewTable_WhenShown_ThenItSitsInABdiThatRunsLeftToRight', () => {
    const window = formatTimeRange('08:00', '12:00')
    renderTable('he', [{ row: 2, customer: null, address: window }])

    // A <bdi> with no strong character runs left to right (HTML, the dir attribute's auto state),
    // so 08:00 stays on the left, as on a clock; nothing must set a direction of its own on it.
    const shown = screen.getByText(window)
    expect(shown.tagName).toBe('BDI')
    expect(shown.hasAttribute('dir')).toBe(false)
  })

  test('GivenAMissingValue_WhenShown_ThenItsCellIsEmptyRatherThanNull', () => {
    renderTable()

    const firstRow = screen.getAllByRole('row')[1]
    expect(within(firstRow).getAllByRole('cell')[1].textContent).toBe('')
  })

  test('GivenNoRows_WhenShown_ThenTheTableSaysSoAcrossAllItsColumns', () => {
    renderTable('en', [])

    const cell = screen.getByRole('cell', { name: 'Every row was imported.' })
    expect(cell.getAttribute('colspan')).toBe(String(3))
  })

  test('GivenHebrew_WhenTheTableIsShown_ThenItIsLaidOutRightToLeft', () => {
    renderTable('he')

    expect(document.documentElement.dir).toBe('rtl')
    expect(screen.getByRole('table').closest('[dir="ltr"]')).toBeNull()
  })
})

import {
  durationMinutes,
  localDate,
  optionalTimeWindow,
  timeOfDay,
  weightKg,
} from '@dispatchflow/schemas'
import { screen, waitFor, within } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import { z } from 'zod'
import { ExampleForm } from '../../../test/ExampleForm.tsx'
import { EXAMPLE_DEPOTS, EXAMPLE_STRINGS } from '../../../test/exampleFormData.ts'
import { renderComponent } from '../../../test/renderComponent.tsx'
import { SaveButton } from '../SaveButton.tsx'
import { SchemaForm } from '../SchemaForm.tsx'
import { useSchemaForm } from '../useSchemaForm.ts'
import { DateField } from './DateField.tsx'
import { NumberField } from './NumberField.tsx'
import { TimeField } from './TimeField.tsx'
import { TimeWindowField } from './TimeWindowField.tsx'

const REQUIRED = 'This is required. Fill it in.'
const strings = EXAMPLE_STRINGS.en

const timesSchema = z.object({
  opensAt: timeOfDay,
  window: optionalTimeWindow,
  weightKg,
  date: localDate,
})

/** A form of the fields whose markup is Mantine's own composite, to probe their wiring. */
function TimesForm({ date = '' }: { readonly date?: string }) {
  const form = useSchemaForm(timesSchema, {
    defaultValues: { opensAt: '', window: { start: '', end: '' }, date },
  })
  const { control } = form

  return (
    <SchemaForm
      form={form}
      schema={timesSchema}
      save={{ isPending: false, error: null, reset: () => undefined }}
      onSubmit={async () => undefined}
    >
      <TimeField control={control} name="opensAt" label="Opens at" required />
      <TimeWindowField control={control} name="window" legend="Delivery window" />
      <NumberField control={control} name="weightKg" label="Weight" unit="kg" decimals={2} />
      <DateField control={control} name="date" label="Delivery date" />
      <SaveButton />
    </SchemaForm>
  )
}

const openingSchema = z.object({ opensAt: timeOfDay })

/** A form of one time and a button that resets it, as a screen does after a reload. */
function ResettableTimeForm() {
  const form = useSchemaForm(openingSchema, { defaultValues: { opensAt: '' } })

  return (
    <>
      <TimeField control={form.control} name="opensAt" label="Opens at" />
      <button type="button" onClick={() => form.reset()}>
        Reset
      </button>
    </>
  )
}

describe('fields', () => {
  test('GivenAFieldWithAnError_WhenRendered_ThenTheErrorIsTiedToTheField', async () => {
    const user = userEvent.setup()
    renderComponent(<ExampleForm etag={null} />)

    await user.click(screen.getByRole('button', { name: 'Save' }))

    for (const name of [strings.name, strings.phone, 'Weight (kg)', 'Volume (L)']) {
      const field = await screen.findByRole('textbox', { name, description: REQUIRED })
      expect(field.getAttribute('aria-invalid'), name).toBe('true')
    }
    expect(
      screen.getByRole('combobox', { name: strings.depot, description: REQUIRED }),
    ).toBeDefined()
  })

  test('GivenARequiredTimeLeftEmpty_WhenSaved_ThenBothItsBoxesCarryTheError', async () => {
    const user = userEvent.setup()
    renderComponent(<TimesForm />)

    await user.click(screen.getByRole('button', { name: 'Save' }))

    for (const name of ['Opens at, hours', 'Opens at, minutes']) {
      const box = await screen.findByRole('spinbutton', { name, description: REQUIRED })
      expect(box.getAttribute('aria-invalid'), name).toBe('true')
    }
    expect(
      screen.getByRole('button', { name: 'Delivery date', description: REQUIRED }),
    ).toBeDefined()
  })

  test('GivenANoteLongerThanItsLimit_WhenSaved_ThenTheMultiLineFieldSaysWhy', async () => {
    const user = userEvent.setup()
    renderComponent(<ExampleForm etag={null} />)

    await user.click(screen.getByRole('textbox', { name: strings.notes }))
    await user.paste('x'.repeat(501))
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(
      await screen.findByRole('textbox', {
        name: strings.notes,
        description: 'This text is too long. Shorten it to 500 characters or fewer.',
      }),
    ).toBeDefined()
  })

  test('GivenAWindowWithOnlyItsStart_WhenSaved_ThenTheGroupSaysBothOrNeither', async () => {
    const user = userEvent.setup()
    renderComponent(<TimesForm />)

    await user.type(screen.getByRole('spinbutton', { name: 'From, hours' }), '0800')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    const group = await screen.findByRole('group', {
      name: 'Delivery window',
      description:
        'The window has only one of its two times. Fill in both times, or leave both empty.',
    })
    for (const box of within(group).getAllByRole('spinbutton')) {
      expect(box.getAttribute('aria-invalid')).toBe('true')
    }
  })

  test('GivenAFilledTime_WhenItsMinutesAreDeleted_ThenItsHoursStay', async () => {
    const user = userEvent.setup()
    renderComponent(<TimesForm />)
    const hours = screen.getByRole('spinbutton', { name: 'Opens at, hours' })
    const minutes = screen.getByRole('spinbutton', { name: 'Opens at, minutes' })
    await user.type(hours, '0830')
    expect(document.activeElement).toBe(minutes)

    await user.keyboard('{Backspace}')

    expect(minutes).toHaveProperty('value', '')
    expect(hours).toHaveProperty('value', '08')
  })

  test('GivenAFilledTime_WhenTheFormIsReset_ThenBothItsBoxesEmpty', async () => {
    const user = userEvent.setup()
    renderComponent(<ResettableTimeForm />)
    const hours = screen.getByRole('spinbutton', { name: 'Opens at, hours' })
    const minutes = screen.getByRole('spinbutton', { name: 'Opens at, minutes' })
    await user.type(hours, '0830')

    await user.click(screen.getByRole('button', { name: 'Reset' }))

    expect(hours).toHaveProperty('value', '')
    expect(minutes).toHaveProperty('value', '')
  })

  test('GivenAWindowWithOnlyItsStart_WhenSaved_ThenFocusMovesToItAndEachBoxSaysWhy', async () => {
    const user = userEvent.setup()
    renderComponent(<TimesForm />)
    const bothOrNeither =
      'The window has only one of its two times. Fill in both times, or leave both empty.'

    await user.type(screen.getByRole('spinbutton', { name: 'Opens at, hours' }), '0900')
    await user.type(screen.getByRole('spinbutton', { name: 'From, hours' }), '0800')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    // The weight and the date are refused too, but the window comes first in reading order.
    await waitFor(() =>
      expect(document.activeElement).toBe(
        screen.getByRole('spinbutton', { name: 'From, hours', description: bothOrNeither }),
      ),
    )
    for (const name of ['From, minutes', 'To, hours', 'To, minutes']) {
      expect(screen.getByRole('spinbutton', { name, description: bothOrNeither })).toBeDefined()
    }
  })

  test('GivenARefusedSaveWhoseFirstRefusalIsAChoice_WhenSaved_ThenFocusMovesToTheChoice', async () => {
    const user = userEvent.setup()
    renderComponent(<ExampleForm etag={null} />)
    await user.type(screen.getByRole('textbox', { name: strings.name }), 'Ada')
    await user.type(screen.getByRole('textbox', { name: strings.phone }), '0501234567')

    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(document.activeElement).toBe(
        screen.getByRole('combobox', { name: strings.depot, description: REQUIRED }),
      ),
    )
  })

  test('GivenARefusedSaveWhoseFirstRefusalIsADate_WhenSaved_ThenFocusMovesToTheDate', async () => {
    const user = userEvent.setup()
    renderComponent(<TimesForm />)
    await user.type(screen.getByRole('spinbutton', { name: 'Opens at, hours' }), '0900')
    await user.type(screen.getByRole('textbox', { name: 'Weight (kg)' }), '3')

    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(document.activeElement).toBe(
        screen.getByRole('button', { name: 'Delivery date', description: REQUIRED }),
      ),
    )
  })

  test('GivenAWindowRefusedForHavingOnlyItsStart_WhenItsEndIsFilledIn_ThenTheRefusalGoes', async () => {
    const user = userEvent.setup()
    renderComponent(<TimesForm />)
    await user.type(screen.getByRole('spinbutton', { name: 'From, hours' }), '0800')
    await user.click(screen.getByRole('button', { name: 'Save' }))
    const group = await screen.findByRole('group', { name: 'Delivery window' })
    expect(within(group).getByText(/only one of its two times/)).toBeDefined()

    await user.type(screen.getByRole('spinbutton', { name: 'To, hours' }), '1200')

    await waitFor(() => expect(within(group).queryByText(/only one of its two times/)).toBeNull())
    for (const box of within(group).getAllByRole('spinbutton')) {
      expect(box.getAttribute('aria-invalid')).toBe('false')
    }
  })

  test('GivenHebrew_WhenATimeIsShown_ThenItsBoxesStillRunHoursThenMinutesLeftToRight', () => {
    renderComponent(<TimesForm />, { language: 'he' })

    const hours = screen.getByRole('spinbutton', { name: 'Opens at, שעות' })
    const minutes = screen.getByRole('spinbutton', { name: 'Opens at, דקות' })
    expect(hours.closest('[dir="ltr"]')).not.toBeNull()
    expect(hours.compareDocumentPosition(minutes) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  test('GivenADecimalOnItsWay_WhenTyped_ThenTheNumberFieldKeepsWhatWasTyped', async () => {
    const user = userEvent.setup()
    renderComponent(<TimesForm />)
    const weight = screen.getByRole('textbox', { name: 'Weight (kg)' })

    await user.type(weight, '12.')

    expect(weight).toHaveProperty('value', '12.')
    await user.type(weight, '5')
    expect(weight).toHaveProperty('value', '12.5')
  })

  test('GivenANumberWhoseSchemaTakesNoNull_WhenBoundToANumberField_ThenTheFormDoesNotCompile', () => {
    // `tsc -b` checks this: the field writes `null` to an emptied box, which `.optional()` alone
    // refuses as missing. The render only keeps the form in use.
    const minutesSchema = z.object({ minutes: durationMinutes.optional() })
    function MinutesForm() {
      const form = useSchemaForm(minutesSchema)
      return (
        <NumberField
          control={form.control}
          // @ts-expect-error a number that takes no `null` is no number field's
          name="minutes"
          label="Minutes"
        />
      )
    }
    renderComponent(<MinutesForm />)

    expect(screen.getByRole('textbox', { name: 'Minutes' })).toBeDefined()
  })

  test('GivenAUnit_WhenTheFieldIsShownInHebrew_ThenItsLabelNamesTheUnitInHebrew', () => {
    renderComponent(<TimesForm />, { language: 'he' })

    expect(screen.getByRole('textbox', { name: 'Weight (ק״ג)' })).toBeDefined()
  })

  test('GivenAPhoneTypedPlainly_WhenTheUserLeavesIt_ThenItIsShownWrittenOutAndTypedAsItWasWhenBack', async () => {
    const user = userEvent.setup()
    renderComponent(<ExampleForm etag={null} />)
    const phone = screen.getByRole('textbox', { name: strings.phone })

    await user.type(phone, '+972501234567')
    expect(phone).toHaveProperty('value', '+972501234567')
    await user.tab()
    expect(phone).toHaveProperty('value', '050-123-4567')
    expect(phone.closest('[dir="ltr"]')).not.toBeNull()
    await user.click(phone)
    expect(phone).toHaveProperty('value', '+972501234567')
  })

  test('GivenTheSelectsOptions_WhenOpened_ThenEachSitsInABdi', async () => {
    const user = userEvent.setup()
    renderComponent(<ExampleForm etag={null} />)

    await user.click(screen.getByRole('combobox', { name: strings.depot }))

    // As with the calendar, jsdom keeps the opening list out of the accessibility tree.
    for (const { label } of EXAMPLE_DEPOTS) {
      const shown = await screen.findByText(label)
      expect(shown.tagName, label).toBe('BDI')
      expect(shown.closest('[role="option"]'), label).not.toBeNull()
    }
  })

  test('GivenADateFieldInHebrew_WhenADayIsChosen_ThenTheMonthIsNamedInHebrewAndTheDayShownAsDayMonthYear', async () => {
    const user = userEvent.setup()
    renderComponent(<TimesForm date="2026-11-26" />, { language: 'he' })
    const date = screen.getByRole('button', { name: 'Delivery date' })
    expect(date.textContent).toBe('26/11/2026')

    await user.click(date)
    // jsdom runs no CSS transition, so the opening calendar stays out of the accessibility tree:
    // its month and its days are found by their text and their labels instead.
    expect(await screen.findByText('נובמבר 2026')).toBeDefined()
    // Israel's week: 1 November 2026 is a Sunday, so the month opens on its first day, with no
    // day of October before it, and Friday and Saturday are the weekend.
    const days = document.querySelectorAll('[role="dialog"] button[aria-label]')
    expect(
      [...days]
        .find((day) => /\d{4}$/.test(day.getAttribute('aria-label') ?? ''))
        ?.getAttribute('aria-label'),
    ).toBe('1 נובמבר 2026')
    for (const [day, weekend] of [
      ['27', true],
      ['28', true],
      ['29', false],
    ] as const) {
      expect(screen.getByLabelText(`${day} נובמבר 2026`).hasAttribute('data-weekend'), day).toBe(
        weekend,
      )
    }
    await user.click(screen.getByLabelText('27 נובמבר 2026'))

    expect(date.textContent).toBe('27/11/2026')
  })
})

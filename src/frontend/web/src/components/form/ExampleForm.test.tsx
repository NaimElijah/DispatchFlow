import { screen, waitFor } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { http, HttpResponse } from 'msw/http'
import { describe, expect, test } from 'vitest'
import { ExampleForm } from '../../test/ExampleForm.tsx'
import { EXAMPLE_PATH, EXAMPLE_STRINGS } from '../../test/exampleFormData.ts'
import { renderComponent } from '../../test/renderComponent.tsx'
import { server } from '../../test/server.ts'

/** Records the example's saves and answers each as stored. */
function answerSaves(): Request[] {
  const requests: Request[] = []
  server.use(
    http.put(EXAMPLE_PATH, ({ request }) => {
      requests.push(request.clone())
      return new HttpResponse(null, { status: 204, headers: { ETag: '"2"' } })
    }),
  )
  return requests
}

/** Fills in every field the example requires, by mouse and keyboard, and leaves the rest empty. */
async function fillInWhatIsRequired(user: ReturnType<typeof userEvent.setup>) {
  const strings = EXAMPLE_STRINGS.en
  await user.type(screen.getByRole('textbox', { name: strings.name }), 'Ada')
  await user.type(screen.getByRole('textbox', { name: strings.phone }), '0501234567')
  await user.click(screen.getByRole('combobox', { name: strings.depot }))
  await user.click(await screen.findByText('מחסן צפון 2'))
  await user.type(screen.getByRole('textbox', { name: 'Weight (kg)' }), '3')
  await user.type(screen.getByRole('textbox', { name: 'Volume (L)' }), '2')
}

describe('the example form', () => {
  test('GivenTheExampleForm_WhenFilledInAndSavedByKeyboardAlone_ThenItSendsWhatWasEntered', async () => {
    const user = userEvent.setup()
    const requests = answerSaves()
    renderComponent(<ExampleForm etag='"1"' />)
    const strings = EXAMPLE_STRINGS.en

    await user.tab()
    expect(document.activeElement).toBe(screen.getByRole('textbox', { name: strings.name }))
    await user.keyboard('Ada Lovelace')
    await user.tab()
    await user.keyboard('Ring twice')
    await user.tab()
    await user.keyboard('0501234567')
    await user.tab()
    await user.keyboard('צפון{ArrowDown}{Enter}')
    await user.tab()
    // The date opens on its default, 26/11/2026, and is kept as it is.
    await user.tab()
    await user.keyboard('0800')
    await user.tab()
    await user.keyboard('1230')
    await user.tab()
    await user.keyboard('12.5')
    await user.tab()
    await user.keyboard('0.25')
    await user.tab()
    await user.keyboard('15')
    await user.tab()
    await user.keyboard('{Enter}')

    expect(await screen.findByText('The example is saved.')).toBeDefined()
    expect(requests).toHaveLength(1)
    expect(requests[0].headers.get('If-Match')).toBe('"1"')
    expect(await requests[0].json()).toEqual({
      name: 'Ada Lovelace',
      notes: 'Ring twice',
      phone: '0501234567',
      depot: 'depot-north',
      date: '2026-11-26',
      window: { start: '08:00', end: '12:30' },
      weightKg: 12.5,
      volumeLitres: 0.25,
      serviceMinutes: 15,
      priority: 3,
    })
  })

  test('GivenADayChosenInTheCalendar_WhenSaved_ThenTheRequestCarriesItAsYYYYMMDD', async () => {
    const user = userEvent.setup()
    const requests = answerSaves()
    renderComponent(<ExampleForm etag='"1"' />)
    const strings = EXAMPLE_STRINGS.en
    await user.type(screen.getByRole('textbox', { name: strings.name }), 'Ada')
    await user.type(screen.getByRole('textbox', { name: strings.phone }), '0501234567')
    await user.click(screen.getByRole('combobox', { name: strings.depot }))
    await user.click(await screen.findByText('מחסן צפון 2'))
    await user.type(screen.getByRole('textbox', { name: 'Weight (kg)' }), '3')
    await user.type(screen.getByRole('textbox', { name: 'Volume (L)' }), '2')
    await user.type(screen.getByRole('textbox', { name: 'Service time (min)' }), '10')

    await user.click(screen.getByRole('button', { name: strings.date }))
    // jsdom keeps the opening calendar out of the accessibility tree: its days go by their labels.
    await user.click(await screen.findByLabelText('27 November 2026'))
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('The example is saved.')).toBeDefined()
    expect(await requests[0].json()).toMatchObject({ date: '2026-11-27' })
  })

  test('GivenAChangeSaved_WhenTheFormIsSavedAgain_ThenItSendsTheVersionTheFirstSaveReturned', async () => {
    const user = userEvent.setup()
    const requests = answerSaves()
    renderComponent(<ExampleForm etag={null} />)
    await fillInWhatIsRequired(user)

    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('The example is saved.')).toBeDefined()
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(requests).toHaveLength(2))
    expect(requests[0].headers.get('If-None-Match')).toBe('*')
    expect(requests[1].headers.get('If-Match')).toBe('"2"')
    expect(requests[1].headers.get('If-None-Match')).toBeNull()
  })

  test('GivenTheOptionalServiceTimeLeftEmpty_WhenSaved_ThenTheBodyLeavesItOut', async () => {
    const user = userEvent.setup()
    const requests = answerSaves()
    renderComponent(<ExampleForm etag='"1"' />)
    await fillInWhatIsRequired(user)

    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('The example is saved.')).toBeDefined()
    expect(await requests[0].json()).not.toHaveProperty('serviceMinutes')
  })

  test('GivenTheOptionalServiceTimeTypedThenEmptied_WhenSaved_ThenTheBodyLeavesItOut', async () => {
    const user = userEvent.setup()
    const requests = answerSaves()
    renderComponent(<ExampleForm etag='"1"' />)
    await fillInWhatIsRequired(user)
    const serviceTime = screen.getByRole('textbox', { name: 'Service time (min)' })

    await user.type(serviceTime, '10')
    await user.clear(serviceTime)
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('The example is saved.')).toBeDefined()
    expect(await requests[0].json()).not.toHaveProperty('serviceMinutes')
  })

  test('GivenTheDateField_WhenEnterIsPressedOnIt_ThenItsCalendarOpensOnTheMonthItHolds', async () => {
    const user = userEvent.setup()
    renderComponent(<ExampleForm etag={null} />)
    const date = screen.getByRole('button', { name: EXAMPLE_STRINGS.en.date })

    date.focus()
    await user.keyboard('{Enter}')

    // jsdom runs no transition, so the opened calendar stays hidden and its days take no focus:
    // moving between days with the arrow keys is checked in a browser, not here.
    expect(date.getAttribute('aria-expanded')).toBe('true')
    expect(await screen.findByLabelText('26 November 2026')).toBeDefined()
  })

  test.each(['en', 'he'] as const)(
    'GivenTheExampleFormIn(%s)_WhenShown_ThenItShowsItsOwnWordsAndDirection',
    (language) => {
      const strings = EXAMPLE_STRINGS[language]
      renderComponent(<ExampleForm etag={null} />, { language })

      expect(document.documentElement.dir).toBe(language === 'he' ? 'rtl' : 'ltr')
      expect(screen.getByRole('textbox', { name: strings.name })).toBeDefined()
      expect(screen.getByRole('group', { name: strings.window })).toBeDefined()
      expect(screen.getByText('26/11/2026')).toBeDefined()
      expect(
        screen.getByRole('textbox', {
          name: `${strings.weightKg} (${language === 'he' ? 'ק״ג' : 'kg'})`,
        }),
      ).toBeDefined()
    },
  )

  test('GivenTheExampleFormInHebrew_WhenSavedEmpty_ThenEachRequiredFieldSaysWhyInHebrew', async () => {
    const user = userEvent.setup()
    renderComponent(<ExampleForm etag={null} />, { language: 'he' })

    await user.click(screen.getByRole('button', { name: 'שמירה' }))

    expect(
      await screen.findByRole('textbox', {
        name: EXAMPLE_STRINGS.he.name,
        description: 'זהו שדה חובה. יש למלא אותו.',
      }),
    ).toBeDefined()
    expect(
      screen.getByRole('textbox', {
        name: `${EXAMPLE_STRINGS.he.weightKg} (ק״ג)`,
        description: 'זהו שדה חובה. יש למלא אותו.',
      }),
    ).toBeDefined()
  })
})

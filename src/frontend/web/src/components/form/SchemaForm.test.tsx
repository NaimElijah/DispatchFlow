import { phoneNumber, requiredText } from '@dispatchflow/schemas'
import { screen, waitFor, within } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { http, HttpResponse } from 'msw/http'
import { describe, expect, test, vi } from 'vitest'
import { z } from 'zod'
import { useVersionedSave, type Saved } from '../../lib/versionedSave.ts'
import { renderComponent } from '../../test/renderComponent.tsx'
import { sendJson } from '../../test/sendJson.ts'
import { server } from '../../test/server.ts'
import { notifySaved } from '../notifySaved.ts'
import { PhoneField } from './fields/PhoneField.tsx'
import { TextField } from './fields/TextField.tsx'
import { SaveButton } from './SaveButton.tsx'
import { SchemaForm } from './SchemaForm.tsx'
import { useSchemaForm } from './useSchemaForm.ts'

const CONTACT_PATH = '/api/v1/contacts/c1'
const TRACE_ID = '4bf92f3577b34da6a3ce929d0e0e4736'

/** A small form for these tests: its keys are the request body's, as a real form's are. */
const contactSchema = z.object({ name: requiredText(100), phone: phoneNumber })

interface ContactFormProps {
  /** The `ETag` the view was read with, or `null` for none yet. */
  readonly etag: string | null
  readonly onReload?: () => void
  readonly onSaved?: (saved: Saved<unknown>) => void
}

function ContactForm({ etag, onReload, onSaved }: ContactFormProps) {
  const form = useSchemaForm(contactSchema, { defaultValues: { name: '', phone: '' } })
  const save = useVersionedSave({
    send: (body, headers) => sendJson('PUT', CONTACT_PATH, body, headers),
    onSaved: (saved) => {
      notifySaved('The contact is saved.')
      onSaved?.(saved)
    },
  })

  return (
    <SchemaForm
      form={form}
      schema={contactSchema}
      save={save}
      onSubmit={(body) => save.mutate({ body, etag })}
      onReload={onReload}
      fieldFor={(code) => (code === 'INVALID_PHONE_NUMBER' ? 'phone' : undefined)}
    >
      <TextField control={form.control} name="name" label="Name" required />
      <PhoneField control={form.control} name="phone" label="Phone" required />
      <SaveButton />
    </SchemaForm>
  )
}

function problem(status: number, code: string, extra: object = {}) {
  return {
    type: 'about:blank',
    title: 'Refused',
    status,
    code,
    params: {},
    traceId: TRACE_ID,
    ...extra,
  }
}

/** Answers the contact's save with each of the given answers in turn, and counts the requests. */
function answerSaves(...answers: (() => Response)[]) {
  const requests: Request[] = []
  server.use(
    http.put(CONTACT_PATH, ({ request }) => {
      requests.push(request.clone())
      const answer = answers[Math.min(requests.length, answers.length) - 1]
      return answer()
    }),
  )
  return requests
}

const json = (
  body: Record<string, unknown>,
  status: number,
  headers: Record<string, string> = {},
) => HttpResponse.json(body, { status, headers })

/** Fills the form with valid values and saves it, by keyboard. */
async function fillAndSave(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByRole('textbox', { name: 'Name' }), 'Ada Lovelace')
  await user.type(screen.getByRole('textbox', { name: 'Phone' }), '0501234567')
  await user.keyboard('{Enter}')
}

function expectInputKept() {
  expect(screen.getByRole('textbox', { name: 'Name' })).toHaveProperty('value', 'Ada Lovelace')
  // The phone is written out once focus leaves it, but the form keeps it as typed.
  expect(screen.getByRole('textbox', { name: 'Phone' })).toHaveProperty('value', '050-123-4567')
}

describe('SchemaForm', () => {
  test('GivenAValidationProblemListingTwoFields_WhenTheFormReceivesIt_ThenBothFieldsShowTheirTranslatedText', async () => {
    const user = userEvent.setup()
    answerSaves(() =>
      json(
        problem(400, 'INVALID_REQUEST', {
          errors: [
            { code: 'WRONG_SIZE', pointer: '/name', params: { max: 100 } },
            { code: 'WRONG_FORMAT', pointer: '/phone', params: {} },
          ],
        }),
        400,
      ),
    )
    renderComponent(<ContactForm etag='"3"' />)

    await fillAndSave(user)

    expect(
      await screen.findByRole('textbox', {
        name: 'Name',
        description: 'This is too long or too short. Change its length, then save again.',
      }),
    ).toBeDefined()
    expect(
      screen.getByRole('textbox', {
        name: 'Phone',
        description: 'This is not in the expected form. Check what was entered and correct it.',
      }),
    ).toBeDefined()
    const summary = screen.getByRole('alert')
    expect(summary.textContent).toContain('Correct the marked fields, then save again.')
    expect(document.activeElement).toBe(summary)
  })

  test('GivenARuleRefusalTheFormMapsToAField_WhenReceived_ThenTheFieldSaysWhyAndTheSummaryAsksToCorrectIt', async () => {
    const user = userEvent.setup()
    answerSaves(() => json(problem(422, 'INVALID_PHONE_NUMBER'), 422))
    renderComponent(<ContactForm etag='"3"' />)

    await fillAndSave(user)

    expect(
      await screen.findByRole('textbox', {
        name: 'Phone',
        description:
          'This is not a valid phone number. Enter the full number with its area code, such as 050-123-4567.',
      }),
    ).toBeDefined()
    expect(screen.getByRole('alert').textContent).toContain(
      'Some of what was sent could not be accepted.',
    )
  })

  test('GivenAnItemForAFieldTheFormLacks_WhenReceived_ThenTheSummaryListsIt', async () => {
    const user = userEvent.setup()
    answerSaves(() =>
      json(
        problem(400, 'INVALID_REQUEST', { errors: [{ code: 'REQUIRED', pointer: '/address' }] }),
        400,
      ),
    )
    renderComponent(<ContactForm etag='"3"' />)

    await fillAndSave(user)

    const summary = await screen.findByRole('alert')
    expect(within(summary).getByRole('listitem').textContent).toBe('This is required. Fill it in.')
  })

  test('GivenAStaleVersionAnswer_WhenSaving_ThenTheUserIsToldAndTheirInputIsKept', async () => {
    const user = userEvent.setup()
    const onReload = vi.fn()
    answerSaves(() =>
      json(problem(412, 'STALE_VERSION', { params: { kind: 'Contact', id: 'c1' } }), 412),
    )
    renderComponent(<ContactForm etag='"3"' onReload={onReload} />)

    await fillAndSave(user)

    const summary = await screen.findByRole('alert')
    expect(summary.textContent).toContain('Someone else changed this after it was opened here')
    expect(summary.textContent).toContain('Your changes are still on the screen.')
    expectInputKept()
    await user.click(within(summary).getByRole('button', { name: 'Reload' }))
    expect(onReload).toHaveBeenCalledOnce()
  })

  test('GivenA503WithRetryAfter_WhenSaving_ThenTheUserIsToldToTryAgainAndTheirInputIsKept', async () => {
    const user = userEvent.setup()
    const requests = answerSaves(
      () => json(problem(503, 'TRY_AGAIN_LATER'), 503, { 'Retry-After': '2' }),
      () => new HttpResponse(null, { status: 204, headers: { ETag: '"4"' } }),
    )
    renderComponent(<ContactForm etag='"3"' />)

    await fillAndSave(user)

    const summary = await screen.findByRole('alert')
    expect(summary.textContent).toContain('Try again in a few seconds.')
    expectInputKept()
    expect(requests).toHaveLength(1)

    await user.click(within(summary).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('The contact is saved.')).toBeDefined()
    expect(requests).toHaveLength(2)
    expect(await requests[1].json()).toEqual({ name: 'Ada Lovelace', phone: '0501234567' })
  })

  test('GivenAViewWithNoVersion_WhenSaving_ThenTheRequestSendsIfNoneMatch', async () => {
    const user = userEvent.setup()
    const requests = answerSaves(
      () => new HttpResponse(null, { status: 204, headers: { ETag: '"1"' } }),
    )
    renderComponent(<ContactForm etag={null} />)

    await fillAndSave(user)

    await screen.findByText('The contact is saved.')
    expect(requests[0].headers.get('If-None-Match')).toBe('*')
    expect(requests[0].headers.get('If-Match')).toBeNull()
  })

  test('GivenTheLoadedETag_WhenSaving_ThenTheRequestSendsItAsIfMatch', async () => {
    const user = userEvent.setup()
    const onSaved = vi.fn()
    const requests = answerSaves(() => json({ id: 'c1' }, 200, { ETag: '"4"' }))
    renderComponent(<ContactForm etag='"3"' onSaved={onSaved} />)

    await fillAndSave(user)

    expect(await screen.findByText('The contact is saved.')).toBeDefined()
    expect(requests[0].headers.get('If-Match')).toBe('"3"')
    expect(requests[0].headers.get('If-None-Match')).toBeNull()
    expect(onSaved).toHaveBeenCalledWith({ data: { id: 'c1' }, etag: '"4"' })
  })

  test('GivenAnUnexpectedError_WhenSaving_ThenTheUserIsToldToReloadAndNothingIsResent', async () => {
    const user = userEvent.setup()
    const requests = answerSaves(() => json(problem(500, 'UNEXPECTED_ERROR'), 500))
    renderComponent(<ContactForm etag='"3"' onReload={() => undefined} />)

    await fillAndSave(user)

    const summary = await screen.findByRole('alert')
    expect(summary.textContent).toContain('Your change may have been saved.')
    expect(within(summary).getByRole('button', { name: 'Reload' })).toBeDefined()
    expect(requests).toHaveLength(1)
  })

  test('GivenAProxysPageInsteadOfAProblem_WhenSaving_ThenTheGeneralTextIsShown', async () => {
    const user = userEvent.setup()
    answerSaves(() => new HttpResponse('<html>502 Bad Gateway</html>', { status: 502 }))
    renderComponent(<ContactForm etag='"3"' />)

    await fillAndSave(user)

    const summary = await screen.findByRole('alert')
    expect(summary.textContent).toContain('Something went wrong.')
    expect(summary.textContent).not.toContain('502')
  })

  test('GivenTheServerOutOfReach_WhenSaving_ThenTheUserIsToldAndCanTryAgain', async () => {
    const user = userEvent.setup()
    answerSaves(() => HttpResponse.error())
    renderComponent(<ContactForm etag='"3"' />)

    await fillAndSave(user)

    const summary = await screen.findByRole('alert')
    expect(summary.textContent).toContain('The server could not be reached.')
    expect(within(summary).getByRole('button', { name: 'Try again' })).toBeDefined()
    expectInputKept()
  })

  test('GivenASaveOnItsWay_WhenSavedAgain_ThenOnlyOneRequestIsSent', async () => {
    const user = userEvent.setup()
    let release: () => void = () => undefined
    const held = new Promise<void>((resolve) => {
      release = resolve
    })
    const requests: Request[] = []
    server.use(
      http.put(CONTACT_PATH, async ({ request }) => {
        requests.push(request)
        await held
        return new HttpResponse(null, { status: 204, headers: { ETag: '"4"' } })
      }),
    )
    renderComponent(<ContactForm etag='"3"' />)

    await fillAndSave(user)
    const button = screen.getByRole('button', { name: 'Save' })
    await waitFor(() => expect(button.getAttribute('aria-busy')).toBe('true'))
    await user.click(button)
    release()

    expect(await screen.findByText('The contact is saved.')).toBeDefined()
    expect(requests).toHaveLength(1)
    await waitFor(() => expect(button.getAttribute('aria-busy')).toBe('false'))
  })

  test('GivenFieldsTheSchemaRefuses_WhenSaved_ThenNothingIsSentAndEachFieldSaysWhy', async () => {
    const user = userEvent.setup()
    renderComponent(<ContactForm etag='"3"' />)

    await user.type(screen.getByRole('textbox', { name: 'Phone' }), '123')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(
      await screen.findByRole('textbox', {
        name: 'Name',
        description: 'This is required. Fill it in.',
      }),
    ).toBeDefined()
    expect(
      screen.getByRole('textbox', {
        name: 'Phone',
        description:
          'This is not a valid phone number. Enter the full number with its area code, such as 050-123-4567.',
      }),
    ).toBeDefined()
    // No handler answers the contact here, so a request sent would fail the test (src/test/server.ts).
    expect(screen.queryByRole('alert')).toBeNull()
  })
})

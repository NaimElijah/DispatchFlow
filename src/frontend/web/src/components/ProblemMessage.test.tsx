import { screen, within } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { describe, expect, test, vi } from 'vitest'
import type { Problem, RequestFailure } from '../lib/problems.ts'
import { renderComponent } from '../test/renderComponent.tsx'
import { ProblemMessage } from './ProblemMessage.tsx'

const TRACE_ID = '4bf92f3577b34da6a3ce929d0e0e4736'

function problem(status: number, code: string, params: Problem['params'] = {}): Problem {
  return { type: 'about:blank', title: 'Refused', status, code, params, traceId: TRACE_ID }
}

function alertText(): string {
  return screen.getByRole('alert').textContent ?? ''
}

describe('ProblemMessage', () => {
  test('GivenAProblemWithAnUnknownCode_WhenShown_ThenAGeneralMessageWithANextStepAndNotTheCode', () => {
    renderComponent(
      <ProblemMessage
        operation="write"
        failure={{ kind: 'refused', problem: problem(422, 'BRAND_NEW_RULE') }}
      />,
    )

    expect(alertText()).toContain('Something went wrong.')
    expect(alertText()).toContain('Try again. If it keeps happening, tell your administrator.')
    expect(alertText()).toContain(`Reference: ${TRACE_ID}`)
    expect(alertText()).not.toContain('BRAND_NEW_RULE')
  })

  test('GivenAReadThatFailedUnexpectedly_WhenShown_ThenItSaysSomethingWentWrongAndOffersToReadAgain', async () => {
    const user = userEvent.setup()
    const onRetry = vi.fn()
    renderComponent(
      <ProblemMessage
        operation="read"
        failure={{ kind: 'unexpected', problem: problem(500, 'UNEXPECTED_ERROR') }}
        onReload={() => undefined}
        onRetry={onRetry}
      />,
    )

    expect(alertText()).toContain('Something went wrong.')
    expect(alertText()).toContain('Try again. If it keeps happening, tell your administrator.')
    expect(alertText()).not.toContain('may have been saved')
    expect(alertText()).toContain(`Reference: ${TRACE_ID}`)
    expect(screen.queryByRole('button', { name: 'Reload' })).toBeNull()
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(onRetry).toHaveBeenCalledOnce()
  })

  test('GivenAReadAnsweredByAProxysPage_WhenShown_ThenItOffersToTryAgainWithNoReference', () => {
    renderComponent(
      <ProblemMessage
        operation="read"
        failure={{ kind: 'unexpected', problem: undefined }}
        onRetry={() => undefined}
      />,
    )

    expect(alertText()).toContain('Something went wrong.')
    expect(alertText()).not.toContain('Reference')
    expect(screen.getByRole('button', { name: 'Try again' })).toBeDefined()
  })

  test('GivenAKnownCodeWithParams_WhenShown_ThenItsTextHasThemFilledIn', () => {
    renderComponent(
      <ProblemMessage
        operation="write"
        failure={{
          kind: 'refused',
          problem: problem(422, 'TEXT_TOO_LONG', { kind: 'NAME', maxLength: 100 }),
        }}
      />,
    )

    expect(alertText()).toContain('This text is too long.')
    expect(alertText()).toContain('Shorten it to 100 characters or fewer.')
    expect(alertText()).not.toContain('{{')
  })

  test('GivenTheReference_WhenShownInHebrew_ThenItSitsInABdi', () => {
    renderComponent(
      <ProblemMessage
        operation="write"
        failure={{ kind: 'stale', problem: problem(412, 'STALE_VERSION') }}
      />,
      { language: 'he' },
    )

    expect(alertText()).toContain('מישהו אחר שינה את הרשומה')
    expect(screen.getByText(TRACE_ID).tagName).toBe('BDI')
  })

  test('GivenAStaleRecord_WhenTheScreenOffersAReload_ThenReloadCallsIt', async () => {
    const user = userEvent.setup()
    const onReload = vi.fn()
    renderComponent(
      <ProblemMessage
        operation="write"
        failure={{ kind: 'stale', problem: problem(412, 'STALE_VERSION') }}
        onReload={onReload}
        onRetry={() => undefined}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Reload' }))

    expect(onReload).toHaveBeenCalledOnce()
    expect(screen.queryByRole('button', { name: 'Try again' })).toBeNull()
  })

  test.each<[string, RequestFailure, string]>([
    ['busy', { kind: 'busy', problem: problem(503, 'TRY_AGAIN_LATER') }, 'The server is busy'],
    ['network', { kind: 'network' }, 'The server could not be reached.'],
  ])('GivenA_%s_Failure_WhenShown_ThenItOffersToTryAgain', async (_kind, failure, text) => {
    const user = userEvent.setup()
    const onRetry = vi.fn()
    renderComponent(<ProblemMessage operation="write" failure={failure} onRetry={onRetry} />)

    expect(alertText()).toContain(text)
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(onRetry).toHaveBeenCalledOnce()
  })

  test('GivenAnUnexpectedFailure_WhenShown_ThenItSaysToReloadBeforeSendingAgain', () => {
    renderComponent(
      <ProblemMessage
        operation="write"
        failure={{ kind: 'unexpected', problem: problem(500, 'UNEXPECTED_ERROR') }}
      />,
    )

    expect(alertText()).toContain('Your change may have been saved.')
    expect(alertText()).toContain('Reload the page to check, before you send it again.')
  })

  test('GivenAProxysPageInsteadOfAProblem_WhenShown_ThenItSaysToReloadBeforeSendingAgainWithNoReference', () => {
    renderComponent(
      <ProblemMessage operation="write" failure={{ kind: 'unexpected', problem: undefined }} />,
    )

    expect(alertText()).toContain('Reload the page to check, before you send it again.')
    expect(alertText()).not.toContain('Reference')
  })

  test('GivenItemsNoFieldShows_WhenShown_ThenEachIsListedWithItsText', () => {
    renderComponent(
      <ProblemMessage
        operation="write"
        failure={{ kind: 'refused', problem: problem(400, 'INVALID_REQUEST') }}
        items={[
          { code: 'REQUIRED', pointer: '/address' },
          { code: 'MISSING_PARAMETER', parameter: { name: 'If-Match', in: 'header' } },
          { code: 'BRAND_NEW_ITEM' },
        ]}
      />,
    )

    const items = within(screen.getByRole('list')).getAllByRole('listitem')
    // An item pointing into the body names a field this form lacks: the app and server disagree.
    expect(items.map((item) => item.textContent)).toEqual([
      'The app and the server do not match. Reload the page to get the latest version of the app, then try again.',
      'The app and the server do not match. Reload the page to get the latest version of the app, then try again.',
      'Something went wrong. Try again. If it keeps happening, tell your administrator.',
    ])
  })
})

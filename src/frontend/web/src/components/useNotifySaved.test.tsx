import type { Language } from '@dispatchflow/i18n'
import { act, fireEvent, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { renderComponent } from '../test/renderComponent.tsx'
import { useNotifySaved, type SavedNotice } from './useNotifySaved.ts'

/** Longer than Mantine's 4-second auto-close and its closing transition together. */
const WELL_PAST_AUTO_CLOSE_MS = 10_000

/** A step of the clock: each runs in its own `act`, so a timer a render starts gets its turn. */
const CLOCK_STEP_MS = 500

/** A button that tells the user a change is stored, as a screen does once its save is done. */
function NotifyProbe({ notice }: { readonly notice: SavedNotice }) {
  const notifySaved = useNotifySaved()
  return (
    <button type="button" onClick={() => notifySaved(notice)}>
      Store it
    </button>
  )
}

function showNotice(notice: SavedNotice, language: Language = 'en') {
  renderComponent(<NotifyProbe notice={notice} />, { language })
  fireEvent.click(screen.getByRole('button', { name: 'Store it' }))
}

function waitWellPastAutoClose() {
  // Mantine starts the auto-close timer only once the opening transition has rendered.
  for (let waited = 0; waited < WELL_PAST_AUTO_CLOSE_MS; waited += CLOCK_STEP_MS) {
    act(() => vi.advanceTimersByTime(CLOCK_STEP_MS))
  }
}

// Mantine closes a notice on a timer, so these tests drive the clock; Testing Library's waiting
// helpers would wait on the faked clock, so they query the page directly after each step.
describe('useNotifySaved', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  test('GivenAPlainNotice_WhenShown_ThenItClosesByItself', () => {
    showNotice({ text: 'The depot is saved.' })
    expect(screen.getByText('The depot is saved.')).toBeDefined()
    // Mantine's close button has no name of its own, so the notice is checked for any button.
    expect(within(screen.getByRole('alert')).queryByRole('button')).toBeNull()

    waitWellPastAutoClose()

    expect(screen.queryByText('The depot is saved.')).toBeNull()
  })

  test('GivenANoticeWithAnAction_WhenShown_ThenItStaysUntilItsNamedCloseButtonIsPressed', () => {
    showNotice({
      text: 'The customer is saved.',
      action: { label: 'Add another', onClick: vi.fn() },
    })

    waitWellPastAutoClose()
    expect(screen.getByText('The customer is saved.')).toBeDefined()
    fireEvent.click(screen.getByRole('button', { name: 'Close' }))
    waitWellPastAutoClose()

    expect(screen.queryByText('The customer is saved.')).toBeNull()
  })

  test('GivenANoticeWithAnAction_WhenTheActionIsPressed_ThenItRunsAndTheNoticeCloses', () => {
    const onClick = vi.fn()
    showNotice({ text: 'The customer is saved.', action: { label: 'Add another', onClick } })

    fireEvent.click(screen.getByRole('button', { name: 'Add another' }))
    waitWellPastAutoClose()

    expect(onClick).toHaveBeenCalledOnce()
    expect(screen.queryByText('The customer is saved.')).toBeNull()
  })

  test('GivenANoticeWithAnActionClosedFromItsButton_WhenItCloses_ThenFocusGoesBackWhereItWas', () => {
    renderComponent(
      <NotifyProbe
        notice={{
          text: 'The customer is saved.',
          action: { label: 'Add another', onClick: vi.fn() },
        }}
      />,
    )
    const storeIt = screen.getByRole('button', { name: 'Store it' })
    storeIt.focus()
    fireEvent.click(storeIt)
    const close = screen.getByRole('button', { name: 'Close' })
    close.focus()

    fireEvent.click(close)
    waitWellPastAutoClose()

    // The notice sits at the end of the page; focus left with it would drop to the page itself.
    expect(document.activeElement).toBe(storeIt)
  })

  test('GivenFocusMovedOutOfTheNotice_WhenItCloses_ThenFocusStaysWhereTheUserIs', () => {
    renderComponent(
      <>
        <NotifyProbe
          notice={{
            text: 'The customer is saved.',
            action: { label: 'Add another', onClick: vi.fn() },
          }}
        />
        <input aria-label="Next field" />
      </>,
    )
    const storeIt = screen.getByRole('button', { name: 'Store it' })
    storeIt.focus()
    fireEvent.click(storeIt)
    const nextField = screen.getByRole('textbox', { name: 'Next field' })
    nextField.focus()

    // A pointer closes the notice while the keyboard's focus is elsewhere.
    fireEvent.click(screen.getByRole('button', { name: 'Close' }))
    waitWellPastAutoClose()

    expect(document.activeElement).toBe(nextField)
  })

  test('GivenTheElementFocusedWhenShownIsGone_WhenTheNoticeCloses_ThenFocusStaysOnThePage', () => {
    const { unmount } = renderComponent(
      <NotifyProbe
        notice={{
          text: 'The customer is saved.',
          action: { label: 'Add another', onClick: vi.fn() },
        }}
      />,
    )
    const storeIt = screen.getByRole('button', { name: 'Store it' })
    storeIt.focus()
    fireEvent.click(storeIt)
    const close = screen.getByRole('button', { name: 'Close' })
    unmount()
    expect(storeIt.isConnected).toBe(false)
    close.focus()

    fireEvent.click(close)
    waitWellPastAutoClose()

    expect(document.activeElement).not.toBe(storeIt)
  })

  test('GivenANoticeWithAnActionInHebrew_WhenShown_ThenItsCloseButtonIsNamedInHebrew', () => {
    showNotice(
      { text: 'הלקוח נשמר.', action: { label: 'הוספת לקוח נוסף', onClick: vi.fn() } },
      'he',
    )

    expect(screen.getByRole('button', { name: 'סגירה' })).toBeDefined()
  })
})

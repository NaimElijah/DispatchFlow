import type { Language } from '@dispatchflow/i18n'
import { act, fireEvent, screen } from '@testing-library/react'
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

  test('GivenANoticeWithAnActionInHebrew_WhenShown_ThenItsCloseButtonIsNamedInHebrew', () => {
    showNotice(
      { text: 'הלקוח נשמר.', action: { label: 'הוספת לקוח נוסף', onClick: vi.fn() } },
      'he',
    )

    expect(screen.getByRole('button', { name: 'סגירה' })).toBeDefined()
  })
})

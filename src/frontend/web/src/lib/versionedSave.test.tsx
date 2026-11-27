import { screen, waitFor } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { describe, expect, test, vi } from 'vitest'
import { renderComponent } from '../test/renderComponent.tsx'
import { useVersionedSave, type VersionedSaveOptions } from './versionedSave.ts'

interface SaveProbeProps {
  readonly send: VersionedSaveOptions<object, unknown>['send']
  readonly onFailed: (error: unknown) => void
}

/** A button that saves through `useVersionedSave` and reports how the save failed. */
function SaveProbe({ send, onFailed }: SaveProbeProps) {
  const save = useVersionedSave({ send })
  return (
    <button
      type="button"
      onClick={() => void save.mutateAsync({ body: {}, etag: null }).catch(onFailed)}
    >
      Save
    </button>
  )
}

describe('useVersionedSave', () => {
  test('GivenASendWithABugThatThrowsATypeError_WhenSaving_ThenItFailsAsItselfAndNotAsUnreachable', async () => {
    const user = userEvent.setup()
    const bug = new TypeError("Cannot read properties of undefined (reading 'id')")
    const onFailed = vi.fn()
    renderComponent(
      <SaveProbe
        send={() => {
          throw bug
        }}
        onFailed={onFailed}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(onFailed).toHaveBeenCalledOnce())
    expect(onFailed).toHaveBeenCalledWith(bug)
  })

  test('GivenASendThatThrowsAnyOtherError_WhenSaving_ThenItFailsAsItself', async () => {
    const user = userEvent.setup()
    const bug = new Error('A bug in the screen')
    const onFailed = vi.fn()
    renderComponent(<SaveProbe send={() => Promise.reject(bug)} onFailed={onFailed} />)

    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(onFailed).toHaveBeenCalledOnce())
    expect(onFailed).toHaveBeenCalledWith(bug)
  })
})

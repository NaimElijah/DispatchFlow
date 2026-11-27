import { notifications } from '@mantine/notifications'
import { createElement, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { SavedNoticeContent } from './SavedNoticeContent.tsx'

/** Marks a saved notice with an action, so its close can tell whether focus was in it. */
const NOTICE_MARK = 'data-saved-notice'

/** One thing a saved notice offers to do next, such as adding the next customer. */
export interface SavedNoticeAction {
  /** What the action does, already translated, such as "Add another customer". */
  readonly label: string
  /** Does it, such as `() => navigate({ to: '/customers/new' })`; the notice then closes. */
  readonly onClick: () => void
}

/** A stored change, as the user is told of it. */
export interface SavedNotice {
  /**
   * What was stored, already translated, such as "The customer is saved.": the kind of record,
   * never a person's name, phone or address. A notice with an action stays until closed, and may
   * outlive the session it was shown in.
   */
  readonly text: string
  /** One thing to do next. A notice that offers one stays until the user closes it. */
  readonly action?: SavedNoticeAction
}

/**
 * Tells the user a change is stored, in the one style every screen's success shares: a green
 * notification, which Mantine announces as an alert.
 *
 * A plain notice closes by itself after Mantine's four seconds, with no close button. A notice
 * with an action stays until the user closes it, with a close button named in the interface
 * language, because a keyboard or screen-reader user needs time to reach an action at the end of
 * the page (WCAG 2.2.1, NFR-46); pressing the action closes it too. When it closes with focus in
 * it, focus goes back to where it was when the notice was shown, such as the save button, rather
 * than to the page (WCAG 2.4.3); an element remounted since under the same id, as a time field's
 * hours box is on a reset, is found again by that id. The action is a button and not a link: notifications render
 * outside the router, so the screen passes its own navigation.
 *
 * @returns the function a screen calls once its save is done, such as in `useVersionedSave`'s
 *   `onSaved`
 */
export function useNotifySaved(): (notice: SavedNotice) => void {
  const { t } = useTranslation('common')
  return useCallback(
    ({ text, action }: SavedNotice) => {
      if (action === undefined) {
        notifications.show({ message: text, color: 'teal', withCloseButton: false })
        return
      }
      const returnTo = document.activeElement
      const id: string = notifications.show({
        [NOTICE_MARK]: true,
        message: createElement(SavedNoticeContent, {
          text,
          actionLabel: action.label,
          onAction: () => {
            notifications.hide(id)
            action.onClick()
          },
        }),
        color: 'teal',
        autoClose: false,
        withCloseButton: true,
        closeButtonProps: { 'aria-label': t('close') },
        onClose: () => {
          // Mantine calls this as it hides the notice, while its button still has focus.
          const focused = document.activeElement
          const focusInNotice =
            focused === null ||
            focused === document.body ||
            focused.closest(`[${NOTICE_MARK}]`) !== null
          const successor =
            returnTo instanceof HTMLElement && !returnTo.isConnected && returnTo.id !== ''
              ? document.getElementById(returnTo.id)
              : returnTo
          if (focusInNotice && successor instanceof HTMLElement) {
            successor.focus()
          }
        },
      })
    },
    [t],
  )
}

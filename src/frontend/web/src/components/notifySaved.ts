import { notifications } from '@mantine/notifications'
import type { ReactNode } from 'react'

/**
 * Tells the user a change is stored, in the one style every screen's success shares: a green
 * notification that Mantine announces and closes by itself. It has no close button, because a
 * button would need a label from the screen, and the notification goes on its own.
 *
 * @param message what was stored, already translated, such as "The depot is saved." with a link to
 *   the form for the next one
 */
export function notifySaved(message: ReactNode): void {
  notifications.show({ message, color: 'teal', withCloseButton: false })
}

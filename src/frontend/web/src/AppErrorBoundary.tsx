import type { i18n } from 'i18next'
import { Component, type ReactNode } from 'react'

/** What the last boundary wraps, and the i18next instance it translates its message with. */
export interface AppErrorBoundaryProps {
  /** The app's i18next instance, read directly, so the message needs no provider that may have failed. */
  i18n: i18n
  /** Everything the app renders. */
  children: ReactNode
}

interface AppErrorBoundaryState {
  failed: boolean
}

/**
 * The last error boundary, around everything the app renders. When the router's error page, or a
 * provider above it, fails too, it shows the translated message in plain HTML, never the error, its
 * message or its stack (NFR-143). It replaces TanStack Router's own global boundary, which
 * `router.ts` turns off because its fallback offers to show the error's message.
 *
 * React reports the error it caught to the console; error reports go to GlitchTip once it is set up
 * (ADR-079).
 */
export class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  state: AppErrorBoundaryState = { failed: false }

  static getDerivedStateFromError(): AppErrorBoundaryState {
    return { failed: true }
  }

  render() {
    if (!this.state.failed) {
      return this.props.children
    }
    const t = this.props.i18n.getFixedT(null, 'shell')
    return (
      <main>
        <h1>{t('error.title')}</h1>
        <p>{t('error.body')}</p>
      </main>
    )
  }
}

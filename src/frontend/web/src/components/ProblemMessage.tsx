import { Alert, Button, List, Stack, Text } from '@mantine/core'
import type { TFunction } from 'i18next'
import type { Ref } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import type { Problem, ProblemItem, RequestFailure } from '../lib/problems.ts'
import { fieldErrorText, problemText, type ProblemText } from '../lib/problemText.ts'

/** What a failed call shows, and what the user can do about it. */
export interface ProblemMessageProps {
  /** How the call failed. */
  readonly failure: RequestFailure
  /** A refusal's items that no field shows, listed under the message. */
  readonly items?: readonly ProblemItem[]
  /**
   * Whether the refusal's own code already sits on a field: the message then asks the user to
   * correct the marked fields, rather than repeat it.
   */
  readonly onField?: boolean
  /** Reloads what the screen shows: offered for a stale record and an unexpected failure. */
  readonly onReload?: () => void
  /** Sends the call again: offered when the core was busy or could not be reached. */
  readonly onRetry?: () => void
  /** The message's element, which takes focus when a form moves the user to it. */
  readonly ref?: Ref<HTMLDivElement>
}

/** What a failure shows: its words, the problem whose reference it gives, and its one action. */
interface Shown {
  readonly words: ProblemText
  readonly problem: Problem | undefined
  readonly action: 'reload' | 'retry' | undefined
}

function shownFor(failure: RequestFailure, onField: boolean, t: TFunction<'problems'>): Shown {
  switch (failure.kind) {
    case 'stale':
      return {
        words: problemText(t, 'STALE_VERSION'),
        problem: failure.problem,
        action: 'reload',
      }
    case 'busy':
      return {
        words: problemText(t, 'TRY_AGAIN_LATER'),
        problem: failure.problem,
        action: 'retry',
      }
    case 'network':
      return {
        words: { text: t('network.text'), nextStep: t('network.nextStep') },
        problem: undefined,
        action: 'retry',
      }
    case 'unexpected':
      // With no problem, something in front of the core answered, so there is no reference; the
      // change may be stored all the same, so the user reloads before sending it again.
      return {
        words: problemText(t, 'UNEXPECTED_ERROR'),
        problem: failure.problem,
        action: 'reload',
      }
    case 'refused':
      return {
        words: onField
          ? problemText(t, 'INVALID_REQUEST')
          : problemText(t, failure.problem.code, failure.problem.params),
        problem: failure.problem,
        action: undefined,
      }
  }
}

/**
 * An item no field shows, in one line. One that points into the body names a field this form lacks,
 * so the app and the server disagree on the request, and it says so rather than ask the user to
 * fill in a field they cannot see.
 */
function itemText(t: TFunction<'problems'>, item: ProblemItem): string {
  if (item.pointer !== undefined) {
    return `${t('mismatch.text')} ${t('mismatch.nextStep')}`
  }
  return fieldErrorText(t, item.code, item.params)
}

/**
 * Why a call failed, in the interface language: what happened, what to do next, and a reference
 * for the administrator when the core gave one (NFR-69, NFR-143, ADR-043). It never shows a code.
 * It works on its own, beside a map or a button, as well as at the top of a form, where it is the
 * error summary.
 *
 * A stale record keeps the user's input on the screen, and its Reload is the screen's own: a form
 * reloads its view, a plan refetches itself (ADR-057). An action shows only when the screen offers
 * it.
 */
export function ProblemMessage({
  failure,
  items = [],
  onField = false,
  onReload,
  onRetry,
  ref,
}: ProblemMessageProps) {
  const { t } = useTranslation('problems')
  const { t: tCommon } = useTranslation('common')
  const { words, problem, action } = shownFor(failure, onField, t)

  return (
    <Alert ref={ref} tabIndex={-1} color="red" title={words.text}>
      <Stack gap="xs">
        <Text size="sm">{words.nextStep}</Text>
        {items.length > 0 && (
          <List size="sm">
            {items.map((item, index) => (
              // Items have no identity of their own; their order is the core's and never changes.
              <List.Item key={index}>{itemText(t, item)}</List.Item>
            ))}
          </List>
        )}
        {problem !== undefined && (
          <Text size="xs">
            <Trans
              t={t}
              i18nKey="reference"
              values={{ traceId: problem.traceId }}
              components={{ id: <bdi /> }}
            />
          </Text>
        )}
        {action === 'reload' && onReload !== undefined && (
          <Button variant="light" color="red" onClick={onReload} w="fit-content">
            {tCommon('reload')}
          </Button>
        )}
        {action === 'retry' && onRetry !== undefined && (
          <Button variant="light" color="red" onClick={onRetry} w="fit-content">
            {tCommon('tryAgain')}
          </Button>
        )}
      </Stack>
    </Alert>
  )
}

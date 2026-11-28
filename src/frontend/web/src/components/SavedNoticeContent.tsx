import { Button, Stack, Text } from '@mantine/core'

/** What a saved notice with an action shows. */
export interface SavedNoticeContentProps {
  /** What was stored, already translated. */
  readonly text: string
  /** The action's label, already translated, such as "Add another customer". */
  readonly actionLabel: string
  /** Runs the action and closes the notice. */
  readonly onAction: () => void
}

/** The body of a saved notice that offers one thing to do next: its text, then its action. */
export function SavedNoticeContent({ text, actionLabel, onAction }: SavedNoticeContentProps) {
  return (
    <Stack gap="xs" align="flex-start">
      <Text size="sm">{text}</Text>
      <Button variant="subtle" size="compact-sm" onClick={onAction}>
        {actionLabel}
      </Button>
    </Stack>
  )
}

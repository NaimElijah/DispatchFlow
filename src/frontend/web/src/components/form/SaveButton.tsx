import { Button, Loader } from '@mantine/core'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useSchemaFormState } from './schemaFormState.ts'

/** The save button's words, when the form saves something more specific than "Save". */
export interface SaveButtonProps {
  readonly children?: ReactNode
}

/**
 * Submits the `SchemaForm` it sits in. While the save is on its way it shows a spinner and says it
 * is busy, and stays focusable: a disabled button would drop a keyboard user's focus to the page.
 * The form ignores a second press meanwhile.
 */
export function SaveButton({ children }: SaveButtonProps) {
  const { t } = useTranslation('common')
  const { pending } = useSchemaFormState()

  return (
    <Button
      type="submit"
      w="fit-content"
      aria-busy={pending}
      leftSection={pending ? <Loader size="xs" color="white" /> : undefined}
    >
      {children ?? t('save')}
    </Button>
  )
}

import { createContext, useContext } from 'react'

/** What a form's own pieces need to know of the form around them. */
export interface SchemaFormState {
  /** Whether the form's save is on its way, so the save button shows it and waits. */
  readonly pending: boolean
  /**
   * How many times the form was reset. A reset to the value a field already holds changes nothing
   * the field can see, so a field whose input keeps state of its own, such as the time picker's
   * half-typed boxes, starts afresh on each.
   */
  readonly resetCount: number
}

/** The state `SchemaForm` gives the pieces inside it. */
export const SchemaFormStateContext = createContext<SchemaFormState | undefined>(undefined)

/**
 * The state of the `SchemaForm` around a piece.
 *
 * @throws Error when the piece is used outside a `SchemaForm`, where it would save nothing
 */
export function useSchemaFormState(): SchemaFormState {
  const state = useContext(SchemaFormStateContext)
  if (state === undefined) {
    throw new Error('A form piece such as SaveButton was rendered outside a SchemaForm')
  }
  return state
}

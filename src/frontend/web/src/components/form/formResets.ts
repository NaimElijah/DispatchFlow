/**
 * The resets of one form, counted for the fields whose input keeps state of its own, such as a
 * time picker's half-typed boxes. React Hook Form tells a field nothing of a reset to the value it
 * already holds, so `useSchemaForm` records each here, and such a field starts afresh on each one
 * that reaches it. Its identity never changes, so a form that holds it stays the same object.
 */
export interface FormResets {
  /** Calls `listener` after every reset; returns what stops it. */
  readonly subscribe: (listener: () => void) => () => void
  /**
   * The resets that reach a field: the whole form's, and those of its own path or a path above it,
   * such as a window's for one of its ends.
   *
   * @param path the field's path, such as `window.start`
   */
  readonly countFor: (path: string) => number
  /** Records a reset of the whole form. */
  readonly resetAll: () => void
  /**
   * Records a reset of one field and what lies under it.
   *
   * @param path the reset field's path
   */
  readonly resetPath: (path: string) => void
}

/** A form's reset record, empty: `useSchemaForm` makes one for each form. */
export function createFormResets(): FormResets {
  let wholeForm = 0
  const byPath = new Map<string, number>()
  const listeners = new Set<() => void>()
  const notify = () => {
    for (const listener of listeners) {
      listener()
    }
  }
  return {
    subscribe: (listener) => {
      listeners.add(listener)
      return () => {
        listeners.delete(listener)
      }
    },
    countFor: (path) => {
      let count = wholeForm
      for (const [resetPath, resets] of byPath) {
        if (path === resetPath || path.startsWith(`${resetPath}.`)) {
          count += resets
        }
      }
      return count
    },
    resetAll: () => {
      wholeForm += 1
      notify()
    },
    resetPath: (path) => {
      byPath.set(path, (byPath.get(path) ?? 0) + 1)
      notify()
    },
  }
}

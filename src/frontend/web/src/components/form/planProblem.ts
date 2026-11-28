import type { FieldPath, FieldValues } from 'react-hook-form'
import { z } from '@dispatchflow/schemas'
import type { Problem, ProblemItem } from '@dispatchflow/api-client'

/**
 * Names the field a refusal belongs on when the core gave no pointer to it: a 422's rules name no
 * field, so a form maps the codes it knows, such as `INVALID_PHONE_NUMBER` to `phone`.
 */
export type FieldFor<TValues extends FieldValues> = (
  code: string,
  params: Readonly<Record<string, unknown>>,
) => FieldPath<TValues> | undefined

/** A refusal that sits on one of the form's fields. */
export interface FieldRefusal<TValues extends FieldValues> {
  readonly path: FieldPath<TValues>
  readonly code: string
  readonly params: Readonly<Record<string, unknown>>
}

/** Where a refusal's parts go: onto the form's fields, or into its error summary. */
export interface ProblemPlan<TValues extends FieldValues> {
  readonly onFields: readonly FieldRefusal<TValues>[]
  /** The items no field shows, which the summary lists. */
  readonly items: readonly ProblemItem[]
  /** Whether the refusal's own code went onto a field, so the summary need not repeat it. */
  readonly onField: boolean
}

const POINTER_ESCAPES = /~[01]/g

/**
 * Reads an RFC 6901 JSON Pointer as the keys it walks: `/stops/0/name` is `stops`, `0`, `name`.
 * The empty pointer names the whole body, which is no field.
 */
function keysOf(pointer: string): string[] | undefined {
  if (!pointer.startsWith('/')) {
    return undefined
  }
  return pointer
    .slice(1)
    .split('/')
    .map((key) => key.replace(POINTER_ESCAPES, (escape) => (escape === '~1' ? '/' : '~')))
}

/** The schema beneath wrappers that change no shape: optional, nullable, defaulted, piped. */
function unwrapped(schema: z.ZodType): z.ZodType {
  if (schema instanceof z.ZodOptional || schema instanceof z.ZodNullable) {
    return unwrapped(schema.unwrap() as z.ZodType)
  }
  if (schema instanceof z.ZodDefault) {
    return unwrapped(schema.unwrap() as z.ZodType)
  }
  if (schema instanceof z.ZodPipe) {
    return unwrapped(schema.in as z.ZodType)
  }
  return schema
}

const ARRAY_INDEX = /^(0|[1-9]\d*)$/

function childOf(schema: z.ZodType, key: string): z.ZodType | undefined {
  const inner = unwrapped(schema)
  if (inner instanceof z.ZodObject) {
    return Object.hasOwn(inner.shape, key) ? (inner.shape[key] as z.ZodType) : undefined
  }
  if (inner instanceof z.ZodArray && ARRAY_INDEX.test(key)) {
    return inner.element as z.ZodType
  }
  return undefined
}

/**
 * The form's field at a pointer, when its schema has one there; the keys join as the form's path.
 * A list is no field: no field shows a refusal of the list as a whole, such as one with too many
 * stops, so it goes to the summary.
 */
function fieldAt<TValues extends FieldValues>(
  schema: z.ZodType,
  pointer: string,
): FieldPath<TValues> | undefined {
  const keys = keysOf(pointer)
  if (keys === undefined || keys.length === 0) {
    return undefined
  }
  let node: z.ZodType | undefined = schema
  for (const key of keys) {
    node = childOf(node, key)
    if (node === undefined) {
      return undefined
    }
  }
  if (unwrapped(node) instanceof z.ZodArray) {
    return undefined
  }
  // The schema's keys are the form's, so a path the schema has is one of the form's fields.
  return keys.join('.') as FieldPath<TValues>
}

/**
 * Decides where each part of a refusal shows. A 400's item goes onto the field its pointer names,
 * when the form's schema has that field. Any other item, and a refusal that lists none, goes onto
 * the field `fieldFor` names. What lands on no field stays for the error summary, which also shows
 * the refusal's own words.
 *
 * @param problem the core's refusal
 * @param schema the form's schema, whose keys are the request body's
 * @param fieldFor the form's map from a rule's code to its field, when it has one
 */
export function planProblem<TValues extends FieldValues>(
  problem: Problem,
  schema: z.ZodType,
  fieldFor?: FieldFor<TValues>,
): ProblemPlan<TValues> {
  const fieldOf = (item: ProblemItem): FieldPath<TValues> | undefined => {
    if (item.pointer !== undefined) {
      return fieldAt<TValues>(schema, item.pointer)
    }
    return item.parameter === undefined ? fieldFor?.(item.code, item.params ?? {}) : undefined
  }

  if (problem.errors === undefined) {
    const path = fieldFor?.(problem.code, problem.params)
    return path === undefined
      ? { onFields: [], items: [], onField: false }
      : {
          onFields: [{ path, code: problem.code, params: problem.params }],
          items: [],
          onField: true,
        }
  }

  const onFields: FieldRefusal<TValues>[] = []
  const items: ProblemItem[] = []
  for (const item of problem.errors) {
    const path = fieldOf(item)
    if (path === undefined) {
      items.push(item)
    } else {
      onFields.push({ path, code: item.code, params: item.params ?? {} })
    }
  }
  return { onFields, items, onField: false }
}

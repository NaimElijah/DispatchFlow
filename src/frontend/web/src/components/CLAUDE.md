# Shared components

## Rules

- **A form** ([ADR-153]) is a schema from `@dispatchflow/schemas`, `useSchemaForm`, the fields of
  `form/fields/` in a `SchemaForm`, and a save whose `onSubmit` returns its promise, such as
  `useVersionedSave`'s `mutateAsync`, so a second press waits for the first. A refusal outside a
  form shows in `ProblemMessage`, a row list in `DataTable`, a stored change through `notifySaved`.
- A field takes the form's `control`, a `name` typed with `FieldPathByValue` so it binds only its
  own kind of value, and a translated `label`. Mantine ties a plain input's error through
  `aria-describedby`; a composite one ties it to each of its own inputs, as `TimeField` does.
- A field never changes the form's value as the user leaves it: that re-runs the field's check and
  wipes a refusal the server gave. Write a value out for display only, as `PhoneField` does, and
  keep Mantine from clamping or rewriting it, as `NumberField` does.
- Pass a field's props by spreading `field`, as `TextField` does, or destructure `ref` from it, as
  `TimeField` does. A component that reads `field.ref` as a member fails ESLint's
  `react-hooks/refs` on every other member it reads.
- jsdom runs no CSS transition, so an opening select's list or calendar stays out of the
  accessibility tree in tests: query its options and days by their text or label.

[ADR-153]: ../../../../../docs/ADRs/ADR-153-the-web-apps-shared-form-refusal-and-table-pieces.md

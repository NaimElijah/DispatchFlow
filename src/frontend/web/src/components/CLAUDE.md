# Shared components

The UI no area owns ([ADR-144]): the form pieces in `form/`, the map's in `map/`, and the pieces
every screen shares, such as `ProblemMessage` and `DataTable` ([ADR-153]).

## Rules

- A field takes the form's `control`, a `name` typed with `FieldPathByValue` so it binds only its
  own kind of value, and a translated `label`. Mantine ties a plain input's error through
  `aria-describedby`; a composite one ties it to each of its own inputs, as `TimeField` does.
- A field never changes the form's value as the user leaves it: that re-runs the field's check and
  wipes a refusal the server gave. Write a value out for display only, as `PhoneField` does.
- Pass a field's props by spreading `field`, as `TextField` does. Reading `field.ref` or another
  of its members during render fails ESLint's `react-hooks/refs`.
- A piece two areas need comes here, beside the others, not into either area's folder.
- A new Mantine component is tested here first: `src/test/setup.ts` fails a test that renders a
  `<style>` element, and one that does is [ADR-143]'s revisit trigger, not a CSS fix.

[ADR-143]: ../../../../../docs/ADRs/ADR-143-the-web-app-shell-declares-routes-per-area-opens-in-english-and-renders-no-style-element.md
[ADR-144]: ../../../../../docs/ADRs/ADR-144-the-web-apps-features-stay-apart-and-its-lower-folders-never-import-upward.md
[ADR-153]: ../../../../../docs/ADRs/ADR-153-the-web-apps-shared-form-refusal-and-table-pieces.md

# Console

The desktop web app for admins and dispatchers. React 19, TypeScript 6.0, Vite 8, npm.

## Layout

- `src/main.tsx` mounts `src/App.tsx`, which today renders only a heading.
- Tests sit next to the code they test, as `*.test.tsx`.
- `src/test/setup.ts` configures Vitest; `vite.config.ts` sets the jsdom environment.

## Rules

- It is a desktop app ([ADR-003]). Lay it out for a desktop screen; it does not need to work on
  a phone.

## Tests

- Vitest with Testing Library, in jsdom.
- Query the page the way a user finds things: by role, label or text. Never assert on component
  internals or state.

## Commands

Run `just` recipes from the repository root, and `npm` or `npx` from this folder.

| Command                           | What it does                                                   |
| --------------------------------- | -------------------------------------------------------------- |
| `just console-check`              | ESLint, Prettier check, Vitest, then `tsc -b` and `vite build` |
| `just console-format`             | Prettier                                                       |
| `just console-run`                | Vite dev server                                                |
| `npx vitest run src/App.test.tsx` | One test file                                                  |

[ADR-003]: ../../../docs/ADRs/ADR-003-dispatcher-console-and-driver-app.md

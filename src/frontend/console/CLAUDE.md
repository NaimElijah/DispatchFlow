# Console: the web app

The web app for admins, dispatchers and drivers ([ADR-035]). Drivers work in it online only.
React 19, TypeScript 6.0, Vite 8, npm.

## Layout

- `src/main.tsx` mounts `src/App.tsx`, which today renders only a heading.
- Tests sit next to the code they test, as `*.test.tsx`.
- `src/test/setup.ts` configures Vitest; `vite.config.ts` sets the jsdom environment.

## Rules

- Lay it out for a desktop screen, except the driver screens, which must also work in a phone's
  browser (NFR-15, NFR-219).
- On a route in progress, the open app reports the driver's location every 30 seconds (FR-243). It
  learns of route changes from its event stream (FR-347, [ADR-026]), and takes proof photos with
  the camera, never from files (NFR-204).
- Read the event stream with `fetch` and eventsource-parser, not the browser's `EventSource`, which
  cannot send an `Authorization` header ([TS §10]).
- `osv-scanner.toml` holds the one vulnerability the audit ignores here, until 2027-01-31
  ([ADR-023]).

## Tests

- Vitest with Testing Library, in jsdom. From the Alpha ([TS §10], Decided), MSW answers API calls
  in tests.
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

[ADR-026]: ../../../docs/ADRs/ADR-026-server-sent-events-and-push-for-live-updates.md
[ADR-035]: ../../../docs/ADRs/ADR-035-both-apps-serve-every-role.md
[ADR-023]: ../../../docs/ADRs/ADR-023-accept-minimatch-and-glob-advisories.md
[TS §10]: ../../../docs/architecture/tech_stack.md#10-web-app

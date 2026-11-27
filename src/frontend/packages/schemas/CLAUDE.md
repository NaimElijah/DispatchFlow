# @dispatchflow/schemas

The Zod 4 schemas of both apps' forms ([TS §12], [ADR-153]). The phone app imports them too, so
nothing here touches the DOM, React or a screen.

## Rules

- Every form's schema lives here, named for what it saves, beside `exampleSchema`; never inside a
  feature's folder.
- Build a schema from `src/fields.ts`. Its keys are the request body's, so a 400's pointer names the
  field it refused.
- Take `z` from `src/zod.ts`, set never to compile code; an app takes the `z` this package exports
  (ESLint here and in the web app; `src/zod.test.ts`).
- A check never sets a message, because one set here beats the app's translating error map and
  reaches the screen untranslated (NFR-143). It names its code with `refusal(code, params)`: the
  code goes into `SchemaCode`, and its text and next step into `@dispatchflow/i18n`'s `problems`.
  `src/refusals.test.ts` proves it for `exampleSchema`; add a form's own schema to that test. The
  web app's `src/lib/problemText.test.ts` fails a `SchemaCode` with no text.
- A schema checks shape for quick feedback only. A rule the server owns, such as a date in the past,
  a duplicate or a full day, stays the server's ([TS §10]).
- Zod stays at 4.6.6 or later, the same exact version as the web app's ([ADR-095]).

[ADR-095]: ../../../../docs/ADRs/ADR-095-accept-the-zod-and-deepmerge-advisories-until-2027-01-31.md
[ADR-153]: ../../../../docs/ADRs/ADR-153-the-web-apps-shared-form-refusal-and-table-pieces.md
[TS §10]: ../../../../docs/architecture/tech_stack.md#10-web-app
[TS §12]: ../../../../docs/architecture/tech_stack.md#12-shared-typescript-packages

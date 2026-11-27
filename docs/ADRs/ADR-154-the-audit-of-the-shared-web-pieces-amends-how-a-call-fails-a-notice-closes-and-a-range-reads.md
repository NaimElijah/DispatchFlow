# ADR-154: The audit of the web app's shared pieces amends how a call fails, how a saved notice closes and how a time range reads

- **Status:** Accepted
- **Date:** 2026-11-27

## Context

[ADR-153] settled #173's shared pieces, and they merged on 27 November 2026. An audit after the
merge ran the production build in a browser under [ADR-069]'s Content Security Policy, in both
languages and by keyboard, and had the code and the tests reviewed afresh; the review of its own
fixes found more. What they found shapes these decisions:

- **A policy violation on every page with a form.** Zod 4 builds an object schema by probing
  `new Function`, to see whether it may compile a faster parser. The policy refuses it, and the
  browser reports the violation although Zod catches the refusal. Zod reads its `jitless` setting
  when a schema is built, not when it parses, and `@dispatchflow/schemas` builds its schemas as it
  is imported.
- **Failures shown as the wrong thing, or as nothing.**
  - The save turned any `TypeError` from a screen's `send` into "the server could not be
    reached", a missing record's ID included.
  - `fetch` resolves once an answer's headers are in, so a connection lost while the body came in
    failed later, unread, and the user was told nothing.
- **Reads had no shared way to fail.**
  - Only the versioned save turned an answer into a `RequestError`.
  - `ProblemMessage` told every unexpected failure that the change may have been saved, which is
    false after a list failed to load (#219, #233, #237).
  - The app's query cache retried a refused read three times before showing why.
- **A notice that vanished with its action.** The saved notice closed after four seconds with no
  close button, while its doc invited a link in it, as #203 wants. A keyboard or screen-reader
  user could not reach it in time ([NFR-46], WCAG 2.2.1).
- **A time range read two ways.** `08:00–12:00` shows 08:00 on the left in a table's `<bdi>`, and
  on the right bare in Hebrew text, where the bidirectional algorithm treats digits as
  right to left.
- **No optional number.** #203 and #212 need an optional service time ([BR-124]), but the number
  field took only a required number, and a schema made optional with `.optional()` refuses the
  `null` an emptied box holds.
- **A window's refusal went unnoticed.** React Hook Form focuses refused fields in the order they
  registered, and a window is no field, so focus stayed on Save, and the refusal was tied only to
  the group.
- **A reset that left a half-typed time.** A reset to the empty text a time field already holds
  changes no value, so Mantine's picker kept its digits for the next record. React Hook Form tells
  a field nothing of such a reset, and one run during a save leaves even `submitCount` as it was.
- **A table cell that wrote any value.** The default cell wrote `12.5` with no unit and a date in
  the device's zone ([NFR-68], [ADR-088]).
- **An example that kept its first version.** The example form sent the `ETag` it opened with on
  every save, so a form copied from it would fail its second save as stale.

## Decision

Decided on 27 November 2026. This amends [ADR-153] decisions 4, 5 and 10, and extends its
decisions 1, 2 and 9.

1. **Zod never compiles code.** `@dispatchflow/schemas` sets `jitless` in `src/zod.ts` before any
   schema is built, and exports that `z`.
   - Every schema of the package, and the web app, take Zod from there.
   - ESLint refuses a value import of `zod` or `zod/*` anywhere else in the package and in the web
     app's non-test files; Zod's types may still come from Zod.
   - A test watches `Function` while the package loads and parses.
2. **Only `fetch` and the answer's body decide that a call failed on the way.** `lib/coreCalls.ts`'s
   `fetchFromCore`:
   - turns a request fetch could not complete into a `RequestError` whose failure is `network`,
     whether no answer arrived or the policy blocked it;
   - reads a copy of each answer's body to its end, and turns an answer that broke off into
     `unexpected`: the request arrived, so a write may be stored;
   - lets anything thrown before the request, a `TypeError` included, fail as itself, and an abort
     stay an abort.

   The web app hands it to openapi-fetch as `fetch` when it builds its client (#148); no shared
   package imports it. This replaces [ADR-153] decision 4's "only a `TypeError` from `send`".
3. **Every call's answer is read one way, a read's as well as a write's.**
   - `answerOf` gives a success's data and `ETag`, or throws how the call failed.
   - `ProblemMessage` takes the call's `operation`. A failed read says that something went wrong,
     with its reference when there is one, and offers Try again. A write keeps [ADR-153]'s wording
     and its Reload after an unexpected failure. `SchemaForm` passes `write`.
   - A read is tried again only when it failed on the way, at most three times: no answer, a busy
     core, or something in front of the core that answered or broke off (`retryRead`). The core's
     own refusal or failure shows at once, and a write is never tried again.
   - `createQueryClient` makes the one query cache, for the app and for the tests alike, so the
     two cannot drift apart.
   - A save's error is typed as any `Error`, since a bug in `send` rejects as itself.
4. **A saved notice that offers an action stays until it is closed.** `useNotifySaved()` replaces
   `notifySaved`, amending [ADR-153] decision 10.
   - A plain notice closes by itself after Mantine's four seconds, with no close button.
   - A notice with an action has no time limit, and a close button named in the interface
     language. Pressing the action closes it too.
   - When it closes with focus in it, focus goes back to where it was when the notice was shown.
   - Its text names the kind of record and never a person, since it may outlive the session
     ([ADR-076]).
   - The action is a button that runs the screen's own navigation: notifications render outside
     the router, where a router link cannot work.
5. **A time range reads left to right in both languages**, 08:00 on the left, as the time field's
   boxes and a clock do.
   - Every screen shows a range inside `<bdi>`, which runs left to right when, as here, it holds
     no strong character (HTML's `dir` attribute, its auto state).
   - The formatter's doc says so, and a test pins the table's `<bdi>`.
6. **An optional number is `optionalNumber(block)`.** Empty means none and leaves the field out of
   the body, whether the box was emptied (`null`) or never typed in (`undefined`). A filled-in
   value passes the block's own checks. The number field binds a required number or one that takes
   `null`, and a path of `number | undefined` alone fails to compile.
7. **Focus on a refusal follows reading order.** This amends [ADR-153] decision 5.
   - `SchemaForm` moves focus to the first refused control in reading order, and React Hook Form
     moves none. A field marks each of its focusable inputs invalid while it is refused, which is
     what `SchemaForm` looks for.
   - A group's own refusal, such as a window's, describes each of the group's inputs and marks
     them invalid, so focus lands on the first of them with the reason.
   - Once the form was first sent, a change to either end of a window checks the window again.
   - `useSchemaForm` counts the form's resets, and `SchemaForm` hands the count to its fields: a
     time field starts its picker afresh on each, and moves focus that was in it to the new hours
     box.
8. **A table's default cell writes text only.** Any other value, a number included, fails the
   table, naming the column, until the column writes it in a cell of its own, through
   `@dispatchflow/formatters` or as plain digits for a row number. This extends [ADR-153]
   decision 9.
9. **A screen sends the newest version it knows**: the `ETag` each save returns, or the one its
   view hands it once read again. The example form does both.
10. **The web app's tests run in New York's zone**, as the formatters' do: far from Israel and from
    the UTC that CI runs in, so a date read in the computer's zone fails everywhere.

## Consequences

- **Easy:**
  - A list screen loads with `answerOf` and shows its failure in `ProblemMessage` with
    `operation="read"`, with nothing of its own, and its refusal shows at once.
  - Every call reports a lost connection, or an answer that broke off, the same way once the web
    app's client passes `fetchFromCore`.
  - #203 and #212 build an optional service time from the shared blocks.
- **Hard:**
  - A `send` that fetches without `fetchFromCore` reports a lost connection as a raw `TypeError`,
    which fails loudly rather than as "unreachable". The test helper and the web app's client both
    use it.
  - Every caller of `ProblemMessage` says whether its call read or wrote.
  - A table column of anything but text needs a cell of its own, a row number included.
  - A time field needs the `SchemaForm` around it to learn of a reset, and only a reset through
    `useSchemaForm`'s `reset` or `resetField` is counted.
- **Cost:**
  - Zod parses objects without its compiled fast path. Forms parse once per save, so nothing a
    user can notice.
  - `fetchFromCore` holds each answer's body twice while it is read. The API's answers are small.
- **Risk:**
  - jsdom keeps an opened calendar hidden, so moving between its days by keyboard is checked in a
    browser and not by a test.

## Alternatives considered

- **Setting `jitless` in the web app's `main.tsx`.** Imports are evaluated before the module's
  body, and the package builds its schemas as it is imported, so the probe would run first.
- **Classifying the error around `send`, as before.** A screen's own bug would keep showing as a
  lost connection.
- **Leaving the body's read to the caller.** openapi-fetch reads it after `fetch` resolves, where a
  broken-off answer's `TypeError` can no longer be told from a bug.
- **One wording for every unexpected failure.** A failed read would keep telling the user that a
  change may be stored.
- **Notices that never hold an action, with the action on the page.** It works, but every screen
  would place and announce its own "add another".
- **Time ranges in Hebrew reading order.** 08:00 would sit on the right in Hebrew, unlike the time
  field's boxes and a clock, and a table's text cell would have to leave ranges out of its
  `<bdi>`.
- **Refusing `HH:mm:ss` in a form's time check.** A record read from the API may carry seconds, so
  the form would refuse a time the user never typed. A window's ends are compared as times instead.
- **Registering a window as a field of its own, so React Hook Form focuses it.** React Hook Form
  would then read an error on either end as the window's, and focus the wrong box.
- **A default cell that writes whole numbers too.** Whether a column failed would depend on its
  data: a weight column would pass on whole test weights and fail in production on 12.5 kg.
- **Reading a field turning untouched, or `submitCount` going back to zero, as a reset.** Both
  are React Hook Form's own signals, but neither shows a reset of a field never left that runs
  during the save itself, as a screen's `onSaved` does.

## Revisit when

- The phone app makes its first call: `fetchFromCore`, `answerOf` and `RequestError` move into a
  shared package with the refusal reader, as [ADR-153] foresees.
- Zod reads `jitless` at parse time, or stops probing for `new Function`.
- Hebrew-speaking users read time ranges right to left in usability testing.
- A notice needs more than one action.
- End-to-end browser tests arrive: moving between a calendar's days by keyboard joins them.

[ADR-069]: ADR-069-sign-in-and-transport-security.md
[ADR-076]: ADR-076-admin-and-dispatcher-data-stays-in-memory-on-devices.md
[ADR-088]: ADR-088-israel-local-time-for-every-date-and-schedule.md
[ADR-153]: ADR-153-the-web-apps-shared-form-refusal-and-table-pieces.md
[BR-124]: ../requirements/requirements.md#orders-records-and-history
[NFR-46]: ../requirements/requirements.md#nfr-46
[NFR-68]: ../requirements/requirements.md#nfr-68

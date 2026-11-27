# ADR-154: The audit of the web app's shared pieces amends how a call fails, how a saved notice closes and how a time range reads

- **Status:** Accepted
- **Date:** 2026-11-27

## Context

[ADR-153] settled #173's shared pieces, and they merged on 27 November 2026. An audit after the
merge ran the production build in a browser under [ADR-069]'s Content Security Policy, in both
languages and by keyboard, and had the code and the tests reviewed afresh. What it found shapes
these decisions:

- **A policy violation on every page with a form.** Zod 4 builds an object schema by probing
  `new Function`, to see whether it may compile a faster parser. The policy refuses it, and the
  browser reports the violation although Zod catches the refusal. Zod reads its `jitless` setting
  when a schema is built, not when it parses, and `@dispatchflow/schemas` builds its schemas as it
  is imported.
- **A bug shown as a lost connection.** The save turned any `TypeError` from a screen's `send`
  into "the server could not be reached", a missing record's ID included. Only `fetch` itself can
  tell that no answer arrived.
- **Reads had no shared way to fail.** Only the versioned save turned an answer into a
  `RequestError`, and `ProblemMessage` told every unexpected failure that the change may have been
  saved, which is false after a list failed to load (#219, #233, #237).
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
- **A table cell that wrote any value.** The default cell wrote `12.5` with no unit and a date in
  the device's zone ([NFR-68], [ADR-088]).
- **An example that kept its first version.** The example form sent the `ETag` it opened with on
  every save, so a form copied from it would fail its second save as stale.

## Decision

Decided on 27 November 2026. This amends [ADR-153] decisions 4, 5 and 10, and extends its
decisions 2 and 9.

1. **Zod builds every schema without compiling code.** `@dispatchflow/schemas` takes `z` from its
   `src/zod.ts`, which sets `jitless` before any schema is built. ESLint refuses a value import of
   `zod` anywhere else in the package, and a test watches `Function` while the package loads and
   parses.
2. **Only `fetch` decides that a request got no answer.** `lib/coreCalls.ts`'s `fetchFromCore`
   turns fetch's own rejection into a `RequestError` whose failure is `network`, and #148's client
   passes it as openapi-fetch's `fetch`. Anything else a `send` throws, a `TypeError` included, is
   a bug and fails as itself. This replaces [ADR-153] decision 4's "only a `TypeError` from `send`".
3. **Every call's answer is read one way, a read's as well as a write's.** `answerOf` gives a
   success's data and `ETag`, or throws how the call failed. `ProblemMessage` takes the call's
   `operation`:
   - a failed read says that something went wrong, with its reference, and offers Try again;
   - a write keeps [ADR-153]'s wording, and its Reload after an unexpected failure;
   - `SchemaForm` passes `write`.
4. **A saved notice that offers an action stays until it is closed.** `useNotifySaved()` replaces
   `notifySaved`, amending [ADR-153] decision 10.
   - A plain notice closes by itself after Mantine's four seconds, with no close button.
   - A notice with an action has no time limit, and a close button named in the interface
     language. Pressing the action closes it too.
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
     moves none.
   - A group's own refusal, such as a window's, describes each of the group's inputs and marks
     them invalid, so focus lands on the first of them with the reason.
   - Once the form was first sent, a change to either end of a window checks the window again.
8. **A table's default cell writes text and whole numbers only.** Any other value fails the
   table, naming the column, until the column writes it through `@dispatchflow/formatters`. This
   extends [ADR-153] decision 9.
9. **A screen keeps the version each save returns**, from the answer's `ETag` or by reading its
   view again, so its next save sends it. The example form does.
10. **The web app's tests run in New York's zone**, as the formatters' do: far from Israel and from
    the UTC that CI runs in, so a date read in the computer's zone fails everywhere.

## Consequences

- **Easy:**
  - A list screen loads with `answerOf` and shows its failure in `ProblemMessage` with
    `operation="read"`, with nothing of its own.
  - A lost connection reads the same from every call once #148's client passes `fetchFromCore`.
  - #203 and #212 build an optional service time from the shared blocks.
- **Hard:**
  - A `send` that fetches without `fetchFromCore` reports a lost connection as a raw `TypeError`,
    which fails loudly rather than as "unreachable". The test helper and #148's client both use it.
  - Every caller of `ProblemMessage` says whether its call read or wrote.
  - A table column of numbers with decimals, dates or other values needs a cell of its own.
- **Cost:**
  - Zod parses objects without its compiled fast path. Forms parse once per save, so nothing a
    user can notice.
- **Risk:**
  - jsdom keeps an opened calendar hidden, so moving between its days by keyboard is checked in a
    browser and not by a test.

## Alternatives considered

- **Setting `jitless` in the web app's `main.tsx`.** Imports are evaluated before the module's
  body, and the package builds its schemas as it is imported, so the probe would run first.
- **Classifying the error around `send`, as before.** A screen's own bug would keep showing as a
  lost connection.
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
- **A default cell that formats any value.** It cannot know a number's unit, nor whether a date is
  Israel-local or an instant.

## Revisit when

- #148's client lands: it passes `fetchFromCore` as openapi-fetch's `fetch`.
- Zod reads `jitless` at parse time, or stops probing for `new Function`.
- Hebrew-speaking users read time ranges right to left in usability testing.
- A notice needs more than one action.
- End-to-end browser tests arrive: moving between a calendar's days by keyboard joins them.

[ADR-069]: ADR-069-sign-in-and-transport-security.md
[ADR-088]: ADR-088-israel-local-time-for-every-date-and-schedule.md
[ADR-153]: ADR-153-the-web-apps-shared-form-refusal-and-table-pieces.md
[BR-124]: ../requirements/requirements.md#orders-records-and-history
[NFR-46]: ../requirements/requirements.md#nfr-46
[NFR-68]: ../requirements/requirements.md#nfr-68

# ADR-153: The web app builds every form, refusal and row list from one set of shared pieces, and its schemas name codes, never messages

- **Status:** Accepted
- **Date:** 2026-11-27

## Context

#173 builds the pieces every Alpha form and list repeats: fields, the error summary, the versioned
save, dates and quantities, and a table. Teammates' screens wait on it: #203, #207, #212, #217,
#218, #219, #224, #225, #226, #233 and #240. Several of them say "use #173's fields, don't make your
own". What shaped the design:

- **The refusals.** The core answers every refusal as the contract's `Problem` ([ADR-043]), with a
  `code`, its `params`, a `traceId` and, on a 400, `errors` whose items name a field by an RFC 6901
  pointer into the body. A 422's rules name no field.
  - `STALE_VERSION` answers 412 for a stale `If-Match` and 409 for a version sent in the body
    ([ADR-140] decision 7).
  - `TRY_AGAIN_LATER` answers 503 with `Retry-After: 2`, an `If-Match` save included ([ADR-121]
    decision 4).
  - `UNEXPECTED_ERROR` means a change may be stored.
  - A screen shows text and a next step, never a code ([NFR-69], [NFR-143], [NFR-144]).
- **The versions.** A read carries `ETag`; a write sends it back as `If-Match` ([ADR-057], [BR-76]).
  The first change of a view with no version yet sends `If-None-Match: *` ([ADR-121] P31, as
  [ADR-126] amends it).
- **No client yet.** The contract declares no operation, and `@dispatchflow/api-client`, which
  generates the `Problem` type with openapi-fetch, is still #148's.
- **The packages.** [TS §10] decides React Hook Form with Zod 4, `@mantine/dates`,
  `@mantine/notifications`, TanStack Table, libphonenumber-js and `Intl` with date-fns, all for the
  Alpha. [TS §12] decides `@dispatchflow/schemas`, `@dispatchflow/formatters` and the error texts
  in `@dispatchflow/i18n`. Zod must be 4.6.6 or later ([ADR-095]).
  - TS §10 names neither the resolver that ties Zod to React Hook Form nor a keyboard-test
    library.
  - Its dates row turns Day.js down for our own dates, yet `@mantine/dates` 9.8.2 requires Day.js
    as a peer.
- **The page's limits.** No component may render a `<style>` element ([ADR-143]), dates and times
  read as Israel's in DD/MM/YYYY on a 24-hour clock in both languages ([ADR-088], [NFR-68]), and
  mixed-direction text sits in `<bdi>` ([NFR-136]).
- **A browser's own time box won't do.** Chrome on an English Windows shows `9:30 AM`.

## Decision

Decided on 27 November 2026. This extends [ADR-043], [ADR-057] and [ADR-143].

1. **The pieces live where their users can reach them.**
   - `@dispatchflow/formatters` shows instants and Israel-local dates and times, a window's range,
     an arrival past the delivery date with its date, and quantities with their unit.
   - `@dispatchflow/schemas` holds the building blocks of every form's schema, and each form's
     schema beside them.
   - `@dispatchflow/i18n` gains `problems`, one table of every refusal code's text and next step
     for both apps, and `common`, the components' strings. An area adds its codes to `problems`.
   - In the web app:
     - `src/lib/` holds the refusal reader and the versioned save;
     - `src/components/` holds `ProblemMessage`, `DataTable` and `notifySaved`;
     - `src/components/form/` holds the form and its save button, with the fields in `fields/`.
2. **A schema names its refusal with the server's code, never a message.** A check raises an issue
   whose `params.code` is the code (`REQUIRED`, `INVALID_TIME_WINDOW`, `INVALID_PHONE_NUMBER`, …),
   and the form's error map words it from `problems`.
   - A message set in a schema would beat that map and reach the screen untranslated.
   - The app's own refusals and the server's therefore read the same.
   - A code with no entry shows the general text, and the `traceId` when the core gave one.
3. **The web app types the `Problem` by hand until #148 generates it.** `lib/problems.ts` checks an
   answer's body with a guard, which stays after #148: a proxy's 502 page is no problem.
   `problems.contract.test.ts` pins the hand-written types to the contract, and #148 swaps in the
   generated type and deletes that test.
4. **A versioned save sends the `ETag` its view was read with, exactly as it came.** `null`, what
   `headers.get('ETag')` gives for a view with no version, sends `If-None-Match: *`, so a forgotten
   `ETag` fails as `STALE_VERSION` rather than overwriting.
   - The save returns the new `ETag` for the next write.
   - It never sends a write twice on its own, and runs whether or not the browser thinks it is
     online, so a lost connection fails at once.
   - A failure is classified by its code before its status: stale, busy, refused, unexpected or
     unreachable.
   - The save takes a `send` shaped like openapi-fetch's answer, so the generated client's call
     fits it.
5. **A refusal shows on the field it names, and the rest in the summary.**
   - A 400 item lands on the field its pointer names, when the form's schema has it.
   - A 422's code lands on the field the form's `fieldFor` maps it to.
   - What lands nowhere, a header or the whole request, goes into `ProblemMessage` at the top,
     which then takes focus.
   - The input always stays. Reload, for a stale record, is the screen's own action; Try again
     resends the same values.
   - No field changes its value as the user leaves it: that re-runs the field's check and wipes
     the server's refusal. The phone field writes a number out for display only.
6. **The time of day is Mantine's 24-hour `TimePicker`**, with a box for the hours and one for the
   minutes. Each box is named after the field and tied to its error, and both run left to right in
   Hebrew too. The date is `DatePickerInput`, shown as DD/MM/YYYY, holding `YYYY-MM-DD`, with
   Israel's week: Sunday first, and Friday and Saturday the weekend.
7. **Day.js is only `@mantine/dates`' peer.** It is declared exactly, `src/DatesLanguage.tsx` loads
   its Hebrew names, and ESLint refuses an import of it anywhere else. Our own dates stay on `Intl`
   and, from their first arithmetic, date-fns, as [TS §10] decides. date-fns is not added before
   that.
8. **These dependencies come in, pinned exactly:**
   - react-hook-form 7.92.0, zod 4.6.7, libphonenumber-js 1.13.17 (its `/min` metadata), and
     @tanstack/react-table 9.3.1 (core features only, its skills refused);
   - @mantine/notifications 9.8.2, @mantine/dates 9.8.2 and dayjs 1.11.23;
   - two that [TS §10] doesn't name:
     - **@hookform/resolvers 7.0.9.** It replaces a hand-written resolver, which would own the
       path mapping, nested errors and types of every form in step with two libraries' majors. It
       comes from the React Hook Form organisation, with three maintainers and a release on
       24 November 2026. It is MIT, 0.6 kB, and every peer but Zod and React Hook Form is
       optional.
     - **@testing-library/user-event 14.6.13**, a development dependency. It replaces `fireEvent`,
       which moves no focus on Tab and types no key sequence, so keyboard tests would not match a
       browser. It comes from the Testing Library organisation, released 18 November 2026 with
       provenance. It is MIT.
9. **Lists of rows use `DataTable`:** a real `<table>` with its caption and column headers, laid
   out in the language's direction. A column without its own cell shows its text in `<bdi>`.
10. **A save's success shows through `notifySaved`.** Mantine's store is the page's one store, and
    the tests empty it after each test.

## Consequences

- **Easy:**
  - A form is its schema, its fields, `SchemaForm` and one save.
  - Every refusal, the app's own or the server's, reads the same in both languages.
  - A screen with a map, a button or table rows uses the same save and the same message without a
    form.
- **Hard:**
  - Every new refusal code needs its entry in `problems` in both languages, or it shows the
    general text.
  - The `Problem` type is hand-written until #148.
  - jsdom runs no CSS transition, so the contents of a select's list and of the calendar stay out
    of the accessibility tree in tests, and are queried by their text and their labels.
- **Cost:**
  - The providers at the app's root (the date pickers' and the notifications') take the first load
    from 152.5 kB to 172.5 kB gzipped, and Vite now warns that the one script passes 500 kB
    minified.
  - Splitting it waits for [TS §10]'s bundle visualizer, which is decided for January.
- **Risk:**
  - TanStack Table 9 is new, and most examples online are v8's.
  - React Hook Form and its resolvers publish without provenance, so their exact pins and pnpm's
    one-day release age stand in.

## Alternatives considered

- **A typed `HH:mm` box for the time.** No dependency today, but we would write and test the
  parsing ourselves, and Day.js comes with the date picker anyway. Mantine's picker is the default
  of the kit already chosen ([TS §1] rule 3).
- **The browser's `<input type="time">`.** It shows a 12-hour clock in English on many systems
  (NFR-68).
- **A hand-written Zod resolver.** It is small for flat forms but grows with nested fields, arrays
  and the libraries' majors, in code every form shares.
- **Messages in the schemas.** They would beat the app's error map, reach the screen in one
  language, and could show a bare code.
- **Each area's own namespace for its refusals.** `ProblemMessage` would need to know where to
  look for each code; one table needs no such lookup.
- **Building #148's client first.** No hand-written type would ever exist, but #173 would wait on
  #148 and on its decision about committing generated files, and so would the teammates.
- **Formatting the phone number into the form's value.** Leaving the field would re-run its check
  and wipe the server's refusal.
- **date-fns now.** Nothing here does date arithmetic yet.
- **`fireEvent` for the keyboard tests.** It cannot show a keyboard user's path through a form.

## Revisit when

- #148 generates `Problem`: swap it in and delete `problems.contract.test.ts`.
- A screen needs Day.js for its own arithmetic or display.
- React Hook Form takes a Standard Schema directly, which drops the resolver.
- Mantine drops its Day.js peer.
- A Mantine piece we use renders a `<style>` element ([ADR-143]).
- The bundle visualizer shows the first load over [NFR-58]'s budget.

[ADR-043]: ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-057]: ADR-057-concurrent-changes-to-a-plan-use-optimistic-locking.md
[ADR-088]: ADR-088-israel-local-time-for-every-date-and-schedule.md
[ADR-095]: ADR-095-accept-the-zod-and-deepmerge-advisories-until-2027-01-31.md
[ADR-121]: ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[ADR-126]: ADR-126-jpa-replaces-jooq-and-the-domain-carries-its-mapping.md
[ADR-140]: ADR-140-the-app-wide-web-package-sits-above-the-modules.md
[ADR-143]: ADR-143-the-web-app-shell-declares-routes-per-area-opens-in-english-and-renders-no-style-element.md
[TS §1]: ../architecture/tech_stack.md#1-what-decides-close-calls
[TS §10]: ../architecture/tech_stack.md#10-web-app
[TS §12]: ../architecture/tech_stack.md#12-shared-typescript-packages
[BR-76]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[NFR-58]: ../requirements/requirements.md#nfr-58
[NFR-68]: ../requirements/requirements.md#nfr-68
[NFR-69]: ../requirements/requirements.md#nfr-69
[NFR-136]: ../requirements/requirements.md#nfr-136
[NFR-143]: ../requirements/requirements.md#nfr-143
[NFR-144]: ../requirements/requirements.md#nfr-144

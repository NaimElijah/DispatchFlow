# ADR-155: The client API keeps instants and local times apart, and sends a list that may grow as a string

- **Status:** Accepted
- **Date:** 2026-11-28

## Context

#149 writes the client API's first operations: open a date's plan, request its solve, and read a
date's plans, a plan, a solve's progress and a route's line. Their bodies carry two things the
contract had no rule for yet.
#184's pull request carries the two writes, which carry no time; the reads, which do, follow with
#214.

- **Times a solve works out.**
  - Planning holds a time of a plan's day as elapsed whole seconds since the delivery date's
    midnight in Israel ([ADR-088]). That is a `SolveTime`, with no upper bound because a route
    that breaks a time rule may run past the date's end ([ADR-112]), or a `DaySeconds` for a limit
    that lies in the day, such as a window's end.
  - The optimization API carries those seconds as they are, because the core and the optimizer
    share the date (#156).
  - The client API's readers are the web app, and later the phone app. They show times on a 24-hour
    clock ([NFR-68]) in `Asia/Jerusalem` ([ADR-088]), formatted with `Intl`. #233's stop list and
    #188's map layer compare a planned arrival with the plan's delivery date, and #233's test
    already reads an RFC 3339 instant.
  - Elapsed seconds make the conversion exact: the instant of the date's midnight plus the seconds.
    That holds on the 23- and 25-hour days of a clock change too, as `DaySeconds.toInstant()`
    already does.
  - [ADR-088] turned down "UTC everywhere, converted only at the edges" for the domain and storage.
    The core still keeps local dates and elapsed seconds. The question here is only the wire.
- **Times a person types.**
  - What a person enters is a wall-clock time on a date. The domain builds it from the date and a
    local time of day (`TimeWindow.on`, `Shift`). `IsraelDateTime` refuses a time in the hour the
    clock skips, and reads one in the hour it repeats as its first occurrence ([BR-111]).
  - The database keeps instants in `timestamptz` and local values, with no offset, apart
    ([ADR-088]). The web app's time picker holds `HH:mm` ([ADR-153]).
  - A problem's `params` write an instant as `ISO_INSTANT`, in UTC with `Z`, and a local date, time
    or date-time with no offset (`ErrorParams`).
- **Lists in responses.** `contracts/CLAUDE.md` makes a new enum value in a response a breaking
  change, except in `Problem.code`. Six of Planning's lists reach a response:
  - Four are sealed types whose variants carry fields of their own:
    - the plan's state, `Plan.Status`, seven records, its job among them while it is solving;
    - a solve's phase, `SolveJob.Phase`, five, each with its times;
    - a drop's reason, `DropReason`, [FR-223]'s full list of eleven, each with its fact;
    - a pin's target, `PinTarget`, a vehicle or a driver.

    `DropReason` mirrors what the optimizer decides, and it has grown once already: [ADR-100] added
    *no vehicle available*.
  - Two are plain values, and one of them has grown too:
    - `SolveFailureReason` gained three values in [ADR-121], and the core decides some of its
      values, such as a date that passed, as well as the optimizer;
    - `ExclusionCause` mirrors the optimizer's own exclusions.
- **The generators.** On 28 November 2026, both generators ran on this contract.
  - openapi-generator 7.27.0, with [ADR-140]'s options, turns each `oneOf` into an interface that
    carries Jackson's `@JsonTypeInfo`, with one record per `kind`. The output compiles against the
    core's classpath.
  - openapi-typescript 7.13.0 turns each one into a union that narrows on `kind`. `tsc` 6.0.3
    refuses a `switch` that misses a kind and ends in a `never` default.

## Decision

Decided on 28 November 2026.

1. **The wire keeps instants and local times apart, as the database does ([ADR-088]).**
   - **An instant is an RFC 3339 `date-time`.** That is a moment, the contract's `Instant`, such as
     when a solve was requested, and a time a solve works out for a plan's day, its `PlanTime`.
     - A `PlanTime` is a `SolveTime` or a `DaySeconds`. The core works it out from the instant of
       the delivery date's midnight in `Asia/Jerusalem` plus the elapsed seconds, so it may fall on
       a later day. A drop's fact is a `PlanTime` even where it copies a time a person typed, such
       as a window's end, because the solve compared it in the day's seconds.
     - The core writes both in UTC with `Z`: a `PlanTime` in whole seconds, and an `Instant` with
       no fraction of a second or exactly three digits of one.
     - A client accepts any offset, shows the time in `Asia/Jerusalem`, and shows the date beside a
       time that isn't on the plan's delivery date.
   - **A time a person enters for a date is that date and a local `HH:mm`,** in requests and
     responses alike, as the domain builds it. An order's window and a shift on a date's roster
     are examples. The core resolves it in Israel under [BR-111], so a client never converts one.
   - A time of day that holds on every date and belongs to no instant, such as a depot's hours or a
     shift's usual hours, is a local `HH:mm` too.
   - The first operation that takes a local time adds one shared schema for it, a string with
     the pattern `^([01][0-9]|2[0-3]):[0-5][0-9]$`, never `format: time`, which is RFC 3339's time
     with seconds and an offset.
   - A date is a `date`, Israel's calendar day.
   - A problem's `params` keep the forms `Problem.params` names.
   - The optimization API keeps its seconds; only the client API converts.
2. **Whether a list is a `oneOf` or a string depends on whether its values carry fields.**
   - A list whose values carry fields of their own is a `oneOf` with a `kind` discriminator, and it
     is closed for v1: adding a kind is a breaking change, so it waits for a v2. With #214's reads, that
     covers
     `PlanStatus`, `SolvePhase`, `DropReason` and `PinTarget`.
   - A list of plain values that the optimizer decides, or that has grown, is a string matching the
     code pattern, with today's values listed in its description, as `Problem.code`'s are. A client
     shows a generic text for a value it doesn't know. With #214's reads, that covers
     `SolveFailed.failureReason`
     and `ExcludedVehicle.cause`.
   - Any other plain list is an `enum`, closed for v1, as the places a problem names a parameter in
     are.
   - The contract's kind names are authoritative. Each was chosen as `DomainError.codeOf` of its
     record, the rule a problem's `params` name a variant by, so a variant has one name in the
     client API. The core's mapping names each kind explicitly, so renaming a Java record changes no
     body, and #214's test holds each kind to its record's `codeOf`, so `params` don't drift
     either. #156 gives the optimization API's drop reasons the same names.

This extends [ADR-140]'s conventions for every client API operation, and [ADR-088] with the wire
form of the times it governs.

## Consequences

- **Easy:**
  - A client formats an instant with `Intl` alone, clock changes included, and shows a time a
    person entered as it came.
  - [BR-111] stays in one place, the core. A form sends what its time picker holds, and a record
    reads and saves its entered times in one form.
  - A new failure reason or exclusion cause ships without a v2.
  - Generated clients get a discriminated union for each closed list, so a screen whose `switch`
    ends in a `never` default fails to compile when it misses a plan state, phase or drop reason.
- **Hard:**
  - #214's web adapter converts each `SolveTime` and `DaySeconds` from its own date, through
    `DaySeconds.toInstant()` and a matching conversion on `SolveTime` in `planning.domain`, tested
    on both clock-change days and past midnight. It truncates every `Instant` to the millisecond,
    and its test checks the `Z`, an `Instant`'s fraction and a `PlanTime`'s whole seconds.
  - A client handles two kinds of time, which each field's schema names: an instant it formats,
    and a local time it shows as it came.
  - A client compares two instants as instants, never as strings, because their fractions of a
    second may be written to different lengths.
  - The compiler can't check that a screen handles every failure reason or exclusion cause. The
    generic text covers the rest.
  - A new drop reason first changes [FR-223], which names the full list, and then waits for a v2 of
    the client API.
  - A time a person enters that crosses midnight can't be a local `HH:mm` on its date. [FR-114],
    which would need one, isn't built.
- **Cost:** a `date-time` is about 20 bytes, where the seconds are about 5. That is a few kilobytes
  on a 30-route plan.
- **Risk:**
  - A client that reads a `date-time` as a local time, ignoring its offset, shows times two or
    three hours off. The shared formatters (#173) take a `Date`, which reads the offset from the
    text, and #233's test pins it.
  - A `switch` with no `never` default compiles with a kind missing. ESLint's typed
    `switch-exhaustiveness-check` would refuse it, but the shared ESLint configuration runs no typed
    rule yet. #148 turns it on with the generated client; until then, review checks for the
    `never` default.
  - JavaScript's date format reads no fraction of a second or exactly three digits, which is why
    an `Instant` carries one of those. The database and the clock carry microseconds, so an
    `Instant` read from either is truncated before it is written.

## Alternatives considered

- **Seconds since the date's midnight, with the plan's delivery date.** This mirrors the domain and
  the optimization API, but every client would repeat the conversion that a clock change makes easy
  to get wrong. A bare number also reads wrong in logs and tools.
- **A local date-time with no offset, `2026-11-29T08:05:00`, for a time a solve works out.** It is
  ambiguous in the repeated hour of the autumn clock change, and it isn't RFC 3339.
- **Israel's offset, `+02:00` or `+03:00`, instead of UTC.** It reads better, but one instant would
  then be written two ways, by season and by writer, which invites a comparison of strings; a
  problem's `params` already write instants in UTC.
- **Instants both ways, a typed time included.** Every time would have one form, but the browser or
  the phone would turn 02:30 on the night the clock skips it into some instant, which the core
  could no longer refuse under [BR-111], and both apps would repeat the clock-change rules.
- **Local times in requests, instants in responses.** [BR-111] would stay in the core, but an edit
  form would convert each field back before saving, and a record would read in one form and save in
  another.
- **An enum for every list.** Every case would be checked at compile time, but each new failure
  reason would need a v2.
- **A string for every list.** A string can't carry a variant's own fields, such as a drop's
  capacity figures or a phase's times.
- **A `oneOf` that tolerates an unknown `kind`, so a new drop reason needs no v2.**
  - Each generated client would need a catch-all variant, Jackson a default type and TypeScript a
    wider union, and screens would lose the exhaustive `switch`.
  - [FR-223] names its list as the full one.
  - The revisit trigger below reopens this.

## Revisit when

- A v2 of the client API is cut. A closed list may gain kinds then.
- A drop reason, plan state or phase must be added while v1 is still served. Then that `oneOf`
  becomes one that tolerates an unknown `kind`.
- A client must work out times without the server, as an offline phone app would.
- A person must enter a time that crosses midnight ([FR-114]).
- A string list turns out to be closed in practice.

[ADR-088]: ADR-088-israel-local-time-for-every-date-and-schedule.md
[ADR-100]: ADR-100-k-covers-door-windows-outside-a-shift-and-no-vehicle-is-a-drop-reason.md
[ADR-112]: ADR-112-a-re-optimization-keeps-every-order-and-route-a-solve-that-breaks-a-lock-fails-and-publishing-clears-locks.md
[ADR-121]: ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[ADR-140]: ADR-140-the-app-wide-web-package-sits-above-the-modules.md
[ADR-153]: ADR-153-the-web-apps-shared-form-refusal-and-table-pieces.md
[BR-111]: ../requirements/requirements.md#time-and-limits
[FR-114]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-223]: ../requirements/requirements.md#daily-planning
[NFR-68]: ../requirements/requirements.md#nfr-68

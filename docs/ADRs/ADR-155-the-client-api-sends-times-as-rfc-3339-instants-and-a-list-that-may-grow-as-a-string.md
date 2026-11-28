# ADR-155: The client API sends times as RFC 3339 instants, and a list that may grow as a string

- **Status:** Accepted
- **Date:** 2026-11-28

## Context

#149 writes the client API's first operations: open a date's plan, request its solve, and read a
date's plans, a plan, a solve's progress and a route's line. Their bodies carry two things the
contract had no rule for yet.

- **Times.**
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
  - A problem's `params` already write an instant as `ISO_INSTANT`, in UTC with `Z`
    (`ErrorParams`). They write a local date or time of day without an offset.
- **Lists in responses.** `contracts/CLAUDE.md` makes a new enum value in a response a breaking
  change, except in `Problem.code`. Six of Planning's lists reach a response:
  - Four are sealed types whose variants carry fields of their own:
    - the plan's state, `Plan.Status`, seven records, its job among them while it is solving;
    - a solve's phase, `SolveJob.Phase`, five, each with its times;
    - a drop's reason, `DropReason`, [FR-223]'s full list of eleven, each with its fact;
    - a pin's target, `PinTarget`, a vehicle or a driver.

    `DropReason` mirrors what the optimizer decides, and it has grown once already: [ADR-100] added
    *no vehicle available*.
  - Two are plain values that mirror what the optimizer decides, and one of them has grown too:
    - `SolveFailureReason` gained three values in [ADR-121];
    - `ExclusionCause` mirrors the optimizer's own exclusions.
- **The generators.** On 28 November 2026, both generators ran on this contract.
  - openapi-generator 7.27.0, with [ADR-140]'s options, turns each `oneOf` into an interface that
    carries Jackson's `@JsonTypeInfo`, with one record per `kind`. The output compiles against the
    core's classpath.
  - openapi-typescript 7.13.0 turns each one into a union that narrows on `kind`. `tsc` 6.0.3
    refuses a `switch` that misses a kind and ends in a `never` default.

## Decision

Decided on 28 November 2026.

1. **In a body, every moment and every time of a plan's day is an RFC 3339 `date-time`.**
   - The contract's `PlanTime` is a time of a plan's day, a `SolveTime` or a `DaySeconds`. The core
     works it out from the instant of the delivery date's midnight in `Asia/Jerusalem` plus the
     elapsed seconds, so it may fall on a later day.
   - `Instant` is a moment, such as when a solve was requested.
   - The core writes both in UTC with `Z`. A client accepts any offset, shows the time in
     `Asia/Jerusalem`, and shows the date beside a time that isn't on the plan's delivery date.
   - A date stays a `date`, Israel's calendar day.
   - A time of day that holds on every date and belongs to no instant stays a local `HH:mm` or
     `HH:mm:ss` in Israel, as `@dispatchflow/formatters` reads it. A depot's hours and a shift's
     usual hours are examples.
   - A problem's `params` keep the forms `Problem.params` names.
   - The optimization API keeps its seconds; only the client API converts.
2. **Whether a list is a `oneOf` or a string depends on whether its values carry fields.**
   - A list whose values carry fields of their own is a `oneOf` with a `kind` discriminator, and it
     is closed for v1: adding a kind is a breaking change, so it waits for a v2. Today that covers
     `PlanStatus`, `SolvePhase`, `DropReason` and `PinTarget`.
   - A list of plain values that the optimizer decides, or that has grown, is a string matching the
     code pattern, with today's values listed in its description, as `Problem.code`'s are. A client
     shows a generic text for a value it doesn't know. Today that covers `SolveFailed.failureReason`
     and `ExcludedVehicle.cause`.
   - The contract's kind names are authoritative. `DomainError.codeOf`, the rule a problem's
     `params` name a variant by, is how each was chosen, so one variant has one name everywhere. The
     core's mapping names each kind explicitly, so renaming a Java record changes nothing on the
     wire.

This extends [ADR-140]'s conventions for every client API operation, and [ADR-088] with the wire
form of the times it governs.

## Consequences

- **Easy:**
  - A client formats times with `Intl` alone, with no conversion of its own, clock changes
    included.
  - A moment has one form in bodies and in a problem's `params`, RFC 3339 in UTC. A client compares
    two as instants, never as strings, since their fractions of a second may be written to
    different lengths.
  - A new failure reason or exclusion cause ships without a v2.
  - Generated clients get a discriminated union for each closed list, so a screen whose `switch`
    ends in a `never` default fails to compile when it misses a plan state, phase or drop reason.
- **Hard:**
  - #214's web adapter converts every `SolveTime` and `DaySeconds` with the plan's delivery date.
    It goes through `DaySeconds.toInstant()` and a matching conversion on `SolveTime` in
    `planning.domain`, tested on both clock-change days and past midnight.
  - The compiler can't check that a screen handles every failure reason or exclusion cause. The
    generic text covers the rest.
  - A new drop reason, as FR-223 allows, waits for a v2 of the client API.
- **Cost:** a `date-time` is about 20 bytes, where the seconds are about 5. That is a few kilobytes
  on a 30-route plan.
- **Risk:**
  - A client that reads a `date-time` as a local time, ignoring its offset, shows times two or
    three hours off. The shared formatters (#173) read the offset, and #233's test pins it.
  - A `switch` with no `never` default compiles with a kind missing. ESLint's
    `switch-exhaustiveness-check` closes that gap.

## Alternatives considered

- **Seconds since the date's midnight, with the plan's delivery date.** This mirrors the domain and
  the optimization API, but every client would repeat the conversion that a clock change makes easy
  to get wrong. A bare number also reads wrong in logs and tools.
- **A local date-time with no offset, `2026-11-29T08:05:00`.** It is ambiguous in the repeated hour
  of the autumn clock change, and it isn't RFC 3339.
- **Israel's offset, `+02:00` or `+03:00`, instead of UTC.** It reads better, but a problem's
  `params` already write UTC, and two forms of one moment invite a comparison of strings.
- **An enum for every list.** Every case would be checked at compile time, but each new failure
  reason would need a v2.
- **A string for every list.** A string can't carry a variant's own fields, such as a drop's
  capacity figures or a phase's times.
- **A `oneOf` that tolerates an unknown `kind`, so a new drop reason needs no v2.**
  - Each generated client would need a catch-all variant, Jackson a default type and TypeScript a
    wider union, and screens would lose the exhaustive `switch`.
  - FR-223 names its list as the full one.
  - The revisit trigger below reopens this.

## Revisit when

- A v2 of the client API is cut. A closed list may gain kinds then.
- A drop reason, plan state or phase must be added while v1 is still served. Then that `oneOf`
  becomes one that tolerates an unknown `kind`.
- A client must work out times without the server, as an offline phone app would.
- A string list turns out to be closed in practice.

[ADR-088]: ADR-088-israel-local-time-for-every-date-and-schedule.md
[ADR-100]: ADR-100-k-covers-door-windows-outside-a-shift-and-no-vehicle-is-a-drop-reason.md
[ADR-112]: ADR-112-a-re-optimization-keeps-every-order-and-route-a-solve-that-breaks-a-lock-fails-and-publishing-clears-locks.md
[ADR-121]: ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[ADR-140]: ADR-140-the-app-wide-web-package-sits-above-the-modules.md
[FR-223]: ../requirements/requirements.md#daily-planning
[NFR-68]: ../requirements/requirements.md#nfr-68

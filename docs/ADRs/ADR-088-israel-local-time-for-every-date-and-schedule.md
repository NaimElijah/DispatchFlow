# ADR-088: Every date, time window, shift and schedule is Israel local time, held as elapsed seconds in a solve

- **Status:** Accepted; amended by ADR-098
- **Date:** 2026-11-13

## Context

Every date, time window, shift and ETA is Israel local time, on clock-change days too ([BR-31]).
Israel changes its clocks, so a delivery date is 23, 24 or 25 hours long. A dispatcher enters a
window as a wall-clock time, such as 09:00. A travel duration is elapsed time. On a clock-change day
09:00 is 8 or 10 hours after the date's midnight, so wall-clock seconds and elapsed seconds differ
by an hour for part of the day ([PL §7.1]). A time in the hour the clock skips is refused, and a
time in the hour it repeats reads as its first occurrence ([BR-111]).

[BR-31] carried the marker "ADR pending: Israel local time". The decision touches the shared kernel,
the solve, the database, the schedules and both apps, so it needs one place where the rule is
written. On 2026-11-13 it was decided that the rule stands as [BR-31] and the design documents cited
below state it.

Four things make the rule hard to keep:

- The database session runs in UTC ([TS §6.3]).
- Devices have their own clocks, which differ from the server's ([BR-114]).
- Browsers format dates themselves.
- Time-zone rules can change, so a stale JDK gives wrong times ([TS §6.1]).

## Decision

- **One zone for the whole system: `Asia/Jerusalem`.** A person or a device has no zone of its own.
- **The shared kernel holds three values** ([DL §3.3]):
  - `DeliveryDate`, the calendar day a plan, its orders and its roster are for;
  - `IsraelDateTime`, a local time in `Asia/Jerusalem`. It refuses a time in the skipped hour and
    reads a repeated hour as its first occurrence ([BR-111]);
  - `DaySeconds`, elapsed seconds since the date's local midnight, within that day's 23, 24 or 25
    hours.
- **A solve works in `DaySeconds`.** The core converts every window, shift and depot hour with
  `ZonedDateTime` in `Asia/Jerusalem`: `s = Duration.between(midnight(date), instant).toSeconds()`
  ([PL §7.1]). The planning horizon is the date's length in `DaySeconds`. The travel-time buckets
  are stored as local times and factors, and the solve job converts them to `DaySeconds` for the
  date, so clock-change days come out right ([PL §14]).
- **The domain never reads the clock.** Use cases pass `now` and `today` in from the `Clock` port
  ([DL §10.1], [NFR-13], [NFR-175]).
- **The database keeps instants and local values apart.** `timestamptz` holds instants, and `date`
  and `time` hold Israel-local values. The session runs in UTC ([TS §6.3]).
- **Schedules run in the zone.** Spring `@Scheduled` uses cron with `Asia/Jerusalem` ([TS §6]). The
  job at 00:00 completes a published plan at the end of its date ([BR-102]).
- **The zone's rules come from the JDK's tzdata,** so the JDK is kept patched ([TS §6.1]). The
  server's clock follows AWS Time Sync through chrony, because the server's clock judges staleness
  and device clock offsets ([TS §15], [FR-74]).
- **The web app displays with `Intl` and `timeZone: 'Asia/Jerusalem'`,** and does arithmetic, such
  as the time left in a window, with date-fns 4 ([TS §10], [FR-154]). Dates show as DD/MM/YYYY and
  times on a 24-hour clock, in both interface languages ([NFR-68], [ADR-089]).
- **Tests cover clock-change days.** The core domain tests include Israel time on those days
  ([PL §24]).

## Consequences

- **Easy:** a solver and the validator work with integers and never see a daylight-saving rule. The
  core converts once, at the edge of a solve.
- **Easy:** a dispatcher, a driver and the server read the same date and the same 09:00.
- **Hard:** a time in the skipped hour is refused, and a time in the repeated hour reads as its
  first occurrence ([BR-111]). A dispatcher meets both only on two days a year.
- **Hard:** `DaySeconds` is not a wall-clock offset. Code that adds it to local midnight as if it
  were one is an hour wrong on a clock-change day.
- **Hard:** every conversion goes through the shared values, because no signature in the domain
  takes a raw `Instant` or number for a business concept ([DL §3.3]).
- **Cost:** the JDK must be patched when Israel's rules change, and the tests need clock-change
  fixtures.
- **Risk:** a server clock that drifts misjudges staleness and clock offsets ([FR-74], [BR-114]).
  chrony on AWS Time Sync is the control.

## Alternatives considered

- **UTC everywhere, converted only at the edges.** Rejected: windows and shifts are given as local
  times ([BR-31]), and the length of a date varies. Each consumer would then handle the 23- and
  25-hour days itself.
- **Wall-clock seconds since midnight.** Rejected: on a clock-change day it is wrong by an hour,
  because a travel duration is elapsed time ([PL §7.1]).
- **A time zone per user or per device.** Rejected: [BR-31] makes every date, window, shift and ETA
  Israel local time, and the company works in Israel ([ADR-024]).
- **Day.js for the web app's arithmetic.** Rejected: time zones come through plugins ([TS §10]).
- **The browser's `Temporal`.** Rejected for now: it is not yet in every browser the drivers use
  ([TS §10]).

## Revisit when

- A second company, or work outside Israel, appears ([ADR-024]'s trigger). Then a zone per company
  is needed.
- Israel changes its daylight-saving rules. Check the JDK's tzdata release before the change takes
  effect.
- `Temporal` is available in every browser the drivers use. Reconsider date-fns and `Intl` for
  arithmetic.
- A clock-change day falls inside the pilot and a window, an ETA or a plan on that day is an hour
  wrong. Add the failing case to the clock-change tests first.

[ADR-024]: ADR-024-one-delivery-company-no-multi-tenancy.md
[ADR-089]: ADR-089-interface-languages.md
[DL §10.1]: ../architecture/domain_layer/domain_layer_design.md#101-principles
[DL §3.3]: ../architecture/domain_layer/domain_layer_design.md#33-value-objects
[PL §14]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#14-travel-times-and-geodata
[PL §24]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#24-testing
[PL §7.1]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#71-time-model
[TS §10]: ../architecture/tech_stack.md#10-web-app
[TS §15]: ../architecture/tech_stack.md#15-infrastructure-and-operations
[TS §6]: ../architecture/tech_stack.md#6-core-service-java
[TS §6.1]: ../architecture/tech_stack.md#61-domain-conventions
[TS §6.3]: ../architecture/tech_stack.md#63-persistence
[BR-31]: ../requirements/requirements.md#time-and-limits
[BR-102]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-111]: ../requirements/requirements.md#time-and-limits
[BR-114]: ../requirements/requirements.md#driver-reports-and-outcomes
[FR-74]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-154]: ../requirements/requirements.md#publishing-and-the-phone-app
[NFR-13]: ../requirements/requirements.md#nfr-13
[NFR-68]: ../requirements/requirements.md#nfr-68
[NFR-175]: ../requirements/requirements.md#nfr-175

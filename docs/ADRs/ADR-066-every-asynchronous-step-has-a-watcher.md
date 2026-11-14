# ADR-066: Every asynchronous step that crosses a commit has a watcher

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

On 2026-11-14 the owner, Naim Elijah, decided for the team that the domain layer design (DL) is
settled, and that its watchers get their own ADR. No "ADR pending" marker covers this decision as a
whole. The requirements already fix three of its pieces: a failed solve returns the plan to `draft` or
`ready` ([BR-59]), a solve that a restart interrupts fails ([BR-74]), and so does a re-optimization
that a restart interrupts or finds queued ([BR-85]).

Under [ADR-063] a transaction changes one aggregate, so many things happen after a commit: a job ends,
an event reaches its listener, a message is sent, a geocoding lookup is answered. Each can be lost.
The process can stop between the commit and the next step, a listener can throw, a provider can never
answer. Nothing may stay stuck without someone, or something, noticing.

Two facts shape the answer. There is one core instance ([NFR-14]), so no distributed lock is needed.
And Spring Modulith 2.x leaves a failed event publication unretried unless the application is
configured to retry it ([PL §11.3][PL-113]).

## Decision

**Every step that crosses a commit has a named watcher,** and the watcher is in the design
([DL §12][DL-12]), not left to chance. A watcher is a scheduled job. It reads the clock and calls the
owner's method with `now`. The rule itself lives in the owner and is tested with a fixed time
([DL §10.1][DL-101]).

| Step | Expected outcome | Watcher | What it does |
|---|---|---|---|
| A queued or running `SolveJob` | Ends within its limit plus 30 s ([BR-32]); waits at most 5 minutes ([BR-112]) | The queue watchdog, every 15 s | `expireIfOverdue(now)` fails it, and `SolveFailed` returns the plan to *draft* or *ready* |
| A plan still unpublished at the midnight after its date | It is *abandoned*, with no solve job of it queued or running | The midnight job, at 00:00 ([BR-102]) | Fails every queued or running solve job of that date with the reason *its date passed* ([BR-128]), then calls `Plan.complete(today)` or `Plan.abandon(today)` ([ADR-053]) |
| The nightly retention run: the anonymizations, the deletions and the audit purge ([ADR-075]) | Completes each night | A Healthchecks.io ping at the end of each run, as for backups | A missed ping alerts the team |
| Any event listener | Completes | Spring Modulith's staleness monitor, a resubmission every minute, and republication at restart | Resubmits at most 5 times, then alerts the team with the listener's name |
| A route handed over or removed while the optimizer was down | Checked when it is back | A job every minute while routes show *not yet checked* ([FR-304], [FR-315], [NFR-42]) | Evaluates them and calls `Plan.recordRouteCheck` |
| A `PlanRevised` the driver must confirm | The driver taps "Seen" ([FR-97], [FR-250]) | `RouteRun`'s confirmed revision, on the live screen | The dispatcher sees who has not confirmed |
| A message | Accepted within 15 minutes of its trigger ([NFR-190]) | The sender, every 5 s | `dropStale` drops and counts it |
| A geocoding request | Answered | The geocoding retry, every 15 minutes for 24 hours ([FR-64]) | Leaves the customer flagged for a hand-placed location |
| A server restart | Nothing half-done survives silently | Startup recovery | Running jobs fail, queued daily solves stay, queued re-optimizations fail ([BR-74], [BR-85], [NFR-12]) |

Three rules go with the table:

- **A new asynchronous step comes with its watcher,** or the design says why it needs none.
- **A watcher's job is tested by fault injection:** make the step fail once and check that the
  watcher recovers it, and that a second delivery changes nothing more ([PL §24][PL-24]).
- **Jobs use Spring's `@Scheduled` in the `Asia/Jerusalem` zone,** on the one instance
  ([PL §11.4][PL-114]).

Related decisions: [ADR-051] fixes the 30-second margin and the worker model that the watchdog
enforces, [ADR-058] fixes what the re-check job does while the optimizer is down, and [ADR-064]
fixes the registry behind the listener watcher.

## Consequences

- **Easy:** a lost step has a known place where it ends: a failure state, a resubmission or a flag a
  person sees.
- **Easy:** each watcher is a plain method on its owner, called with `now`, so a test needs no clock
  and no scheduler.
- **Hard:** each new asynchronous step costs a watcher, a test and a row in this table.
- **Hard:** watchers and the steps they watch can race, for example a worker finishing as the
  watchdog fails its job. The loser finds the job ended (`JobAlreadyEnded`) and stops ([DL §11][DL-11]).
- **Cost:** several scheduled jobs, one resubmission job and one alert path for the team.
- **Risk:** the watchers run inside the one core process. If it is down, none runs. Startup recovery
  covers the restart, and the sources name no watcher for the watchers themselves.
- **Risk:** the listener watcher alerts the team on the sixth failure. If the team does not read the
  alert, the publication stays failed.

## Alternatives considered

- **No watchers: rely on the first attempt and each step's own retry.** Rejected: a failed
  publication is not retried unless configured, and a crash between commit and delivery would leave
  the step outstanding ([PL §11.3][PL-113]).
- **A message broker with dead-letter queues.** Rejected: one VM, and PostgreSQL already covers the
  solve queue, the message outbox and the event registry ([TS §23][TS-23]).
- **A distributed scheduler or lock.** Rejected for now: there is one instance ([NFR-14],
  [PL §11.4][PL-114]).

## Revisit when

- A step is found stuck in the pilot that no row of the table covers. Add its watcher, and amend this
  ADR if the rule above failed to prevent it.
- A second core instance is planned ([NFR-14] lifted). The jobs then need a lock, and republishing
  at startup is no longer safe by itself.
- A watcher's job is found dead while the process is up. The sources give no watcher for the
  watchers; add one.

[ADR-051]: ADR-051-one-solver-worker-per-lane-killed-30-seconds-past-its-limit.md
[ADR-053]: ADR-053-plan-lifecycle-publishing-stability-drop-reasons-and-abandoned-plans.md
[ADR-058]: ADR-058-routes-changed-while-the-optimizer-is-down-are-checked-when-it-returns.md
[ADR-063]: ADR-063-a-transaction-changes-one-aggregate-and-every-rule-has-one-owner.md
[ADR-064]: ADR-064-events-only-where-they-earn-their-place-through-the-modulith-registry.md
[BR-32]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-59]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-74]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-85]: ../requirements/requirements.md#re-optimization
[BR-102]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-112]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-128]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[DL-11]: ../architecture/domain_layer/domain_layer_design.md#11-concurrency
[DL-12]: ../architecture/domain_layer/domain_layer_design.md#12-who-notices-when-an-asynchronous-step-doesnt-happen
[DL-101]: ../architecture/domain_layer/domain_layer_design.md#101-principles
[FR-64]: ../requirements/requirements.md#geocoding
[FR-97]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-250]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-304]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-315]: ../requirements/requirements.md#customers-and-orders
[NFR-12]: ../requirements/requirements.md#nfr-12
[NFR-14]: ../requirements/requirements.md#nfr-14
[NFR-42]: ../requirements/requirements.md#nfr-42
[NFR-190]: ../requirements/requirements.md#nfr-190
[PL-24]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#24-testing
[PL-113]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#113-event-publication-making-retries-happen
[PL-114]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#114-scheduled-jobs-and-watchers
[TS-23]: ../architecture/tech_stack.md#23-alternatives-rejected-across-the-stack
[ADR-075]: ADR-075-retention-deletes-records-and-anonymizes-people-in-place.md

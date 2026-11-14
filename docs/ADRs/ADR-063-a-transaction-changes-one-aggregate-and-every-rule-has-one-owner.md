# ADR-063: A transaction changes one existing aggregate, and every business rule has exactly one owner

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

On 2026-11-14 the owner, Naim Elijah, decided for the team that the domain layer design (DL) is
settled, and that its transaction rule and its rule owners each get an ADR. No "ADR pending" marker
in requirements.md covers this decision. The implementation plan lists it among the decisions that
need an ADR before the code they govern merges ([PL §25][PL-25]), and [ADR-040] deferred it.

The forces are these:

- **Several writers share one aggregate.** `Plan` is changed by dispatchers and by five automatic
  writers: the solve-result listener, the listeners for cancelled and rescheduled orders, Live
  Operations' late-outcome call and the route re-check job ([DL §11][DL-11]). Two of them must never
  overwrite each other silently ([BR-76]).
- **Some rules cover a set of aggregates.** At least two active Admins ([BR-19]), at most 200
  uncancelled orders a date ([BR-101], [BR-113]), at most 5 queued jobs in a lane ([BR-112]), one
  plan per date ([BR-34]). No single aggregate holds those sets ([DL §5][DL-5]).
- **The domain is pure** ([ADR-040]). It reads no clock, calls no repository and imports no
  framework, so a rule that needs facts from outside gets them as parameters.
- **Offline phones.** Reports from 15 phones must not bump the version that protects dispatchers'
  edits ([DL §16][DL-16]). That drives where each kind of data lives, and it is settled in
  [ADR-065].

## Decision

**A command changes one existing aggregate** and saves it with a version check ([BR-76], [DL §6.1][DL-61]).
In the same transaction it may also:

1. **Create new aggregates of the same module that only this command can see.** A new aggregate has
   no other writer and no version that can conflict. If two dispatchers request a solve at the same
   moment, the second save fails its version check and the `SolveJob` it created rolls back with it.
2. **Write technical records:** the audit record ([NFR-23]), the idempotency record ([NFR-65]),
   Spring Modulith's event publications and outbox rows such as geocoding requests.

Nothing else. A change to a second existing aggregate follows by an event ([ADR-064]) or by a later
call, never in the same transaction.

**Outside calls come first** ([DL §6.3][DL-63]). A use case calls the optimizer, OSRM or the
geocoder before it opens its transaction, then loads, changes and saves the aggregate with the
version it read. If the version moved, the save fails with `STALE_VERSION` and only the call is
lost. No database transaction waits on HTTP.

**Every rule has one owner** ([DL §3.7][DL-37]), and DispatchFlow has no domain services:

- **A rule over one aggregate's own state goes on that aggregate,** with the facts it cannot see
  passed in as values. Publishing, accepting a proposal and handing over a route are `Plan`'s methods.
- **A rule that defines a value goes on that value.** `OrderStatus.derive`, `CustomerMatch`,
  `ArrivalProjection` and `OnTimeRate` are examples.
- **A rule over a small set that changes together goes on an aggregate that holds the set.** These
  are `FleetRegister`, `DayRoster` and `StopMessages`.
- **A rule over a set that does not fit one aggregate goes on a snapshot value,** read under a lock,
  that the one aggregate method able to break the rule requires as a parameter:

| Rule | Owner | Lock that builds the snapshot | Backstop in the database |
|---|---|---|---|
| At least two active Admins ([BR-19]) | `ActiveAdmins` | `FOR UPDATE` on the active Admin accounts | The lock itself |
| At most 200 orders a date ([BR-101], [BR-113]) | `DateCapacity` | Advisory lock on the date | The lock itself |
| At most 5 queued jobs per lane ([BR-112]) | `LaneCapacity` | Advisory lock on the lane | The lock itself |
| One plan per date ([BR-34]) | `Plan.open` | None | Partial unique index, excluding cancelled plans |
| One account per email ([BR-45]) | `UserAccount.open` | None | Unique index on the canonical email |
| One reference per date ([FR-211]) | `Order.place`, `ImportBatch` | None | Partial unique index on (date, reference) |
| One running re-optimization per plan ([BR-38]) | `SolveJob.queue` | None | Partial unique index on running jobs |

A snapshot is built only by the repository query that takes its lock. An ArchUnit test allows no
other caller of its factory, so the method that could break the rule cannot run without the lock
([DL §3.9][DL-39], rule 6). The 15-vehicle limit ([BR-36]) is a `FleetRegister` invariant and needs
no lock.

## Consequences

- **Easy:** a conflict ends in one place, a failed version check, which the dispatcher sees as
  `STALE_VERSION` (HTTP 412) and answers by refetching.
- **Easy:** a rule has one place to read, one place to test and one named error. Every invariant is
  tested with plain values and no mocks.
- **Easy:** a solve request cannot strand a plan in *solving*, because the plan and its job commit
  together.
- **Hard:** a change that touches two existing aggregates takes two transactions. Cancelling an order
  changes `Order`, and `Plan.removeStopOf` follows from the event ([DL §6.2][DL-62]). For a moment the
  stop is still on the route.
- **Hard:** every reaction must be idempotent, because delivery is at least once ([ADR-064]).
- **Hard:** a set rule needs three parts: a snapshot value, a lock and, where the database can say
  it, a backstop. A new set rule means writing all of them.
- **Cost:** three snapshot values, two kinds of lock, four unique indexes, and one more
  ArchUnit rule.
- **Risk:** `Plan` is the only aggregate with several writers in a normal day. DL expects a few writes
  a minute at peak and locks held for milliseconds ([DL §11][DL-11]). Neither figure is measured.
- **Risk:** a listener that loses a version race retries up to three times, then the event registry
  resubmits it. A busy day could show repeated retries before the pilot's load is known.

## Alternatives considered

- **Domain services.** Rejected. A service that read repositories or called other modules would be a
  use case in the wrong layer, and one that held a single aggregate's rule would leave that aggregate
  anaemic ([DL §16][DL-16]).
- **Several existing aggregates in one transaction.** Rejected: each added aggregate adds a version
  check that can fail, and the contention the one-aggregate rule prevents comes back.
- **Create the `SolveJob` after the plan changes, through an event.** Rejected: it adds a hop that
  can fail by itself and leave the plan in *solving* ([DL §16][DL-16]).
- **One aggregate per date that holds its orders, for [BR-101].** Rejected: orders move between dates
  ([FR-258], [FR-315]), so a move would change two aggregates ([DL §5][DL-5]).
- **One aggregate that holds every Admin account, for [BR-19].** Rejected: roles live on each
  account, because changing them must revoke that account's tokens in the same transaction
  ([NFR-177]).

## Revisit when

- A requirement needs an operation that coordinates several aggregates and belongs to none of them.
  [DL §3.7][DL-37] names that as the case for a domain service.
- The fleet or plan limit rises past ten times today's ([BR-36], [BR-101]), which is 150 vehicles or
  2,000 orders. [DL §15][DL-15] says `Plan` and `FleetRegister` still load whole at ten times the size.
- A load test ([NFR-10]) shows requests waiting on the date, lane or Admin locks, or dispatchers meet
  `STALE_VERSION` refusals on `Plan` on two or more saves a day.
- Pickups or a second company are added. [DL §15][DL-15] marks both as not additive.

[ADR-040]: ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-064]: ADR-064-events-only-where-they-earn-their-place-through-the-modulith-registry.md
[ADR-065]: ADR-065-facts-outrank-intent.md
[BR-19]: ../requirements/requirements.md#access
[BR-34]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-36]: ../requirements/requirements.md#time-and-limits
[BR-38]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-45]: ../requirements/requirements.md#access
[BR-76]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-101]: ../requirements/requirements.md#time-and-limits
[BR-112]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-113]: ../requirements/requirements.md#time-and-limits
[DL-5]: ../architecture/domain_layer/domain_layer_design.md#5-rules-over-sets-of-aggregates
[DL-11]: ../architecture/domain_layer/domain_layer_design.md#11-concurrency
[DL-15]: ../architecture/domain_layer/domain_layer_design.md#15-growth-likely-changes-and-where-they-go
[DL-16]: ../architecture/domain_layer/domain_layer_design.md#16-why-it-looks-like-this
[DL-37]: ../architecture/domain_layer/domain_layer_design.md#37-domain-services-none
[DL-39]: ../architecture/domain_layer/domain_layer_design.md#39-purity
[DL-61]: ../architecture/domain_layer/domain_layer_design.md#61-the-rule
[DL-62]: ../architecture/domain_layer/domain_layer_design.md#62-commands
[DL-63]: ../architecture/domain_layer/domain_layer_design.md#63-outside-calls-happen-before-the-transaction
[FR-211]: ../requirements/requirements.md#customers-and-orders
[FR-258]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-315]: ../requirements/requirements.md#customers-and-orders
[NFR-10]: ../requirements/requirements.md#nfr-10
[NFR-23]: ../requirements/requirements.md#nfr-23
[NFR-65]: ../requirements/requirements.md#nfr-65
[NFR-177]: ../requirements/requirements.md#nfr-177
[PL-25]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#25-decisions-to-record-as-adrs

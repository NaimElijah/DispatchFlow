# ADR-121: The application skeleton's review settles its seams, its conflicts, its retention and its scoring inputs

- **Status:** Accepted; amended by ADR-126
- **Date:** 2026-11-15

## Context

[ADR-119] declared the application layer ahead of its milestones, and [ADR-120] set its conventions.
Both merged in #82. Before the milestones build the bodies, Naim Elijah, the owner, asked for the
skeleton to be checked and fixed.

Nine reviewers read it:
- four by module;
- one each for the tests, security, architecture, the module contracts and the docs.

They found contracts that the builders could not build against as declared. The gaps sat in three
places:
- **between the core and the optimizer:** a failure with no kind, no exclusions returned, no seed for
  the baseline, and scoring inputs no side held;
- **between modules:** a roster read that skips the driver's standing, and a late report that can't
  find its order;
- **in rules the requirements already state:** views without the version their writes need, a
  sign-out that needs an access token, and phone writes without a request key.

Most fixes follow an accepted row or ADR. Eighteen needed a decision: thirteen after the review
(decisions 1 to 13), and five after a seven-reviewer re-review of the fixes (decisions 14 to 18). Naim
took each on 2026-11-15, all as recommended.

## Decision

### Naim's decisions

1. **The scoring rates live in the core's configuration.**
   - The values are:
     - the door-window rate ([BR-120]);
     - the lateness rate and the moved-stop cost ([BR-115]);
     - whether breaks are reserved ([FR-81], [BR-105]).
   - They are fixed named values, which no screen edits.
   - A Planning port answers them, and the core sends them in every problem, as [ADR-029] asks ("from
     `core`'s settings").
   - Reporting's baseline needs the same values. It reads them through a port of its own, bound to the
     same configuration, when M5 settles the baseline's travel inputs.
2. **The optimizer applies the departure allowance and a handed-over route's confirmed return.**
   - Python's `ProblemFactory` gains `departure_allowance` in M0 and a per-driver `confirmed_return`
     in M9, as [ADR-050] keeps every convention there.
   - The core sends both, and never folds them into the shift times. Folding them would move a route
     across [BR-105]'s 6-hour break threshold.
3. **A location the stored matrix lacks fetches the whole matrix again.** That covers a new order's
   address in a re-optimization ([FR-35]), and a late order on a publishing re-check or an evaluation.
   - The core fetches the matrix over the new location list, cached by the list's hash, and stores it
     in the job's success transaction.
   - When every location is already there, a re-optimization adds only the position rows, as before.

   This amends [ADR-052]'s "a re-optimization adds rows only", and the same rule in [ADR-120]
   decision 8.
4. **A conflict on a call that sends no version is retried, then answered 503.**
   - The web adapter retries the call at once, up to three times, as a listener does ([DL §11][DL-11]).
     It then answers 503 with `Retry-After`, so a phone's outbox backs off and sends again ([ADR-087]).
   - 412 stays only for a call that sent `If-Match`.
   - Fourteen calls are of this kind:
     - the driver's reports, positions, start, finish, problems and acknowledgement of a route change;
     - a dispatcher's fill of the roster, and an outcome recorded on a driver's behalf;
     - the two link pages;
     - the own-account changes: the terms, the language, the password and the step-up.
   - The SMS provider's callback (`ReceiveReply`) gets the same answer, and the provider sends it again.
   - A use case hands an email over only after its commit, so a retry never repeats one.

   This amends [ADR-057]'s "a stale version answers 412" for calls that send no version.
5. **Before M5's sign-in,** the web adapter builds the `Actor` from a stand-in session of a seeded
   account.
   - The stand-in exists only when `dev` or `test` is the only active profile, and the core refuses to
     boot with the stand-in and any other profile active. A test checks the refusal, and M5 deletes
     the stand-in.
   - The `web` ADR records how.
6. **The Alpha's access token.** Until M6 builds refresh, the dev profile alone sets a longer
   access-token lifetime.
   - Unless `dev` is the only active profile, the core refuses to boot with any lifetime but
     [NFR-86]'s 15 minutes, which M5 tests. M6 deletes the dev allowance.
   - This extends [ADR-069]'s 15-minute token.
7. **M2 builds two calls that its order screens use.**
   - `OrderPlacement.placementOf` reads the Planning view `placementsOn` reads.
   - `StopProgress` answers "no report" for every stop until M5 completes it.

   No stop can have a report before M5.
8. **Planning's copies of coordinates and positions end with retention.**
   - A plan keeps its matrix's numbers and its location list's hash, for the cache.
   - The location list is dropped, and the coordinates in its solve jobs' inputs are blanked, 90 days
     after the plan's date ([NFR-257], [NFR-260]).
   - The positions in a re-optimization job's input are blanked after 7 days ([NFR-184]).
   - A Planning retention job does both.

   This also amends [ADR-052]'s "each plan stores its own matrix with its location list", and extends
   [ADR-075]'s table of what retention removes.
9. **The nightly retention run is built in M13,** before real data arrives. That covers every
   retention, anonymization, purge and erase job, and the roadmap's M13 row lists it. It also drops,
   in any account's state, the links [ADR-104] keeps 72 hours after sending, so an unconfirmed
   email-change address doesn't wait for a later link. That extends [ADR-104].
10. **Email-change links are limited to 5 an hour per account that requests them** ([FR-411], new;
    decision 15 settles the key).
    - The web adapter counts them with Bucket4j, as [ADR-071]'s buckets count every limit, and as
      [ADR-120] P13 leaves [FR-172]'s per-account limit to the adapter.
    - Without the limit, enough bounced links would get the company's sending suspended, and recovery
      emails would stop for everyone.
11. **A late report's alert is raised before the stop moves.**
    - `ReconcileOutcome` raises the dispatcher's alert from the stop's whereabouts, whose
      `OnAnotherRoute` names the route it leaves. Only then does it call Planning's
      `ReconcileLateOutcome`.
    - The alert's key makes a repeat harmless. So a failure between the two steps can't lose the alert
      ([BR-89]), as it could when the move came first.
    - This amends [ADR-065], whose listener raised the alert after the move.
12. **A superseded put-back ends the order's put-back attempt.**
    - After Planning removes the later stop ([FR-404]), Live Operations calls a new Customers & Orders
      `api`, which returns the order to its earlier attempt, with that attempt's date and window
      (decision 18). Its status then derives *delivered*, and no solve plans it again.
    - The domain method is a gap for M8.
    - This extends [ADR-114], whose listener made only the Planning call.
13. **Route locks and personal-data requests.**
    - `LockRoute` and `UnlockRoute` stay, marked "Built in an unscheduled milestone". This is an
      exception to [ADR-120]'s P11, because the domain already holds locks ([ADR-112]). The locks are
      not sent until the optimizer has a lock rule.
    - Correcting and deleting a person's data on request ([NFR-49], [NFR-185]) is left out until M13
      and the legal advice ([Q-42]), with the exports [ADR-120] decision 9 leaves out.
    - Fleet's depot-geocoding port is not declared until M7, as [ADR-120] P4 already says. It is listed
      with those left-out items.
14. **Three new solve failure reasons** ([FR-65]):
    - *search failed* (`SOLVER_FAILED`);
    - *no solution within the limit* (`NO_SOLUTION_WITHIN_BUDGET`);
    - *request refused* (`REQUEST_REFUSED`): an unknown solver, an unsupported budget or an invalid
      problem, which is a fault for the team.

    `SolveFailureReason` gains them. The optimizer adapter maps `SearchFailure` to the first two. It
    throws `OptimizerRefusedRequest` for a refused request, and the worker fails the job with the
    third. So the dispatcher can tell "try a longer limit" from "the system is broken".
15. **FR-411 counts the account that asks.** The bucket is keyed by the signed-in requester, so a user
    who aims requests at another account can't spend that account's limit.
16. **A 404 on a phone write marks the outbox row refused,** as 409 and 422 do, when it carries the
    contract's `NOT_FOUND` code. Any other 404, such as a misrouted deploy's, is treated as a 5xx. A
    report on a route no plan holds any more then no longer blocks the route's later reports. This
    extends [ADR-087]'s sender rules.
17. **Each revision's stops keep the window, service time and door flag their solve took,** as
    [ADR-052] keeps the legs.
    - `PublishedStopFact` reads them from there. So the projections build every leg, including a
      put-back's, a rescheduled order's, and a stop served after a cancellation.
    - This is a Planning domain change for M5: `PlannedStop`, `RevisionStop` and [ADR-059]'s
      `plan_revision_stop` rows gain the three, which extends [ADR-059].
18. **A put-back keeps the earlier attempt's date and window.** Ending it (decision 12) restores both,
    frees the later date's capacity ([BR-101]) and skips the past-date check. This is an Orders domain
    change for M8.

### Settled by following accepted rows and ADRs

These continue [ADR-120]'s placements.

- **P16. The optimizer's failures carry a kind.**
  - A failed search is `SearchFailedError`, with a closed `SearchFailure`: solver failed, or no
    solution within the budget. Its value is the code the contract will carry. Planning maps it to
    decision 14's reasons, and Reporting's `RefusalCause` maps it without reading text.
  - An unknown solver or an unsupported budget is a refused request. The core gets it as
    `OptimizerRefusedRequest`.
  - Planning refuses a solver the optimizer doesn't offer, before its transaction (`SolverNotOffered`,
    422). When the optimizer can't be reached for its solvers, the request goes on unchecked and the
    job fails as *optimizer unavailable*. The service's registry holds only [FR-20]'s solvers
    ([ADR-004]), so the baseline's heuristic is no longer one of them, which amends [ADR-044].
- **P17. The optimizer's answers carry what the core stores.**
  - A daily solve returns its excluded vehicles ([FR-197]), its objective's terms and its run's stats
    ([NFR-11]).
  - The baseline runs on its own solver, with a seed and a budget that are Reporting's named
    constants. So a recompute replays the same request.
- **P18. The core's requests carry what only the core knows.** That covers:
  - the orders a dispatcher took off ([FR-146]);
  - the put-back orders ([FR-75]);
  - the service-area flag ([FR-320]);
  - for a daily solve, only vehicles rostered to an available driver;
  - for a re-optimization, every vehicle of the plan and every order on its routes;
  - for an evaluation, every vehicle its layout routes, each with the layout's driver, whatever their
    availability; the roster rule ([BR-20]) is the calling use case's;
  - position rows to every location, depots included, with Python judging which positions are usable.
- **P19. Phone writes take a request key** ([ADR-087]): starting a route, finishing it and reporting a
  problem, as stop reports and positions already do.
- **P20. Sign-out needs only the refresh token** ([NFR-01]). A revocation is saved without a version
  check.
  - The domain revokes the session (`Session.revoke`, or `exchange` on a reused token). The repository
    writes the revoked state only over a live row, since a session only moves from live to revoked.
  - So no conflict can lose a sign-out or a reuse revocation.
  - This amends [ADR-057]'s "`save` checks the version" for that one write.
- **P21. The roster that feeds a solve keeps only drivers whose standing is active** ([ADR-062]).
  - A hand-over asks Fleet's `assignability` before its transaction ([DL §4.2][DL-42]).
  - A late report finds its order and attempt from the revision in force at the corrected time.
- **P22. Every view a write is made from carries the version the write sends.**
- **P23. Alerts are read across dates.** An alert raised after its plan's date passed stays listed
  until it is acknowledged ([FR-261]).
- **P24. The answers to replies count toward the daily cap of 1,000** ([NFR-196]). This settles what
  [ADR-108]'s Risk left to the application. The opt-out hash keeps every key version that a stored
  opt-out still uses, with a re-hash step for rotations ([ADR-069]).
- **P25. Each outside system has its named outage error:** `RoadNetworkUnavailable`,
  `OptimizerUnavailable`, `GeocoderUnavailable` and `RoadSnapUnavailable`.
  - The geocoder answers a point and a match quality, or nothing. Ambiguity is a quality, and the
    distance to the nearest road is a port of its own, because OSRM reports it.
  - Reporting names its own outage error for the baseline (`BaselineOptimizerUnavailable`), since it
    can't import Planning's.
  - When the road network can't be reached for an evaluation, the use case asks
    `FeasibilityChecker.unavailableFor` for the unavailable evaluation, which only the adapter builds
    ([DL §13][DL-13]). An edit or publishing then refuses, and a hand-over records *not yet
    checked*.
  - This amends [ADR-085]'s four-case port shape.
- **P26. Refusals outside a use case.** The web adapter's file reader writes the refused-attempt record
  for a file it can't read. This extends [ADR-120] decision 1's list of refusals made before a use case
  runs ([ADR-110]).
- **P27. Bodies written for real.** The list of [ADR-119] decision 2 also holds:
  - the error types' accessors;
  - a record's check of its own invariant (`Actor`, `ReconciliationFact`, `SolveConfiguration`'s
    values and `BaselineBudget`'s minimum).

  Each check has a unit test. They are the only tests besides the architecture rules.
- **P28. Two rules join [ADR-120] decision 10:**
  - rule 4 also checks each implementation's package and name. It skips a sealed interface only when
    every permitted subclass is a record or an enum, so a sealed port is refused;
  - a new rule 8 keeps Reporting to the events of Live Operations, Planning and Customers & Orders,
    and to Access's `Actor` and `ActorRole` (P5).
- **P29. The application errors the review added, with their statuses,** join [ADR-120] decision 7's
  list:
  - `SolverNotOffered` 422;
  - `RouteNotStarted` 409, the *not started* answer [ADR-107] gives the application;
  - `ProofFormatRefused` and `ProofImageUnreadable`, both 422, so the phone's outbox marks the row
    refused.

  `OptimizerRefusedRequest` and the outage errors never reach a client as themselves.
- **P30. Two of [ADR-120]'s settled shapes change:**
  - Planning reads `StopOrders` for the printable route only; the loading list takes weight and volume
    from `OrdersForDate`.
  - Live Operations reads each stop's window, service time and door flag from `PublishedRoutes`
    (decision 17), and only locations and last change times from `OrdersForDate`.
- **P31. Two writes send their version another way:**
  - accepting a proposal sends the plan's version in the request body, because the request's target
    is the proposal;
  - the company profile's first save sends `If-None-Match: *`, because no view exists before it.

  Which status a stale body version answers is for the `web` ADR. Every other versioned write sends
  `If-Match` ([ADR-057]).

## Consequences

- **Easy:**
  - The M0, M1 and M5 contracts can be written from both sides of the optimizer's seam without
    guessing.
  - The phone's outbox has a rule for every answer it can get.
  - No retention job is unscheduled.
- **Hard:**
  - Python's `ProblemFactory` gains three inputs: the departure allowance at M0, the service-area flag
    at M7, and the confirmed return at M9.
  - Planning gains a retention job and three failure reasons.
  - The domain document changes in §4.3 (`Order`), §4.4 (`PlannedStop`), §6.2, §8, §9.6 and §9.7.
- **Cost:**
  - One new requirement row (FR-411), and FR-65's three new reasons.
  - A configuration block for four values.
  - A full matrix fetch when a re-optimization meets a new address: one OSRM call, under 5 s for 216
    points ([NFR-57]).
- **Risk:**
  - The dev profile's stand-in session or its longer token reaches another profile. The core refuses to
    boot with the stand-in unless `dev` or `test` is the only active profile, and with the longer token
    unless `dev` is the only active profile. M0 tests the stand-in's refusal, and M5 the token's.
  - A full re-fetch gives an existing pair a different time than the plan was solved with, after
    OSRM's monthly refresh. The stored legs keep the times each revision was planned with ([ADR-052]).

## Alternatives considered

- **Planning settings an Admin edits, for the scoring rates.** Rejected: [BR-115] fixes the rates after
  tuning, and the settings page ([FR-183]) doesn't list them.
- **The core folds the allowance and the confirmed return into the shift times.** Rejected: that is a
  convention, which [ADR-050] keeps in Python, and it would move routes across the break threshold.
- **Add only the new locations' rows and columns to the stored matrix.** Rejected: less OSRM work, but
  one matrix would mix two fetches.
- **Answer 409 after the retries, or keep 412 and teach the outbox a 412 rule.** Rejected: 409 marks a
  lost race as the driver's error, and 412 means a precondition the client never sent.
- **Build sign-in at M2, or refresh at M5.** Rejected: each moves work into a lane's Alpha milestone,
  where a dev-profile setting does the job on a laptop.
- **Keep Planning's copies of coordinates, and accept them in the data inventory.** Rejected: customer
  homes and driver positions would outlive [NFR-257] and [NFR-184].
- **Planning remembers its reconciliations, so the listener can always call it.** Rejected: a second
  record for what the alert's key already makes safe.
- **The order's status counts a delivered earlier attempt as settling the order.** Rejected: every
  reader of the status would need the exception.

## Revisit when

- M0 writes the `web` ADR. It checks the stand-in session, the longer token and the retry-then-503
  answer. It settles which HTTP status each domain error gets, the 401 challenge, and the status of
  a stale version sent in a body (P31).
- The optimizer has a lock rule, or [FR-147] is scheduled.
- The legal advice ([Q-42]) arrives, which sets M13's personal-data steps and the retention periods.
- Re-fetching whole matrices for re-optimizations costs more than [NFR-07]'s limit allows.

[ADR-004]: ADR-004-pluggable-solver-port.md
[ADR-029]: ADR-029-solver-objective-and-re-optimization-rules.md
[ADR-044]: ADR-044-solver-port-takes-start-budget-and-seed-and-problem-judges.md
[ADR-050]: ADR-050-re-optimization-conventions-live-only-in-the-python-problem-factory.md
[ADR-052]: ADR-052-each-plan-stores-the-matrix-and-values-it-was-solved-with.md
[ADR-057]: ADR-057-concurrent-changes-to-a-plan-use-optimistic-locking.md
[ADR-059]: ADR-059-plan-revisions-are-stored-as-rows.md
[ADR-062]: ADR-062-fleet-checks-the-driver-role-when-it-reads.md
[ADR-065]: ADR-065-facts-outrank-intent.md
[ADR-069]: ADR-069-sign-in-and-transport-security.md
[ADR-071]: ADR-071-api-rate-limits-in-process-with-bucket4j.md
[ADR-075]: ADR-075-retention-deletes-records-and-anonymizes-people-in-place.md
[ADR-085]: ADR-085-govmap-geocodes-addresses-with-nominatim-as-the-fallback.md
[ADR-087]: ADR-087-driver-reports-sync-through-an-idempotent-outbox.md
[ADR-104]: ADR-104-the-company-profile-holds-customer-defaults-and-capabilities-follow-the-role-matrix.md
[ADR-107]: ADR-107-live-operations-alerts-once-per-key-corrects-a-put-back-by-event-and-treats-a-reschedule-as-a-cancellation.md
[ADR-108]: ADR-108-messaging-appends-opt-ins-holds-its-fixed-wording-and-cuts-comments-to-500-characters.md
[ADR-110]: ADR-110-audit-records-hold-only-ids-codes-enums-numbers-and-instants-record-refusals-and-are-kept-a-year.md
[ADR-112]: ADR-112-a-re-optimization-keeps-every-order-and-route-a-solve-that-breaks-a-lock-fails-and-publishing-clears-locks.md
[ADR-114]: ADR-114-live-operations-reaches-planning-from-its-own-listener-and-names-every-fact-it-judges.md
[ADR-119]: ADR-119-the-application-layer-is-declared-ahead-as-signatures-whose-bodies-throw.md
[ADR-120]: ADR-120-the-application-layer-takes-an-actor-and-a-call-context-and-reads-through-query-ports.md
[BR-20]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-89]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-101]: ../requirements/requirements.md#time-and-limits
[BR-105]: ../requirements/requirements.md#routing-constraints
[BR-115]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-120]: ../requirements/requirements.md#solving-dropping-and-validation
[FR-20]: ../requirements/requirements.md#daily-planning
[FR-35]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-65]: ../requirements/requirements.md#daily-planning
[FR-75]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-81]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-146]: ../requirements/requirements.md#manual-editing
[FR-147]: ../requirements/requirements.md#manual-editing
[FR-172]: ../requirements/requirements.md#company-users-and-roles
[FR-183]: ../requirements/requirements.md#company-users-and-roles
[FR-197]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-261]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-320]: ../requirements/requirements.md#geocoding
[FR-404]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-411]: ../requirements/requirements.md#company-users-and-roles
[NFR-01]: ../requirements/requirements.md#nfr-01
[NFR-07]: ../requirements/requirements.md#nfr-07
[NFR-11]: ../requirements/requirements.md#nfr-11
[NFR-49]: ../requirements/requirements.md#nfr-49
[NFR-57]: ../requirements/requirements.md#nfr-57
[NFR-86]: ../requirements/requirements.md#nfr-86
[NFR-184]: ../requirements/requirements.md#nfr-184
[NFR-185]: ../requirements/requirements.md#nfr-185
[NFR-196]: ../requirements/requirements.md#nfr-196
[NFR-257]: ../requirements/requirements.md#nfr-257
[NFR-260]: ../requirements/requirements.md#nfr-260
[Q-42]: ../requirements/requirements.md#open-questions
[DL-11]: ../architecture/domain_layer/domain_layer_design.md#11-concurrency
[DL-13]: ../architecture/domain_layer/domain_layer_design.md#13-inside-each-module-hexagon
[DL-42]: ../architecture/domain_layer/domain_layer_design.md#42-fleet

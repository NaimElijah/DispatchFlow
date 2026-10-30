# DispatchFlow — Requirements

What the system must do. For how it does it, see
[`../architecture/architecture.md`](../architecture/architecture.md); for why a given
solution was chosen, see [`../ADRs/`](../ADRs/README.md).

**Priorities** use MoSCoW: **Must**, **Should**, **Could**; **Won't** items are listed under
[Non-goals](#non-goals). **Size** is XS, S, M, L or XL. *Size* and *Enforced by* stay blank
until the team sizes and implements each requirement; *Enforced by* then names the test that
enforces it.

## Purpose and scope

**The problem.** Small delivery fleets of roughly 2–15 vehicles — pharmacies, florists,
bakeries, local grocers, small courier companies — still plan their routes by hand, in
spreadsheets, or in the dispatcher's head. Enterprise route planners exist but are too
expensive and too complex for them. The result is extra kilometres, overtime, missed
delivery windows, and no visibility into where drivers are. Manual plans also cannot adapt
when the day goes wrong: a van breaking down at 10:00 means phone calls and guesswork.

**What DispatchFlow does.** A dispatcher loads the day's orders and DispatchFlow decides which vehicle
takes which orders and in what order, so that every delivery lands inside its time window,
no vehicle exceeds its weight or volume capacity, every route fits its driver's shift, and
total driving time is as low as possible — the Vehicle Routing Problem with Time Windows
(VRPTW). During the day it keeps the plan valid: when drivers run late, vehicles break down
or new orders arrive, it re-plans the remaining stops in under 30 seconds while disturbing
drivers' existing routes as little as possible. It proves its value by comparing every plan
against a baseline in kilometres, driver-hours and on-time percentage.

**Who uses it.**

| Role | Uses DispatchFlow to |
|---|---|
| Admin | Manage the organization and its users |
| Dispatcher | Manage fleet, shifts, customers and orders; plan, edit, publish and monitor routes; re-optimize during the day |
| Driver | Follow the published route, report each stop's outcome, capture proof of delivery |

**Academic aim.** DispatchFlow runs on Google OR-Tools. It is the solver the product ships,
demos and pilots with, so a working system never waits on research. Beside it, behind the same
solver interface ([FR-20](#daily-planning)), DispatchFlow builds its own metaheuristic solver:
Clarke-Wright savings construction improved by Adaptive Large Neighbourhood Search. That keeps
the product from reducing to an OR-Tools integration with a map. The DispatchFlow solver is
aimed where it can plausibly win, which is live re-optimization: repairing the current plan
after a disruption instead of solving again from scratch. It is not expected to beat OR-Tools
on static instances ([Non-goals](#non-goals)). The two solvers are compared in a planned
experiment whose success criteria are fixed in advance
([Solver research targets](#solver-research-targets)):

- on the Solomon and Gehring-Homberger instances and on the pilot's real days;
- under equal time limits;
- with PyVRP as a state-of-the-art reference.

The results are reported whether or not the DispatchFlow solver wins.
([ADR-004](../ADRs/ADR-004-pluggable-solver-port.md),
[ADR-005](../ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md))

> Until 2026-10-27, the [implementation plan](<../init_impl/DeliOpt — Implementation Plan.md>)
> ranked the DispatchFlow solver a Should and re-optimization a Could, and the
> [initial presentation](../presentation/DispatchFlow_Presentation.pptx) used OR-Tools only as a
> benchmark baseline and scheduled the solver core before the platform. All of these were revised
> to follow [ADR-005](../ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md): OR-Tools
> first, and the DispatchFlow solver as a measured research track.

**Scope.** Israel only: road data from the OpenStreetMap Israel extract, Hebrew addresses.
One plan per organization per day, at the scale stated in [NFR-05](#nfr-05). Everything
deliberately left out is listed under [Non-goals](#non-goals).

## Functional requirements

### Organization, users and roles

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-01 | An Admin can set up and manage their organization. Anyone can sign up a new organization, becoming its first Admin. Every record in DispatchFlow belongs to exactly one organization. | Must |  |  |
| FR-02 | An Admin can create users and give each one a role: Admin, Dispatcher or Driver. | Must |  |  |
| FR-03 | A user can sign in and use only the functions their role permits, as the permission matrix below says. | Must |  |  |

**Permission matrix** (FR-03; decided on 2026-10-11, Q-12, ADR pending: authorize every web
handler by role). A ✓ means the role may; the server checks it on every call, and the
organization is always the caller's own.

| Capability | Admin | Dispatcher | Driver |
|---|---|---|---|
| Manage the organization and its users: rename it, register users, change their roles | ✓ | | |
| Read the organization and its users | ✓ | ✓ | Only the organization and themselves |
| Manage depots, vehicles, drivers and shifts; customers and orders | ✓ | ✓ | |
| Plan, solve, edit, publish and re-optimize; live views and reports | ✓ | ✓ | |
| Work their own route: read it, report stops and proofs, report positions | | | ✓ (BR-02) |

### Fleet, depots and shifts

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-04 | A dispatcher can define depots, each with a location. | Must |  |  |
| FR-05 | A dispatcher can register vehicles, each with a weight capacity, a volume capacity, a start depot and an end depot. | Must |  |  |
| FR-06 | A dispatcher can register drivers, each with a shift start, a shift end and an assigned vehicle. | Must |  |  |
| FR-07 | A dispatcher can mark a vehicle unavailable during the day (for example after a breakdown), so that re-optimization ([FR-35](#live-operations-and-re-optimization)) redistributes its remaining stops. | Must |  |  |

### Customers and orders

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-08 | A dispatcher can create customers, each with an address, coordinates, a default time window, and notes that drivers see. | Must |  |  |
| FR-09 | A dispatcher can enter an order through a form: customer, date, time window (defaulting to the customer's), weight and volume demand, service duration and priority (1 to 5). | Must |  |  |
| FR-10 | A dispatcher can import a day's orders from a CSV file. Every row is validated, and every rejected row is reported with its reason; a file with any rejected row imports nothing, and rows name their customer by address (ADR pending: CSV order import). (File format: [Q-10](#open-questions).) | Must |  |  |
| FR-11 | A dispatcher can add orders to a day whose plan is already published; they enter the routes through re-optimization. | Must |  |  |

### Geocoding

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-12 | The system converts every customer address, including Hebrew addresses, to coordinates. | Must |  |  |
| FR-13 | Geocoding results are cached, so the same address is not looked up repeatedly — within the limits the provider's terms allow ([NFR-17](#nfr-17)). | Must |  |  |
| FR-14 | A dispatcher can fix a bad match by dragging the customer's pin on the map. The corrected location is saved for that customer and used for all of their future orders. | Must |  |  |

### Daily planning

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-15 | A dispatcher can create a plan for a date and request a solve over that date's orders, vehicles and driver shifts. | Must |  |  |
| FR-16 | A solve runs in the background. The dispatcher sees that the plan is solving and is notified when the result is ready, without reloading. | Must |  |  |
| FR-17 | A solve assigns orders to vehicles, orders the stops of every route, and gives each stop a planned ETA, honouring every rule in [Business rules](#business-rules-and-invariants). | Must |  |  |
| FR-18 | When not every order fits, the solve still returns a plan and lists every dropped order with the reason it was dropped: *capacity*, *time window*, *shift*, *displaced* by other orders, or *missing location* for an order whose customer has no coordinates. | Must |  |  |
| FR-19 | The solve time limit is configurable within the range in [NFR-06](#nfr-06); the solve returns the best solution found within it. (Default value and who may change it: [Q-21](#open-questions).) | Must |  |  |
| FR-20 | A solve or a re-optimization can be run with either solver: OR-Tools, the default, or the DispatchFlow solver (Clarke-Wright + ALNS). Outside the optimization service a solver is known only by its name, and choosing one changes nothing else. | Must |  |  |
| FR-21 | An organization can enable a fixed cost per vehicle used, so the solver also reduces the number of vehicles on the road. | Must |  |  |

### Manual editing

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-22 | A dispatcher can drag a stop to another position in its route, or to another route, while the plan is ready and not yet published. | Should |  |  |
| FR-23 | After each manual edit, ETAs are recalculated from road travel times and every time-window, capacity and shift violation is flagged immediately. No optimization runs for this. A plan with flagged violations cannot be published ([BR-17](#business-rules-and-invariants)). | Should |  |  |

### Dispatcher views

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-24 | A dispatcher sees all routes of a plan on a map, drawn along the road network, with their stops. | Must |  |  |
| FR-25 | A dispatcher sees a timeline of stops per vehicle. | Should |  |  |

### Publishing and the driver app

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-26 | A dispatcher can publish a plan; each driver then sees their own route. | Must |  |  |
| FR-27 | A driver sees today's route: stops in order with planned ETAs, customer details and notes. | Must |  |  |
| FR-28 | A driver can hand off any stop to navigation: a Navigate action deep-links to Waze or Google Maps. | Must |  |  |
| FR-29 | A driver can mark a stop *arrived*, *delivered* or *failed*. Marking *failed* requires a reason. | Must |  |  |
| FR-30 | A driver can capture proof of delivery: a photo or a signature, with an optional note. | Should |  |  |
| FR-31 | While a route is in progress, the driver app reports the driver's location, including while it runs in the background on iOS and Android. The web build reports only while it is open. (Start/stop conditions and frequency: [Q-15](#open-questions).) | Must |  |  |
| FR-32 | A driver on the road receives route changes after a re-optimization is applied. (Manual edits happen only before publishing.) | Must |  |  |

### Live operations and re-optimization

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-33 | A dispatcher sees, live on the map, each vehicle's latest position and each stop's status. | Must |  |  |
| FR-34 | The system detects stops at risk of missing their time window — a driver running behind the plan — and alerts the dispatcher. A stop is at risk when its projected arrival, the planned arrival plus the route's observed delay, is later than its window end minus a threshold. (Threshold value: [Q-07](#open-questions).) | Must |  |  |
| FR-35 | A dispatcher can re-optimize the remaining stops of a published plan after a disruption: a late driver, an unavailable vehicle, or new orders. (Whether disruptions may also trigger it automatically: [Q-07](#open-questions).) | Must |  |  |
| FR-36 | Before a re-optimization is applied, the dispatcher sees a preview of what changes for each driver. Nothing changes for drivers until the dispatcher accepts it. | Must |  |  |
| FR-37 | Re-optimization keeps drivers' existing routes as stable as it can: route stability is part of its objective, measured as the number of stops that leave their vehicle, dropped stops included. (Stability weight: [Q-06](#open-questions).) | Must |  |  |

### Reporting and baseline

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-38 | A dispatcher sees a daily summary of each plan: per route and in total — distance, driving time, stops, delivered and failed stops, on-time percentage, vehicles used, dropped orders. The on-time percentage is the share of delivered stops whose actual arrival, as the driver's phone recorded it, was at or before their window end; with nothing delivered it is shown as unavailable, not as zero. | Must |  |  |
| FR-39 | Every plan is compared with a naive nearest-neighbour baseline on the same orders and fleet, in kilometres, driver-hours and planned on-time percentage: orders served inside their windows over all orders of the day, so dropped orders count as not on time. A vehicle is withdrawn mid-route when a re-optimization leaves it out while it still has stops it made: it was marked unavailable (FR-07), left without a driver, or its shift ran out. Its route keeps those stops and never returns, so its driver-hours are unknown. Such a day is compared on kilometres and on-time only, and the comparison names the withdrawn vehicles. Those two figures are not strictly like for like: the day's baseline is solved after the re-optimization, leaving out a vehicle that was marked unavailable or left without a driver, and giving one whose shift ran out its whole shift (decided on 2026-10-10, Q-24). | Must |  |  |
| FR-40 | Where a customer supplies historical manual routes, a plan can be compared against them on the same measures. | Should |  |  |
| FR-41 | An analytics dashboard shows trends across days: on-time percentage, kilometres saved. | Could |  |  |

### Research and evaluation

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-42 | DispatchFlow has its own solver: a Clarke-Wright savings construction improved by Adaptive Large Neighbourhood Search with destroy and repair operators and simulated-annealing acceptance, behind the same solver interface as OR-Tools. For a re-optimization it starts from the current routes and repairs them, destroying and reinserting stops around the disruption, instead of solving again from scratch. No product capability depends on it ([Non-goals](#non-goals)). | Must |  |  |
| FR-43 | A benchmark harness runs OR-Tools and the DispatchFlow solver on the Solomon instances (56 instances of 100 customers, classes C1, C2, R1, R2, RC1, RC2), on Gehring-Homberger instances, and on the pilot's real days once the pilot provides them ([Q-01](#open-questions)). It reports the gap from best-known solutions where they are published, the DispatchFlow solver's gap from OR-Tools ([NFR-24](#nfr-24)), and the solve time. Both solvers get the same time limit on the same hardware, and every result is scored by the shared objective ([BR-09](#business-rules-and-invariants)) once the validator accepts it ([BR-15](#business-rules-and-invariants)). (Gehring-Homberger subset: [Q-22](#open-questions).) | Must |  |  |
| FR-44 | A scaling study reports solve time and solution quality at 25, 50, 100 and 200 stops. | Must |  |  |
| FR-45 | A synthetic order generator builds realistic days from real Israeli addresses, for testing and as the fallback when no pilot data is available. | Must |  |  |
| FR-46 | A simulation replays a real or synthetic day with injected delays, breakdowns and new orders, generated from a recorded seed so that the day can be replayed. It runs every re-optimization with each solver at the same set of time limits: OR-Tools warm-started from the current routes, and the DispatchFlow solver repairing them. At each limit it measures each solver's objective, route stability, elapsed time and whether the new orders were served, all scored by the shared objective ([BR-09](#business-rules-and-invariants)). A run in which OR-Tools did not start from the current routes is reported and not counted as warm-started ([NFR-25](#nfr-25)). | Must |  |  |
| FR-47 | A pilot runs DispatchFlow with a real customer; their feedback is collected and written up. (Pilot customer: [Q-01](#open-questions).) | Must |  |  |
| FR-48 | A usability study runs task-based sessions with the pilot's dispatcher (build a plan, handle a breakdown) scored with the SUS questionnaire, collects driver feedback on route stability after re-optimization, and feeds UI iterations between rounds. | Should |  |  |
| FR-51 | The benchmark harness also runs PyVRP, an open-source state-of-the-art VRP solver, on the static instances of FR-43 under the same time limits, as a reference for both solvers. Only the harness uses PyVRP; it never serves a solve in the product. | Should |  |  |

### Later capabilities

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-49 | End customers receive an SMS or email with their delivery ETA. | Could |  |  |
| FR-50 | An organization can run several depots, with vehicles starting and ending at different ones. | Could |  |  |

## Business rules and invariants

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-01 | **Tenant isolation.** Every entity belongs to exactly one organization. No user can read or change another organization's data. |  |  |
| BR-02 | **Driver scope.** A Driver can see and update only the stops of routes assigned to them. |  |  |
| BR-03 | **Time windows.** Service at a stop starts inside its order's time window. A vehicle that arrives early waits. An order's window defaults to its customer's default window. |  |  |
| BR-04 | **Service time.** Each stop occupies the vehicle for the order's service duration, which counts toward the arrival time at the next stop. |  |  |
| BR-05 | **Capacity.** On every route, the total weight demand never exceeds the vehicle's weight capacity, and the total volume demand never exceeds its volume capacity. Capacities are positive in both dimensions; demands are zero or more. |  |  |
| BR-06 | **Shifts.** A route leaves no earlier than its driver's shift start and returns to its end depot no later than the shift end. |  |  |
| BR-07 | **Single assignment.** Within a plan, every order is either served by exactly one stop or reported as dropped — never both, never twice. |  |  |
| BR-08 | **Dropping.** An order is dropped only when not every order can be served. Priority is a whole number from 1 to 5, and dropping follows it strictly: a higher-priority order is never dropped to save any number of lower-priority orders, and among orders of one priority the fewest are dropped. |  |  |
| BR-09 | **Objective.** A solve minimizes total travel time plus drop penalties, plus the fixed vehicle cost when enabled ([FR-21](#daily-planning)). A re-optimization also adds a route-stability penalty ([FR-37](#live-operations-and-re-optimization)). |  |  |
| BR-10 | **Locked stops.** Re-optimization never moves a completed or in-progress stop. Each vehicle's current location becomes its new starting point. |  |  |
| BR-11 | **Failure reason.** A stop cannot be marked *failed* without a reason. |  |  |
| BR-12 | **Plan lifecycle.** A plan moves `draft → solving → ready → published → completed`. A failed solve returns it to `draft`, or to `ready` if it was solved before; a ready plan may be solved again. Manual edits keep a plan `ready`; an accepted re-optimization keeps it `published` and changes it in place. No other transition exists; there is no cancel. |  |  |
| BR-13 | **Corrected locations win.** A location a dispatcher corrected by hand overrides any geocoding result for that customer. |  |  |
| BR-14 | **Routable orders only.** An order can be planned only when its customer has coordinates. |  |  |
| BR-15 | **Validated solutions only.** No solution — from either solver, or from a re-optimization — is stored as a plan or shown as feasible unless the independent feasibility validator accepts it. A solution the validator rejects fails its solve job and lists the violations. |  |  |
| BR-16 | **Edits are flagged, never silently fixed.** A manual edit that breaks a rule above is kept as the dispatcher made it and flagged with every violation. |  |  |
| BR-17 | **No publishing with violations.** A plan with any flagged violation cannot be published; the dispatcher fixes it or solves again first. |  |  |
| BR-18 | **Outcomes are final.** A delivery moves `pending → arrived → delivered` or `failed`, and may skip *arrived*. Once *delivered* or *failed* is recorded, it cannot change. |  |  |
| BR-19 | **At least one Admin.** An organization always keeps at least one Admin: the role of its only Admin cannot be changed. An Admin who wants to step down first makes another user an Admin. |  |  |
| BR-20 | **Published as solved.** A plan is published only while every route can still be driven as it was solved: its van is not marked unavailable, has a driver who holds the Driver role, and that driver is the route's. Otherwise nothing is published, every changed van is named, and the dispatcher solves again. Other changes since the solve, to a driver's shift, a van's capacity or its depots, are caught by BR-21. Accepting a re-optimization applies the same rule to every van the proposal keeps driving, skipping vans withdrawn mid-route, and refuses, naming every changed van, so the dispatcher re-optimizes again (ADR pending: check the vans when a re-optimization is accepted). |  |  |
| BR-21 | **Rules hold at publishing.** Before a ready plan is published, every route is re-checked once by the shared validator against the current fleet and orders of its date: shifts, capacities, depots and time windows. If a route breaks a rule, nothing is published, every broken rule is listed, and the dispatcher solves again or edits the route. When nothing breaks, the re-checked times are published: they equal the solved times unless the fleet changed since the solve, and then they follow it. An order the plan does not account for refuses before it (BR-22). Violations already flagged refuse first (BR-17), without a re-check, and a plan without routes needs none. If the re-check cannot run, nothing is published. |  |  |
| BR-22 | **Every order accounted for at publishing.** A plan is published only when it serves or drops every order of its date. An order placed after the solve was requested refuses publishing: nothing is published, every such order is named, and the dispatcher solves again. Changed vans (BR-20) are reported first; flagged violations (BR-17) and the re-check (BR-21) come after. Orders placed after publishing enter through re-optimization (FR-11). |  |  |

## Non-functional requirements

### Security and access

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-01"></a>NFR-01 | Every API call requires a valid access token, except signing up, signing in, refreshing, signing out and health checks; roles are checked on the server for every operation. An access token is accepted for 15 minutes by default. |  |  |
| <a id="nfr-02"></a>NFR-02 | Tenant isolation (BR-01) is enforced by the backend on every read and write, and is tested through the API with at least two organizations. |  |  |
| <a id="nfr-03"></a>NFR-03 | Secrets — provider API keys, token-signing keys, database credentials — come from the environment or a secret manager, never from the repository, logs or error messages. |  |  |
| <a id="nfr-22"></a>NFR-22 | A signed-in user stays signed in through refresh tokens. Each one is exchanged once, for a new access token and the session's next refresh token, and lasts 14 days by default from its issue. Presenting one already exchanged revokes its whole session; a sign-out revokes its session; a role or password change revokes every session of the user. Only a refresh token's hash is stored, and every refusal looks the same. |  |  |
| <a id="nfr-23"></a>NFR-23 | Every API call that changes something, and every sign-up, sign-in, refresh and sign-out, leaves one audit record: when, the acting user and organization when known, the operation, the records it names, its outcome and its trace id. An audit record never carries personal data or a secret (NFR-19, NFR-03). Reads are not audited. |  |  |

### Performance and scale

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-04"></a>NFR-04 | Fleet size: up to 15 vehicles per organization. |  |  |
| <a id="nfr-05"></a>NFR-05 | Plan size: up to 200 stops per plan. |  |  |
| <a id="nfr-06"></a>NFR-06 | A daily solve finishes within its configured time limit, settable between 5 and 60 seconds. |  |  |
| <a id="nfr-07"></a>NFR-07 | A re-optimization returns its preview within 30 seconds at the 99th percentile, measured under simulated disruptions at the scale of NFR-04 and NFR-05. |  |  |
| <a id="nfr-08"></a>NFR-08 | Violation checks after a manual edit feel instant to the dispatcher. (Numeric target: [Q-08](#open-questions).) |  |  |
| <a id="nfr-09"></a>NFR-09 | Stop-status and position updates reach the dispatcher's map in real time. (Latency target: [Q-08](#open-questions).) |  |  |
| <a id="nfr-10"></a>NFR-10 | The API is load-tested with k6. (Target load: [Q-08](#open-questions).) |  |  |

### Solver research targets

These are the success criteria of the planned comparison between the DispatchFlow solver and
OR-Tools ([ADR-005](../ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md)).
They bind the research track, not the product. OR-Tools serves the product whatever the
outcome, and a missed target is a result to report, not a failed release. The study reports
each target's measured value and whether it was met.

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-24"></a>NFR-24 | **Static quality.** On the Solomon instances, with both solvers given the same time limit on the same hardware, the DispatchFlow solver's objective is on average within **N%** of OR-Tools'. Both solvers are scored by the shared objective ([BR-09](#business-rules-and-invariants)), with the study's drop penalty and fixed-cost setting. Each solver runs *k* times per instance, one solver at a time on an otherwise idle machine, and its result on the instance is the mean of those runs. The gap on an instance is (DispatchFlow − OR-Tools) / OR-Tools, and the average is the mean of the gaps over all instances. No failure is left out: the target is met only if every run of the DispatchFlow solver returns a solution the validator accepts ([BR-15](#business-rules-and-invariants)), and an instance on which an OR-Tools run fails or is rejected is reported and left out of the mean, which can only count against the DispatchFlow solver. Every run records its seed, the iterations or solutions it reached and the solver versions, so that it can be replayed under that budget ([NFR-13](#nfr-13)). (N, the time limit, *k*, the drop penalty and the fixed-cost setting: [Q-29](#open-questions).) |  |  |
| <a id="nfr-25"></a>NFR-25 | **Incremental re-optimization.** Given a published plan at the scale of [NFR-04](#nfr-04) and [NFR-05](#nfr-05) and one new order, both solvers get the same time limit of **T seconds**: the DispatchFlow solver repairs the plan, and OR-Tools is warm-started from it. Within that limit the DispatchFlow solver returns a solution the validator accepts that serves the new order, and over the study's insertions its objective is on average no worse than OR-Tools'. Both are scored by the shared objective ([BR-09](#business-rules-and-invariants)). Only insertions for which OR-Tools, solving the whole day from scratch, serves the new order are counted. The elapsed time is measured inside the optimization service, from receiving the problem to returning the solution, and stays within T at the 99th percentile. Both solvers are also run at a set of shorter and longer limits, and the report gives each solver's objective at each limit, so that a claim such as "ours at 1 s matches OR-Tools at 5 s" can be checked. This is a solver-side target; the end-to-end promise to the dispatcher remains [NFR-07](#nfr-07). (T, the set of limits, and how insertions are sampled: [Q-30](#open-questions).) |  |  |

### Reliability and persistence

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-11"></a>NFR-11 | Every solve is recorded with its parameters, status, timings, solver used and objective value, so it can be audited and reproduced. |  |  |
| <a id="nfr-12"></a>NFR-12 | A queued solve survives a backend restart. |  |  |
| <a id="nfr-13"></a>NFR-13 | The DispatchFlow solver is reproducible: the same input, seed and iteration budget give the same solution. |  |  |
| <a id="nfr-14"></a>NFR-14 | The system runs on a single VM with no redundancy. (Availability and backup targets: [Q-08](#open-questions).) |  |  |

### Platforms and deployment

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-15"></a>NFR-15 | The dispatcher console is a desktop web app. The driver app runs on iOS, Android and in a browser, built from one codebase. |  |  |
| <a id="nfr-16"></a>NFR-16 | Every server-side component starts with one Docker Compose command. CI builds and tests every change. |  |  |

### Compliance and personal data

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-17"></a>NFR-17 | Use of every third-party map or geocoding service complies with its terms. For Google's Geocoding API this means the restriction on use with a non-Google map and the 30-day limit on cached coordinates; both must be resolved before the pilot handles real data. |  |  |
| <a id="nfr-18"></a>NFR-18 | Maps built on OpenStreetMap data show the attribution its licence requires. |  |  |
| <a id="nfr-19"></a>NFR-19 | Personal data — customer names, addresses and phone numbers, driver location pings, delivery photos and signatures — never appears in logs. (Retention periods and legal basis for tracking drivers: [Q-14](#open-questions).) |  |  |

### Observability and maintainability

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-20"></a>NFR-20 | Every service writes structured logs carrying a correlation id propagated across every hop, exports OpenTelemetry traces and metrics, and exposes health and readiness endpoints. Solve latency, re-optimization latency, job queue depth, error rate, dropped-order rate and on-time percentage are measured. |  |  |
| <a id="nfr-21"></a>NFR-21 | Every API — client to backend, backend to optimization service — is described by a versioned OpenAPI contract, and both sides are tested against it. |  |  |

## Non-goals

DispatchFlow deliberately does not:

- **Use live traffic data.** Travel times come from the road network with time-of-day
  multipliers; ETA drift is a stated, known limitation. (ADR pending:
  no live traffic)
- **Handle payments.**
- **Run a marketplace or crowdsourced drivers.** Every driver belongs to the organization.
- **Provide turn-by-turn navigation.** The driver app hands off to Waze or Google Maps.
- **Serve enterprise-scale fleets.** It is sized for roughly 2–15 vehicles and 200 stops a day;
  larger fleets are not designed for.
- **Beat OR-Tools on static benchmarks.** The DispatchFlow solver is not expected to outperform
  OR-Tools' mature routing solver on standard static VRPTW instances. Its target is live
  re-optimization ([NFR-25](#nfr-25)); the static comparison measures how close it comes
  ([NFR-24](#nfr-24)).
  ([ADR-005](../ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md))
- **Depend on the DispatchFlow solver.** No product capability, default, demo or pilot run
  requires it. OR-Tools serves every solve unless the DispatchFlow solver is chosen
  ([FR-20](#daily-planning)).
  ([ADR-005](../ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md))

> Native mobile apps were a non-goal in the implementation plan. That was reversed on
> 2026-10-09: the driver app ships for iOS and Android
> ([ADR-003](../ADRs/ADR-003-dispatcher-console-and-driver-app.md)). The plan was revised on
> 2026-10-28 to match: the driver app is native and a Must, and live driver location is a Must.
> On the same day the initial presentation stopped marking the driver client optional.

## Open questions

| ID | Question | Blocks |
|---|---|---|
| Q-01 | Which business is the pilot customer, and what does it provide — historical orders and routes, fleet data, access to dispatcher and drivers? Needed before the approval meeting; fallback is synthetic data ([FR-45](#research-and-evaluation)). Data owner: the pilot customer. | FR-40, FR-47, FR-48 |
| Q-02 | Geocoding provider: Hebrew-accuracy test on real addresses against at least one alternative, plus a review of Google's terms on non-Google maps and caching. The chosen provider's terms must also allow keeping the coordinates copied onto customers and depots; if Google stays, those copies are geocoded again on a schedule before its retention runs out (settles Q-25). | FR-12, FR-13, NFR-17 — ADR pending: Google geocoding provider |
| Q-03 | Dispatcher map: Leaflet or MapLibre, and which tile source (OpenStreetMap's public tile servers have a usage policy). | FR-24 |
| Q-04 | Real-time channel: WebSocket or server-sent events to the dispatcher console; how route changes reach a backgrounded driver app (push notification, socket, polling). | FR-16, FR-32, FR-33 |
| Q-05 | ~~Status sets and Plan transitions.~~ **Resolved** on 2026-10-09 by ADR pending: plan lifecycle publishing stability and drop reasons. Still open: what triggers a published plan's completion — the end of the day or the last delivery. | BR-12 |
| Q-06 | How heavily route stability weighs in the objective, and how it is modelled in OR-Tools. (The measure itself was settled by ADR pending: plan lifecycle publishing stability and drop reasons.) How OR-Tools models stability also decides whether the re-optimization comparison is fair to it. | FR-37, FR-46, NFR-07 |
| Q-07 | Is re-optimization only dispatcher-triggered, or may detected disruptions trigger it? What lateness counts as "at risk"? | FR-34, FR-35 |
| Q-08 | Numeric targets: manual-edit check latency, live-update latency, k6 API load, SUS score, availability, backup and restore. | NFR-08, NFR-09, NFR-10, NFR-14, FR-48 |
| Q-09 | ~~May a plan with flagged violations be published?~~ **Resolved** on 2026-10-09: no ([BR-17](#business-rules-and-invariants), ADR pending: plan lifecycle publishing stability and drop reasons). | FR-23 |
| Q-10 | CSV import: the column format. (Whether a file with invalid rows imports its valid rows was **resolved** on 2026-10-10: it imports nothing, and rows name their customer by address — ADR pending: CSV order import.) | FR-10 |
| Q-11 | Capacity units (kilograms? litres or cubic metres?) and what happens when an order has no volume. | FR-05, FR-09 — ADR pending: capacity weight and volume |
| Q-12 | ~~Role permission matrix: which roles may manage fleet, customers and orders, solve, edit, publish, re-optimize.~~ **Resolved** on 2026-10-11: an Admin does everything a Dispatcher does, plus the organization and its users; a Dispatcher runs every operation and reads users; a Driver works their own route only (the matrix under [FR-03](#organization-users-and-roles), ADR pending: authorize every web handler by role). | FR-03 |
| Q-13 | ~~Tenant-isolation mechanism: application-level filtering, PostgreSQL row-level security, or both.~~ **Resolved** on 2026-10-10: application-level filtering, with keys that carry the organization and no row-level security (ADR pending: tenant isolation by filtering and tenant keys). | NFR-02 — ADR pending: multi-tenancy by organization |
| Q-14 | Where proof-of-delivery files are stored; retention periods for pings, photos and signatures; the legal basis for tracking drivers' locations. | FR-30, FR-31, NFR-19 |
| Q-15 | When location reporting starts and stops, and how often it pings (freshness versus battery). What the app does when the driver refuses, limits or later revokes background location permission, and whether the dispatcher is told that the driver's location is unavailable. | FR-31 |
| Q-16 | How the native driver app reaches the pilot's phones (app stores or internal distribution), and which tool runs end-to-end tests on native builds. How background location reporting (FR-31) is verified on real iOS and Android phones: automated, or a scripted device checklist run before each pilot build. | FR-31, NFR-15 — [ADR-003](../ADRs/ADR-003-dispatcher-console-and-driver-app.md) |
| Q-17 | Can the DispatchFlow solver, written in Python, reach enough ALNS iterations within the 30-second budget to compete? If not, what changes? (Evidence of 2026-10-10: about 1,800 iterations in 30 s on a 200-stop day, after constant-time insertion checks; see [optimization-engine.md](../architecture/optimization-engine.md). Whether that competes is for the benchmarks.) | FR-42, NFR-07, NFR-25 — [ADR-002](../ADRs/ADR-002-core-backend-and-separate-optimization-service.md) |
| Q-18 | When to adopt PostGIS — which first feature needs spatial queries. | [ADR-007](../ADRs/ADR-007-postgresql-with-flyway-migrations.md) |
| Q-19 | ~~Which reasons the system can give for a dropped order, and how they are determined.~~ **Resolved** on 2026-10-09 by ADR pending: plan lifecycle publishing stability and drop reasons. | FR-18 |
| Q-20 | Time-of-day multipliers: which time buckets, where they apply (matrix or ETA calculation), and how they and the ETA buffer are calibrated from actual arrivals. | ADR pending: no live traffic |
| Q-21 | Default solve time limit, and who may change it. | FR-19 |
| Q-22 | Which Gehring-Homberger instance sizes run in CI and which only in the offline study. | FR-43 |
| Q-23 | Re-optimization may move a stop to a vehicle that is not carrying its goods. For now goods are assumed transferable (ADR pending: validator and reoptimization problem conventions). Should a vehicle that takes a moved or new order visit a depot first, and how are such reloads modelled in both solvers and the validator? | FR-07, FR-11, FR-35 |
| Q-24 | ~~Should a day with a vehicle withdrawn mid-route be compared with its baseline, and on what?~~ **Resolved** on 2026-10-10: it is compared on kilometres and on-time, without driver-hours, and the comparison names the withdrawn vehicles (FR-39). | FR-39 |
| Q-25 | ~~Must geocoded coordinates copied onto customers and depots expire with the geocode cache, and how?~~ **Resolved** on 2026-10-10: it is settled with the provider choice. The chosen provider's terms must allow keeping the copies; if Google stays, they are refreshed on a schedule. That condition now belongs to Q-02, and nothing changes in code until Q-02 is decided. | NFR-17, FR-12, FR-13 |
| Q-26 | ~~May the last Admin of an organization be demoted?~~ **Resolved** on 2026-10-10: no ([BR-19](#business-rules-and-invariants)). | FR-01, FR-02 |
| Q-27 | ~~When a Driver's role changes, what happens to their driver record in fleet?~~ **Resolved** on 2026-10-10 by ADR pending: fleet checks the driver role when it reads: fleet stops counting the record while the user is not a Driver, and another driver may take its van. | FR-02, FR-06 |
| Q-28 | ~~Should publishing also catch a shift, capacity or depot change made after the solve?~~ **Resolved** on 2026-10-10 by ADR pending: recheck every route when a plan is published: every route is re-checked through `evaluate` against the current fleet, a broken rule refuses publishing, and the re-checked times are published when nothing breaks ([BR-21](#business-rules-and-invariants)). | BR-05, BR-06, BR-20 |
| Q-29 | The parameters of [NFR-24](#nfr-24): the value of N, meaning how close to OR-Tools the DispatchFlow solver must come on static instances; the time limit both solvers get; the number of runs *k* per solver and instance; and the drop penalty and fixed-cost setting of the shared objective in the study, since the Solomon instances carry no priorities to derive a penalty from. To be agreed with the academic advisor before the study. | NFR-24 |
| Q-30 | The parameters of [NFR-25](#nfr-25): the value of T, the time limit within which one new order is inserted into a published plan; the set of shorter and longer limits both solvers are also run at; and how the study's insertions are sampled (which plans, which new orders, how many). Q-17's evidence bears on what is reachable. To be agreed with the academic advisor before the study. | NFR-25, FR-46 |

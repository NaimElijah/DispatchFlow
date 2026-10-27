# DeliOpt — Implementation Plan

Oct 9, 2026 · @Naim Elijah

Revised Oct 27, 2026 for the solver strategy: OR-Tools first, with our own solver as a measured research track ([ADR-005](../ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md)).

## Overview

DeliOpt is a web platform that plans each day's delivery routes for fleets of roughly 2–15 vehicles: pharmacies, florists, bakeries, local grocers, small courier companies. A dispatcher loads the day's orders and the system decides which vehicle takes which orders and in what order, so every delivery lands inside its time window, no vehicle is overloaded, and total driving time is as low as possible. That is the Vehicle Routing Problem with Time Windows (VRPTW).

Four components:

1. **Dispatcher web app** (React) — import orders, see routes on a map, adjust them manually, publish to drivers.
2. **Core backend** (Spring Boot + PostgreSQL) — orders, fleets, users, plans, statuses, authentication.
3. **Optimization service** (Python) — takes stops, time windows, capacities and a travel-time matrix, returns routes. Every solver sits behind one solver interface: Google OR-Tools, which the product runs on, and our own metaheuristic. Travel times come from a self-hosted OSRM engine on Israeli OpenStreetMap data.
4. **Driver app** (installable mobile web app) — ordered stop list, deep link to Waze, delivered/failed status, proof of delivery.

The academically interesting core is the optimization, and we build both solvers. OR-Tools is the solver the product runs on, so we always have a working system. Our own metaheuristic, Clarke-Wright savings plus ALNS, sits behind the same interface as a separate, measured part of the project. We aim it at live re-optimization, where it can plausibly win, and benchmark it honestly against OR-Tools and PyVRP.

## Customer and problem

Securing one real business as a pilot customer before the approval meeting is the single highest-value thing we can do — advisors weigh this heavily.

Today these businesses plan routes by hand or in their heads, or use enterprise tools that are too expensive and too complex for them. The pain: late deliveries, wasted fuel and driver hours, no visibility into where drivers are. The pitch is enterprise-grade route optimization sized and priced for a five-van business.

Fallback if we cannot land a customer in time: a synthetic order generator built on real Israeli addresses, plus the academic benchmarks. We state this explicitly as a risk mitigation rather than leaving it unanswered.

## Architecture

&#91;embedded content: DeliOpt architecture · 7 components\]

Solving takes 5 to 60 seconds, so it runs as an asynchronous job: the backend creates a job record, the solver picks it up, and the frontend is notified when the solution comes back. A job table in PostgreSQL is enough for a queue at this scale; RabbitMQ is an option if we want to show it.

OSRM is self-hosted in Docker on the Geofabrik Israel extract. The backend uses it too, for the road geometry drawn on the map and for recalculating ETAs after a manual edit. Geocoding sits behind an adapter interface with results cached in the database; the dispatcher can drag a pin to fix a bad match, and the correction is saved per customer.

Everything runs under Docker Compose and deploys to a single cloud VM. GitHub Actions runs tests and builds on every pull request.

## Data model

Every entity belongs to an organization, which is what makes the system multi-tenant.

| Entity | Key fields |
| --- | --- |
| Organization | Tenant root for every other entity |
| User | Role: Admin, Dispatcher, Driver |
| Depot | Location |
| Vehicle | Capacity (units or weight), start depot, end depot |
| Driver | Shift start, shift end, assigned vehicle |
| Customer | Address, coordinates, default time window |
| Order | Customer, date, time window, demand, service duration, priority, status |
| Plan | Date, status: draft, solving, ready, published, completed |
| Route | Plan, vehicle, driver, totals |
| Stop | Route, order, sequence, planned ETA, actual arrival, status, failure reason |
| SolveJob | Parameters, status, timings, solver used, objective value |
| LocationPing | Driver, time, coordinates |
| DeliveryProof | Photo or signature, note |

## Optimization engine

This is the heart of the project and where most of the technical credibility sits.

**Solver interface (strategy pattern).** Inside the optimization service, every solver implements one interface, `solve(problem) → solution`, and is chosen by name for each solve. OR-Tools is the default, and our own solver is the second. The backend, the dispatcher app and the driver app know a solver only by its name. Every solution goes through the feasibility validator and is scored by one shared objective, whichever solver produced it, so the solvers are compared on equal terms ([ADR-004](../ADRs/ADR-004-pluggable-solver-port.md)).

**Modeling in OR-Tools.** OR-Tools is the solver the product runs on. The routing model is built on the travel-time matrix from OSRM. A time dimension with slack models waiting, and each stop's time window is a constraint on that dimension. Service time is added to transit time at each stop. A capacity dimension enforces vehicle capacities. Each vehicle's start and end times come from the driver's shift. Each order gets a drop penalty proportional to its priority, so when not everything fits the solver skips orders instead of failing — and the system reports which orders were dropped and why.

**Search strategy.** An initial solution from a construction heuristic (cheapest arc), improved by Guided Local Search under a configurable time limit, so the dispatcher gets a good answer in bounded time.

**Objective.** Minimize total travel time plus penalties for dropped orders. An optional fixed cost per vehicle lets the solver also reduce fleet usage.

**Feasibility validator.** An independent module that does not trust the solver. It re-checks every constraint on any solution: time windows, capacities, shifts, and that each order is visited at most once. Used in tests and after every solve. Worth presenting explicitly — it shows we treat the solver as something to verify, not believe.

**Our own solver (Must, Phase 2).** Clarke-Wright savings construction improved by Adaptive Large Neighborhood Search, with destroy and repair operators and simulated-annealing acceptance, behind the same interface. Naim Elijah owns this track. It is a separate, measured part of the project, and the product never depends on it.

**Where we aim it.** We do not aim it at beating OR-Tools on static benchmarks. OR-Tools' routing solver is mature (guided local search, many neighborhood operators, years of tuning), and we are unlikely to beat it there in one academic year. We aim it at live re-optimization instead. When a new order arrives or a driver runs late, we expect our solver to repair the current plan within a short time limit instead of solving from scratch, which suits ALNS's destroy-and-repair design. OR-Tools can also start from the current routes, so we measure this rather than assume we win. Customer-specific constraints such as route stability are a second possible edge.

**Benchmarking, honestly.**

- **Instances:** the Solomon instances (56 instances, 100 customers, classes C1/C2/R1/R2/RC1/RC2), Gehring-Homberger instances, and the customer's real days.
- **Equal terms:** every solver gets the same time limit on the same hardware and runs alone on it, and every result is scored by the shared objective. Each run records its seed, how far its search got and the solver versions.
- **Reference:** PyVRP, an open-source state-of-the-art solver, is a tougher reference point on the static instances.
- **Re-optimization:** OR-Tools is warm-started from the current plan too, and both solvers run at the same set of short time limits. Both run until their limit, so "faster" means reaching the same quality at a shorter limit.
- **Measures:** the gap from published best-known solutions, the gap from OR-Tools, the solve time, and for re-optimization each solver's objective at each limit.
- **CI regression gate:** fixed seeds and a fixed search budget instead of a time limit, with quality thresholds, so the gate gives the same answer on every run. The equal-time study runs offline.

**Success criteria, fixed in advance.** The requirements state two measurable targets, each with its protocol (NFR-24 and NFR-25 in the requirements):

- on the Solomon instances, solution quality within N% of OR-Tools under the same time limit;
- for a single-order insertion into a plan of up to 200 stops and 15 vehicles, a validated solution that serves the new order within a T-second limit and is no worse than OR-Tools warm-started under the same limit.

We agree the values of N and T with the advisor. The results are reported whatever they are: "within X% of OR-Tools on static instances; on incremental updates, ours at 1 s matches OR-Tools at Y s" is a good result even if we lose on some measures.

**Real-world baseline.** Compare optimized routes against a naive nearest-neighbor ordering, and against the pilot customer's actual manual routes if we have one.

## Manual editing and re-optimization

**Manual edits.** The dispatcher drags a stop to another route or position. The backend recalculates ETAs through OSRM and immediately flags any time-window or capacity violation. The solver is not called for this — it stays instant.

**Mid-day re-optimization (Must).** New orders arrive or a driver runs late, and the system re-plans only the remaining stops. Completed and in-progress stops are locked, and each vehicle's current location becomes its new start point. OR-Tools re-optimizes warm-started from the current routes, while our own solver repairs the current plan. That comparison is what our research track is aimed at.

## Driver app

A mobile-first installable web app, not a native app:

- Today's route in order
- Customer details and notes
- A Navigate button that deep-links to Waze or Google Maps
- Arrived, delivered and failed buttons, with a reason required on failure
- Proof of delivery: photo or signature
- Location pings while the app is open

Status updates flow to the dispatcher's map in real time over WebSocket or server-sent events.

## Technology stack

| Layer | Choice | Why |
| --- | --- | --- |
| Frontend | React + TypeScript, Leaflet or MapLibre, installable web app | One codebase for dispatcher and driver views |
| Backend | Java 21, Spring Boot, Spring Security, JPA | Team familiarity, mature ecosystem |
| Solver | Python, FastAPI; Google OR-Tools and our own ALNS behind one solver interface | OR-Tools: the mature open-source VRP solver the product runs on. ALNS: our research component |
| Benchmark reference | PyVRP, used by the benchmark harness only | State-of-the-art open-source reference point |
| Routing data | OSRM + OpenStreetMap Israel extract | Free, self-hosted, fast matrices, no quotas |
| Geocoding | Google Geocoding API behind an adapter, cached in DB | Handles Hebrew addresses where free options fail |
| Database | PostgreSQL, PostGIS optional | Relational data plus geo queries |
| Infrastructure | Docker Compose, GitHub Actions, one cloud VM | Simple and reproducible |
| Testing | JUnit, Testcontainers, pytest + Hypothesis, Playwright, k6 | See the testing section |

Why a separate Python solver: OR-Tools' Python ecosystem and documentation are the richest, algorithm work iterates independently of the backend, and one solver interface inside the service lets us run OR-Tools and our own solver side by side and choose one for each solve. That strategy pattern is a good design point to raise with the advisor. The cost is working in two languages, and we should say we considered it rather than wait to be asked.

## Scope

Prioritized so the project is clearly the right size, and so scope creep has a written answer.

**Phase 1: OR-Tools first.** A dispatcher's day runs end to end on OR-Tools: orders come in, they are solved, validated routes appear on the map, and the plan is published. Nothing in Phase 1 waits for our solver.

**Phase 2: our own solver.** Its owner, Naim Elijah, builds it behind the same interface. It may start while Phase 1 is under way, but it is never on Phase 1's critical path.

**Must**

- Organization and user management with roles
- Vehicle and driver setup
- Order entry by form and CSV import
- Geocoding with manual correction
- VRPTW solving on OR-Tools with capacities, shifts and dropped-order reporting
- Map view of routes
- Publishing routes to drivers
- Driver app with statuses
- Daily summary
- Mid-day re-optimization
- Our own ALNS solver with the benchmark study (Phase 2)

**Should**

- Manual drag-and-drop editing with live violation checks
- Proof of delivery
- Live driver location
- PyVRP as a benchmark reference

**Could**

- Customer SMS or email with ETA
- Analytics dashboard: on-time percentage, kilometers saved
- Multiple depots

**Won't (explicitly out of scope)**

- Live traffic data
- Native mobile apps
- Payments
- Marketplace or crowdsourced drivers
- Beating OR-Tools on static benchmark instances (we measure the gap instead)
- A product that depends on our own solver

## Testing and evaluation

**Unit tests.** Backend logic in JUnit, solver model building in pytest.

**Solver correctness.** The feasibility validator runs on every solution. Property-based tests with Hypothesis generate random instances and assert that every solution is feasible. Small hand-crafted instances with known optimal answers cover the rest.

**Integration tests.** Testcontainers spins up real PostgreSQL and OSRM and tests the full path from orders to published routes.

**End-to-end tests.** Playwright covers the dispatcher and driver flows.

**Performance tests.** Solve time and solution quality at 25, 50, 100 and 200 stops. Re-optimization quality at each short time limit for each solver, OR-Tools warm-started against ours. k6 load-tests the API.

**Evaluation metrics.** Total driving time and distance against the baseline; vehicles used; percentage of deliveries inside their windows; solve time; gap from best-known solutions on the Solomon instances; gap from OR-Tools and PyVRP under equal time limits; re-optimization quality at each time limit, and route stability, for each solver.

**Acceptance.** A pilot with the customer, with their feedback collected and written up.

## Risks and mitigations

This is what the seminar email means by knowing how to deal with the project's challenges. Have these ready in the meeting.

| Risk | Mitigation |
| --- | --- |
| Solver too slow or poor quality | The product runs on OR-Tools within a configurable time limit; early benchmarking |
| Our solver underperforms OR-Tools | The product never depends on it. It is a measured research track with success criteria fixed in advance, and its results are reported honestly |
| Research track slips | One owner (Naim Elijah); nothing in Phase 1 waits on it |
| Unfair solver comparison | Same time limits and hardware, one shared objective and validator, and OR-Tools warm-started for re-optimization too |
| Poor geocoding of Hebrew addresses | Google API, DB caching, manual pin correction saved per customer |
| No live traffic, so ETAs drift | Time-of-day travel-time multipliers, buffer calibrated against actual arrivals; stated openly as a known limitation |
| No real customer | Pursue one now; fall back on synthetic Israeli data plus benchmarks |
| Background location limited on mobile web, especially iOS | Track only while the app is open; use delivery status updates as a location proxy |
| Two-language stack | A clear API contract between backend and solver, defined early as an OpenAPI spec |
| Scope creep | The Must/Should/Could/Won't list, reviewed at each milestone |
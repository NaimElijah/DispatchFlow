# DeliOpt — Implementation Plan

Oct 9, 2026 · @Naim Elijah

## Overview

DeliOpt is a web platform that plans each day's delivery routes for fleets of roughly 2–15 vehicles: pharmacies, florists, bakeries, local grocers, small courier companies. A dispatcher loads the day's orders and the system decides which vehicle takes which orders and in what order, so every delivery lands inside its time window, no vehicle is overloaded, and total driving time is as low as possible. That is the Vehicle Routing Problem with Time Windows (VRPTW).

Four components:

1. **Dispatcher web app** (React) — import orders, see routes on a map, adjust them manually, publish to drivers.
2. **Core backend** (Spring Boot + PostgreSQL) — orders, fleets, users, plans, statuses, authentication.
3. **Optimization service** (Python + Google OR-Tools) — takes stops, time windows, capacities and a travel-time matrix, returns routes. Travel times come from a self-hosted OSRM engine on Israeli OpenStreetMap data.
4. **Driver app** (installable mobile web app) — ordered stop list, deep link to Waze, delivered/failed status, proof of delivery.

The academically interesting core is the optimization: OR-Tools as a solid baseline, optionally our own metaheuristic, both benchmarked on the standard Solomon VRPTW instances.

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

**Modeling in OR-Tools.** The routing model is built on the travel-time matrix from OSRM. A time dimension with slack models waiting, and each stop's time window is a constraint on that dimension. Service time is added to transit time at each stop. A capacity dimension enforces vehicle capacities. Each vehicle's start and end times come from the driver's shift. Each order gets a drop penalty proportional to its priority, so when not everything fits the solver skips orders instead of failing — and the system reports which orders were dropped and why.

**Search strategy.** An initial solution from a construction heuristic (cheapest arc), improved by Guided Local Search under a configurable time limit, so the dispatcher gets a good answer in bounded time.

**Objective.** Minimize total travel time plus penalties for dropped orders. An optional fixed cost per vehicle lets the solver also reduce fleet usage.

**Feasibility validator.** An independent module that does not trust the solver. It re-checks every constraint on any solution: time windows, capacities, shifts, and that each order is visited at most once. Used in tests and after every solve. Worth presenting explicitly — it shows we treat the solver as something to verify, not believe.

**Academic depth (Should).** Implement my own metaheuristic — Adaptive Large Neighborhood Search with destroy and repair operators — and benchmark it against OR-Tools on the Solomon instances (56 instances, 100 customers, classes C1/C2/R1/R2/RC1/RC2). Measure gap from published best-known solutions and solve time. Research component without making the product depend on it.

**Real-world baseline.** Compare optimized routes against a naive nearest-neighbor ordering, and against the pilot customer's actual manual routes if we have one.

## Manual editing and re-optimization

**Manual edits.** The dispatcher drags a stop to another route or position. The backend recalculates ETAs through OSRM and immediately flags any time-window or capacity violation. The solver is not called for this — it stays instant.

**Mid-day re-optimization (Could).** New orders arrive or a driver runs late, and the system re-solves only the remaining stops. Completed and in-progress stops are locked, and each vehicle's current location becomes its new start point.

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
| Solver | Python, FastAPI, Google OR-Tools | Best-in-class open-source VRP solver |
| Routing data | OSRM + OpenStreetMap Israel extract | Free, self-hosted, fast matrices, no quotas |
| Geocoding | Google Geocoding API behind an adapter, cached in DB | Handles Hebrew addresses where free options fail |
| Database | PostgreSQL, PostGIS optional | Relational data plus geo queries |
| Infrastructure | Docker Compose, GitHub Actions, one cloud VM | Simple and reproducible |
| Testing | JUnit, Testcontainers, pytest + Hypothesis, Playwright, k6 | See the testing section |

Why a separate Python solver: OR-Tools' Python ecosystem and documentation are the richest, algorithm work iterates independently of the backend, and the service boundary lets us swap solvers later — OR-Tools versus our own. That swappability is a good design point to raise with the advisor. The cost is working in two languages, and we should say we considered it rather than wait to be asked.

## Scope

Prioritized so the project is clearly the right size, and so scope creep has a written answer.

**Must**

- Organization and user management with roles
- Vehicle and driver setup
- Order entry by form and CSV import
- Geocoding with manual correction
- VRPTW solving with capacities, shifts and dropped-order reporting
- Map view of routes
- Publishing routes to drivers
- Driver app with statuses
- Daily summary

**Should**

- Manual drag-and-drop editing with live violation checks
- Proof of delivery
- Live driver location
- Our own ALNS solver with the benchmark study

**Could**

- Mid-day re-optimization
- Customer SMS or email with ETA
- Analytics dashboard: on-time percentage, kilometers saved
- Multiple depots

**Won't (explicitly out of scope)**

- Live traffic data
- Native mobile apps
- Payments
- Marketplace or crowdsourced drivers

## Testing and evaluation

**Unit tests.** Backend logic in JUnit, solver model building in pytest.

**Solver correctness.** The feasibility validator runs on every solution. Property-based tests with Hypothesis generate random instances and assert that every solution is feasible. Small hand-crafted instances with known optimal answers cover the rest.

**Integration tests.** Testcontainers spins up real PostgreSQL and OSRM and tests the full path from orders to published routes.

**End-to-end tests.** Playwright covers the dispatcher and driver flows.

**Performance tests.** Solve time and solution quality at 25, 50, 100 and 200 stops. k6 load-tests the API.

**Evaluation metrics.** Total driving time and distance against the baseline; vehicles used; percentage of deliveries inside their windows; solve time; gap from best-known solutions on the Solomon instances.

**Acceptance.** A pilot with the customer, with their feedback collected and written up.

## Risks and mitigations

This is what the seminar email means by knowing how to deal with the project's challenges. Have these ready in the meeting.

| Risk | Mitigation |
| --- | --- |
| Solver too slow or poor quality | OR-Tools baseline, configurable time limits, Early benchmarking |
| Poor geocoding of Hebrew addresses | Google API, DB caching, manual pin correction saved per customer |
| No live traffic, so ETAs drift | Time-of-day travel-time multipliers, buffer calibrated against actual arrivals; stated openly as a known limitation |
| No real customer | Pursue one now; fall back on synthetic Israeli data plus benchmarks |
| Background location limited on mobile web, especially iOS | Track only while the app is open; use delivery status updates as a location proxy |
| Two-language stack | A clear API contract between backend and solver, defined early as an OpenAPI spec |
| Scope creep | The Must/Should/Could/Won't list, reviewed at each milestone |
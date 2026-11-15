# DispatchFlow — Roadmap

**Updated:** 14 November 2026. Dates move; this file moves with them.

When each part of DispatchFlow is built, and what must be done by each date. What the system must do is
in the [requirements](requirements/requirements.md); the domain model is in the
[domain document](architecture/domain_layer/domain_layer_design.md); the tools, and when each is adopted,
are in the [tech stack](architecture/tech_stack.md); why each decision was taken is in the
[ADRs](ADRs/README.md). This file repeats none of them.

## Contents

1. [The course calendar](#1-the-course-calendar)
2. [Milestones](#2-milestones)
3. [Checkpoints](#3-checkpoints)
4. [The Alpha](#4-the-alpha)
5. [Waiting on people outside the team](#5-waiting-on-people-outside-the-team)

---

## 1. The course calendar

The course sets the dates, and every milestone is placed against them (course syllabus, Semester A,
2026–27).

| Date | What the course asks | What must exist by then |
|---|---|---|
| Sun 15 Nov 2026 | ARD submitted, by the end of the day | The requirements |
| Tue 1 Dec | Oral exam on the ARD, graded per student (15%). One or two slides per topic: the Alpha version, set with the mentor at this meeting; each challenge and how the project solves it; a Gantt chart to the end of the project, detailed to the end of the semester | The walking skeleton running on every laptop; the Alpha definition ([4.2](#42-alpha-definition)); the Gantt chart from the milestones in [2](#2-milestones) |
| Before 29 Dec | A meeting where each team declares its goals for the hackathon | The demo script ([4.3](#43-demo-script)) as the stated goal |
| Tue 29 Dec | Hackathon: a working Alpha shown at the end of the day, even if not fully tested (10%) | The demo script runs end to end |
| Tue 5 Jan 2027 | Iteration 1, by the end of the day: the ADD; the full Alpha demo; a usability-study plan that walks through every screen, with a questionnaire, and the main flows run to confirm the menus | The ADD, assembled from the domain document, the tech stack, the ADRs and this roadmap; the study plan |
| Tue 12 Jan | Meeting with the mentor: project status and the plan for Semester B; Alpha demo (40%); oral exam on iteration 1 (15%); the customer's impression (20%) | The demo, shown to the customer; the Q-63 review ([3](#3-checkpoints)) |
| Semester B | Live operations, the phone app, messages, the own solver, the research study, the pilot | M6 to M13 |
| June 2027 | End of the project | — |

The Alpha is graded as a **demo**, so its "done" is a script that runs in front of people, not only a
test suite. It must work by **29 December**: about six and a half weeks for four people who also have
other courses, an oral exam and the ADD. Attendance at the seminar meetings and the hackathon is
mandatory, and missing them costs points.

---

## 2. Milestones

Each milestone ends with something that runs, and a check that proves it. M0 to M5 make the **Alpha**.
M6 to M13 make the **pilot**, in Semester B.

The domain layer of every context, Java and Python, is built ahead of its milestone, from November
2026. The application layer is declared ahead too, as signatures whose bodies throw (ADR-119). Each
milestone then fills its context's application bodies and builds its adapters on that domain.

### 2.1 Lanes

Four people, four lanes. A lane has one owner, and the others help. The team fills in the names.

| Lane | Alpha | Semester B |
|---|---|---|
| **Optimizer** | M0's optimizer, M1 | M11 (own solver), M9's `ProblemFactory`, the research harness |
| **Core** | M0's core, M2, and the M4 and M5 back ends | M6, M8, M9 |
| **Front end** | M0's map, the M4 workspace, M5's driver screens and summary | M10 (phone app), the live screens |
| **Integration** | M0's tooling and CI, M3, the demo script and the ADD | M7, M12, M13 |

### 2.2 Alpha track

| # | Milestone | Dates | What gets built | Done when |
|---|---|---|---|---|
| M0 | Walking skeleton | 12–22 Nov | The restructure to the layout of ADR-037, with `mise`, `justfile` and `compose.dev.yaml`; both contracts with one endpoint each; the core with a Planning module, Flyway V1 through the `migrate` step, and jOOQ code generation; the optimizer with an OR-Tools adapter and a validator for capacity, windows and shifts; `just seed`; a web page that shows the solved routes on a MapLibre map; CI with unit tests and `ApplicationModules.verify()`. The tooling details deferred to this PR are settled: the Node major (ADR-038), the ESLint majors, Prettier's settings and Renovate's pins (ADR-039), and `TestDispatchFlowApplication` and `just core-run` (ADR-042). The web app is checked to render under the CSP (ADR-069) | On all four laptops: `just dev`, click *Solve* on a seeded day, and routes appear on the map; `just check` passes locally and in CI |
| M0–M1 | The app-wide `web` package | before the first web adapter merges | Its own ADR, once the generator's package layout is visible (ADR-040) | The ADR is accepted |
| M1 | Optimizer for daily solves | 16 Nov–6 Dec | `Problem` with `validate` for every daily rule, `cost` and `explain_drop`; `ProblemFactory.daily`, `Solution`, `TravelTimeModel`, `PenaltyScale`, `nearest_neighbour`; `/v1/evaluate` and `/v1/baseline`. The re-optimization conventions wait for M9 | Accepts best-known Solomon solutions; rejects the crafted violation catalogue, naming each rule; a 200-stop synthetic day solves inside its limit. The demo day is solved both at the upper bound and at per-bucket times, and the result goes to ADR-048's trigger |
| M2 | Records and CSV import | 23 Nov–6 Dec | Fleet, Customers & Orders, per-date rosters; UTF-8 CSV import with upload and confirm, all or nothing, address matching (ADR-086) | The import tests: all or nothing, every row error listed together, address matching |
| M3 | Travel times | 23–29 Nov | The OSRM adapter, the matrix builder with deduplicated locations, unreachable points, route shapes | A 216-point matrix in under 5 s on a laptop (NFR-57); unreachable points detected; OSRM's memory measured against the 16 GB VM (ADR-078) |
| M4 | Solve jobs and the planning workspace | 30 Nov–13 Dec | The `solve_job` queue, one worker per lane (ADR-051), the queue watchdog, event resubmission; progress over the event stream; map, timeline and table with one shared selection | A solve requested in the browser finishes without a reload; a core killed mid-solve leaves the plan *draft* or *ready* |
| M5 | Publish, drive, report | 7–20 Dec | Publishing checks in BR-72's order, plan revisions (ADR-059); driver screens in the browser, online only, with a signature as proof; `Delivery` and `RouteRun`; live status on the dispatcher's screen; the daily summary and the baseline comparison | The demo script ([4.3](#43-demo-script)) runs end to end, under Playwright in CI and by hand. The CI minutes are decided from the real run times ([TS §22](architecture/tech_stack.md#22-open-decisions)) |
| — | Freeze | 21–28 Dec | Bug fixes only; the demo rehearsed twice; the ADD drafted | A full rehearsal on a clean laptop |
| — | Hackathon | 29 Dec | The Alpha, shown | — |
| — | Iteration 1 | 30 Dec–5 Jan | The ADD, the usability-study plan | Submitted |
| — | January break | after 12 Jan | The tools [TS §20](architecture/tech_stack.md#20-adoption-order) adopts in January | Each one runs in CI |

### 2.3 Semester B track

Sized in weeks. The dates go in once the Semester B calendar is known.

| # | Milestone | Weeks | What gets built | Done when |
|---|---|---|---|---|
| M6 | Access in full | 2 | Sessions, refresh rotation, revocation and the stream's heartbeat re-check (ADR-045), links, rate limits (ADR-071), step-up for Admin operations | Every Access row has a passing API test; the permission-matrix test is generated |
| M7 | Imports in full and geocoding | 2 | Excel import, and Windows-1255 with confirmation; GovMap behind the `Geocoder` port (ADR-085), its cache, retries and flags | GovMap's terms read and its token in hand before the code starts; the accuracy test run on GovMap, and its results set FR-63's rules; the full import suite, encodings included |
| M8 | Live operations | 3 | Idempotent reports (ADR-087), clock correction, positions, late-outcome reconciliation and the hold for backdated reports (BR-132), arrival projection (ADR-067), at-risk stops, alerts | A replayed report stream gives the same state twice; the live map updates within 5 s |
| M9 | Re-optimization | 3 | The `ProblemFactory` conventions (ADR-050), warm start, immutable proposals, `Plan.accept`; hand-over, cancel and reschedule | UC-10 and UC-11 pass end to end; NFR-07 measured at the 95th percentile |
| M10 | Phone app | 4 | The Expo app, the encrypted store, the outbox, background location, push, the route-change diff, the native Admin and Dispatcher screens (ADR-035) | The device checklist passes on one Android phone and one iPhone |
| M11 | DispatchFlow solver | from January, research lane | ALNS behind the `Solver` port, with the `alns` package's outer loop (ADR-092), repair mode, Numba kernels; the harness and the simulation | The advisor confirms Q-93's answers, NFR-117's 1% margin and the `alns` loop; mutmut runs over `Problem.validate` before the first measured run (ADR-091); property tests pass; deterministic across processes; a Solomon smoke run in CI |
| M12 | Messages to recipients | 2 | Triggers, the outgoing-message state machine, the provider adapter, the inbound webhook, the reply parser | The SMS vendor chosen against Q-96's checklist, with a signed data-processing agreement before real recipients get messages; UC-14 passes end to end against the fake gateway |
| M13 | Production and pilot | 3 | Terraform with the deploy, update and backup controls (ADR-078), Caddy, backups, observability, security scans | Legal advice (Q-42) before the pilot handles real data; a team member has read the Hours of Work and Rest Law (Q-39); restore drill under 4 h; ASVS Level 1 self-check passed |

M11 depends only on M1. M13 needs a partner company: its AWS account, store accounts, SMS contract and
data. If no company is confirmed by the start of M13, the pilot is staged (FR-407, FR-410), on the
team's accounts (ADR-080).

---

## 3. Checkpoints

- **1 December.** The walking skeleton is part of the Alpha definition presentation, with the Gantt
  chart: this file's Alpha milestones, and the Semester B ones in weeks.
- **12 January, the Q-63 review.** Compare the weeks M0 to M5 really took with the planned ones, and
  lower the Semester B Musts that no longer fit. FR-13, FR-21 and FR-47 stay Must.

---

## 4. The Alpha

### 4.1 What's in, what waits

| Area | In the Alpha | Waits for | Why it can wait |
|---|---|---|---|
| Orders | UTF-8 CSV import, upload then confirm | Excel and Windows-1255 (M7) | The demo file is ours; the encoding rules matter with real company files |
| Locations | Customers come from `just seed` with coordinates; the demo CSV names them by address, so the import matches them (FR-310) | Geocoding (M7) | Synthetic addresses are drawn from the OSM extract and already carry coordinates. A row with a new address creates a customer without coordinates, and its order drops as *missing location* (BR-14), which the demo can show |
| Optimizer | Daily rules, OR-Tools, nearest neighbour | Re-optimization conventions (M9), ALNS (M11) | Nothing in the Alpha re-optimizes |
| Planning | Solve, view, publish | Manual edits with undo (FR-145, a Should): in if M4 finishes early | The demo story doesn't need it |
| Drivers | Browser screens, online, with a signature as proof | Photo proofs, the offline outbox, the phone app (M10) | A driver's *delivered* needs a proof (FR-367), and a signature is the cheapest one that works with a mouse |
| Access | Seeded accounts, sign-in, roles | Sessions in full, links, rate limits, step-up (M6) | The Alpha runs on a laptop with synthetic data |
| Architecture checks | `ApplicationModules.verify()`; the ArchUnit purity rules (ADR-040) | Error Prone and NullAway (January) | Those checks catch most structural mistakes from the first commit |
| Contract checks | Code generated from both contracts (ADR-043) | Schemathesis, oasdiff (January) | Breaking changes matter once a phone app version is in someone's hands |
| End-to-end tests | One Playwright flow: the demo script, which runs UC-06 to UC-08's main flows | Their listed failures after the Alpha; UC-10 and UC-11 at M9; UC-14 at M12 (NFR-83) | — |

### 4.2 Alpha definition

The Alpha is a local, web-only DispatchFlow on synthetic data. A dispatcher imports a day's orders,
solves the day with OR-Tools, sees routes on a map, timeline and table, and publishes. A driver, in a
browser, sees only their own route and reports each stop, signing for each delivery. The dispatcher
watches the reports arrive and ends with a daily summary that compares the plan with a
nearest-neighbour baseline. Out of scope for the Alpha: geocoding, Excel imports, photos, the phone app,
re-optimization and recipient messages ([4.1](#41-whats-in-what-waits)). The mentor sets the final
scope on 1 December.

### 4.3 Demo script

The same script runs under Playwright in CI and by hand at the hackathon and on 12 January.

1. Sign in as a dispatcher. Open tomorrow's date.
2. Import a CSV of 200 synthetic orders for the seeded customers. One row has an error, and the preview
   names it. Fix it, and confirm.
3. Show the roster: 12 vehicles, 12 drivers, two depots.
4. Solve with OR-Tools, with a 45-second limit. The progress bar moves by itself.
5. Routes appear on the map, the timeline and the table. Select a stop in one, and it's selected in all
   three. Open the dropped list: each drop says why (FR-144).
6. Publish. The checks pass, and the plan becomes revision 1.
7. In a second window, sign in as a driver. The driver sees only their own route. Mark three stops:
   delivered with a signature, delivered with a signature, failed with a reason.
8. Back on the dispatcher's screen, the three stops have changed colour without a reload.
9. Open the daily summary: kilometres, driver-hours and the planned on-time percentage, against the
   nearest-neighbour baseline.

Step 9 is the line the room remembers ("12% fewer kilometres than the baseline"), so measure it on the
demo day's data before the hackathon, and keep that day's seed fixed.

---

## 5. Waiting on people outside the team

| Who | What | By |
|---|---|---|
| The academic advisor | Q-93's study design, NFR-117's 1% margin, and whether the `alns` loop counts as the team's solver (ADR-091, ADR-092) | Before the first measured run (M11) |
| A lawyer | Q-42's legal advice: retention (Q-14), consent (Q-38, Q-95), IS 5568's duties (Q-59), NFR-209's exceptions (ADR-077) | Before the pilot handles real data (M13) |
| The company | Q-01 (which company and what data), Q-62's two cost caps, its accounts and its dispatcher's check of the Hebrew terms (DL §17) | No fixed date; the staged pilot is the fallback |
| The team | Q-40's pilot length, shadow period and operations; Q-48's store review time, Apple's background-location rules and tester accounts | Before M13 |

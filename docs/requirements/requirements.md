# DispatchFlow — Requirements

What the system must do. For how it does it, see
[`../architecture/architecture.md`](../architecture/architecture.md); for why a given
solution was chosen, see [`../ADRs/`](../ADRs/README.md).

**Priorities** use MoSCoW: **Must**, **Should**, **Could**; **Won't** items are listed under
[Non-goals](#non-goals). **Size** is XS, S, M, L or XL. *Size* and *Enforced by* stay blank
until the team sizes and implements each requirement; *Enforced by* then names the test that
enforces it. Rows FR-52 to FR-122, BR-23 to BR-38, NFR-26 to NFR-52 and Q-31 to Q-73 came from a gap
analysis on 2026-11-02, as did the five non-goals from *Keep payroll* to *Let one user belong to
several organizations*. They are proposals until the team approves them under issue #3, and `(proposed)`
marks a priority the team has not confirmed. On 2026-11-03 the business rules were grouped by
subject and NFR-27, NFR-45 and NFR-46 moved to a new usability section, without changing their text,
except that BR-16 now names the rules it called "above".
Rows numbered FR-123, BR-39, NFR-53, Q-76 and UC-01 onward, and the non-goals marked
*(Proposed, 2026-11-03)*, came from a second review that day; they too are proposals under issue #3,
and that review added cross-references and questions to older rows. The business rules and
non-functional requirements among them state a proposed priority at the end of their text, because
those tables have no Priority column; older rows in those tables carry no priority.

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
The interface languages are Hebrew, laid out right to left, and English ([NFR-27](#nfr-27), proposed).

### Use cases

The main processes, each traced to the requirements it exercises. They anchor the usability study
([FR-48](#research-and-evaluation)) and the end-to-end tests ([NFR-83](#nfr-83)). They were added on
2026-11-03 and are proposals under issue #3.

| ID | Actor | Goal | Main flow | Alternatives and failures | Requirements |
|---|---|---|---|---|---|
| UC-01 | Anyone | Start using DispatchFlow | Sign up an organization; become its first Admin; set its settings | Sign-up closed or capped ([Q-41](#open-questions)) | FR-01, FR-125, FR-122 |
| UC-02 | Admin | Give people access | Create users with roles; they receive first credentials and sign in | A forgotten password is recovered; a leaver is deactivated; the only Admin cannot be demoted | FR-02, FR-03, FR-52, FR-53, FR-123, BR-19 |
| UC-03 | Dispatcher | Set up the fleet | Define depots; register vans and drivers; set a date's drivers, shifts and vans; retire what is no longer used | A van breaks or a driver falls sick during the day | FR-04 to FR-06, FR-54, FR-56, FR-79, FR-129 to FR-132 |
| UC-04 | Dispatcher | Keep customers current | Create or import customers; check geocoded pins; fix a bad pin by dragging it | No match, several matches, or the geocoder is down | FR-08, FR-12 to FR-14, FR-56, FR-59, FR-63, FR-64, FR-83, FR-134, FR-139, FR-140 |
| UC-05 | Dispatcher | Load the day's orders | Import a CSV or enter orders by form, or copy a previous day | A file with any rejected row imports nothing and lists every reason | FR-09, FR-10, FR-60 to FR-62, FR-84, FR-85, FR-138 |
| UC-06 | Dispatcher | Plan the day | Run the pre-flight check; solve; review routes on the map, timeline and table; edit by hand | Some orders are dropped with reasons; the solve fails and is requested again | FR-15 to FR-25, FR-65 to FR-67, FR-90, FR-91, FR-141 to FR-151 |
| UC-07 | Dispatcher | Publish the plan | Publish; every driver is alerted and sees their route | Publishing refuses over violations, changed vans or new orders | FR-26, FR-152, BR-17, BR-20 to BR-22 |
| UC-08 | Driver | Drive the route | Load the van from the loading list; start the route; navigate to each stop, calling the customer if needed; mark it delivered with proof, or failed with a reason; finish | No connection: reports queue and are sent later; a problem is reported to the dispatcher; a route change arrives mid-route | FR-27 to FR-32, FR-59, FR-68 to FR-72, FR-95 to FR-97, FR-153 to FR-156 |
| UC-09 | Dispatcher | Watch the day | Follow vehicles and stop statuses live; act on the alert list | A position is stale; a stop is at risk; a route has not started | FR-33, FR-34, FR-74, FR-101, FR-102, FR-157, FR-158 |
| UC-10 | Dispatcher | Recover from a disruption | Mark a van or driver unavailable, or add or change orders; re-optimize; review the preview; accept | The preview is declined, fails or is refused because something changed since | FR-07, FR-11, FR-35 to FR-37, FR-57, FR-75, FR-82, FR-103, FR-159, BR-25, BR-26 |
| UC-11 | Dispatcher | Change a published route without optimizing | Hand a route to another driver, or cancel an order | The order's stop was already reported | FR-55, FR-58, FR-73, BR-30, BR-47 |
| UC-12 | Dispatcher | Review the day | Read the daily summary and the baseline comparison; export it | Nothing delivered: on-time is shown as unavailable | FR-38 to FR-40, FR-106, FR-107, FR-161 |
| UC-13 | Team (research) | Compare the solvers | Run the benchmark harness and the disruption simulation from a configuration; analyse the results | A run the validator rejects is reported, as NFR-24 and NFR-25 say | FR-42 to FR-46, FR-51, FR-163 to FR-165, FR-167, FR-168, NFR-24, NFR-25, NFR-62 |

## Functional requirements

### Organization, users and roles

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-01 | An Admin can set up and manage their organization. Anyone can sign up a new organization, becoming its first Admin. Every record in DispatchFlow belongs to exactly one organization. (See also Q-41 and Q-61.) | Must |  |  |
| FR-02 | An Admin can create users and give each one a role: Admin, Dispatcher or Driver. (See also FR-53, Q-35 and Q-37.) | Must |  |  |
| FR-03 | A user can sign in and use only the functions their role permits, as the permission matrix below says. | Must |  |  |
| FR-52 | A user receives first credentials and can recover a forgotten password without help from the DispatchFlow team. These calls are exceptions to [NFR-01](#nfr-01), and recovery attempts are limited. (Identifier and channel: [Q-37](#open-questions). Attempt limit: [Q-73](#open-questions). ADR pending: sign-in and transport security) | Must (proposed) |  |  |
| FR-53 | An Admin can deactivate a user: sign-in stops, every session ends, and their history stays. An access token already issued can last the 15 minutes of [NFR-01](#nfr-01). The only active Admin cannot be deactivated ([BR-19](#business-rules-and-invariants)), a deactivated Admin does not count toward [BR-19](#business-rules-and-invariants), and a deactivated driver counts as no driver under [BR-20](#business-rules-and-invariants). (Reactivating a user: [Q-55](#open-questions).) | Should (proposed) |  |  |
| FR-77 | Users accept the terms and a privacy notice, and drivers see what the app collects about them. Acceptance is recorded and is never asked for during a route. (Who is responsible for the data and what the notice says: [Q-42](#open-questions).) | Should (proposed) |  |  |
| FR-78 | A platform operator, who belongs to no organization and holds none of the roles of [FR-03](#organization-users-and-roles), can suspend an organization. (What a suspension does, and who may see tenant data for support: [Q-61](#open-questions).) | Could (proposed) |  |  |
| FR-123 | A signed-in user can change their own password by giving the current one, and a wrong one counts toward the limit of [NFR-35](#nfr-35). Every session of that user ends, as [NFR-22](#nfr-22) requires, so they sign in again, and the password policy of [NFR-36](#nfr-36) applies. An access token already issued can last the 15 minutes of [NFR-01](#nfr-01) ([Q-89](#open-questions)). Level 1 of [NFR-40](#nfr-40)'s standard requires this capability. | Must (proposed) |  |  |
| FR-124 | A user can choose their interface language, Hebrew or English ([NFR-27](#nfr-27)). The choice follows them to every device they sign in on, and a new user starts with their organization's default ([FR-125](#organization-users-and-roles)). | Should (proposed) |  |  |
| FR-125 | An Admin can see and change the organization's settings in one place: name, dispatch phone ([FR-132](#fleet-depots-and-shifts)), default interface language, the time window a new customer starts with ([FR-08](#customers-and-orders)), departure allowance ([FR-67](#daily-planning)), fixed vehicle cost ([FR-21](#daily-planning)) and the default solve time limit within [NFR-06](#nfr-06). The service duration a new customer starts with is there too where [FR-89](#customers-and-orders) is built, and the cost per kilometre and per driver-hour where [FR-108](#reporting-and-baseline) is built. Each change is audited ([NFR-23](#nfr-23)). (Who may change the solve limit: [Q-21](#open-questions).) | Should (proposed) |  |  |
| FR-126 | An Admin can view the organization's audit records ([NFR-23](#nfr-23)) and filter them by user, operation, record and date. | Could (proposed) |  |  |
| FR-127 | A user can see their own active sessions, with device and last use, and end any of them. An Admin can end every session of a user without deactivating them ([FR-53](#organization-users-and-roles)). An access token already issued can last the 15 minutes of [NFR-01](#nfr-01) ([Q-89](#open-questions)). | Could (proposed) |  |  |
| FR-128 | An organization can require a second sign-in factor for its Admins. (Whether it is needed for the pilot, and which factor: [Q-81](#open-questions).) | Could (proposed) |  |  |

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
| Record a stop's outcome on a driver's behalf (FR-73, proposed) | ✓ | ✓ | |
| Deactivate a user (FR-53, proposed) | ✓ | | |
| Start and finish their own route, report a problem, confirm a route change (FR-68, FR-95, FR-97, proposed) | | | ✓ |
| View a stop's proof of delivery (FR-105, proposed) | ✓ | ✓ | Only on their own route |
| Change their own password and language; see and end their own sessions (FR-123, FR-124, FR-127, proposed) | ✓ | ✓ | ✓ |
| Read the organization's settings (FR-125, proposed) | ✓ | ✓ | Only its name and dispatch phone |
| Change the organization's settings, require a second factor, view audit records, end another user's sessions (FR-125 to FR-128, proposed) | ✓ | | |

### Fleet, depots and shifts

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-04 | A dispatcher can define depots, each with a location. (See also FR-129 and FR-130.) | Must |  |  |
| FR-05 | A dispatcher can register vehicles, each with a weight capacity, a volume capacity, a start depot and an end depot. (See also FR-131, FR-133 and FR-169.) | Must |  |  |
| FR-06 | A dispatcher can register drivers, each with a shift start, a shift end and an assigned vehicle. (See also FR-54 and FR-132.) | Must |  |  |
| FR-07 | A dispatcher can mark a vehicle unavailable during the day (for example after a breakdown), so that re-optimization ([FR-35](#live-operations-and-re-optimization)) redistributes its remaining stops. (See also Q-55.) | Must |  |  |
| FR-54 | A dispatcher can set, for a given date, which drivers work, their shift hours and which van each takes. The values registered in [FR-06](#fleet-depots-and-shifts) are the default. A shift ends after it starts and lies on its date. (On a date with an open plan: [Q-72](#open-questions).) | Must (proposed) |  |  |
| FR-55 | A dispatcher can hand a route of a published plan to another driver. The new driver sees it, the old one no longer does, that date's van assignment ([FR-54](#fleet-depots-and-shifts)) follows it, the stops stay as they are, and no optimization runs. (A new driver who already holds a route, or whose shift the route breaks: [Q-65](#open-questions). Editing a published plan at all: [Q-34](#open-questions).) | Must (proposed) |  |  |
| FR-56 | A dispatcher can see, change and retire depots, vehicles, drivers and customers. A retired record is not offered for new plans and stays on past ones ([BR-37](#business-rules-and-invariants)). (A record in use on an open plan, and other cases: [Q-72](#open-questions).) | Must (proposed) |  |  |
| FR-79 | A dispatcher can define a weekly shift template, which fills the per-date values of [FR-54](#fleet-depots-and-shifts) that a dispatcher has not set. | Should (proposed) |  |  |
| FR-80 | An organization has a working calendar that marks Israeli holidays and short Fridays, so a dispatcher setting a date's drivers and shifts ([FR-54](#fleet-depots-and-shifts)) can see them. | Could (proposed) |  |  |
| FR-81 | Routes reserve time for driver breaks, so that a break fits inside the shift. (Whether this is a legal duty, and the length and placement of breaks: [Q-39](#open-questions); if it is a duty, this becomes a Must.) | Should (proposed) |  |  |
| FR-82 | A dispatcher can mark a driver unavailable during the day (for example when they fall sick), so that re-optimization ([FR-35](#live-operations-and-re-optimization)) redistributes their remaining stops. A driver marked unavailable counts as no driver under [BR-20](#business-rules-and-invariants). | Should (proposed) |  |  |
| FR-129 | A depot can have opening hours. Routes leave it no earlier than it opens and return no later than it closes ([BR-46](#business-rules-and-invariants)). An order that cannot be served for that reason is dropped with the reason *depot hours*, and a manual edit that breaks them is flagged ([FR-23](#manual-editing)). (Loading bays and staggered departures: [Q-79](#open-questions).) | Should (proposed) |  |  |
| FR-130 | A dispatcher sets a depot's location by address, geocoded as a customer's is ([FR-12](#geocoding)), or by placing its pin on the map ([FR-14](#geocoding)). A depot without coordinates cannot start or end a route, so a solve leaves its vans out and names them. | Should (proposed) |  |  |
| FR-131 | A vehicle has a display name and a licence plate, each unique among its organization's active vehicles ([BR-45](#business-rules-and-invariants)). The name identifies the vehicle on the map, the timeline, the driver app and printouts. | Should (proposed) |  |  |
| FR-132 | A driver has a contact phone, which the console shows beside their route. A driver can call the organization's dispatch phone ([FR-125](#organization-users-and-roles)) from the app with one tap. This phone is never a recovery channel ([Q-37](#open-questions)). | Should (proposed) |  |  |
| FR-133 | A vehicle can have a maximum route duration, a maximum driven distance or a maximum number of stops, and solves and re-optimizations honour them. An order that cannot be served for that reason is dropped with the reason *vehicle limit*, and a manual edit that breaks one is flagged ([FR-23](#manual-editing)). (Whether any is needed: [Q-80](#open-questions).) | Could (proposed) |  |  |

### Customers and orders

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-08 | A dispatcher can create customers, each with an address, coordinates, a default time window, and notes that drivers see. (See also FR-134 and FR-135.) | Must |  |  |
| FR-09 | A dispatcher can enter an order through a form: customer, date, time window (defaulting to the customer's), weight and volume demand, service duration and priority (1 to 5). (See also FR-135, FR-136 and Q-43.) | Must |  |  |
| FR-10 | A dispatcher can import a day's orders from a CSV file. Every row is validated, and every rejected row is reported with its reason; a file with any rejected row imports nothing, and rows name their customer by address (ADR pending: CSV order import). (File format: [Q-10](#open-questions).) (See also FR-60, FR-61, FR-138 and Q-31.) | Must |  |  |
| FR-11 | A dispatcher can add orders to a day whose plan is already published; they enter the routes through re-optimization. | Must |  |  |
| FR-57 | A dispatcher can change an order's time window, demand, service duration, priority or date until its current stop is reported. On a published plan the change reaches routes through re-optimization. (On a ready plan or during a solve, after *arrived*, and a date change or a broken route on a published plan: [Q-68](#open-questions).) | Must (proposed) |  |  |
| FR-58 | A dispatcher can cancel an order until its current stop is reported. On a published plan the stop leaves the driver's route at once, the rest stays as it is, and no optimization runs. (On a ready plan or during a solve, or after *arrived*: [Q-68](#open-questions). How fast the driver is told: [Q-08](#open-questions). Editing a published plan at all: [Q-34](#open-questions).) | Must (proposed) |  |  |
| FR-59 | A customer has a name and a contact phone, and a driver can call that phone from a stop. | Must (proposed) |  |  |
| FR-60 | An import resolves each row to exactly one customer. (What counts as a match, and a row whose address matches no customer, or several: [Q-31](#open-questions).) | Must (proposed) |  |  |
| FR-61 | The import reads Hebrew text in UTF-8, with or without a byte order mark, and in Windows-1255, which spreadsheet programs commonly save. A file whose bytes are not valid in its encoding is rejected with that reason. (How a file's encoding is known, and to confirm against the pilot's real exports: [Q-10](#open-questions).) | Must (proposed) |  |  |
| FR-62 | A dispatcher can copy a previous date's orders to a new date and change them before solving. | Should (proposed) |  |  |
| FR-83 | A dispatcher can import customers in bulk from a CSV file, validated like the order import in [FR-10](#customers-and-orders): every row is checked, every rejected row is reported with its reason, and a file with any rejected row imports nothing. (What makes a row invalid or a duplicate: [Q-10](#open-questions).) | Should (proposed) |  |  |
| FR-84 | A dispatcher can download a template file for the order import ([FR-10](#customers-and-orders)), so the format is known without asking anyone. (Column format: [Q-10](#open-questions).) | Should (proposed) |  |  |
| FR-85 | An order can carry a reference from an outside system. Importing a reference that already exists on the same date is refused and reported with that reason, so a file whose rows carry references cannot be imported twice. | Should (proposed) |  |  |
| FR-86 | An order can carry a floor, an apartment and an entrance code, and the driver sees them at the stop. | Should (proposed) |  |  |
| FR-87 | An order can carry its own recipient name and phone, for a buyer who is not the recipient. The driver sees them at the stop beside the customer's contact ([FR-59](#customers-and-orders)). | Should (proposed) |  |  |
| FR-88 | A dispatcher can define a standing order that recurs on chosen weekdays. | Could (proposed) |  |  |
| FR-89 | A customer can have a default service duration, used for its new orders. | Could (proposed) |  |  |
| FR-134 | A dispatcher can search and filter customers by name, phone and address, and is warned before saving a customer that is likely to duplicate an existing one, with the same phone or a matching address. (What counts as a match: [Q-31](#open-questions).) | Should (proposed) |  |  |
| FR-135 | An order can carry its own delivery note, beside the customer's standing notes ([FR-08](#customers-and-orders)). The driver sees both at the stop, each labelled. | Should (proposed) |  |  |
| FR-136 | An order can carry a package count. The driver sees it at the stop and on the loading list ([FR-153](#publishing-and-the-driver-app)). (Whether a count can stand in for volume: [Q-88](#open-questions).) | Could (proposed) |  |  |
| FR-137 | A dispatcher can see an order's history: when it was created, imported, changed, planned, dropped, moved by a re-optimization, reported or cancelled, and by whom. This is its own per-order record, because the audit of [NFR-23](#nfr-23) names only API calls. | Should (proposed) |  |  |
| FR-138 | A dispatcher can import orders from an Excel workbook (.xlsx) as well as CSV, validated exactly as [FR-10](#customers-and-orders) and [FR-60](#customers-and-orders) require, within the limits of [NFR-37](#nfr-37). (Which sheet is read: [Q-10](#open-questions).) | Could (proposed) |  |  |

### Geocoding

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-12 | The system converts every customer address, including Hebrew addresses, to coordinates. (See also FR-139, FR-140 and Q-83.) | Must |  |  |
| FR-13 | Geocoding results are cached, so the same address is not looked up repeatedly — within the limits the provider's terms allow ([NFR-17](#nfr-17)). | Must |  |  |
| FR-14 | A dispatcher can fix a bad match by dragging the customer's pin on the map. The corrected location is saved for that customer and used for all of their future orders. | Must |  |  |
| FR-63 | Before a solve, the system flags to the dispatcher every address whose match is missing, partial, ambiguous or outside the service area. A flag does not stop the solve, and a pin the dispatcher corrected ([BR-13](#business-rules-and-invariants)) clears it. (What counts as partial or ambiguous: [Q-02](#open-questions). Service area: [Q-45](#open-questions).) | Must (proposed) |  |  |
| FR-64 | If the geocoding provider cannot be reached, a customer is still saved without coordinates, the lookup is retried, and the dispatcher can place the pin by hand. (How often it retries, and when it stops: [Q-73](#open-questions).) | Should (proposed) |  |  |
| FR-139 | While a dispatcher types a customer's or depot's address, the system suggests matching addresses, and the dispatcher picks one. A typed address the dispatcher keeps without picking a suggestion is geocoded as [FR-12](#geocoding) says. Suggestions come through the server, from the provider or the organization's own records, never from another organization's lookups. (Provider terms and the price of suggesting while typing: [Q-02](#open-questions). Geocoding cap: [NFR-52](#nfr-52).) | Could (proposed) |  |  |
| FR-140 | Before a lookup, the system normalizes common Hebrew abbreviations and spelling variants, such as רח׳ for רחוב, שד׳ for שדרות, ת״א for תל אביב, and full against defective spelling. The address is kept and shown as the dispatcher typed it. (Whether it is needed: [Q-02](#open-questions). Addresses written in Arabic or English: [Q-83](#open-questions).) | Could (proposed) |  |  |

### Daily planning

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-15 | A dispatcher can create a plan for a date and request a solve over that date's orders, vehicles and driver shifts. | Must |  |  |
| FR-16 | A solve runs in the background. The dispatcher sees that the plan is solving and is notified when the result is ready, without reloading. | Must |  |  |
| FR-17 | A solve assigns orders to vehicles, orders the stops of every route, and gives each stop a planned ETA, honouring every rule in [Business rules](#business-rules-and-invariants). | Must |  |  |
| FR-18 | When not every order fits, the solve still returns a plan and lists every dropped order with the reason it was dropped: *capacity*, *time window*, *shift*, *displaced* by other orders, or *missing location* for an order whose customer has no coordinates. (See also FR-66, FR-91, FR-141, FR-144 and FR-146.) | Must |  |  |
| FR-19 | The solve time limit is configurable within the range in [NFR-06](#nfr-06); the solve returns the best solution found within it. (Default value and who may change it: [Q-21](#open-questions).) | Must |  |  |
| FR-20 | A solve or a re-optimization can be run with either solver: OR-Tools, the default, or the DispatchFlow solver (Clarke-Wright + ALNS). Outside the optimization service a solver is known only by its name, and choosing one changes nothing else. | Must |  |  |
| FR-21 | An organization can enable a fixed cost per vehicle used, so the solver also reduces the number of vehicles on the road. | Must |  |  |
| FR-65 | When a solve fails, the dispatcher is told without reloading, with the reason (service unavailable, timed out or interrupted under [BR-32](#business-rules-and-invariants), no uncancelled orders on that date, validator rejection), and can request it again. | Must (proposed) |  |  |
| FR-66 | If the road network cannot reach a customer's location, the solve still returns a plan and drops that order with the reason *unreachable location*. | Must (proposed) |  |  |
| FR-67 | An organization can set a departure allowance, the minutes between shift start and leaving the depot for loading. Planned ETAs include it. | Should (proposed) |  |  |
| FR-90 | A planned travel time comes from the road network and varies by time of day, and planned ETAs include a buffer. (Buckets, where they apply and calibration: [Q-20](#open-questions).) The [Non-goals](#non-goals) assume this but require nothing. | Must (proposed) |  |  |
| FR-91 | A dispatcher can pin an order to a van or a driver before a solve, and the solve honours the pin. A pin that cannot be honoured drops the order with that reason. (A pin that would displace a higher-priority order, or drop one that could otherwise be served: [Q-51](#open-questions).) | Should (proposed) |  |  |
| FR-92 | Before a dispatcher solves a plan again, the system warns that the manual edits made to it ([FR-22](#manual-editing)) will be discarded. | Should (proposed) |  |  |
| FR-93 | A dispatcher can compare two solves of the same date side by side. | Could (proposed) |  |  |
| FR-94 | A dispatcher can ask for a solve that spreads the workload more evenly across drivers. (It needs its own objective beside [BR-09](#business-rules-and-invariants), which stays the one [NFR-24](#nfr-24) and [NFR-25](#nfr-25) score by.) | Could (proposed) |  |  |
| FR-141 | Before a solve, a pre-flight check lists the known causes of dropped orders or a failed solve: orders whose customer has no coordinates ([BR-14](#business-rules-and-invariants)), vans without a driver and drivers without a van on that date ([FR-54](#fleet-depots-and-shifts)), orders heavier or larger than every van, and windows outside every shift. The check does not stop the solve, and a clean check does not promise that no order drops. | Should (proposed) |  |  |
| FR-142 | While a solve runs, the dispatcher sees how long it has run out of its time limit ([FR-19](#daily-planning)). | Could (proposed) |  |  |
| FR-143 | Before publishing, each route shows its load against the van's weight and volume capacity, its duration against the driver's shift, its total waiting time and its planned distance, so that a nearly full or nearly overtime route is visible. | Should (proposed) |  |  |
| FR-144 | Each dropped order's reason ([FR-18](#daily-planning)) comes with the fact that caused it, in plain language, such as "weighs 450 kg; the largest van carries 400 kg" or "window ends at 09:00; the earliest arrival is 09:20". (How the fact is found for *displaced*: [Q-51](#open-questions).) | Should (proposed) |  |  |

### Manual editing

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-22 | A dispatcher can drag a stop to another position in its route, or to another route, while the plan is ready and not yet published. (See also FR-145, FR-146, FR-147 and Q-34.) | Should |  |  |
| FR-23 | After each manual edit, ETAs are recalculated from road travel times and every time-window, capacity and shift violation is flagged immediately. No optimization runs for this. A plan with flagged violations cannot be published ([BR-17](#business-rules-and-invariants)). | Should |  |  |
| FR-145 | While a plan is ready and not yet published, a dispatcher can undo and redo the manual edits made since the last solve, one at a time. | Should (proposed) |  |  |
| FR-146 | While a plan is ready and not yet published, a dispatcher can take a stop off its route into the dropped list, with the reason *removed by dispatcher*, and put a dropped order on a route by hand, with the same recalculation and flags as [FR-23](#manual-editing). Taking a stop off by hand is an exception to [BR-08](#business-rules-and-invariants), which binds solves and re-optimizations. An order dropped for *missing location* or *unreachable location* cannot be placed until its pin is fixed. | Should (proposed) |  |  |
| FR-147 | A dispatcher can lock a route, or the sequence of its stops, before solving again, so that the solve keeps it as it is. A lock that cannot be honoured fails the solve with that reason ([FR-65](#daily-planning)), and the warning of [FR-92](#daily-planning) does not cover a locked route. (A lock that keeps lower-priority stops while a higher-priority order drops: [Q-51](#open-questions). See also [FR-91](#daily-planning).) | Could (proposed) |  |  |

### Dispatcher views

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-24 | A dispatcher sees all routes of a plan on a map, drawn along the road network, with their stops. | Must |  |  |
| FR-25 | A dispatcher sees a timeline of stops per vehicle. | Should |  |  |
| FR-148 | Each route has one colour and one label, used the same way on the map, the timeline, lists and printouts. Colours are told apart by labels as well, never by colour alone ([NFR-46](#nfr-46)). | Should (proposed) |  |  |
| FR-149 | The map also shows depots, dropped orders at their customer's location, and the addresses flagged by [FR-63](#geocoding), each visibly different from a routed stop. | Should (proposed) |  |  |
| FR-150 | Selecting a stop on the map, the timeline or a list selects it in the others, and shows its window, ETA, load, waiting time and any violation. | Should (proposed) |  |  |
| FR-151 | A dispatcher sees every route and stop of a plan in a table that can be sorted and filtered, as an alternative to the map and timeline. | Should (proposed) |  |  |

### Publishing and the driver app

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-26 | A dispatcher can publish a plan; each driver then sees their own route. (See also FR-152 and BR-41.) | Must |  |  |
| FR-27 | A driver sees today's route: stops in order with planned ETAs, customer details and notes. | Must |  |  |
| FR-28 | A driver can hand off any stop to navigation: a Navigate action deep-links to Waze or Google Maps. (See also Q-53.) | Must |  |  |
| FR-29 | A driver can mark a stop *arrived*, *delivered* or *failed*. Marking *failed* requires a reason. (See also Q-47.) | Must |  |  |
| FR-30 | A driver can capture proof of delivery: a photo or a signature, with an optional note. | Should |  |  |
| FR-31 | While a route is in progress, the driver app reports the driver's location, including while it runs in the background on iOS and Android. The web build reports only while it is open. (Start/stop conditions and frequency: [Q-15](#open-questions).) (See also FR-68 and NFR-30.) | Must |  |  |
| FR-32 | A driver on the road receives route changes after a re-optimization is applied. (Manual edits happen only before publishing.) (See also FR-71 and Q-34.) | Must |  |  |
| FR-68 | A driver can start and finish their route. A route is *not started*, *in progress* or *finished*. (A driver who does not start or finish, finishing with stops pending, and changes to a finished route: [Q-71](#open-questions).) | Must (proposed) |  |  |
| FR-69 | The driver app shows the last received route, customer details and notes without a connection, with the route's date and when it was received. (The web build: [Q-69](#open-questions).) | Must (proposed) |  |  |
| FR-70 | A driver can start and finish a route and report stops without a connection, and capture proof too where [FR-30](#publishing-and-the-driver-app) is built. Reports keep the time they were made, survive closing the app, and are sent within 60 seconds of the connection returning while the app is open, and when it is next opened otherwise. Reports of one route are sent in the order they were made. Each stays queued until the server acknowledges it, and a refused one is shown to the driver. (ADR pending: driver report sync and idempotency. Reports still queued when the session ends: [Q-67](#open-questions). The web build: [Q-69](#open-questions).) | Must (proposed) |  |  |
| FR-71 | When a route changes, the driver app alerts the driver, even in the background, and shows which stops were added, removed or reordered. (Channel: [Q-04](#open-questions). How fast: [Q-08](#open-questions). Background delivery is verified on real phones: [Q-16](#open-questions). The web build: [Q-69](#open-questions).) | Must (proposed) |  |  |
| FR-72 | A dispatcher can print or export each route: stops in order, addresses, phones, notes and each stop's weight and volume. Taken before the vans leave, it is the fallback when the system or a phone later fails. | Should (proposed) |  |  |
| FR-95 | A driver can report a problem from the app (breakdown, running late, van full), and the dispatcher is alerted. (How fast: [Q-08](#open-questions).) | Should (proposed) |  |  |
| FR-96 | A driver sees updated ETAs for their remaining stops after each reported stop and each accepted re-optimization, using the projected arrival of [FR-34](#live-operations-and-re-optimization). | Should (proposed) |  |  |
| FR-97 | A driver confirms that they have seen a route change ([FR-71](#publishing-and-the-driver-app)), and the dispatcher sees which drivers have not. | Should (proposed) |  |  |
| FR-98 | A driver can scan a barcode at loading and at delivery. | Could (proposed) |  |  |
| FR-99 | A driver can record an age or ID check at delivery, for orders that require one. | Could (proposed) |  |  |
| FR-100 | A dispatcher and a driver can exchange messages in the app. | Could (proposed) |  |  |
| FR-152 | When a plan is published, every driver with a route is alerted that their route is ready, through the channel of [FR-71](#publishing-and-the-driver-app). (Channel: [Q-04](#open-questions).) | Should (proposed) |  |  |
| FR-153 | Before leaving the depot, a driver sees a loading list: their orders in reverse stop order, with weight and volume, and package counts where [FR-136](#customers-and-orders) is built, so the van is loaded last in, first out. | Should (proposed) |  |  |
| FR-154 | Each stop shows its time window and how long remains until it ends, and a stop at risk ([FR-34](#live-operations-and-re-optimization)) is marked in the driver's list as well as the dispatcher's. | Should (proposed) |  |  |
| FR-155 | When the driver comes within a set distance of the next stop, the app offers to mark it *arrived*. It never marks a stop on its own. (Distance: [Q-73](#open-questions).) | Could (proposed) |  |  |
| FR-156 | When a driver finishes a route ([FR-68](#publishing-and-the-driver-app)), the app shows a summary: stops delivered and failed, and any reports still waiting to be sent ([FR-70](#publishing-and-the-driver-app)). | Could (proposed) |  |  |

### Live operations and re-optimization

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-33 | A dispatcher sees, live on the map, each vehicle's latest position and each stop's status. (See also FR-158 and FR-160.) | Must |  |  |
| FR-34 | The system detects stops at risk of missing their time window — a driver running behind the plan — and alerts the dispatcher. A stop is at risk when its projected arrival, the planned arrival plus the route's observed delay, is later than its window end minus a threshold. (Threshold value: [Q-07](#open-questions).) | Must |  |  |
| FR-35 | A dispatcher can re-optimize the remaining stops of a published plan after a disruption: a late driver, an unavailable vehicle, or new orders. (Whether disruptions may also trigger it automatically: [Q-07](#open-questions).) (See also FR-57, FR-58, FR-82 and FR-159.) | Must |  |  |
| FR-36 | Before a re-optimization is applied, the dispatcher sees a preview of what changes for each driver. Nothing changes for drivers until the dispatcher accepts it. (See also FR-103.) | Must |  |  |
| FR-37 | Re-optimization keeps drivers' existing routes as stable as it can: route stability is part of its objective, measured as the number of stops that leave their vehicle, dropped stops included. (Stability weight: [Q-06](#open-questions).) | Must |  |  |
| FR-73 | A dispatcher can record a stop's outcome on a driver's behalf, with a reason when failed. The record names who made it. | Must (proposed) |  |  |
| FR-74 | The map shows how old each vehicle's position is, marks it stale past a limit, and says so when no position has been received. (Limit: [Q-15](#open-questions).) | Must (proposed) |  |  |
| FR-75 | A dispatcher can put a failed or dropped order back, for today's re-optimization or on another date. It becomes a new stop, and the original outcome stays as recorded. (Whether that is a new linked order, or a second stop on one order and so an exception to [BR-07](#business-rules-and-invariants): [Q-66](#open-questions).) | Should (proposed) |  |  |
| FR-76 | A dispatcher can see every order of a date with its state ([BR-33](#business-rules-and-invariants)) and, when it is on a route, its ETA, and find one by customer name or phone. | Should (proposed) |  |  |
| FR-101 | The dispatcher is alerted when a route has not started on time ([FR-68](#publishing-and-the-driver-app)). (How late, and against what: [Q-73](#open-questions). How fast: [Q-08](#open-questions).) | Should (proposed) |  |  |
| FR-102 | The dispatcher is alerted immediately when a driver marks a stop *failed*. (How fast: [Q-08](#open-questions).) | Should (proposed) |  |  |
| FR-103 | The preview of a re-optimization ([FR-36](#live-operations-and-re-optimization)) also shows which orders would be dropped, which time windows would break, and the change in the plan's totals. (Whether a window may break at all: [Q-32](#open-questions).) | Must (proposed) |  |  |
| FR-104 | A dispatcher can see the history of the accepted re-optimizations of a plan: who accepted each, when, and what changed. | Should (proposed) |  |  |
| FR-105 | A dispatcher can view a stop's proof of delivery ([FR-30](#publishing-and-the-driver-app)). | Should (proposed) |  |  |
| FR-157 | A dispatcher has one alert list that gathers every alert: stops at risk ([FR-34](#live-operations-and-re-optimization)), problems drivers report ([FR-95](#publishing-and-the-driver-app)), routes not started ([FR-101](#live-operations-and-re-optimization)), failed stops ([FR-102](#live-operations-and-re-optimization)), unconfirmed route changes ([FR-97](#publishing-and-the-driver-app)), late reports ([BR-27](#business-rules-and-invariants)) and refused reports ([BR-28](#business-rules-and-invariants)). A dispatcher acknowledges an alert, which records who did it and when, and an unacknowledged alert stays visible until its plan completes. | Should (proposed) |  |  |
| FR-158 | A dispatcher sees each route's progress: stops done out of all stops, current delay against the plan, next stop and when the driver last reported. | Should (proposed) |  |  |
| FR-159 | A dispatcher can limit a re-optimization to chosen routes, so that no stop leaves a route left out. (Whether stops may move into a route left out: [Q-85](#open-questions). An order dropped although a route left out had room: [Q-51](#open-questions).) | Should (proposed) |  |  |
| FR-160 | A dispatcher can replay a past day on the map: each vehicle's recorded positions and each stop's reports, in time order. Each replay is audited, an exception to [NFR-23](#nfr-23)'s rule on reads. (Retention of positions: [Q-14](#open-questions). Whether tracking may be replayed at all: [Q-42](#open-questions).) | Could (proposed) |  |  |

### Reporting and baseline

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-38 | A dispatcher sees a daily summary of each plan: per route and in total — distance, driving time, stops, delivered and failed stops, on-time percentage, vehicles used, dropped orders. The on-time percentage is the share of delivered stops whose actual arrival, as the driver's phone recorded it, was at or before their window end; with nothing delivered it is shown as unavailable, not as zero. (See also Q-49.) | Must |  |  |
| FR-39 | Every plan is compared with a naive nearest-neighbour baseline on the same orders and fleet, in kilometres, driver-hours and planned on-time percentage: orders served inside their windows over all orders of the day, so dropped orders count as not on time. A vehicle is withdrawn mid-route when a re-optimization leaves it out while it still has stops it made: it was marked unavailable (FR-07), left without a driver, or its shift ran out. Its route keeps those stops and never returns, so its driver-hours are unknown. Such a day is compared on kilometres and on-time only, and the comparison names the withdrawn vehicles. Those two figures are not strictly like for like: the day's baseline is solved after the re-optimization, leaving out a vehicle that was marked unavailable or left without a driver, and giving one whose shift ran out its whole shift (decided on 2026-10-10, Q-24). (See also BR-30 and Q-50.) | Must |  |  |
| FR-40 | Where a customer supplies historical manual routes, a plan can be compared against them on the same measures. | Should |  |  |
| FR-41 | An analytics dashboard shows trends across days: on-time percentage, kilometres saved. | Could |  |  |
| FR-106 | The daily summary ([FR-38](#reporting-and-baseline)) also shows planned against actual distance, driving time and arrival times. (How actuals are measured: [Q-40](#open-questions).) | Should (proposed) |  |  |
| FR-107 | A dispatcher can export summaries and order lists to a spreadsheet. | Could (proposed) |  |  |
| FR-108 | A summary shows savings in money, from an organization's cost per kilometre and per driver-hour. | Could (proposed) |  |  |
| FR-109 | A dispatcher sees a report per driver. (What may be shown about an employee: [Q-42](#open-questions).) | Could (proposed) |  |  |
| FR-161 | A dispatcher sees failed and dropped orders over a range of dates, grouped by reason ([FR-18](#daily-planning), [FR-29](#publishing-and-the-driver-app)), customer and, where the order was on a route, driver. (Whether failure reasons can be grouped: [Q-47](#open-questions). What may be shown about a driver: [Q-42](#open-questions).) | Should (proposed) |  |  |
| FR-162 | A dispatcher can see the record of every solve and re-optimization of a plan ([NFR-11](#nfr-11)): solver, time limit, duration, objective value and dropped orders. | Could (proposed) |  |  |

### Research and evaluation

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-42 | DispatchFlow has its own solver: a Clarke-Wright savings construction improved by Adaptive Large Neighbourhood Search with destroy and repair operators and simulated-annealing acceptance, behind the same solver interface as OR-Tools. For a re-optimization it starts from the current routes and repairs them, destroying and reinserting stops around the disruption, instead of solving again from scratch. No product capability depends on it ([Non-goals](#non-goals)). | Must |  |  |
| FR-43 | A benchmark harness runs OR-Tools and the DispatchFlow solver on the Solomon instances (56 instances of 100 customers, classes C1, C2, R1, R2, RC1, RC2), on Gehring-Homberger instances, and on the pilot's real days once the pilot provides them ([Q-01](#open-questions)). It reports the gap from best-known solutions where they are published, the DispatchFlow solver's gap from OR-Tools ([NFR-24](#nfr-24)), and the solve time. Both solvers get the same time limit on the same hardware, and every result is scored by the shared objective ([BR-09](#business-rules-and-invariants)) once the validator accepts it ([BR-15](#business-rules-and-invariants)). (Gehring-Homberger subset: [Q-22](#open-questions).) | Must |  |  |
| FR-44 | A scaling study reports solve time and solution quality at 25, 50, 100 and 200 stops. | Must |  |  |
| FR-45 | A synthetic order generator builds realistic days from real Israeli addresses, for testing and as the fallback when no pilot data is available. | Must |  |  |
| FR-46 | A simulation replays a real or synthetic day with injected delays, breakdowns and new orders, generated from a recorded seed so that the day can be replayed. It runs every re-optimization with each solver at the same set of time limits: OR-Tools warm-started from the current routes, and the DispatchFlow solver repairing them. At each limit it measures each solver's objective, route stability, elapsed time and whether the new orders were served, all scored by the shared objective ([BR-09](#business-rules-and-invariants)). A run in which OR-Tools did not start from the current routes is reported and not counted as warm-started ([NFR-25](#nfr-25)). (See also FR-167.) | Must |  |  |
| FR-47 | A pilot runs DispatchFlow with a real customer; their feedback is collected and written up. (Pilot customer: [Q-01](#open-questions).) (See also Q-40.) | Must |  |  |
| FR-48 | A usability study runs task-based sessions with the pilot's dispatcher (build a plan, handle a breakdown) scored with the SUS questionnaire, collects driver feedback on route stability after re-optimization, and feeds UI iterations between rounds. (See also Q-76.) | Should |  |  |
| FR-51 | The benchmark harness also runs PyVRP, an open-source state-of-the-art VRP solver, on the static instances of FR-43 under the same time limits, as a reference for both solvers. Only the harness uses PyVRP; it never serves a solve in the product. | Should |  |  |
| FR-163 | The DispatchFlow solver can log, at a sampled interval, each iteration's operators chosen, operator weights, current and best objective and acceptance temperature, so that convergence and each operator's contribution can be plotted. Logging is off in the product. | Should (proposed) |  |  |
| FR-164 | The benchmark harness can run the DispatchFlow solver with single destroy or repair operators removed, or with adaptive weights fixed, to measure each part's contribution (an ablation study). | Should (proposed) |  |  |
| FR-165 | Solver comparisons ([FR-43](#research-and-evaluation), [FR-46](#research-and-evaluation)) report per-instance paired results with a paired significance test, such as the Wilcoxon signed-rank test, and an effect size. The test and significance level are fixed before the study. (Method: [Q-84](#open-questions).) | Should (proposed) |  |  |
| FR-167 | The simulation of [FR-46](#research-and-evaluation) can also inject cancellations ([FR-58](#customers-and-orders)), unavailable drivers ([FR-82](#fleet-depots-and-shifts)), changed time windows ([FR-57](#customers-and-orders)) and late driver reports ([BR-27](#business-rules-and-invariants)), from the same recorded seed. | Should (proposed) |  |  |
| FR-168 | An Admin of the pilot organization can export a pilot day as a pseudonymized benchmark instance ([NFR-50](#nfr-50)) that both solvers and PyVRP read, so that a real day can be rerun beside Solomon instances. The export is audited, and the instance stays outside the repository ([NFR-62](#nfr-62)). (Whether coordinates may be kept: [Q-60](#open-questions).) | Could (proposed) |  |  |

### Later capabilities

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-49 | End customers receive an SMS or email with their delivery ETA. | Could |  |  |
| FR-50 | An organization can run several depots, with vehicles starting and ending at different ones. | Could |  |  |
| FR-110 | Pick-up and delivery jobs: an order is collected at one address and delivered to another, not only loaded at a depot. Courier companies, named under [Purpose and scope](#purpose-and-scope), usually need this. | Could (proposed) |  |  |
| FR-111 | A van can run more than one trip a day, returning to a depot to reload between trips. (Reloads and transferable goods: [Q-23](#open-questions).) | Could (proposed) |  |  |
| FR-112 | A dispatcher can require that an order goes on a van or a driver with a given attribute, such as refrigeration or a licence. | Could (proposed) |  |  |
| FR-113 | An order can have several time windows. (Would change [BR-03](#business-rules-and-invariants) and [BR-35](#business-rules-and-invariants).) | Could (proposed) |  |  |
| FR-114 | Shifts and time windows can cross midnight, as a bakery's do. (Would change [FR-54](#fleet-depots-and-shifts) and [BR-35](#business-rules-and-invariants). Whether overnight work is in the pilot's scope: [Q-40](#open-questions).) | Could (proposed) |  |  |
| FR-115 | A driver can record a partial delivery or a return of goods. | Could (proposed) |  |  |
| FR-116 | Another system can send orders to DispatchFlow through an API. This is a revisit trigger of [ADR-006](../ADRs/ADR-006-versioned-openapi-contracts.md), and it needs a credential beyond [NFR-01](#nfr-01)'s access token. | Could (proposed) |  |  |
| FR-117 | A customer can follow their delivery on a live tracking page. It needs an exception to [NFR-01](#nfr-01) that still keeps [BR-01](#business-rules-and-invariants). (Accessibility scope: [Q-59](#open-questions).) | Could (proposed) |  |  |
| FR-118 | A route respects road limits that depend on the vehicle type. | Could (proposed) |  |  |
| FR-119 | A dispatcher can plan across several days at once. (Would change [BR-34](#business-rules-and-invariants).) | Could (proposed) |  |  |
| FR-120 | A dispatcher can do their day-to-day tasks from a phone, not only from the desktop console ([NFR-15](#nfr-15)). This is a revisit trigger of [ADR-003](../ADRs/ADR-003-dispatcher-console-and-driver-app.md). Approving it also changes [NFR-15](#nfr-15) and `src/frontend/console/CLAUDE.md`. (ADR pending: dispatcher on a phone) | Could (proposed) |  |  |
| FR-121 | The driver app is available in Arabic and Russian, beside Hebrew and English ([NFR-27](#nfr-27)). (Which languages, for whom: [Q-44](#open-questions).) | Could (proposed) |  |  |
| FR-122 | A new organization can load sample data to try the system. | Could (proposed) |  |  |
| FR-169 | A route can start or end at a driver's chosen address instead of a depot, for a driver who keeps the van at home. (Would change [BR-40](#business-rules-and-invariants). Privacy of a home address: [Q-87](#open-questions).) | Could (proposed) |  |  |

## Business rules and invariants

Rules are grouped by subject; their IDs are unchanged.

### Tenancy and access

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-01 | **Tenant isolation.** Every entity belongs to exactly one organization. No user can read or change another organization's data. |  |  |
| BR-02 | **Driver scope.** A Driver can see and update only the stops of routes assigned to them. (See also BR-27 and Q-35.) |  |  |
| BR-19 | **At least one Admin.** An organization always keeps at least one Admin: the role of its only Admin cannot be changed. An Admin who wants to step down first makes another user an Admin. (See also FR-53.) |  |  |
| BR-41 | **Drivers see published routes only.** A plan that is `draft`, `solving` or `ready`, and a re-optimization preview not yet accepted ([FR-36](#live-operations-and-re-optimization)), is never shown to drivers. Priority: Must (proposed). |  |  |
| BR-45 | **Unique identifiers.** A sign-in identifier belongs to at most one user in all of DispatchFlow ([Non-goals](#non-goals)). A vehicle's display name and licence plate are each unique among its organization's active vehicles ([FR-131](#fleet-depots-and-shifts)), so a retired one ([BR-43](#business-rules-and-invariants)) does not block a new one. Priority: Must (proposed) for the identifier, Should (proposed) for vehicles. |  |  |

### Routing constraints

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-03 | **Time windows.** Service at a stop starts inside its order's time window. A vehicle that arrives early waits. An order's window defaults to its customer's default window. (See also BR-23 and Q-32.) |  |  |
| BR-04 | **Service time.** Each stop occupies the vehicle for the order's service duration, which counts toward the arrival time at the next stop. |  |  |
| BR-05 | **Capacity.** On every route, the total weight demand never exceeds the vehicle's weight capacity, and the total volume demand never exceeds its volume capacity. Capacities are positive in both dimensions; demands are zero or more. |  |  |
| BR-06 | **Shifts.** A route leaves no earlier than its driver's shift start and returns to its end depot no later than the shift end. (See also BR-46.) |  |  |
| BR-14 | **Routable orders only.** An order can be planned only when its customer has coordinates. |  |  |
| BR-39 | **One route per van and driver.** Within a plan, a van drives at most one route and a driver drives at most one route. A route withdrawn mid-route ([FR-39](#reporting-and-baseline)) does not count. (The default until [Q-36](#open-questions) and [Q-65](#open-questions) are answered; [FR-111](#later-capabilities) would change it.) Priority: Must (proposed). |  |  |
| BR-40 | **Routes start and end at depots.** A route starts at its van's start depot, or at the start point of [BR-10](#business-rules-and-invariants) and [BR-24](#business-rules-and-invariants) during a re-optimization, and ends at the van's end depot. A route withdrawn mid-route ([FR-39](#reporting-and-baseline)) ends at its last reported stop. ([FR-169](#later-capabilities) would change it.) Priority: Must (proposed). |  |  |
| BR-46 | **Depot hours.** When a depot has opening hours ([FR-129](#fleet-depots-and-shifts)), a route leaves it no earlier than it opens and returns no later than it closes. With [BR-06](#business-rules-and-invariants), the tighter limit holds. Priority: Should (proposed). |  |  |

### Solving, dropping and validation

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-07 | **Single assignment.** Within a plan, every order is either served by exactly one stop or reported as dropped — never both, never twice. (See also BR-30.) |  |  |
| BR-08 | **Dropping.** An order is dropped only when not every order can be served. Priority is a whole number from 1 to 5, and dropping follows it strictly: a higher-priority order is never dropped to save any number of lower-priority orders, and among orders of one priority the fewest are dropped. (See also FR-146, Q-43 and Q-51.) |  |  |
| BR-09 | **Objective.** A solve minimizes total travel time plus drop penalties, plus the fixed vehicle cost when enabled ([FR-21](#daily-planning)). A re-optimization also adds a route-stability penalty ([FR-37](#live-operations-and-re-optimization)). |  |  |
| BR-15 | **Validated solutions only.** No solution — from either solver, or from a re-optimization — is stored as a plan or shown as feasible unless the independent feasibility validator accepts it. A solution the validator rejects fails its solve job and lists the violations. (See also BR-42 and NFR-80.) |  |  |
| BR-42 | **One definition of feasibility.** Manual-edit checks ([FR-23](#manual-editing)), the publishing re-check ([BR-21](#business-rules-and-invariants)), solver acceptance ([BR-15](#business-rules-and-invariants)) and research scoring ([FR-43](#research-and-evaluation), [FR-46](#research-and-evaluation)) use the same validator, and research scoring uses the shared objective ([BR-09](#business-rules-and-invariants)). No solver's own check is the final word on its solution. Priority: Must (proposed). |  |  |

### Plan lifecycle and publishing

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-12 | **Plan lifecycle.** A plan moves `draft → solving → ready → published → completed`. A failed solve returns it to `draft`, or to `ready` if it was solved before; a ready plan may be solved again. Manual edits keep a plan `ready`; an accepted re-optimization keeps it `published` and changes it in place. No other transition exists; there is no cancel. (See also BR-26, BR-32 and Q-46.) |  |  |
| BR-16 | **Edits are flagged, never silently fixed.** A manual edit that breaks any of BR-01 to BR-15, BR-39, BR-40 or BR-46 is kept as the dispatcher made it and flagged with every violation. |  |  |
| BR-17 | **No publishing with violations.** A plan with any flagged violation cannot be published; the dispatcher fixes it or solves again first. |  |  |
| BR-20 | **Published as solved.** A plan is published only while every route can still be driven as it was solved: its van is not marked unavailable, has a driver who holds the Driver role, and that driver is the route's. Otherwise nothing is published, every changed van is named, and the dispatcher solves again. Other changes since the solve, to a driver's shift, a van's capacity or its depots, are caught by BR-21. Accepting a re-optimization applies the same rule to every van the proposal keeps driving, skipping vans withdrawn mid-route, and refuses, naming every changed van, so the dispatcher re-optimizes again (ADR pending: check the vans when a re-optimization is accepted). (See also FR-53, FR-55 and FR-82.) |  |  |
| BR-21 | **Rules hold at publishing.** Before a ready plan is published, every route is re-checked once by the shared validator against the current fleet and orders of its date: shifts, capacities, depots and time windows, and vehicle limits where [FR-133](#fleet-depots-and-shifts) is built. If a route breaks a rule, nothing is published, every broken rule is listed, and the dispatcher solves again or edits the route. When nothing breaks, the re-checked times are published: they equal the solved times unless the fleet changed since the solve, and then they follow it. An order the plan does not account for refuses before it (BR-22). Violations already flagged refuse first (BR-17), without a re-check, and a plan without routes needs none. If the re-check cannot run, nothing is published. |  |  |
| BR-22 | **Every order accounted for at publishing.** A plan is published only when it serves or drops every order of its date. An order placed after the solve was requested refuses publishing: nothing is published, every such order is named, and the dispatcher solves again. Changed vans (BR-20) are reported first; flagged violations (BR-17) and the re-check (BR-21) come after. Orders placed after publishing enter through re-optimization (FR-11). (See also BR-30.) |  |  |
| BR-32 | **No plan stays `solving`.** A solve that has started and has not returned within its limit plus a fixed margin fails, as does one running when a restart interrupts it. The limit runs from the solve's start, and a queued solve still waits its turn ([NFR-12](#nfr-12)). [BR-12](#business-rules-and-invariants)'s failed-solve transition then applies. (Margin: [Q-21](#open-questions). ADR pending: solve job timeout) |  |  |
| BR-34 | **One plan per organization per date.** An organization has at most one plan for a date. This binds what [Scope](#purpose-and-scope) states. |  |  |
| BR-38 | **One change at a time.** A plan has at most one re-optimization at a time, and two dispatchers cannot silently overwrite each other's changes to a plan: the second request or the out-of-date change is refused with its reason. (Whether an open preview counts as a re-optimization: [Q-58](#open-questions).) (ADR pending: concurrent changes to a plan) |  |  |
| BR-47 | **Published routes change only through named actions.** A published route changes only through an accepted re-optimization ([FR-36](#live-operations-and-re-optimization)), a hand-over ([FR-55](#fleet-depots-and-shifts)), a cancellation ([FR-58](#customers-and-orders)), a driver's start, finish or report ([FR-68](#publishing-and-the-driver-app), [FR-29](#publishing-and-the-driver-app)), including a late one accepted under [BR-27](#business-rules-and-invariants), or an outcome recorded on a driver's behalf ([FR-73](#live-operations-and-re-optimization)). Each change is audited ([NFR-23](#nfr-23)), and no background process changes a route silently. (The default until [Q-34](#open-questions) is answered.) Priority: Must (proposed). |  |  |

### Re-optimization

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-10 | **Locked stops.** Re-optimization never moves a completed or in-progress stop. Each vehicle's current location becomes its new starting point. (See also BR-24 and Q-52.) |  |  |
| BR-23 | **Facts are not violations.** Times already recorded are facts. When a re-optimization is validated ([BR-15](#business-rules-and-invariants)), [BR-03](#business-rules-and-invariants), [BR-04](#business-rules-and-invariants), [BR-06](#business-rules-and-invariants), [BR-46](#business-rules-and-invariants) and the vehicle limits of [FR-133](#fleet-depots-and-shifts) apply only to what has not happened yet, so a locked stop that ran late never fails the solution. (ADR pending: validator and reoptimization problem conventions) |  |  |
| BR-24 | **Start point without a usable position.** If a vehicle has no position newer than the freshness limit, or its latest position is outside the service area or implies an impossible speed, re-optimization starts it from the last stop it reported, or from its start depot if it reported none. The preview names that vehicle. (Freshness limit: [Q-15](#open-questions). Speed: [Q-73](#open-questions).) GPS interference placed phones in Israel abroad in 2024 ([Ynet](https://www.ynetnews.com/article/rko9utojc)). (ADR pending: validator and reoptimization problem conventions) |  |  |
| BR-25 | **Accepting re-checks recorded outcomes.** Accepting a re-optimization re-checks it against every stop status, cancellation, order change, hand-over and fleet change recorded since the preview. If the proposal would move or drop a stop that changed, or relies on an order, van or driver that changed, nothing is applied, what changed is named, and the dispatcher re-optimizes. A report on a stop the proposal leaves on the same van and in the same order does not refuse it, and the report stands. (How old a preview may be: [Q-58](#open-questions).) (ADR pending: check recorded changes when a re-optimization is accepted) |  |  |
| BR-26 | **A failed re-optimization changes nothing.** A re-optimization that fails, is rejected by the validator or is declined leaves the plan `published` and every route as it was. A re-optimization that has not returned within its limit plus a fixed margin, or that a restart interrupts, has failed. The dispatcher is told why. (The limit, when it starts, and the margin: [Q-21](#open-questions).) |  |  |

### Driver reports and outcomes

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-11 | **Failure reason.** A stop cannot be marked *failed* without a reason. (See also Q-47.) |  |  |
| BR-18 | **Outcomes are final.** A delivery moves `pending → arrived → delivered` or `failed`, and may skip *arrived*. Once *delivered* or *failed* is recorded, it cannot change. (See also BR-28 and Q-33.) |  |  |
| BR-27 | **Outcomes outlive route changes.** An outcome a driver recorded while the stop was on their route is accepted however late it arrives, even if the stop has moved since (an exception to [BR-02](#business-rules-and-invariants)), unless the stop already has a recorded outcome or its order is cancelled; then [BR-28](#business-rules-and-invariants) applies. The stop leaves the other driver's route, that driver is told, and the dispatcher is alerted. (Whether this default stands, and who resolves a conflict: [Q-64](#open-questions). ADR pending: driver report sync and idempotency) |  |  |
| BR-28 | **Repeated reports are harmless.** A report received twice takes effect once, and the repeat gets the first answer. A report contradicting a recorded outcome, or arriving for a cancelled order, is refused and shown to the dispatcher. (The default until [Q-64](#open-questions) is answered.) (ADR pending: driver report sync and idempotency) |  |  |
| BR-29 | **Stops may be done out of order.** A driver can report a route's stops in a different order from the plan. The system records the actual order, and re-optimization treats those stops as done in that order. The start point stays [BR-10](#business-rules-and-invariants) and [BR-24](#business-rules-and-invariants). |  |  |
| BR-44 | **Two times on every report.** Every driver report keeps the time the phone recorded and the time the server received it. (Which one a summary and the on-time percentage use: [Q-49](#open-questions).) Priority: Must (proposed). |  |  |

### Orders, records and history

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-13 | **Corrected locations win.** A location a dispatcher corrected by hand overrides any geocoding result for that customer. (See also Q-53.) |  |  |
| BR-30 | **Cancelled orders.** A cancelled order is neither served nor dropped: [BR-07](#business-rules-and-invariants), [BR-22](#business-rules-and-invariants), on-time figures and the baseline no longer count it. An order whose current stop has been reported cannot be cancelled. |  |  |
| BR-33 | **Order states.** An order is *unplanned*, *planned*, *dropped*, *delivered*, *failed* or *cancelled*. A state changes only through the actions that other rows define ([FR-17](#daily-planning), [FR-18](#daily-planning), [FR-29](#publishing-and-the-driver-app), [FR-36](#live-operations-and-re-optimization), [FR-57](#customers-and-orders), [FR-58](#customers-and-orders), [FR-73](#live-operations-and-re-optimization), [FR-75](#live-operations-and-re-optimization), [FR-145](#manual-editing), [FR-146](#manual-editing)), and [BR-30](#business-rules-and-invariants) still applies. [BR-18](#business-rules-and-invariants) governs a stop's outcome. An order leaves *failed* only through [FR-75](#live-operations-and-re-optimization). It leaves *dropped* through [FR-75](#live-operations-and-re-optimization), when a ready plan is solved again ([BR-12](#business-rules-and-invariants)), when its date changes ([FR-57](#customers-and-orders)), when it is cancelled ([FR-58](#customers-and-orders)), when a late report is accepted ([BR-27](#business-rules-and-invariants)), when a dispatcher records its outcome ([FR-73](#live-operations-and-re-optimization)), or when a dispatcher puts it on a route by hand ([FR-146](#manual-editing)) or undoes the edit that dropped it ([FR-145](#manual-editing)). |  |  |
| BR-35 | **Valid order data.** An order's time window starts before it ends and lies on the order's date. Its service duration is not negative. An order with invalid data is refused with its reason. (Windows that cross midnight: [FR-114](#later-capabilities). An order on a past date, or on a date whose plan is completed: [Q-46](#open-questions).) |  |  |
| BR-37 | **History stays.** A plan that is completed, or whose date has passed, keeps the values it was solved and reported with, after the fleet or a customer changes. Editing a van does not rewrite an old report. (ADR pending: past plans keep their values) |  |  |
| BR-43 | **Retire, don't delete.** A depot, vehicle, driver, customer or order that any plan refers to is retired ([FR-56](#fleet-depots-and-shifts)) or cancelled ([FR-58](#customers-and-orders)), never deleted. Only a personal-data request ([NFR-49](#nfr-49)) or a retention period ([NFR-31](#nfr-31)) removes personal fields, which include a customer's coordinates, and the non-personal figures stay. An organization that leaves is the exception: its data is deleted as [NFR-31](#nfr-31) says. Priority: Must (proposed). |  |  |

### Time and limits

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-31 | **Israel local time.** Every date, time window, shift and ETA is Israel local time, on clock-change days too. A plan's date is the local calendar date. (An hour the clock skips or repeats: [Q-73](#open-questions). ADR pending: Israel local time) |  |  |
| BR-36 | **Limits are enforced.** Adding a sixteenth vehicle to an organization is refused, and the message names the limit ([NFR-04](#nfr-04)). A date's orders are held to the limit of [NFR-05](#nfr-05) the same way. (What counts toward it, and where it is refused: [Q-70](#open-questions). ADR pending: hard limits on fleet and plan size) |  |  |

## Non-functional requirements

### Security and access

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-01"></a>NFR-01 | Every API call requires a valid access token, except signing up, signing in, refreshing, signing out and health checks; roles are checked on the server for every operation. An access token is accepted for 15 minutes by default. (See also FR-52.) |  |  |
| <a id="nfr-02"></a>NFR-02 | Tenant isolation (BR-01) is enforced by the backend on every read and write, and is tested through the API with at least two organizations. |  |  |
| <a id="nfr-03"></a>NFR-03 | Secrets — provider API keys, token-signing keys, database credentials — come from the environment or a secret manager, never from the repository, logs or error messages. |  |  |
| <a id="nfr-22"></a>NFR-22 | A signed-in user stays signed in through refresh tokens. Each one is exchanged once, for a new access token and the session's next refresh token, and lasts 14 days by default from its issue. Presenting one already exchanged revokes its whole session; a sign-out revokes its session; a role or password change revokes every session of the user. Only a refresh token's hash is stored, and every refusal looks the same. (See also Q-54.) |  |  |
| <a id="nfr-23"></a>NFR-23 | Every API call that changes something, and every sign-up, sign-in, refresh and sign-out, leaves one audit record: when, the acting user and organization when known, the operation, the records it names, its outcome and its trace id. An audit record never carries personal data or a secret (NFR-19, NFR-03). Reads are not audited. |  |  |
| <a id="nfr-26"></a>NFR-26 | In every deployed environment, all traffic between clients and the server is encrypted in transit, and an unencrypted request is refused or redirected, never served. (ADR pending: sign-in and transport security) |  |  |
| <a id="nfr-35"></a>NFR-35 | Failed sign-ins for one account are limited to 10 per 15 minutes. (What happens past the limit, so that a driver is not locked out mid-route: [Q-73](#open-questions). ADR pending: sign-in and transport security) |  |  |
| <a id="nfr-36"></a>NFR-36 | Passwords are stored only as salted hashes from a function built for passwords, never in a form that can be read back, and a minimum password policy applies. (Policy: [Q-73](#open-questions). ADR pending: sign-in and transport security) |  |  |
| <a id="nfr-37"></a>NFR-37 | Uploads, meaning CSV and Excel files, delivery photos and signatures, are limited by file type and size, and one over a limit is refused with the reason. A compressed file's unpacked size is limited too, and a workbook's external references are never followed. (Types and sizes: [Q-73](#open-questions).) |  |  |
| <a id="nfr-38"></a>NFR-38 | Proof-of-delivery files are served only to users of the organization they belong to ([BR-01](#business-rules-and-invariants)) whose role permits it (the [permission matrix](#organization-users-and-roles)). |  |  |
| <a id="nfr-39"></a>NFR-39 | Tokens, and the route data kept for offline use ([FR-69](#publishing-and-the-driver-app)), are stored encrypted on the phone, with the key in the phone's secure storage, and are removed when the user signs out. (Checked on a real phone: [Q-16](#open-questions). The web build: [Q-69](#open-questions). Reports still queued at sign-out, and this data when a session is revoked: [Q-67](#open-questions). ADR pending: sign-in and transport security) |  |  |
| <a id="nfr-40"></a>NFR-40 | A self-check against Level 1 of the [OWASP Application Security Verification Standard (ASVS) 5.0](https://asvs.owasp.org/) passes before the pilot handles real data. (ADR pending: security and accessibility standards) |  |  |
| <a id="nfr-53"></a>NFR-53 | Every API that clients call is rate-limited per user and per organization, beyond the sign-in limit of [NFR-35](#nfr-35); a request over a limit is refused with when to retry, and a driver's queued reports ([FR-70](#publishing-and-the-driver-app)) are not lost by it, and such a refusal is neither shown to the driver nor counted against the 60 seconds of [FR-70](#publishing-and-the-driver-app). Calls without a token ([NFR-01](#nfr-01), [FR-52](#organization-users-and-roles)) are limited per source address, and sign-ups also in total per day. (Limits: [Q-73](#open-questions). ADR pending: API rate limiting) Priority: Must (proposed) for calls without a token, Should (proposed) for the rest. |  |  |
| <a id="nfr-54"></a>NFR-54 | CI scans dependencies and container images for known vulnerabilities and the repository with its full history for committed secrets ([NFR-03](#nfr-03)). A known vulnerability fails the build unless it is recorded as accepted, with its reason and an expiry date. A detected secret always fails it and is rotated, not only removed. ([ADR-016](../ADRs/ADR-016-dependency-audit-with-osv-scanner.md) covers dependencies only. ADR pending: image and secret scanning) Priority: Must (proposed). |  |  |
| <a id="nfr-55"></a>NFR-55 | Web responses set a Content Security Policy, HSTS ([NFR-26](#nfr-26)) and a frame-ancestors rule, and any cookie that carries a session is Secure, HttpOnly and SameSite=Lax or Strict. A change made with such a cookie needs anti-forgery protection, and cross-origin requests are accepted only from named origins. Priority: Must (proposed). |  |  |
| <a id="nfr-56"></a>NFR-56 | A request that breaks its OpenAPI contract ([NFR-21](#nfr-21)) is refused with the reason before anything acts on it. (ADR pending: runtime contract validation) Priority: Should (proposed). |  |  |

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
| <a id="nfr-41"></a>NFR-41 | Organizations can solve at the same time without one delaying another past a stated limit. (Number of organizations and the limit: [Q-62](#open-questions).) |  |  |
| <a id="nfr-57"></a>NFR-57 | The travel-time matrix for a plan at the scale of [NFR-04](#nfr-04) and [NFR-05](#nfr-05) is built within a stated time. (Value, and whether it counts toward a solve's limit: [Q-58](#open-questions) and [Q-86](#open-questions).) Priority: Should (proposed). |  |  |
| <a id="nfr-58"></a>NFR-58 | The console's first load finishes within 3 seconds on a stated connection, and panning, zooming and selecting on a plan of 200 stops and 15 routes holds a stated frame rate. (Proposed value; the connection and the frame rate: [Q-86](#open-questions).) Priority: Should (proposed). |  |  |
| <a id="nfr-59"></a>NFR-59 | Reads and writes that do not solve answer within 500 ms at the 95th percentile, per operation, under the load of [NFR-10](#nfr-10), except imports ([NFR-60](#nfr-60)) and publishing. (Proposed value; to confirm under [Q-08](#open-questions) and [Q-86](#open-questions).) Priority: Should (proposed). |  |  |
| <a id="nfr-60"></a>NFR-60 | An import of 200 order rows ([FR-10](#customers-and-orders)) is validated and reported within 10 seconds, not counting the geocoding of new addresses. (Proposed value; to confirm under [Q-86](#open-questions).) Priority: Could (proposed). |  |  |
| <a id="nfr-61"></a>NFR-61 | The driver app shows the last received route within 2 seconds of opening, with or without a connection ([FR-69](#publishing-and-the-driver-app)). (Proposed value; to confirm under [Q-86](#open-questions).) Priority: Should (proposed). |  |  |

### Solver research targets

These are the success criteria of the planned comparison between the DispatchFlow solver and
OR-Tools ([ADR-005](../ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md)).
They bind the research track, not the product. OR-Tools serves the product whatever the
outcome, and a missed target is a result to report, not a failed release. The study reports
each target's measured value and whether it was met. NFR-62 states the provenance every result of the study must carry.

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-24"></a>NFR-24 | **Static quality.** On the Solomon instances, with both solvers given the same time limit on the same hardware, the DispatchFlow solver's objective is on average within **N%** of OR-Tools'. Both solvers are scored by the shared objective ([BR-09](#business-rules-and-invariants)), with the study's drop penalty and fixed-cost setting. Each solver runs *k* times per instance, one solver at a time on an otherwise idle machine, and its result on the instance is the mean of those runs. The gap on an instance is (DispatchFlow − OR-Tools) / OR-Tools, and the average is the mean of the gaps over all instances. No failure is left out: the target is met only if every run of the DispatchFlow solver returns a solution the validator accepts ([BR-15](#business-rules-and-invariants)), and an instance on which an OR-Tools run fails or is rejected is reported and left out of the mean, which can only count against the DispatchFlow solver. Every run records its seed, the iterations or solutions it reached and the solver versions, so that it can be replayed under that budget ([NFR-13](#nfr-13)). (N, the time limit, *k*, the drop penalty and the fixed-cost setting: [Q-29](#open-questions).) |  |  |
| <a id="nfr-25"></a>NFR-25 | **Incremental re-optimization.** Given a published plan at the scale of [NFR-04](#nfr-04) and [NFR-05](#nfr-05) and one new order, both solvers get the same time limit of **T seconds**: the DispatchFlow solver repairs the plan, and OR-Tools is warm-started from it. Within that limit the DispatchFlow solver returns a solution the validator accepts that serves the new order, and over the study's insertions its objective is on average no worse than OR-Tools'. Both are scored by the shared objective ([BR-09](#business-rules-and-invariants)). Only insertions for which OR-Tools, solving the whole day from scratch, serves the new order are counted. The elapsed time is measured inside the optimization service, from receiving the problem to returning the solution, and stays within T at the 99th percentile. Both solvers are also run at a set of shorter and longer limits, and the report gives each solver's objective at each limit, so that a claim such as "ours at 1 s matches OR-Tools at 5 s" can be checked. This is a solver-side target; the end-to-end promise to the dispatcher remains [NFR-07](#nfr-07). (T, the set of limits, and how insertions are sampled: [Q-30](#open-questions).) |  |  |
| <a id="nfr-62"></a>NFR-62 | **Provenance.** Every benchmark and simulation result is traceable to the commit, configuration file, seeds, hardware description and solver versions that produced it, and the whole study reruns from one command, given any pilot data. Results are kept beside the configuration that made them, except that instances and results derived from pilot data stay outside the repository ([NFR-50](#nfr-50)), which keeps only their hash and configuration. Priority: Must (proposed). |  |  |

### Product success targets

These measure whether the product worked for the pilot customer. They bind the pilot, not the
research track, and their values are not yet agreed ([Q-40](#open-questions)).

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-32"></a>NFR-32 | Over at least **N** consecutive pilot days, driven kilometres and driver-hours are **X%** below the customer's own manual routes on comparable days, with an on-time percentage no lower than theirs. (N, X: [Q-40](#open-questions).) |  |  |
| <a id="nfr-33"></a>NFR-33 | At least 80% of delivered stops arrive within ±15 minutes of their ETA in the published plan or the last accepted re-optimization, not [FR-96](#publishing-and-the-driver-app)'s updated estimate. (Proposed values; to confirm under [Q-20](#open-questions) and [Q-40](#open-questions).) |  |  |
| <a id="nfr-34"></a>NFR-34 | A dispatcher goes from orders in hand to a published plan in 15 minutes or less on a typical pilot day, and no slower than their current method. (Proposed value; to confirm under [Q-40](#open-questions).) |  |  |

### Reliability and persistence

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-11"></a>NFR-11 | Every solve is recorded with its parameters, status, timings, solver used and objective value, so it can be audited and reproduced. |  |  |
| <a id="nfr-12"></a>NFR-12 | A queued solve survives a backend restart. (See also BR-32.) |  |  |
| <a id="nfr-13"></a>NFR-13 | The DispatchFlow solver is reproducible: the same input, seed and iteration budget give the same solution. |  |  |
| <a id="nfr-14"></a>NFR-14 | The system runs on a single VM with no redundancy. (Availability and backup targets: [Q-08](#open-questions).) (See also NFR-28, NFR-29 and NFR-42.) |  |  |
| <a id="nfr-28"></a>NFR-28 | The database and proof files are backed up at least every 24 hours to storage off the VM. A restore finishes within 4 hours and is rehearsed once before the pilot. (Proposed values; to confirm under [Q-08](#open-questions). ADR pending: backup and restore) |  |  |
| <a id="nfr-29"></a>NFR-29 | During the pilot's operating hours the API is down at most 1% of the time, measured over each pilot month; that is about 8 minutes per 14-hour day on average. Planned downtime falls outside those hours, and an outage alerts a named team member within 10 minutes. (Proposed values; to confirm under [Q-08](#open-questions) and [Q-40](#open-questions).) |  |  |
| <a id="nfr-42"></a>NFR-42 | If the optimization service, the road-routing service or the geocoder is down, published routes, driver reports, hand-over ([FR-55](#fleet-depots-and-shifts)) and cancellation ([FR-58](#customers-and-orders)) keep working. |  |  |
| <a id="nfr-43"></a>NFR-43 | The system reports the date of the road data in use, and the data is no older than a stated refresh interval. (Interval: [Q-73](#open-questions).) |  |  |
| <a id="nfr-64"></a>NFR-64 | A report the server has acknowledged ([FR-70](#publishing-and-the-driver-app)) is not lost when any one service crashes or restarts. (How much acknowledged data may be lost between backups if the VM is lost: [Q-77](#open-questions).) Priority: Must (proposed). |  |  |
| <a id="nfr-65"></a>NFR-65 | A driver's report can be retried safely, as [BR-28](#business-rules-and-invariants) requires: its request key is scoped to the signed-in user, and the same key with a different body is refused. (ADR pending: driver report sync and idempotency) Priority: Must (proposed). |  |  |
| <a id="nfr-66"></a>NFR-66 | If map tiles cannot be loaded, the console still lists routes and stops and the driver app still shows the route, its stops and Navigate ([FR-28](#publishing-and-the-driver-app)), with a notice that the map is unavailable. Priority: Should (proposed). |  |  |

### Platforms and deployment

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-15"></a>NFR-15 | The dispatcher console is a desktop web app. The driver app runs on iOS, Android and in a browser, built from one codebase. |  |  |
| <a id="nfr-16"></a>NFR-16 | Every server-side component starts with one Docker Compose command. CI builds and tests every change. |  |  |
| <a id="nfr-44"></a>NFR-44 | The server states the oldest supported version of the driver app. A version below it tells the driver to update, and every other version keeps working ([NFR-21](#nfr-21)). (Range, and a below-minimum app in the middle of a route: [Q-73](#open-questions). ADR pending: supported driver app versions) |  |  |
| <a id="nfr-47"></a>NFR-47 | A full shift of location reporting ([FR-31](#publishing-and-the-driver-app)) stays within a stated battery limit, measured on real phones ([Q-16](#open-questions)). (Limit: [Q-38](#open-questions).) |  |  |
| <a id="nfr-52"></a>NFR-52 | The monthly running cost stays within a stated budget, checked against the bill each pilot month, and geocoding has a per-organization cap. (Budget, and how many organizations share one VM: [Q-62](#open-questions). The cap and what happens past it: [Q-73](#open-questions). Whether open sign-up stays: [Q-41](#open-questions).) |  |  |
| <a id="nfr-67"></a>NFR-67 | The console supports the current and previous major versions of Chrome, Edge, Firefox and Safari on screens of at least 1366×768. (Whether Safari is needed: [Q-82](#open-questions).) Priority: Should (proposed). |  |  |

### Usability, accessibility and localization

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-27"></a>NFR-27 | Every screen of the console and the driver app is available in Hebrew, laid out right to left, and in English. Mixed text such as addresses with numbers keeps its correct reading order, checked against a stated list of sample strings. (ADR pending: interface languages. Further languages: [Q-44](#open-questions).) |  |  |
| <a id="nfr-45"></a>NFR-45 | The driver app needs no action while the vehicle is moving, can be used one-handed, and is readable in sunlight. No prompt hides the route until the driver answers it, and that includes [FR-97](#publishing-and-the-driver-app)'s confirmation. Checked by hand on real phones with a scripted checklist before each pilot build ([Q-16](#open-questions)). |  |  |
| <a id="nfr-46"></a>NFR-46 | Both clients meet WCAG 2.2 level AA: automated checks in CI, and a manual audit of the rest before the pilot. (Whether [IS 5568](https://www.boia.org/blog/israels-digital-accessibility-laws-an-overview), the Israeli web-accessibility standard, which that page says follows WCAG 2.0 AA, applies, and to which client: [Q-59](#open-questions). ADR pending: security and accessibility standards) |  |  |
| <a id="nfr-68"></a>NFR-68 | Dates are shown as DD/MM/YYYY, times on a 24-hour clock, distances in kilometres, weights in kilograms and money in shekels (₪), in both interface languages. (Capacity units: [Q-11](#open-questions).) Priority: Should (proposed). |  |  |
| <a id="nfr-69"></a>NFR-69 | Every error message says what happened and what the user can do next, in the user's language. A stack trace or a bare internal code is never shown. Every error code has Hebrew and English text and a next step. Sign-in and recovery answer the same whether or not the identifier exists. Priority: Should (proposed). |  |  |
| <a id="nfr-70"></a>NFR-70 | These actions ask for confirmation that names what will change: publishing, accepting a re-optimization, cancelling an order, handing over a route, recording an outcome on a driver's behalf ([FR-73](#live-operations-and-re-optimization)) and deactivating a user. A driver's own report on a stop does not, because [NFR-45](#nfr-45) bars a prompt that hides the route. (Correcting a mistaken outcome: [Q-33](#open-questions).) Priority: Should (proposed). |  |  |
| <a id="nfr-71"></a>NFR-71 | In the driver app every touch target is at least 48 by 48 density-independent pixels, and text follows the phone's font size setting without breaking the layout ([NFR-45](#nfr-45)). Priority: Should (proposed). |  |  |

### Compliance and personal data

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-17"></a>NFR-17 | Use of every third-party map or geocoding service complies with its terms. For Google's Geocoding API this means the restriction on use with a non-Google map and the 30-day limit on cached coordinates; both must be resolved before the pilot handles real data. |  |  |
| <a id="nfr-18"></a>NFR-18 | Maps built on OpenStreetMap data show the attribution its licence requires. |  |  |
| <a id="nfr-19"></a>NFR-19 | Personal data — customer names, addresses and phone numbers, driver location pings, delivery photos and signatures — never appears in logs. (Retention periods and legal basis for tracking drivers: [Q-14](#open-questions).) (See also NFR-30, NFR-31 and NFR-49.) |  |  |
| <a id="nfr-30"></a>NFR-30 | Driver location is collected only while that driver's route is in progress ([FR-68](#publishing-and-the-driver-app)), and the app shows the driver when it is being collected. (This proposes part of the answer to [Q-15](#open-questions).) |  |  |
| <a id="nfr-31"></a>NFR-31 | Each kind of personal data has a stated retention period and is deleted automatically when it ends. An organization that leaves can get its data exported, and it is then deleted. (Periods: [Q-14](#open-questions). Who runs it, and through what: [Q-73](#open-questions).) |  |  |
| <a id="nfr-48"></a>NFR-48 | The driver app explains why it needs background location before it asks for the permission. If it is distributed through Google Play ([Q-16](#open-questions)), this follows [Google Play's rule on background location](https://support.google.com/googleplay/android-developer/answer/9799150?hl=en). Apple's rules were not checked. ([Q-48](#open-questions).) |  |  |
| <a id="nfr-49"></a>NFR-49 | A person's personal data can be found, corrected and deleted on request, and reports keep their non-personal figures afterwards. (Rights and duties: [Q-42](#open-questions). Who runs it, and through what: [Q-73](#open-questions).) |  |  |
| <a id="nfr-50"></a>NFR-50 | Pilot data used for research ([FR-43](#research-and-evaluation), [FR-46](#research-and-evaluation)) is pseudonymized and is never committed to the repository: every personal or access field is replaced: names, phones, notes, street addresses, apartment details, entrance codes and outside references. This binds `src/research` too. (Coordinates, which identify a home but which a replay needs: [Q-60](#open-questions).) |  |  |
| <a id="nfr-51"></a>NFR-51 | The licence of every dependency is compatible with the project's own licence ([ADR-017](../ADRs/ADR-017-proprietary-licence.md)), and the notices those licences require ship with the apps. (ADR pending: dependency licence check) |  |  |
| <a id="nfr-72"></a>NFR-72 | A data inventory lists every kind of personal data DispatchFlow keeps: where it is stored, why, for how long ([NFR-31](#nfr-31)) and who can read it. It is kept current with each release, and everything it lists counts as personal data under [NFR-19](#nfr-19). (What the law requires of it: [Q-42](#open-questions).) Priority: Should (proposed). |  |  |
| <a id="nfr-73"></a>NFR-73 | The driver app asks only for the phone permissions it uses, location ([FR-31](#publishing-and-the-driver-app)), camera ([FR-30](#publishing-and-the-driver-app)) and notifications ([FR-71](#publishing-and-the-driver-app)), after sign-in and before a route starts, never during a route in progress, and keeps working, with that feature degraded, when one is refused. (A refused location permission: [Q-15](#open-questions).) Priority: Should (proposed). |  |  |

### Observability and maintainability

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-20"></a>NFR-20 | Every service writes structured logs carrying a correlation id propagated across every hop, exports OpenTelemetry traces and metrics, and exposes health and readiness endpoints. Solve latency, re-optimization latency, job queue depth, error rate, dropped-order rate and on-time percentage are measured. |  |  |
| <a id="nfr-21"></a>NFR-21 | Every API — client to backend, backend to optimization service — is described by a versioned OpenAPI contract, and both sides are tested against it. |  |  |
| <a id="nfr-74"></a>NFR-74 | Errors in both clients and crashes of the driver app reach the team with the app version and trace id ([NFR-20](#nfr-20)), and carry no personal data ([NFR-19](#nfr-19)), credentials or tokens. (ADR pending: client error and crash reporting) Priority: Should (proposed). |  |  |
| <a id="nfr-75"></a>NFR-75 | The team is alerted when the solve failure rate, the re-optimization latency of [NFR-07](#nfr-07), the job queue depth or the disk use passes a stated threshold, or when a backup fails ([NFR-28](#nfr-28)). (Thresholds: [Q-73](#open-questions).) Priority: Should (proposed). |  |  |
| <a id="nfr-76"></a>NFR-76 | Logs, traces and metrics are kept for a stated period and then deleted. (Period: [Q-14](#open-questions).) Priority: Should (proposed). |  |  |
| <a id="nfr-77"></a>NFR-77 | Every database change follows [ADR-007](../ADRs/ADR-007-postgresql-with-flyway-migrations.md) and is tested in CI against the schema of the last merge to `main`, with data in it, checking the values after the migration. Priority: Should (proposed). |  |  |
| <a id="nfr-79"></a>NFR-79 | A dispatcher guide and a driver guide exist in Hebrew and English, and an operations runbook covers deployment, restore ([NFR-28](#nfr-28)), secret rotation ([NFR-03](#nfr-03)) and handling an outage ([NFR-29](#nfr-29)). Priority: Should (proposed). |  |  |

### Testing and verification

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-80"></a>NFR-80 | The feasibility validator ([BR-15](#business-rules-and-invariants), [BR-42](#business-rules-and-invariants)) is tested on its own. It accepts published best-known Solomon solutions, under a stated rounding convention, and solutions exactly on a limit, such as a load equal to capacity. It rejects a catalogue of crafted solutions that together break each of BR-03 to BR-07, BR-14, BR-39 and BR-40, and names the rule each one breaks. A fake solver that returns an infeasible solution fails its solve job, lists the violations and stores no plan. Priority: Must (proposed). |  |  |
| <a id="nfr-82"></a>NFR-82 | Property-based tests generate small random instances from a recorded seed, under a solution or iteration budget and never a wall-clock limit. The validator ([BR-15](#business-rules-and-invariants)) accepts whatever either solver returns, so a rejected solution or a solver error fails the test, and on instances built around a known full solution every order is served. The same holds for generated re-optimizations of a published plan, where locked stops ([BR-10](#business-rules-and-invariants)) also stay in place. The DispatchFlow solver gives the same solution for the same input, seed and iteration budget ([NFR-13](#nfr-13)), in separate processes too. Priority: Should (proposed). |  |  |
| <a id="nfr-83"></a>NFR-83 | End-to-end tests run UC-06, UC-07, UC-08 and UC-10 ([Use cases](#use-cases)), each main flow and at least one listed failure, in CI against the Docker Compose stack ([NFR-16](#nfr-16)), with a controllable clock, fixed road data and no outside network. (Which driver build they use: [Q-16](#open-questions).) Priority: Should (proposed). |  |  |

## Non-goals

DispatchFlow deliberately does not:

- **Use live traffic data.** Travel times come from the road network with time-of-day
  multipliers; ETA drift is a stated, known limitation. (ADR pending:
  no live traffic) (See also FR-90.)
- **Handle payments.** (See also Q-57.)
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
- **Keep payroll, attendance or statutory driver-hours records.** Reopen when a customer requires
  it or a legal duty is confirmed ([Q-39](#open-questions)). (ADR pending: scope cuts for the
  first release)
- **Manage inventory or warehouses.** Reopen when a pilot customer cannot load vans without it.
  (ADR pending: scope cuts for the first release)
- **Manage fleet maintenance.** Reopen when a customer asks to plan around scheduled servicing.
  (ADR pending: scope cuts for the first release)
- **Forecast demand.** Reopen when a customer needs a plan prepared before its orders exist.
  (ADR pending: scope cuts for the first release)
- **Let one user belong to several organizations.** Reopen when a real driver or dispatcher works
  for two organizations. (ADR pending: scope cuts for the first release)
- **Integrate vehicle telematics or trackers.** Positions come only from the driver app
  ([FR-31](#publishing-and-the-driver-app)). Reopen when a customer's vans already carry trackers.
  (Proposed, 2026-11-03)
  (ADR pending: scope cuts for the first release)
- **Run the dispatcher console offline.** Only the driver app works without a connection
  ([FR-69](#publishing-and-the-driver-app), [FR-70](#publishing-and-the-driver-app)); printed routes
  ([FR-72](#publishing-and-the-driver-app)) are the console's fallback. Reopen when a customer's
  dispatcher cannot get through an outage on printed routes. (Proposed, 2026-11-03)
  (ADR pending: scope cuts for the first release)
- **Take orders from end customers.** There is no storefront or order form for buyers; orders come
  from a dispatcher, an import or, later, an API ([FR-116](#later-capabilities)). Reopen when a customer
  asks to take orders directly from its buyers. (Proposed, 2026-11-03)
  (ADR pending: scope cuts for the first release)
- **Model electric-vehicle range and charging.** Reopen when a customer's fleet includes electric
  vans whose range limits a day. (Proposed, 2026-11-03)
  (ADR pending: scope cuts for the first release)
- **Monitor cargo temperature.** Refrigeration is at most a van attribute
  ([FR-112](#later-capabilities)). Reopen when a customer must keep temperature records for its goods.
  (Proposed, 2026-11-03)
  (ADR pending: scope cuts for the first release)
- **Apply a re-optimization without a dispatcher's acceptance.** A re-optimization may be triggered
  automatically ([Q-07](#open-questions)), but only a dispatcher applies it
  ([FR-36](#live-operations-and-re-optimization), [BR-47](#business-rules-and-invariants)). Reopen when a customer runs days with no dispatcher on duty.
  (Proposed, 2026-11-03)
  (ADR pending: scope cuts for the first release)

> Native mobile apps were a non-goal in the implementation plan. That was reversed on
> 2026-10-09: the driver app ships for iOS and Android
> ([ADR-003](../ADRs/ADR-003-dispatcher-console-and-driver-app.md)). The plan was revised on
> 2026-10-28 to match: the driver app is native and a Must, and live driver location is a Must.
> On the same day the initial presentation stopped marking the driver client optional.

## Open questions

| ID | Question | Blocks |
|---|---|---|
| Q-01 | Which business is the pilot customer, and what does it provide — historical orders and routes, fleet data, access to dispatcher and drivers? Needed before the approval meeting; fallback is synthetic data ([FR-45](#research-and-evaluation)). Data owner: the pilot customer. | FR-40, FR-47, FR-48 and FR-43 |
| Q-02 | Geocoding provider: Hebrew-accuracy test on real addresses against at least one alternative, plus a review of Google's terms on non-Google maps and caching. The chosen provider's terms must also allow keeping the coordinates copied onto customers and depots; if Google stays, those copies are geocoded again on a schedule before its retention runs out (settles Q-25). What does the chosen provider's answer count as partial or ambiguous ([FR-63](#geocoding))? Do its terms and price allow suggestions while typing ([FR-139](#geocoding)), and does the accuracy test show that normalization ([FR-140](#geocoding)) is needed? | FR-12, FR-13, NFR-17 — ADR pending: Google geocoding provider — also FR-63, FR-139, FR-140 |
| Q-03 | Dispatcher map: Leaflet or MapLibre, and which tile source (OpenStreetMap's public tile servers have a usage policy). | FR-24 |
| Q-04 | Real-time channel: WebSocket or server-sent events to the dispatcher console; how route changes reach a backgrounded driver app (push notification, socket, polling). What an alert does when the console is closed. | FR-16, FR-32, FR-33 and FR-34, FR-65, FR-71, FR-95, FR-101, FR-102, BR-27, FR-152 |
| Q-05 | ~~Status sets and Plan transitions.~~ **Resolved** on 2026-10-09 by ADR pending: plan lifecycle publishing stability and drop reasons. Still open: what triggers a published plan's completion — the end of the day or the last delivery. | BR-12 |
| Q-06 | How heavily route stability weighs in the objective, and how it is modelled in OR-Tools. (The measure itself was settled by ADR pending: plan lifecycle publishing stability and drop reasons.) How OR-Tools models stability also decides whether the re-optimization comparison is fair to it. | FR-37, FR-46, NFR-07 |
| Q-07 | Is re-optimization only dispatcher-triggered, or may detected disruptions trigger it? What lateness counts as "at risk"? | FR-34, FR-35 |
| Q-08 | Numeric targets: manual-edit check latency, live-update latency, k6 API load, SUS score, availability, backup and restore. Proposed values, not yet agreed, so the question stays open: manual-edit check within 500 ms at p95; a driver's report reaches the dispatcher's map within 5 s at p95; k6 load of 15 phones and 5 consoles with under 1% errors; SUS of at least 68, although one pilot dispatcher is too few respondents for a pass mark. Backup and restore are proposed in [NFR-28](#nfr-28), and availability in [NFR-29](#nfr-29). One full restore (4 hours) uses nearly all of, or more than, a month's downtime allowance: 1% of 14-hour days is about 3 hours over 22 operating days and about 4.2 hours over 30. Alerts and route changes have no target yet: how fast do they reach the reader ([FR-58](#customers-and-orders), [FR-71](#publishing-and-the-driver-app), [FR-95](#publishing-and-the-driver-app), [FR-101](#live-operations-and-re-optimization), [FR-102](#live-operations-and-re-optimization)), and what if the phone is offline? | NFR-08, NFR-09, NFR-10, NFR-14, FR-48 and NFR-28, NFR-29, FR-58, FR-71, FR-95, FR-101, FR-102, NFR-59 |
| Q-09 | ~~May a plan with flagged violations be published?~~ **Resolved** on 2026-10-09: no ([BR-17](#business-rules-and-invariants), ADR pending: plan lifecycle publishing stability and drop reasons). | FR-23 |
| Q-10 | CSV import: the column format. (Whether a file with invalid rows imports its valid rows was **resolved** on 2026-10-10: it imports nothing, and rows name their customer by address — ADR pending: CSV order import.) Which encodings do real exports use ([FR-61](#customers-and-orders)), how is a file's encoding known (byte order mark, detection or the dispatcher's choice), and what about UTF-16? A damaged UTF-8 file can still be valid Windows-1255, so detection alone can import garbled text. What is the customer file's format ([FR-83](#customers-and-orders)), and what makes a customer row invalid or a duplicate? Which sheet of an Excel workbook is read ([FR-138](#customers-and-orders))? | FR-10 and FR-61, FR-83, FR-84, FR-138 |
| Q-11 | Capacity units (kilograms? litres or cubic metres?) and what happens when an order has no volume. (See also Q-88.) | FR-05, FR-09 — ADR pending: capacity weight and volume, NFR-68 |
| Q-12 | ~~Role permission matrix: which roles may manage fleet, customers and orders, solve, edit, publish, re-optimize.~~ **Resolved** on 2026-10-11: an Admin does everything a Dispatcher does, plus the organization and its users; a Dispatcher runs every operation and reads users; a Driver works their own route only (the matrix under [FR-03](#organization-users-and-roles), ADR pending: authorize every web handler by role). | FR-03 |
| Q-13 | ~~Tenant-isolation mechanism: application-level filtering, PostgreSQL row-level security, or both.~~ **Resolved** on 2026-10-10: application-level filtering, with keys that carry the organization and no row-level security (ADR pending: tenant isolation by filtering and tenant keys). | NFR-02 — ADR pending: multi-tenancy by organization |
| Q-14 | Where proof-of-delivery files are stored; retention periods for pings, photos and signatures; the legal basis for tracking drivers' locations. Retention for the other personal data ([NFR-19](#nfr-19), [FR-87](#customers-and-orders)) too, and how soon a leaving organization's data is deleted ([NFR-31](#nfr-31)). How long are logs, traces and metrics kept ([NFR-76](#nfr-76))? | FR-30, FR-31, NFR-19 and NFR-31, NFR-76, FR-87, FR-160 |
| Q-15 | When location reporting starts and stops, and how often it pings (freshness versus battery). What the app does when the driver refuses, limits or later revokes background location permission, and whether the dispatcher is told that the driver's location is unavailable. How old may a position be before it counts as stale, and is its age counted from the phone's clock or the server's? | FR-31 and FR-68, FR-74, BR-24, NFR-30, NFR-73 |
| Q-16 | How the native driver app reaches the pilot's phones (app stores or internal distribution), and which tool runs end-to-end tests on native builds. How background location reporting (FR-31) is verified on real iOS and Android phones: automated, or a scripted device checklist run before each pilot build. The same goes for the other rows only a real phone can verify. | FR-31, NFR-15 and FR-71, NFR-39, NFR-45, NFR-47, NFR-48, NFR-61, NFR-71, NFR-73, NFR-83 — [ADR-003](../ADRs/ADR-003-dispatcher-console-and-driver-app.md) |
| Q-17 | Can the DispatchFlow solver, written in Python, reach enough ALNS iterations within the 30-second budget to compete? If not, what changes? (Evidence of 2026-10-10: about 1,800 iterations in 30 s on a 200-stop day, after constant-time insertion checks; see [optimization-engine.md](../architecture/optimization-engine.md). Whether that competes is for the benchmarks.) | FR-42, NFR-07, NFR-25 — [ADR-002](../ADRs/ADR-002-core-backend-and-separate-optimization-service.md) |
| Q-18 | When to adopt PostGIS — which first feature needs spatial queries. | [ADR-007](../ADRs/ADR-007-postgresql-with-flyway-migrations.md) |
| Q-19 | ~~Which reasons the system can give for a dropped order, and how they are determined.~~ **Resolved** on 2026-10-09 by ADR pending: plan lifecycle publishing stability and drop reasons. | FR-18 |
| Q-20 | Time-of-day multipliers: which time buckets, where they apply (matrix or ETA calculation), and how they and the ETA buffer are calibrated from actual arrivals. | FR-90, NFR-33 and ADR pending: no live traffic |
| Q-21 | Default solve time limit, and who may change it. What limit does a re-optimization have, and when does it start? What margin past the limit makes a solve or a re-optimization count as failed, how long may either wait in the queue, and does a queued re-optimization survive a restart? | FR-19 and BR-26, BR-32, FR-125 |
| Q-22 | Which Gehring-Homberger instance sizes run in CI and which only in the offline study. | FR-43 |
| Q-23 | Re-optimization may move a stop to a vehicle that is not carrying its goods. For now goods are assumed transferable (ADR pending: validator and reoptimization problem conventions). Should a vehicle that takes a moved or new order visit a depot first, and how are such reloads modelled in both solvers and the validator? | FR-07, FR-11, FR-35 and BR-05, FR-111 |
| Q-24 | ~~Should a day with a vehicle withdrawn mid-route be compared with its baseline, and on what?~~ **Resolved** on 2026-10-10: it is compared on kilometres and on-time, without driver-hours, and the comparison names the withdrawn vehicles (FR-39). | FR-39 |
| Q-25 | ~~Must geocoded coordinates copied onto customers and depots expire with the geocode cache, and how?~~ **Resolved** on 2026-10-10: it is settled with the provider choice. The chosen provider's terms must allow keeping the copies; if Google stays, they are refreshed on a schedule. That condition now belongs to Q-02, and nothing changes in code until Q-02 is decided. | NFR-17, FR-12, FR-13 |
| Q-26 | ~~May the last Admin of an organization be demoted?~~ **Resolved** on 2026-10-10: no ([BR-19](#business-rules-and-invariants)). | FR-01, FR-02 |
| Q-27 | ~~When a Driver's role changes, what happens to their driver record in fleet?~~ **Resolved** on 2026-10-10 by ADR pending: fleet checks the driver role when it reads: fleet stops counting the record while the user is not a Driver, and another driver may take its van. | FR-02, FR-06 |
| Q-28 | ~~Should publishing also catch a shift, capacity or depot change made after the solve?~~ **Resolved** on 2026-10-10 by ADR pending: recheck every route when a plan is published: every route is re-checked through `evaluate` against the current fleet, a broken rule refuses publishing, and the re-checked times are published when nothing breaks ([BR-21](#business-rules-and-invariants)). | BR-05, BR-06, BR-20 |
| Q-29 | The parameters of [NFR-24](#nfr-24): the value of N, meaning how close to OR-Tools the DispatchFlow solver must come on static instances; the time limit both solvers get; the number of runs *k* per solver and instance; and the drop penalty and fixed-cost setting of the shared objective in the study, since the Solomon instances carry no priorities to derive a penalty from. To be agreed with the academic advisor before the study. | NFR-24 |
| Q-30 | The parameters of [NFR-25](#nfr-25): the value of T, the time limit within which one new order is inserted into a published plan; the set of shorter and longer limits both solvers are also run at; and how the study's insertions are sampled (which plans, which new orders, how many). Q-17's evidence bears on what is reachable. To be agreed with the academic advisor before the study. | NFR-25, FR-46 |
| Q-31 | What counts as a match: exact text, normalized text or the same coordinates? A CSV row's address matches no customer, or several. Create the customer and flag it, reject the row, or let the dispatcher choose? Suggested owner: Team, pilot dispatcher. | FR-10, FR-60, FR-134 |
| Q-32 | When a stop can't make its window during re-optimization: drop it and alert, allow it late with a flag (this changes [BR-03](#business-rules-and-invariants), the validator and the [NFR-25](#nfr-25) comparison), or have the dispatcher widen the window through [FR-57](#customers-and-orders)? Where do the goods go? Hard windows mean today that a stop 10 minutes late is dropped with its goods on the van. Suggested owner: Team, pilot customer. | BR-03, BR-15, BR-23, FR-35, FR-37, FR-46, FR-57, FR-103, NFR-25 |
| Q-33 | How is a mistaken final outcome handled: it stays final with a note, or a dispatcher corrects it with an audit record? Suggested owner: Team. | BR-18, BR-27, BR-28, BR-33, FR-38, FR-73, NFR-70 |
| Q-34 | What manual control exists on a published plan: none, moving a stop to a named route with violation checks, or re-optimizing with pinned stops? Must [FR-22](#manual-editing) be a Must for the pilot? [FR-55](#fleet-depots-and-shifts) and [FR-58](#customers-and-orders) already change a published plan without an optimization, while [FR-22](#manual-editing) and [FR-32](#publishing-and-the-driver-app) say manual edits happen only before publishing. [BR-47](#business-rules-and-invariants) states the default until this is answered. Suggested owner: Team, pilot dispatcher. | BR-12, BR-16, FR-22, FR-23, FR-32, FR-35, FR-55, FR-58, FR-91, BR-47 |
| Q-35 | May one person dispatch and drive? Multiple roles, a separate account, or not supported? Suggested owner: Team. | FR-02, FR-03, BR-02, BR-20 |
| Q-36 | One driver per van per date, or two part-day drivers sharing a van? Suggested owner: Team, pilot customer. | FR-06, FR-15, FR-54, FR-55, FR-79, BR-06, BR-20, BR-39 |
| Q-37 | How do drivers without email sign in and recover access: a phone with SMS (a cost), or Admin-issued credentials? What do other users recover through? Suggested owner: Team. | FR-02, FR-52, NFR-01, NFR-35, NFR-36, FR-132 |
| Q-38 | Are the phones company-owned or personal, which models and OS versions, and what battery use is acceptable over a full shift? Whether personal phones change the consent position needs verification. Suggested owner: Pilot customer, team. | FR-31, FR-77, NFR-15, NFR-30, NFR-44, NFR-47, Q-15, Q-16 |
| Q-39 | Must routes leave time for driver breaks? [Wikipedia](https://en.wikipedia.org/wiki/Israeli_labor_law), which is not the statute, says a work day of at least six hours gives 45 minutes of rest, including an uninterrupted 30-minute break. Rules specific to drivers were not found. Needs verification against the Hours of Work and Rest Law itself. How long is a break, where does it fall, and how do the validator and [BR-06](#business-rules-and-invariants) treat it? Must the organization keep records of driver hours ([Non-goals](#non-goals))? Suggested owner: Team, legal advice. | BR-06, BR-15, FR-17, FR-81, [Non-goals](#non-goals) |
| Q-40 | What is the pilot protocol: length, operating hours and days, a shadow period first, collecting a manual baseline, which days are comparable, the target saving X of [NFR-32](#nfr-32), how actuals are measured, support cover during exams, the manual fallback, whether overnight work is in scope, and what acceptance means with no customer? Suggested owner: Team, advisor. | FR-40, FR-47, FR-48, FR-72, FR-106, FR-114, NFR-29, NFR-32, NFR-33, NFR-34 |
| Q-41 | Does [FR-01](#organization-users-and-roles)'s "Anyone can sign up" hold during the pilot? Open sign-up lets anyone spend the paid geocoding quota. Invite-only, verified sign-up, or a per-organization cap? [BR-45](#business-rules-and-invariants) makes an identifier unique across organizations, so a sign-up can reveal that a person already uses DispatchFlow and can squat a driver's identifier: must an owner prove control of an identifier first, and must sign-up answer the same either way? Suggested owner: Team. | FR-01, FR-52, FR-122, NFR-52, BR-45, NFR-69 |
| Q-42 | Who is responsible for the personal data, and is a written pilot agreement or database registration needed? Is hosting abroad allowed? Are sending customer addresses to a third-party geocoder and tracking employees allowed? Are a pharmacy's delivery records health data? What must the privacy notice say ([FR-77](#organization-users-and-roles)), what may a report show about an employee ([FR-109](#reporting-and-baseline)), and what rights does a person have over their data ([NFR-49](#nfr-49))? Can a deletion coexist with rows that keep names ([FR-53](#organization-users-and-roles), [FR-73](#live-operations-and-re-optimization), [FR-104](#live-operations-and-re-optimization), [BR-37](#business-rules-and-invariants)) and with backups ([NFR-28](#nfr-28))? Amendment 13 of the Privacy Protection Law was due to take effect on 14 August 2025 ([Pearl Cohen](https://www.pearlcohen.com/major-amendment-to-israeli-privacy-law-set-to-take-effect/)). All of this needs verification. Suggested owner: Team, legal advice. | Q-02, Q-14, FR-12, FR-31, FR-33, FR-47, FR-77, FR-109, NFR-14, NFR-19, NFR-28, NFR-30, NFR-31, NFR-49, NFR-50, FR-53, FR-73, FR-104, BR-37, FR-160, FR-161, NFR-72 |
| Q-43 | Is priority 1 or 5 the highest, and what is the default in a CSV row without one? Suggested owner: Team. | FR-09, FR-10, FR-57, FR-84, BR-08, BR-09 |
| Q-44 | Hebrew and English are the interface languages ([NFR-27](#nfr-27)). Which further languages are needed, and for whom, such as Arabic and Russian for drivers? Suggested owner: Team, pilot customer. | FR-121, NFR-27 |
| Q-45 | What is the service area's boundary under "Israel only", and how is an address outside it treated? Suggested owner: Team. | FR-14, FR-63, FR-64, BR-24 |
| Q-46 | What happens to a published plan for a day that doesn't run? [BR-12](#business-rules-and-invariants) says "there is no cancel", and what triggers a plan's completion is still open ([Q-05](#open-questions)). What happens to an order placed or put back on a past date, or on a date whose plan is completed ([BR-35](#business-rules-and-invariants), [FR-75](#live-operations-and-re-optimization))? Suggested owner: Team. | BR-12, BR-34, BR-35, Q-05, FR-11, FR-15, FR-38, FR-39, FR-75 |
| Q-47 | Are failure reasons a closed list, free text, or both? Suggested owner: Team, pilot customer. | FR-29, FR-73, BR-11, FR-161 |
| Q-48 | Who owns the developer accounts, how long does store review take for an app using background location, and what do Apple's rules on it require? All need verification and could move the pilot date. Suggested owner: Team. | Q-16, FR-31, FR-47, NFR-48 |
| Q-49 | [FR-38](#reporting-and-baseline)'s measures have gaps. Failed stops are left out of the on-time percentage, so marking late stops *failed* improves the score. A stop with no arrival report has no on-time figure. A driver's phone clock may be wrong. And distance may be planned or actual ([FR-106](#reporting-and-baseline) shows both). Which of these need a rule? Suggested owner: Team. | FR-38, FR-41, FR-70, FR-73, FR-106, NFR-32, NFR-33, BR-44 |
| Q-50 | [FR-39](#reporting-and-baseline)'s baseline: does the nearest-neighbour baseline obey the business rules (windows, capacity, shifts), and does comparing one plan against another prove anything, when [FR-40](#reporting-and-baseline) and [NFR-32](#nfr-32) compare against the customer's own routes? Suggested owner: Team, advisor. | FR-39, FR-40, FR-108, NFR-32 |
| Q-51 | [BR-08](#business-rules-and-invariants) drops orders strictly by priority, and among equal priorities the fewest. If the objective's drop penalties ([BR-09](#business-rules-and-invariants)) are proportional, they can trade many low-priority orders against one high-priority order, which [BR-08](#business-rules-and-invariants) forbids, and a time-limited search cannot prove "fewest". Which one holds, how is it tested, and may a pin ([FR-91](#daily-planning)) displace a higher-priority order, or drop an order that could otherwise be served? How is the fact behind a *displaced* order found ([FR-144](#daily-planning))? May a lock or a limit on a re-optimization ([FR-147](#manual-editing), [FR-159](#live-operations-and-re-optimization)) keep lower-priority stops while a higher-priority order drops? Suggested owner: Naim, advisor. | BR-08, BR-09, FR-17, FR-42, FR-46, FR-91, NFR-24, NFR-25, FR-144, FR-147, FR-159 |
| Q-52 | Does "in-progress" in [BR-10](#business-rules-and-invariants) include a stop the driver is driving towards? If it does, re-optimization cannot move the next stop; if it does not, a driver can be sent elsewhere after leaving. Suggested owner: Team. | BR-10, BR-25, FR-35 |
| Q-53 | What destination does the Navigate action of [FR-28](#publishing-and-the-driver-app) send: the address or the stored coordinates? And does an address change clear a pin the dispatcher corrected by hand ([BR-13](#business-rules-and-invariants))? Suggested owner: Team. | FR-14, FR-28, FR-56, BR-13 |
| Q-54 | [NFR-22](#nfr-22) revokes a whole session when a refresh token that was already exchanged is presented again. A retry on weak signal, after the exchange worked but its reply was lost, looks like reuse and would sign the driver out. How should that be handled? Suggested owner: Team. | NFR-22, FR-70 |
| Q-55 | [FR-07](#fleet-depots-and-shifts): how does a vehicle return to service after it was marked unavailable, and what happens to a vehicle that is out of service before the day starts? How does a driver marked unavailable ([FR-82](#fleet-depots-and-shifts)) come back, and can a deactivated user ([FR-53](#organization-users-and-roles)) be reactivated? Suggested owner: Team. | FR-07, FR-35, FR-53, FR-54, FR-82, BR-20 |
| Q-56 | What does [FR-50](#later-capabilities) (Could) add beyond [FR-04](#fleet-depots-and-shifts) and [FR-05](#fleet-depots-and-shifts) (Must), which already let a dispatcher define depots and give each vehicle a start depot and an end depot? Suggested owner: Team. | FR-50, FR-04, FR-05 |
| Q-57 | Does the non-goal "Handle payments" cover cash on delivery, and billing for DispatchFlow itself? Suggested owner: Team. | [Non-goals](#non-goals) |
| Q-58 | What do the 30 seconds in Purpose and scope, and the limit in [NFR-06](#nfr-06), include: waiting in the queue, building the preview, the transfer to the console? And "under 30 seconds" differs from [NFR-07](#nfr-07)'s 99th percentile. Does an open preview count under [BR-38](#business-rules-and-invariants), and how old may a preview be when it is accepted ([BR-25](#business-rules-and-invariants))? Does building the travel-time matrix count toward a solve's limit ([NFR-57](#nfr-57))? Suggested owner: Team. | BR-25, BR-32, BR-38, FR-19, NFR-06, NFR-07, NFR-25, NFR-41, NFR-57 |
| Q-59 | Does IS 5568, the Israeli web-accessibility standard ([BOIA](https://www.boia.org/blog/israels-digital-accessibility-laws-an-overview)), apply to the console, to the driver app, or only to a public page such as [FR-117](#later-capabilities)? Needs verification. Suggested owner: Team, legal advice. | NFR-46, FR-117 |
| Q-60 | Does Ben-Gurion University require ethics approval for the usability study ([FR-48](#research-and-evaluation)) and for the use of pilot data ([FR-47](#research-and-evaluation), [NFR-50](#nfr-50))? How may pilot coordinates be used in research, given that they identify a home and a replay ([FR-46](#research-and-evaluation)) needs them? Suggested owner: Team, advisor. | FR-43, FR-46, FR-47, FR-48, NFR-50, FR-168 |
| Q-61 | Platform operator: what does a suspension do, and who may see tenant data for support, given [BR-01](#business-rules-and-invariants)? The operator belongs to no organization, which [FR-01](#organization-users-and-roles) and [BR-01](#business-rules-and-invariants) forbid today: is the operator an exception to them, or outside the product's data? Suggested owner: Team. | BR-01, FR-01, FR-78, FR-168 |
| Q-62 | How many organizations share one VM ([NFR-14](#nfr-14), [NFR-41](#nfr-41)), how long may one organization's solve delay another's, and what is the monthly budget ([NFR-52](#nfr-52))? Suggested owner: Team. | NFR-14, NFR-41, NFR-52 |
| Q-63 | **Which Musts can three people deliver by June 2027, when the timeline on slide 12 of the [initial presentation](../presentation/DispatchFlow_Presentation.pptx) ends?** When the proposed rows were added, 61 of the 122 functional requirements were Must. Can the pilot Musts be separated from the academic ones ([FR-20](#daily-planning), [FR-42](#research-and-evaluation) to [FR-46](#research-and-evaluation)), and should [FR-13](#geocoding), [FR-21](#daily-planning) and [FR-47](#research-and-evaluation) stay Must? [FR-42](#research-and-evaluation) itself says no product capability depends on it. [ADR-005](../ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md) says "We build both", behind the port of [ADR-004](../ADRs/ADR-004-pluggable-solver-port.md), so dropping a solver row needs an ADR that supersedes them. Neither ADR's decision states a priority, though ADR-005 records that the plan's Should and Could were revised to follow it. The review of 2026-11-03 added 46 functional rows, of which one is a Must because Level 1 of [NFR-40](#nfr-40)'s standard requires it ([FR-123](#organization-users-and-roles)), so 62 of 168 are Must. It also added 9 business rules and 28 non-functional requirements, and proposes 15 of those 37 as Must, two of them only in part, which that count does not show. Suggested owner: Team, advisor. | FR-13, FR-20, FR-21, FR-42 to FR-47 |
| Q-64 | A report can reach the server late ([FR-70](#publishing-and-the-driver-app), [BR-27](#business-rules-and-invariants)) after another driver or a dispatcher ([FR-73](#live-operations-and-re-optimization)) recorded a different outcome, or after the order was cancelled ([FR-58](#customers-and-orders)). Which record wins, can a cancelled order become delivered, and who resolves it? How is "while the stop was on their route" judged, given that a phone's clock may be wrong ([Q-49](#open-questions))? Suggested owner: Team, pilot dispatcher. | BR-18, BR-27, BR-28, BR-30, FR-58, FR-70, FR-73 |
| Q-65 | When a route is handed over ([FR-55](#fleet-depots-and-shifts)): may the new driver already hold a route ([FR-27](#publishing-and-the-driver-app) says today's route), can a started route be handed over, and is a route that breaks the new driver's shift ([BR-06](#business-rules-and-invariants)) refused, flagged or allowed? Is the new driver checked as under [BR-20](#business-rules-and-invariants): active, available and holding the Driver role? Suggested owner: Team, pilot customer. | FR-27, FR-55, BR-06, BR-20, BR-39 |
| Q-66 | When a failed or dropped order is put back ([FR-75](#live-operations-and-re-optimization)), is that a new order linked to the old one, or a second stop on the same order? A new order leaves [BR-07](#business-rules-and-invariants), [BR-30](#business-rules-and-invariants) and [BR-35](#business-rules-and-invariants) as they are. A second stop is an exception to [BR-07](#business-rules-and-invariants), and needs its date and window changed. Suggested owner: Team. | FR-57, FR-58, FR-75, BR-07, BR-30, BR-33, BR-35 |
| Q-67 | Reports queued on a phone ([FR-70](#publishing-and-the-driver-app)) may still be unsent when the driver's session ends, through a password change ([NFR-22](#nfr-22)), deactivation ([FR-53](#organization-users-and-roles)), ending a session by hand ([FR-127](#organization-users-and-roles)) or sign-out ([NFR-39](#nfr-39)). How are they authenticated, and do they survive sign-out? What happens to the stored route data of [NFR-39](#nfr-39) when a session is revoked, including on a phone that stays offline? Suggested owner: Team. | FR-53, FR-70, BR-27, NFR-22, NFR-39, FR-123, FR-127 |
| Q-68 | [FR-57](#customers-and-orders) and [FR-58](#customers-and-orders) define changes only on a published plan. When an order on a ready plan, or one being solved, is changed or cancelled, is its stop removed at once, or does publishing refuse ([BR-21](#business-rules-and-invariants), [BR-22](#business-rules-and-invariants))? On a published plan, does a date change take the stop off at once, as [FR-58](#customers-and-orders) does, and is a route that a change breaks flagged before the re-optimization? May an order be changed or cancelled after its driver marks *arrived* ([FR-29](#publishing-and-the-driver-app), [BR-30](#business-rules-and-invariants))? Suggested owner: Team. | FR-57, FR-58, BR-07, BR-12, BR-21, BR-22, BR-30 |
| Q-69 | Which driver-app rows bind the web build? [FR-31](#publishing-and-the-driver-app) carves it out, and `src/frontend/driver-app/CLAUDE.md` says a phone-only feature must degrade in the browser, but [FR-69](#publishing-and-the-driver-app), [FR-70](#publishing-and-the-driver-app), [FR-71](#publishing-and-the-driver-app) and [NFR-39](#nfr-39) do not say how. Suggested owner: Team. | FR-69, FR-70, FR-71, NFR-15, NFR-39, FR-155, NFR-61, NFR-71, NFR-73 |
| Q-70 | What counts toward the 200 stops of [NFR-05](#nfr-05): orders or stops, failed and cancelled ones, stops a put-back adds ([FR-75](#live-operations-and-re-optimization))? Where is the limit refused: at import, at entry or at the solve? [FR-18](#daily-planning) has no over-limit reason, and [BR-22](#business-rules-and-invariants) needs every order accounted for. Suggested owner: Team. | BR-22, BR-36, FR-10, FR-11, FR-18, FR-75, NFR-05 |
| Q-71 | Route lifecycle ([FR-68](#publishing-and-the-driver-app)): what if a driver forgets to start but reports stops ([FR-29](#publishing-and-the-driver-app)), given [NFR-30](#nfr-30)? What if a driver never finishes, given that [NFR-30](#nfr-30) keeps collecting location? May a driver finish with stops pending? May re-optimization add stops to a finished route? What state is a route withdrawn mid-route ([FR-39](#reporting-and-baseline)) in? Does a finished route bear on a plan's completion ([Q-05](#open-questions))? What does a driver see when alerted about a route for a later date ([FR-152](#publishing-and-the-driver-app)), given that [FR-27](#publishing-and-the-driver-app) shows today's route? Suggested owner: Team. | FR-29, FR-31, FR-33, FR-39, FR-68, NFR-30, Q-05, FR-27, FR-152 |
| Q-72 | What does changing or retiring a record ([FR-54](#fleet-depots-and-shifts), [FR-56](#fleet-depots-and-shifts)) do when it is in use: a van on today's published route, a depot a vehicle starts from, a customer with open orders, a customer's address corrected or a driver's shift shortened on a date with an open plan? Does a retired customer match a CSV row ([FR-60](#customers-and-orders)), do retired vans count toward the limit ([BR-36](#business-rules-and-invariants)), and can a retired driver still sign in ([FR-53](#organization-users-and-roles))? Suggested owner: Team. | FR-53, FR-54, FR-56, FR-60, BR-20, BR-21, BR-25, BR-36, BR-37 |
| Q-73 | Values and mechanisms the proposed rows leave open: how many recovery attempts are allowed ([FR-52](#organization-users-and-roles)); how often a failed geocoding lookup retries, and when it stops ([FR-64](#geocoding)); how late a route start must be to alert, and against what: shift start, or shift start plus the allowance of [FR-67](#daily-planning) ([FR-101](#live-operations-and-re-optimization)); the speed above which a position is impossible, and between which two points it is measured ([BR-24](#business-rules-and-invariants)); how a time in an hour the clock skips or repeats is treated ([BR-31](#business-rules-and-invariants)); what happens past the sign-in limit, whether the 15 minutes roll, and whether a success resets the count ([NFR-35](#nfr-35)); the password policy ([NFR-36](#nfr-36)); upload types and sizes ([NFR-37](#nfr-37)); the road-data refresh interval, and who is told when the data is older ([NFR-43](#nfr-43)); the supported driver-app versions, and whether an app below the minimum keeps its route and sends its queue ([NFR-44](#nfr-44), [FR-70](#publishing-and-the-driver-app)); the geocoding cap and what happens past it ([NFR-52](#nfr-52), [FR-12](#geocoding)); and who runs an organization export or a personal-data request, through what, and how soon ([NFR-31](#nfr-31), [NFR-49](#nfr-49)). Also the distance at which the app offers *arrived* ([FR-155](#publishing-and-the-driver-app)), the API rate limits ([NFR-53](#nfr-53)) and the alert thresholds ([NFR-75](#nfr-75)). The proposed targets also need their measurement conditions: what counts as down, how it is probed and when the 10 minutes start ([NFR-29](#nfr-29)); what a restore covers, and what a failed backup does ([NFR-28](#nfr-28)); the period, and what stands in for a skipped arrival ([NFR-33](#nfr-33)); the day's size and when the clock starts ([NFR-34](#nfr-34)); the sample strings ([NFR-27](#nfr-27)); the pass criteria for one-handed use and sunlight ([NFR-45](#nfr-45)); who runs the manual audit and what passes ([NFR-46](#nfr-46)); shift length and phone state ([NFR-47](#nfr-47)); and the plan size, ping rate and duration behind the proposed values of [Q-08](#open-questions). Suggested owner: Team. | FR-12, FR-52, FR-64, FR-70, FR-101, FR-155, BR-24, BR-31, NFR-27, NFR-28, NFR-29, NFR-31, NFR-33, NFR-34, NFR-35, NFR-36, NFR-37, NFR-43, NFR-44, NFR-45, NFR-46, NFR-47, NFR-49, NFR-52, NFR-53, NFR-75, Q-08 |
| Q-76 | Who takes part in usability sessions ([FR-48](#research-and-evaluation)) before a pilot customer exists: classmates, the advisor, a stand-in dispatcher? Does it need ethics approval ([Q-60](#open-questions))? Suggested owner: Team, advisor. | FR-48, Q-60 |
| Q-77 | How much acknowledged data may be lost if the VM is lost between backups? [NFR-28](#nfr-28)'s daily backup could lose up to a day of driver reports that [NFR-64](#nfr-64) promises to keep. Is continuous archiving of the database's write-ahead log needed? Suggested owner: Team. | NFR-28, NFR-64, FR-70 |
| Q-78 | Should routes avoid toll roads such as Route 6 and the Carmel Tunnels, allow them, or count their cost? Is that per organization or per van? Suggested owner: Team, pilot customer. | FR-90, FR-118, BR-09 |
| Q-79 | Does a depot have opening hours, and how many vans can load at once? Must departures be staggered, and does that interact with the departure allowance of [FR-67](#daily-planning)? Suggested owner: Team, pilot customer. | FR-67, FR-129, BR-46 |
| Q-80 | Does any van need a maximum route duration, distance or number of stops beyond its driver's shift ([FR-133](#fleet-depots-and-shifts))? Suggested owner: Team, pilot customer. | FR-133 |
| Q-81 | Is a second sign-in factor needed for Admins during the pilot, and which: an authenticator app or SMS (a cost)? Does Level 1 of [NFR-40](#nfr-40)'s standard require it? Suggested owner: Team. | FR-128, NFR-40 |
| Q-82 | Which browsers and screen sizes must the console support ([NFR-67](#nfr-67))? Does the pilot's dispatcher use Safari or a small laptop? Suggested owner: Team, pilot customer. | NFR-67, NFR-58 |
| Q-83 | How are addresses written in Arabic, or in English transliteration, handled, for Arab towns and mixed cities? Does the chosen geocoder ([Q-02](#open-questions)) find them, and does normalization ([FR-140](#geocoding)) cover them? Suggested owner: Team. | FR-12, FR-140, Q-02 |
| Q-84 | Which statistical method compares the solvers: the test, the significance level, the effect size and how many instances and runs? To be agreed with the academic advisor before the study, together with [Q-29](#open-questions) and [Q-30](#open-questions). Suggested owner: Naim, advisor. | FR-165, NFR-24, NFR-25 |
| Q-85 | When a re-optimization is limited to chosen routes ([FR-159](#live-operations-and-re-optimization)), may stops move into a route left out? Is the limited mode part of the [NFR-25](#nfr-25) study? Suggested owner: Team. | FR-159, NFR-25 |
| Q-86 | The proposed performance values of [NFR-57](#nfr-57) to [NFR-61](#nfr-61): on what hardware, at what plan size and on what connection are they measured, what frame rate holds ([NFR-58](#nfr-58)), what percentile and sample size apply, and are the values right? Suggested owner: Team. | NFR-57, NFR-58, NFR-59, NFR-60, NFR-61 |
| Q-87 | If a route may start at a driver's home ([FR-169](#later-capabilities)), who may see that address, and is it personal data under [Q-42](#open-questions)? Suggested owner: Team, legal advice. | FR-169, Q-42 |
| Q-88 | Small businesses may not know an order's volume in litres. Can a package count ([FR-136](#customers-and-orders)) or a size class stand in for volume, and how does that relate to the units of [Q-11](#open-questions)? Suggested owner: Team, pilot customer. | FR-05, FR-09, FR-136, BR-05, Q-11 |
| Q-89 | An access token outlives the session it belongs to by up to 15 minutes ([FR-53](#organization-users-and-roles), [FR-123](#organization-users-and-roles), [FR-127](#organization-users-and-roles), [NFR-01](#nfr-01)). Does that pass Level 1 of [NFR-40](#nfr-40)'s standard, which asks that a terminated session allows no further use? If not, is a token issued before a user's last revocation refused, at the cost of one lookup per API call? Suggested owner: Team. | NFR-01, NFR-40, FR-53, FR-123, FR-127 |

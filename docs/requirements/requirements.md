# DispatchFlow — Requirements

What the system must do. The [README](../../README.md) describes the product,
[`architecture.md`](../architecture/architecture.md) how it is built, and the
[ADRs](../ADRs/README.md) why.

- **Priorities** are MoSCoW: **Must**, **Should**, **Could**. **Won't** is under
  [Non-goals](#non-goals). *Size* and *Enforced by* stay blank until the team sizes and implements a
  row. BR and NFR rows without a Priority column state theirs at the end of the text, if they have one.
- **Proposals** under issue #3: FR-52 to FR-171, BR-23 to BR-47, NFR-26 to NFR-84, Q-31 to Q-89,
  FR-291, NFR-176, Q-90 to Q-94, every UC row, and the non-goals from *Keep payroll* on. `(proposed)`
  marks a priority the team has not confirmed.
- **Answers of 2026-11-04:** Naim answered the open questions, which added FR-292, BR-102 and NFR-177 onward.
  The team reviews them under issue #3 like the other proposals.
- **Mentor's additions of 2026-11-10:** Avi Segal, the seminar mentor, asked for SMS messages to
  recipients, a delivery rating, leave-at-door orders and proof on every delivery. They added
  FR-348, BR-119, NFR-189, Q-95 and UC-14 onward, and changed FR-30, FR-49, FR-57, FR-70, FR-105,
  FR-125, FR-157, FR-342, BR-03, BR-09, NFR-01, NFR-39, NFR-52, NFR-83, NFR-175, NFR-184, Q-20, Q-42,
  Q-62, Q-63 and Q-92. The team reviews them under issue #3.
- **Split rows** keep their ID for the core statement. Their parts (FR-172, BR-48 and NFR-85 onward)
  sit directly under them, with the same status and priority unless a part states its own.
- **IDs are never reused.** Withdrawn: FR-78, FR-122, BR-01, NFR-02, NFR-41, Q-13, Q-41, Q-61
  ([ADR-024](../ADRs/ADR-024-one-delivery-company-no-multi-tenancy.md): one company and no
  multi-tenancy), and FR-50 ([Q-56]: adds nothing beyond depots and vehicles). Never used: FR-166,
  FR-170, FR-171, NFR-63, NFR-78, NFR-81, NFR-84, Q-74, Q-75.
- **Old citations in ADRs:** NFR-16 (CI) is now NFR-133, NFR-15 (driver app platforms) NFR-132,
  NFR-21 (contract tests) NFR-162, and FR-51 (PyVRP never serves the product) FR-286.

## Purpose and scope

DispatchFlow replaces hand-planned routes at one delivery company
([ADR-024](../ADRs/ADR-024-one-delivery-company-no-multi-tenancy.md): one company and no
multi-tenancy): it plans each day's routes, re-plans them when the day goes wrong, and measures the
result against a baseline. The [README](../../README.md#what-and-why) has the full description.

- **Users:** Admins, Dispatchers and Drivers, as the [permission matrix](#company-users-and-roles)
  sets out. Recipients take part only by SMS, with no account ([FR-49]: SMS messages to recipients).
- **Scope:** Israel only, with OpenStreetMap's Israel road data and Hebrew addresses; one plan per
  day at the scale of [NFR-04] (fleet size limit) and [NFR-05] (plan size limit); Hebrew, right to
  left, and English ([NFR-27]: Hebrew and English interface).
- **Research:** OR-Tools serves the product. Our own solver is built beside it and compared with it
  ([ADR-005](../ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md): OR-Tools first
  with our solver as research, [Solver research targets](#solver-research-targets)).
- **Out of scope:** [Non-goals](#non-goals).

### Use cases

The main processes, each traced to the rows it exercises. They anchor the usability study ([FR-48]:
usability study) and the end-to-end tests ([NFR-83]: end-to-end tests).

| ID | Actor | Goal | Main flow | Alternatives and failures | Requirements |
|---|---|---|---|---|---|
| UC-01 | Admin | Set up DispatchFlow | Sign in with the account created at deployment; set the company's settings | Sign-in fails; a forgotten password is recovered | FR-01, FR-52, FR-125, FR-291, FR-172, FR-183, FR-184, FR-185, FR-186, FR-292, FR-299, FR-294, FR-295, FR-296, FR-346, FR-345, FR-350, FR-375 |
| UC-02 | Admin | Give people access | Create users with roles; they receive first credentials and sign in | A forgotten password is recovered; a leaver is deactivated; the last two Admins cannot be demoted | FR-02, FR-03, FR-52, FR-53, FR-123, BR-19, FR-172, FR-173, FR-174, FR-175, FR-177, FR-178, FR-179, FR-180, FR-293, FR-294, FR-295, FR-296, FR-297, FR-298, FR-345 |
| UC-03 | Dispatcher | Set up the fleet | Define depots; register vans and drivers; set a date's drivers, shifts and vans; retire what is no longer used | A van breaks or a driver falls sick during the day | FR-04 to FR-06, FR-54, FR-56, FR-79, FR-129 to FR-132, FR-189, FR-190, FR-193, FR-195, FR-196, FR-197, FR-198, FR-199, FR-200, FR-305, FR-306, FR-307 |
| UC-04 | Dispatcher | Keep customers current | Create or import customers; check geocoded pins; fix a bad pin by dragging it | No match, several matches, or the geocoder is down | FR-08, FR-12 to FR-14, FR-56, FR-59, FR-63, FR-64, FR-83, FR-134, FR-139, FR-140, FR-215, FR-193, FR-208, FR-216, FR-217, FR-218, FR-210, FR-212, FR-219, FR-220, FR-221, FR-305, FR-306, FR-307, FR-320, FR-318, FR-377 |
| UC-05 | Dispatcher | Load the day's orders | Import a CSV or enter orders by form, or copy a previous day | A file with any rejected row imports nothing and lists every reason | FR-09, FR-10, FR-60 to FR-62, FR-84, FR-85, FR-138, FR-203, FR-204, FR-205, FR-209, FR-211, FR-213, FR-309, FR-319, FR-310, FR-311, FR-312, FR-313, FR-314, FR-364, FR-377 |
| UC-06 | Dispatcher | Plan the day | Run the pre-flight check; solve; review routes on the map, timeline and table; edit by hand | Some orders are dropped with reasons; the solve fails and is requested again | FR-15 to FR-25, FR-65 to FR-67, FR-90, FR-91, FR-141 to FR-151, FR-222, FR-223, FR-224, FR-233, FR-234, FR-225, FR-226, FR-227, FR-228, FR-230, FR-231, FR-232, FR-235, FR-236, FR-237, FR-238, FR-239, FR-240, FR-241, FR-321, FR-322, FR-323, BR-119, BR-120, BR-121, FR-385 |
| UC-07 | Dispatcher | Publish the plan | Publish; every driver is alerted and sees their route | Publishing refuses over violations, changed vans or new orders | FR-26, FR-152, BR-17, BR-20 to BR-22, BR-63, BR-64, BR-65, BR-66, BR-67, BR-68, BR-69, BR-70, BR-71, BR-72, BR-73, FR-336, FR-337, FR-338 |
| UC-08 | Driver | Drive the route | Load the van from the loading list; start the route; navigate to each stop, calling the customer if needed; mark it delivered with proof, or failed with a reason; finish | No connection: reports queue and are sent later; a problem is reported to the dispatcher; a route change arrives mid-route | FR-27 to FR-32, FR-59, FR-68 to FR-72, FR-95 to FR-97, FR-153 to FR-156, FR-242, FR-243, FR-208, FR-244, FR-245, FR-246, FR-247, FR-248, FR-249, FR-250, FR-251, FR-252, FR-324, FR-325, FR-326, FR-327, FR-328, FR-329, FR-330, FR-331, FR-347, FR-365, FR-366, FR-367, FR-368, NFR-204, NFR-205 |
| UC-09 | Dispatcher | Watch the day | Follow vehicles and stop statuses live; act on the alert list | A position is stale; a stop is at risk; a route has not started | FR-33, FR-34, FR-74, FR-101, FR-102, FR-157, FR-158, FR-253, FR-254, FR-260, FR-261, FR-335, FR-361, FR-369, FR-383 |
| UC-10 | Dispatcher | Recover from a disruption | Mark a van or driver unavailable, or add or change orders; re-optimize; review the preview; accept | The preview is declined, fails or is refused because something changed since | FR-07, FR-11, FR-35 to FR-37, FR-57, FR-75, FR-82, FR-103, FR-159, BR-25, BR-26, FR-255, FR-256, FR-206, FR-258, FR-194, BR-82, BR-83, BR-84, BR-85, BR-86, FR-300, FR-301, FR-334, FR-332, FR-315, FR-316, FR-317 |
| UC-11 | Dispatcher | Change a published route without optimizing | Hand a route to another driver, or cancel an order | The order's stop was already reported | FR-55, FR-58, FR-73, BR-30, BR-47, FR-191, FR-192, FR-207, FR-257, BR-92, BR-77, BR-78, FR-302, FR-303, FR-304, FR-333 |
| UC-12 | Dispatcher | Review the day | Read the daily summary and the baseline comparison; export it | Nothing delivered: on-time is shown as unavailable | FR-38 to FR-40, FR-106, FR-107, FR-161, FR-264, FR-265, FR-266, FR-267, FR-268, FR-269, FR-270, FR-271, FR-339, FR-340, FR-341, FR-342, FR-362, FR-370 |
| UC-13 | Team (research) | Compare the solvers | Run the benchmark harness and the disruption simulation from a configuration; analyse the results | A run the validator rejects is reported, as NFR-113 and NFR-114 say | FR-42 to FR-46, FR-51, FR-163 to FR-165, FR-167, FR-168, NFR-24, NFR-25, NFR-62, FR-272, FR-273, FR-274, FR-275, FR-276, FR-277, FR-278, FR-279, FR-280, FR-281, FR-282, FR-286, FR-287, FR-288, FR-289, FR-290, NFR-110, NFR-111, NFR-112, NFR-113, NFR-114, NFR-115, NFR-116, NFR-117, NFR-118, NFR-119, NFR-120, NFR-121, NFR-122, NFR-182 |
| UC-14 | Recipient | Know when the delivery comes, and rate it | Get the "out for delivery", "arriving in about 15 minutes" and "delivered" messages; reply with a rating from 1 to 10 and get a thank-you | The time changes, or the order is dropped, moved or cancelled; the stop fails; a reply that is not a rating gets one reminder; a low rating alerts the dispatcher; the recipient opts out; the SMS provider is down | FR-49, FR-348 to FR-363, FR-371 to FR-384, NFR-189 to NFR-193, NFR-196 to NFR-203 |

## Functional requirements

### Company, users and roles

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-01 | The first Admin account is created when the system is deployed. There is no public sign-up. | Must |  |  |
| FR-291 | The first Admin is created by a step on the server, only when no active Admin exists, with a first password ([FR-294]: first-password rules) and no default credential, and leaves an audit record. | Must (proposed) |  |  |
| FR-292 | On a written request from a person the pilot agreement names, a named team member with server access can run a step that creates a new Admin with a first password ([FR-294]: first-password rules) and leaves an audit record. | Must (proposed) |  |  |
| FR-346 | That step also deactivates each Admin account the request names and ends its sessions, an exception to [FR-174] (the last Admins cannot be deactivated) and [BR-19] (the minimum of active Admins). | Must (proposed) |  |  |
| FR-299 | An Admin is warned at every sign-in while the company has fewer than two active Admins ([BR-19]: the minimum of active Admins). | Must (proposed) |  |  |
| FR-02 | An Admin can create users and give each one a role: Admin, Dispatcher or Driver. | Must |  |  |
| FR-293 | A user has one role, so someone who both dispatches and drives has two accounts with two sign-in identifiers. | Must (proposed) |  |  |
| FR-03 | A user can sign in and use only the functions their role permits, as the [permission matrix](#company-users-and-roles) says. | Must |  |  |
| FR-52 | A user receives first credentials and can recover a forgotten password without help from the DispatchFlow team. These calls are exceptions to [NFR-01] (access token on every call). (ADR pending: sign-in and transport security) | Must (proposed) |  |  |
| FR-172 | Attempts to recover a password are limited to 5 an hour per account. | Must (proposed) |  |  |
| FR-294 | A first password is random, meets [NFR-95] (password policy), expires on first use or after 72 hours, and allows nothing except setting a new password. ([ADR-030](../ADRs/ADR-030-admin-issued-credentials-and-token-revocation.md): Admin-issued credentials and token revocation) | Must (proposed) |  |  |
| FR-295 | A driver signs in with a username, and an Admin issues a new first password if they forget theirs, which ends their old password and sessions. | Must (proposed) |  |  |
| FR-296 | Admins and Dispatchers have an email address and recover a forgotten password through an emailed link. | Must (proposed) |  |  |
| FR-345 | A recovery link works once, expires within 1 hour, and ends every session of the user when used. | Must (proposed) |  |  |
| FR-53 | An Admin can deactivate a user: sign-in stops, every session ends, and their history stays. | Should (proposed) |  |  |
| FR-173 | An access token issued before a user is deactivated is refused at once ([NFR-177]: revoked tokens refused at once). | Should (proposed) |  |  |
| FR-174 | An Admin cannot be deactivated while the company has two or fewer active Admins, and a deactivated Admin does not count toward [BR-19] (the minimum of active Admins). | Should (proposed) |  |  |
| FR-175 | A deactivated driver counts as no driver under [BR-20] (routes publish only with their solved van and driver). | Should (proposed) |  |  |
| FR-297 | An Admin can reactivate a deactivated user, whose history stays intact. | Should (proposed) |  |  |
| FR-298 | A deactivated driver's queued reports cannot be sent, so a dispatcher records those outcomes ([FR-73]: dispatcher records an outcome). | Should (proposed) |  |  |
| FR-77 | Users accept the terms and a privacy notice. Acceptance is recorded and is never asked for during a route. [Q-42] (legal advice on personal data) | Should (proposed) |  |  |
| FR-176 | Drivers see what the app collects about them. | Should (proposed) |  |  |
| FR-123 | A signed-in user can change their own password by giving the current one. | Must (proposed) |  |  |
| FR-177 | A wrong current password counts toward the limit of [NFR-35] (sign-in attempt limit). | Must (proposed) |  |  |
| FR-178 | Changing a password ends every session of that user, as [NFR-90] (role or password change revokes sessions) requires, so they sign in again. | Must (proposed) |  |  |
| FR-179 | The password policy of [NFR-95] (password policy) applies to the new password. | Must (proposed) |  |  |
| FR-180 | An access token issued before a password change is refused at once ([NFR-177]: revoked tokens refused at once). | Must (proposed) |  |  |
| FR-124 | A user can choose their interface language, Hebrew or English ([NFR-27]: Hebrew and English interface). | Should (proposed) |  |  |
| FR-181 | A user's language choice follows them to every device they sign in on. | Should (proposed) |  |  |
| FR-182 | A new user starts with the company's default language ([FR-125]: the company settings page). | Should (proposed) |  |  |
| FR-125 | An Admin can see and change the company's settings in one place: name, dispatch phone ([FR-199]: call dispatch from the app), default interface language and the time window a new customer starts with ([FR-08]: create customers). | Must (proposed) |  |  |
| FR-183 | An Admin can also see and change, in the same place, the departure allowance ([FR-67]: loading time before departure), fixed vehicle cost ([FR-21]: fixed cost per vehicle used) and default solve time limit within [NFR-06] (solve time limit range). | Should (proposed) |  |  |
| FR-184 | Where [FR-89] (default service duration per customer) is built, an Admin can also see and change there the service duration a new customer starts with. | Should (proposed) |  |  |
| FR-185 | Where [FR-108] (savings in money) is built, an Admin can also see and change there the cost per kilometre and per driver-hour. | Should (proposed) |  |  |
| FR-186 | Each change to the company's settings is audited ([NFR-23]: audit records). | Should (proposed) |  |  |
| FR-126 | An Admin can view the company's audit records ([NFR-23]: audit records) and filter them by user, operation, record and date. | Could (proposed) |  |  |
| FR-127 | A user can see their own active sessions, with device and last use, and end any of them. | Could (proposed) |  |  |
| FR-187 | An Admin can end every session of a user without deactivating them. | Could (proposed) |  |  |
| FR-188 | An access token issued before a session is ended is refused at once ([NFR-177]: revoked tokens refused at once). | Could (proposed) |  |  |
| FR-128 | The company can require a second sign-in factor for its Admins. | Could (proposed) |  |  |

**Permission matrix** (FR-03; decided on 2026-10-11, Q-12, ADR pending: authorize every web
handler by role). A ✓ means the role may; the server checks it on every call.

| Capability | Admin | Dispatcher | Driver |
|---|---|---|---|
| Manage the company's details and its users: rename it, register users, change their roles | ✓ | | |
| Read the company's details and its users | ✓ | ✓ | Only the company's details and themselves |
| Manage depots, vehicles, drivers and shifts; customers and orders | ✓ | ✓ | |
| Plan, solve, edit, publish, cancel a published plan and re-optimize; live views and reports | ✓ | ✓ | |
| Work their own route: read it, report stops and proofs, report positions | | | ✓ (BR-02) |
| Record or correct a stop's outcome on a driver's behalf (FR-73, FR-333, proposed) | ✓ | ✓ | |
| Deactivate or reactivate a user (FR-53, FR-297, proposed) | ✓ | | |
| Issue a first password (FR-294, FR-295, proposed) | ✓ | | |
| Start and finish their own route, report a problem, confirm a route change (FR-68, FR-95, FR-97, proposed) | | | ✓ |
| View a stop's proof of delivery (FR-105, proposed) | ✓ | ✓ | Only on their own route |
| Read recipients' ratings and comments (FR-360, FR-362, proposed) | ✓ | ✓ | |
| See which phones opted out of messages, and record an opt-out (FR-380, proposed) | ✓ | ✓ | |
| Change their own password and language; see and end their own sessions (FR-123, FR-124, FR-127, proposed) | ✓ | ✓ | ✓ |
| Read the company's settings (FR-125, proposed) | ✓ | ✓ | Only its name and dispatch phone |
| Change the company's settings, require a second factor, view audit records, end another user's sessions, turn messages to recipients and the rating question on or off (FR-125, FR-126, FR-128, FR-187, FR-350, FR-375, proposed) | ✓ | | |

### Fleet, depots and shifts

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-04 | A dispatcher can define depots, each with a location. | Must |  |  |
| FR-05 | A dispatcher can register vehicles, each with a weight capacity, a volume capacity, a start depot and an end depot. | Must |  |  |
| FR-06 | A dispatcher can register drivers, each with a shift start, a shift end and an assigned vehicle. | Must |  |  |
| FR-07 | A dispatcher can mark a vehicle unavailable during the day, so that re-optimization ([FR-35]: re-optimize after a disruption) redistributes its remaining stops. | Must |  |  |
| FR-300 | A dispatcher can mark a vehicle or driver available again, effective from the next solve or re-optimization. | Must (proposed) |  |  |
| FR-301 | A van out of service before the day starts is left out of that date through [FR-54] (per-date shifts and vans). | Must (proposed) |  |  |
| FR-54 | A dispatcher can set, for a given date, which drivers work, their shift hours and which van each takes. | Must (proposed) |  |  |
| FR-189 | The values registered in [FR-06] (register drivers) are the default for a date. | Must (proposed) |  |  |
| FR-190 | A shift ends after it starts and lies on its date. | Must (proposed) |  |  |
| FR-55 | A dispatcher can hand a route of a published plan to another driver. The new driver sees it, and the old one no longer does. | Must (proposed) |  |  |
| FR-191 | A handed-over route takes that date's van assignment ([FR-54]: per-date shifts and vans) with it. | Must (proposed) |  |  |
| FR-192 | A handed-over route keeps its stops, and no optimization runs. | Must (proposed) |  |  |
| FR-302 | The new driver must be active, available, hold the Driver role and have no route on that date ([BR-39]: one route per van and per driver). | Must (proposed) |  |  |
| FR-303 | A started route can be handed over, and its reported stops stay recorded. | Must (proposed) |  |  |
| FR-304 | A hand-over that breaks the new driver's shift ([BR-06]: routes stay within the driver's shift) is flagged, and the dispatcher confirms it. | Must (proposed) |  |  |
| FR-56 | A dispatcher can see, change and retire depots, vehicles, drivers and customers. | Must (proposed) |  |  |
| FR-193 | A retired record is not offered for new plans and stays on past ones ([BR-37]: past plans keep their values). | Must (proposed) |  |  |
| FR-305 | A record that an uncancelled plan for today or a later date uses cannot be retired, and the refusal names the plan. | Must (proposed) |  |  |
| FR-306 | A changed record applies to later plans; on an open plan, [BR-21] (re-check at publishing) or a flag catches any conflict. | Must (proposed) |  |  |
| FR-307 | A retired customer matches no import row, a retired van does not count toward [BR-36] (the vehicle limit), and a retired driver cannot sign in and has every session ended. | Must (proposed) |  |  |
| FR-79 | A dispatcher can define a weekly shift template, which fills the per-date values of [FR-54] (per-date shifts and vans) that a dispatcher has not set. | Should (proposed) |  |  |
| FR-80 | The company has a working calendar that marks Israeli holidays and short Fridays, so a dispatcher setting a date's drivers and shifts ([FR-54]: per-date shifts and vans) can see them. | Could (proposed) |  |  |
| FR-81 | Routes reserve time for driver breaks, as [BR-105] (time reserved for breaks) says, and the driver chooses when to take one. If breaks are a legal duty, this becomes a Must. [Q-39] (legal driver breaks) | Should (proposed) |  |  |
| FR-82 | A dispatcher can mark a driver unavailable during the day, so that re-optimization ([FR-35]: re-optimize after a disruption) redistributes their remaining stops. | Should (proposed) |  |  |
| FR-194 | A driver marked unavailable counts as no driver under [BR-20] (routes publish only with their solved van and driver). | Should (proposed) |  |  |
| FR-114 | Shifts and time windows can cross midnight. (Would change [FR-190]: valid shift times, [BR-35]: valid time window and service duration, [BR-102]: plans complete at midnight.) [Q-40] (pilot protocol) | Could (proposed) |  |  |
| FR-129 | A depot can have opening hours. Routes leave it no earlier than it opens and return no later than it closes ([BR-46]: routes keep to depot hours). | Should (proposed) |  |  |
| FR-195 | An order that cannot be served because of a depot's hours is dropped with the reason *depot hours*. | Should (proposed) |  |  |
| FR-196 | A manual edit that breaks a depot's opening hours is flagged ([FR-233]: violations flagged after an edit). | Should (proposed) |  |  |
| FR-130 | A dispatcher sets a depot's location by address, geocoded as a customer's is ([FR-12]: geocode every address), or by placing its pin on the map ([FR-14]: drag a pin to fix a match). | Should (proposed) |  |  |
| FR-197 | A depot without coordinates cannot start or end a route, so a solve leaves its vans out and names them. | Should (proposed) |  |  |
| FR-131 | A vehicle has a display name and a licence plate, each unique among active vehicles ([BR-48]: unique vehicle names and plates). | Should (proposed) |  |  |
| FR-198 | A vehicle's display name identifies it on the map, the timeline, the driver app and printouts. | Should (proposed) |  |  |
| FR-132 | A driver has a contact phone, which the console shows beside their route. | Should (proposed) |  |  |
| FR-199 | A driver can call the company's dispatch phone ([FR-125]: the company settings page) from the app with one tap. | Should (proposed) |  |  |
| FR-200 | A driver's contact phone is never a recovery channel. | Should (proposed) |  |  |
| FR-133 | A vehicle can have a maximum route duration, a maximum driven distance or a maximum number of stops, and solves and re-optimizations honour them. | Could (proposed) |  |  |
| FR-201 | An order that cannot be served because of a vehicle limit is dropped with the reason *vehicle limit*. | Could (proposed) |  |  |
| FR-202 | A manual edit that breaks a vehicle limit is flagged ([FR-233]: violations flagged after an edit). | Could (proposed) |  |  |
| FR-169 | A route can start or end at a driver's chosen address instead of a depot. (Would change [BR-40]: routes start and end at depots.) | Could (proposed) |  |  |
| FR-308 | A route's home address is visible only to Admins, Dispatchers and that driver, and counts as personal data ([NFR-19]: no personal data in logs). | Could (proposed) |  |  |

### Customers and orders

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-08 | A dispatcher can create customers, each with an address, coordinates, a default time window, and notes that drivers see. | Must |  |  |
| FR-09 | A dispatcher can enter an order through a form: customer, date, time window (defaulting to the customer's), weight and volume demand, service duration and priority (1 to 5). | Must |  |  |
| FR-10 | A dispatcher can import a day's orders from a CSV file. (ADR pending: CSV order import) | Must |  |  |
| FR-203 | The order import validates every row and reports every rejected row with its reason. | Must |  |  |
| FR-204 | An order import file with any rejected row imports nothing. | Must |  |  |
| FR-205 | Rows of the order import name their customer by address. | Must |  |  |
| FR-309 | An order file has fixed, named columns, which the template of [FR-84] (import template files) shows where it is built. | Must (proposed) |  |  |
| FR-11 | A dispatcher can add orders to a day whose plan is already published; they enter the routes through re-optimization. | Must |  |  |
| FR-57 | A dispatcher can change an order's time window, leave-at-door mark ([FR-364]: mark an order to leave at the door), demand, service duration, priority or date until its current stop is marked *arrived* or has an outcome ([BR-92]: no order change after arrival). | Must (proposed) |  |  |
| FR-206 | On a published plan, a change to an order reaches routes through re-optimization, except a date change ([FR-315]: date change on a published plan). | Must (proposed) |  |  |
| FR-315 | On a published plan, changing an order's date takes its stop off at once, as cancelling does, and a route a change breaks is flagged until it is re-optimized. | Must (proposed) |  |  |
| FR-316 | On a ready plan, cancelling an order or changing its date takes its stop off at once; any other change waits for the re-check at publishing ([BR-21]: re-check at publishing). | Must (proposed) |  |  |
| FR-317 | A change to an order while its plan is solving refuses publishing, as [BR-71] (orders placed after the solve request refuse publishing) does. | Must (proposed) |  |  |
| FR-58 | A dispatcher can cancel an order until its current stop is marked *arrived* or has an outcome ([BR-92]: no order change after arrival). | Must (proposed) |  |  |
| FR-207 | On a published plan, a cancelled order's stop leaves the driver's route at once, the rest of the route stays as it is, and no optimization runs. | Must (proposed) |  |  |
| FR-59 | A customer has a name and a contact phone. | Must (proposed) |  |  |
| FR-208 | A driver can call a customer's contact phone from a stop. | Must (proposed) |  |  |
| FR-60 | An import resolves each row to exactly one customer. | Must (proposed) |  |  |
| FR-310 | A row matches a customer on normalized address text. | Must (proposed) |  |  |
| FR-311 | A row matching no customer creates one from the row's name and phone, flagged for a pin check; a row without them is rejected. | Must (proposed) |  |  |
| FR-312 | A row matching several customers is rejected and lists them. | Must (proposed) |  |  |
| FR-61 | The import reads Hebrew text in UTF-8, with or without a byte order mark, and in Windows-1255. | Must (proposed) |  |  |
| FR-209 | An import file whose bytes are not valid in its encoding is rejected with that reason. | Must (proposed) |  |  |
| FR-313 | A file with a UTF-8 byte order mark or valid UTF-8 is read as UTF-8; any other file is read as Windows-1255, and the dispatcher confirms a preview first. | Must (proposed) |  |  |
| FR-314 | A file with a UTF-16 byte order mark or NUL bytes is refused with that reason. | Must (proposed) |  |  |
| FR-364 | A dispatcher can mark an order "may be left at the door" on the order form and in a yes-or-no column (yes, no, כן, לא) of the order import ([FR-309]: fixed import columns), where a blank means no. | Must (proposed) |  |  |
| FR-62 | A dispatcher can copy a previous date's orders to a new date and change them before solving. | Should (proposed) |  |  |
| FR-83 | A dispatcher can import customers in bulk from a CSV file. | Should (proposed) |  |  |
| FR-210 | The customer import checks every row, reports every rejected row with its reason, and imports nothing from a file with any rejected row, as the order import does ([FR-203]: every import row validated, [FR-204]: all-or-nothing order import). | Should (proposed) |  |  |
| FR-318 | A customer row needs a name, an address and a phone; one with the same phone or address as another customer is a duplicate and is rejected. | Should (proposed) |  |  |
| FR-84 | A dispatcher can download a template file for the order import ([FR-10]: CSV order import) and the customer import ([FR-83]: customer CSV import). | Should (proposed) |  |  |
| FR-85 | An order can carry a reference from an outside system. | Should (proposed) |  |  |
| FR-211 | Importing an order whose reference already exists on the same date is refused and reported with that reason. | Should (proposed) |  |  |
| FR-86 | An order can carry a floor, an apartment and an entrance code, and the driver sees them at the stop. | Should (proposed) |  |  |
| FR-87 | An order can carry its own recipient name and phone, and the driver sees them at the stop beside the customer's contact. | Should (proposed) |  |  |
| FR-88 | A dispatcher can define a standing order that recurs on chosen weekdays. | Could (proposed) |  |  |
| FR-89 | A customer can have a default service duration, used for that customer's new orders. | Could (proposed) |  |  |
| FR-110 | Pick-up and delivery jobs: an order is collected at one address and delivered to another, not only loaded at a depot. | Could (proposed) |  |  |
| FR-113 | An order can have several time windows. (Would change [BR-03]: service starts inside the time window, [BR-35]: valid time window and service duration.) | Could (proposed) |  |  |
| FR-116 | Another system can send orders to DispatchFlow through an API, a revisit trigger of [ADR-006](../ADRs/ADR-006-versioned-openapi-contracts.md) (versioned OpenAPI contracts). | Could (proposed) |  |  |
| FR-214 | An outside system sending orders needs a credential beyond the access token of [NFR-01] (access token on every call). | Could (proposed) |  |  |
| FR-134 | A dispatcher can search and filter customers by name, phone and address. | Should (proposed) |  |  |
| FR-212 | A dispatcher is warned before saving a customer that is likely to duplicate an existing one, with the same phone or a matching address. | Should (proposed) |  |  |
| FR-135 | An order can carry its own delivery note, beside the customer's standing notes ([FR-08]: create customers). The driver sees both at the stop, each labelled. | Should (proposed) |  |  |
| FR-136 | An order can carry a package count. The driver sees it at the stop and on the loading list ([FR-153]: loading list). | Could (proposed) |  |  |
| FR-137 | A dispatcher can see an order's history: when it was created, imported, changed, planned, dropped, moved by a re-optimization, reported or cancelled, and by whom. It is its own record, separate from the audit of [NFR-23] (audit records). | Should (proposed) |  |  |
| FR-138 | A dispatcher can import orders from an Excel workbook (.xlsx) as well as CSV. | Could (proposed) |  |  |
| FR-213 | The Excel import is validated exactly as [FR-203] (every import row validated), [FR-204] (all-or-nothing order import), [FR-205] (import rows name customers by address) and [FR-60] (each import row matches one customer) require, within the limits of [NFR-37] (upload size limits), [NFR-96] (unpacked size limit) and [NFR-97] (no external workbook references). | Could (proposed) |  |  |
| FR-319 | The Excel import reads the workbook's first sheet. | Could (proposed) |  |  |

### Geocoding

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-12 | The system converts every customer address, including Hebrew addresses, to coordinates. | Must |  |  |
| FR-13 | Geocoding results are cached, so the same address is not looked up repeatedly, within the limits the provider's terms allow ([NFR-17]: map and geocoding terms). | Must |  |  |
| FR-14 | A dispatcher can fix a bad match by dragging the customer's pin on the map. | Must |  |  |
| FR-215 | A corrected pin location is saved for that customer and used for all their future orders. | Must |  |  |
| FR-63 | Before a solve, the system flags to the dispatcher every address whose match is missing, partial, ambiguous or outside the service area. [Q-02] (geocoding provider) | Must (proposed) |  |  |
| FR-216 | An address flag does not stop the solve. | Must (proposed) |  |  |
| FR-217 | A pin the dispatcher corrected ([BR-13]: hand-corrected locations win) clears that address's flag. | Must (proposed) |  |  |
| FR-320 | The service area is what the OpenStreetMap road-data extract covers; an address outside it drops as *unreachable location* ([FR-66]: unreachable locations are dropped) unless its pin is fixed. | Must (proposed) |  |  |
| FR-64 | If the geocoding provider cannot be reached, a customer is still saved without coordinates and the lookup is retried every 15 minutes for 24 hours, then stays flagged for a hand-placed pin. | Should (proposed) |  |  |
| FR-218 | If the geocoding provider cannot be reached, the dispatcher can place the customer's pin by hand. | Should (proposed) |  |  |
| FR-139 | While a dispatcher types a customer's or depot's address, the system suggests matching addresses, and the dispatcher picks one. [Q-02] (geocoding provider) | Could (proposed) |  |  |
| FR-219 | A typed address the dispatcher keeps without picking a suggestion is geocoded as [FR-12] (geocode every address) says. | Could (proposed) |  |  |
| FR-220 | Suggestions come through the server, from the provider or the company's own records, within the cap of [NFR-135] (daily geocoding cap). | Could (proposed) |  |  |
| FR-140 | Before a lookup, the system normalizes common Hebrew abbreviations and spelling variants, such as רח׳ for רחוב, שד׳ for שדרות, ת״א for תל אביב, and full against defective spelling. [Q-02] (geocoding provider) | Could (proposed) |  |  |
| FR-221 | An address normalized for a lookup is kept and shown as the dispatcher typed it. | Could (proposed) |  |  |

### Daily planning

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-15 | A dispatcher can create a plan for a date and request a solve over that date's orders, vehicles and driver shifts. | Must |  |  |
| FR-16 | A solve runs in the background, and the dispatcher sees that the plan is solving. | Must |  |  |
| FR-222 | The dispatcher is notified when a solve's result is ready, without reloading. | Must |  |  |
| FR-17 | A solve assigns orders to vehicles, orders the stops of every route, and gives each stop a planned ETA, honouring every rule in [Business rules](#business-rules-and-invariants). | Must |  |  |
| FR-18 | When not every order fits, the solve still returns a plan and lists every dropped order with the reason it was dropped. | Must |  |  |
| FR-223 | A dropped order's reason is *capacity*, *time window*, *shift*, *displaced* by other orders, *missing location* for an order whose customer has no coordinates, or one that [FR-66] (unreachable locations are dropped), [FR-146] (take a stop off by hand), [FR-195] (depot-hours drops), [FR-201] (vehicle-limit drops) or [FR-228] (an impossible van or driver pin drops the order) adds. | Must |  |  |
| FR-19 | The solve time limit is configurable within the range in [NFR-06] (solve time limit range), with a default of 30 seconds; the solve returns the best solution found within it. | Must |  |  |
| FR-20 | A solve or a re-optimization can be run with either solver: OR-Tools, the default, or the DispatchFlow solver (Clarke-Wright + ALNS). | Must |  |  |
| FR-224 | Outside the optimization service, a solver is known only by its name, and choosing one changes nothing else. | Must |  |  |
| FR-21 | The company can enable a fixed cost per vehicle used, so the solver also reduces the number of vehicles on the road. | Must |  |  |
| FR-65 | When a solve fails, the dispatcher is told without reloading and sees the reason: service unavailable, timed out or interrupted ([BR-32]: a solve that overruns its limit fails, [BR-74]: a restart fails a running solve, [BR-112]: solve queue limits), no uncancelled orders on that date, or validator rejection. | Must (proposed) |  |  |
| FR-225 | After a failed solve, the dispatcher can request it again. | Must (proposed) |  |  |
| FR-66 | If the road network cannot reach a customer's location, the solve still returns a plan and drops that order with the reason *unreachable location*. | Must (proposed) |  |  |
| FR-67 | The company can set a departure allowance, the minutes between shift start and leaving the depot for loading. | Should (proposed) |  |  |
| FR-226 | Planned ETAs include the departure allowance. | Should (proposed) |  |  |
| FR-90 | A planned travel time comes from the road network and varies by time of day. | Must (proposed) |  |  |
| FR-227 | Planned ETAs include a buffer, starting at 10% of travel time and calibrated from pilot arrivals. [Q-20] (travel-time buckets and factors) | Must (proposed) |  |  |
| FR-321 | A day has three travel-time buckets, morning peak, evening peak and off-peak, whose factors start at 1.3, 1.3 and 1.0 and are calibrated from pilot arrivals. [Q-20] (travel-time buckets and factors) | Must (proposed) |  |  |
| FR-322 | Solvers plan with the highest factor, so a plan stays feasible all day; ETAs and the validator use each bucket's factor. [Q-20] (travel-time buckets and factors) | Must (proposed) |  |  |
| FR-323 | Toll roads count like any road, for the whole company, and no toll cost enters the objective. | Must (proposed) |  |  |
| FR-91 | A dispatcher can pin an order to a van or a driver before a solve, and the solve honours the pin. | Should (proposed) |  |  |
| FR-228 | A pin that cannot be honoured drops the order with that reason. | Should (proposed) |  |  |
| FR-92 | Before a dispatcher solves a plan again, the system warns that the manual edits made to it will be discarded. | Should (proposed) |  |  |
| FR-93 | A dispatcher can compare two solves of the same date side by side. | Could (proposed) |  |  |
| FR-94 | A dispatcher can ask for a solve that spreads the workload more evenly across drivers. | Could (proposed) |  |  |
| FR-229 | A solve that spreads the workload needs its own objective beside [BR-09] (the solve objective), which stays the objective that [NFR-24] (static quality target) and [NFR-25] (incremental re-optimization study) score by. | Could (proposed) |  |  |
| FR-111 | A van can run more than one trip a day, returning to a depot to reload between trips. | Could (proposed) |  |  |
| FR-112 | A dispatcher can require that an order goes on a van or a driver with a given attribute, such as refrigeration or a licence. | Could (proposed) |  |  |
| FR-118 | A route respects road limits that depend on the vehicle type. | Could (proposed) |  |  |
| FR-119 | A dispatcher can plan across several days at once. (Would change [BR-34]: one plan per date.) | Could (proposed) |  |  |
| FR-141 | Before a solve, a pre-flight check lists the known causes of dropped orders or a failed solve. | Should (proposed) |  |  |
| FR-230 | The pre-flight check lists orders whose customer has no coordinates ([BR-14]: only orders with coordinates are planned), vans without a driver and drivers without a van on that date ([FR-54]: per-date shifts and vans), orders heavier or larger than every van, orders with no volume and windows outside every shift. | Should (proposed) |  |  |
| FR-231 | The pre-flight check does not stop the solve, and a clean check does not promise that no order drops. | Should (proposed) |  |  |
| FR-142 | While a solve runs, the dispatcher sees how long it has run out of its time limit ([FR-19]: configurable solve time limit). | Could (proposed) |  |  |
| FR-143 | Before publishing, each route shows its load against the van's weight and volume capacity and its duration against the driver's shift. | Should (proposed) |  |  |
| FR-232 | Before publishing, each route also shows its total waiting time and planned distance. | Should (proposed) |  |  |
| FR-144 | Each dropped order's reason ([FR-18]: dropped orders listed with reasons) comes with the fact that caused it, in plain language, such as "weighs 450 kg; the largest van carries 400 kg" or "window ends at 09:00; the earliest arrival is 09:20". | Should (proposed) |  |  |

### Manual editing

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-22 | A dispatcher can drag a stop to another position in its route, or to another route, while the plan is ready and not yet published. | Should |  |  |
| FR-23 | After each manual edit, ETAs are recalculated from road travel times, and no optimization runs. | Should |  |  |
| FR-233 | After each manual edit, every time-window, capacity and shift violation is flagged immediately. | Should |  |  |
| FR-234 | A plan with flagged violations cannot be published ([BR-17]: no publishing with violations). | Should |  |  |
| FR-145 | While a plan is ready and not yet published, a dispatcher can undo and redo the manual edits made since the last solve, one at a time. | Should (proposed) |  |  |
| FR-146 | While a plan is ready and not yet published, a dispatcher can take a stop off its route into the dropped list, with the reason *removed by dispatcher*. | Should (proposed) |  |  |
| FR-235 | While a plan is ready and not yet published, a dispatcher can put a dropped order on a route by hand, with the same recalculation and flags as [FR-23] (ETAs recalculated after an edit) and [FR-233] (violations flagged after an edit). | Should (proposed) |  |  |
| FR-236 | Taking a stop off a route by hand is an exception to [BR-08] (drop only when not every order fits), which binds solves and re-optimizations. | Should (proposed) |  |  |
| FR-237 | An order dropped for *missing location* or *unreachable location* cannot be placed until its pin is fixed. | Should (proposed) |  |  |
| FR-147 | A dispatcher can lock a route, or the sequence of its stops, before solving again, so that the solve keeps it as it is. | Could (proposed) |  |  |
| FR-238 | A lock that cannot be honoured fails the solve with that reason ([FR-65]: a failed solve shown with its reason). | Could (proposed) |  |  |
| FR-239 | The warning of [FR-92] (warning before solving over edits) does not cover a locked route. | Could (proposed) |  |  |

### Dispatcher views

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-24 | A dispatcher sees all routes of a plan on a map, drawn along the road network, with their stops. | Must |  |  |
| FR-25 | A dispatcher sees a timeline of stops per vehicle. | Should |  |  |
| FR-120 | A dispatcher can do their day-to-day tasks from a phone, not only from the desktop console. This is a revisit trigger of [ADR-003](../ADRs/ADR-003-dispatcher-console-and-driver-app.md) (web console and React Native driver app). (Would change [NFR-15]: desktop web console, `src/frontend/console/CLAUDE.md`.) (ADR pending: dispatcher on a phone) | Could (proposed) |  |  |
| FR-148 | Each route has one colour and one label, used the same way on the map, the timeline, lists and printouts. | Should (proposed) |  |  |
| FR-240 | Route colours are told apart by labels as well, never by colour alone ([NFR-46]: WCAG accessibility level). | Should (proposed) |  |  |
| FR-149 | The map also shows depots, dropped orders at their customer's location, and the addresses flagged by [FR-63] (doubtful addresses flagged before a solve), each visibly different from a routed stop. | Should (proposed) |  |  |
| FR-150 | Selecting a stop on the map, the timeline or a list selects it in the others. | Should (proposed) |  |  |
| FR-241 | Selecting a stop shows its window, ETA, load, waiting time and any violation. | Should (proposed) |  |  |
| FR-151 | A dispatcher sees every route and stop of a plan in a table that can be sorted and filtered, as an alternative to the map and timeline. | Should (proposed) |  |  |

### Publishing and the driver app

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-26 | A dispatcher can publish a plan; each driver then sees their own route. | Must |  |  |
| FR-336 | A dispatcher can cancel a published plan for a day that does not run, while no route has started and no stop is reported. | Should (proposed) |  |  |
| FR-337 | When a plan is cancelled, its drivers are alerted and its orders return to *unplanned*. | Should (proposed) |  |  |
| FR-338 | A cancelled plan has no daily summary or baseline comparison, and is left out of trends ([FR-41]: trends dashboard). | Should (proposed) |  |  |
| FR-27 | A driver sees today's route: stops in order with planned ETAs, customer details and notes. | Must |  |  |
| FR-324 | A route for a later date is listed with its date, and the alert of [FR-152] (drivers alerted on publishing) opens it. | Must (proposed) |  |  |
| FR-28 | A driver can hand off any stop to navigation: a Navigate action deep-links to Waze or Google Maps. | Must |  |  |
| FR-325 | Navigate sends the stop's stored coordinates, so a corrected pin is honoured. | Must (proposed) |  |  |
| FR-29 | A driver can mark a stop *arrived*, *delivered* or *failed*. | Must |  |  |
| FR-242 | Marking a stop *failed* requires a reason. | Must |  |  |
| FR-365 | The driver sees at the stop that its order may be left at the door. | Must (proposed) |  |  |
| FR-366 | When nobody takes a leave-at-door order, the driver may leave it at the door and mark the stop *delivered*, recorded as left at the door; no other order can be recorded so. | Must (proposed) |  |  |
| FR-30 | A driver can capture proof of delivery: a photo or a signature, with an optional note. | Must (proposed) |  |  |
| FR-367 | The server refuses a driver's *delivered* report that comes without a proof ([FR-30]: proof of delivery); an outcome a dispatcher records or corrects ([FR-73]: dispatcher records an outcome, [FR-333]: dispatcher corrects an outcome) needs none. | Must (proposed) |  |  |
| FR-368 | A stop left at the door needs a photo as its proof, because nobody is there to sign; without a camera, a dispatcher records it ([FR-73]: dispatcher records an outcome). | Must (proposed) |  |  |
| FR-31 | While a route is in progress, the driver app reports the driver's location every 30 seconds, including while it runs in the background on iOS and Android. | Must |  |  |
| FR-243 | The web build reports the driver's location only while it is open. | Must |  |  |
| FR-326 | The web build is for demos and development and needs a connection; offline work ([FR-69]: route shown offline, [FR-70]: report stops offline), background alerts ([FR-71]: route-change alerts) and encrypted storage ([NFR-39]: encrypted storage on the phone) bind iOS and Android only, and it shows a notice saying so. | Must (proposed) |  |  |
| FR-347 | The web build learns of a route change by reading its route every 30 seconds while open. | Must (proposed) |  |  |
| FR-32 | A driver on the road receives route changes after a re-optimization is applied. Manual edits happen only before publishing. | Must |  |  |
| FR-68 | A driver can start and finish their route. | Must (proposed) |  |  |
| FR-244 | A route is *not started*, *in progress* or *finished*. | Must (proposed) |  |  |
| FR-327 | A driver's first stop report starts a route that was not started. | Must (proposed) |  |  |
| FR-328 | Location collection stops 1 hour after shift end on a route nobody finished. | Must (proposed) |  |  |
| FR-329 | A driver can finish a route with stops pending after a warning, and the dispatcher is alerted. | Must (proposed) |  |  |
| FR-69 | The driver app shows the last received route, customer details and notes without a connection, with the route's date and when it was received. | Must (proposed) |  |  |
| FR-70 | A driver can start and finish a route and report stops without a connection, and capture proof too ([FR-30]: proof of delivery), which is sent with its report. | Must (proposed) |  |  |
| FR-245 | Offline reports keep the time they were made and survive closing the app. | Must (proposed) |  |  |
| FR-246 | Queued reports are sent within 60 seconds of the connection returning while the app is open, otherwise when it next opens. | Must (proposed) |  |  |
| FR-247 | Reports of one route are sent in the order they were made. | Must (proposed) |  |  |
| FR-248 | A report stays queued until the server acknowledges it, and a refused one is shown to the driver, except as [NFR-101] (rate-limited reports stay silent) says. (ADR pending: driver report sync and idempotency) | Must (proposed) |  |  |
| FR-330 | Signing out warns the driver about unsent reports. | Must (proposed) |  |  |
| FR-331 | Reports queued when a session ends stay encrypted on the phone and are sent after the same user signs in again. | Must (proposed) |  |  |
| FR-71 | When a route changes, the driver app alerts the driver, even in the background. | Must (proposed) |  |  |
| FR-249 | The driver app shows which stops a route change added, removed or reordered. | Must (proposed) |  |  |
| FR-72 | A dispatcher can print or export each route: stops in order, addresses, phones, notes and each stop's weight and volume. | Should (proposed) |  |  |
| FR-95 | A driver can report a problem from the app (breakdown, running late, van full), and the dispatcher is alerted. | Should (proposed) |  |  |
| FR-96 | A driver sees updated ETAs for their remaining stops after each reported stop and each accepted re-optimization, using the projected arrival of [FR-254] (projected arrival). | Should (proposed) |  |  |
| FR-97 | A driver confirms that they have seen a route change ([FR-71]: route-change alerts). | Should (proposed) |  |  |
| FR-250 | The dispatcher sees which drivers have not confirmed a route change. | Should (proposed) |  |  |
| FR-98 | A driver can scan a barcode at loading and at delivery. | Could (proposed) |  |  |
| FR-99 | A driver can record an age or ID check at delivery, for orders that require one. | Could (proposed) |  |  |
| FR-100 | A dispatcher and a driver can exchange messages in the app. | Could (proposed) |  |  |
| FR-115 | A driver can record a partial delivery or a return of goods. | Could (proposed) |  |  |
| FR-121 | The driver app is available in Arabic and Russian, beside Hebrew and English ([NFR-27]: Hebrew and English interface). | Could (proposed) |  |  |
| FR-152 | When a plan is published, every driver with a route is alerted that their route is ready, through the channel of [FR-71] (route-change alerts). | Should (proposed) |  |  |
| FR-153 | Before leaving the depot, a driver sees a loading list: their orders in reverse stop order, with weight and volume, and package counts where [FR-136] (package count) is built. | Should (proposed) |  |  |
| FR-154 | Each stop shows its time window and how long remains until it ends. | Should (proposed) |  |  |
| FR-251 | A stop at risk ([FR-34]: alerts for at-risk stops) is marked in the driver's list as well as the dispatcher's. | Should (proposed) |  |  |
| FR-155 | When the driver comes within 100 metres of the next stop, the app offers to mark it *arrived*. | Could (proposed) |  |  |
| FR-252 | The app never marks a stop *arrived* on its own. | Could (proposed) |  |  |
| FR-156 | When a driver finishes a route ([FR-68]: start and finish a route), the app shows a summary: stops delivered and failed, and any reports still waiting to be sent ([FR-70]: report stops offline). | Could (proposed) |  |  |

### Live operations and re-optimization

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-33 | A dispatcher sees, live on the map, each vehicle's latest position and each stop's status. | Must |  |  |
| FR-34 | The system detects stops at risk of missing their time window and alerts the dispatcher. | Must |  |  |
| FR-253 | A stop is at risk when its projected arrival is later than its window end minus 15 minutes. | Must |  |  |
| FR-254 | A stop's projected arrival is its planned arrival plus the route's observed delay. | Must |  |  |
| FR-383 | A route's observed delay is the actual time of its latest event, its start or a stop's arrival or outcome, minus that event's planned time. | Must (proposed) |  |  |
| FR-369 | A leave-at-door stop is never at risk ([FR-253]: when a stop is at risk), because its window does not bind ([BR-119]: leave-at-door windows are preferences). | Must (proposed) |  |  |
| FR-385 | The map, timeline, table and re-optimization preview mark a leave-at-door stop served outside its window, with the minutes outside, without counting it as a violation. | Must (proposed) |  |  |
| FR-35 | A dispatcher can re-optimize the remaining stops of a published plan after a disruption: a late driver, an unavailable vehicle, or new orders. | Must |  |  |
| FR-332 | Only a person starts a re-optimization; alerts inform them, and nothing starts one automatically. | Must (proposed) |  |  |
| FR-36 | Before a re-optimization is applied, the dispatcher sees a preview of what changes for each driver. | Must |  |  |
| FR-255 | Nothing changes for drivers until the dispatcher accepts the re-optimization. | Must |  |  |
| FR-37 | Re-optimization keeps drivers' existing routes as stable as it can: route stability is part of its objective. | Must |  |  |
| FR-256 | Route stability is the number of stops that leave their vehicle, dropped stops included. | Must |  |  |
| FR-73 | A dispatcher can record a stop's outcome on a driver's behalf, with a reason when failed. | Must (proposed) |  |  |
| FR-257 | The record of an outcome made on a driver's behalf names who made it. | Must (proposed) |  |  |
| FR-333 | A dispatcher can correct a recorded outcome at any time, with a reason; the original stays in the history, and figures use the correction. | Must (proposed) |  |  |
| FR-74 | The map shows how old each vehicle's position is, marks it stale after 5 minutes by the server's clock, and says so when no position has been received. | Must (proposed) |  |  |
| FR-75 | A dispatcher can put a failed or dropped order back, for today's re-optimization or on another date. | Should (proposed) |  |  |
| FR-258 | A put-back order gets a new current stop on the same order, its date and window may change, and the earlier stop's outcome stays as recorded. | Should (proposed) |  |  |
| FR-76 | A dispatcher can see every order of a date with its state ([BR-33]: the order states) and, when it is on a route, its ETA. | Should (proposed) |  |  |
| FR-259 | A dispatcher can find an order by customer name or phone. | Should (proposed) |  |  |
| FR-101 | The dispatcher is alerted when a route has not started ([FR-68]: start and finish a route) 15 minutes after shift start plus the departure allowance ([FR-67]: loading time before departure). | Should (proposed) |  |  |
| FR-102 | The dispatcher is alerted immediately when a driver marks a stop *failed*. | Should (proposed) |  |  |
| FR-103 | The preview of a re-optimization ([FR-36]: re-optimization preview) also shows which orders would be dropped, which time windows would break, and the change in the plan's totals. | Must (proposed) |  |  |
| FR-334 | The preview names each moved stop and new order whose goods need a hand-over ([BR-106]: goods stay on their van). | Must (proposed) |  |  |
| FR-104 | A dispatcher can see the history of the accepted re-optimizations of a plan: who accepted each, when, and what changed. | Should (proposed) |  |  |
| FR-105 | A dispatcher can view a stop's proof of delivery ([FR-30]: proof of delivery). | Must (proposed) |  |  |
| FR-117 | A customer can follow their delivery on a live tracking page. [Q-59] (Israeli accessibility standard) | Could (proposed) |  |  |
| FR-263 | The live tracking page is an exception to [NFR-01] (access token on every call): it opens only through a link that cannot be guessed, and shows only that delivery. | Could (proposed) |  |  |
| FR-157 | A dispatcher has one alert list that gathers every alert: stops at risk ([FR-34]: alerts for at-risk stops), problems drivers report ([FR-95]: driver problem reports), routes not started ([FR-101]: route-not-started alert), failed stops ([FR-102]: failed-stop alert), unconfirmed route changes ([FR-97]: driver confirms a route change), late reports ([BR-89]: an accepted late outcome removes the stop from the other route), refused reports ([BR-90]: contradicting reports are refused), routes finished with stops pending ([FR-329]: finish with stops pending) and low ratings ([FR-361]: low-rating alert). | Should (proposed) |  |  |
| FR-260 | A dispatcher acknowledges an alert, which records who did it and when. | Should (proposed) |  |  |
| FR-261 | An unacknowledged alert stays visible until its plan completes, and one raised after that stays until it is acknowledged. | Should (proposed) |  |  |
| FR-335 | An alert raised while the console is closed waits in the alert list. | Should (proposed) |  |  |
| FR-158 | A dispatcher sees each route's progress: stops done out of all stops, current delay against the plan, next stop and when the driver last reported. | Should (proposed) |  |  |
| FR-159 | A dispatcher can limit a re-optimization to chosen routes, so that no stop moves into or out of a route left out. This mode is not part of [NFR-25] (incremental re-optimization study). | Should (proposed) |  |  |
| FR-160 | A dispatcher can replay a past day on the map, within the retention of [NFR-31] (retention periods): each vehicle's recorded positions and each stop's reports, in time order. [Q-42] (legal advice on personal data) | Could (proposed) |  |  |
| FR-262 | Each replay is audited, an exception to the rule on reads in [NFR-23] (audit records). | Could (proposed) |  |  |

### Messages to recipients

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-49 | A delivery's recipient gets SMS messages about it, sent through an SMS provider from a two-way virtual number ([ADR-031](../ADRs/ADR-031-sms-through-an-israeli-provider-for-recipient-messages.md): SMS for messages to recipients). [Q-96] (which SMS provider) | Must (proposed) |  |  |
| FR-348 | A message goes to the order's recipient phone ([FR-87]: recipient name and phone on an order) when it has one, otherwise to the customer's contact phone ([FR-59]: customer name and phone). | Must (proposed) |  |  |
| FR-349 | Each message starts with the company's name, is written in the company's default interface language ([FR-125]: the company settings page), and has fixed wording per message type in the first release. | Must (proposed) |  |  |
| FR-382 | With a company name of up to 19 characters and a dispatch phone of up to 11, every message fits one SMS of 70 characters. | Must (proposed) |  |  |
| FR-350 | An Admin can turn messages to recipients on or off for the company ([FR-125]: the company settings page); they start off, a change applies to messages triggered after it, and replies are read either way. | Must (proposed) |  |  |
| FR-351 | When a route starts ([FR-68]: start and finish a route), the recipient of each of its stops gets an "out for delivery" message giving the stop's projected arrival ([FR-254]: projected arrival), rounded to the nearest quarter hour, ±30 minutes. | Must (proposed) |  |  |
| FR-372 | A stop that joins a route in progress, such as a new order or a put-back ([FR-258]: a put-back order gets a new stop), gets the "out for delivery" message when it joins. | Must (proposed) |  |  |
| FR-373 | Once a stop is *arrived* or has an outcome, it gets none of the messages of [FR-351] to [FR-354] (out-for-delivery through order-change messages) or [FR-372] (stops joining a route in progress). | Must (proposed) |  |  |
| FR-352 | A recipient gets an "arriving in about 15 minutes" message when their stop's projected arrival ([FR-254]: projected arrival) is 15 minutes away or less, its route is in progress, and the stop before it in plan order, if any, has an outcome. | Must (proposed) |  |  |
| FR-353 | Once their stop's route has started, a recipient gets a "time changed" message, with a new window as [FR-351] (out-for-delivery message) gives it, whenever the stop's projected arrival is 30 minutes or more from the arrival last sent to them. | Must (proposed) |  |  |
| FR-384 | A stop gets at most 2 "time changed" messages. | Must (proposed) |  |  |
| FR-354 | Once their stop's route has started, a recipient gets a message when their order is dropped, moves to another date ([FR-57]: change an order) or is cancelled ([FR-58]: cancel an order). | Must (proposed) |  |  |
| FR-355 | When a stop's first outcome ([FR-374]: messages follow the first outcome) is *failed*, its recipient gets a "missed you" message with the company's dispatch phone ([FR-125]: the company settings page), and not the failure reason. | Must (proposed) |  |  |
| FR-356 | When a stop's first outcome is *delivered*, its recipient gets a message that confirms it, says whether it was left at the door ([FR-366]: leaving an order at the door), and, while [FR-375] (the rating question's switch) is on, asks for a rating from 1 to 10. | Must (proposed) |  |  |
| FR-374 | The first recorded outcome sends its message, whether the driver reports it ([FR-29]: report a stop's status) or a dispatcher records it ([FR-73]: dispatcher records an outcome); a correction ([FR-333]: dispatcher corrects an outcome) sends none and leaves a given rating as it is. | Must (proposed) |  |  |
| FR-375 | An Admin can turn the rating question on or off ([FR-125]: the company settings page); it starts off. [Q-95] (consent for messages to recipients) | Must (proposed) |  |  |
| FR-357 | A reply received at most 48 hours after the SMS provider accepted the rating request rates that delivery when it is a rating ([FR-376]: what counts as a rating); only the first rating counts. | Must (proposed) |  |  |
| FR-376 | A rating is a whole number from 1 to 10, in Western digits with no leading zero, at the start of the reply after spaces are trimmed, and followed by neither a digit nor a decimal point and a digit. | Must (proposed) |  |  |
| FR-371 | A reply answers the latest rating request sent to its phone, with phones compared in the form that [FR-377] (phones stored in one form) stores them in. | Must (proposed) |  |  |
| FR-377 | Phones on customers and orders are checked when entered or imported and stored in international form (E.164); a phone that is not a valid number is refused with that reason. | Must (proposed) |  |  |
| FR-378 | Messages go only to Israeli mobile numbers; a stop whose phone is another kind gets none, and the dispatcher sees why. | Must (proposed) |  |  |
| FR-358 | A rating gets one thank-you message, and later replies about that delivery get no answer, except as [FR-363] (opting out of messages) says. | Must (proposed) |  |  |
| FR-359 | Within the 48 hours and before a delivery is rated, the first reply that is neither a rating nor an opt-out gets one message repeating the format; later ones get no answer. | Must (proposed) |  |  |
| FR-379 | A reply from a phone with no rating request in the last 48 hours gets no answer and is not kept, except an opt-out ([FR-363]: opting out of messages). | Must (proposed) |  |  |
| FR-360 | Each reply within the 48 hours of a rating request, other than a bare rating or an opt-out, is kept as a comment on the delivery, which Admins and Dispatchers can read. [Q-42] (legal advice on personal data) | Must (proposed) |  |  |
| FR-361 | A rating from 1 to 4 alerts the dispatcher, as an at-risk stop does ([FR-34]: alerts for at-risk stops), and appears in the alert list where [FR-157] (one alert list) is built. [Q-42] (legal advice on personal data) | Must (proposed) |  |  |
| FR-362 | The daily summary's total ([FR-38]: the daily summary) shows the average of the ratings given for the day's stops, or unavailable when there is none, and the share of accepted rating requests that were answered. | Must (proposed) |  |  |
| FR-381 | Ratings appear in no per-route or per-driver figure until [Q-42] (legal advice on personal data) is answered. | Must (proposed) |  |  |
| FR-363 | A reply whose first word, ignoring letter case and punctuation, is "הסר", "הסרה", "להסרה" or "STOP" opts its phone out: no further messages go to it, and a phone messaged in the last 48 hours gets one confirmation. [Q-95] (consent for messages to recipients) | Must (proposed) |  |  |
| FR-380 | Dispatchers see on a customer or an order that its phone opted out and, at the person's own request by phone, can record or remove an opt-out, noting who did it and when. | Must (proposed) |  |  |

### Reporting and baseline

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-38 | A dispatcher sees a daily summary of each plan, per route and in total: distance, driving time, stops, delivered and failed stops, on-time percentage, vehicles used and dropped orders. | Must |  |  |
| FR-264 | The on-time percentage is the share of delivered stops whose actual arrival, as the driver's phone recorded it and [BR-114] (report times corrected for the phone's clock) corrected it, was at or before their window end. | Must |  |  |
| FR-265 | With nothing delivered, the on-time percentage is shown as unavailable, not as zero. | Must |  |  |
| FR-339 | The summary shows failed stops beside the on-time percentage, so marking late stops *failed* stays visible. | Must (proposed) |  |  |
| FR-340 | A delivered stop with no arrival report counts its delivery time as its arrival. | Must (proposed) |  |  |
| FR-341 | The distance in [FR-38] (the daily summary) is planned distance; actual distance is under [FR-106] (planned against actual figures). | Must (proposed) |  |  |
| FR-39 | Every plan is compared with a naive nearest-neighbour baseline on the same orders and fleet, in kilometres, driver-hours and planned on-time percentage. | Must |  |  |
| FR-266 | Planned on-time percentage is orders served inside their windows over all orders of the day, so dropped orders count as not on time. | Must |  |  |
| FR-370 | The on-time percentages ([FR-264]: the on-time percentage, [FR-266]: the planned on-time percentage) count a leave-at-door stop as on time only when it is served inside its preferred window. | Must (proposed) |  |  |
| FR-267 | A vehicle is withdrawn mid-route when a re-optimization leaves it out while it still has stops it made: it was marked unavailable ([FR-07]: mark a vehicle unavailable), left without a driver, or its shift ran out. | Must |  |  |
| FR-268 | A withdrawn vehicle's route keeps the stops it made and never returns, so its driver-hours are unknown. | Must |  |  |
| FR-269 | A day with a vehicle withdrawn mid-route is compared on kilometres and on-time only, and the comparison names the withdrawn vehicles. | Must |  |  |
| FR-270 | The baseline of a day with a vehicle withdrawn mid-route is solved after the re-optimization, so its kilometre and on-time figures are not strictly like for like. [Q-24] (baseline for a withdrawn vehicle) | Must |  |  |
| FR-271 | The baseline of a day with a vehicle withdrawn mid-route leaves out a vehicle that was marked unavailable or left without a driver, and gives a vehicle whose shift ran out its whole shift (decided on 2026-10-10). [Q-24] (baseline for a withdrawn vehicle) | Must |  |  |
| FR-342 | The baseline meets windows, as [BR-119] (leave-at-door windows are preferences) qualifies them, capacity and shifts, drops what does not fit, and passes the validator ([BR-15]: only validator-accepted solutions count). | Must (proposed) |  |  |
| FR-40 | Where the company supplies historical manual routes, a plan can be compared against them on the same measures. | Should |  |  |
| FR-41 | An analytics dashboard shows trends across days: on-time percentage, kilometres saved. | Could |  |  |
| FR-106 | The daily summary ([FR-38]: the daily summary) also shows planned against actual distance, driving time and arrival times. [Q-40] (pilot protocol) | Should (proposed) |  |  |
| FR-107 | A dispatcher can export summaries and order lists to a spreadsheet. | Could (proposed) |  |  |
| FR-108 | A summary shows savings in money, from the company's cost per kilometre and per driver-hour. | Could (proposed) |  |  |
| FR-109 | A dispatcher sees a report per driver. [Q-42] (legal advice on personal data) | Could (proposed) |  |  |
| FR-161 | A dispatcher sees failed and dropped orders over a range of dates, grouped by reason ([FR-223]: the drop reasons, [FR-242]: a failed stop needs a reason), customer and, where the order was on a route, driver. [Q-42] (legal advice on personal data) | Should (proposed) |  |  |
| FR-162 | A dispatcher can see the record of every solve and re-optimization of a plan ([NFR-11]: every solve recorded): solver, time limit, duration, objective value and dropped orders. | Could (proposed) |  |  |

### Research and evaluation

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-42 | DispatchFlow has its own solver: a Clarke-Wright savings construction improved by Adaptive Large Neighbourhood Search with destroy and repair operators and simulated-annealing acceptance. | Must |  |  |
| FR-272 | The DispatchFlow solver sits behind the same solver interface as OR-Tools. | Must |  |  |
| FR-273 | For a re-optimization, the DispatchFlow solver starts from the current routes and repairs them, destroying and reinserting stops around the disruption, instead of solving again from scratch. | Must |  |  |
| FR-274 | No product capability depends on the DispatchFlow solver ([Non-goals](#non-goals)). | Must |  |  |
| FR-43 | A benchmark harness runs OR-Tools and the DispatchFlow solver on the Solomon instances (56 instances of 100 customers, classes C1, C2, R1, R2, RC1, RC2). | Must |  |  |
| FR-275 | The benchmark harness also runs both solvers on the 200-customer Gehring-Homberger instances, and the 400-customer ones if time remains, offline and never in CI. | Must |  |  |
| FR-276 | The benchmark harness also runs both solvers on the pilot's real days once the pilot provides them. [Q-01] (which company and what data) | Must |  |  |
| FR-277 | The harness reports the gap from best-known solutions where they are published, the DispatchFlow solver's gap from OR-Tools ([NFR-24]: static quality target), and the solve time. | Must |  |  |
| FR-278 | In the benchmark harness, both solvers get the same time limit on the same hardware. | Must |  |  |
| FR-279 | Every benchmark result is scored by the shared objective ([BR-09]: the solve objective) once the validator accepts it ([BR-15]: only validator-accepted solutions count). | Must |  |  |
| FR-44 | A scaling study reports solve time and solution quality at 25, 50, 100 and 200 stops. | Must |  |  |
| FR-45 | A synthetic order generator builds realistic days from real Israeli addresses, for testing and as the fallback when no pilot data is available. | Must |  |  |
| FR-46 | A simulation replays a real or synthetic day with injected delays, breakdowns and new orders, generated from a recorded seed, so that the day can be replayed. | Must |  |  |
| FR-280 | The simulation runs every re-optimization with each solver at the same set of time limits: OR-Tools warm-started from the current routes, and the DispatchFlow solver repairing them. | Must |  |  |
| FR-281 | At each limit the simulation measures each solver's objective, route stability, elapsed time and whether the new orders were served, all scored by the shared objective ([BR-09]: the solve objective, [BR-56]: the re-optimization objective). | Must |  |  |
| FR-282 | A simulation run in which OR-Tools did not start from the current routes is reported and not counted as warm-started ([NFR-25]: incremental re-optimization study). | Must |  |  |
| FR-47 | A pilot runs DispatchFlow at the company. [Q-01] (which company and what data) [Q-40] (pilot protocol) | Must |  |  |
| FR-283 | The company's feedback from the pilot is collected and written up. | Must |  |  |
| FR-48 | A usability study runs task-based sessions with the pilot's dispatcher (build a plan, handle a breakdown), scored with the SUS questionnaire. | Should |  |  |
| FR-284 | The usability study collects driver feedback on route stability after re-optimization. | Should |  |  |
| FR-285 | Usability study results feed UI iterations between rounds. | Should |  |  |
| FR-343 | The SUS score is reported with no pass mark, since one dispatcher is too few respondents. | Should (proposed) |  |  |
| FR-344 | Until the company's dispatcher is available, sessions use classmates and a stand-in dispatcher, on a separate deployment with synthetic data ([FR-45]: synthetic order generator); a later round uses the company's dispatcher. | Should (proposed) |  |  |
| FR-51 | The benchmark harness also runs PyVRP, an open-source VRP solver, on the static instances of [FR-43] (Solomon benchmark harness) and [FR-275] (Gehring-Homberger benchmarks) under the same time limits, as a reference for both solvers. | Should |  |  |
| FR-286 | Only the harness uses PyVRP; it never serves a solve in the product. | Should |  |  |
| FR-163 | The DispatchFlow solver can log, at a sampled interval, each iteration's operators chosen, operator weights, current and best objective and acceptance temperature. | Should (proposed) |  |  |
| FR-287 | Solver logging is off in the product. | Should (proposed) |  |  |
| FR-164 | The benchmark harness can run the DispatchFlow solver with single destroy or repair operators removed, or with adaptive weights fixed, to measure each part's contribution (an ablation study). | Should (proposed) |  |  |
| FR-165 | Solver comparisons ([FR-43]: Solomon benchmark harness, [FR-46]: disruption simulation) report per-instance paired results with a Wilcoxon signed-rank test and a rank-biserial effect size. | Should (proposed) |  |  |
| FR-288 | The significance level of a solver comparison is 0.05, fixed before the study. | Should (proposed) |  |  |
| FR-167 | The simulation of [FR-46] (disruption simulation) can also inject cancellations ([FR-58]: cancel an order), unavailable drivers ([FR-82]: mark a driver unavailable), changed time windows ([FR-57]: change an order) and late driver reports ([BR-27]: late outcomes are still accepted), from the same recorded seed. | Should (proposed) |  |  |
| FR-168 | An Admin can export a pilot day as a pseudonymized benchmark instance ([NFR-50]: pilot data pseudonymized) that both solvers and PyVRP read. [Q-60] (research use of pilot coordinates) | Could (proposed) |  |  |
| FR-289 | A pilot-day export is audited. | Could (proposed) |  |  |
| FR-290 | An exported pilot-day instance stays outside the repository ([NFR-122]: results kept with their configuration). | Could (proposed) |  |  |

## Business rules and invariants

Rules are grouped by subject.

### Access

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-02 | **Driver scope.** A Driver can see and update only the stops of routes assigned to them. |  |  |
| BR-19 | **Two Admins.** The company keeps two active Admins: an Admin's role cannot change while it has two or fewer. |  |  |
| BR-41 | **Drivers see published routes only.** A plan that is `draft`, `solving` or `ready`, and a re-optimization preview not yet accepted ([FR-36]: re-optimization preview), is never shown to drivers. Priority: Must (proposed). |  |  |
| BR-45 | **Unique sign-in.** A sign-in identifier belongs to at most one user. Priority: Must (proposed). |  |  |
| BR-48 | **Unique vehicles.** A vehicle's display name and licence plate are each unique among active vehicles ([FR-131]: vehicle name and plate); a retired vehicle ([BR-43]: retire instead of delete) does not count. Priority: Should (proposed). |  |  |

### Routing constraints

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-03 | **Time windows.** Service at a stop starts inside its order's time window, except as [BR-107] (re-optimization may keep late stops) and [BR-119] (leave-at-door windows are preferences) say. A vehicle that arrives early waits, except as [BR-122] (door stops are served on arrival) says. |  |  |
| BR-49 | **Default time window.** An order's window defaults to its customer's default window. |  |  |
| BR-119 | **Leave-at-door windows.** The time window of a leave-at-door order ([FR-364]: mark an order to leave at the door) is a preference: service may start outside it, within the driver's shift ([BR-06]: routes stay within the driver's shift) and on the order's date. ([ADR-032](../ADRs/ADR-032-leave-at-door-windows-are-preferences.md): leave-at-door windows are preferences) Priority: Must (proposed). |  |  |
| BR-122 | **Door stops on arrival.** A van serves a leave-at-door stop when it arrives and never waits there, so the stop's start time follows from its route. ([ADR-032](../ADRs/ADR-032-leave-at-door-windows-are-preferences.md): leave-at-door windows are preferences) Priority: Must (proposed). |  |  |
| BR-04 | **Service time.** Each stop occupies the vehicle for the order's service duration, which counts toward the arrival time at the next stop. |  |  |
| BR-05 | **Capacity.** On every route, the total weight demand never exceeds the vehicle's weight capacity, and the total volume demand never exceeds its volume capacity. |  |  |
| BR-50 | **Capacity values.** Vehicle capacities are positive, in kilograms and litres; order demands are zero or more, and a blank volume counts as zero. |  |  |
| BR-06 | **Shifts.** A route leaves no earlier than its driver's shift start and returns to its end depot no later than the shift end, except after a hand-over confirmed under [FR-304] (hand-over that breaks a shift). |  |  |
| BR-105 | **Break reserve.** Where [FR-81] (driver breaks) is built, a route on a shift of 6 hours or more returns at least 45 minutes before shift end. [Q-39] (legal driver breaks) Priority: Should (proposed). |  |  |
| BR-14 | **Routable orders only.** An order can be planned only when its customer has coordinates. |  |  |
| BR-39 | **One route per van and driver.** Within a plan, a van drives at most one route and so does a driver; a route withdrawn mid-route ([FR-267]: vehicle withdrawn mid-route) does not count. (Would be changed by [FR-111]: several trips per van.) Priority: Must (proposed). |  |  |
| BR-40 | **Routes start and end at depots.** Except as [BR-51] (re-optimized routes start where the van is) and [BR-52] (a withdrawn route ends at its last reported stop) say, a route starts at its van's start depot and ends at the van's end depot. (Would be changed by [FR-169]: routes from a driver's own address.) Priority: Must (proposed). |  |  |
| BR-51 | **Re-optimization start.** During a re-optimization, a route starts at the start point of [BR-79] (re-optimization starts from current locations) and [BR-24] (fallback start without a usable position) instead of its van's start depot. Priority: Must (proposed). |  |  |
| BR-52 | **Withdrawn route end.** A route withdrawn mid-route ([FR-267]: vehicle withdrawn mid-route) ends at its last reported stop. Priority: Must (proposed). |  |  |
| BR-46 | **Depot hours.** When a depot has opening hours ([FR-129]: depot opening hours), a route leaves it no earlier than it opens and returns no later than it closes. Together with [BR-06] (routes stay within the driver's shift), the tighter limit holds. Priority: Should (proposed). |  |  |

### Solving, dropping and validation

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-07 | **Single assignment.** Within a plan, every order is either served by exactly one current stop or reported as dropped, never both and never twice. Earlier failed stops stay as history ([FR-258]: a put-back order gets a new stop). |  |  |
| BR-08 | **Dropping.** An order is dropped only when not every order can be served. |  |  |
| BR-53 | **Priority scale.** Order priority is a whole number from 1, the highest, to 5, and a row without one gets 3. |  |  |
| BR-54 | **Priority in dropping.** A drop's penalty is proportional to 6 minus the order's priority, so solves prefer to drop lower priorities; several low-priority orders can outweigh one high-priority order. |  |  |
| BR-55 | **Fewer drops.** Equal penalties make a solve prefer fewer drops among orders of one priority, as best effort, and each penalty is large enough that no order drops just to save travel ([BR-08]: drop only when not every order fits). |  |  |
| BR-09 | **Objective.** A solve minimizes total travel time plus drop penalties, plus the window penalty of [BR-120] (leave-at-door window penalty), plus the fixed vehicle cost when enabled ([FR-21]: fixed cost per vehicle used). |  |  |
| BR-56 | **Re-optimization objective.** A re-optimization minimizes the objective of [BR-09] (the solve objective) plus a route-stability penalty ([FR-37]: route stability in re-optimization) and a lateness penalty ([BR-107]: re-optimization may keep late stops), as [BR-115] (the re-optimization penalties) sets them. |  |  |
| BR-115 | **Re-optimization penalties.** Each stop that leaves its vehicle costs 5 minutes of travel time, and each late minute costs 5 minutes; both are starting values, tuned on a separate seeded set and fixed before the study's measured runs. ([ADR-029](../ADRs/ADR-029-solver-objective-and-re-optimization-rules.md): solver objective and re-optimization rules) Priority: Must (proposed). |  |  |
| BR-120 | **Window penalty.** A leave-at-door stop served outside its window adds 5 times the time outside it to the objective, in solves and re-optimizations, replacing the lateness penalty of [BR-115] (the re-optimization penalties); 5 is a starting value, tuned with it. Priority: Must (proposed). |  |  |
| BR-121 | **No window drops.** The common drop-penalty scale of [BR-54] (drop penalty scales with priority) is set so that every leave-at-door order's drop penalty exceeds the largest window penalty ([BR-120]: leave-at-door window penalty) the longest shift on its date allows, so *time window* is never its drop reason ([FR-223]: the drop reasons). Priority: Must (proposed). |  |  |
| BR-15 | **Validated solutions only.** No solution, from either solver or from a re-optimization, is stored as a plan or shown as feasible unless the independent feasibility validator accepts it. |  |  |
| BR-57 | **Rejected solutions.** A solution the validator rejects fails its solve job and lists the violations. |  |  |
| BR-42 | **One definition of feasibility.** Manual-edit checks ([FR-233]: violations flagged after an edit), the publishing re-check ([BR-21]: re-check at publishing), solver acceptance ([BR-15]: only validator-accepted solutions count) and research scoring ([FR-279]: benchmarks scored by the shared objective, [FR-281]: what the simulation measures) use the same validator, so no solver's own check is the final word on its solution. Priority: Must (proposed). |  |  |
| BR-58 | **Research objective.** Research scoring uses the shared objective ([BR-09]: the solve objective, [BR-56]: the re-optimization objective). Priority: Must (proposed). |  |  |

### Plan lifecycle and publishing

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-12 | **Plan lifecycle.** A plan moves `draft → solving → ready → published → completed`, and makes no other transition except those of [BR-59] (a failed solve reverts the plan), [BR-60] (a ready plan may be solved again) and [BR-103] (a cancelled plan is final). |  |  |
| BR-59 | **Failed solve.** A failed solve returns a plan to `draft`, or to `ready` if it was solved before. |  |  |
| BR-60 | **Solving again.** A `ready` plan may be solved again. |  |  |
| BR-61 | **Manual edits.** Manual edits keep a plan `ready`. |  |  |
| BR-62 | **Accepted re-optimization.** An accepted re-optimization keeps a plan `published` and changes it in place. |  |  |
| BR-102 | **Plan completion.** A published plan becomes `completed` at midnight, Israel time, at the end of its date, and late reports are still accepted ([BR-27]: late outcomes are still accepted). Priority: Must (proposed). |  |  |
| BR-103 | **Cancelled plan.** A published plan may become `cancelled` ([FR-336]: cancel a published plan) and then makes no further transition. Priority: Should (proposed). |  |  |
| BR-118 | **No past plans.** A plan for a past date cannot be created or published. Priority: Must (proposed). |  |  |
| BR-16 | **Edits are flagged, never silently fixed.** A manual edit that breaks any of [BR-02] to [BR-15] (driver scope through validated solutions), [BR-39] (one route per van and per driver), [BR-40] (routes start and end at depots), [BR-46] (routes keep to depot hours), [BR-51] (re-optimized routes start where the van is), [BR-52] (a withdrawn route ends at its last reported stop), [BR-79] (re-optimization starts from current locations) or [BR-105] (time reserved for breaks) is kept as the dispatcher made it and flagged with every violation. |  |  |
| BR-17 | **No publishing with violations.** A plan with any flagged violation cannot be published; the dispatcher fixes it or solves again first. |  |  |
| BR-20 | **Published as solved.** A plan is published only while, for every route, the van is not marked unavailable and the van's driver holds the Driver role and is the route's driver. |  |  |
| BR-63 | **Changed vans.** When a van no longer meets [BR-20] (routes publish only with their solved van and driver), nothing is published, every changed van is named, and the dispatcher solves again. |  |  |
| BR-64 | **Check at acceptance.** Accepting a re-optimization applies [BR-20] (routes publish only with their solved van and driver) to every van the proposal keeps driving, skipping vans withdrawn mid-route. (ADR pending: check the vans when a re-optimization is accepted) |  |  |
| BR-65 | **Changed vans at acceptance.** When a van no longer meets [BR-20] (routes publish only with their solved van and driver), accepting a re-optimization is refused, every changed van is named, and the dispatcher re-optimizes again. |  |  |
| BR-21 | **Re-check at publishing.** Before a ready plan is published, the shared validator re-checks every route once against the current fleet and orders of its date: shifts, capacities, depots and time windows. |  |  |
| BR-66 | **Vehicle limits at publishing.** Where [FR-133] (per-vehicle route limits) is built, the re-check at publishing also covers vehicle limits. |  |  |
| BR-67 | **Broken rule.** If a route breaks a rule in the re-check at publishing, nothing is published, every broken rule is listed, and the dispatcher solves again or edits the route. |  |  |
| BR-68 | **Re-checked times.** When the re-check at publishing finds nothing broken, the re-checked times are published: they equal the solved times unless the fleet changed since the solve, and then they follow it. |  |  |
| BR-69 | **Plans without routes.** A plan without routes needs no re-check at publishing. |  |  |
| BR-70 | **Re-check cannot run.** If the re-check at publishing cannot run, nothing is published. |  |  |
| BR-22 | **Every order accounted for.** A plan is published only when it serves or drops every order of its date. |  |  |
| BR-71 | **Late orders.** An order placed after the solve was requested refuses publishing: nothing is published, every such order is named, and the dispatcher solves again. |  |  |
| BR-72 | **Order of refusals.** Publishing refusals fire in this order: changed vans ([BR-20]: routes publish only with their solved van and driver), an order the plan does not account for ([BR-22]: every order served or dropped before publishing), flagged violations ([BR-17]: no publishing with violations; refused without a re-check), then the re-check ([BR-21]: re-check at publishing). |  |  |
| BR-73 | **Orders after publishing.** Orders placed after publishing enter through re-optimization ([FR-11]: add orders to a published day). |  |  |
| BR-32 | **No plan stays `solving`.** A solve that has started and has not returned within 30 seconds past its limit fails, and the failed-solve transition of [BR-59] (a failed solve reverts the plan) then applies. (ADR pending: solve job timeout) |  |  |
| BR-74 | **Solve interrupted.** A solve running when a restart interrupts it fails. |  |  |
| BR-75 | **Solve limit start.** The limit of a solve runs from the start of its search, after the travel-time matrix is built, and a queued solve still waits its turn ([NFR-12]: queued solves survive restarts). |  |  |
| BR-112 | **Queue limits.** A queued solve or re-optimization waits at most 5 minutes and then fails, and a request beyond 5 queued is refused. Priority: Must (proposed). |  |  |
| BR-34 | **One plan per date.** The company has at most one plan for a date, not counting cancelled ones. |  |  |
| BR-38 | **One change at a time.** A plan has at most one re-optimization running at a time; a second request is refused with its reason, and an open preview does not count. (ADR pending: concurrent changes to a plan) |  |  |
| BR-76 | **No silent overwrite.** Two dispatchers cannot silently overwrite each other's changes to a plan: an out-of-date change is refused with its reason. |  |  |
| BR-47 | **Named actions only.** A published route changes only through an accepted re-optimization ([FR-255]: nothing changes until accepted), a hand-over ([FR-55]: hand a route to another driver), a cancelled order or date change ([FR-58]: cancel an order, [FR-315]: date change on a published plan), a cancelled plan ([FR-336]: cancel a published plan), a driver's start, finish or report ([FR-68]: start and finish a route, [FR-29]: report a stop's status, [BR-27]: late outcomes are still accepted), or an outcome recorded or corrected for a driver ([FR-73]: dispatcher records an outcome, [FR-333]: dispatcher corrects an outcome). Priority: Must (proposed). |  |  |
| BR-77 | **Audited changes.** Each change to a published route is audited ([NFR-23]: audit records). Priority: Must (proposed). |  |  |
| BR-78 | **No silent changes.** No background process changes a published route silently. Priority: Must (proposed). |  |  |

### Re-optimization

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-10 | **Locked stops.** Re-optimization never moves a completed or in-progress stop. A stop is in progress once its driver marks it *arrived*. |  |  |
| BR-79 | **Re-optimization start point.** In a re-optimization, each vehicle's current location becomes its new starting point. |  |  |
| BR-106 | **Goods stay on their van.** A re-optimization moves a stop off its van only if that van is unavailable, without a driver, out of shift or finished, or the stop is new; other stops only reorder or drop. ([ADR-029](../ADRs/ADR-029-solver-objective-and-re-optimization-rules.md): solver objective and re-optimization rules) Priority: Must (proposed). |  |  |
| BR-117 | **Stops on no van.** Under [BR-106] (goods stay on their van), a dropped order or one moved to the date counts as new, and a failed stop put back stays with its van. Priority: Must (proposed). |  |  |
| BR-107 | **Late stops.** A re-optimization may keep a stop past its window end, within its driver's shift and any break reserve ([BR-105]: time reserved for breaks), flagged in the preview ([FR-103]: preview shows drops and broken windows); a flagged stop does not refuse acceptance, and a solve stays bound by [BR-03] (service starts inside the time window). ([ADR-029](../ADRs/ADR-029-solver-objective-and-re-optimization-rules.md): solver objective and re-optimization rules) Priority: Must (proposed). |  |  |
| BR-108 | **Finished routes.** A re-optimization adds no stop to a finished route, and a route withdrawn mid-route ([FR-267]: vehicle withdrawn mid-route) counts as finished. Priority: Must (proposed). |  |  |
| BR-23 | **Facts are not violations.** When a re-optimization is validated ([BR-15]: only validator-accepted solutions count), [BR-03] (service starts inside the time window) as [BR-107] (re-optimization may keep late stops) relaxes it, [BR-04] (service time at each stop), [BR-06] (routes stay within the driver's shift), [BR-46] (routes keep to depot hours) and the vehicle limits of [FR-133] (per-vehicle route limits) apply only to what has not happened yet, so a locked stop that ran late never fails the solution. (ADR pending: validator and reoptimization problem conventions) |  |  |
| BR-24 | **Start point without a usable position.** If a vehicle has no usable position, re-optimization starts it from the last stop it reported, or from its start depot if it reported none. (ADR pending: validator and reoptimization problem conventions) |  |  |
| BR-80 | **Usable position.** A position is not usable when it is older than 5 minutes, outside the service area, or implies a speed above 200 km/h from the position before it. |  |  |
| BR-81 | **Preview names the vehicle.** The preview names each vehicle that has no usable position. |  |  |
| BR-25 | **Accepting re-checks recorded outcomes.** Accepting a re-optimization re-checks it against every stop status, cancellation, order change, hand-over and fleet change recorded since the preview. (ADR pending: check recorded changes when a re-optimization is accepted) |  |  |
| BR-82 | **Changed since the preview.** If the proposal would move or drop a stop that changed, or relies on an order, van or driver that changed, nothing is applied, what changed is named, and the dispatcher re-optimizes. |  |  |
| BR-83 | **Reports that stand.** A report on a stop the proposal leaves on the same van and in the same order does not refuse the proposal, and the report stands. |  |  |
| BR-26 | **A failed re-optimization changes nothing.** A re-optimization that fails, is rejected by the validator or is declined leaves the plan `published` and every route as it was. |  |  |
| BR-84 | **Re-optimization timeout.** A re-optimization has a limit of 20 seconds from the start of its search, and has failed if it has not returned within 30 seconds past it. |  |  |
| BR-85 | **Re-optimization interrupted.** A re-optimization that a restart interrupts, or finds queued, has failed. |  |  |
| BR-86 | **Failure reason shown.** The dispatcher is told why a re-optimization failed or was rejected. |  |  |

### Driver reports and outcomes

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-11 | **Failure reason.** A stop cannot be marked *failed* without a reason. |  |  |
| BR-109 | **Failure reasons.** A failure reason is customer absent, wrong address, refused, no access, damaged, or other with text. The list is fixed in the first release. Priority: Must (proposed). |  |  |
| BR-18 | **Outcomes are final for drivers.** Once *delivered* or *failed* is recorded, a driver cannot change it, and a dispatcher can correct it ([FR-333]: dispatcher corrects an outcome). |  |  |
| BR-87 | **Delivery states.** A delivery moves `pending → arrived → delivered` or `failed`, and may skip *arrived*. |  |  |
| BR-27 | **Outcomes outlive route changes.** Except as [BR-88] (when a late outcome is refused) says, an outcome a driver recorded while the stop was on their route is accepted however late it arrives, even if the stop has moved since (an exception to [BR-02]: drivers see and update only their own stops). (ADR pending: driver report sync and idempotency) |  |  |
| BR-88 | **Late outcome refused.** A late outcome is not accepted if the stop already has a recorded outcome or its order or plan is cancelled; [BR-90] (contradicting reports are refused) applies instead. |  |  |
| BR-89 | **Stop leaves the other route.** When a late outcome is accepted for a stop that has moved, the stop leaves the other driver's route, that driver is told, and the dispatcher is alerted. |  |  |
| BR-110 | **Judging a late report.** Whether a stop was on the driver's route is judged from the route's history at the report's corrected time ([BR-114]: report times corrected for the phone's clock). Priority: Must (proposed). |  |  |
| BR-28 | **Repeated reports are harmless.** A report received twice takes effect once, and the repeat gets the first answer. (ADR pending: driver report sync and idempotency) |  |  |
| BR-90 | **Contradicting reports.** A report contradicting a recorded outcome, or arriving for a cancelled order or plan, is refused and shown to the dispatcher. |  |  |
| BR-29 | **Stops may be done out of order.** A driver can report a route's stops in a different order from the plan, and the system records the actual order. |  |  |
| BR-91 | **Out-of-order stops in re-optimization.** Re-optimization treats stops reported out of order as done in that order. The start point stays as [BR-79] (re-optimization starts from current locations) and [BR-24] (fallback start without a usable position) set it. |  |  |
| BR-44 | **Two times on every report.** Every driver report keeps the time the phone recorded and the time the server received it. Priority: Must (proposed). |  |  |
| BR-114 | **Clock correction.** A report's time is corrected by the phone's clock offset, measured when the report is sent. Priority: Must (proposed). |  |  |

### Orders, records and history

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-13 | **Corrected locations win.** A location a dispatcher corrected by hand overrides any geocoding result for that customer. |  |  |
| BR-104 | **Address change.** Changing a customer's address clears a hand-corrected pin and geocodes again, after warning the dispatcher. Priority: Must (proposed). |  |  |
| BR-30 | **Cancelled orders.** A cancelled order is neither served nor dropped: [BR-07] (each order served once or dropped), [BR-22] (every order served or dropped before publishing), on-time figures and the baseline no longer count it. |  |  |
| BR-92 | **No change after arrival.** An order cannot be changed or cancelled once its current stop is marked *arrived* or has an outcome. |  |  |
| BR-33 | **Order states.** An order is *unplanned*, *planned*, *dropped*, *delivered*, *failed* or *cancelled*. |  |  |
| BR-93 | **Order state changes.** An order's state changes only through the actions that [FR-17] (what a solve produces), [FR-18] (dropped orders listed with reasons), [FR-29] (report a stop's status), [FR-36] (re-optimization preview), [FR-57] (change an order), [FR-58] (cancel an order), [FR-73] (dispatcher records an outcome), [FR-75] (put a failed or dropped order back), [FR-145] (undo and redo edits), [FR-146] (take a stop off by hand), [FR-235] (put a dropped order on a route by hand), [FR-333] (dispatcher corrects an outcome) and [FR-337] (what cancelling a plan does) define, and [BR-30] (cancelled orders are neither served nor dropped) still applies. [BR-18] (outcomes are final for drivers) governs a stop's outcome. |  |  |
| BR-94 | **Leaving *failed*.** An order leaves *failed* only through [FR-75] (put a failed or dropped order back) or a correction ([FR-333]: dispatcher corrects an outcome). |  |  |
| BR-95 | **Leaving *dropped*.** An order leaves *dropped* through [FR-75] (put a failed or dropped order back), when a ready plan is solved again ([BR-60]: a ready plan may be solved again), when its date changes ([FR-57]: change an order) or when it is cancelled ([FR-58]: cancel an order). |  |  |
| BR-96 | **Leaving *dropped*, continued.** An order also leaves *dropped* when a late report is accepted ([BR-27]: late outcomes are still accepted), when a dispatcher records its outcome ([FR-73]: dispatcher records an outcome), puts it on a route by hand ([FR-235]: put a dropped order on a route by hand) or undoes the edit that dropped it ([FR-145]: undo and redo edits), and when its plan is cancelled ([FR-337]: what cancelling a plan does). |  |  |
| BR-35 | **Valid order data.** An order's time window starts before it ends and lies on the order's date, and its service duration is not negative. (For windows that cross midnight, see [FR-114]: shifts and windows crossing midnight.) |  |  |
| BR-97 | **Invalid orders refused.** An order with invalid data is refused with its reason. |  |  |
| BR-116 | **No past dates.** An order placed, moved or put back on a past date is refused with the reason. Priority: Must (proposed). |  |  |
| BR-37 | **History stays.** A plan that is completed, or whose date has passed, keeps the values it was solved and reported with, after the fleet or a customer changes. (ADR pending: past plans keep their values) |  |  |
| BR-43 | **Retire, don't delete.** A depot, vehicle, driver, customer or order that any plan refers to is retired ([FR-56]: maintain and retire records) or cancelled ([FR-58]: cancel an order), never deleted. Priority: Must (proposed). |  |  |
| BR-98 | **Personal data removal.** Only a personal-data request ([NFR-49]: personal-data requests) or a retention period ([NFR-31]: retention periods) removes personal fields, which include a customer's coordinates, and the non-personal figures stay. Priority: Must (proposed). |  |  |
| BR-99 | **Company exit.** When the company stops using DispatchFlow, its data is deleted as [NFR-150] (export then deletion when the company leaves) says, an exception to [BR-43] (retire instead of delete) and [BR-98] (only a request or retention removes personal data). Priority: Must (proposed). |  |  |

### Time and limits

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-31 | **Israel local time.** Every date, time window, shift and ETA is Israel local time, on clock-change days too. (ADR pending: Israel local time) |  |  |
| BR-100 | **Plan date.** A plan's date is the local calendar date. |  |  |
| BR-111 | **Clock changes.** A time in the hour the clock skips is refused, and a time in the hour it repeats reads as its first occurrence. Priority: Must (proposed). |  |  |
| BR-36 | **Vehicle limit.** Adding a sixteenth vehicle is refused, and the message names the limit ([NFR-04]: fleet size limit). (ADR pending: hard limits on fleet and plan size) |  |  |
| BR-101 | **Order limit.** An order beyond the limit of [NFR-05] (plan size limit) for its date is refused, and the message names the limit. (ADR pending: hard limits on fleet and plan size) |  |  |
| BR-113 | **Order limit count.** The limit counts a date's uncancelled orders, failed ones included, and a put-back does not count again. It is checked whenever an order joins a date: import, entry, copy, put-back or date change. Priority: Must (proposed). |  |  |

## Non-functional requirements

### Security and access

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-01"></a>NFR-01 | Every API call requires a valid access token, except signing in, refreshing, signing out, health checks, the calls [FR-52] (first credentials and password recovery) and [FR-263] (tracking page through an unguessable link) name, and the calls of the SMS provider that [NFR-192] (SMS provider callbacks) accepts. |  |  |
| <a id="nfr-85"></a>NFR-85 | Roles are checked on the server for every operation. |  |  |
| <a id="nfr-86"></a>NFR-86 | An access token is accepted for 15 minutes by default, unless [NFR-177] (revoked tokens refused at once) refuses it. |  |  |
| <a id="nfr-177"></a>NFR-177 | The server refuses an access token issued before its user's last revocation. The user's other sessions refresh and carry on. ([ADR-030](../ADRs/ADR-030-admin-issued-credentials-and-token-revocation.md): Admin-issued credentials and token revocation) Priority: Must (proposed). |  |  |
| <a id="nfr-188"></a>NFR-188 | An open event stream ends when its access token expires or its user's sessions are revoked, and the console reconnects with a fresh token. Priority: Must (proposed). |  |  |
| <a id="nfr-03"></a>NFR-03 | Secrets (provider API keys, token-signing keys, database credentials, the first Admin's credential) come from the environment or a secret manager, never from the repository, logs or error messages. |  |  |
| <a id="nfr-22"></a>NFR-22 | A signed-in user stays signed in through refresh tokens. Each is exchanged once, for a new access token and the session's next refresh token. |  |  |
| <a id="nfr-87"></a>NFR-87 | A refresh token lasts 14 days by default from its issue. |  |  |
| <a id="nfr-88"></a>NFR-88 | Presenting a refresh token that was already exchanged revokes its whole session, except as [NFR-178] (grace for a repeated refresh) says. |  |  |
| <a id="nfr-178"></a>NFR-178 | While its session is live, a refresh token presented again within 30 seconds of its exchange gets a new token, which replaces the one that exchange issued, and revokes nothing. Presenting a replaced token revokes the session. ([ADR-030](../ADRs/ADR-030-admin-issued-credentials-and-token-revocation.md): Admin-issued credentials and token revocation) Priority: Must (proposed). |  |  |
| <a id="nfr-89"></a>NFR-89 | A sign-out revokes the session it ends. |  |  |
| <a id="nfr-90"></a>NFR-90 | A role or password change revokes every session of the user. |  |  |
| <a id="nfr-91"></a>NFR-91 | Only a refresh token's hash is stored. |  |  |
| <a id="nfr-92"></a>NFR-92 | Every refusal of a refresh token looks the same. |  |  |
| <a id="nfr-23"></a>NFR-23 | Every API call that changes something, and every sign-in, refresh and sign-out, leaves one audit record. Reads are not audited. |  |  |
| <a id="nfr-93"></a>NFR-93 | An audit record holds when, the acting user when known, the operation, the records it names, its outcome and its trace id. |  |  |
| <a id="nfr-94"></a>NFR-94 | An audit record never carries personal data or a secret ([NFR-19]: no personal data in logs, [NFR-03]: secrets kept out of the repository and logs). |  |  |
| <a id="nfr-26"></a>NFR-26 | In every deployed environment, all traffic between clients and the server is encrypted in transit, and an unencrypted request is refused or redirected, never served. (ADR pending: sign-in and transport security) |  |  |
| <a id="nfr-35"></a>NFR-35 | Failed sign-ins for one account from one source address are limited to 10 in a rolling 15 minutes; past that, that pair is refused until the window clears, and a success resets the count. (ADR pending: sign-in and transport security) |  |  |
| <a id="nfr-36"></a>NFR-36 | Passwords are stored only as salted hashes from a function built for passwords, never in a form that can be read back. (ADR pending: sign-in and transport security) |  |  |
| <a id="nfr-95"></a>NFR-95 | Passwords have at least 8 characters, with no composition rules, and the 3,000 most common passwords of at least 8 characters are refused. |  |  |
| <a id="nfr-37"></a>NFR-37 | Uploads are limited, and one over a limit is refused with the reason: CSV and Excel files to 5 MB, and delivery photos and signatures, in JPEG or PNG, to 5 MB. |  |  |
| <a id="nfr-96"></a>NFR-96 | An uploaded compressed file over 50 MB unpacked is refused with the reason. |  |  |
| <a id="nfr-97"></a>NFR-97 | An uploaded workbook's external references are never followed. |  |  |
| <a id="nfr-38"></a>NFR-38 | Proof-of-delivery files are served only to signed-in users whose role permits it (the [permission matrix](#company-users-and-roles)). |  |  |
| <a id="nfr-39"></a>NFR-39 | Tokens, the route data kept for offline use ([FR-69]: route shown offline), and queued reports with their proofs are stored encrypted on the phone, with the key in the phone's secure storage. (ADR pending: sign-in and transport security) |  |  |
| <a id="nfr-98"></a>NFR-98 | Tokens and the route data kept for offline use are removed from the phone when the user signs out. |  |  |
| <a id="nfr-179"></a>NFR-179 | When a session is revoked, the route data on the phone is wiped at its next server contact, or after 14 days on a phone that stays offline. Priority: Must (proposed). |  |  |
| <a id="nfr-40"></a>NFR-40 | A self-check against Level 1 of the [OWASP Application Security Verification Standard (ASVS) 5.0](https://asvs.owasp.org/) passes before the pilot handles real data. (ADR pending: security and accessibility standards) |  |  |
| <a id="nfr-53"></a>NFR-53 | Every API that clients call is rate-limited to 120 calls a minute per user, beyond the sign-in limit of [NFR-35] (sign-in attempt limit). (ADR pending: API rate limiting) Priority: Should (proposed). |  |  |
| <a id="nfr-99"></a>NFR-99 | A request over a rate limit is refused, with when to retry. Priority: Should (proposed). |  |  |
| <a id="nfr-100"></a>NFR-100 | A rate-limit refusal does not lose a driver's queued reports ([FR-70]: report stops offline). Priority: Should (proposed). |  |  |
| <a id="nfr-101"></a>NFR-101 | A rate-limit refusal of a queued report is neither shown to the driver nor counted against the 60 seconds of [FR-246] (queued reports sent on reconnect). Priority: Should (proposed). |  |  |
| <a id="nfr-102"></a>NFR-102 | Calls without a token ([NFR-01]: access token on every call, [FR-52]: first credentials and password recovery) are limited to 20 a minute per source address, and one over a limit is refused with when to retry. (ADR pending: API rate limiting) Priority: Must (proposed). |  |  |
| <a id="nfr-192"></a>NFR-192 | Calls from the SMS provider, replies and delivery reports alike, are accepted only with the provider's signature or a header secret, two while one is rotated, compared in constant time; any other call is refused and changes nothing. Priority: Must (proposed). |  |  |
| <a id="nfr-199"></a>NFR-199 | A provider call received twice, with the same provider message identifier, takes effect once. Priority: Must (proposed). |  |  |
| <a id="nfr-200"></a>NFR-200 | Calls with the provider's signature or secret are limited to 600 a minute in all, and calls without it as [NFR-102] (limit on calls without a token) says. Priority: Must (proposed). |  |  |
| <a id="nfr-201"></a>NFR-201 | A provider call is answered as received only after what it carries is stored, or discarded as [FR-379] (replies without a rating request) says, so the provider sends a failed one again. Priority: Must (proposed). |  |  |
| <a id="nfr-203"></a>NFR-203 | A comment is kept as plain text cut to 500 characters, shown escaped, and exported with spreadsheet formula characters neutralized ([FR-107]: export to a spreadsheet). Priority: Must (proposed). |  |  |
| <a id="nfr-206"></a>NFR-206 | Only the production deployment holds the SMS provider's credential; every other environment sends through a fake that reaches no phone. Priority: Must (proposed). |  |  |
| <a id="nfr-207"></a>NFR-207 | Generated and pseudonymized phones come from one stated range of Israeli mobile numbers, used only with the fake provider of [NFR-206] (SMS credential only in production). Priority: Must (proposed). |  |  |
| <a id="nfr-54"></a>NFR-54 | CI scans dependencies and container images for known vulnerabilities; [ADR-016](../ADRs/ADR-016-dependency-audit-with-osv-scanner.md) (dependency audit with OSV-Scanner) covers dependencies only. (ADR pending: image and secret scanning) Priority: Must (proposed). |  |  |
| <a id="nfr-103"></a>NFR-103 | CI scans the repository, with its full history, for committed secrets ([NFR-03]: secrets kept out of the repository and logs). (ADR pending: image and secret scanning) Priority: Must (proposed). |  |  |
| <a id="nfr-104"></a>NFR-104 | A known vulnerability fails the build unless it is recorded as accepted, with its reason and an expiry date. Priority: Must (proposed). |  |  |
| <a id="nfr-105"></a>NFR-105 | A detected secret always fails the build and is rotated, not only removed. Priority: Must (proposed). |  |  |
| <a id="nfr-55"></a>NFR-55 | Web responses set a Content Security Policy, HSTS ([NFR-26]: encryption in transit) and a frame-ancestors rule. Priority: Must (proposed). |  |  |
| <a id="nfr-106"></a>NFR-106 | Any cookie that carries a session is Secure, HttpOnly and SameSite=Lax or Strict. Priority: Must (proposed). |  |  |
| <a id="nfr-107"></a>NFR-107 | A change made with a session cookie needs anti-forgery protection. Priority: Must (proposed). |  |  |
| <a id="nfr-108"></a>NFR-108 | Cross-origin requests are accepted only from named origins. Priority: Must (proposed). |  |  |
| <a id="nfr-56"></a>NFR-56 | A request that breaks its OpenAPI contract ([NFR-21]: versioned OpenAPI contracts) is refused with the reason before anything acts on it. (ADR pending: runtime contract validation) Priority: Should (proposed). |  |  |

### Performance and scale

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-04"></a>NFR-04 | Fleet size: up to 15 vehicles. |  |  |
| <a id="nfr-05"></a>NFR-05 | Plan size: up to 200 stops per plan. |  |  |
| <a id="nfr-06"></a>NFR-06 | A daily solve finishes within its configured time limit, settable between 5 and 60 seconds; the limit covers the search, not building the travel-time matrix. |  |  |
| <a id="nfr-07"></a>NFR-07 | A re-optimization returns its preview within 30 seconds at the 99th percentile, from the dispatcher's request to the preview on screen, measured under simulated disruptions at the scale of [NFR-04] (fleet size limit) and [NFR-05] (plan size limit). |  |  |
| <a id="nfr-08"></a>NFR-08 | Violation checks after a manual edit finish within 500 ms at the 95th percentile, from the edit to the flags on screen. |  |  |
| <a id="nfr-09"></a>NFR-09 | Stop-status and position updates reach the dispatcher's map within 5 seconds of reaching the server, at the 95th percentile. |  |  |
| <a id="nfr-180"></a>NFR-180 | Alerts and route changes reach an online reader within 30 seconds at the 95th percentile, and an offline phone when it reconnects. Priority: Must (proposed). |  |  |
| <a id="nfr-10"></a>NFR-10 | The API is load-tested with k6: 15 phones and 5 consoles on a plan of 200 stops, with 30-second pings for 10 minutes and under 1% errors. |  |  |
| <a id="nfr-57"></a>NFR-57 | The travel-time matrix for a plan at the scale of [NFR-04] (fleet size limit) and [NFR-05] (plan size limit) is built within 5 seconds. Priority: Should (proposed). |  |  |
| <a id="nfr-181"></a>NFR-181 | [NFR-08] (edit-check latency), [NFR-09] (live update latency), [NFR-57] to [NFR-61] (matrix build through driver app opening times), [NFR-109] (console frame rate) and [NFR-180] (alert delivery time) are measured on the deployment VM, a mid-range laptop for the console and a mid-range Android phone for the driver app, at 200 stops and 15 routes, at the 95th percentile over at least 100 samples. Priority: Should (proposed). |  |  |
| <a id="nfr-58"></a>NFR-58 | The console's first load finishes within 3 seconds on a throttled Fast 4G connection. Priority: Should (proposed). |  |  |
| <a id="nfr-109"></a>NFR-109 | Panning, zooming and selecting in the console on a plan of 200 stops and 15 routes holds 30 frames a second. Priority: Should (proposed). |  |  |
| <a id="nfr-59"></a>NFR-59 | Reads and writes that do not solve answer within 500 ms at the 95th percentile, per operation, under the load of [NFR-10] (API load test), except imports ([NFR-60]: import validation time) and publishing. Priority: Should (proposed). |  |  |
| <a id="nfr-60"></a>NFR-60 | An import of 200 order rows ([FR-10]: CSV order import) is validated and reported within 10 seconds, not counting the geocoding of new addresses. Priority: Could (proposed). |  |  |
| <a id="nfr-61"></a>NFR-61 | The driver app shows the last received route within 2 seconds of opening, with or without a connection ([FR-69]: route shown offline). Priority: Should (proposed). |  |  |

### Solver research targets

These are the success criteria of the planned comparison between the DispatchFlow solver and
OR-Tools ([ADR-005](../ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md): OR-Tools
first with our solver as research). They bind the research track, not the product. OR-Tools serves
the product whatever the outcome, and a missed target is a result to report, not a failed release.
The study reports each target's measured value and whether it was met. The target of [NFR-25]
(incremental re-optimization study) is met only if [NFR-116] (our solver serves the new order),
[NFR-117] (re-optimization quality target) and [NFR-119] (what the re-optimization limit covers) all
hold. NFR-62 states the provenance every result of the study must carry.

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-24"></a>NFR-24 | **Static quality.** On the Solomon instances, with both solvers given the same time limit of 60 seconds on the same hardware, the DispatchFlow solver's objective is on average within **5%** of OR-Tools'. |  |  |
| <a id="nfr-110"></a>NFR-110 | In the static study, both solvers are scored by the shared objective ([BR-09]: the solve objective), with a drop penalty large enough that nothing drops if avoidable, no fixed vehicle cost and a travel-time factor of 1.0 with no buffer. |  |  |
| <a id="nfr-111"></a>NFR-111 | In the static study, each solver runs 10 times per instance, one solver at a time on an otherwise idle machine, and its result on the instance is the mean of those runs. |  |  |
| <a id="nfr-112"></a>NFR-112 | The gap on an instance is (DispatchFlow − OR-Tools) / OR-Tools, and the average gap is the mean of the gaps over all instances that [NFR-114] (failed OR-Tools instances) does not leave out. |  |  |
| <a id="nfr-113"></a>NFR-113 | No failure is left out: the static-quality target is met only if every run of the DispatchFlow solver returns a solution the validator accepts ([BR-15]: only validator-accepted solutions count). |  |  |
| <a id="nfr-114"></a>NFR-114 | An instance on which an OR-Tools run fails or is rejected is reported and left out of the mean. |  |  |
| <a id="nfr-115"></a>NFR-115 | Every run of the static study records its seed, the iterations or solutions it reached and the solver versions, so that it can be replayed under that budget ([NFR-13]: reproducible solver). |  |  |
| <a id="nfr-25"></a>NFR-25 | **Incremental re-optimization.** Given a published plan at the scale of [NFR-04] (fleet size limit) and [NFR-05] (plan size limit) and one new order, both solvers get the same time limit of **5 seconds**: the DispatchFlow solver repairs the plan, and OR-Tools is warm-started from it. |  |  |
| <a id="nfr-116"></a>NFR-116 | Under the limit of [NFR-25] (incremental re-optimization study), the DispatchFlow solver returns a solution the validator accepts that serves the new order. |  |  |
| <a id="nfr-117"></a>NFR-117 | Over the study's insertions, the DispatchFlow solver's objective is on average no worse than OR-Tools', both scored by the shared objective ([BR-09]: the solve objective, [BR-56]: the re-optimization objective). |  |  |
| <a id="nfr-118"></a>NFR-118 | Only insertions for which OR-Tools, solving the whole day from scratch under [BR-10] (completed and in-progress stops are locked) and [BR-106] (goods stay on their van), serves the new order are counted. |  |  |
| <a id="nfr-182"></a>NFR-182 | The study's insertions are 10 seeded new orders into each of 30 synthetic plans at full scale, 300 in all. |  |  |
| <a id="nfr-195"></a>NFR-195 | The plans of [NFR-182] (the study's seeded insertions) have no leave-at-door orders unless the study's configuration sets their share before the measured runs, and every result reports that share. Priority: Must (proposed). |  |  |
| <a id="nfr-119"></a>NFR-119 | For both solvers, the limit of [NFR-25] (incremental re-optimization study) covers receiving the problem to returning the solution, validation included, and holds at the 99th percentile inside the optimization service; the end-to-end promise remains [NFR-07] (re-optimization response time). |  |  |
| <a id="nfr-120"></a>NFR-120 | In the re-optimization study, both solvers are also run at limits of 1, 2, 10 and 30 seconds, and the report gives each solver's objective at each limit. |  |  |
| <a id="nfr-62"></a>NFR-62 | **Provenance.** Every benchmark and simulation result is traceable to the commit, configuration file, seeds, hardware description and solver versions that produced it. Priority: Must (proposed). |  |  |
| <a id="nfr-121"></a>NFR-121 | The whole study reruns from one command, given any pilot data. Priority: Must (proposed). |  |  |
| <a id="nfr-122"></a>NFR-122 | Results are kept beside the configuration that made them, except that instances and results derived from pilot data stay outside the repository ([NFR-50]: pilot data pseudonymized), which keeps only their hash and configuration. Priority: Must (proposed). |  |  |

### Product success targets

These measure whether the product worked for the company. They bind the pilot, not the
research track, and their values are not yet agreed ([Q-40]: pilot protocol).

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-32"></a>NFR-32 | Over at least **N** consecutive pilot days, driven kilometres and driver-hours are **X%** below the company's own manual routes on comparable days. [Q-40] (pilot protocol) |  |  |
| <a id="nfr-123"></a>NFR-123 | Over the pilot days of [NFR-32] (pilot savings target), the on-time percentage is no lower than on the company's own manual routes on comparable days. |  |  |
| <a id="nfr-33"></a>NFR-33 | At least 80% of delivered stops arrive within ±15 minutes of their ETA in the published plan or the last accepted re-optimization, not the updated estimate of [FR-96] (updated ETAs for the driver). [Q-40] (pilot protocol) |  |  |
| <a id="nfr-34"></a>NFR-34 | A dispatcher goes from orders in hand to a published plan in 15 minutes or less on a typical pilot day of 100 to 200 orders. [Q-40] (pilot protocol) |  |  |
| <a id="nfr-124"></a>NFR-124 | A dispatcher goes from orders in hand to a published plan on a typical pilot day no slower than with their current method. |  |  |

### Reliability and persistence

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-11"></a>NFR-11 | Every solve is recorded with its parameters, status, timings, solver used and objective value. |  |  |
| <a id="nfr-12"></a>NFR-12 | A queued solve survives a backend restart. |  |  |
| <a id="nfr-13"></a>NFR-13 | The DispatchFlow solver is reproducible: the same input, seed and iteration budget give the same solution. |  |  |
| <a id="nfr-14"></a>NFR-14 | The system runs on a single VM with no redundancy. |  |  |
| <a id="nfr-28"></a>NFR-28 | The database and new proof files are backed up to storage off the VM every hour during operating hours, and at least every 24 hours. ([ADR-027](../ADRs/ADR-027-proof-files-on-the-vm-disk-with-hourly-backups.md): proof files on the VM disk) |  |  |
| <a id="nfr-125"></a>NFR-125 | A restore of the database and proof files finishes within 4 hours. ([ADR-027](../ADRs/ADR-027-proof-files-on-the-vm-disk-with-hourly-backups.md): proof files on the VM disk) |  |  |
| <a id="nfr-126"></a>NFR-126 | A restore is rehearsed once before the pilot. |  |  |
| <a id="nfr-29"></a>NFR-29 | During the pilot's operating hours the API is down at most 1% of the time, measured over each pilot month; that is about 8 minutes per 14-hour day on average. [Q-40] (pilot protocol) |  |  |
| <a id="nfr-127"></a>NFR-127 | Planned downtime falls outside the pilot's operating hours. |  |  |
| <a id="nfr-128"></a>NFR-128 | An outage alerts a named team member within 10 minutes of the first failure; down means 2 failed health checks in a row, probed every minute. [Q-40] (pilot protocol) |  |  |
| <a id="nfr-183"></a>NFR-183 | A restore after losing the VM does not count against the downtime of [NFR-29] (API availability). Priority: Must (proposed). |  |  |
| <a id="nfr-42"></a>NFR-42 | If the optimization service, the road-routing service or the geocoder is down, published routes, driver reports, hand-over ([FR-55]: hand a route to another driver) and cancellation ([FR-58]: cancel an order) keep working. |  |  |
| <a id="nfr-43"></a>NFR-43 | The system reports the date of the road data in use. |  |  |
| <a id="nfr-129"></a>NFR-129 | The road data in use is refreshed monthly, and the team is told when it is older than 60 days. |  |  |
| <a id="nfr-64"></a>NFR-64 | A report the server has acknowledged ([FR-248]: reports stay queued until acknowledged) is not lost when any one service crashes or restarts. Priority: Must (proposed). |  |  |
| <a id="nfr-65"></a>NFR-65 | A driver's report can be retried safely, as [BR-28] (repeated reports take effect once) requires: its request key is scoped to the signed-in user. (ADR pending: driver report sync and idempotency) Priority: Must (proposed). |  |  |
| <a id="nfr-130"></a>NFR-130 | A driver's report sent with a request key already used with a different body is refused. (ADR pending: driver report sync and idempotency) Priority: Must (proposed). |  |  |
| <a id="nfr-66"></a>NFR-66 | If map tiles cannot be loaded, the console still lists routes and stops, with a notice that the map is unavailable. Priority: Should (proposed). |  |  |
| <a id="nfr-131"></a>NFR-131 | If map tiles cannot be loaded, the driver app still shows the route, its stops and Navigate ([FR-28]: hand off to navigation), with a notice that the map is unavailable. Priority: Should (proposed). |  |  |
| <a id="nfr-189"></a>NFR-189 | If the SMS provider cannot be reached or does not answer, every other function keeps working without waiting for it, as [NFR-42] (work goes on when a service is down) says for the other services. Priority: Must (proposed). |  |  |
| <a id="nfr-190"></a>NFR-190 | A message the SMS provider has not accepted within 15 minutes of the server recording its trigger is dropped, and the number dropped is measured as [NFR-161] (measured service metrics) measures the others. Priority: Must (proposed). |  |  |
| <a id="nfr-191"></a>NFR-191 | Each message, identified by its stop, its type and, for "time changed", how many came before it, is sent at most once, even when its trigger repeats ([BR-28]: repeated reports take effect once). Priority: Must (proposed). |  |  |
| <a id="nfr-196"></a>NFR-196 | At most 1,000 messages leave the company a day; past that, sending stops until midnight. Priority: Must (proposed). |  |  |
| <a id="nfr-198"></a>NFR-198 | A send whose outcome is unknown is sent again only if the SMS provider drops repeats by a reference DispatchFlow gives ([Q-96]: which SMS provider); otherwise it counts as dropped. Priority: Must (proposed). |  |  |

### Platforms and deployment

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-15"></a>NFR-15 | The dispatcher console is a desktop web app. |  |  |
| <a id="nfr-132"></a>NFR-132 | The driver app runs on iOS, Android and in a browser, built from one codebase. |  |  |
| <a id="nfr-186"></a>NFR-186 | The driver app reaches the pilot's phones through TestFlight and Google Play internal testing, on the company's developer accounts. ([Q-48]: app store review, [ADR-028](../ADRs/ADR-028-driver-app-through-test-tracks-with-a-device-checklist.md): driver app through test tracks) Priority: Must (proposed). |  |  |
| <a id="nfr-16"></a>NFR-16 | Every server-side component starts with one Docker Compose command. |  |  |
| <a id="nfr-133"></a>NFR-133 | CI builds and tests every change. |  |  |
| <a id="nfr-44"></a>NFR-44 | The server states the oldest supported version of the driver app, which is the previous one. (ADR pending: supported driver app versions) |  |  |
| <a id="nfr-134"></a>NFR-134 | A driver app below the oldest supported version tells the driver to update, keeps its route and still sends its queue, and every other version keeps working ([NFR-21]: versioned OpenAPI contracts). |  |  |
| <a id="nfr-47"></a>NFR-47 | A full 8-hour shift of location reporting ([FR-31]: location reporting during a route), screen off and unplugged, uses at most 15% of the battery, measured on real phones. [Q-38] (driver phones and battery) |  |  |
| <a id="nfr-52"></a>NFR-52 | The monthly running cost stays within ₪1,200, SMS messages to recipients included ([FR-49]: SMS messages to recipients), checked against the bills each pilot month. [Q-62] (the monthly budget) |  |  |
| <a id="nfr-135"></a>NFR-135 | Geocoding lookups are held to 1,000 a day; past that, they wait for the next day and the dispatcher is told. |  |  |
| <a id="nfr-67"></a>NFR-67 | The console supports the current and previous major versions of Chrome and Edge on screens of at least 1366×768; Firefox and Safari work as best effort. Priority: Should (proposed). |  |  |

### Usability, accessibility and localization

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-27"></a>NFR-27 | Every screen of the console and the driver app is available in Hebrew, laid out right to left, and in English. (ADR pending: interface languages) |  |  |
| <a id="nfr-136"></a>NFR-136 | Mixed text, such as addresses with numbers, keeps its correct reading order, checked against a stated list of sample strings. |  |  |
| <a id="nfr-45"></a>NFR-45 | The driver app needs no action while the vehicle is moving. |  |  |
| <a id="nfr-137"></a>NFR-137 | The driver app can be used one-handed: main actions are reachable by thumb on a checklist phone. |  |  |
| <a id="nfr-138"></a>NFR-138 | The driver app is readable in sunlight, with a contrast of at least 7:1. |  |  |
| <a id="nfr-139"></a>NFR-139 | No prompt in the driver app hides the route until the driver answers it, and that includes the confirmation of [FR-97] (driver confirms a route change). |  |  |
| <a id="nfr-140"></a>NFR-140 | The driver app's use while moving, one-handed use, sunlight readability, prompts, background location, offline work, background alerts and encrypted storage are checked by hand on real phones with a scripted checklist before each pilot build. |  |  |
| <a id="nfr-46"></a>NFR-46 | Both clients meet WCAG 2.2 level AA. (ADR pending: security and accessibility standards) |  |  |
| <a id="nfr-141"></a>NFR-141 | Automated WCAG 2.2 level AA checks of both clients run in CI. |  |  |
| <a id="nfr-142"></a>NFR-142 | A team member audits by hand the WCAG 2.2 level AA criteria the automated checks do not cover, before the pilot, and it passes with no Level A or AA failures. |  |  |
| <a id="nfr-68"></a>NFR-68 | Dates are shown as DD/MM/YYYY, times on a 24-hour clock, distances in kilometres, weights in kilograms, volumes in litres and money in shekels (₪), in both interface languages. Priority: Should (proposed). |  |  |
| <a id="nfr-69"></a>NFR-69 | Every error message says what happened and what the user can do next, in the user's language. Priority: Should (proposed). |  |  |
| <a id="nfr-143"></a>NFR-143 | No error message shows a stack trace or a bare internal code. Priority: Should (proposed). |  |  |
| <a id="nfr-144"></a>NFR-144 | Every error code has Hebrew and English text and a next step. Priority: Should (proposed). |  |  |
| <a id="nfr-145"></a>NFR-145 | Sign-in and recovery answer the same whether or not the identifier exists. Priority: Should (proposed). |  |  |
| <a id="nfr-70"></a>NFR-70 | These actions ask for confirmation that names what will change: publishing, cancelling a plan, accepting a re-optimization, cancelling an order, handing over a route, recording or correcting an outcome on a driver's behalf ([FR-73]: dispatcher records an outcome, [FR-333]: dispatcher corrects an outcome) and deactivating a user. Priority: Should (proposed). |  |  |
| <a id="nfr-146"></a>NFR-146 | A driver's own report on a stop asks for no confirmation, because [NFR-139] (prompts never hide the route) bars a prompt that hides the route. Priority: Should (proposed). |  |  |
| <a id="nfr-71"></a>NFR-71 | In the driver app every touch target is at least 48 by 48 density-independent pixels. Priority: Should (proposed). |  |  |
| <a id="nfr-147"></a>NFR-147 | In the driver app, text follows the phone's font size setting without breaking the layout ([NFR-137]: one-handed use, [NFR-138]: sunlight contrast). Priority: Should (proposed). |  |  |

### Compliance and personal data

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-17"></a>NFR-17 | Use of every third-party map or geocoding service complies with its terms. |  |  |
| <a id="nfr-148"></a>NFR-148 | For Google's Geocoding API, the restriction on use with a non-Google map and the 30-day limit on cached coordinates are both resolved before the pilot handles real data. |  |  |
| <a id="nfr-18"></a>NFR-18 | Maps built on OpenStreetMap data show the attribution its licence requires. |  |  |
| <a id="nfr-19"></a>NFR-19 | Personal data (customer names, addresses and phone numbers, driver location pings, delivery photos and signatures) never appears in logs. |  |  |
| <a id="nfr-30"></a>NFR-30 | Driver location is collected only while that driver's route is in progress ([FR-68]: start and finish a route), and stops as [FR-328] (location stops after shift end) says. |  |  |
| <a id="nfr-149"></a>NFR-149 | The driver app shows the driver when location is being collected. |  |  |
| <a id="nfr-31"></a>NFR-31 | Each kind of personal data has a stated retention period and is deleted automatically when it ends. [Q-14] (retention of proofs and records) [Q-42] (legal advice on personal data) |  |  |
| <a id="nfr-184"></a>NFR-184 | Until legal advice, retention is 7 days for location pings, 90 days for proofs and for messages to recipients with their replies, counted from sending or receipt, and 1 year after retirement for customers and drivers. [Q-14] (retention of proofs and records) [Q-42] (legal advice on personal data) Priority: Must (proposed). |  |  |
| <a id="nfr-150"></a>NFR-150 | When the company stops using DispatchFlow, it can get its data exported, and the data is deleted 30 days later. |  |  |
| <a id="nfr-48"></a>NFR-48 | The driver app explains why it needs background location before it asks for the permission. |  |  |
| <a id="nfr-151"></a>NFR-151 | The driver app follows [Google Play's rule on background location](https://support.google.com/googleplay/android-developer/answer/9799150?hl=en). Apple's rules were not checked. [Q-48] (app store review) |  |  |
| <a id="nfr-49"></a>NFR-49 | A person's personal data can be found, corrected and deleted on request. [Q-42] (legal advice on personal data) |  |  |
| <a id="nfr-152"></a>NFR-152 | After a person's data is deleted, reports keep their non-personal figures. |  |  |
| <a id="nfr-185"></a>NFR-185 | A named team member runs the company's data export ([NFR-150]: export then deletion when the company leaves) and personal-data requests, on an Admin's written request, within 30 days. Priority: Must (proposed). |  |  |
| <a id="nfr-50"></a>NFR-50 | Pilot data used for research ([FR-276]: benchmarks on pilot days, [FR-46]: disruption simulation), including in `src/research`, is pseudonymized and is never committed to the repository. |  |  |
| <a id="nfr-153"></a>NFR-153 | Pseudonymization replaces every personal or access field: names, phones, notes, street addresses, apartment details, entrance codes and outside references. [Q-60] (research use of pilot coordinates) |  |  |
| <a id="nfr-51"></a>NFR-51 | The licence of every dependency is compatible with the project's own licence ([ADR-017](../ADRs/ADR-017-proprietary-licence.md): proprietary licence). (ADR pending: dependency licence check) |  |  |
| <a id="nfr-154"></a>NFR-154 | The notices that dependency licences require ship with the apps. |  |  |
| <a id="nfr-72"></a>NFR-72 | A data inventory lists every kind of personal data DispatchFlow keeps: where it is stored, why, for how long ([NFR-31]: retention periods) and who can read it. [Q-42] (legal advice on personal data) Priority: Should (proposed). |  |  |
| <a id="nfr-155"></a>NFR-155 | The data inventory is kept current with each release. Priority: Should (proposed). |  |  |
| <a id="nfr-156"></a>NFR-156 | Everything the data inventory lists counts as personal data under [NFR-19] (no personal data in logs). Priority: Should (proposed). |  |  |
| <a id="nfr-73"></a>NFR-73 | The driver app asks only for the phone permissions it uses: location ([FR-31]: location reporting during a route), camera ([FR-30]: proof of delivery) and notifications ([FR-71]: route-change alerts). Priority: Should (proposed). |  |  |
| <a id="nfr-157"></a>NFR-157 | The driver app asks for phone permissions after sign-in and before a route starts, never during a route in progress. Priority: Should (proposed). |  |  |
| <a id="nfr-158"></a>NFR-158 | The driver app keeps working, with that feature degraded, when a phone permission is refused, limited or revoked. Priority: Should (proposed). |  |  |
| <a id="nfr-187"></a>NFR-187 | When location permission is refused, limited or revoked, the dispatcher sees that the driver's location is unavailable. Priority: Should (proposed). |  |  |
| <a id="nfr-193"></a>NFR-193 | Messages sent to recipients, and their replies and comments, are personal data kept as [NFR-184] (default retention periods) says; each day's average rating and share answered stay. Priority: Must (proposed). |  |  |
| <a id="nfr-202"></a>NFR-202 | A phone's opt-out ([FR-363]: opting out of messages) is kept as a hash of the phone, keyed by a secret ([NFR-03]: secrets kept out of the repository and logs), until the company leaves ([NFR-150]: export then deletion when the company leaves), whatever retention or a request removes. Priority: Must (proposed). |  |  |
| <a id="nfr-204"></a>NFR-204 | Proof photos are taken inside the driver app, which asks to frame the package and the door, not people; they never reach the phone's gallery and leave the phone once the server acknowledges them. Priority: Must (proposed). |  |  |
| <a id="nfr-205"></a>NFR-205 | The server re-encodes each proof image and strips its metadata before storing it. Priority: Must (proposed). |  |  |

### Observability and maintainability

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-20"></a>NFR-20 | Every service writes structured logs carrying a correlation id propagated across every hop. |  |  |
| <a id="nfr-159"></a>NFR-159 | Every service exports OpenTelemetry traces and metrics. |  |  |
| <a id="nfr-160"></a>NFR-160 | Every service exposes health and readiness endpoints. |  |  |
| <a id="nfr-161"></a>NFR-161 | Solve latency, re-optimization latency, job queue depth, error rate, dropped-order rate and on-time percentage are measured. |  |  |
| <a id="nfr-21"></a>NFR-21 | Every API, client to backend and backend to optimization service, is described by a versioned OpenAPI contract. |  |  |
| <a id="nfr-162"></a>NFR-162 | Both sides of every API are tested against its OpenAPI contract. |  |  |
| <a id="nfr-74"></a>NFR-74 | Errors in both clients and crashes of the driver app reach the team with the app version and trace id ([NFR-20]: structured logs with correlation ids). (ADR pending: client error and crash reporting) Priority: Should (proposed). |  |  |
| <a id="nfr-163"></a>NFR-163 | Client errors and driver app crashes reported to the team carry no personal data ([NFR-19]: no personal data in logs), credentials or tokens. Priority: Should (proposed). |  |  |
| <a id="nfr-75"></a>NFR-75 | The team is alerted when solve failures pass 10% of a day, a preview takes over 30 seconds ([NFR-07]: re-optimization response time), 5 jobs are queued or disk use passes 80%. Priority: Should (proposed). |  |  |
| <a id="nfr-164"></a>NFR-164 | The team is alerted when a backup fails ([NFR-28]: scheduled off-VM backups). Priority: Should (proposed). |  |  |
| <a id="nfr-197"></a>NFR-197 | The team is alerted when sending stops at the cap of [NFR-196] (daily message cap) or more than 10% of a day's messages are dropped ([NFR-190]: stale messages dropped). Priority: Must (proposed). |  |  |
| <a id="nfr-76"></a>NFR-76 | Logs, traces and metrics are kept for 7 days and then deleted. Priority: Should (proposed). |  |  |
| <a id="nfr-77"></a>NFR-77 | Every database change follows [ADR-007](../ADRs/ADR-007-postgresql-with-flyway-migrations.md) (PostgreSQL with Flyway migrations). Priority: Should (proposed). |  |  |
| <a id="nfr-165"></a>NFR-165 | Every database change is tested in CI against the schema of the last merge to `main`, with data in it, checking the values after the migration. Priority: Should (proposed). |  |  |
| <a id="nfr-79"></a>NFR-79 | A dispatcher guide and a driver guide exist in Hebrew and English. Priority: Should (proposed). |  |  |
| <a id="nfr-166"></a>NFR-166 | An operations runbook covers deployment, restore ([NFR-125]: restore time), secret rotation ([NFR-03]: secrets kept out of the repository and logs), handling an outage ([NFR-29]: API availability) and recovering a lost Admin ([FR-292]: Admin created on a written request). Priority: Should (proposed). |  |  |

### Testing and verification

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-80"></a>NFR-80 | The feasibility validator ([BR-15]: only validator-accepted solutions count, [BR-42]: one shared feasibility validator) is tested on its own, apart from any real solver. Priority: Must (proposed). |  |  |
| <a id="nfr-167"></a>NFR-167 | The feasibility validator accepts published best-known Solomon solutions, under a stated rounding convention. Priority: Must (proposed). |  |  |
| <a id="nfr-168"></a>NFR-168 | The feasibility validator accepts solutions exactly on a limit, such as a load equal to capacity or a late stop within the shift in a re-optimization ([BR-107]: re-optimization may keep late stops). Priority: Must (proposed). |  |  |
| <a id="nfr-169"></a>NFR-169 | The feasibility validator rejects a catalogue of crafted solutions that together break each of [BR-03] to [BR-07] (time windows through single assignment), [BR-10] (completed and in-progress stops are locked), [BR-14] (only orders with coordinates are planned), [BR-39] (one route per van and per driver), [BR-40] (routes start and end at depots), [BR-51] (re-optimized routes start where the van is), [BR-52] (a withdrawn route ends at its last reported stop), [BR-106] (goods stay on their van), [BR-107] (re-optimization may keep late stops) and [BR-108] (nothing added to finished routes), and names the rule each one breaks. Priority: Must (proposed). |  |  |
| <a id="nfr-170"></a>NFR-170 | A fake solver that returns an infeasible solution fails its solve job, lists the violations and stores no plan; in a re-optimization it leaves the plan and its routes as they were ([BR-26]: a failed re-optimization changes nothing). Priority: Must (proposed). |  |  |
| <a id="nfr-194"></a>NFR-194 | The feasibility validator accepts a leave-at-door stop served outside its window, within its driver's shift and on its date, and rejects one served outside either, in an instance that also has binding windows ([BR-119]: leave-at-door windows are preferences). Priority: Must (proposed). |  |  |
| <a id="nfr-82"></a>NFR-82 | Property-based tests generate small random instances from a recorded seed, under a solution or iteration budget and never a wall-clock limit. Priority: Should (proposed). |  |  |
| <a id="nfr-171"></a>NFR-171 | In property-based tests the validator ([BR-15]: only validator-accepted solutions count) accepts whatever either solver returns, so a rejected solution or a solver error fails the test. Priority: Should (proposed). |  |  |
| <a id="nfr-172"></a>NFR-172 | In property-based tests on instances built around a known full solution, every order is served. Priority: Should (proposed). |  |  |
| <a id="nfr-173"></a>NFR-173 | In property-based tests, generated re-optimizations of a published plan pass [NFR-171] (validator property tests) and [NFR-172] (full-service property tests), and locked stops ([BR-10]: completed and in-progress stops are locked) also stay in place. Priority: Should (proposed). |  |  |
| <a id="nfr-174"></a>NFR-174 | In property-based tests, the DispatchFlow solver gives the same solution for the same input, seed and iteration budget ([NFR-13]: reproducible solver), in separate processes too. Priority: Should (proposed). |  |  |
| <a id="nfr-83"></a>NFR-83 | End-to-end tests run UC-06, UC-07, UC-08, UC-10 and UC-14 ([Use cases](#use-cases)), each main flow and at least one listed failure, in CI against the Docker Compose stack ([NFR-16]: one-command Docker Compose start), using the driver app's web build. Priority: Should (proposed). |  |  |
| <a id="nfr-175"></a>NFR-175 | End-to-end tests run with a controllable clock, fixed road data, solves under a search budget rather than a time limit, a faked push service, a faked SMS provider and no outside network. Priority: Should (proposed). |  |  |
| <a id="nfr-176"></a>NFR-176 | Tests through the API check every role against every capability of the [permission matrix](#company-users-and-roles), and one driver against another driver's routes, stops, proofs and positions, apart from the late reports of [BR-27] (late outcomes are still accepted). Priority: Must (proposed). |  |  |

## Non-goals

DispatchFlow deliberately does not:

- **Serve more than one company.** One deployment serves one delivery company, and nothing separates
  one company's data from another's. Reopen when a second company wants DispatchFlow.
  ([ADR-024](../ADRs/ADR-024-one-delivery-company-no-multi-tenancy.md): one company and no
  multi-tenancy)
- **Use live traffic data.** Travel times come from the road network with time-of-day
  multipliers; ETA drift is a stated, known limitation. (ADR pending:
  no live traffic)
- **Handle payments,** including cash on delivery and billing for DispatchFlow itself. A note on an order
  may mention cash, but nothing is calculated. ([Q-57]: scope of the payments non-goal)
- **Run a marketplace or crowdsourced drivers.** Every driver belongs to the company.
- **Provide turn-by-turn navigation.** The driver app hands off to Waze or Google Maps.
- **Serve enterprise-scale fleets.** Its limits of 15 vehicles and 200 stops a plan are final
  ([Q-90]: real fleet size and daily stops); reopen them when a larger company wants DispatchFlow.
- **Beat OR-Tools on static benchmarks.** The DispatchFlow solver is not expected to outperform
  OR-Tools' mature routing solver on standard static VRPTW instances. Its target is live
  re-optimization ([NFR-25]: incremental re-optimization study); the static comparison measures how
  close it comes ([NFR-24]: static quality target).
  ([ADR-005](../ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md): OR-Tools first
  with our solver as research)
- **Depend on the DispatchFlow solver.** No product capability, default, demo or pilot run requires
  it. OR-Tools serves every solve unless the DispatchFlow solver is chosen ([FR-20]: choice of
  solver). ([ADR-005](../ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md):
  OR-Tools first with our solver as research)
- **Keep payroll, attendance or statutory driver-hours records.** Reopen when the company requires
  it or a legal duty is confirmed ([Q-39]: legal driver breaks). (ADR pending: scope cuts for the
  first release)
- **Manage inventory or warehouses.** Reopen when the company cannot load vans without it.
  (ADR pending: scope cuts for the first release)
- **Manage fleet maintenance.** Reopen when the company asks to plan around scheduled servicing.
  (ADR pending: scope cuts for the first release)
- **Forecast demand.** Reopen when the company needs a plan prepared before its orders exist.
  (ADR pending: scope cuts for the first release)
- **Integrate vehicle telematics or trackers.** Positions come only from the driver app ([FR-31]:
  location reporting during a route). Reopen when the company's vans already carry trackers.
  (Proposed, 2026-11-03) (ADR pending: scope cuts for the first release)
- **Run the dispatcher console offline.** Only the driver app works without a connection
  ([FR-69]: route shown offline, [FR-70]: report stops offline); printed routes
  ([FR-72]: print or export routes) are the console's fallback. Reopen when the company's
  dispatcher cannot get through an outage on printed routes. (Proposed, 2026-11-03)
  (ADR pending: scope cuts for the first release)
- **Take orders from end customers.** There is no storefront or order form for buyers; orders come
  from a dispatcher, an import or, later, an API ([FR-116]: order intake API). Reopen when the
  company asks to take orders directly from its buyers. (Proposed, 2026-11-03) (ADR pending: scope
  cuts for the first release)
- **Model electric-vehicle range and charging.** Reopen when the company's fleet includes electric
  vans whose range limits a day. (Proposed, 2026-11-03)
  (ADR pending: scope cuts for the first release)
- **Monitor cargo temperature.** Refrigeration is at most a van attribute ([FR-112]: orders that
  need a van or driver attribute). Reopen when the company must keep temperature records for its
  goods. (Proposed, 2026-11-03) (ADR pending: scope cuts for the first release)
- **Start or apply a re-optimization without a dispatcher.** Only a person starts one ([FR-332]:
  only a person starts a re-optimization) and applies it ([FR-255]: nothing changes until
  accepted, [BR-47]: published routes change only through named actions). Reopen when the company
  runs days with no dispatcher on duty. (Proposed, 2026-11-03) (ADR pending: scope cuts for the
  first release)
- **Hold conversations with recipients.** A reply is read only for a rating, a comment or an opt-out
  ([FR-357]: rating replies, [FR-360]: rating comments, [FR-363]: opting out of messages); a
  recipient with a question calls the company. Reopen when the company wants to answer recipients
  by message. (Proposed, 2026-11-10)
- **Message recipients on WhatsApp or by email.** Messages go by SMS only
  ([ADR-031](../ADRs/ADR-031-sms-through-an-israeli-provider-for-recipient-messages.md): SMS for messages to
  recipients). Reopen when ADR-031's revisit trigger fires. (Proposed, 2026-11-10)

> Native mobile apps were a non-goal in the implementation plan. That was reversed on 2026-10-09:
> the driver app ships for iOS and Android
> ([ADR-003](../ADRs/ADR-003-dispatcher-console-and-driver-app.md): web console and React Native
> driver app). The plan was revised on 2026-10-28 to match: the driver app is native and a Must, and
> live driver location is a Must. On the same day the initial presentation stopped marking the
> driver client optional.

## Open questions

| ID | Question | Blocks |
|---|---|---|
| Q-01 | Which delivery company is it, and what does it provide — historical orders and routes, fleet data, access to dispatcher and drivers? Needed before the approval meeting; fallback is synthetic data ([FR-45]: synthetic order generator). Data owner: the company. | FR-40, FR-47, FR-48 and FR-276 |
| Q-02 | Geocoding provider: Hebrew-accuracy test on real addresses against at least one alternative, plus a review of Google's terms on non-Google maps and caching. The chosen provider's terms must also allow keeping the coordinates copied onto customers and depots; if Google stays, those copies are geocoded again on a schedule before its retention runs out (settles Q-25). What does the chosen provider's answer count as partial or ambiguous ([FR-63]: doubtful addresses flagged before a solve)? Do its terms and price allow suggestions while typing ([FR-139]: address suggestions while typing), and does the accuracy test show that normalization ([FR-140]: Hebrew address normalization) is needed? The test also covers addresses in Arabic and in English transliteration ([Q-83]: Arabic or transliterated addresses). | FR-12, FR-13, NFR-17 — ADR pending: Google geocoding provider — also FR-63, FR-139, FR-140 |
| Q-03 | Dispatcher map: Leaflet or MapLibre, and which tile source (OpenStreetMap's public tile servers have a usage policy). | FR-24 |
| Q-04 | ~~Real-time channel.~~ **Resolved** on 2026-11-04 by [ADR-026](../ADRs/ADR-026-server-sent-events-and-push-for-live-updates.md) (server-sent events and push): server-sent events to the console, HTTP POST for driver and console actions, and a push to a backgrounded driver app, which then fetches its route. An alert raised while the console is closed waits in the alert list ([FR-335]: alerts wait in the alert list). | FR-16, FR-32, FR-33 and FR-34, FR-65, FR-71, FR-95, FR-101, FR-102, BR-27, FR-152, FR-222, BR-89 |
| Q-05 | ~~Status sets and Plan transitions.~~ **Resolved** on 2026-10-09 by ADR pending: plan lifecycle publishing stability and drop reasons. ~~What triggers a published plan's completion.~~ **Resolved** on 2026-11-04: the end of its date, at midnight Israel time ([BR-102]: plans complete at midnight). | BR-12 |
| Q-06 | ~~How heavily route stability weighs.~~ **Resolved** on 2026-11-04 by [ADR-029](../ADRs/ADR-029-solver-objective-and-re-optimization-rules.md) (solver objective and re-optimization rules): a penalty per moved stop, starting at 5 minutes of travel time and tuned before the study ([BR-115]: the re-optimization penalties). Under [BR-106] (goods stay on their van), only drops and the stops of a van that is out count. (The measure itself was settled by ADR pending: plan lifecycle publishing stability and drop reasons.) | FR-37, FR-46, NFR-07 |
| Q-07 | ~~Who starts a re-optimization, and what lateness counts as at risk.~~ **Resolved** on 2026-11-04: only a person starts one ([FR-332]: only a person starts a re-optimization), and a stop is at risk 15 minutes before its window ends ([FR-253]: when a stop is at risk). | FR-253, FR-35 |
| Q-08 | ~~Numeric targets.~~ **Resolved** on 2026-11-04: [NFR-08] (edit-check latency), [NFR-09] (live update latency), [NFR-10] (API load test), [NFR-180] (alert delivery time) and [FR-343] (SUS score without a pass mark) carry the values. 1% downtime, the backup and a 4-hour restore stand, and a restore after losing the VM does not count against the 1% ([NFR-183]: VM loss excluded from downtime). | NFR-08, NFR-09, NFR-10, NFR-14, FR-48 and NFR-28, NFR-29, FR-207, FR-71, FR-95, FR-101, FR-102, NFR-59, NFR-125, NFR-126, NFR-128 |
| Q-09 | ~~May a plan with flagged violations be published?~~ **Resolved** on 2026-10-09: no ([BR-17]: no publishing with violations; ADR pending: plan lifecycle publishing stability and drop reasons). | FR-23 |
| Q-10 | ~~CSV column format, encodings, the customer file and the Excel sheet.~~ **Resolved** on 2026-11-04: named template columns ([FR-309]: fixed import columns); UTF-8, or Windows-1255 with a confirmed preview ([FR-313]: encoding detection); no UTF-16 ([FR-314]: UTF-16 files refused); customer rows as [FR-318] (customer row rules) says; the first sheet ([FR-319]: first sheet only). (Whether a file with invalid rows imports its valid rows was **resolved** on 2026-10-10: it imports nothing, and rows name their customer by address — ADR pending: CSV order import.) | FR-10 and FR-61, FR-210, FR-84, FR-138 |
| Q-11 | ~~Capacity units, and an order with no volume.~~ **Resolved** on 2026-11-04: kilograms and litres, and a blank volume counts as zero ([BR-50]: capacity units and blank volumes). The pre-flight check lists those orders ([FR-230]: pre-flight check). | FR-05, FR-09 — ADR pending: capacity weight and volume, NFR-68 |
| Q-12 | ~~Role permission matrix: which roles may manage fleet, customers and orders, solve, edit, publish, re-optimize.~~ **Resolved** on 2026-10-11: an Admin does everything a Dispatcher does, plus the company and its users; a Dispatcher runs every operation and reads users; a Driver works their own route only (the matrix under [FR-03]: role-based access; ADR pending: authorize every web handler by role). | FR-03 |
| Q-14 | Proof files: **resolved** on 2026-11-04 by [ADR-027](../ADRs/ADR-027-proof-files-on-the-vm-disk-with-hourly-backups.md) (proof files on the VM disk): on the VM's disk, backed up off it. Retention defaults are in [NFR-184] (default retention periods), [NFR-76] (log retention) and [NFR-150] (export then deletion when the company leaves). **Still open:** retention for orders and stops with their recipients and entrance codes, customers never retired, Admin and Dispatcher accounts, audit records, sessions, the geocode cache, research instances and backups, which [NFR-31] (retention periods) requires. The legal basis for tracking drivers moved to [Q-42] (legal advice on personal data). Suggested owner: Team, legal advice. | FR-30, FR-31, NFR-19 and NFR-31, NFR-76, FR-87, FR-160, NFR-150, NFR-184 |
| Q-15 | ~~When location reporting runs, how often, staleness and refused permission.~~ **Resolved** on 2026-11-04: every 30 seconds from route start to finish ([FR-31]: location reporting during a route, [NFR-30]: location collected only on a route), stale after 5 minutes by the server's clock ([FR-74]: age of each vehicle position), and a refused permission shows the dispatcher that location is unavailable ([NFR-187]: location permission refused). | FR-31 and FR-68, FR-74, BR-80, NFR-30, NFR-158 |
| Q-16 | ~~How the app reaches the phones, and how native builds are tested.~~ **Resolved** on 2026-11-04 by [ADR-028](../ADRs/ADR-028-driver-app-through-test-tracks-with-a-device-checklist.md) (driver app through test tracks): TestFlight and Google Play internal testing ([NFR-186]: app distribution through test tracks), a scripted checklist on real phones ([NFR-140]: hand checklist on real phones), and automated end-to-end tests on the web build ([NFR-83]: end-to-end tests). | FR-31, NFR-15 and FR-71, NFR-39, NFR-140, NFR-47, NFR-151, NFR-61, NFR-71, NFR-73, NFR-83 — [ADR-003](../ADRs/ADR-003-dispatcher-console-and-driver-app.md) (web console and React Native driver app), NFR-132 |
| Q-17 | Can the DispatchFlow solver, written in Python, reach enough ALNS iterations within the 30-second budget to compete? If not, what changes? (Evidence of 2026-10-10: about 1,800 iterations in 30 s on a 200-stop day, after constant-time insertion checks; see [optimization-engine.md](../architecture/optimization-engine.md). Whether that competes is for the benchmarks.) | FR-42, NFR-07, NFR-25 — [ADR-002](../ADRs/ADR-002-core-backend-and-separate-optimization-service.md) (Java core and Python solver service) |
| Q-18 | ~~When to adopt PostGIS.~~ **Resolved** on 2026-11-04: when the first feature needs a spatial query, as [ADR-007](../ADRs/ADR-007-postgresql-with-flyway-migrations.md) (PostgreSQL with Flyway migrations) already says. Until then, plain latitude and longitude columns, with service-area and distance checks in application code. | [ADR-007](../ADRs/ADR-007-postgresql-with-flyway-migrations.md) (PostgreSQL with Flyway migrations) |
| Q-19 | ~~Which reasons the system can give for a dropped order, and how they are determined.~~ **Resolved** on 2026-10-09 by ADR pending: plan lifecycle publishing stability and drop reasons. | FR-18 |
| Q-20 | Buckets and factors: **resolved** on 2026-11-04 by [ADR-029](../ADRs/ADR-029-solver-objective-and-re-optimization-rules.md) (solver objective and re-optimization rules): three buckets ([FR-321]: travel-time buckets), solvers plan at the highest factor and ETAs and the validator use each bucket's ([FR-322]: planning with the highest factor), and the buffer starts at 10% ([FR-227]: ETA buffer). **Still open:** the buckets' starting hours; the factor of an arc that crosses a boundary; which factor the shared objective and lateness use; whether the validator and [BR-03] (service starts inside the time window) ignore the buffer; and what an off-peak factor's earlier arrival costs at a door stop ([BR-122]: door stops are served on arrival). Suggested owner: Team. | FR-90, NFR-33 and ADR pending: no live traffic, FR-227, FR-321, FR-322 |
| Q-21 | ~~Solve and re-optimization limits, margins and queues.~~ **Resolved** on 2026-11-04: a 30-second default solve limit, which an Admin changes ([FR-19]: configurable solve time limit, [FR-183]: planning settings for Admins); a 20-second re-optimization limit ([BR-84]: the re-optimization time limit); failure 30 seconds past either ([BR-32]: a solve that overruns its limit fails); queue limits in [BR-112] (solve queue limits); a queued re-optimization does not survive a restart ([BR-85]: a restart fails a re-optimization). | FR-19 and BR-84, BR-32, FR-183 |
| Q-22 | ~~Which Gehring-Homberger sizes run where.~~ **Resolved** on 2026-11-04: none in CI, which runs a small Solomon smoke run; the offline study runs the 200-customer instances, and 400 if time remains ([FR-275]: Gehring-Homberger benchmarks). | FR-275 |
| Q-23 | ~~Moving a stop to a van that is not carrying its goods.~~ **Resolved** on 2026-11-04 by [ADR-029](../ADRs/ADR-029-solver-objective-and-re-optimization-rules.md) (solver objective and re-optimization rules): goods stay on their van, except for a van that is out and for new orders ([BR-106]: goods stay on their van, [BR-117]: dropped and moved orders count as new), and the preview flags those for a hand-over ([FR-334]: preview names goods hand-overs). No depot visit is modelled. A late driver cannot shed stops to another van. | FR-07, FR-11, FR-35 and BR-05, FR-111 |
| Q-24 | ~~Should a day with a vehicle withdrawn mid-route be compared with its baseline, and on what?~~ **Resolved** on 2026-10-10: it is compared on kilometres and on-time, without driver-hours, and the comparison names the withdrawn vehicles (FR-269). | FR-270, FR-271 |
| Q-25 | ~~Must geocoded coordinates copied onto customers and depots expire with the geocode cache, and how?~~ **Resolved** on 2026-10-10: it is settled with the provider choice. The chosen provider's terms must allow keeping the copies; if Google stays, they are refreshed on a schedule. That condition now belongs to Q-02, and nothing changes in code until Q-02 is decided. | NFR-17, FR-12, FR-13 |
| Q-26 | ~~May the company's last Admin be demoted?~~ **Resolved** on 2026-10-10: no ([BR-19]: the minimum of active Admins). | FR-01, FR-02 |
| Q-27 | ~~When a Driver's role changes, what happens to their driver record in fleet?~~ **Resolved** on 2026-10-10 by ADR pending: fleet checks the driver role when it reads: fleet stops counting the record while the user is not a Driver, and another driver may take its van. | FR-02, FR-06 |
| Q-28 | ~~Should publishing also catch a shift, capacity or depot change made after the solve?~~ **Resolved** on 2026-10-10 by ADR pending: recheck every route when a plan is published: every route is re-checked through `evaluate` against the current fleet, a broken rule refuses publishing, and the re-checked times are published when nothing breaks ([BR-68]: publishing uses the re-checked times). | BR-05, BR-06, BR-20 |
| Q-29 | ~~The parameters of [NFR-24] (static quality target).~~ **Resolved** on 2026-11-04, not yet agreed with the academic advisor: N = 5%, a 60-second limit, 10 runs, a drop penalty large enough that nothing drops if avoidable, and no fixed vehicle cost ([NFR-24]: static quality target, [NFR-110]: static study scoring, [NFR-111]: static study runs). | NFR-24, NFR-110, NFR-111 |
| Q-30 | ~~The parameters of [NFR-25] (incremental re-optimization study).~~ **Resolved** on 2026-11-04, not yet agreed with the academic advisor: T = 5 seconds, also run at 1, 2, 10 and 30 seconds, over 300 insertions ([NFR-25]: incremental re-optimization study, [NFR-120]: runs at other time limits, [NFR-182]: the study's seeded insertions). | NFR-25, FR-46, NFR-118, NFR-120 |
| Q-31 | ~~What counts as a match, and a row with no match or several.~~ **Resolved** on 2026-11-04: normalized address text ([FR-310]: matching rows by address); no match creates a customer, flagged ([FR-311]: unmatched rows create customers); several reject the row ([FR-312]: ambiguous rows are rejected). | FR-205, FR-60, FR-212 |
| Q-32 | ~~A stop that cannot make its window during re-optimization.~~ **Resolved** on 2026-11-04 by [ADR-029](../ADRs/ADR-029-solver-objective-and-re-optimization-rules.md) (solver objective and re-optimization rules): it may stay on the route, late and flagged, within the driver's shift ([BR-107]: re-optimization may keep late stops). A solve stays bound by [BR-03] (service starts inside the time window). | BR-03, BR-15, BR-23, FR-35, FR-37, FR-46, FR-57, FR-103, NFR-25 |
| Q-33 | ~~A mistaken final outcome.~~ **Resolved** on 2026-11-04: a dispatcher corrects it, with a reason, and the original stays in the history ([FR-333]: dispatcher corrects an outcome, [BR-18]: outcomes are final for drivers). | BR-18, BR-27, BR-28, BR-33, FR-38, FR-73, NFR-70 |
| Q-34 | ~~Manual control on a published plan.~~ **Resolved** on 2026-11-04: only the named actions of [BR-47] (published routes change only through named actions). To change a route: re-optimize, hand over or cancel. [FR-22] (move stops by hand) stays a Should. | BR-12, BR-16, FR-22, FR-23, FR-32, FR-35, FR-55, FR-207, FR-91, BR-47 |
| Q-35 | ~~One person dispatching and driving.~~ **Resolved** on 2026-11-04: two accounts, with one role each ([FR-293]: one role per user). | FR-02, FR-03, BR-02, BR-20 |
| Q-36 | ~~One driver per van per date, or two part-day drivers.~~ **Resolved** on 2026-11-04: one ([BR-39]: one route per van and per driver). Reopen when the company runs two part-day drivers on one van. | FR-06, FR-15, FR-54, FR-55, FR-79, BR-06, BR-20, BR-39 |
| Q-37 | ~~How drivers without email sign in and recover.~~ **Resolved** on 2026-11-04 by [ADR-030](../ADRs/ADR-030-admin-issued-credentials-and-token-revocation.md) (Admin-issued credentials and token revocation): a username and an Admin-issued password ([FR-294]: first-password rules, [FR-295]: driver sign-in and password reset), and an emailed link for Admins and Dispatchers ([FR-296]: emailed recovery for Admins and Dispatchers). No SMS, and [FR-200] (a driver's phone is no recovery channel) stands. | FR-02, FR-52, NFR-01, NFR-35, NFR-36, FR-200 |
| Q-38 | Are the phones company-owned or personal, which models and OS versions, and what battery use is acceptable over a full shift? Whether personal phones change the consent position needs verification. **Default until the company answers:** drivers' own iOS and Android phones, any version our Expo SDK supports, and the limit of [NFR-47] (battery use). Suggested owner: The company, team. | FR-31, FR-77, NFR-15, NFR-30, NFR-44, NFR-47, Q-15, Q-16, NFR-132 |
| Q-39 | Must routes leave time for driver breaks by law? [Wikipedia](https://en.wikipedia.org/wiki/Israeli_labor_law), which is not the statute, says a work day of at least six hours gives 45 minutes of rest, including an uninterrupted 30-minute break. Rules specific to drivers were not found. Needs verification against the Hours of Work and Rest Law itself, and whether the company must keep records of driver hours ([Non-goals](#non-goals)). **Default until verified** (2026-11-04): [BR-105] (time reserved for breaks). Suggested owner: Team, legal advice. | BR-06, BR-15, FR-17, FR-81, [Non-goals](#non-goals), BR-105 |
| Q-40 | What is the pilot protocol: length, operating hours and days, a shadow period first, collecting a manual baseline, which days are comparable, the target saving X of [NFR-32] (pilot savings target), how actuals are measured, support cover during exams, the manual fallback, whether overnight work is in scope, and what acceptance means if no company is confirmed? Owner: Team. | FR-40, FR-47, FR-48, FR-72, FR-106, FR-114, NFR-29, NFR-32, NFR-33, NFR-34, NFR-128 |
| Q-42 | What needs legal advice about personal data? **Working assumptions until then** (2026-11-04): the company owns the data and the team processes it under a written pilot agreement; hosting in Israel; a deletion anonymizes names in kept rows; backups age out. How long backups are kept is not set. **Support access:** Naim Elijah, Moshe Klein, Bentzion Hadad and Shai Manor may see the company's data for support, and those reads are not audited. Still to verify: database registration; sending addresses to a third-party geocoder; tracking employees and the legal basis for it; whether pharmacy deliveries are health data; the privacy notice ([FR-77]: terms and privacy acceptance); what a report may show about an employee ([FR-109]: report per driver); what a delivery's rating and comment show about its driver ([FR-361]: low-rating alert); a person's rights ([NFR-49]: personal-data requests). Amendment 13 of the Privacy Protection Law was due to take effect on 14 August 2025 ([Pearl Cohen](https://www.pearlcohen.com/major-amendment-to-israeli-privacy-law-set-to-take-effect/)). Suggested owner: Team, legal advice. | Q-02, Q-14, FR-12, FR-31, FR-33, FR-47, FR-77, FR-109, NFR-14, NFR-19, NFR-28, NFR-30, NFR-31, NFR-49, NFR-50, FR-53, FR-73, FR-104, BR-37, FR-160, FR-161, NFR-72, NFR-184 |
| Q-43 | ~~Which priority is highest, and the default.~~ **Resolved** on 2026-11-04: 1 is the highest, and the default is 3 ([BR-53]: the priority scale). | FR-09, FR-10, FR-57, FR-84, BR-53, BR-09 |
| Q-44 | ~~Further interface languages.~~ **Resolved** on 2026-11-04: Hebrew and English only, and [FR-121] (Arabic and Russian driver app) stays a Could. Reopen when the company's drivers need it. | FR-121, NFR-27 |
| Q-45 | ~~The service area's boundary.~~ **Resolved** on 2026-11-04: what the OpenStreetMap road-data extract covers ([FR-320]: the service area). | FR-14, FR-63, FR-64, BR-24, BR-80 |
| Q-46 | ~~A day that does not run, and orders on past dates.~~ **Resolved** on 2026-11-04: a dispatcher can cancel a published plan ([FR-336]: cancel a published plan, [BR-103]: a cancelled plan is final), and past dates refuse orders ([BR-116]: no orders on past dates). | BR-12, BR-34, BR-97, Q-05, FR-11, FR-15, FR-38, FR-39, FR-75 |
| Q-47 | ~~Failure reasons.~~ **Resolved** on 2026-11-04: a fixed list plus other with text ([BR-109]: the fixed list of failure reasons). | FR-242, FR-73, BR-11, FR-161 |
| Q-48 | How long does store review take for an app using background location, and what do Apple's rules on it require? Do drivers count as TestFlight external testers, whose builds go to App Review, and does Google internal testing need each driver's Google account address? All need verification and could move the pilot date. **Decided on 2026-11-04:** the company owns the developer accounts ([NFR-186]: app distribution through test tracks), so distribution waits for [Q-01] (which company and what data). Suggested owner: Team. | Q-16, FR-31, FR-47, NFR-151, NFR-186 |
| Q-49 | ~~The gaps in the measures of [FR-38] (the daily summary).~~ **Resolved** on 2026-11-04: failed stops shown beside on-time ([FR-339]: failed stops beside on-time), a missing arrival uses the delivery time ([FR-340]: delivery time stands in for arrival), clock correction ([BR-114]: report times corrected for the phone's clock), and planned distance ([FR-341]: the summary shows planned distance). | FR-38, FR-41, FR-70, FR-73, FR-106, NFR-32, NFR-33, BR-44 |
| Q-50 | ~~What the baseline of [FR-39] (nearest-neighbour baseline comparison) obeys and proves.~~ **Resolved** on 2026-11-04: it obeys the business rules ([FR-342]: rules the baseline obeys) and is a sanity check. The proof of value stays [NFR-32] (pilot savings target), against the company's own routes. | FR-39, FR-40, FR-108, NFR-32 |
| Q-51 | ~~Strict or proportional drop penalties.~~ **Resolved** on 2026-11-04 by [ADR-029](../ADRs/ADR-029-solver-objective-and-re-optimization-rules.md) (solver objective and re-optimization rules): proportional ([BR-54]: drop penalty scales with priority), with fewer drops as best effort ([BR-55]: fewer drops preferred). A pin, lock or route limit is honoured over priority. For *displaced*, the fact given under [FR-144] (drop reasons explained) says that other orders took its place, without claiming one cause. | BR-54, BR-55, BR-09, FR-17, FR-42, FR-46, FR-91, NFR-24, NFR-25, FR-144, FR-147, FR-159 |
| Q-52 | ~~Whether in-progress includes the stop being driven to.~~ **Resolved** on 2026-11-04: no, a stop locks when its driver marks it *arrived* ([BR-10]: completed and in-progress stops are locked). | BR-10, BR-25, FR-35 |
| Q-53 | ~~What Navigate sends, and a pin after an address change.~~ **Resolved** on 2026-11-04: stored coordinates ([FR-325]: navigation uses stored coordinates), and an address change clears the pin after a warning ([BR-104]: an address change geocodes again). | FR-14, FR-28, FR-56, BR-13 |
| Q-54 | ~~A refresh retried on weak signal.~~ **Resolved** on 2026-11-04 by [ADR-030](../ADRs/ADR-030-admin-issued-credentials-and-token-revocation.md) (Admin-issued credentials and token revocation): a 30-second grace ([NFR-178]: grace for a repeated refresh). | NFR-88, FR-70 |
| Q-55 | ~~How a vehicle, driver or user comes back.~~ **Resolved** on 2026-11-04: a dispatcher marks them available ([FR-300]: mark available again, [FR-301]: van out before the day starts), and an Admin reactivates a user ([FR-297]: reactivate a user). | FR-07, FR-35, FR-53, FR-54, FR-82, BR-20 |
| Q-56 | ~~What FR-50 adds beyond [FR-04] (define depots) and [FR-05] (register vehicles).~~ **Resolved** on 2026-11-04: nothing, so FR-50 is withdrawn. | FR-04, FR-05 |
| Q-57 | ~~What the non-goal on payments covers.~~ **Resolved** on 2026-11-04: cash on delivery and billing for DispatchFlow ([Non-goals](#non-goals)). | [Non-goals](#non-goals) |
| Q-58 | ~~What the 30 seconds and the solve limit include.~~ **Resolved** on 2026-11-04: [NFR-07] (re-optimization response time) runs from the request to the preview on screen, the limit of [NFR-06] (solve time limit range) covers the search only, and an open preview blocks nothing and has no age limit ([BR-38]: one re-optimization at a time, [BR-25]: re-check when a re-optimization is accepted). | BR-25, BR-32, BR-38, FR-19, NFR-06, NFR-07, NFR-25, NFR-57 |
| Q-59 | Does IS 5568, the Israeli web-accessibility standard ([BOIA](https://www.boia.org/blog/israels-digital-accessibility-laws-an-overview)), apply beyond public pages? **Assumed on 2026-11-04, not checked:** only a public page such as [FR-117] (customer live tracking page); the console and the driver app count as internal tools. Needs verification before [FR-117] (customer live tracking page) ships. [NFR-46] (WCAG accessibility level) stands either way. Suggested owner: Team, legal advice. | NFR-46, FR-117 |
| Q-60 | How may pilot coordinates be used in research, given that they identify a home and a replay ([FR-46]: disruption simulation) needs them? **Assumed on 2026-11-04, not confirmed by Ben-Gurion University:** no ethics approval is needed for the usability study ([FR-48]: usability study) or for pilot data ([FR-47]: pilot at the company, [NFR-50]: pilot data pseudonymized). Suggested owner: Team, advisor. | FR-43, FR-46, FR-47, FR-48, NFR-153, FR-168, FR-276 |
| Q-62 | **What monthly budget will the company accept?** **Resolved** on 2026-11-04 as ₪300 at most, and **reopened** on 2026-11-10: SMS messages to recipients ([FR-49]: SMS messages to recipients) raise the cap to ₪1,200 ([NFR-52]: monthly running cost). The team's guess at what the company will pay is ₪200 to ₪300, which the company has not confirmed. Will it pay ₪1,200, or must the messages be cut? Suggested owner: Team, the company. | NFR-52, FR-49 |
| Q-63 | **Which Musts can four people deliver by June 2027, when the timeline on slide 12 of the [initial presentation](../presentations/proposal/DispatchFlow_Proposal_Presentation.pptx) ends?** 208 of 379 functional rows are Must; splitting, the answers of 2026-11-04 and the mentor's additions of 2026-11-10 raised that count. *Size* is blank, so the team sizes the Musts first. Can the pilot Musts be separated from the academic ones ([FR-20]: choice of solver, [FR-42] to [FR-46]: own solver through disruption simulation), and should [FR-13] (cache geocoding results), [FR-21] (fixed cost per vehicle used) and [FR-47] (pilot at the company) stay Must? [FR-274] (no product feature needs our solver) says no product capability depends on [FR-42] (our own solver). [ADR-005](../ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md) (OR-Tools first with our solver as research) says "We build both", behind the port of [ADR-004](../ADRs/ADR-004-pluggable-solver-port.md) (every solver behind one port), so dropping a solver row needs an ADR that supersedes them. Suggested owner: Team, advisor. | FR-13, FR-20, FR-21, FR-42 to FR-47 |
| Q-64 | ~~Which record wins when a report arrives late.~~ **Resolved** on 2026-11-04: the first recorded wins, a dispatcher resolves a conflict ([BR-88]: when a late outcome is refused, [BR-90]: contradicting reports are refused, [FR-333]: dispatcher corrects an outcome), and the route's history decides ([BR-110]: late reports judged by route history). | BR-18, BR-27, BR-28, BR-30, FR-58, FR-70, FR-73, BR-88, BR-90 |
| Q-65 | ~~What a hand-over allows.~~ **Resolved** on 2026-11-04: a free, active, available driver ([FR-302]: who can take a handed-over route), started routes too ([FR-303]: hand over a started route), and a shift break flagged for the dispatcher to confirm ([FR-304]: hand-over that breaks a shift). | FR-27, FR-55, BR-06, BR-20, BR-39 |
| Q-66 | ~~Whether a put-back is a new order or a second stop.~~ **Resolved** on 2026-11-04: a second stop on the same order ([FR-258]: a put-back order gets a new stop, [BR-07]: each order served once or dropped). | FR-57, FR-58, FR-258, BR-07, BR-30, BR-33, BR-35 |
| Q-67 | ~~Reports queued when a session ends.~~ **Resolved** on 2026-11-04: they survive and are sent after the same user signs in ([FR-331]: queued reports survive sign-out); sign-out warns ([FR-330]: sign-out warning); a deactivated driver's are recorded by a dispatcher ([FR-298]: a deactivated driver's reports go unsent); and revocation wipes route data ([NFR-179]: phone data wiped on revocation). | FR-53, FR-248, BR-27, NFR-22, NFR-98, FR-123, FR-127 |
| Q-68 | ~~Order changes on ready, solving and published plans.~~ **Resolved** on 2026-11-04: [FR-315] (date change on a published plan), [FR-316] (order changes on a ready plan) and [FR-317] (order change during a solve). After *arrived*, no change or cancel ([BR-92]: no order change after arrival). | FR-57, FR-58, BR-07, BR-12, BR-21, BR-22, BR-30, FR-206 |
| Q-69 | ~~Which driver-app rows bind the web build.~~ **Resolved** on 2026-11-04: it is online only, as [FR-326] (limits of the web build) says. | FR-69, FR-70, FR-71, NFR-15, NFR-39, FR-155, NFR-61, NFR-71, NFR-73, NFR-132 |
| Q-70 | ~~What counts toward the 200 stops, and where the limit is refused.~~ **Resolved** on 2026-11-04: [BR-113] (what the order limit counts). No new drop reason is needed. | BR-22, BR-101, FR-10, FR-11, FR-18, FR-75, NFR-05 |
| Q-71 | ~~The route lifecycle's gaps.~~ **Resolved** on 2026-11-04: [FR-327] (first report starts the route), [FR-328] (location stops after shift end), [FR-329] (finish with stops pending), [FR-324] (routes for later dates) and [BR-108] (nothing added to finished routes). A finished route has no bearing on a plan's completion ([BR-102]: plans complete at midnight). | FR-29, FR-31, FR-33, FR-39, FR-68, NFR-30, Q-05, FR-27, FR-152, FR-267 |
| Q-72 | ~~Changing or retiring a record in use.~~ **Resolved** on 2026-11-04: [FR-305] (records in use cannot be retired), [FR-306] (changed records apply to later plans) and [FR-307] (what retiring a record does). | FR-53, FR-54, FR-56, FR-60, BR-20, BR-21, BR-25, BR-36, BR-37 |
| Q-73 | ~~Values the proposed rows left open.~~ **Resolved** on 2026-11-04: each row now states its value. The sample strings of [NFR-136] (mixed-direction text) belong to its test, and the period of [NFR-33] (ETA accuracy target) and the start of [NFR-34] (time to a published plan) follow [Q-40] (pilot protocol). | FR-12, FR-172, FR-64, FR-70, FR-101, FR-155, BR-80, BR-31, NFR-27, NFR-28, NFR-29, NFR-31, NFR-33, NFR-34, NFR-35, NFR-95, NFR-37, NFR-129, NFR-44, NFR-137, NFR-138, NFR-46, NFR-47, NFR-49, NFR-135, NFR-53, NFR-75, Q-08, NFR-96, NFR-102, NFR-125, NFR-128, NFR-134, NFR-136, NFR-142, NFR-150, NFR-164 |
| Q-76 | ~~Who takes part in usability sessions before the company is confirmed.~~ **Resolved** on 2026-11-04: stand-ins on synthetic data, then the company's dispatcher ([FR-344]: stand-in usability sessions). | FR-48, Q-60 |
| Q-77 | ~~How much acknowledged data a lost VM may lose.~~ **Resolved** on 2026-11-04 by [ADR-027](../ADRs/ADR-027-proof-files-on-the-vm-disk-with-hourly-backups.md) (proof files on the VM disk): at most an hour during operating hours ([NFR-28]: scheduled off-VM backups). | NFR-28, NFR-64, FR-70 |
| Q-78 | ~~Toll roads.~~ **Resolved** on 2026-11-04: allowed, with no cost counted ([FR-323]: toll roads). Reopen if the company objects to its toll bills. | FR-90, FR-118, BR-09 |
| Q-79 | ~~Depot hours and loading.~~ **Resolved** on 2026-11-04: opening hours only ([FR-129]: depot opening hours). Reopen if vans queue at the depot. | FR-67, FR-129, BR-46 |
| Q-80 | ~~Per-van limits beyond the shift.~~ **Resolved** on 2026-11-04: not needed yet, so [FR-133] (per-vehicle route limits) stays a Could. Reopen when the company names a van that needs one. | FR-133 |
| Q-81 | ~~A second sign-in factor for Admins.~~ **Resolved** on 2026-11-04: not for the pilot, so [FR-128] (second sign-in factor for Admins) stays a Could. ASVS 5.0 item 6.3.3 is Level 2, so the Level 1 check of [NFR-40] (OWASP ASVS self-check) does not require it. | FR-128, NFR-40 |
| Q-82 | ~~Browsers and screen sizes.~~ **Resolved** on 2026-11-04: Chrome and Edge ([NFR-67]: supported browsers). Reopen if the company's dispatcher uses something else. | NFR-67, NFR-58 |
| Q-83 | ~~Addresses in Arabic or transliteration.~~ **Resolved** on 2026-11-04: a miss is flagged for a hand-placed pin ([FR-63]: doubtful addresses flagged before a solve), the test of [Q-02] (geocoding provider) includes them, and [FR-140] (Hebrew address normalization) stays Hebrew only. | FR-12, FR-140, Q-02 |
| Q-84 | ~~The statistical method.~~ **Resolved** on 2026-11-04, not yet agreed with the academic advisor: a Wilcoxon signed-rank test, level 0.05, rank-biserial effect size ([FR-165]: paired statistical tests, [FR-288]: significance level). | FR-165, NFR-24, NFR-25 |
| Q-85 | ~~Moving stops into a route left out.~~ **Resolved** on 2026-11-04: never, and the limited mode is not in the study ([FR-159]: re-optimize chosen routes only). | FR-159, NFR-25 |
| Q-86 | ~~The conditions behind the performance values.~~ **Resolved** on 2026-11-04: [NFR-181] (how latency targets are measured). None has been measured yet. | NFR-57, NFR-58, NFR-59, NFR-60, NFR-61, NFR-109 |
| Q-87 | ~~Who sees a driver's home address.~~ **Resolved** on 2026-11-04: [FR-308] (privacy of a route's home address), if [FR-169] (routes from a driver's own address) is built. | FR-169, Q-42 |
| Q-88 | ~~A stand-in for an unknown volume.~~ **Resolved** on 2026-11-04: none, a blank counts as zero ([BR-50]: capacity units and blank volumes), and the count of [FR-136] (package count) is only shown to the driver. | FR-05, FR-09, FR-136, BR-05, Q-11 |
| Q-89 | ~~An access token outliving its session.~~ **Resolved** on 2026-11-04 by [ADR-030](../ADRs/ADR-030-admin-issued-credentials-and-token-revocation.md) (Admin-issued credentials and token revocation): ASVS 5.0 items 7.4.1 and 7.4.2 are Level 1, so the server refuses it at once ([NFR-177]: revoked tokens refused at once). | NFR-86, NFR-40, FR-173, FR-180, FR-188 |
| Q-90 | ~~The company's real fleet size and daily stops.~~ **Resolved** on 2026-11-04: the limits of 15 vehicles and 200 stops are final ([NFR-04]: fleet size limit, [NFR-05]: plan size limit). A larger company reopens them, and [NFR-07] (re-optimization response time) with them. | NFR-04, NFR-05, NFR-07, BR-36, [Non-goals](#non-goals), BR-101 |
| Q-91 | ~~Regaining control after losing the only Admin.~~ **Resolved** on 2026-11-04 by [ADR-030](../ADRs/ADR-030-admin-issued-credentials-and-token-revocation.md) (Admin-issued credentials and token revocation): the company keeps two active Admins ([BR-19]: the minimum of active Admins, [FR-299]: warning below the Admin minimum), and a named team member can run an audited server step on its written request ([FR-292]: Admin created on a written request). | FR-174, BR-19, NFR-166 |
| Q-92 | Security and privacy gaps from the review of 2026-11-04. What does a restore do to sessions, revocations and deletions ([NFR-125]: restore time)? How are backups encrypted, and who holds their credential ([NFR-28]: scheduled off-VM backups)? Are queued reports and proofs removed from an abandoned phone ([FR-331]: queued reports survive sign-out, [NFR-39]: encrypted storage on the phone)? How does a phone know it was revoked ([NFR-179]: phone data wiped on revocation), given [NFR-92] (uniform refresh refusals)? What clears session data from the console and the web build ([NFR-98]: phone data removed at sign-out)? What removes a push token, and must Expo's access token be used? When is a team member's server access removed, and do server-side reads leave a record ([Q-42]: legal advice on personal data)? May a corrected time later than the server's receipt pass [BR-110] (late reports judged by route history)? Does location stop when a route is handed over or a driver is marked unavailable ([NFR-30]: location collected only on a route)? When do the 30 days of [NFR-150] (export then deletion when the company leaves) start, given the 30 days of [NFR-185] (who runs exports and requests)? Should [NFR-19] (no personal data in logs) name coordinates, recipients and entrance codes? Suggested owner: Team. | NFR-125, NFR-28, FR-331, NFR-39, NFR-179, NFR-98, BR-110, NFR-30, NFR-150, NFR-185, NFR-19 |
| Q-93 | Study design gaps from the review of 2026-11-04, to settle with the advisor before any run, since a full run takes days. How large is the drop penalty, as a number or a formula ([BR-54]: drop penalty scales with priority, [NFR-110]: static study scoring)? Which solver and limit build the 30 plans of [NFR-182] (the study's seeded insertions), at what time of day is an order inserted, is each insertion separate, and how does 200 stops plus one pass [BR-101] (the order limit)? How many insertions may [NFR-118] (which insertions count) leave out, and is that reported? Is the test paired per plan and two-sided, and which limit is primary, given that a difference test cannot show "within 5%" ([FR-165]: paired statistical tests)? Do 60 seconds and 10 runs ([NFR-24]: static quality target, [NFR-111]: static study runs) bind Gehring-Homberger, PyVRP and [FR-44] (scaling study)? Should [NFR-120] (runs at other time limits) include the product's 20 seconds ([BR-84]: the re-optimization time limit)? Does a full study resume after a crash ([NFR-121]: one-command study rerun)? Under [BR-106] (goods stay on their van), stability varies only through drops, so does the stability fallback of [ADR-005](../ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md) (OR-Tools first with our solver as research) still hold? Suggested owner: Naim, advisor. | BR-54, NFR-110, NFR-182, BR-101, NFR-118, FR-165, FR-44, NFR-24, NFR-111, NFR-120, BR-84, NFR-121 |
| Q-94 | Lifecycle and testing gaps from the review of 2026-11-04. What state does an order take when its stop is unreported at midnight ([BR-102]: plans complete at midnight, [BR-33]: the order states)? What if a dispatcher corrects a failed stop to delivered after a put-back ([FR-333]: dispatcher corrects an outcome, [FR-258]: a put-back order gets a new stop)? How does a re-optimization treat a route past its shift after a hand-over ([FR-304]: hand-over that breaks a shift)? What exactly does normalized mean in [FR-310] (matching rows by address)? How is the phone path of [NFR-180] (alert delivery time) measured, and how long may an offline phone take after reconnecting? What call mix and error definition does [NFR-10] (API load test) use, and can k6 hold the consoles' event streams? Which devices are mid-range in [NFR-181] (how latency targets are measured)? Suggested owner: Team. | BR-102, BR-33, FR-333, FR-258, FR-304, FR-310, NFR-180, NFR-10, NFR-181 |
| Q-95 | What consent do messages to recipients need? Is the rating request ([FR-356]: delivered message with a rating request) advertising under the spam law (section 30A of the Communications Law), which needs prior written consent, and can its exception for existing customers cover a carrier? Must recipients get a privacy notice, and how, without links? Is asking for a rating a new purpose for a phone given for delivery? Must the pilot agreement name the SMS provider as a processor of phone numbers ([Q-42]: legal advice on personal data)? Is removing an opt-out at the person's request by phone ([FR-380]: opt-outs seen and recorded by dispatchers) enough, or must it be in writing? No ruling on survey messages was found on 2026-11-10. **Default until answered** (2026-11-10): delivery messages need no consent, nobody turns the rating question on ([FR-375]: the rating question's switch), and "הסר" or "STOP" stops every message. Suggested owner: Team, legal advice. | FR-49, FR-356, FR-357, FR-363, FR-375, Q-42 |
| Q-96 | Which SMS provider? It needs an API over TLS only, a two-way virtual number, Hebrew in Unicode, where one message holds 70 characters, callbacks it signs or sends with a secret in a header ([NFR-192]: SMS provider callbacks), dropping repeated sends by a reference DispatchFlow gives ([NFR-198]: unknown sends not repeated), resending callbacks that are not acknowledged ([NFR-201]: callbacks acknowledged after storing), a spend limit, personal console accounts with a second factor, retention of message content within [NFR-184] (default retention periods) or deletion on request, and a statement of where it holds the data and which other parties receive it. Can its sender show the company's name and still take replies? Israeli price lists seen on 2026-11-10 ask ₪0.02 to ₪0.05 a message before VAT; [NFR-52] (monthly running cost) holds at 150 deliveries a day only below about ₪0.045 a segment before VAT. Twilio asks $0.2575, so it was ruled out ([ADR-031](../ADRs/ADR-031-sms-through-an-israeli-provider-for-recipient-messages.md): SMS for messages to recipients). A signed agreement on its handling of personal data comes before messages reach real recipients. Suggested owner: Team. | FR-49, NFR-52, NFR-192, NFR-198 |

[FR-03]: #company-users-and-roles
[FR-04]: #fleet-depots-and-shifts
[FR-05]: #fleet-depots-and-shifts
[FR-06]: #fleet-depots-and-shifts
[FR-07]: #fleet-depots-and-shifts
[FR-08]: #customers-and-orders
[FR-10]: #customers-and-orders
[FR-11]: #customers-and-orders
[FR-12]: #geocoding
[FR-13]: #geocoding
[FR-14]: #geocoding
[FR-17]: #daily-planning
[FR-18]: #daily-planning
[FR-19]: #daily-planning
[FR-20]: #daily-planning
[FR-21]: #daily-planning
[FR-22]: #manual-editing
[FR-23]: #manual-editing
[FR-28]: #publishing-and-the-driver-app
[FR-29]: #publishing-and-the-driver-app
[FR-30]: #publishing-and-the-driver-app
[FR-31]: #publishing-and-the-driver-app
[FR-34]: #live-operations-and-re-optimization
[FR-35]: #live-operations-and-re-optimization
[FR-36]: #live-operations-and-re-optimization
[FR-37]: #live-operations-and-re-optimization
[FR-38]: #reporting-and-baseline
[FR-39]: #reporting-and-baseline
[FR-41]: #reporting-and-baseline
[FR-42]: #research-and-evaluation
[FR-43]: #research-and-evaluation
[FR-44]: #research-and-evaluation
[FR-45]: #research-and-evaluation
[FR-46]: #research-and-evaluation
[FR-47]: #research-and-evaluation
[FR-48]: #research-and-evaluation
[FR-49]: #messages-to-recipients
[FR-52]: #company-users-and-roles
[FR-54]: #fleet-depots-and-shifts
[FR-55]: #fleet-depots-and-shifts
[FR-56]: #fleet-depots-and-shifts
[FR-57]: #customers-and-orders
[FR-58]: #customers-and-orders
[FR-59]: #customers-and-orders
[FR-60]: #customers-and-orders
[FR-63]: #geocoding
[FR-65]: #daily-planning
[FR-66]: #daily-planning
[FR-67]: #daily-planning
[FR-68]: #publishing-and-the-driver-app
[FR-69]: #publishing-and-the-driver-app
[FR-70]: #publishing-and-the-driver-app
[FR-71]: #publishing-and-the-driver-app
[FR-72]: #publishing-and-the-driver-app
[FR-73]: #live-operations-and-re-optimization
[FR-74]: #live-operations-and-re-optimization
[FR-75]: #live-operations-and-re-optimization
[FR-77]: #company-users-and-roles
[FR-81]: #fleet-depots-and-shifts
[FR-82]: #fleet-depots-and-shifts
[FR-83]: #customers-and-orders
[FR-84]: #customers-and-orders
[FR-87]: #customers-and-orders
[FR-89]: #customers-and-orders
[FR-92]: #daily-planning
[FR-95]: #publishing-and-the-driver-app
[FR-96]: #publishing-and-the-driver-app
[FR-97]: #publishing-and-the-driver-app
[FR-101]: #live-operations-and-re-optimization
[FR-102]: #live-operations-and-re-optimization
[FR-103]: #live-operations-and-re-optimization
[FR-106]: #reporting-and-baseline
[FR-107]: #reporting-and-baseline
[FR-108]: #reporting-and-baseline
[FR-109]: #reporting-and-baseline
[FR-111]: #daily-planning
[FR-112]: #daily-planning
[FR-114]: #fleet-depots-and-shifts
[FR-116]: #customers-and-orders
[FR-117]: #live-operations-and-re-optimization
[FR-121]: #publishing-and-the-driver-app
[FR-125]: #company-users-and-roles
[FR-128]: #company-users-and-roles
[FR-129]: #fleet-depots-and-shifts
[FR-131]: #fleet-depots-and-shifts
[FR-133]: #fleet-depots-and-shifts
[FR-136]: #customers-and-orders
[FR-139]: #geocoding
[FR-140]: #geocoding
[FR-144]: #daily-planning
[FR-145]: #manual-editing
[FR-146]: #manual-editing
[FR-152]: #publishing-and-the-driver-app
[FR-153]: #publishing-and-the-driver-app
[FR-157]: #live-operations-and-re-optimization
[FR-159]: #live-operations-and-re-optimization
[FR-165]: #research-and-evaluation
[FR-169]: #fleet-depots-and-shifts
[FR-174]: #company-users-and-roles
[FR-183]: #company-users-and-roles
[FR-190]: #fleet-depots-and-shifts
[FR-195]: #fleet-depots-and-shifts
[FR-199]: #fleet-depots-and-shifts
[FR-200]: #fleet-depots-and-shifts
[FR-201]: #fleet-depots-and-shifts
[FR-203]: #customers-and-orders
[FR-204]: #customers-and-orders
[FR-205]: #customers-and-orders
[FR-223]: #daily-planning
[FR-227]: #daily-planning
[FR-228]: #daily-planning
[FR-230]: #daily-planning
[FR-233]: #manual-editing
[FR-235]: #manual-editing
[FR-242]: #publishing-and-the-driver-app
[FR-246]: #publishing-and-the-driver-app
[FR-248]: #publishing-and-the-driver-app
[FR-253]: #live-operations-and-re-optimization
[FR-254]: #live-operations-and-re-optimization
[FR-255]: #live-operations-and-re-optimization
[FR-258]: #live-operations-and-re-optimization
[FR-263]: #live-operations-and-re-optimization
[FR-264]: #reporting-and-baseline
[FR-266]: #reporting-and-baseline
[FR-267]: #reporting-and-baseline
[FR-274]: #research-and-evaluation
[FR-275]: #research-and-evaluation
[FR-276]: #research-and-evaluation
[FR-279]: #research-and-evaluation
[FR-281]: #research-and-evaluation
[FR-288]: #research-and-evaluation
[FR-292]: #company-users-and-roles
[FR-293]: #company-users-and-roles
[FR-294]: #company-users-and-roles
[FR-295]: #company-users-and-roles
[FR-296]: #company-users-and-roles
[FR-297]: #company-users-and-roles
[FR-298]: #company-users-and-roles
[FR-299]: #company-users-and-roles
[FR-300]: #fleet-depots-and-shifts
[FR-301]: #fleet-depots-and-shifts
[FR-302]: #fleet-depots-and-shifts
[FR-303]: #fleet-depots-and-shifts
[FR-304]: #fleet-depots-and-shifts
[FR-305]: #fleet-depots-and-shifts
[FR-306]: #fleet-depots-and-shifts
[FR-307]: #fleet-depots-and-shifts
[FR-308]: #fleet-depots-and-shifts
[FR-309]: #customers-and-orders
[FR-310]: #customers-and-orders
[FR-311]: #customers-and-orders
[FR-312]: #customers-and-orders
[FR-313]: #customers-and-orders
[FR-314]: #customers-and-orders
[FR-315]: #customers-and-orders
[FR-316]: #customers-and-orders
[FR-317]: #customers-and-orders
[FR-318]: #customers-and-orders
[FR-319]: #customers-and-orders
[FR-320]: #geocoding
[FR-321]: #daily-planning
[FR-322]: #daily-planning
[FR-323]: #daily-planning
[FR-324]: #publishing-and-the-driver-app
[FR-325]: #publishing-and-the-driver-app
[FR-326]: #publishing-and-the-driver-app
[FR-327]: #publishing-and-the-driver-app
[FR-328]: #publishing-and-the-driver-app
[FR-329]: #publishing-and-the-driver-app
[FR-330]: #publishing-and-the-driver-app
[FR-331]: #publishing-and-the-driver-app
[FR-332]: #live-operations-and-re-optimization
[FR-333]: #live-operations-and-re-optimization
[FR-334]: #live-operations-and-re-optimization
[FR-335]: #live-operations-and-re-optimization
[FR-336]: #publishing-and-the-driver-app
[FR-337]: #publishing-and-the-driver-app
[FR-339]: #reporting-and-baseline
[FR-340]: #reporting-and-baseline
[FR-341]: #reporting-and-baseline
[FR-342]: #reporting-and-baseline
[FR-343]: #research-and-evaluation
[FR-344]: #research-and-evaluation
[FR-351]: #messages-to-recipients
[FR-354]: #messages-to-recipients
[FR-356]: #messages-to-recipients
[FR-357]: #messages-to-recipients
[FR-360]: #messages-to-recipients
[FR-361]: #messages-to-recipients
[FR-363]: #messages-to-recipients
[FR-364]: #customers-and-orders
[FR-366]: #publishing-and-the-driver-app
[FR-372]: #messages-to-recipients
[FR-374]: #messages-to-recipients
[FR-375]: #messages-to-recipients
[FR-376]: #messages-to-recipients
[FR-377]: #messages-to-recipients
[FR-379]: #messages-to-recipients
[FR-380]: #messages-to-recipients
[BR-02]: #access
[BR-03]: #routing-constraints
[BR-04]: #routing-constraints
[BR-06]: #routing-constraints
[BR-07]: #solving-dropping-and-validation
[BR-08]: #solving-dropping-and-validation
[BR-09]: #solving-dropping-and-validation
[BR-10]: #re-optimization
[BR-13]: #orders-records-and-history
[BR-14]: #routing-constraints
[BR-15]: #solving-dropping-and-validation
[BR-17]: #plan-lifecycle-and-publishing
[BR-18]: #driver-reports-and-outcomes
[BR-19]: #access
[BR-20]: #plan-lifecycle-and-publishing
[BR-21]: #plan-lifecycle-and-publishing
[BR-22]: #plan-lifecycle-and-publishing
[BR-24]: #re-optimization
[BR-25]: #re-optimization
[BR-26]: #re-optimization
[BR-27]: #driver-reports-and-outcomes
[BR-28]: #driver-reports-and-outcomes
[BR-30]: #orders-records-and-history
[BR-32]: #plan-lifecycle-and-publishing
[BR-33]: #orders-records-and-history
[BR-34]: #plan-lifecycle-and-publishing
[BR-35]: #orders-records-and-history
[BR-36]: #time-and-limits
[BR-37]: #orders-records-and-history
[BR-38]: #plan-lifecycle-and-publishing
[BR-39]: #routing-constraints
[BR-40]: #routing-constraints
[BR-42]: #solving-dropping-and-validation
[BR-43]: #orders-records-and-history
[BR-46]: #routing-constraints
[BR-47]: #plan-lifecycle-and-publishing
[BR-48]: #access
[BR-50]: #routing-constraints
[BR-51]: #routing-constraints
[BR-52]: #routing-constraints
[BR-53]: #solving-dropping-and-validation
[BR-54]: #solving-dropping-and-validation
[BR-55]: #solving-dropping-and-validation
[BR-56]: #solving-dropping-and-validation
[BR-59]: #plan-lifecycle-and-publishing
[BR-60]: #plan-lifecycle-and-publishing
[BR-68]: #plan-lifecycle-and-publishing
[BR-71]: #plan-lifecycle-and-publishing
[BR-74]: #plan-lifecycle-and-publishing
[BR-79]: #re-optimization
[BR-84]: #re-optimization
[BR-85]: #re-optimization
[BR-88]: #driver-reports-and-outcomes
[BR-89]: #driver-reports-and-outcomes
[BR-90]: #driver-reports-and-outcomes
[BR-92]: #orders-records-and-history
[BR-98]: #orders-records-and-history
[BR-101]: #time-and-limits
[BR-102]: #plan-lifecycle-and-publishing
[BR-103]: #plan-lifecycle-and-publishing
[BR-104]: #orders-records-and-history
[BR-105]: #routing-constraints
[BR-106]: #re-optimization
[BR-107]: #re-optimization
[BR-108]: #re-optimization
[BR-109]: #driver-reports-and-outcomes
[BR-110]: #driver-reports-and-outcomes
[BR-112]: #plan-lifecycle-and-publishing
[BR-113]: #time-and-limits
[BR-114]: #driver-reports-and-outcomes
[BR-115]: #solving-dropping-and-validation
[BR-116]: #orders-records-and-history
[BR-117]: #re-optimization
[BR-119]: #routing-constraints
[BR-120]: #solving-dropping-and-validation
[BR-122]: #routing-constraints
[NFR-01]: #nfr-01
[NFR-03]: #nfr-03
[NFR-04]: #nfr-04
[NFR-05]: #nfr-05
[NFR-06]: #nfr-06
[NFR-07]: #nfr-07
[NFR-08]: #nfr-08
[NFR-09]: #nfr-09
[NFR-10]: #nfr-10
[NFR-11]: #nfr-11
[NFR-12]: #nfr-12
[NFR-13]: #nfr-13
[NFR-15]: #nfr-15
[NFR-16]: #nfr-16
[NFR-17]: #nfr-17
[NFR-19]: #nfr-19
[NFR-20]: #nfr-20
[NFR-21]: #nfr-21
[NFR-23]: #nfr-23
[NFR-24]: #nfr-24
[NFR-25]: #nfr-25
[NFR-26]: #nfr-26
[NFR-27]: #nfr-27
[NFR-28]: #nfr-28
[NFR-29]: #nfr-29
[NFR-30]: #nfr-30
[NFR-31]: #nfr-31
[NFR-32]: #nfr-32
[NFR-33]: #nfr-33
[NFR-34]: #nfr-34
[NFR-35]: #nfr-35
[NFR-37]: #nfr-37
[NFR-39]: #nfr-39
[NFR-40]: #nfr-40
[NFR-42]: #nfr-42
[NFR-46]: #nfr-46
[NFR-47]: #nfr-47
[NFR-49]: #nfr-49
[NFR-50]: #nfr-50
[NFR-52]: #nfr-52
[NFR-57]: #nfr-57
[NFR-60]: #nfr-60
[NFR-61]: #nfr-61
[NFR-67]: #nfr-67
[NFR-76]: #nfr-76
[NFR-83]: #nfr-83
[NFR-90]: #nfr-90
[NFR-92]: #nfr-92
[NFR-95]: #nfr-95
[NFR-96]: #nfr-96
[NFR-97]: #nfr-97
[NFR-98]: #nfr-98
[NFR-101]: #nfr-101
[NFR-102]: #nfr-102
[NFR-109]: #nfr-109
[NFR-110]: #nfr-110
[NFR-111]: #nfr-111
[NFR-114]: #nfr-114
[NFR-116]: #nfr-116
[NFR-117]: #nfr-117
[NFR-118]: #nfr-118
[NFR-119]: #nfr-119
[NFR-120]: #nfr-120
[NFR-121]: #nfr-121
[NFR-122]: #nfr-122
[NFR-125]: #nfr-125
[NFR-135]: #nfr-135
[NFR-136]: #nfr-136
[NFR-137]: #nfr-137
[NFR-138]: #nfr-138
[NFR-139]: #nfr-139
[NFR-140]: #nfr-140
[NFR-150]: #nfr-150
[NFR-161]: #nfr-161
[NFR-171]: #nfr-171
[NFR-172]: #nfr-172
[NFR-177]: #nfr-177
[NFR-178]: #nfr-178
[NFR-179]: #nfr-179
[NFR-180]: #nfr-180
[NFR-181]: #nfr-181
[NFR-182]: #nfr-182
[NFR-183]: #nfr-183
[NFR-184]: #nfr-184
[NFR-185]: #nfr-185
[NFR-186]: #nfr-186
[NFR-187]: #nfr-187
[NFR-190]: #nfr-190
[NFR-192]: #nfr-192
[NFR-196]: #nfr-196
[NFR-198]: #nfr-198
[NFR-201]: #nfr-201
[NFR-206]: #nfr-206
[Q-01]: #open-questions
[Q-02]: #open-questions
[Q-14]: #open-questions
[Q-20]: #open-questions
[Q-24]: #open-questions
[Q-38]: #open-questions
[Q-39]: #open-questions
[Q-40]: #open-questions
[Q-42]: #open-questions
[Q-48]: #open-questions
[Q-56]: #open-questions
[Q-57]: #open-questions
[Q-59]: #open-questions
[Q-60]: #open-questions
[Q-62]: #open-questions
[Q-83]: #open-questions
[Q-90]: #open-questions
[Q-95]: #open-questions
[Q-96]: #open-questions

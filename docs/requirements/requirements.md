# DispatchFlow — Requirements

What the system must do. The [README](../../README.md) describes the product,
[`architecture.md`](../architecture/architecture.md) how it is built, and the
[ADRs](../ADRs/README.md) why.

- **Priorities** are MoSCoW: **Must**, **Should**, **Could**. **Won't** is under
  [Non-goals](#non-goals). *Size* and *Enforced by* stay blank until the team sizes and implements a
  row. BR and NFR rows without a Priority column state theirs at the end of the text, if they have one.
- **IDs are never reused.** Withdrawn: FR-78, FR-122, BR-01, NFR-02, NFR-41, Q-13, Q-41, Q-61
  ([ADR-024](../ADRs/ADR-024-one-delivery-company-no-multi-tenancy.md): one company and no
  multi-tenancy), FR-50 ([Q-56]: adds nothing beyond depots and vehicles), and FR-295
  ([ADR-036](../ADRs/ADR-036-one-account-per-person-signed-in-by-email.md): one account per
  person, signed in by email). Never used: FR-166, FR-170, FR-171, NFR-63, NFR-78, NFR-81, NFR-84,
  Q-74, Q-75.

## Purpose and scope

DispatchFlow replaces hand-planned routes at one delivery company
([ADR-024](../ADRs/ADR-024-one-delivery-company-no-multi-tenancy.md): one company and no
multi-tenancy): it plans each day's routes, re-plans them when the day goes wrong, and measures the
result against a baseline. The [README](../../README.md#what-and-why) has the full description.

- **Users:** Admins, Dispatchers and Drivers, as the [permission matrix](#company-users-and-roles)
  sets out. Recipients take part only by SMS, with no account ([FR-49]: SMS messages to recipients).
- **Clients:** a web app and a phone app, each serving Admins, Dispatchers and Drivers ([NFR-15]: web
  app for all roles, [NFR-132]: phone app for all roles).
- **Cost:** DispatchFlow itself is free; the company pays its own running costs ([NFR-52]: monthly
  hosting cost, [NFR-254]: SMS cost, [Q-62]: the monthly budget).
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
[`use-cases.md`](use-cases.md) walks through each use case step by step, with diagrams; this table
stays the authority.

| ID | Actor | Goal | Main flow | Alternatives and failures | Requirements |
|---|---|---|---|---|---|
| UC-01 | Admin | Set up DispatchFlow | Sign in with the account created at deployment; set the company's settings | Sign-in fails; a forgotten password is recovered | FR-01, FR-52, FR-125, FR-291, FR-393, FR-172, FR-183, FR-184, FR-185, FR-186, FR-292, FR-299, FR-294, FR-296, FR-386, FR-346, FR-345, FR-350, FR-375 |
| UC-02 | Admin | Give people access | Create users with roles; they receive a set-password link and sign in | A forgotten password is recovered; a leaver is deactivated; the last two Admins cannot be demoted | FR-02, FR-03, FR-52, FR-53, FR-123, BR-19, FR-172, FR-173, FR-174, FR-175, FR-177, FR-178, FR-179, FR-180, FR-293, FR-294, FR-296, FR-386, FR-297, FR-298, FR-345, FR-389, BR-123 |
| UC-03 | Dispatcher | Set up the fleet | Define depots; register vehicles and drivers; set a date's drivers, shifts and vehicles; retire what is no longer used | A vehicle breaks or a driver falls sick during the day | FR-04 to FR-06, FR-54, FR-56, FR-79, FR-129 to FR-132, FR-189, FR-190, FR-193, FR-195, FR-196, FR-197, FR-198, FR-199, FR-200, FR-305, FR-306, FR-307 |
| UC-04 | Dispatcher | Keep customers current | Create or import customers; check geocoded pins; fix a bad pin by dragging it | No match, several matches, or the geocoder is down | FR-08, FR-12 to FR-14, FR-56, FR-59, FR-63, FR-64, FR-83, FR-134, FR-139, FR-140, FR-215, FR-193, FR-208, FR-216, FR-217, FR-218, FR-210, FR-212, FR-219, FR-220, FR-221, FR-305, FR-306, FR-307, FR-320, FR-318, FR-377 |
| UC-05 | Dispatcher | Load the day's orders | Import a CSV or enter orders by form, or copy a previous day | A file with any rejected row imports nothing and lists every reason | FR-09, FR-10, FR-60 to FR-62, FR-84, FR-85, FR-138, FR-203, FR-204, FR-205, FR-209, FR-211, FR-213, FR-309, FR-319, FR-310, FR-398, FR-311, FR-312, FR-313, FR-399, FR-314, FR-364, FR-377 |
| UC-06 | Dispatcher | Plan the day | Run the pre-flight check; solve; review routes on the map, timeline and table; edit by hand | Some orders are dropped with reasons; the solve fails and is requested again | FR-15 to FR-25, FR-65 to FR-67, FR-90, FR-91, FR-141 to FR-151, FR-222, FR-223, FR-224, FR-233, FR-234, FR-225, FR-226, FR-227, FR-228, FR-230, FR-231, FR-232, FR-235, FR-236, FR-237, FR-238, FR-239, FR-240, FR-241, FR-321, FR-322, FR-400, FR-401, FR-402, FR-403, FR-323, BR-119, BR-120, BR-121, BR-126, BR-127, BR-128, FR-385 |
| UC-07 | Dispatcher | Publish the plan | Publish; every driver is alerted and sees their route | Publishing refuses over violations, changed vehicles or new orders | FR-26, FR-152, BR-17, BR-20 to BR-22, BR-63, BR-64, BR-65, BR-66, BR-67, BR-68, BR-69, BR-70, BR-71, BR-72, BR-73, FR-336, FR-337, FR-338 |
| UC-08 | Driver | Drive the route | Load the vehicle from the loading list; start the route; navigate to each stop, calling the customer if needed; mark it delivered with proof, or failed with a reason; finish | No connection: reports queue and are sent later; a problem is reported to the dispatcher; a route change arrives mid-route | FR-27 to FR-32, FR-59, FR-68 to FR-72, FR-95 to FR-97, FR-153 to FR-156, FR-242, FR-243, FR-208, FR-244, FR-245, FR-246, FR-247, FR-248, FR-249, FR-250, FR-251, FR-252, FR-324, FR-325, FR-326, FR-327, FR-328, FR-329, FR-330, FR-331, FR-347, FR-365, FR-366, FR-367, FR-368, NFR-204, NFR-205 |
| UC-09 | Dispatcher | Watch the day | Follow vehicles and stop statuses live; act on the alert list | A position is stale; a stop is at risk; a route has not started | FR-33, FR-34, FR-74, FR-101, FR-102, FR-157, FR-158, FR-253, FR-254, FR-260, FR-261, FR-335, FR-405, FR-361, FR-369, FR-383 |
| UC-10 | Dispatcher | Recover from a disruption | Mark a vehicle or driver unavailable, or add or change orders; re-optimize; review the preview; accept | The preview is declined, fails or is refused because something changed since | FR-07, FR-11, FR-35 to FR-37, FR-57, FR-75, FR-82, FR-103, FR-159, BR-25, BR-26, FR-255, FR-256, FR-206, FR-396, FR-397, FR-258, FR-194, BR-82, BR-83, BR-84, BR-85, BR-86, FR-300, FR-301, FR-334, FR-332, FR-315, FR-316, FR-317 |
| UC-11 | Dispatcher | Change a published route without optimizing | Hand a route to another driver, or cancel an order | The order's stop was already reported | FR-55, FR-58, FR-73, BR-30, BR-47, FR-191, FR-192, FR-207, FR-257, BR-92, BR-77, BR-78, FR-302, FR-303, FR-304, FR-394, FR-395, FR-333, FR-404 |
| UC-12 | Dispatcher | Review the day | Read the daily summary and the baseline comparison; export it | Nothing delivered: on-time is shown as unavailable | FR-38 to FR-40, FR-106, FR-107, FR-161, FR-264, FR-265, FR-266, FR-267, FR-268, FR-269, FR-270, FR-271, FR-339, FR-340, FR-341, FR-342, FR-362, FR-370, BR-129 |
| UC-13 | Team (research) | Compare the solvers | Run the benchmark harness and the disruption simulation from a configuration; analyse the results | A run the validator rejects is reported, as NFR-113 and NFR-114 say | FR-42 to FR-46, FR-51, FR-163 to FR-165, FR-167, FR-168, NFR-24, NFR-25, NFR-62, FR-272, FR-273, FR-274, FR-275, FR-276, FR-277, FR-278, FR-279, FR-280, FR-281, FR-282, FR-286, FR-287, FR-288, FR-289, FR-290, NFR-110, NFR-111, NFR-112, NFR-113, NFR-114, NFR-115, NFR-116, NFR-117, NFR-118, NFR-119, NFR-120, NFR-121, NFR-122, NFR-182, FR-406, NFR-242, NFR-243, NFR-244, NFR-245, NFR-246, NFR-247 |
| UC-14 | Recipient | Know when the delivery comes, and rate it | Get the "out for delivery", "arriving in about 15 minutes" and "delivered" messages; reply with a rating from 1 to 10 and get a thank-you | The time changes, or the order is dropped, moved or cancelled; the stop fails; a reply that is not a rating gets one reminder; a low rating alerts the dispatcher; the recipient opts out; the SMS provider is down | FR-49, FR-348 to FR-363, FR-371 to FR-384, NFR-189 to NFR-193, NFR-196 to NFR-203 |

## Functional requirements

### Company, users and roles

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-01 | The first Admin account is created when the system is deployed. There is no public sign-up. | Must |  |  |
| FR-291 | The first Admin is created by a step on the server, only when no active Admin exists, with a set-password link ([FR-294]: set-password link) and no default credential, and leaves an audit record. | Must |  |  |
| FR-393 | Every server step that takes a person's identifier ([FR-291]: first Admin created on the server, [FR-292]: Admin created on a written request, [FR-346]: Admin deactivation on a written request, [NFR-49]: personal-data requests, [NFR-185]: data requests run by a named member) reads it at a prompt that does not echo it, so it never enters a command line or shell history. | Must |  |  |
| FR-409 | The audit record of a step of [FR-393] (non-echoing prompt for identifiers) holds the account ID, and its exports go to an encrypted file that is deleted on a set date, never to the terminal. | Must |  |  |
| FR-292 | On a written request from a person the pilot agreement names, a named team member with server access can run a step that creates a new Admin with a set-password link ([FR-294]: set-password link) and leaves an audit record. | Must |  |  |
| FR-346 | That step also deactivates each Admin account the request names and ends its sessions, an exception to [FR-174] (the last Admins cannot be deactivated) and [BR-19] (the minimum of active Admins). | Must |  |  |
| FR-299 | An Admin is warned at every sign-in while the company has fewer than two active Admins ([BR-19]: the minimum of active Admins). | Must |  |  |
| FR-389 | Every active Admin is emailed when an account gains the Admin role, an Admin account is reactivated, or an Admin's password changes or new email takes effect. | Must |  |  |
| FR-02 | An Admin can create users and give each one or more roles: Admin, Dispatcher and Driver; a user with no role is refused with the reason. | Must |  |  |
| FR-293 | An account can hold several roles, and may do everything any of them allows. ([ADR-036](../ADRs/ADR-036-one-account-per-person-signed-in-by-email.md): one account per person, signed in by email) | Must |  |  |
| FR-03 | A user can sign in and use only the functions their roles permit, as the [permission matrix](#company-users-and-roles) says. | Must |  |  |
| FR-52 | A user receives first credentials, recovers a forgotten password and confirms a new email without help from the DispatchFlow team. These calls are exceptions to [NFR-01] (access token on every call). ([ADR-036](../ADRs/ADR-036-one-account-per-person-signed-in-by-email.md): one account per person, signed in by email) | Must |  |  |
| FR-172 | Attempts to recover a password are limited to 5 an hour per account. | Must |  |  |
| FR-294 | A new user gets a set-password link at their email; it works once, expires 72 hours after it is sent, and allows nothing except setting a password that meets [NFR-95] (password policy). ([ADR-036](../ADRs/ADR-036-one-account-per-person-signed-in-by-email.md): one account per person, signed in by email) | Must |  |  |
| FR-296 | Every user signs in with their own email address. | Must |  |  |
| FR-386 | A user who forgets their password recovers it through a link emailed to them ([FR-345]: recovery link rules); requesting one leaves their current password working. | Must |  |  |
| FR-345 | A recovery link works once, expires within 1 hour, and ends every session of the user when used. | Must |  |  |
| FR-53 | An Admin can deactivate a user: sign-in stops, every session ends, and their history stays. | Must |  |  |
| FR-173 | An access token issued before a user is deactivated is refused at once ([NFR-177]: revoked tokens refused at once). | Must |  |  |
| FR-174 | An Admin cannot be deactivated while the company has two or fewer active Admins, and a deactivated Admin does not count toward [BR-19] (the minimum of active Admins). | Must |  |  |
| FR-175 | A deactivated driver counts as no driver under [BR-20] (routes publish only with their solved vehicle and driver). | Must |  |  |
| FR-297 | An Admin can reactivate a deactivated user, whose history stays intact. | Must |  |  |
| FR-298 | A deactivated driver's queued reports cannot be sent, so a dispatcher records those outcomes ([FR-73]: dispatcher records an outcome). | Must |  |  |
| FR-77 | Users accept the terms and a privacy notice. Acceptance is recorded and is never asked for during a route. [Q-42] (legal advice on personal data) | Should |  |  |
| FR-176 | Drivers see what each app collects about them. | Should |  |  |
| FR-123 | A signed-in user can change their own password by giving the current one. | Must |  |  |
| FR-177 | A wrong current password, wherever one is asked for ([FR-123]: change own password, [FR-387]: change of email, [NFR-214]: password before Admin operations), counts toward [NFR-35] (sign-in attempt limit) and [NFR-216] (per-address sign-in limit). | Must |  |  |
| FR-178 | Changing a password ends every session of that user, as [NFR-90] (role, password or email change revokes sessions) requires, so they sign in again. | Must |  |  |
| FR-179 | The password policy of [NFR-95] (password policy) applies to the new password. | Must |  |  |
| FR-180 | An access token issued before a password change is refused at once ([NFR-177]: revoked tokens refused at once). | Must |  |  |
| FR-387 | An Admin can change a user's email, except another Admin's, which only that Admin can change ([ADR-116]: only an Admin changes their own email), and a user can change their own by giving their current password. | Must |  |  |
| FR-388 | A new email takes effect only once a link sent to it is used, within 1 hour of sending; the old address is told, without naming the new one. | Must |  |  |
| FR-411 | Requests for an email-change link are limited to 5 an hour per account that requests them. | Must |  |  |
| FR-392 | Sessions end ([NFR-90]: role, password or email change revokes sessions) when the new email takes effect, and an address another account holds is refused only then, the request being answered the same either way. | Must |  |  |
| FR-124 | A user can choose their interface language, Hebrew or English ([NFR-27]: Hebrew and English interface). | Should |  |  |
| FR-181 | A user's language choice follows them to every device they sign in on. | Should |  |  |
| FR-182 | A new user starts with the company's default language ([FR-125]: the company settings page). | Should |  |  |
| FR-125 | An Admin can see and change the company's settings in one place: name, dispatch phone ([FR-199]: call dispatch from the app), default interface language and the time window a new customer starts with ([FR-08]: create customers). | Must |  |  |
| FR-183 | An Admin can also see and change, in the same place, the departure allowance ([FR-67]: loading time before departure), fixed vehicle cost ([FR-21]: fixed cost per vehicle used) and default solve time limit within [NFR-06] (solve time limit range). | Should |  |  |
| FR-184 | Where [FR-89] (default service duration per customer) is built, an Admin can also see and change there the service duration a new customer starts with, which must be positive. | Should |  |  |
| FR-185 | Where [FR-108] (savings in money) is built, an Admin can also see and change there the cost per kilometre and per driver-hour. | Should |  |  |
| FR-186 | Each change to the company's settings is audited ([NFR-23]: audit records). | Should |  |  |
| FR-126 | An Admin can view the company's audit records ([NFR-23]: audit records) and filter them by user, operation, record and date. | Could |  |  |
| FR-127 | A user can see their own active sessions, with device and last use, and end any of them. | Could |  |  |
| FR-187 | An Admin can end every session of a user without deactivating them. | Must |  |  |
| FR-188 | An access token issued before a session is ended is refused at once ([NFR-177]: revoked tokens refused at once). | Must |  |  |
| FR-128 | The company can require a second sign-in factor for its Admins. | Could |  |  |

**Permission matrix** (FR-03; decided on 2026-10-11, Q-12, [ADR-068]: authorize every web handler by
role). A ✓ means the role may; the server checks it on every call. An account with several roles may
do everything any of them allows ([FR-293]: roles combined in one account), outcomes on its own
route included.

| Capability | Admin | Dispatcher | Driver |
|---|---|---|---|
| Manage the company's details and its users: rename it, register users, change their roles | ✓ | | |
| Read the company's details and its users | ✓ | ✓ | Only the company's details and themselves |
| Manage depots, vehicles, drivers and shifts; customers and orders | ✓ | ✓ | |
| Plan, solve, edit, publish, cancel a published plan and re-optimize; live views and reports | ✓ | ✓ | |
| Work their own route: read it, report stops and proofs, report positions | | | ✓ (BR-02) |
| Record or correct a stop's outcome on a driver's behalf (FR-73, FR-333) | ✓ | ✓ | |
| Confirm or refuse a held backdated report (BR-132) | ✓ | ✓ | |
| Deactivate or reactivate a user (FR-53, FR-297) | ✓ | | |
| Send a new user a set-password link (FR-294) | ✓ | | |
| Change a user's email (FR-387) | ✓ | Only their own | Only their own |
| Retire a driver, which removes only the account's Driver role (FR-56, FR-307) | ✓ | ✓ | |
| Start and finish their own route, report a problem, confirm a route change (FR-68, FR-95, FR-97) | | | ✓ |
| View a stop's proof of delivery (FR-105) | ✓ | ✓ | Only on their own route |
| Read recipients' ratings and comments (FR-360, FR-362) | ✓ | ✓ | |
| See which phones opted out of messages, and record an opt-out (FR-380) | ✓ | ✓ | |
| Change their own password and language; see and end their own sessions (FR-123, FR-124, FR-127) | ✓ | ✓ | ✓ |
| Read the company's settings (FR-125) | ✓ | ✓ | Only its name and dispatch phone |
| Change the company's settings, require a second factor, view audit records, end another user's sessions, turn messages to recipients and the rating question on or off (FR-125, FR-126, FR-128, FR-187, FR-350, FR-375) | ✓ | | |

### Fleet, depots and shifts

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-04 | A dispatcher can define depots, each with a location. | Must |  |  |
| FR-05 | A dispatcher can register vehicles, each with a weight capacity, a volume capacity, a start depot and an end depot. | Must |  |  |
| FR-06 | A dispatcher can register drivers, each with a shift start, a shift end and an assigned vehicle. | Must |  |  |
| FR-07 | A dispatcher can mark a vehicle unavailable during the day, so that re-optimization ([FR-35]: re-optimize after a disruption) redistributes its remaining stops. | Must |  |  |
| FR-300 | A dispatcher can mark a vehicle or driver available again, effective from the next solve or re-optimization. | Must |  |  |
| FR-301 | A vehicle out of service before the day starts is left out of that date through [FR-54] (per-date shifts and vehicles). | Must |  |  |
| FR-54 | A dispatcher can set, for a given date, which drivers work, their shift hours and which vehicle each takes. | Must |  |  |
| FR-189 | The values registered in [FR-06] (register drivers) are the default for a date. | Must |  |  |
| FR-190 | A shift ends after it starts and lies on its date. | Must |  |  |
| FR-55 | A dispatcher can hand a route of a published plan to another driver. The new driver sees it, and the old one no longer does. | Must |  |  |
| FR-191 | A handed-over route takes that date's vehicle assignment ([FR-54]: per-date shifts and vehicles) with it. | Must |  |  |
| FR-192 | A handed-over route keeps its stops, and no optimization runs. | Must |  |  |
| FR-302 | The new driver must be active, available, hold the Driver role and have no route on that date ([BR-39]: one route per vehicle and per driver). | Must |  |  |
| FR-303 | A started route can be handed over, and its reported stops stay recorded. | Must |  |  |
| FR-304 | A hand-over that breaks the new driver's shift ([BR-06]: routes stay within the driver's shift) is flagged, and the dispatcher confirms it. | Must |  |  |
| FR-394 | For a route that a confirmed hand-over put past its shift, the confirmed return time becomes that day's shift end, and a re-optimization cannot stretch it. | Must |  |  |
| FR-395 | A hand-over or cancellation made while the optimization service is down completes, and its routes show *not yet checked* until the validator checks them once the service returns; publishing stays refused meanwhile ([BR-70]: a re-check that cannot run refuses publishing). ([ADR-058]: routes changed while the optimizer is down) | Must |  |  |
| FR-56 | A dispatcher can see, change and retire depots, vehicles, drivers and customers. | Must |  |  |
| FR-193 | A retired record is not offered for new plans and stays on past ones ([BR-37]: past plans keep their values). | Must |  |  |
| FR-305 | A record that an uncancelled plan for today or a later date uses cannot be retired, and the refusal names the plan. | Must |  |  |
| FR-306 | A changed record applies to later plans; on an open plan, [BR-21] (re-check at publishing) or a flag catches any conflict. | Must |  |  |
| FR-307 | A retired customer matches no import row, a retired vehicle does not count toward [BR-36] (the vehicle limit), and retiring a driver removes the Driver role from their account, which keeps its other roles. | Must |  |  |
| FR-79 | A dispatcher can define a weekly shift template, which fills the per-date values of [FR-54] (per-date shifts and vehicles) that a dispatcher has not set. | Should |  |  |
| FR-80 | The company has a working calendar that marks Israeli holidays and short Fridays, so a dispatcher setting a date's drivers and shifts ([FR-54]: per-date shifts and vehicles) can see them. | Could |  |  |
| FR-81 | Routes reserve time for driver breaks, as [BR-105] (time reserved for breaks) says, and the driver chooses when to take one. If breaks are a legal duty, this becomes a Must. [Q-39] (legal driver breaks) | Should |  |  |
| FR-82 | A dispatcher can mark a driver unavailable during the day, so that re-optimization ([FR-35]: re-optimize after a disruption) redistributes their remaining stops. | Must |  |  |
| FR-194 | A driver marked unavailable counts as no driver under [BR-20] (routes publish only with their solved vehicle and driver). | Must |  |  |
| FR-114 | Shifts and time windows can cross midnight. (Would change [FR-190]: valid shift times, [BR-35]: valid time window and service duration, [BR-102]: plans complete at midnight.) [Q-40] (pilot protocol) | Could |  |  |
| FR-129 | A depot can have opening hours. Routes leave it no earlier than it opens and return no later than it closes ([BR-46]: routes keep to depot hours). | Should |  |  |
| FR-195 | An order that cannot be served because of a depot's hours is dropped with the reason *depot hours*. | Should |  |  |
| FR-196 | A manual edit that breaks a depot's opening hours is flagged ([FR-233]: violations flagged after an edit). | Should |  |  |
| FR-130 | A dispatcher sets a depot's location by address, geocoded as a customer's is ([FR-12]: geocode every address), or by placing its pin on the map ([FR-14]: drag a pin to fix a match). | Should |  |  |
| FR-197 | A depot without coordinates cannot start or end a route, so a solve leaves its vehicles out and names them. | Should |  |  |
| FR-131 | A vehicle has a display name and a licence plate, each unique among active vehicles ([BR-48]: unique vehicle names and plates). | Should |  |  |
| FR-198 | A vehicle's display name identifies it on the map, the timeline, the phone app and printouts. | Should |  |  |
| FR-132 | A driver has a contact phone, which the web app shows beside their route. | Should |  |  |
| FR-199 | A driver can call the company's dispatch phone ([FR-125]: the company settings page) from either app with one tap. | Should |  |  |
| FR-200 | A driver's contact phone is never a recovery channel. | Should |  |  |
| FR-133 | A vehicle can have a maximum route duration, a maximum driven distance or a maximum number of stops, and solves and re-optimizations honour them. | Could |  |  |
| FR-201 | An order that cannot be served because of a vehicle limit is dropped with the reason *vehicle limit*. | Could |  |  |
| FR-202 | A manual edit that breaks a vehicle limit is flagged ([FR-233]: violations flagged after an edit). | Could |  |  |
| FR-169 | A route can start or end at a driver's chosen address instead of a depot. (Would change [BR-40]: routes start and end at depots.) | Could |  |  |
| FR-308 | A route's home address is visible only to Admins, Dispatchers and that driver, and counts as personal data ([NFR-19]: no personal data in logs). | Could |  |  |

### Customers and orders

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-08 | A dispatcher can create customers, each with an address, coordinates, a default time window, and notes that drivers see. | Must |  |  |
| FR-09 | A dispatcher can enter an order through a form: customer, date, time window (defaulting to the customer's), weight and volume demand, service duration and priority (1 to 5). | Must |  |  |
| FR-10 | A dispatcher can import a day's orders from a CSV file. ([ADR-086]: CSV order import) | Must |  |  |
| FR-203 | The order import validates every row and reports every rejected row with its reason. | Must |  |  |
| FR-204 | An order import file with any rejected row imports nothing. | Must |  |  |
| FR-205 | Rows of the order import name their customer by address. | Must |  |  |
| FR-309 | An order file has fixed, named columns, which the template of [FR-84] (import template files) shows where it is built. | Must |  |  |
| FR-11 | A dispatcher can add orders to a day whose plan is already published; they enter the routes through re-optimization. | Must |  |  |
| FR-57 | A dispatcher can change an order's time window, leave-at-door mark ([FR-364]: mark an order to leave at the door), demand, service duration, priority or date until its current stop is marked *arrived* or has an outcome ([BR-92]: no order change after arrival). | Must |  |  |
| FR-206 | On a published plan, a change to an order reaches routes through re-optimization, except a date change ([FR-315]: date change on a published plan). | Must |  |  |
| FR-396 | When a dispatcher changes anything but the date of an order on a published plan ([FR-315]: date change on a published plan), the answer says at once that the order is on a published route and that the driver sees the change only after a re-optimization. | Should |  |  |
| FR-397 | A stop whose order changed after publishing, other than by a date change ([FR-315]: date change on a published plan), is marked *changed since publishing* in the live view until a re-optimization applies the change or the stop has an outcome. | Should |  |  |
| FR-315 | On a published plan, changing an order's date takes its stop off at once, as cancelling does, and a route a change breaks is flagged until it is re-optimized. | Must |  |  |
| FR-316 | On a ready plan, cancelling an order or changing its date takes its stop off at once; any other change waits for the re-check at publishing ([BR-21]: re-check at publishing). | Must |  |  |
| FR-317 | A change to an order while its plan is solving refuses publishing, as [BR-71] (orders placed after the solve request refuse publishing) does. | Must |  |  |
| FR-58 | A dispatcher can cancel an order until its current stop is marked *arrived* or has an outcome ([BR-92]: no order change after arrival). | Must |  |  |
| FR-207 | On a published plan, a cancelled order's stop leaves the driver's route at once, the rest of the route stays as it is, and no optimization runs. | Must |  |  |
| FR-59 | A customer has a name and a contact phone. | Must |  |  |
| FR-208 | A driver can call a customer's contact phone from a stop. | Must |  |  |
| FR-60 | An import resolves each row to exactly one customer. | Must |  |  |
| FR-310 | A row matches a customer on normalized address text ([FR-398]: normalized address text for matching). | Must |  |  |
| FR-398 | Normalized address text is Unicode NFC, with whitespace trimmed and collapsed, a single geresh and a single gershayim character, abbreviations expanded from a fixed table, punctuation other than letters, digits, geresh and gershayim replaced by a space, and Latin letters in lower case; matching also ignores invisible format characters and non-spacing combining marks such as niqqud ([ADR-102]: order references and the abbreviation table). | Must |  |  |
| FR-311 | A row matching no customer creates one from the row's name and phone, flagged for a pin check; a row without them is rejected. | Must |  |  |
| FR-312 | A row matching several customers is rejected and lists them. | Must |  |  |
| FR-61 | The import reads Hebrew text in UTF-8, with or without a byte order mark, and in Windows-1255. | Must |  |  |
| FR-209 | An import file whose bytes are not valid in its encoding is rejected with that reason. | Must |  |  |
| FR-313 | A file with a UTF-8 byte order mark or valid UTF-8 is read as UTF-8; any other file is read as Windows-1255, and the dispatcher confirms a preview first. | Must |  |  |
| FR-399 | An import preview expires once 1 hour has passed since it was made, and the next hourly sweep deletes its rows; a discarded preview's rows are deleted at once. | Must |  |  |
| FR-314 | A file with a UTF-16 byte order mark or NUL bytes is refused with that reason. | Must |  |  |
| FR-364 | A dispatcher can mark an order "may be left at the door" on the order form and in a yes-or-no column (yes, no, כן, לא) of the order import ([FR-309]: fixed import columns), where a blank means no. | Must |  |  |
| FR-62 | A dispatcher can copy a previous date's orders to a new date and change them before solving. | Should |  |  |
| FR-83 | A dispatcher can import customers in bulk from a CSV file. | Should |  |  |
| FR-210 | The customer import checks every row, reports every rejected row with its reason, and imports nothing from a file with any rejected row, as the order import does ([FR-203]: every import row validated, [FR-204]: all-or-nothing order import). | Should |  |  |
| FR-318 | A customer row needs a name, an address and a phone; one with the same phone or address as another customer is a duplicate and is rejected. | Should |  |  |
| FR-84 | A dispatcher can download a template file for the order import ([FR-10]: CSV order import) and the customer import ([FR-83]: customer CSV import). | Should |  |  |
| FR-85 | An order can carry a reference from an outside system. | Should |  |  |
| FR-211 | Importing an order whose reference already exists on the same date is refused and reported with that reason. | Should |  |  |
| FR-86 | An order can carry a floor, an apartment and an entrance code, and the driver sees them at the stop. | Should |  |  |
| FR-87 | An order can carry its own recipient name and phone, and the driver sees them at the stop beside the customer's contact. | Should |  |  |
| FR-88 | A dispatcher can define a standing order that recurs on chosen weekdays. | Could |  |  |
| FR-89 | A customer can have a default service duration, which must be positive, used for that customer's new orders. | Could |  |  |
| FR-110 | Pick-up and delivery jobs: an order is collected at one address and delivered to another, not only loaded at a depot. | Could |  |  |
| FR-113 | An order can have several time windows. (Would change [BR-03]: service starts inside the time window, [BR-35]: valid time window and service duration.) | Could |  |  |
| FR-116 | Another system can send orders to DispatchFlow through an API, a revisit trigger of [ADR-006](../ADRs/ADR-006-versioned-openapi-contracts.md) (versioned OpenAPI contracts). | Could |  |  |
| FR-214 | An outside system sending orders needs a credential beyond the access token of [NFR-01] (access token on every call). | Could |  |  |
| FR-134 | A dispatcher can search and filter customers by name, phone and address. | Should |  |  |
| FR-212 | A dispatcher is warned before saving a customer that is likely to duplicate an existing one, with the same phone or a matching address. | Should |  |  |
| FR-135 | An order can carry its own delivery note, beside the customer's standing notes ([FR-08]: create customers). The driver sees both at the stop, each labelled. | Should |  |  |
| FR-136 | An order can carry a package count. The driver sees it at the stop and on the loading list ([FR-153]: loading list). | Could |  |  |
| FR-137 | A dispatcher can see an order's history: when it was created, imported, changed, planned, dropped, moved by a re-optimization, reported or cancelled, and by whom. It is its own record, separate from the audit of [NFR-23] (audit records). | Should |  |  |
| FR-138 | A dispatcher can import orders from an Excel workbook (.xlsx) as well as CSV. | Must |  |  |
| FR-213 | The Excel import is validated exactly as [FR-203] (every import row validated), [FR-204] (all-or-nothing order import), [FR-205] (import rows name customers by address) and [FR-60] (each import row matches one customer) require, within the limits of [NFR-37] (upload size limits), [NFR-96] (unpacked size limit) and [NFR-97] (no external workbook references). | Must |  |  |
| FR-319 | The Excel import reads the workbook's first sheet. | Must |  |  |

### Geocoding

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-12 | The system converts every customer address, including Hebrew addresses, to coordinates. | Must |  |  |
| FR-13 | Geocoding results are cached, so the same address is not looked up repeatedly, within the limits the provider's terms allow ([NFR-17]: map and geocoding terms). | Must |  |  |
| FR-14 | A dispatcher can fix a bad match by dragging the customer's pin on the map. | Must |  |  |
| FR-215 | A corrected pin location is saved for that customer and used for all their future orders. | Must |  |  |
| FR-63 | Before a solve, the system flags to the dispatcher every address whose match is missing, partial or ambiguous, that lies outside the service area, or whose location is more than 300 m from the nearest road, a starting value. [Q-02] (geocoding provider) | Must |  |  |
| FR-216 | An address flag does not stop the solve. | Must |  |  |
| FR-217 | A pin the dispatcher corrected ([BR-13]: hand-corrected locations win) clears that address's flag. | Must |  |  |
| FR-320 | The service area is the company's area within the OpenStreetMap road-data extract, or the whole extract until a company is known; an address outside it drops as *unreachable location* ([FR-66]: unreachable locations are dropped) unless its pin is fixed. | Must |  |  |
| FR-64 | If the geocoding provider cannot be reached, a customer is still saved without coordinates and the lookup is retried every 15 minutes for 24 hours, then stays flagged for a hand-placed pin. | Should |  |  |
| FR-218 | If the geocoding provider cannot be reached or finds no match, the dispatcher can place the customer's pin by hand. | Must |  |  |
| FR-139 | While a dispatcher types a customer's or depot's address, the system suggests matching addresses, and the dispatcher picks one. [Q-02] (geocoding provider) | Could |  |  |
| FR-219 | A typed address the dispatcher keeps without picking a suggestion is geocoded as [FR-12] (geocode every address) says. | Could |  |  |
| FR-220 | Suggestions come through the server, from the provider or the company's own records, within the cap of [NFR-135] (daily geocoding cap). | Could |  |  |
| FR-140 | Before a lookup, the system normalizes common Hebrew abbreviations and spelling variants, such as רח׳ for רחוב, שד׳ for שדרות, ת״א for תל אביב, and full against defective spelling. [Q-02] (geocoding provider) | Could |  |  |
| FR-221 | An address normalized for a lookup is kept and shown as the dispatcher typed it. | Could |  |  |

### Daily planning

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-15 | A dispatcher can create a plan for a date and request a solve over that date's orders, vehicles and driver shifts. | Must |  | `OpenPlanTest`, `RequestSolveTest`, `PlanControllerIT` (opening a plan and queueing its solve) |
| FR-16 | A solve runs in the background, and the dispatcher sees that the plan is solving. | Must |  |  |
| FR-222 | The dispatcher is notified when a solve's result is ready, without reloading. | Must |  |  |
| FR-17 | A solve assigns orders to vehicles, orders the stops of every route, and gives each stop a planned ETA, honouring every rule in [Business rules](#business-rules-and-invariants). | Must |  |  |
| FR-18 | When not every order fits, the solve still returns a plan and lists every dropped order with the reason it was dropped. | Must |  |  |
| FR-223 | A dropped order's reason is *capacity*, *time window*, *shift*, *displaced* by other orders, *missing location* for an order whose customer has no coordinates, *no vehicle available* ([ADR-100]: K's door-window bound and the no-vehicle reason), or one that [FR-66] (unreachable locations are dropped), [FR-146] (take a stop off by hand), [FR-195] (depot-hours drops), [FR-201] (vehicle-limit drops) or [FR-228] (an impossible vehicle or driver pin drops the order) adds. | Must |  |  |
| FR-19 | The solve time limit is configurable within the range in [NFR-06] (solve time limit range), with a default of 45 seconds; the solve returns the best solution found within it. | Must |  | `RequestSolveTest`, `PlanControllerIT` (the solve's time limit) |
| FR-20 | A solve or a re-optimization can be run with either solver: OR-Tools, the default, or the DispatchFlow solver (Clarke-Wright + ALNS). | Must |  |  |
| FR-224 | Outside the optimization service, a solver is known only by its name, and choosing one changes nothing else. | Must |  |  |
| FR-21 | The company can enable a fixed cost per vehicle used, so the solver also reduces the number of vehicles on the road. | Must |  |  |
| FR-65 | When a solve fails, the dispatcher is told without reloading and sees the reason: service unavailable, timed out or interrupted ([BR-32]: a solve that overruns its limit fails, [BR-74]: a restart fails a running solve, [BR-112]: solve queue limits), no uncancelled orders on that date, validator rejection, search failed, no solution within the limit, or request refused. | Must |  |  |
| FR-225 | After a failed solve, the dispatcher can request it again. | Must |  |  |
| FR-66 | If the road network cannot reach a customer's location, the solve still returns a plan and drops that order with the reason *unreachable location*. | Must |  |  |
| FR-67 | The company can set a departure allowance, the minutes between shift start and leaving the depot for loading. | Should |  |  |
| FR-226 | Planned ETAs include the departure allowance. | Should |  |  |
| FR-90 | A planned travel time comes from the road network and varies by time of day. | Must |  |  |
| FR-227 | Planned ETAs include a buffer, starting at 10% of travel time and calibrated from pilot arrivals. [Q-20] (travel-time buckets and factors) | Must |  |  |
| FR-321 | A day has three travel-time buckets: morning peak 07:00–09:30, evening peak 15:30–18:30 and off-peak otherwise. Their factors start at 1.3, 1.3 and 1.0; the hours and factors are estimates, not measurements, calibrated from pilot arrivals. [Q-20] (travel-time buckets and factors) | Must |  |  |
| FR-408 | A bucket includes its start and excludes its end, so 07:00–09:30 means [07:00, 09:30). | Must |  |  |
| FR-322 | Solvers plan with the highest factor and the buffer, the upper bound that [ADR-048] (travel times without live traffic) computes, so a plan stays feasible all day; ETAs and the validator use each bucket's factor. [Q-20] (travel-time buckets and factors) | Must |  |  |
| FR-400 | An arc takes the travel-time factor of the bucket in which it departs, read from the departure's local wall-clock time and cut at the boundaries of [FR-408] (bucket boundaries). | Must |  |  |
| FR-401 | The validator and the planned ETAs use buffered travel times ([FR-227]: ETA buffer). | Must |  |  |
| FR-402 | The objective's travel cost uses per-bucket times without the buffer, and its lateness and window penalties use the validator's times. | Must |  |  |
| FR-403 | At a leave-at-door stop the solver may plan a wait, but the validator re-times the stop with no waiting ([BR-122]: door stops are served on arrival) and charges an early arrival as time outside the window ([BR-120]: leave-at-door window penalty). | Must |  |  |
| FR-323 | Toll roads count like any road, for the whole company, and no toll cost enters the objective. | Must |  |  |
| FR-91 | A dispatcher can pin an order to a vehicle or a driver before a solve, and the solve honours the pin. | Should |  |  |
| FR-228 | A pin that cannot be honoured drops the order with that reason. | Should |  |  |
| FR-92 | Before a dispatcher solves a plan again, the system warns that the manual edits made to it will be discarded. | Should |  |  |
| FR-93 | A dispatcher can compare two solves of the same date side by side. | Could |  |  |
| FR-94 | A dispatcher can ask for a solve that spreads the workload more evenly across drivers. | Could |  |  |
| FR-229 | A solve that spreads the workload needs its own objective beside [BR-09] (the solve objective), which stays the objective that [NFR-24] (static quality target) and [NFR-25] (incremental re-optimization study) score by. | Could |  |  |
| FR-111 | A vehicle can run more than one trip a day, returning to a depot to reload between trips. | Could |  |  |
| FR-112 | A dispatcher can require that an order goes on a vehicle or a driver with a given attribute, such as refrigeration or a licence. | Could |  |  |
| FR-118 | A route respects road limits that depend on the vehicle type. | Could |  |  |
| FR-119 | A dispatcher can plan across several days at once. (Would change [BR-34]: one plan per date.) | Could |  |  |
| FR-141 | Before a solve, a pre-flight check lists the known causes of dropped orders or a failed solve. | Should |  |  |
| FR-230 | The pre-flight check lists orders whose customer has no coordinates ([BR-14]: only orders with coordinates are planned), vehicles without a driver and drivers without a vehicle on that date ([FR-54]: per-date shifts and vehicles), orders heavier or larger than every vehicle, and windows outside every shift. | Should |  |  |
| FR-231 | The pre-flight check does not stop the solve, and a clean check does not promise that no order drops. | Should |  |  |
| FR-142 | While a solve runs, the dispatcher sees how long its search has run out of its time limit ([FR-19]: configurable solve time limit), that it is queued or building the matrix before that, and any overrun past the limit. | Must |  |  |
| FR-143 | Before publishing, each route shows its load against the vehicle's weight and volume capacity and its duration against the driver's shift. | Should |  |  |
| FR-232 | Before publishing, each route also shows its total waiting time and planned distance. | Should |  |  |
| FR-144 | Each dropped order's reason ([FR-18]: dropped orders listed with reasons) comes with the fact that caused it, in plain language, such as "weighs 450 kg; the largest vehicle carries 400 kg" or "window ends at 09:00; the earliest arrival is 09:20". | Should |  |  |

### Manual editing

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-22 | A dispatcher can drag a stop to another position in its route, or to another route, while the plan is ready and not yet published. | Should |  |  |
| FR-23 | After each manual edit, ETAs are recalculated from road travel times, and no optimization runs. | Should |  |  |
| FR-233 | After each manual edit, every time-window, capacity and shift violation is flagged immediately. | Should |  |  |
| FR-234 | A plan with flagged violations cannot be published ([BR-17]: no publishing with violations). | Should |  |  |
| FR-145 | While a plan is ready and not yet published, a dispatcher can undo and redo the manual edits made since the last solve, one at a time. | Should |  |  |
| FR-146 | While a plan is ready and not yet published, a dispatcher can take a stop off its route into the dropped list, with the reason *removed by dispatcher*. | Should |  |  |
| FR-235 | While a plan is ready and not yet published, a dispatcher can put a dropped order on a route by hand, with the same recalculation and flags as [FR-23] (ETAs recalculated after an edit) and [FR-233] (violations flagged after an edit). | Should |  |  |
| FR-236 | Taking a stop off a route by hand is an exception to [BR-08] (drop only when not every order fits), which binds solves and re-optimizations. | Should |  |  |
| FR-237 | An order dropped for *missing location* or *unreachable location* cannot be placed until its pin is fixed. | Should |  |  |
| FR-147 | A dispatcher can lock a route, or the sequence of its stops, before solving again, so that the solve keeps it as it is. | Could |  |  |
| FR-238 | A lock that cannot be honoured fails the solve with that reason ([FR-65]: a failed solve shown with its reason). | Could |  |  |
| FR-239 | The warning of [FR-92] (warning before solving over edits) does not cover a locked route. | Could |  |  |

### Dispatcher views

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-24 | A dispatcher sees all routes of a plan on a map, drawn along the road network, with their stops. | Must |  |  |
| FR-25 | A dispatcher sees a timeline of stops per vehicle. | Should |  |  |
| FR-120 | Admins and Dispatchers can do everything in the phone app that they can do in the web app. ([ADR-035](../ADRs/ADR-035-both-apps-serve-every-role.md): both apps serve every role) | Must |  |  |
| FR-148 | Each route has one colour and one label, used the same way on the map, the timeline, lists and printouts. | Should |  |  |
| FR-240 | Route colours are told apart by labels as well, never by colour alone ([NFR-46]: WCAG accessibility level). | Should |  |  |
| FR-149 | The map also shows depots, dropped orders at their customer's location, and the addresses flagged by [FR-63] (doubtful addresses flagged before a solve), each visibly different from a routed stop. | Should |  |  |
| FR-150 | Selecting a stop on the map, the timeline or a list selects it in the others. | Should |  |  |
| FR-241 | Selecting a stop shows its window, ETA, load, waiting time and any violation. | Should |  |  |
| FR-151 | A dispatcher sees every route and stop of a plan in a table that can be sorted and filtered, as an alternative to the map and timeline. | Should |  |  |

### Publishing and the phone app

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-26 | A dispatcher can publish a plan; each driver then sees their own route. | Must |  |  |
| FR-336 | A dispatcher can cancel a published plan for a day that does not run, while no route has started and no stop is reported. | Should |  |  |
| FR-337 | When a plan is cancelled, its drivers are alerted and its orders return to *unplanned*. | Should |  |  |
| FR-338 | A cancelled plan has no daily summary or baseline comparison, and is left out of trends ([FR-41]: trends dashboard). | Should |  |  |
| FR-27 | A driver sees today's route: stops in order with planned ETAs, customer details and notes. | Must |  |  |
| FR-324 | A route for a later date is listed with its date, and the alert of [FR-152] (drivers alerted on publishing) opens it. | Must |  |  |
| FR-28 | A driver can hand off any stop to navigation: a Navigate action deep-links to Waze or Google Maps. | Must |  |  |
| FR-325 | Navigate sends the stop's stored coordinates, so a corrected pin is honoured. | Must |  |  |
| FR-29 | A driver can mark a stop *arrived*, *delivered* or *failed*. | Must |  |  |
| FR-242 | Marking a stop *failed* requires a reason. | Must |  |  |
| FR-365 | The driver sees at the stop that its order may be left at the door. | Must |  |  |
| FR-366 | When nobody takes a leave-at-door order, the driver may leave it at the door and mark the stop *delivered*, recorded as left at the door; no other order can be recorded so. | Must |  |  |
| FR-30 | A driver can capture proof of delivery: a photo or a signature, with an optional note. | Must |  |  |
| FR-367 | The server refuses a driver's *delivered* report that comes without a proof ([FR-30]: proof of delivery); an outcome a dispatcher records or corrects ([FR-73]: dispatcher records an outcome, [FR-333]: dispatcher corrects an outcome) needs none. | Must |  |  |
| FR-368 | A stop left at the door needs a photo as its proof, because nobody is there to sign; without a camera, a dispatcher records it ([FR-73]: dispatcher records an outcome). | Must |  |  |
| FR-31 | While a route is in progress, the phone app reports the driver's location every 30 seconds, including while it runs in the background on iOS and Android. | Must |  |  |
| FR-243 | While a route is in progress and the web app is open and visible on it, the web app reports the driver's location every 30 seconds. | Must |  |  |
| FR-326 | In the web app a driver works online only: offline work ([FR-69]: route shown offline, [FR-70]: report stops offline), background location, background alerts ([FR-71]: route-change alerts) and encrypted storage ([NFR-39]: encrypted storage on the phone) bind the phone app, and the web app says so. | Must |  |  |
| FR-347 | The web app learns of a route change from its event stream ([ADR-026](../ADRs/ADR-026-server-sent-events-and-push-for-live-updates.md): server-sent events and push), and reads the route again when the stream reconnects. | Must |  |  |
| FR-391 | While the web app is open on a driver's route, it alerts the driver to a route change ([FR-71]: route-change alerts) and to a newly published route ([FR-152]: drivers alerted on publishing). | Must |  |  |
| FR-32 | A driver on the road receives route changes after a re-optimization is applied. Manual edits happen only before publishing. | Must |  |  |
| FR-68 | A driver can start and finish their route. | Must |  |  |
| FR-244 | A route is *not started*, *in progress* or *finished*. | Must |  |  |
| FR-327 | A driver's first stop report starts a route that was not started. | Must |  |  |
| FR-328 | Location collection stops 1 hour after shift end on a route nobody finished. | Must |  |  |
| FR-329 | A driver can finish a route with stops pending after a warning, and the dispatcher is alerted. | Must |  |  |
| FR-69 | The phone app shows the last received route, customer details and notes without a connection, with the route's date and when it was received. | Must |  |  |
| FR-70 | A driver can start and finish a route and report stops without a connection, and capture proof too ([FR-30]: proof of delivery), which is sent with its report. | Must |  |  |
| FR-245 | Offline reports keep the time they were made and survive closing the phone app. | Must |  |  |
| FR-246 | Queued reports are sent within 60 seconds of the connection returning while the phone app is open, otherwise when it next opens. | Must |  |  |
| FR-247 | Reports of one route are sent in the order they were made. | Must |  |  |
| FR-248 | A report stays queued until the server acknowledges it, and a refused one is shown to the driver, except as [NFR-101] (rate-limited reports stay silent) says. ([ADR-087]: driver report sync and idempotency) | Must |  |  |
| FR-330 | Signing out warns the driver about unsent reports. | Must |  |  |
| FR-331 | Reports queued when a session ends stay encrypted on the phone and are sent after the same user signs in again. | Must |  |  |
| FR-71 | When a route changes, the phone app alerts the driver, even in the background. | Must |  |  |
| FR-249 | Either app shows which stops a route change added, removed or reordered. | Must |  |  |
| FR-72 | A dispatcher can print or export each route: stops in order, addresses, phones, notes and each stop's weight and volume. | Must |  |  |
| FR-95 | A driver can report a problem from either app (breakdown, running late, vehicle full), and the dispatcher is alerted. | Should |  |  |
| FR-96 | A driver sees updated ETAs for their remaining stops after each reported stop and each accepted re-optimization, using the projected arrival of [FR-254] (projected arrival). | Should |  |  |
| FR-97 | A driver confirms that they have seen a route change ([FR-71]: route-change alerts). | Should |  |  |
| FR-250 | The dispatcher sees which drivers have not confirmed a route change. | Should |  |  |
| FR-98 | A driver can scan a barcode at loading and at delivery. | Could |  |  |
| FR-99 | A driver can record an age or ID check at delivery, for orders that require one. | Could |  |  |
| FR-100 | A dispatcher and a driver can exchange messages in either app. | Could |  |  |
| FR-115 | A driver can record a partial delivery or a return of goods. | Could |  |  |
| FR-121 | The driver screens of both apps are available in Arabic and Russian, beside Hebrew and English ([NFR-27]: Hebrew and English interface). | Could |  |  |
| FR-152 | When a plan is published, every driver with a route is alerted that their route is ready, through the channel of [FR-71] (route-change alerts). | Must |  |  |
| FR-153 | Before leaving the depot, a driver sees a loading list: their orders in reverse stop order, with weight and volume, and package counts where [FR-136] (package count) is built. | Should |  |  |
| FR-154 | Each stop shows its time window and how long remains until it ends. | Should |  |  |
| FR-251 | A stop at risk ([FR-34]: alerts for at-risk stops) is marked in the driver's list as well as the dispatcher's. | Should |  |  |
| FR-155 | When the driver comes within 100 metres of the next stop, the app they use offers to mark it *arrived*. | Could |  |  |
| FR-252 | Neither app marks a stop *arrived* on its own. | Could |  |  |
| FR-156 | When a driver finishes a route ([FR-68]: start and finish a route), the app they use shows a summary: stops delivered and failed, and any reports still waiting to be sent ([FR-70]: report stops offline). | Could |  |  |

### Live operations and re-optimization

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-33 | A dispatcher sees, live on the map, each vehicle's latest position and each stop's status. | Must |  |  |
| FR-34 | The system detects stops at risk of missing their time window and alerts the dispatcher. | Must |  |  |
| FR-253 | A stop is at risk when its projected arrival is later than its window end minus 15 minutes. | Must |  |  |
| FR-254 | A stop's projected arrival carries the route's observed delay along its planned legs: waiting for a window absorbs delay, and no arrival is projected before now. ([ADR-067]: live arrivals propagate delay along stored legs) | Must |  |  |
| FR-383 | A route's observed delay is the actual time of its latest event, its start or a stop's arrival or outcome, minus that event's planned time. | Must |  |  |
| FR-369 | A leave-at-door stop is never at risk ([FR-253]: when a stop is at risk), because its window does not bind ([BR-119]: leave-at-door windows are preferences). | Must |  |  |
| FR-385 | The map, timeline, table and re-optimization preview mark a leave-at-door stop served outside its window, with the minutes outside, without counting it as a violation. | Must |  |  |
| FR-35 | A dispatcher can re-optimize the remaining stops of a published plan after a disruption: a late driver, an unavailable vehicle, or new orders. | Must |  |  |
| FR-332 | Only a person starts a re-optimization; alerts inform them, and nothing starts one automatically. | Must |  |  |
| FR-36 | Before a re-optimization is applied, the dispatcher sees a preview of what changes for each driver. | Must |  |  |
| FR-255 | Nothing changes for drivers until the dispatcher accepts the re-optimization. | Must |  |  |
| FR-37 | Re-optimization keeps drivers' existing routes as stable as it can: route stability is part of its objective. | Must |  |  |
| FR-256 | Route stability is the number of stops that leave their vehicle, dropped stops included. | Must |  |  |
| FR-73 | A dispatcher can record a stop's outcome on a driver's behalf, with a reason when failed. | Must |  |  |
| FR-257 | The record of an outcome made on a driver's behalf names who made it. | Must |  |  |
| FR-333 | A dispatcher can correct a recorded outcome at any time, with a reason; the original stays in the history, and figures use the correction. | Must |  |  |
| FR-404 | A correction of a failed stop to *delivered* after a put-back ([FR-258]: a put-back order gets a new stop) is allowed while the new stop has no outcome, and that stop then leaves its route, audited; otherwise it is refused, naming the conflict. | Must |  |  |
| FR-74 | The map shows how old each vehicle's position is, marks it stale after 5 minutes by the server's clock, and says so when no position has been received. | Must |  |  |
| FR-75 | A dispatcher can put a failed or dropped order back, for today's re-optimization or on another date. | Should |  |  |
| FR-258 | A put-back order gets a new current stop on the same order, its date and window may change, and the earlier stop's outcome stays as recorded. | Should |  |  |
| FR-76 | A dispatcher can see every order of a date with its state ([BR-33]: the order states) and, when it is on a route, its ETA. | Should |  |  |
| FR-259 | A dispatcher can find an order by customer name or phone. | Should |  |  |
| FR-101 | The dispatcher is alerted when a route has not started ([FR-68]: start and finish a route) 15 minutes after shift start plus the departure allowance ([FR-67]: loading time before departure). | Should |  |  |
| FR-102 | The dispatcher is alerted immediately when a driver marks a stop *failed*. | Should |  |  |
| FR-103 | The preview of a re-optimization ([FR-36]: re-optimization preview) also shows which orders would be dropped, which time windows would break, and the change in the plan's totals. | Must |  |  |
| FR-334 | The preview names each moved stop and new order whose goods need a hand-over ([BR-106]: goods stay on their vehicle). | Must |  |  |
| FR-104 | A dispatcher can see the history of the accepted re-optimizations of a plan: who accepted each, when, and what changed. | Should |  |  |
| FR-105 | A dispatcher can view a stop's proof of delivery ([FR-30]: proof of delivery). | Must |  |  |
| FR-117 | A customer can follow their delivery on a live tracking page. [Q-59] (Israeli accessibility standard) | Could |  |  |
| FR-263 | The live tracking page is an exception to [NFR-01] (access token on every call): it opens only through a link that cannot be guessed, and shows only that delivery. | Could |  |  |
| FR-157 | A dispatcher has one alert list that gathers every alert: stops at risk ([FR-34]: alerts for at-risk stops), problems drivers report ([FR-95]: driver problem reports), routes not started ([FR-101]: route-not-started alert), failed stops ([FR-102]: failed-stop alert), unconfirmed route changes ([FR-97]: driver confirms a route change), late reports ([BR-89]: an accepted late outcome removes the stop from the other route), refused reports ([BR-90]: contradicting reports are refused), routes finished with stops pending ([FR-329]: finish with stops pending) and low ratings ([FR-361]: low-rating alert). | Must |  |  |
| FR-260 | A dispatcher acknowledges an alert, which records who did it and when. | Should |  |  |
| FR-261 | An unacknowledged alert stays visible until its plan completes, and one raised after that stays until it is acknowledged. | Should |  |  |
| FR-335 | An alert raised while the web app is closed waits in the alert list. | Must |  |  |
| FR-405 | In the phone app, an Admin's or Dispatcher's alerts arrive through the open event stream while the app is open and by push while it is in the background; a push carries no personal data ([NFR-218]: no personal data in push notifications). | Must |  |  |
| FR-158 | A dispatcher sees each route's progress: stops done out of all stops, current delay against the plan, next stop and when the driver last reported. | Should |  |  |
| FR-159 | A dispatcher can limit a re-optimization to chosen routes, so that no stop moves into or out of a route left out. This mode is not part of [NFR-25] (incremental re-optimization study). | Should |  |  |
| FR-160 | A dispatcher can replay a past day on the map, within the retention of [NFR-31] (retention periods): each vehicle's recorded positions and each stop's reports, in time order. [Q-42] (legal advice on personal data) | Could |  |  |
| FR-262 | Each replay is audited, an exception to the rule on reads in [NFR-23] (audit records). | Could |  |  |

### Messages to recipients

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-49 | A delivery's recipient gets SMS messages about it, sent through an SMS provider from a two-way virtual number ([ADR-031](../ADRs/ADR-031-sms-through-an-israeli-provider-for-recipient-messages.md): SMS for messages to recipients). [Q-96] (which SMS provider) | Must |  |  |
| FR-348 | A message goes to the order's recipient phone ([FR-87]: recipient name and phone on an order) when it has one, otherwise to the customer's contact phone ([FR-59]: customer name and phone). | Must |  |  |
| FR-349 | Each message starts with the company's name, is written in the company's default interface language ([FR-125]: the company settings page), and has fixed wording per message type in the first release. | Must |  |  |
| FR-382 | With a company name of up to 19 characters and a dispatch phone of up to 11, every message fits one SMS of 70 characters. | Must |  |  |
| FR-350 | An Admin can turn messages to recipients on or off for the company ([FR-125]: the company settings page); they start off, a change applies to messages triggered after it, and replies are read either way. | Must |  |  |
| FR-351 | When a route starts ([FR-68]: start and finish a route), the recipient of each of its stops gets an "out for delivery" message giving the stop's projected arrival ([FR-254]: projected arrival), rounded to the nearest quarter hour, ±30 minutes. | Must |  |  |
| FR-372 | A stop that joins a route in progress, such as a new order or a put-back ([FR-258]: a put-back order gets a new stop), gets the "out for delivery" message when it joins. | Must |  |  |
| FR-373 | Once a stop is *arrived* or has an outcome, it gets none of the messages of [FR-351] to [FR-354] (out-for-delivery through order-change messages) or [FR-372] (stops joining a route in progress). | Must |  |  |
| FR-352 | A recipient gets an "arriving in about 15 minutes" message when their stop's projected arrival ([FR-254]: projected arrival) is 15 minutes away or less, its route is in progress, and the stop before it in plan order, if any, has an outcome. | Must |  |  |
| FR-353 | Once their stop's route has started, a recipient gets a "time changed" message, with a new window as [FR-351] (out-for-delivery message) gives it, whenever the stop's projected arrival is 30 minutes or more from the arrival last sent to them. | Must |  |  |
| FR-384 | A stop gets at most 2 "time changed" messages. | Must |  |  |
| FR-354 | Once their stop's route has started, a recipient gets a message when their order is dropped, moves to another date ([FR-57]: change an order) or is cancelled ([FR-58]: cancel an order). | Must |  |  |
| FR-355 | When a stop's first outcome ([FR-374]: messages follow the first outcome) is *failed*, its recipient gets a "missed you" message with the company's dispatch phone ([FR-125]: the company settings page), and not the failure reason. | Must |  |  |
| FR-356 | When a stop's first outcome is *delivered*, its recipient gets a message that confirms it, says whether it was left at the door ([FR-366]: leaving an order at the door), and, while [FR-375] (the rating question's switch) is on, asks for a rating from 1 to 10. | Must |  |  |
| FR-374 | The first recorded outcome sends its message, whether the driver reports it ([FR-29]: report a stop's status) or a dispatcher records it ([FR-73]: dispatcher records an outcome); a correction ([FR-333]: dispatcher corrects an outcome) sends none and leaves a given rating as it is. | Must |  |  |
| FR-375 | An Admin can turn the rating question on or off ([FR-125]: the company settings page); it starts off. [Q-95] (consent for messages to recipients) | Must |  |  |
| FR-357 | A reply received at most 48 hours after the SMS provider accepted the rating request rates that delivery when it is a rating ([FR-376]: what counts as a rating); only the first rating counts. | Must |  |  |
| FR-376 | A rating is a whole number from 1 to 10, in Western digits with no leading zero, at the start of the reply after spaces are trimmed, and followed by neither a digit nor a decimal point and a digit. | Must |  |  |
| FR-371 | A reply answers the latest rating request sent to its phone, with phones compared in the form that [FR-377] (phones stored in one form) stores them in. | Must |  |  |
| FR-377 | Phones on customers and orders are checked when entered or imported and stored in international form (E.164); a phone that is not a valid number is refused with that reason. | Must |  |  |
| FR-378 | Messages go only to Israeli mobile numbers; a stop whose phone is another kind gets none, and the dispatcher sees why. | Must |  |  |
| FR-358 | A rating gets one thank-you message, and later replies about that delivery get no answer, except as [FR-363] (opting out of messages) says. | Must |  |  |
| FR-359 | Within the 48 hours and before a delivery is rated, the first reply that is neither a rating nor an opt-out gets one message repeating the format; later ones get no answer. | Must |  |  |
| FR-379 | A reply from a phone with no rating request in the last 48 hours gets no answer and is not kept, except an opt-out ([FR-363]: opting out of messages). | Must |  |  |
| FR-360 | Each reply within the 48 hours of a rating request, other than a bare rating or an opt-out, is kept as a comment on the delivery, which Admins and Dispatchers can read. [Q-42] (legal advice on personal data) | Must |  |  |
| FR-361 | A rating from 1 to 4 alerts the dispatcher, as an at-risk stop does ([FR-34]: alerts for at-risk stops), and appears in the alert list where [FR-157] (one alert list) is built. [Q-42] (legal advice on personal data) | Must |  |  |
| FR-362 | The daily summary's total ([FR-38]: the daily summary) shows the average of the ratings given for the day's stops, or unavailable when there is none, and the share of accepted rating requests that were answered. | Must |  |  |
| FR-381 | Ratings appear in no per-route or per-driver figure until [Q-42] (legal advice on personal data) is answered. | Must |  |  |
| FR-363 | A reply whose first word, ignoring letter case and punctuation, is "הסר", "הסרה", "להסרה" or "STOP" opts its phone out: no further messages go to it, and a phone messaged in the last 48 hours gets one confirmation. [Q-95] (consent for messages to recipients) | Must |  |  |
| FR-380 | Dispatchers see on a customer or an order that its phone opted out and, at the person's own request by phone, can record or remove an opt-out, noting who did it and when. | Must |  |  |

### Reporting and baseline

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-38 | A dispatcher sees a daily summary of each plan, per route and in total: distance, driving time, stops, delivered and failed stops, on-time percentage, vehicles used and dropped orders. | Must |  |  |
| FR-264 | The on-time percentage is the share of delivered stops whose actual arrival, as the driver's device recorded it and [BR-114] (report times corrected for the device's clock) corrected it, was at or before their window end. | Must |  |  |
| FR-265 | With nothing delivered, the on-time percentage is shown as unavailable, not as zero. | Must |  |  |
| FR-339 | The summary shows failed stops beside the on-time percentage, so marking late stops *failed* stays visible. | Must |  |  |
| FR-340 | A delivered stop with no arrival report counts its delivery time as its arrival. | Must |  |  |
| FR-341 | The distance in [FR-38] (the daily summary) is planned distance; actual distance is under [FR-106] (planned against actual figures). | Must |  |  |
| FR-39 | Every plan is compared with a naive nearest-neighbour baseline on the same orders and fleet, in kilometres, driver-hours and planned on-time percentage. | Must |  |  |
| FR-266 | Planned on-time percentage is orders served inside their windows over all orders of the day, so dropped orders count as not on time. | Must |  |  |
| FR-370 | The on-time percentages ([FR-264]: the on-time percentage, [FR-266]: the planned on-time percentage) count a leave-at-door stop as on time only when it is served inside its preferred window. | Must |  |  |
| FR-267 | A vehicle is withdrawn mid-route when a re-optimization leaves it out while it still has stops it made: it was marked unavailable ([FR-07]: mark a vehicle unavailable), left without a driver, or its shift ran out. | Must |  |  |
| FR-268 | A withdrawn vehicle's route keeps the stops it made and never returns, so its driver-hours are unknown. | Must |  |  |
| FR-269 | A day with a vehicle withdrawn mid-route is compared on kilometres and on-time only, and the comparison names the withdrawn vehicles. | Must |  |  |
| FR-270 | The baseline of a day with a vehicle withdrawn mid-route is solved after the re-optimization, so its kilometre and on-time figures are not strictly like for like. [Q-24] (baseline for a withdrawn vehicle) | Must |  |  |
| FR-271 | The baseline of a day with a vehicle withdrawn mid-route leaves out a vehicle that was marked unavailable or left without a driver, and gives a vehicle whose shift ran out its whole shift (decided on 2026-10-10). [Q-24] (baseline for a withdrawn vehicle) | Must |  |  |
| FR-342 | The baseline meets windows, as [BR-119] (leave-at-door windows are preferences) qualifies them, capacity and shifts, drops what does not fit, and passes the validator ([BR-15]: only validator-accepted solutions count). | Must |  |  |
| FR-40 | Where the company supplies historical manual routes, a plan can be compared against them on the same measures. | Should |  |  |
| FR-41 | An analytics dashboard shows trends across days: on-time percentage, kilometres saved. | Could |  |  |
| FR-106 | The daily summary ([FR-38]: the daily summary) also shows planned against actual distance, driving time and arrival times. [Q-40] (pilot protocol) | Should |  |  |
| FR-107 | A dispatcher can export summaries and order lists to a spreadsheet. | Could |  |  |
| FR-108 | A summary shows savings in money, from the company's cost per kilometre and per driver-hour. | Could |  |  |
| FR-109 | A dispatcher sees a report per driver. [Q-42] (legal advice on personal data) | Could |  |  |
| FR-161 | A dispatcher sees failed and dropped orders over a range of dates, grouped by reason ([FR-223]: the drop reasons, [FR-242]: a failed stop needs a reason), customer and, where the order was on a route, driver. [Q-42] (legal advice on personal data) | Should |  |  |
| FR-162 | A dispatcher can see the record of every solve and re-optimization of a plan ([NFR-11]: every solve recorded): solver, time limit, duration, objective value and dropped orders. | Could |  |  |

### Research and evaluation

| ID | Requirement | Priority | Size | Enforced by |
|---|---|---|---|---|
| FR-42 | DispatchFlow has its own solver: a Clarke-Wright savings construction improved by Adaptive Large Neighbourhood Search with destroy and repair operators and simulated-annealing acceptance. | Must |  |  |
| FR-272 | The DispatchFlow solver sits behind the same solver interface as OR-Tools. | Must |  |  |
| FR-273 | For a re-optimization, the DispatchFlow solver starts from the current routes and repairs them, destroying and reinserting stops around the disruption, instead of solving again from scratch. | Must |  |  |
| FR-274 | No product capability depends on the DispatchFlow solver ([Non-goals](#non-goals)). | Must |  |  |
| FR-43 | A benchmark harness runs OR-Tools and the DispatchFlow solver on the Solomon instances (56 instances of 100 customers, classes C1, C2, R1, R2, RC1, RC2). | Must |  |  |
| FR-275 | The benchmark harness also runs both solvers on the 200-customer Gehring-Homberger instances, and the 400-customer ones if time remains, offline and never in CI. | Must |  |  |
| FR-390 | The benchmark harness reads a benchmark instance's demands and capacities as weights, with no volume demand and no volume limit, exempt from [BR-50] (capacity and demand values). | Must |  |  |
| FR-276 | The benchmark harness also runs both solvers on the pilot's real days once the pilot provides them. [Q-01] (which company and what data) | Must |  |  |
| FR-277 | The harness reports the gap from best-known solutions where they are published, the DispatchFlow solver's gap from OR-Tools ([NFR-24]: static quality target), and the solve time. | Must |  |  |
| FR-278 | In the benchmark harness, both solvers get the same time limit on the same hardware. | Must |  |  |
| FR-279 | Every benchmark result is scored by the shared objective ([BR-09]: the solve objective) once the validator accepts it ([BR-15]: only validator-accepted solutions count). | Must |  |  |
| FR-44 | A scaling study reports solve time and solution quality at 25, 50, 100 and 200 stops. | Must |  |  |
| FR-406 | The scaling study runs each of its sizes 3 times. | Must |  |  |
| FR-45 | A synthetic order generator builds realistic days from real Israeli addresses, for testing and as the fallback when no pilot data is available. | Must |  |  |
| FR-46 | A simulation replays a real or synthetic day with injected delays, breakdowns and new orders, generated from a recorded seed, so that the day can be replayed. | Must |  |  |
| FR-280 | The simulation runs every re-optimization with each solver at the same set of time limits: OR-Tools warm-started from the current routes, and the DispatchFlow solver repairing them. | Must |  |  |
| FR-281 | At each limit the simulation measures each solver's objective, route stability, elapsed time and whether the new orders were served, all scored by the shared objective ([BR-09]: the solve objective, [BR-56]: the re-optimization objective). | Must |  |  |
| FR-282 | A simulation run in which OR-Tools did not start from the current routes is reported and not counted as warm-started ([NFR-25]: incremental re-optimization study). | Must |  |  |
| FR-47 | A pilot runs DispatchFlow at the company. [Q-01] (which company and what data) [Q-40] (pilot protocol) | Must |  |  |
| FR-407 | If no company is confirmed by the start of M13 (the pilot milestone), the pilot is staged: 5 working days on synthetic data ([FR-45]: synthetic order generator), with classmates as drivers on their own phones and a stand-in dispatcher, on a production-like deployment. | Must |  |  |
| FR-410 | A staged pilot counts as handling real personal data for [NFR-40] (OWASP ASVS self-check), [Q-42] (legal advice on personal data) and [NFR-72] (data inventory), because classmates' accounts and location traces are real. | Must |  |  |
| FR-283 | The company's feedback from the pilot is collected and written up. | Must |  |  |
| FR-48 | A usability study runs task-based sessions with the pilot's dispatcher (build a plan, handle a breakdown), scored with the SUS questionnaire. | Should |  |  |
| FR-284 | The usability study collects driver feedback on route stability after re-optimization. | Should |  |  |
| FR-285 | Usability study results feed UI iterations between rounds. | Should |  |  |
| FR-343 | The SUS score is reported with no pass mark, since one dispatcher is too few respondents. | Should |  |  |
| FR-344 | Until the company's dispatcher is available, sessions use classmates and a stand-in dispatcher, on a separate deployment with synthetic data ([FR-45]: synthetic order generator); a later round uses the company's dispatcher. | Should |  |  |
| FR-51 | The benchmark harness also runs PyVRP, an open-source VRP solver, on the static instances of [FR-43] (Solomon benchmark harness) and [FR-275] (Gehring-Homberger benchmarks) under the same time limits, as a reference for both solvers. | Should |  |  |
| FR-286 | Only the harness uses PyVRP; it never serves a solve in the product. | Should |  |  |
| FR-163 | The DispatchFlow solver can log, at a sampled interval, each iteration's operators chosen, operator weights, current and best objective and acceptance temperature. | Should |  |  |
| FR-287 | Solver logging is off in the product. | Should |  |  |
| FR-164 | The benchmark harness can run the DispatchFlow solver with single destroy or repair operators removed, or with adaptive weights fixed, to measure each part's contribution (an ablation study). | Should |  |  |
| FR-165 | Solver comparisons ([FR-43]: Solomon benchmark harness, [FR-46]: disruption simulation) report per-instance paired results with a one-sided Wilcoxon signed-rank test of non-inferiority and a rank-biserial effect size: [NFR-24] (static quality target) on (gap − 0.05) and [NFR-117] (re-optimization quality target) on (ours − OR-Tools − its margin), the primary limit 5 seconds. | Must |  |  |
| FR-288 | The significance level of a solver comparison is 0.05, fixed before the study. | Must |  |  |
| FR-167 | The simulation of [FR-46] (disruption simulation) can also inject cancellations ([FR-58]: cancel an order), unavailable drivers ([FR-82]: mark a driver unavailable), changed time windows ([FR-57]: change an order) and late driver reports ([BR-27]: late outcomes are still accepted), from the same recorded seed. | Should |  |  |
| FR-168 | An Admin can export a pilot day as a pseudonymized benchmark instance ([NFR-50]: pilot data pseudonymized) that both solvers and PyVRP read. [Q-60] (research use of pilot coordinates) | Must |  |  |
| FR-289 | A pilot-day export is audited. | Must |  |  |
| FR-290 | An exported pilot-day instance stays outside the repository ([NFR-122]: results kept with their configuration). | Must |  |  |

## Business rules and invariants

Rules are grouped by subject.

### Access

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-02 | **Driver scope.** The Driver role lets an account see and update only the stops of routes assigned to it; the account's other roles may allow more. |  |  |
| BR-19 | **Two Admins.** The company keeps two active Admins: an account cannot lose the Admin role while the company has two or fewer. |  |  |
| BR-41 | **Drivers see published routes only.** Through the Driver role the server never sends a plan that is `draft`, `solving`, `ready` or `abandoned`, or a re-optimization preview not yet accepted ([FR-36]: re-optimization preview), nor do its screens show one, whatever the account's other roles. Priority: Must. |  |  |
| BR-45 | **Unique sign-in.** An email address belongs to at most one user, compared after trimming spaces and folding ASCII letters only, with the domain in its ASCII (Punycode) form. Priority: Must. |  |  |
| BR-123 | **No role, no sign-in.** An account that holds no role cannot sign in, recovery sends it nothing, and losing its last role ends its sessions ([NFR-90]: role, password or email change revokes sessions). Priority: Must. |  |  |
| BR-125 | **Links go to the stored address.** Every link and notice goes to the address stored on the account, never to the address typed in a request. Priority: Must. |  |  |
| BR-48 | **Unique vehicles.** A vehicle's display name and licence plate are each unique among active vehicles ([FR-131]: vehicle name and plate); a retired vehicle ([BR-43]: retire instead of delete) does not count. Priority: Should. |  |  |

### Routing constraints

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-03 | **Time windows.** Service at a stop starts inside its order's time window, except as [BR-107] (re-optimization may keep late stops) and [BR-119] (leave-at-door windows are preferences) say. A vehicle that arrives early waits, except as [BR-122] (door stops are served on arrival) says. |  |  |
| BR-49 | **Default time window.** An order's window defaults to its customer's default window. |  |  |
| BR-119 | **Leave-at-door windows.** The time window of a leave-at-door order ([FR-364]: mark an order to leave at the door) is a preference: service may start outside it, within the driver's shift ([BR-06]: routes stay within the driver's shift) and on the order's date. ([ADR-032](../ADRs/ADR-032-leave-at-door-windows-are-preferences.md): leave-at-door windows are preferences) Priority: Must. |  |  |
| BR-122 | **Door stops on arrival.** A vehicle serves a leave-at-door stop when it arrives and never waits there, so the stop's start time follows from its route. ([ADR-032](../ADRs/ADR-032-leave-at-door-windows-are-preferences.md): leave-at-door windows are preferences) Priority: Must. |  |  |
| BR-04 | **Service time.** Each stop occupies the vehicle for the order's service duration, which counts toward the arrival time at the next stop. |  |  |
| BR-05 | **Capacity.** On every route, the total weight demand never exceeds the vehicle's weight capacity, and the total volume demand never exceeds its volume capacity. |  |  |
| BR-50 | **Capacity values.** Vehicle capacities, and an order's weight and volume demands, are positive, in kilograms and litres; a blank value is refused with the reason. |  |  |
| BR-06 | **Shifts.** A route leaves no earlier than its driver's shift start and returns to its end depot no later than the shift end, except after a hand-over confirmed under [FR-304] (hand-over that breaks a shift). |  |  |
| BR-105 | **Break reserve.** Where [FR-81] (driver breaks) is built, a route on a shift of 6 hours or more returns at least 45 minutes before shift end. [Q-39] (legal driver breaks) Priority: Should. |  |  |
| BR-14 | **Routable orders only.** An order can be planned only when its customer has coordinates. |  |  |
| BR-39 | **One route per vehicle and driver.** Within a plan, a vehicle drives at most one route and so does a driver; a route withdrawn mid-route ([FR-267]: vehicle withdrawn mid-route) does not count. (Would be changed by [FR-111]: several trips per vehicle.) Priority: Must. |  |  |
| BR-40 | **Routes start and end at depots.** Except as [BR-51] (re-optimized routes start where the vehicle is) and [BR-52] (a withdrawn route ends at its last reported stop) say, a route starts at its vehicle's start depot and ends at the vehicle's end depot. (Would be changed by [FR-169]: routes from a driver's own address.) Priority: Must. |  |  |
| BR-51 | **Re-optimization start.** During a re-optimization, a route starts at the start point of [BR-79] (re-optimization starts from current locations) and [BR-24] (fallback start without a usable position) instead of its vehicle's start depot. Priority: Must. |  |  |
| BR-52 | **Withdrawn route end.** A route withdrawn mid-route ([FR-267]: vehicle withdrawn mid-route) ends at its last reported stop. Priority: Must. |  |  |
| BR-46 | **Depot hours.** When a depot has opening hours ([FR-129]: depot opening hours), a route leaves it no earlier than it opens and returns no later than it closes. Together with [BR-06] (routes stay within the driver's shift), the tighter limit holds. Priority: Should. |  |  |

### Solving, dropping and validation

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-07 | **Single assignment.** Within a plan, every order is either served by exactly one current stop or reported as dropped, never both and never twice. Earlier failed stops stay as history ([FR-258]: a put-back order gets a new stop). |  |  |
| BR-08 | **Dropping.** An order is dropped only when not every order can be served. |  |  |
| BR-53 | **Priority scale.** Order priority is a whole number from 1, the highest, to 5, and a row without one gets 3. |  |  |
| BR-54 | **Priority in dropping.** A drop's penalty is proportional to 6 minus the order's priority, so solves prefer to drop lower priorities; several low-priority orders can outweigh one high-priority order. |  |  |
| BR-126 | **Drop unit.** A drop costs K(6 − priority), K being 5L + 2T + F + 1 seconds: L the larger of the date's longest shift and the longest a leave-at-door stop can spend outside its window during a shift, T the largest entry of its unbuffered per-bucket matrix at the highest factor, F the fixed vehicle cost; in a re-optimization, L also covers the longest a stop can be served late within a shift, and the rate is the larger of the door-window and lateness rates ([ADR-105]: re-optimization conventions). ([ADR-049]: drop penalty unit, scaled by priority; [ADR-100]: K's door-window bound and the no-vehicle reason) |  |  |
| BR-55 | **Fewer drops.** Equal penalties make a solve prefer fewer drops among orders of one priority, as best effort, and each penalty is large enough that no order drops just to save travel ([BR-08]: drop only when not every order fits). |  |  |
| BR-09 | **Objective.** A solve minimizes total travel time plus drop penalties, plus the window penalty of [BR-120] (leave-at-door window penalty), plus the fixed vehicle cost when enabled ([FR-21]: fixed cost per vehicle used). |  |  |
| BR-56 | **Re-optimization objective.** A re-optimization minimizes the objective of [BR-09] (the solve objective) plus a route-stability penalty ([FR-37]: route stability in re-optimization) and a lateness penalty ([BR-107]: re-optimization may keep late stops), as [BR-115] (the re-optimization penalties) sets them. |  |  |
| BR-115 | **Re-optimization penalties.** Each stop that leaves its vehicle costs 5 minutes of travel time, and each late minute costs 5 minutes; both are starting values, tuned on a separate seeded set and fixed before the study's measured runs. ([ADR-029](../ADRs/ADR-029-solver-objective-and-re-optimization-rules.md): solver objective and re-optimization rules) Priority: Must. |  |  |
| BR-120 | **Window penalty.** A leave-at-door stop served outside its window adds 5 times the time outside it to the objective, in solves and re-optimizations, replacing the lateness penalty of [BR-115] (the re-optimization penalties); 5 is a starting value, tuned with it. Priority: Must. |  |  |
| BR-121 | **No window drops.** The common drop-penalty scale of [BR-54] (drop penalty scales with priority) is set so that every leave-at-door order's drop penalty exceeds the largest window penalty ([BR-120]: leave-at-door window penalty) a leave-at-door stop on its date can incur, so *time window* is never its drop reason ([FR-223]: the drop reasons). Priority: Must. |  |  |
| BR-15 | **Validated solutions only.** No solution, from either solver or from a re-optimization, is stored as a plan or shown as feasible unless the independent feasibility validator accepts it. |  |  |
| BR-57 | **Rejected solutions.** A solution the validator rejects fails its solve job and lists the violations. |  |  |
| BR-42 | **One definition of feasibility.** Manual-edit checks ([FR-233]: violations flagged after an edit), the publishing re-check ([BR-21]: re-check at publishing), solver acceptance ([BR-15]: only validator-accepted solutions count) and research scoring ([FR-279]: benchmarks scored by the shared objective, [FR-281]: what the simulation measures) use the same validator, so no solver's own check is the final word on its solution. Priority: Must. |  |  |
| BR-58 | **Research objective.** Research scoring uses the shared objective ([BR-09]: the solve objective, [BR-56]: the re-optimization objective). Priority: Must. |  |  |

### Plan lifecycle and publishing

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-12 | **Plan lifecycle.** A plan moves `draft → solving → ready → published → completed`, and makes no other transition except those of [BR-59] (a failed solve reverts the plan), [BR-60] (a ready plan may be solved again), [BR-103] (a cancelled plan is final) and [BR-127] (an unpublished plan is abandoned at midnight). |  |  |
| BR-59 | **Failed solve.** A failed solve returns a plan to `draft`, or to `ready` if it was solved before. |  |  |
| BR-60 | **Solving again.** A `ready` plan may be solved again. |  |  |
| BR-61 | **Manual edits.** Manual edits keep a plan `ready`. |  |  |
| BR-62 | **Accepted re-optimization.** An accepted re-optimization keeps a plan `published` and changes it in place. |  |  |
| BR-102 | **Plan completion.** A published plan becomes `completed` at midnight, Israel time, at the end of its date, and late reports are still accepted ([BR-27]: late outcomes are still accepted). Priority: Must. |  |  |
| BR-127 | **Abandoned plan.** At midnight, Israel time, at the end of its date, a plan that is `draft`, `solving` or `ready` becomes `abandoned`, which is terminal and read-only. Priority: Must. |  |  |
| BR-128 | **Solve jobs at midnight.** At midnight after a date, every queued or running solve job of that date fails with the reason "its date passed", before its plan completes or becomes `abandoned` ([BR-127]: an unpublished plan is abandoned at midnight). Priority: Must. |  |  |
| BR-103 | **Cancelled plan.** A published plan may become `cancelled` ([FR-336]: cancel a published plan) and then makes no further transition. Priority: Should. |  |  |
| BR-118 | **No past plans.** A plan for a past date cannot be created or published. Priority: Must. |  | `OpenPlanTest`, `PlanControllerIT` (creating a plan) |
| BR-16 | **Edits are flagged, never silently fixed.** A manual edit that breaks any of [BR-02] to [BR-15] (driver scope through validated solutions), [BR-39] (one route per vehicle and per driver), [BR-40] (routes start and end at depots), [BR-46] (routes keep to depot hours), [BR-51] (re-optimized routes start where the vehicle is), [BR-52] (a withdrawn route ends at its last reported stop), [BR-79] (re-optimization starts from current locations) or [BR-105] (time reserved for breaks) is kept as the dispatcher made it and flagged with every violation. |  |  |
| BR-17 | **No publishing with violations.** A plan with any flagged violation cannot be published; the dispatcher fixes it or solves again first. |  |  |
| BR-20 | **Published as solved.** A plan is published only while, for every route, the vehicle is not marked unavailable and the vehicle's driver holds the Driver role and is the route's driver. |  |  |
| BR-63 | **Changed vehicles.** When a vehicle no longer meets [BR-20] (routes publish only with their solved vehicle and driver), nothing is published, every changed vehicle is named, and the dispatcher solves again. |  |  |
| BR-64 | **Check at acceptance.** Accepting a re-optimization applies [BR-20] (routes publish only with their solved vehicle and driver) to every vehicle the proposal keeps driving, skipping vehicles withdrawn mid-route. ([ADR-055]: acceptance checks the vehicles) |  |  |
| BR-65 | **Changed vehicles at acceptance.** When a vehicle no longer meets [BR-20] (routes publish only with their solved vehicle and driver), accepting a re-optimization is refused, every changed vehicle is named, and the dispatcher re-optimizes again. |  |  |
| BR-21 | **Re-check at publishing.** Before a ready plan is published, the shared validator re-checks every route once against the current fleet and orders of its date: shifts, capacities, depots and time windows. |  |  |
| BR-66 | **Vehicle limits at publishing.** Where [FR-133] (per-vehicle route limits) is built, the re-check at publishing also covers vehicle limits. |  |  |
| BR-67 | **Broken rule.** If a route breaks a rule in the re-check at publishing, nothing is published, every broken rule is listed, and the dispatcher solves again or edits the route. |  |  |
| BR-68 | **Re-checked times.** When the re-check at publishing finds nothing broken, the re-checked times are published: they equal the solved times unless the fleet changed since the solve, and then they follow it. |  |  |
| BR-69 | **Plans without routes.** A plan without routes needs no re-check at publishing. |  |  |
| BR-70 | **Re-check cannot run.** If the re-check at publishing cannot run, nothing is published. |  |  |
| BR-22 | **Every order accounted for.** A plan is published only when it serves or drops every order of its date. |  |  |
| BR-71 | **Late orders.** An order placed after the solve was requested refuses publishing: nothing is published, every such order is named, and the dispatcher solves again. |  |  |
| BR-72 | **Order of refusals.** Publishing refusals fire in this order: changed vehicles ([BR-20]: routes publish only with their solved vehicle and driver), an order the plan does not account for ([BR-22]: every order served or dropped before publishing), flagged violations ([BR-17]: no publishing with violations; refused without a re-check), then the re-check ([BR-21]: re-check at publishing). |  |  |
| BR-73 | **Orders after publishing.** Orders placed after publishing enter through re-optimization ([FR-11]: add orders to a published day). |  |  |
| BR-32 | **No plan stays `solving`.** A solve that has started and has not returned within 30 seconds past its limit fails, and the failed-solve transition of [BR-59] (a failed solve reverts the plan) then applies. ([ADR-051]: solve job timeout) |  |  |
| BR-74 | **Solve interrupted.** A solve running when a restart interrupts it fails. |  |  |
| BR-75 | **Solve limit start.** The limit of a solve runs from the start of its search, after the travel-time matrix is built, and a queued solve still waits its turn ([NFR-12]: queued solves survive restarts). |  |  |
| BR-112 | **Queue limits.** A queued solve or re-optimization waits at most 5 minutes and then fails, and a request beyond 5 queued is refused. Priority: Must. |  | `RequestSolveTest`, `PlanControllerIT`, `SolveJobPersistenceIT` (the queue limit of 5) |
| BR-34 | **One plan per date.** The company has at most one plan for a date, not counting cancelled ones. |  | `PlanPersistenceIT.GivenADatesPlan_WhenAnotherIsAddedPastTheRead_ThenPlanAlreadyExistsNamesTheFirst`, `OpenPlanTest`, `PlanControllerIT` |
| BR-38 | **One change at a time.** A plan has at most one re-optimization running at a time; a second request is refused with its reason, and an open preview does not count. ([ADR-057]: concurrent changes to a plan) |  |  |
| BR-76 | **No silent overwrite.** Two dispatchers cannot silently overwrite each other's changes to a plan: an out-of-date change is refused with its reason. |  | `RequestSolveTest`, `PlanControllerIT` (requesting a solve) |
| BR-47 | **Named actions only.** A published route changes only through an accepted re-optimization ([FR-255]: nothing changes until accepted), a hand-over ([FR-55]: hand a route to another driver), a cancelled order or date change ([FR-58]: cancel an order, [FR-315]: date change on a published plan), a cancelled plan ([FR-336]: cancel a published plan), a driver's start, finish or report ([FR-68]: start and finish a route, [FR-29]: report a stop's status, [BR-27]: late outcomes are still accepted), or an outcome recorded or corrected for a driver ([FR-73]: dispatcher records an outcome, [FR-333]: dispatcher corrects an outcome). Priority: Must. |  |  |
| BR-77 | **Audited changes.** Each change to a published route is audited ([NFR-23]: audit records). Priority: Must. |  |  |
| BR-78 | **No silent changes.** No background process changes a published route silently. Priority: Must. |  |  |

### Re-optimization

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-10 | **Locked stops.** Re-optimization never moves a completed or in-progress stop. A stop is in progress once its driver marks it *arrived*. |  |  |
| BR-79 | **Re-optimization start point.** In a re-optimization, each vehicle's current location becomes its new starting point. |  |  |
| BR-106 | **Goods stay on their vehicle.** A re-optimization moves a stop off its vehicle only if that vehicle is unavailable, without a driver, out of shift or finished, or the stop is new; other stops only reorder or drop. ([ADR-029](../ADRs/ADR-029-solver-objective-and-re-optimization-rules.md): solver objective and re-optimization rules) Priority: Must. |  |  |
| BR-117 | **Stops on no vehicle.** Under [BR-106] (goods stay on their vehicle), a dropped order or one moved to the date counts as new, and a failed stop put back stays with its vehicle. Priority: Must. |  |  |
| BR-107 | **Late stops.** A re-optimization may keep a stop past its window end, within its driver's shift and any break reserve ([BR-105]: time reserved for breaks), flagged in the preview ([FR-103]: preview shows drops and broken windows); a flagged stop does not refuse acceptance, and a solve stays bound by [BR-03] (service starts inside the time window). ([ADR-029](../ADRs/ADR-029-solver-objective-and-re-optimization-rules.md): solver objective and re-optimization rules) Priority: Must. |  |  |
| BR-108 | **Finished routes.** A re-optimization adds no stop to a finished route, and a route withdrawn mid-route ([FR-267]: vehicle withdrawn mid-route) counts as finished. Priority: Must. |  |  |
| BR-23 | **Facts are not violations.** When a re-optimization is validated ([BR-15]: only validator-accepted solutions count), [BR-03] (service starts inside the time window) as [BR-107] (re-optimization may keep late stops) relaxes it, [BR-04] (service time at each stop), [BR-06] (routes stay within the driver's shift), [BR-46] (routes keep to depot hours) and the vehicle limits of [FR-133] (per-vehicle route limits) apply only to what has not happened yet, so a locked stop that ran late never fails the solution. ([ADR-050]: validator and re-optimization problem conventions) |  |  |
| BR-24 | **Start point without a usable position.** If a vehicle has no usable position, re-optimization starts it from the last stop it reported, or from its start depot if it reported none. ([ADR-050]: validator and re-optimization problem conventions) |  |  |
| BR-80 | **Usable position.** A position is not usable when it is older than 5 minutes, outside the service area, or implies a speed above 200 km/h from the position before it. |  |  |
| BR-81 | **Preview names the vehicle.** The preview names each vehicle that has no usable position. |  |  |
| BR-25 | **Accepting re-checks recorded outcomes.** Accepting a re-optimization re-checks it against every stop status, cancellation, order change, hand-over and fleet change recorded since the preview. ([ADR-056]: acceptance checks recorded changes) |  |  |
| BR-82 | **Changed since the preview.** If the proposal would move or drop a stop that changed, or relies on an order, vehicle or driver that changed, nothing is applied, what changed is named, and the dispatcher re-optimizes. |  |  |
| BR-83 | **Reports that stand.** A report on a stop the proposal leaves on the same vehicle and in the same order does not refuse the proposal, and the report stands. |  |  |
| BR-26 | **A failed re-optimization changes nothing.** A re-optimization that fails, is rejected by the validator or is declined leaves the plan `published` and every route as it was. |  |  |
| BR-84 | **Re-optimization timeout.** A re-optimization has a limit of 20 seconds from the start of its search, and has failed if it has not returned within 30 seconds past it. |  |  |
| BR-85 | **Re-optimization interrupted.** A re-optimization that a restart interrupts, or finds queued, has failed. |  |  |
| BR-86 | **Failure reason shown.** The dispatcher is told why a re-optimization failed or was rejected. |  |  |

### Driver reports and outcomes

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-11 | **Failure reason.** A stop cannot be marked *failed* without a reason. |  |  |
| BR-109 | **Failure reasons.** A failure reason is customer absent, wrong address, refused, no access, damaged, or other with text. The list is fixed in the first release. Priority: Must. |  |  |
| BR-18 | **Outcomes are final for drivers.** Once *delivered* or *failed* is recorded, a driver cannot change it, and a dispatcher can correct it ([FR-333]: dispatcher corrects an outcome). |  |  |
| BR-87 | **Delivery states.** A delivery moves `pending → arrived → delivered` or `failed`, and may skip *arrived*. |  |  |
| BR-27 | **Outcomes outlive route changes.** Except as [BR-88] (when a late outcome is refused) and [BR-132] (backdated report held) say, an outcome a driver recorded while the stop was on their route is accepted however late it arrives, even if the stop has moved since (an exception to [BR-02]: drivers see and update only their own stops). ([ADR-087]: driver report sync and idempotency) |  |  |
| BR-88 | **Late outcome refused.** A late outcome is not accepted if the stop already has a recorded outcome, or if its corrected time is after its order or plan was cancelled; [BR-90] (contradicting reports are refused) applies instead. |  |  |
| BR-89 | **Stop leaves the other route.** When a late outcome is accepted for a stop that has moved, the stop leaves the other driver's route, that driver is told, and the dispatcher is alerted. |  |  |
| BR-110 | **Judging a late report.** Whether a stop was on the driver's route is judged from the route's history at the report's corrected time ([BR-114]: report times corrected for the device's clock); a corrected time later than the server's receipt time is cut to the receipt time. Priority: Must. |  |  |
| BR-28 | **Repeated reports are harmless.** A report received twice takes effect once, and the repeat gets the first answer. ([ADR-087]: driver report sync and idempotency) |  |  |
| BR-90 | **Contradicting reports.** A report contradicting a recorded outcome, or timed after its order or plan was cancelled, is refused and shown to the dispatcher. |  |  |
| BR-130 | **Served after cancellation.** A late outcome whose corrected time is before its order or plan was cancelled is accepted as *served after cancellation*, and the dispatcher is alerted, unless [BR-132] (backdated report held) holds it ([ADR-065]: facts outrank intent). |  |  |
| BR-132 | **Backdated report.** A late outcome that would take a stop off another driver's route, or override a cancellation, is held for the dispatcher to confirm or refuse if the reporting account made contact in the window of [BR-133] (contact window for a held report). ([BR-110]: late reports judged by route history) |  |  |
| BR-133 | **Contact window.** Contact is any request from any session of the reporting account other than a token refresh or an outbox send, made from 2 minutes after the report's corrected time to 2 minutes before the report arrived. |  |  |
| BR-134 | **Held report overtaken.** While a report is held, an outcome recorded on the stop's current route wins over it. |  |  |
| BR-135 | **Held report refused.** A held report that the dispatcher refuses is treated as [BR-90] (contradicting reports are refused) treats a contradicting report: refused and shown to the dispatcher. |  |  |
| BR-136 | **Further late report held.** A further late report for a stop that already has a held report is held too ([BR-132]: backdated report held), except one from the same driver and route, which is refused: silently when identical, as a contradiction otherwise. |  |  |
| BR-29 | **Stops may be done out of order.** A driver can report a route's stops in a different order from the plan, and the system records the actual order. |  |  |
| BR-91 | **Out-of-order stops in re-optimization.** Re-optimization treats stops reported out of order as done in that order. The start point stays as [BR-79] (re-optimization starts from current locations) and [BR-24] (fallback start without a usable position) set it. |  |  |
| BR-44 | **Two times on every report.** Every driver report keeps the time the device recorded and the time the server received it. Priority: Must. |  |  |
| BR-114 | **Clock correction.** A report's time is corrected by the clock offset of the device that made it, measured when the report is sent. Priority: Must. |  |  |

### Orders, records and history

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-13 | **Corrected locations win.** A location a dispatcher corrected by hand overrides any geocoding result for that customer. |  |  |
| BR-104 | **Address change.** Changing a customer's address clears a hand-corrected pin and geocodes again, after warning the dispatcher. Priority: Must. |  |  |
| BR-30 | **Cancelled orders.** A cancelled order is neither served nor dropped: [BR-07] (each order served once or dropped), [BR-22] (every order served or dropped before publishing), on-time figures and the baseline no longer count it. |  |  |
| BR-92 | **No change after arrival.** An order cannot be changed or cancelled once its current stop is marked *arrived* or has an outcome. |  |  |
| BR-33 | **Order states.** An order is *unplanned*, *planned*, *dropped*, *delivered*, *failed* or *cancelled*. An order that fits several takes the first of *delivered* or *failed*, then *cancelled*, then *planned* or *dropped*, then *unplanned*, and a cancellation is still shown beside an outcome. |  |  |
| BR-129 | **Unreported at midnight.** When its plan completes ([BR-102]: plans complete at midnight), an order whose stop has no report stays *planned*, and the daily summary shows the stop as *not reported* until a late report ([BR-27]: late outcomes are still accepted) or a dispatcher's record ([FR-73]: dispatcher records an outcome) settles it. |  |  |
| BR-131 | **Orders of an abandoned plan.** An order whose current attempt is on an `abandoned` plan is *unplanned* ([BR-127]: an unpublished plan is abandoned at midnight). Priority: Must. |  |  |
| BR-93 | **Order state changes.** An order's state changes only through the actions that [FR-17] (what a solve produces), [FR-18] (dropped orders listed with reasons), [FR-29] (report a stop's status), [FR-36] (re-optimization preview), [FR-57] (change an order), [FR-58] (cancel an order), [FR-73] (dispatcher records an outcome), [FR-75] (put a failed or dropped order back), [FR-145] (undo and redo edits), [FR-146] (take a stop off by hand), [FR-235] (put a dropped order on a route by hand), [FR-333] (dispatcher corrects an outcome) and [FR-337] (what cancelling a plan does) define, and [BR-30] (cancelled orders are neither served nor dropped) still applies. [BR-18] (outcomes are final for drivers) governs a stop's outcome. |  |  |
| BR-94 | **Leaving *failed*.** An order leaves *failed* only through [FR-75] (put a failed or dropped order back) or a correction ([FR-333]: dispatcher corrects an outcome). |  |  |
| BR-95 | **Leaving *dropped*.** An order leaves *dropped* through [FR-75] (put a failed or dropped order back), when a ready plan is solved again ([BR-60]: a ready plan may be solved again), when its date changes ([FR-57]: change an order) or when it is cancelled ([FR-58]: cancel an order). |  |  |
| BR-96 | **Leaving *dropped*, continued.** An order also leaves *dropped* when a late report is accepted ([BR-27]: late outcomes are still accepted), when a dispatcher records its outcome ([FR-73]: dispatcher records an outcome), puts it on a route by hand ([FR-235]: put a dropped order on a route by hand) or undoes the edit that dropped it ([FR-145]: undo and redo edits), and when its plan is cancelled ([FR-337]: what cancelling a plan does). |  |  |
| BR-35 | **Valid order data.** An order's time window starts before it ends and lies on the order's date, and its service duration is positive. (For windows that cross midnight, see [FR-114]: shifts and windows crossing midnight.) |  |  |
| BR-124 | **Blank service duration.** An order with no service duration takes its customer's default ([FR-89]: default service duration per customer), and is refused with the reason when there is none. |  |  |
| BR-97 | **Invalid orders refused.** An order with invalid data is refused with its reason. |  |  |
| BR-116 | **No past dates.** An order placed, moved or put back on a past date is refused with the reason. Priority: Must. |  |  |
| BR-37 | **History stays.** A plan that is completed, or whose date has passed, keeps the values it was solved and reported with, after the fleet or a customer changes. ([ADR-052]: past plans keep their values) |  |  |
| BR-43 | **Retire, don't delete.** A depot, vehicle, driver, customer or order that any plan refers to is retired ([FR-56]: maintain and retire records) or cancelled ([FR-58]: cancel an order), never deleted. Priority: Must. |  |  |
| BR-98 | **Personal data removal.** Only a personal-data request ([NFR-49]: personal-data requests) or a retention period ([NFR-31]: retention periods) removes personal fields, which include a customer's coordinates, and the non-personal figures stay. Priority: Must. |  |  |
| BR-99 | **Company exit.** When the company stops using DispatchFlow, its data is deleted as [NFR-150] (export then deletion when the company leaves) says, an exception to [BR-43] (retire instead of delete) and [BR-98] (only a request or retention removes personal data). Priority: Must. |  |  |

### Time and limits

| ID | Rule | Size | Enforced by |
|---|---|---|---|
| BR-31 | **Israel local time.** Every date, time window, shift and ETA is Israel local time, on clock-change days too. ([ADR-088]: Israel local time) |  |  |
| BR-100 | **Plan date.** A plan's date is the local calendar date. |  |  |
| BR-111 | **Clock changes.** A time in the hour the clock skips is refused, and a time in the hour it repeats reads as its first occurrence. Priority: Must. |  |  |
| BR-36 | **Vehicle limit.** Adding a sixteenth vehicle is refused, and the message names the limit ([NFR-04]: fleet size limit). ([ADR-060]: hard limits on fleet and plan size) |  |  |
| BR-101 | **Order limit.** An order beyond the limit of [NFR-05] (plan size limit) for its date is refused, and the message names the limit; research instances are exempt, as [FR-390] (benchmark demands read as weights) exempts them from [BR-50] (capacity and demand values). ([ADR-060]: hard limits on fleet and plan size) |  |  |
| BR-113 | **Order limit count.** The limit counts a date's uncancelled orders, failed ones included, and a put-back does not count again. It is checked whenever an order joins a date: import, entry, copy, put-back or date change. Priority: Must. |  |  |

## Non-functional requirements

### Security and access

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-01"></a>NFR-01 | Every API call requires a valid access token, except signing in, refreshing, signing out, health checks, the calls [FR-52] (first credentials, recovery and email confirmation) and [FR-263] (tracking page through an unguessable link) name, and the calls of the SMS provider that [NFR-192] (SMS provider callbacks) accepts. |  |  |
| <a id="nfr-85"></a>NFR-85 | Roles are checked on the server for every operation and for every event the event stream sends. |  |  |
| <a id="nfr-86"></a>NFR-86 | An access token is accepted for 15 minutes by default, unless [NFR-177] (revoked tokens refused at once) refuses it. |  |  |
| <a id="nfr-177"></a>NFR-177 | The server refuses an access token issued before its user's last revocation. The user's other sessions refresh and carry on. ([ADR-036](../ADRs/ADR-036-one-account-per-person-signed-in-by-email.md): one account per person, signed in by email) Priority: Must. |  |  |
| <a id="nfr-188"></a>NFR-188 | An open event stream ends when its access token expires or its user's sessions are revoked, and the web app reconnects with a fresh token. Priority: Must. |  |  |
| <a id="nfr-267"></a>NFR-267 | An open event stream re-checks its session at each heartbeat, every 15 seconds, and closes once the session is revoked, which is how [NFR-188] (event stream ends with its token or session) holds for revocation. Priority: Must. |  |  |
| <a id="nfr-03"></a>NFR-03 | Secrets (provider API keys, token-signing keys, database credentials, the email sender's and the push service's credentials) come from the environment or a secret manager, never from the repository, logs or error messages. |  |  |
| <a id="nfr-286"></a>NFR-286 | The core connects to the database with an account the migrations grant only what its features use, which can't change the schema, and refuses to start as a superuser, an owner, a member of another role, or a login that switched role. ([ADR-145]: the migrate step as the owner, the core as app) Priority: Must. |  | `DatabaseRolesIT` (the grants), `DatabaseSessionGuardIT` (the refusals) |
| <a id="nfr-228"></a>NFR-228 | When a team member leaves, their server access is removed that day and every secret they held is rotated. |  |  |
| <a id="nfr-22"></a>NFR-22 | A signed-in user stays signed in through refresh tokens. Each is exchanged once, for a new access token and the session's next refresh token. |  |  |
| <a id="nfr-87"></a>NFR-87 | A refresh token lasts 14 days by default from its issue. |  |  |
| <a id="nfr-88"></a>NFR-88 | Presenting a refresh token that was already exchanged revokes its whole session, except as [NFR-178] (grace for a repeated refresh) says; a token the session no longer keeps is refused as unknown and revokes nothing ([ADR-104]: the company profile and role-matrix capabilities). |  |  |
| <a id="nfr-178"></a>NFR-178 | While its session is live, a refresh token presented again within 30 seconds of its exchange gets a new token, which replaces the one that exchange issued, and revokes nothing. Presenting a replaced token revokes the session. ([ADR-036](../ADRs/ADR-036-one-account-per-person-signed-in-by-email.md): one account per person, signed in by email) Priority: Must. |  |  |
| <a id="nfr-89"></a>NFR-89 | A sign-out revokes the session it ends. |  |  |
| <a id="nfr-90"></a>NFR-90 | A role, password or email change revokes every session of the user. |  |  |
| <a id="nfr-214"></a>NFR-214 | The server refuses every operation the permission matrix gives only to Admins unless that session gave the current password in the last 5 minutes; both apps ask for it. Priority: Must. |  |  |
| <a id="nfr-227"></a>NFR-227 | Giving the password for [NFR-214] (password before Admin operations) issues the session new tokens, and the old ones are refused ([NFR-177]: revoked tokens refused at once). Priority: Must. |  |  |
| <a id="nfr-91"></a>NFR-91 | Only a refresh token's hash is stored. |  |  |
| <a id="nfr-211"></a>NFR-211 | A recovery, set-password or email-change link carries a token of at least 128 bits from a secure random source; only its hash is stored, and it never appears in a log. Priority: Must. |  |  |
| <a id="nfr-212"></a>NFR-212 | The token travels in the link's fragment; the page posts it, sends no referrer, caches nothing, and removes the token from the address bar and history as it loads. Priority: Must. |  |  |
| <a id="nfr-213"></a>NFR-213 | A recovery, set-password or email-change link opens the web app over HTTPS, or the phone app only through verified app links: Universal Links on iOS, App Links on Android. Priority: Must. |  |  |
| <a id="nfr-223"></a>NFR-223 | Emails that carry a link token have link and open tracking turned off. Priority: Must. |  |  |
| <a id="nfr-224"></a>NFR-224 | Only setting the password, or confirming the email, spends a token; a newer link of a kind voids older ones of that kind, and an email or password change, a deactivation or ending a user's sessions voids every unspent link. Priority: Must. |  |  |
| <a id="nfr-92"></a>NFR-92 | Every refusal of a refresh token looks the same. |  |  |
| <a id="nfr-23"></a>NFR-23 | Every API call that changes something, and every sign-in, refresh and sign-out, leaves one audit record. Reads are not audited. |  |  |
| <a id="nfr-229"></a>NFR-229 | AWS Systems Manager (SSM) records who opened each session on the VM and when (CloudTrail), and records no session stream, so the reads made inside a session are not audited. |  |  |
| <a id="nfr-265"></a>NFR-265 | Every human account that can deploy or release the app, at AWS, GitHub, Expo, Apple, Google, Mend and the domain's registrar, requires a second sign-in factor. Priority: Must. |  |  |
| <a id="nfr-93"></a>NFR-93 | An audit record holds when, the acting user and session when known, the app and its version as the client declares them, the operation, the records it names, its outcome and its trace id. |  | `AuditRecordTest`, `AuditLogServiceTest`, `AuditRecordsPersistenceIT`; the apps' side: `@dispatchflow/api-client`'s `client.test.ts` |
| <a id="nfr-94"></a>NFR-94 | An audit record never carries personal data or a secret ([NFR-19]: no personal data in logs, [NFR-03]: secrets kept out of the repository and logs). |  | `AuditRecordTest`, `AuditLogServiceTest` |
| <a id="nfr-26"></a>NFR-26 | In every deployed environment, all traffic between clients and the server is encrypted in transit, and an unencrypted request is refused or redirected, never served. ([ADR-069]: sign-in and transport security) |  |  |
| <a id="nfr-35"></a>NFR-35 | Failed sign-ins for one typed address from one source address are limited to 10 in a rolling 15 minutes; past that, that pair is refused until the window clears, and a success resets the count. ([ADR-036](../ADRs/ADR-036-one-account-per-person-signed-in-by-email.md): one account per person, signed in by email) |  |  |
| <a id="nfr-215"></a>NFR-215 | At most 100 recovery emails ([FR-386]: password recovery by email) are sent per calendar day in Israel time; other emails are never held back, and the team is alerted once a day when the cap is reached. Priority: Must. |  |  |
| <a id="nfr-216"></a>NFR-216 | Past 10 failed sign-ins for one typed address in a rolling 15 minutes, from any source, only one attempt on it is checked every 5 seconds; others are refused with when to retry. Priority: Must. |  |  |
| <a id="nfr-226"></a>NFR-226 | A success resets the count of [NFR-216] (per-address sign-in limit), and neither it nor [NFR-35] (sign-in attempt limit) ever locks an account. Priority: Must. |  |  |
| <a id="nfr-36"></a>NFR-36 | Passwords are stored only as salted hashes from a function built for passwords, never in a form that can be read back. ([ADR-069]: sign-in and transport security) |  |  |
| <a id="nfr-95"></a>NFR-95 | Passwords have at least 8 characters, with no composition rules, and the 3,000 most common passwords of at least 8 characters are refused. |  |  |
| <a id="nfr-37"></a>NFR-37 | Uploads are limited, and one over a limit is refused with the reason: CSV and Excel files to 5 MB, and delivery photos and signatures, in JPEG or PNG, to 5 MB. |  |  |
| <a id="nfr-96"></a>NFR-96 | An uploaded compressed file over 50 MB unpacked is refused with the reason. |  |  |
| <a id="nfr-97"></a>NFR-97 | An uploaded workbook's external references are never followed. |  |  |
| <a id="nfr-38"></a>NFR-38 | Proof-of-delivery files are served only to signed-in users whose roles permit it (the [permission matrix](#company-users-and-roles)). |  |  |
| <a id="nfr-39"></a>NFR-39 | Tokens, the route data kept for offline use ([FR-69]: route shown offline), and queued reports with their proofs are stored encrypted on the phone, with the key in the phone's secure storage. ([ADR-069]: sign-in and transport security) |  |  |
| <a id="nfr-230"></a>NFR-230 | Everything the phone app or the web app receives through the Admin or Dispatcher role stays in memory, caches included, and is never written to storage. |  |  |
| <a id="nfr-231"></a>NFR-231 | The phone app lets an export leave the device only through the share sheet. |  |  |
| <a id="nfr-98"></a>NFR-98 | Tokens and the route data kept for offline use are removed from the phone when the user signs out. |  |  |
| <a id="nfr-232"></a>NFR-232 | At sign-out the phone app wipes everything the account received through the Admin or Dispatcher role. |  |  |
| <a id="nfr-233"></a>NFR-233 | At sign-out the web app clears its browser storage, driver screens included, sends Clear-Site-Data and deletes the refresh cookie. |  |  |
| <a id="nfr-179"></a>NFR-179 | When a session is revoked, the route data on the phone is wiped at its next server contact, or after 14 days on a phone that stays offline. Priority: Must. |  |  |
| <a id="nfr-234"></a>NFR-234 | When the server refuses a phone's refresh, the phone wipes its route data and asks the driver to sign in again, and it keeps the queued reports and proofs. Priority: Must. |  |  |
| <a id="nfr-235"></a>NFR-235 | A phone offline for 14 days wipes its queued reports and proofs along with its route data. Priority: Must. |  |  |
| <a id="nfr-236"></a>NFR-236 | When a session is revoked, the phone also wipes everything the account received through the Admin or Dispatcher role, at its next server contact. Priority: Must. |  |  |
| <a id="nfr-269"></a>NFR-269 | In the web app, a refused refresh or an ended session drops the data held in memory, the query cache included. Priority: Must. |  |  |
| <a id="nfr-40"></a>NFR-40 | A self-check against Level 1 of the [OWASP Application Security Verification Standard (ASVS) 5.0](https://asvs.owasp.org/) passes before the pilot handles real data. ([ADR-070]: security and accessibility standards) |  |  |
| <a id="nfr-53"></a>NFR-53 | Every API that clients call is rate-limited to 120 calls a minute per user, beyond the sign-in limit of [NFR-35] (sign-in attempt limit). ([ADR-071]: API rate limiting) Priority: Should. |  |  |
| <a id="nfr-99"></a>NFR-99 | A request over a rate limit is refused, with when to retry. Priority: Should. |  |  |
| <a id="nfr-100"></a>NFR-100 | A rate-limit refusal does not lose a driver's queued reports ([FR-70]: report stops offline). Priority: Should. |  |  |
| <a id="nfr-101"></a>NFR-101 | A rate-limit refusal of a queued report is neither shown to the driver nor counted against the 60 seconds of [FR-246] (queued reports sent on reconnect). Priority: Should. |  |  |
| <a id="nfr-102"></a>NFR-102 | Calls without a token ([NFR-01]: access token on every call, [FR-52]: first credentials, recovery and email confirmation) are limited to 20 a minute per source address, and one over a limit is refused with when to retry. ([ADR-071]: API rate limiting) Priority: Must. |  |  |
| <a id="nfr-192"></a>NFR-192 | Calls from the SMS provider, replies and delivery reports alike, are accepted only with the provider's signature or a header secret, two while one is rotated, compared in constant time; any other call is refused and changes nothing. Priority: Must. |  |  |
| <a id="nfr-199"></a>NFR-199 | A provider call received twice, with the same provider message identifier, takes effect once. Priority: Must. |  |  |
| <a id="nfr-200"></a>NFR-200 | Calls with the provider's signature or secret are limited to 600 a minute in all, and calls without it as [NFR-102] (limit on calls without a token) says. Priority: Must. |  |  |
| <a id="nfr-201"></a>NFR-201 | A provider call is answered as received only after what it carries is stored, or discarded as [FR-379] (replies without a rating request) says, so the provider sends a failed one again. Priority: Must. |  |  |
| <a id="nfr-203"></a>NFR-203 | A comment is kept as plain text cut to 500 characters, shown escaped, and exported with spreadsheet formula characters neutralized ([FR-107]: export to a spreadsheet). Priority: Must. |  |  |
| <a id="nfr-206"></a>NFR-206 | Only the production deployment holds the SMS provider's credential; every other environment sends through a fake that reaches no phone. Priority: Must. |  |  |
| <a id="nfr-207"></a>NFR-207 | Generated and pseudonymized phones come from one stated range of Israeli mobile numbers, used only with the fake provider of [NFR-206] (SMS credential only in production). Priority: Must. |  |  |
| <a id="nfr-54"></a>NFR-54 | CI scans dependencies and container images for known vulnerabilities; [ADR-016](../ADRs/ADR-016-dependency-audit-with-osv-scanner.md) (dependency audit with OSV-Scanner) covers dependencies only. ([ADR-072]: image and secret scanning) Priority: Must. |  |  |
| <a id="nfr-103"></a>NFR-103 | CI scans the repository, with its full history, for committed secrets ([NFR-03]: secrets kept out of the repository and logs). ([ADR-072]: image and secret scanning) Priority: Must. |  |  |
| <a id="nfr-104"></a>NFR-104 | A known vulnerability fails the build unless it is recorded as accepted, with its reason and an expiry date. Priority: Must. |  |  |
| <a id="nfr-105"></a>NFR-105 | A detected secret always fails the build and is rotated, not only removed. Priority: Must. |  |  |
| <a id="nfr-55"></a>NFR-55 | Web responses set a Content Security Policy, HSTS ([NFR-26]: encryption in transit) and a frame-ancestors rule. Priority: Must. |  |  |
| <a id="nfr-106"></a>NFR-106 | Any cookie that carries a session is Secure, HttpOnly and SameSite=Lax or Strict. Priority: Must. |  |  |
| <a id="nfr-107"></a>NFR-107 | A change made with a session cookie needs anti-forgery protection. Priority: Must. |  |  |
| <a id="nfr-108"></a>NFR-108 | Cross-origin requests are accepted only from named origins. Priority: Must. |  |  |
| <a id="nfr-56"></a>NFR-56 | A request that breaks its OpenAPI contract ([NFR-21]: versioned OpenAPI contracts) is refused with the reason before anything acts on it. ([ADR-073]: runtime contract validation) Priority: Should. |  |  |

### Performance and scale

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-04"></a>NFR-04 | Fleet size: up to 15 vehicles. |  |  |
| <a id="nfr-05"></a>NFR-05 | Plan size: up to 200 stops per plan. |  |  |
| <a id="nfr-06"></a>NFR-06 | A daily solve finishes within its configured time limit, settable between 5 and 60 seconds; the limit covers the search, not building the travel-time matrix. |  |  |
| <a id="nfr-07"></a>NFR-07 | A re-optimization returns its preview within 30 seconds at the 95th percentile, from the dispatcher's request to the preview on screen, measured under simulated disruptions at the scale of [NFR-04] (fleet size limit) and [NFR-05] (plan size limit). |  |  |
| <a id="nfr-08"></a>NFR-08 | Violation checks after a manual edit finish within 500 ms at the 95th percentile, from the edit to the flags on screen. |  |  |
| <a id="nfr-09"></a>NFR-09 | Stop-status and position updates reach the dispatcher's map within 5 seconds of reaching the server, at the 95th percentile. |  |  |
| <a id="nfr-180"></a>NFR-180 | Alerts and route changes reach an online reader within 30 seconds, and an offline phone within 60 seconds of reconnecting while the app is open, each at the 95th percentile. Priority: Must. |  |  |
| <a id="nfr-240"></a>NFR-240 | On phones, [NFR-180] (alert delivery time) is measured from the server's send to the alert on screen. Priority: Must. |  |  |
| <a id="nfr-10"></a>NFR-10 | The API is load-tested with k6: 15 phones and 5 web app sessions on a plan of 200 stops, with 30-second pings for 10 minutes and under 1% errors. |  |  |
| <a id="nfr-237"></a>NFR-237 | In the load test of [NFR-10] (API load test), each phone sends a position every 30 seconds, a report about every 2 minutes and reads its route on each push. |  |  |
| <a id="nfr-238"></a>NFR-238 | In that load test, each web session holds an open event stream and reloads the plan every minute, the streams running through k6's SSE extension. |  |  |
| <a id="nfr-239"></a>NFR-239 | In that load test, an error is a 5xx answer or an answer that takes over 2 seconds. |  |  |
| <a id="nfr-57"></a>NFR-57 | The travel-time matrix for a plan at the scale of [NFR-04] (fleet size limit) and [NFR-05] (plan size limit) is built within 5 seconds. Priority: Should. |  |  |
| <a id="nfr-181"></a>NFR-181 | [NFR-08] (edit-check latency), [NFR-09] (live update latency), [NFR-57] to [NFR-61] (matrix build through phone app opening times), [NFR-109] (map frame rate) and [NFR-180] (alert delivery time) are measured on the deployment VM, a mid-range laptop for the web app and a mid-range Android phone for the phone app ([NFR-241]: what mid-range means), at 200 stops and 15 routes, at the 95th percentile over at least 100 samples. Priority: Should. |  |  |
| <a id="nfr-241"></a>NFR-241 | A mid-range laptop has 4 cores, 8 GB of memory and integrated graphics and is under 4 years old; a mid-range Android phone is of the Galaxy A5x class and under 3 years old. Priority: Should. |  |  |
| <a id="nfr-58"></a>NFR-58 | The web app's first load finishes within 3 seconds on a throttled Fast 4G connection. Priority: Should. |  |  |
| <a id="nfr-109"></a>NFR-109 | Panning, zooming and selecting in either app on a plan of 200 stops and 15 routes holds 30 frames a second. Priority: Should. |  |  |
| <a id="nfr-59"></a>NFR-59 | Reads and writes that do not solve answer within 500 ms at the 95th percentile, per operation, under the load of [NFR-10] (API load test), except imports ([NFR-60]: import validation time) and publishing. Priority: Should. |  |  |
| <a id="nfr-60"></a>NFR-60 | An import of 200 order rows ([FR-10]: CSV order import) is validated and reported within 10 seconds, not counting the geocoding of new addresses. Priority: Could. |  |  |
| <a id="nfr-61"></a>NFR-61 | The phone app shows a driver's last received route within 3 seconds of opening, with or without a connection ([FR-69]: route shown offline). Priority: Should. |  |  |

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
| <a id="nfr-242"></a>NFR-242 | The 60-second limit of [NFR-24] (static quality target) and the 10 runs of [NFR-111] (static study runs) also bind the Gehring-Homberger and PyVRP runs. |  |  |
| <a id="nfr-112"></a>NFR-112 | The gap on an instance is (DispatchFlow − OR-Tools) / OR-Tools, and the average gap is the mean of the gaps over all instances that [NFR-114] (failed OR-Tools instances) does not leave out. |  |  |
| <a id="nfr-113"></a>NFR-113 | No failure is left out: the static-quality target is met only if every run of the DispatchFlow solver returns a solution the validator accepts ([BR-15]: only validator-accepted solutions count). |  |  |
| <a id="nfr-114"></a>NFR-114 | An instance on which an OR-Tools run fails or is rejected is reported and left out of the mean. |  |  |
| <a id="nfr-115"></a>NFR-115 | Every run of the static study records its seed, the iterations or solutions it reached and the solver versions, so that it can be replayed under that budget ([NFR-13]: reproducible solver). |  |  |
| <a id="nfr-25"></a>NFR-25 | **Incremental re-optimization.** Given a published plan at the scale of [NFR-04] (fleet size limit) and [NFR-05] (plan size limit) and one new order, both solvers get the same time limit of **5 seconds**: the DispatchFlow solver repairs the plan, and OR-Tools is warm-started from it. |  |  |
| <a id="nfr-116"></a>NFR-116 | Under the limit of [NFR-25] (incremental re-optimization study), the DispatchFlow solver returns a solution the validator accepts that serves the new order. |  |  |
| <a id="nfr-117"></a>NFR-117 | Over the study's insertions, the DispatchFlow solver's objective is on average no worse than OR-Tools', within a margin of 1% of OR-Tools' mean objective that the advisor confirms, both scored by the shared objective ([BR-09]: the solve objective, [BR-56]: the re-optimization objective). |  |  |
| <a id="nfr-118"></a>NFR-118 | Only insertions for which OR-Tools, solving the whole day from scratch under [BR-10] (completed and in-progress stops are locked) and [BR-106] (goods stay on their vehicle), serves the new order are counted. |  |  |
| <a id="nfr-243"></a>NFR-243 | The study counts the insertions that [NFR-118] (which insertions count) leaves out, and reports their number. |  |  |
| <a id="nfr-182"></a>NFR-182 | The study's insertions are 10 seeded new orders into each of 30 synthetic plans at full scale, 300 in all. |  |  |
| <a id="nfr-244"></a>NFR-244 | OR-Tools solves the 30 plans of [NFR-182] (the study's seeded insertions) at the 60-second limit, on seeded synthetic days. |  |  |
| <a id="nfr-270"></a>NFR-270 | The 30 solved plans are stored with the study's inputs, their hash is recorded in the run manifest ([NFR-62]: provenance of results), and a resumed or rerun study reads them rather than solving again. |  |  |
| <a id="nfr-245"></a>NFR-245 | Each insertion's time is drawn from the seed, across the plan's driving hours. |  |  |
| <a id="nfr-246"></a>NFR-246 | Each insertion is made on its own, into the same solved plan. |  |  |
| <a id="nfr-195"></a>NFR-195 | The plans of [NFR-182] (the study's seeded insertions) have no leave-at-door orders unless the study's configuration sets their share before the measured runs, and every result reports that share. Priority: Must. |  |  |
| <a id="nfr-119"></a>NFR-119 | For both solvers, the limit of [NFR-25] (incremental re-optimization study) covers receiving the problem to returning the solution, validation included, and holds at the 99th percentile inside the optimization service; the end-to-end promise remains [NFR-07] (re-optimization response time). |  |  |
| <a id="nfr-120"></a>NFR-120 | In the re-optimization study, both solvers are also run at limits of 1, 2, 10, 20 and 30 seconds, and the report gives each solver's objective at each limit. |  |  |
| <a id="nfr-62"></a>NFR-62 | **Provenance.** Every benchmark and simulation result is traceable to the commit, configuration file, seeds, hardware description and solver versions that produced it. Priority: Must. |  |  |
| <a id="nfr-121"></a>NFR-121 | The whole study reruns from one command, given any pilot data. Priority: Must. |  |  |
| <a id="nfr-247"></a>NFR-247 | A study that crashed resumes when it is run again, and the runner skips the results it already has. Priority: Must. |  |  |
| <a id="nfr-122"></a>NFR-122 | Results are kept beside the configuration that made them, except that instances and results derived from pilot data stay outside the repository ([NFR-50]: pilot data pseudonymized), which keeps only their hash and configuration. Priority: Must. |  |  |

### Product success targets

These measure whether the product worked for the company. They bind the pilot, not the research
track. Their values were confirmed on 2026-11-13; the 10% of [NFR-32] (pilot savings target) is
a judgment, not a measurement. The pilot's length and operations are still open ([Q-40]: pilot
protocol).

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-32"></a>NFR-32 | On at least **10** comparable pilot days ([NFR-248]: comparable pilot days), driven kilometres and driver-hours are at least **10%** below the company's own manual routes on those days. |  |  |
| <a id="nfr-248"></a>NFR-248 | A pilot day and a manual-route day are comparable when they fall on the same weekday and their order counts differ by at most 20%. |  |  |
| <a id="nfr-249"></a>NFR-249 | Pilot actuals come from the apps' report times and the position traces. |  |  |
| <a id="nfr-250"></a>NFR-250 | In a staged pilot ([FR-407]: staged pilot), [NFR-32] (pilot savings target) compares against the nearest-neighbour baseline ([FR-39]: nearest-neighbour baseline comparison) and the report says the pilot was staged; the other targets are measured as written. |  |  |
| <a id="nfr-123"></a>NFR-123 | Over the pilot days of [NFR-32] (pilot savings target), the on-time percentage is no lower than on the company's own manual routes on comparable days ([NFR-248]: comparable pilot days). |  |  |
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
| <a id="nfr-251"></a>NFR-251 | Every backup is encrypted, and its key is kept in the secret manager, readable by two named team members. |  |  |
| <a id="nfr-273"></a>NFR-273 | The backup secrets are under a customer-managed KMS key whose policy names the identities that may read them, the two named members of [NFR-251] (backup encryption) among them. |  |  |
| <a id="nfr-266"></a>NFR-266 | The VM cannot delete or corrupt backups, the old versions and the backup repository's key and configuration objects included. The bucket is versioned, with a 35-day Object Lock in governance mode. Priority: Must. |  |  |
| <a id="nfr-281"></a>NFR-281 | Old backup versions are kept at least as long as the lock period of [NFR-266] (backups the VM cannot delete). Priority: Must. |  |  |
| <a id="nfr-277"></a>NFR-277 | Only the break-glass role, which a team member assumes with a second sign-in factor, can bypass the backup lock or change the bucket's lock, lifecycle or policy; assuming it raises an alert. Priority: Must. |  |  |
| <a id="nfr-278"></a>NFR-278 | Backups are pruned on a schedule inside the AWS region in Israel (`il-central-1`), under a role of their own, never on a runner outside Israel. Priority: Must. |  |  |
| <a id="nfr-125"></a>NFR-125 | A restore of the database and proof files finishes within 4 hours. ([ADR-027](../ADRs/ADR-027-proof-files-on-the-vm-disk-with-hourly-backups.md): proof files on the VM disk) |  |  |
| <a id="nfr-252"></a>NFR-252 | A restore revokes every session, and messages to recipients stay off until an Admin turns them on again ([FR-350]: the messages switch). |  |  |
| <a id="nfr-272"></a>NFR-272 | A restore also rotates the access-token signing key and removes the old key from the accepted set at once, with no overlap of two keys; [ADR-069] (sign-in and transport security) allows the overlap only for an ordinary rotation. |  |  |
| <a id="nfr-253"></a>NFR-253 | The restore runbook re-applies, from records that carry only IDs and keyed hashes, the personal-data requests ([NFR-49]: personal-data requests), opt-outs ([FR-363]: opting out of messages), deactivations and role removals made since the restored backup was taken. |  |  |
| <a id="nfr-284"></a>NFR-284 | The records that [NFR-253] (restore runbook re-applies requests) re-applies are kept off the VM as each change happens. |  |  |
| <a id="nfr-274"></a>NFR-274 | For each account whose password or email changed since the backup, found in the logs by account ID, a restore blanks the password and voids its credential links. |  |  |
| <a id="nfr-282"></a>NFR-282 | The restore step itself issues set-password links ([FR-294]: set-password link) to the Admin accounts, at addresses the company confirms, as an audited step of [FR-393] (non-echoing prompt for identifiers). |  |  |
| <a id="nfr-283"></a>NFR-283 | Admins then confirm each account's state and roles with the company before any other set-password link goes out, to the address the company confirms. |  |  |
| <a id="nfr-275"></a>NFR-275 | A restore voids every open credential link. |  |  |
| <a id="nfr-276"></a>NFR-276 | If the backup is older than the logs reach, a restore gives every account the treatment of [NFR-274] (restore blanks changed passwords), then of [NFR-282] (restore issues links to Admins) and [NFR-283] (Admins confirm accounts before other links). |  |  |
| <a id="nfr-126"></a>NFR-126 | A restore is rehearsed once before the pilot. |  |  |
| <a id="nfr-29"></a>NFR-29 | During the pilot's operating hours the API is down at most 1% of the time, measured over each pilot month; that is about 8 minutes per 14-hour day on average. [Q-40] (pilot protocol) |  |  |
| <a id="nfr-127"></a>NFR-127 | Planned downtime falls outside the pilot's operating hours. |  |  |
| <a id="nfr-128"></a>NFR-128 | An outage alerts a named team member within 10 minutes of the first failure; down means 2 failed health checks in a row, probed every minute. [Q-40] (pilot protocol) |  |  |
| <a id="nfr-183"></a>NFR-183 | A restore after losing the VM does not count against the downtime of [NFR-29] (API availability). Priority: Must. |  |  |
| <a id="nfr-42"></a>NFR-42 | If the optimization service, the road-routing service or the geocoder is down, published routes, driver reports, hand-over ([FR-55]: hand a route to another driver) and cancellation ([FR-58]: cancel an order) keep working; what is checked once the optimization service returns is under [FR-395] (routes changed while the optimizer is down). |  |  |
| <a id="nfr-43"></a>NFR-43 | The system reports the date of the road data in use. |  |  |
| <a id="nfr-129"></a>NFR-129 | The road data in use is refreshed monthly, and the team is told when it is older than 60 days. |  |  |
| <a id="nfr-64"></a>NFR-64 | A report the server has acknowledged ([FR-248]: reports stay queued until acknowledged) is not lost when any one service crashes or restarts. Priority: Must. |  |  |
| <a id="nfr-65"></a>NFR-65 | A driver's report can be retried safely, as [BR-28] (repeated reports take effect once) requires: its request key is scoped to the signed-in user. ([ADR-087]: driver report sync and idempotency) Priority: Must. |  |  |
| <a id="nfr-285"></a>NFR-285 | A batch of positions can be retried safely: it carries a request key scoped to the signed-in user, so a retried batch is stored once. ([ADR-087]: driver report sync and idempotency) Priority: Must. |  |  |
| <a id="nfr-130"></a>NFR-130 | A driver's report sent with a request key already used with a different body is refused. ([ADR-087]: driver report sync and idempotency) Priority: Must. |  |  |
| <a id="nfr-66"></a>NFR-66 | If map tiles cannot be loaded, the web app still lists routes and stops, with a notice that the map is unavailable. Priority: Should. |  |  |
| <a id="nfr-131"></a>NFR-131 | If map tiles cannot be loaded, the phone app still shows the route, its stops and Navigate ([FR-28]: hand off to navigation), with a notice that the map is unavailable. Priority: Should. |  |  |
| <a id="nfr-189"></a>NFR-189 | If the SMS provider cannot be reached or does not answer, every other function keeps working without waiting for it, as [NFR-42] (work goes on when a service is down) says for the other services. Priority: Must. |  |  |
| <a id="nfr-190"></a>NFR-190 | A message the SMS provider has not accepted within 15 minutes of the server recording its trigger is dropped, and the number dropped is measured as [NFR-161] (measured service metrics) measures the others. Priority: Must. |  |  |
| <a id="nfr-191"></a>NFR-191 | Each message, identified by its stop, its type and, for "time changed", how many came before it, is sent at most once, even when its trigger repeats ([BR-28]: repeated reports take effect once). Priority: Must. |  |  |
| <a id="nfr-196"></a>NFR-196 | At most 1,000 messages leave the company a day; past that, sending stops until midnight. Priority: Must. |  |  |
| <a id="nfr-198"></a>NFR-198 | A send whose outcome is unknown is sent again only if the SMS provider drops repeats by a reference DispatchFlow gives ([Q-96]: which SMS provider); otherwise it counts as dropped. Priority: Must. |  |  |

### Platforms and deployment

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-15"></a>NFR-15 | The web app serves Admins, Dispatchers and Drivers in a desktop browser, and its driver screens also work in a phone's browser. ([ADR-035](../ADRs/ADR-035-both-apps-serve-every-role.md): both apps serve every role) |  |  |
| <a id="nfr-132"></a>NFR-132 | The phone app runs on iOS and Android, from one codebase, and serves Admins, Dispatchers and Drivers. |  |  |
| <a id="nfr-186"></a>NFR-186 | The phone app reaches the pilot's phones through TestFlight and Google Play internal testing, on the company's developer accounts. ([Q-48]: app store review, [ADR-028](../ADRs/ADR-028-driver-app-through-test-tracks-with-a-device-checklist.md): phone app through test tracks) Priority: Must. |  |  |
| <a id="nfr-255"></a>NFR-255 | In a staged pilot ([FR-407]: staged pilot), the team's own Apple and Google developer accounts take the place of the company's for [NFR-186] (app distribution through test tracks). ([ADR-080]: team accounts stand in when there is no company) Priority: Must. |  |  |
| <a id="nfr-16"></a>NFR-16 | Every server-side component starts with one Docker Compose command. |  | `just infra-check` (`infra/deploy/check_dev_stack.py`), on Linux in CI |
| <a id="nfr-208"></a>NFR-208 | The server side's linux/amd64 container images start with one command ([NFR-16]: one-command Docker Compose start) and pass their health checks on Linux in CI, and on Windows and macOS through Docker Desktop, checked by hand before each pilot build. Priority: Must. |  | `just infra-check` (`infra/deploy/check_dev_stack.py`), on Linux in CI |
| <a id="nfr-133"></a>NFR-133 | CI builds and tests every change. |  |  |
| <a id="nfr-44"></a>NFR-44 | The server states the oldest supported version of the phone app, which is the previous one. ([ADR-082]: supported phone app versions) |  |  |
| <a id="nfr-134"></a>NFR-134 | A phone app below the oldest supported version tells the user to update, keeps its route and still sends its queue, and every other version keeps working ([NFR-21]: versioned OpenAPI contracts). |  |  |
| <a id="nfr-47"></a>NFR-47 | A full 8-hour shift of location reporting ([FR-31]: location reporting during a route), screen off and unplugged, uses at most 15% of the battery, measured on real phones. [Q-38] (driver phones and battery) |  |  |
| <a id="nfr-52"></a>NFR-52 | Hosting (server, disk, backups and email) costs at most ₪550 a month, VAT included, checked against the bills each pilot month. [Q-62] (the monthly budget) |  |  |
| <a id="nfr-254"></a>NFR-254 | SMS messages to recipients ([FR-49]: SMS messages to recipients) are paid per message, only while messages are turned on ([FR-350]: the messages switch); the estimate is ₪550 to ₪850 a month with VAT at 150 deliveries a day. |  |  |
| <a id="nfr-135"></a>NFR-135 | Geocoding lookups are held to 1,000 a day; past that, they wait for the next day and the dispatcher is told. |  |  |
| <a id="nfr-67"></a>NFR-67 | The web app supports the current and previous major versions of Chrome and Edge on screens of at least 1366×768; Firefox and Safari work as best effort. Priority: Should. |  |  |
| <a id="nfr-219"></a>NFR-219 | The web app's driver screens support the current and previous major versions of Chrome on Android and Safari on iOS. Priority: Should. |  |  |
| <a id="nfr-220"></a>NFR-220 | The phone app's code can also be built as a web page, which serves only development and the end-to-end tests and never reaches users or real data; users reach every role through the web app ([NFR-15]: web app for all roles) and the phone app ([NFR-132]: phone app for all roles). Priority: Must. |  |  |
| <a id="nfr-256"></a>NFR-256 | That web build is online only, has no push and reads its route every 30 seconds while open. Priority: Must. |  |  |

### Usability, accessibility and localization

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-27"></a>NFR-27 | Every screen of the web app and the phone app is available in Hebrew, laid out right to left, and in English. ([ADR-089]: interface languages) |  |  |
| <a id="nfr-136"></a>NFR-136 | Mixed text, such as addresses with numbers, keeps its correct reading order, checked against a stated list of sample strings. |  |  |
| <a id="nfr-45"></a>NFR-45 | The driver screens of both apps need no action while the vehicle is moving. |  |  |
| <a id="nfr-137"></a>NFR-137 | The driver screens of both apps can be used one-handed: main actions are reachable by thumb on a checklist phone. |  |  |
| <a id="nfr-138"></a>NFR-138 | The driver screens of both apps are readable in sunlight, with a contrast of at least 7:1. |  |  |
| <a id="nfr-139"></a>NFR-139 | No prompt on the driver screens of either app hides the route until the driver answers it, and that includes the confirmation of [FR-97] (driver confirms a route change). |  |  |
| <a id="nfr-140"></a>NFR-140 | The phone app's driver screens (use while moving, one-handed use, sunlight readability, prompts), background location, offline work, background alerts and encrypted storage are checked by hand on real phones with a scripted checklist before each pilot build. |  |  |
| <a id="nfr-221"></a>NFR-221 | The checklist of [NFR-140] (hand checklist on real phones) also walks the phone app's Admin and Dispatcher screens, its verified app links ([NFR-213]: links open only verified apps), and the web app's driver screens in the browsers of [NFR-219] (phone browsers for the driver screens). Priority: Must. |  |  |
| <a id="nfr-46"></a>NFR-46 | Both clients meet WCAG 2.2 level AA. ([ADR-070]: security and accessibility standards) |  |  |
| <a id="nfr-141"></a>NFR-141 | Automated WCAG 2.2 level AA checks of both clients run in CI. |  |  |
| <a id="nfr-142"></a>NFR-142 | A team member audits by hand the WCAG 2.2 level AA criteria the automated checks do not cover, before the pilot, and it passes with no Level A or AA failures. |  |  |
| <a id="nfr-68"></a>NFR-68 | Dates are shown as DD/MM/YYYY, times on a 24-hour clock, distances in kilometres, weights in kilograms, volumes in litres and money in shekels (₪), in both interface languages. Priority: Should. |  |  |
| <a id="nfr-69"></a>NFR-69 | Every error message says what happened and what the user can do next, in the user's language. Priority: Should. |  |  |
| <a id="nfr-143"></a>NFR-143 | No error message shows a stack trace or a bare internal code. Priority: Must. |  |  |
| <a id="nfr-144"></a>NFR-144 | Every error code has Hebrew and English text and a next step. Priority: Should. |  |  |
| <a id="nfr-145"></a>NFR-145 | Sign-in and recovery give the same answer whether or not the address exists, a deactivated or role-less account gets the answer a wrong password gets, and a recovery refused by [FR-172] (recovery attempt limit) sends nothing. Priority: Must. |  |  |
| <a id="nfr-225"></a>NFR-225 | An unknown address runs the same password-hash work as a known one, no answer waits for an email to be sent, and [NFR-35] (sign-in attempt limit) and [NFR-216] (per-address sign-in limit) count typed addresses whether or not an account holds them. Priority: Must. |  |  |
| <a id="nfr-70"></a>NFR-70 | These actions ask for confirmation that names what will change: publishing, cancelling a plan, accepting a re-optimization, cancelling an order, handing over a route, recording or correcting an outcome on a driver's behalf ([FR-73]: dispatcher records an outcome, [FR-333]: dispatcher corrects an outcome) and deactivating a user. Priority: Should. |  |  |
| <a id="nfr-146"></a>NFR-146 | A driver's own report on a stop asks for no confirmation, because [NFR-139] (prompts never hide the route) bars a prompt that hides the route. Priority: Should. |  |  |
| <a id="nfr-71"></a>NFR-71 | On the driver screens of both apps every touch target is at least 48 by 48 density-independent (CSS) pixels. Priority: Should. |  |  |
| <a id="nfr-147"></a>NFR-147 | On the driver screens of both apps, text follows the device's font size setting without breaking the layout ([NFR-137]: one-handed use, [NFR-138]: sunlight contrast). Priority: Should. |  |  |

### Compliance and personal data

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-17"></a>NFR-17 | Use of every third-party map or geocoding service complies with its terms. |  |  |
| <a id="nfr-148"></a>NFR-148 | The geocoding provider's terms allow keeping its coordinates on customers and depots and showing them on our own map; this is checked before the pilot handles real data. |  |  |
| <a id="nfr-18"></a>NFR-18 | Maps built on OpenStreetMap data show the attribution its licence requires. |  |  |
| <a id="nfr-19"></a>NFR-19 | Personal data (for example customer names, addresses, coordinates and phone numbers, recipients' names and phones, entrance codes, users' email addresses, anything typed at sign-in, driver location pings, delivery photos and signatures) never appears in logs. |  |  |
| <a id="nfr-30"></a>NFR-30 | Driver location is collected only while that driver's route is in progress ([FR-68]: start and finish a route), and stops as [FR-328] (location stops after shift end) says; it also stops for the previous driver at a hand-over ([FR-55]: hand a route to another driver) and when the driver is marked unavailable ([FR-82]: mark a driver unavailable). |  |  |
| <a id="nfr-149"></a>NFR-149 | Both apps show the driver when location is being collected. |  |  |
| <a id="nfr-31"></a>NFR-31 | Each kind of personal data has a stated retention period and is deleted automatically when it ends. [Q-14] (retention of proofs and records) [Q-42] (legal advice on personal data) |  |  |
| <a id="nfr-184"></a>NFR-184 | Until legal advice, retention is 7 days for location pings, 90 days for proofs and for messages to recipients with their replies, counted from sending or receipt, and 1 year after retirement for customers and driver records. [Q-14] (retention of proofs and records) [Q-42] (legal advice on personal data) Priority: Must. |  |  |
| <a id="nfr-257"></a>NFR-257 | Until legal advice, an order's personal fields are deleted 90 days after its date, and a customer never retired is anonymized in place 1 year after their last order. [Q-14] (retention of proofs and records) [Q-42] (legal advice on personal data) Priority: Must. |  |  |
| <a id="nfr-258"></a>NFR-258 | Until legal advice, accounts of any role are anonymized in place 1 year after deactivation, audit records are deleted after 1 year and sessions 30 days after they end. [Q-14] (retention of proofs and records) [Q-42] (legal advice on personal data) Priority: Must. |  |  |
| <a id="nfr-259"></a>NFR-259 | Until legal advice, a geocode cache entry unused for 1 year is deleted, research instances 1 year after the project ends, and backups after 35 days. [Q-14] (retention of proofs and records) [Q-42] (legal advice on personal data) Priority: Must. |  |  |
| <a id="nfr-280"></a>NFR-280 | The server keeps contact times ([BR-133]: contact window for a held report) for 14 days, as long as a late report can arrive ([NFR-235]: offline wipe of queued reports and route data), only for sessions of accounts with the Driver role, at most one a minute for each session; the data inventory ([NFR-72]: data inventory) lists them. Priority: Must. |  |  |
| <a id="nfr-279"></a>NFR-279 | Personal data erased from the database leaves every backup within a total time that the data inventory ([NFR-72]: data inventory) states. Priority: Must. |  |  |
| <a id="nfr-260"></a>NFR-260 | At a retention deadline, the personal columns of a customer, a driver record or an account of any role are blanked and the row is kept, while positions, proofs, messages and replies are deleted. ([ADR-075]: retention deletes records and anonymizes people in place) Priority: Must. |  |  |
| <a id="nfr-150"></a>NFR-150 | When the company stops using DispatchFlow, it can get its data exported, and the data is deleted 30 days after the export is delivered. |  |  |
| <a id="nfr-48"></a>NFR-48 | Each app explains why it needs location before it asks for the permission: the phone app for background location, the web app before the browser asks. |  |  |
| <a id="nfr-151"></a>NFR-151 | The phone app follows [Google Play's rule on background location](https://support.google.com/googleplay/android-developer/answer/9799150?hl=en). Apple's rules were not checked. [Q-48] (app store review) |  |  |
| <a id="nfr-49"></a>NFR-49 | A person's personal data can be found, corrected and deleted on request. [Q-42] (legal advice on personal data) |  |  |
| <a id="nfr-152"></a>NFR-152 | After a person's data is deleted, reports keep their non-personal figures. |  |  |
| <a id="nfr-185"></a>NFR-185 | A named team member runs the company's data export ([NFR-150]: export then deletion when the company leaves) and personal-data requests, on an Admin's written request, within 30 days. Priority: Must. |  |  |
| <a id="nfr-50"></a>NFR-50 | Pilot data used for research ([FR-276]: benchmarks on pilot days, [FR-46]: disruption simulation), including in `src/research`, is pseudonymized and is never committed to the repository. |  |  |
| <a id="nfr-153"></a>NFR-153 | Pseudonymization replaces every personal or access field: names, phones, notes, street addresses, apartment details, entrance codes and outside references. [Q-60] (research use of pilot coordinates) |  |  |
| <a id="nfr-51"></a>NFR-51 | The licence of every dependency is compatible with the project's own licence ([ADR-017](../ADRs/ADR-017-proprietary-licence.md): proprietary licence). ([ADR-074]: dependency licence check) |  |  |
| <a id="nfr-154"></a>NFR-154 | The notices that dependency licences require ship with the apps. |  |  |
| <a id="nfr-72"></a>NFR-72 | A data inventory lists every kind of personal data DispatchFlow keeps: where it is stored, why, for how long ([NFR-31]: retention periods) and who can read it. [Q-42] (legal advice on personal data) Priority: Must. |  |  |
| <a id="nfr-261"></a>NFR-261 | Every column that the schema marks as personal is listed in the data inventory ([NFR-72]: data inventory). Priority: Must. |  |  |
| <a id="nfr-155"></a>NFR-155 | The data inventory is kept current with each release. Priority: Must. |  |  |
| <a id="nfr-156"></a>NFR-156 | Everything the data inventory lists counts as personal data under [NFR-19] (no personal data in logs). Priority: Must. |  |  |
| <a id="nfr-73"></a>NFR-73 | The phone app asks only for the phone permissions it uses: location ([FR-31]: location reporting during a route) and camera ([FR-30]: proof of delivery) for an account holding the Driver role, and notifications ([FR-71]: route-change alerts). Priority: Should. |  |  |
| <a id="nfr-157"></a>NFR-157 | The phone app asks for phone permissions after sign-in and, for a driver, before the route starts, never during a route in progress. Priority: Should. |  |  |
| <a id="nfr-158"></a>NFR-158 | Both apps keep working, with that feature degraded, when a permission is refused, limited or revoked. Priority: Should. |  |  |
| <a id="nfr-187"></a>NFR-187 | When location permission is refused, limited or revoked, the dispatcher sees that the driver's location is unavailable. Priority: Should. |  |  |
| <a id="nfr-193"></a>NFR-193 | Messages sent to recipients, and their replies and comments, are personal data kept as [NFR-184] (default retention periods) says; each day's average rating and share answered stay. Priority: Must. |  |  |
| <a id="nfr-202"></a>NFR-202 | A phone's opt-out ([FR-363]: opting out of messages) is kept as a hash of the phone, keyed by a secret ([NFR-03]: secrets kept out of the repository and logs), until the company leaves ([NFR-150]: export then deletion when the company leaves), whatever retention or a request removes. Priority: Must. |  |  |
| <a id="nfr-204"></a>NFR-204 | Proof photos are taken inside either app, which asks to frame the package and the door, not people; they never come from or reach the device's gallery, and leave the device once the server acknowledges them. Priority: Must. |  |  |
| <a id="nfr-222"></a>NFR-222 | The web app takes proof photos from the live camera stream inside the page, never through a file picker. Priority: Must. |  |  |
| <a id="nfr-205"></a>NFR-205 | The server re-encodes each proof image and strips its metadata before storing it. Priority: Must. |  |  |
| <a id="nfr-209"></a>NFR-209 | The server and its data, backups included, are hosted in Israel, except as [NFR-262] (accepted exceptions to hosting in Israel) says. ([ADR-078]: hosting on AWS in Israel) Priority: Must. |  |  |
| <a id="nfr-262"></a>NFR-262 | Four outside services are accepted exceptions to [NFR-209] (hosting in Israel): the push service, for push tokens and "something changed"; app builds and updates, for app code; the app stores, for testers' store accounts; and Navigate's map apps ([NFR-271]: Navigate's hand-off to map apps). ([ADR-077]: accepted exceptions to hosting in Israel) Priority: Must. |  |  |
| <a id="nfr-271"></a>NFR-271 | Navigate ([FR-28]: hand off to navigation, [FR-325]: navigation uses stored coordinates) opens Waze or Google Maps on the driver's tap, with the stop's coordinates only, never a name, phone or address text. Priority: Must. |  |  |
| <a id="nfr-217"></a>NFR-217 | The data inventory ([NFR-72]: data inventory) names each outside service that receives company data (the email sender, push, geocoder, SMS provider, app stores, Navigate's map apps), what it receives, and whether it is kept in Israel or an accepted exception. Priority: Must. |  |  |
| <a id="nfr-218"></a>NFR-218 | No push notification carries personal data, for any role: it says only that something changed, and the app fetches the details with its own token. Priority: Must. |  |  |
| <a id="nfr-263"></a>NFR-263 | A push token is removed at sign-out, at revocation and when the push service reports the device gone. Priority: Must. |  |  |
| <a id="nfr-264"></a>NFR-264 | The push service is set to accept sends only with DispatchFlow's access token. Priority: Must. |  |  |

### Observability and maintainability

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-20"></a>NFR-20 | Every service writes structured logs carrying a correlation id propagated across every hop. |  |  |
| <a id="nfr-159"></a>NFR-159 | Every service exports OpenTelemetry traces and metrics. |  |  |
| <a id="nfr-160"></a>NFR-160 | Every service exposes health and readiness endpoints. |  |  |
| <a id="nfr-161"></a>NFR-161 | Solve latency, re-optimization latency, job queue depth, error rate, dropped-order rate and on-time percentage are measured. |  |  |
| <a id="nfr-21"></a>NFR-21 | Every API, client to backend and backend to optimization service, is described by a versioned OpenAPI contract. |  |  |
| <a id="nfr-162"></a>NFR-162 | Both sides of every API are tested against its OpenAPI contract. |  |  |
| <a id="nfr-74"></a>NFR-74 | Errors in both clients and crashes of the phone app reach the team with the app version and trace id ([NFR-20]: structured logs with correlation ids). ([ADR-079]: client error and crash reporting) Priority: Should. |  |  |
| <a id="nfr-163"></a>NFR-163 | Client errors and phone app crashes reported to the team carry no personal data ([NFR-19]: no personal data in logs), credentials or tokens. Priority: Should. |  |  |
| <a id="nfr-75"></a>NFR-75 | The team is alerted when solve failures pass 10% of a day, a preview takes over 30 seconds ([NFR-07]: re-optimization response time), 5 jobs are queued or disk use passes 80%. Priority: Should. |  |  |
| <a id="nfr-268"></a>NFR-268 | The text of an alert to the team, by email or chat, carries no personal data ([NFR-19]: no personal data in logs). Priority: Must. |  |  |
| <a id="nfr-164"></a>NFR-164 | The team is alerted when a backup fails ([NFR-28]: scheduled off-VM backups). Priority: Must. |  |  |
| <a id="nfr-197"></a>NFR-197 | The team is alerted when sending stops at the cap of [NFR-196] (daily message cap) or more than 10% of a day's messages are dropped ([NFR-190]: stale messages dropped). Priority: Must. |  |  |
| <a id="nfr-76"></a>NFR-76 | Logs, traces and metrics are kept for 7 days and then deleted. Priority: Should. |  |  |
| <a id="nfr-77"></a>NFR-77 | Every database change follows [ADR-041] (one PostgreSQL schema per module, changed only by Flyway migrations) and [ADR-126] (persistence through JPA, with the schema never changed by Hibernate). Priority: Should. |  |  |
| <a id="nfr-165"></a>NFR-165 | Every database change is tested in CI against the schema of the last merge to `main`, with data in it, checking the values after the migration. Priority: Should. |  |  |
| <a id="nfr-79"></a>NFR-79 | A dispatcher guide and a driver guide exist in Hebrew and English. Priority: Should. |  |  |
| <a id="nfr-166"></a>NFR-166 | An operations runbook covers deployment, restore ([NFR-125]: restore time), secret rotation ([NFR-03]: secrets kept out of the repository and logs), handling an outage ([NFR-29]: API availability) and recovering a lost Admin ([FR-292]: Admin created on a written request). Priority: Should. |  |  |

### Testing and verification

| ID | Requirement | Size | Enforced by |
|---|---|---|---|
| <a id="nfr-80"></a>NFR-80 | The feasibility validator ([BR-15]: only validator-accepted solutions count, [BR-42]: one shared feasibility validator) is tested on its own, apart from any real solver. Priority: Must. |  |  |
| <a id="nfr-167"></a>NFR-167 | The feasibility validator accepts published best-known Solomon solutions, under a stated rounding convention. Priority: Must. |  |  |
| <a id="nfr-168"></a>NFR-168 | The feasibility validator accepts solutions exactly on a limit, such as a load equal to capacity or a late stop within the shift in a re-optimization ([BR-107]: re-optimization may keep late stops). Priority: Must. |  |  |
| <a id="nfr-169"></a>NFR-169 | The feasibility validator rejects a catalogue of crafted solutions that together break each of [BR-03] to [BR-07] (time windows through single assignment), [BR-10] (completed and in-progress stops are locked), [BR-14] (only orders with coordinates are planned), [BR-39] (one route per vehicle and per driver), [BR-40] (routes start and end at depots), [BR-51] (re-optimized routes start where the vehicle is), [BR-52] (a withdrawn route ends at its last reported stop), [BR-106] (goods stay on their vehicle), [BR-107] (re-optimization may keep late stops) and [BR-108] (nothing added to finished routes), and names the rule each one breaks. Priority: Must. |  |  |
| <a id="nfr-170"></a>NFR-170 | A fake solver that returns an infeasible solution fails its solve job, lists the violations and stores no plan; in a re-optimization it leaves the plan and its routes as they were ([BR-26]: a failed re-optimization changes nothing). Priority: Must. |  |  |
| <a id="nfr-194"></a>NFR-194 | The feasibility validator accepts a leave-at-door stop served outside its window, within its driver's shift and on its date, and rejects one served outside either, in an instance that also has binding windows ([BR-119]: leave-at-door windows are preferences). Priority: Must. |  |  |
| <a id="nfr-82"></a>NFR-82 | Property-based tests generate small random instances from a recorded seed, under a solution or iteration budget and never a wall-clock limit. Priority: Should. |  |  |
| <a id="nfr-171"></a>NFR-171 | In property-based tests the validator ([BR-15]: only validator-accepted solutions count) accepts whatever either solver returns, so a rejected solution or a solver error fails the test. Priority: Should. |  |  |
| <a id="nfr-172"></a>NFR-172 | In property-based tests on instances built around a known full solution, every order is served. Priority: Should. |  |  |
| <a id="nfr-173"></a>NFR-173 | In property-based tests, generated re-optimizations of a published plan pass [NFR-171] (validator property tests) and [NFR-172] (full-service property tests), and locked stops ([BR-10]: completed and in-progress stops are locked) also stay in place. Priority: Should. |  |  |
| <a id="nfr-174"></a>NFR-174 | In property-based tests, the DispatchFlow solver gives the same solution for the same input, seed and iteration budget ([NFR-13]: reproducible solver), in separate processes too. Priority: Should. |  |  |
| <a id="nfr-83"></a>NFR-83 | End-to-end tests run UC-06, UC-07, UC-08, UC-10, UC-11 and UC-14 ([Use cases](#use-cases)), each main flow and at least one listed failure, in CI against the Docker Compose stack ([NFR-16]: one-command Docker Compose start), on the web app and again on the phone app's web build. Priority: Should. |  |  |
| <a id="nfr-175"></a>NFR-175 | End-to-end tests run with a controllable clock, fixed road data, solves under a search budget rather than a time limit, a faked push service, a faked SMS provider, a faked email sender and no outside network. Priority: Should. |  |  |
| <a id="nfr-176"></a>NFR-176 | Tests through the API check every role against every capability of the [permission matrix](#company-users-and-roles), and one driver against another driver's routes, stops, proofs, positions and stream events, apart from the late reports of [BR-27] (late outcomes are still accepted). Priority: Must. |  |  |
| <a id="nfr-210"></a>NFR-210 | Tests through the API also check each combination of two roles, and all three, against exactly the union of their capabilities ([FR-293]: roles combined in one account), outcomes on the account's own route included, and that a role-less account cannot sign in ([BR-123]: no role, no sign-in). Priority: Must. |  |  |

## Non-goals

DispatchFlow deliberately does not:

- **Serve more than one company.** One deployment serves one delivery company, and nothing separates
  one company's data from another's. Reopen when a second company wants DispatchFlow.
  ([ADR-024](../ADRs/ADR-024-one-delivery-company-no-multi-tenancy.md): one company and no
  multi-tenancy)
- **Use live traffic data.** Travel times come from the road network with time-of-day multipliers;
  ETA drift is a stated, known limitation. ([ADR-048]: travel times without live traffic)
- **Handle payments,** including cash on delivery and billing for DispatchFlow itself. A note on an order
  may mention cash, but nothing is calculated. ([Q-57]: scope of the payments non-goal)
- **Run a marketplace or crowdsourced drivers.** Every driver belongs to the company.
- **Provide turn-by-turn navigation.** Both apps hand off to Waze or Google Maps.
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
  it or a legal duty is confirmed ([Q-39]: legal driver breaks). ([ADR-090]: scope cuts for the
  first release)
- **Manage inventory or warehouses.** Reopen when the company cannot load vehicles without it.
  ([ADR-090]: scope cuts for the first release)
- **Manage fleet maintenance.** Reopen when the company asks to plan around scheduled servicing.
  ([ADR-090]: scope cuts for the first release)
- **Forecast demand.** Reopen when the company needs a plan prepared before its orders exist.
  ([ADR-090]: scope cuts for the first release)
- **Integrate vehicle telematics or trackers.** Positions come only from the apps ([FR-31]: location
  reporting during a route, [FR-243]: web app location while open). Reopen when the company's
  vehicles already carry trackers. ([ADR-090]: scope cuts for the first release)
- **Run dispatching offline.** Only a driver's route work runs without a connection, in the phone
  app ([FR-69]: route shown offline, [FR-70]: report stops offline); printed routes ([FR-72]: print
  or export routes) are the fallback for dispatching. Reopen when the company's dispatcher cannot
  get through an outage on printed routes. ([ADR-090]: scope cuts for the first release)
- **Take orders from end customers.** There is no storefront or order form for buyers; orders come
  from a dispatcher, an import or, later, an API ([FR-116]: order intake API). Reopen when the
  company asks to take orders directly from its buyers. ([ADR-090]: scope cuts for the first
  release)
- **Model electric-vehicle range and charging.** Reopen when the company's fleet includes electric
  vehicles whose range limits a day. ([ADR-090]: scope cuts for the first release)
- **Monitor cargo temperature.** Refrigeration is at most a vehicle attribute ([FR-112]: orders that
  need a vehicle or driver attribute). Reopen when the company must keep temperature records for its
  goods. ([ADR-090]: scope cuts for the first release)
- **Start or apply a re-optimization without a dispatcher.** Only a person starts one ([FR-332]:
  only a person starts a re-optimization) and applies it ([FR-255]: nothing changes until accepted,
  [BR-47]: published routes change only through named actions). Reopen when the company runs days
  with no dispatcher on duty. ([ADR-090]: scope cuts for the first release)
- **Hold conversations with recipients.** A reply is read only for a rating, a comment or an opt-out
  ([FR-357]: rating replies, [FR-360]: rating comments, [FR-363]: opting out of messages); a
  recipient with a question calls the company. Reopen when the company wants to answer recipients by
  message.
- **Message recipients on WhatsApp or by email.** Messages go by SMS only
  ([ADR-031](../ADRs/ADR-031-sms-through-an-israeli-provider-for-recipient-messages.md): SMS for
  messages to recipients). Reopen when ADR-031's revisit trigger fires.

## Open questions

| ID | Question | Blocks |
|---|---|---|
| Q-01 | Which delivery company is it, and what does it provide — historical orders and routes, fleet data, access to dispatcher and drivers? The team looks for one, with no fixed date; until then synthetic data ([FR-45]: synthetic order generator) is the fallback, and with no company at the start of the pilot the pilot is staged ([FR-407]: staged pilot), and a staged pilot's people and traces count as real ([FR-410]: staged pilot counts as real data). Owner: Team. Data owner: the company. | FR-40, FR-47, FR-48 and FR-276 |
| Q-02 | ~~Geocoding provider, and what its terms and answers mean for the rows it blocks.~~ **Resolved** on 2026-11-13: GovMap, through the `Geocoder` port. Before the geocoding code is built, a team member reads GovMap's terms and gets its API token; if the terms forbid keeping its coordinates on customers and depots, or showing them on our map ([NFR-148]: geocoding terms), self-hosted Nominatim replaces it behind the same port. **Not yet checked:** GovMap's price, request limits and coordinate system, which is expected to be the Israeli grid (ITM), converted to WGS84 by the adapter. The other points are answered by one test, run when geocoding is built: one address list goes through GovMap alone, in Hebrew, Arabic and transliteration, each with a known location ([Q-83]: Arabic or transliterated addresses). Its results set what counts as partial or ambiguous ([FR-63]: doubtful addresses flagged before a solve) and show whether normalization ([FR-140]: Hebrew address normalization) is needed. Suggestions while typing ([FR-139]: address suggestions while typing) use GovMap's if its terms allow, otherwise Photon. | FR-12, FR-13, NFR-17 — [ADR-085] (GovMap as the geocoding provider) — also FR-63, FR-139, FR-140 |
| Q-03 | ~~Map library for each app, and the tile source.~~ **Resolved** on 2026-11-13 by [ADR-084] (MapLibre in both apps with self-hosted tiles): MapLibre in both apps, `maplibre-gl` in the web app and in the phone app's web build ([NFR-220]: the phone app's web build) and MapLibre React Native on phones, with one style and self-hosted Protomaps tiles served by Caddy. [NFR-109] (map frame rate) binds both apps, measured as [NFR-181] (how latency targets are measured) says. (Widened on 2026-11-11 to both apps, by [ADR-035](../ADRs/ADR-035-both-apps-serve-every-role.md) (both apps serve every role).) | FR-24, FR-120, NFR-109 |
| Q-04 | ~~Real-time channel.~~ **Resolved** on 2026-11-04 by [ADR-026](../ADRs/ADR-026-server-sent-events-and-push-for-live-updates.md) (server-sent events and push): server-sent events to the web app, HTTP POST for driver and web app actions, and a push to a backgrounded phone app, which then fetches its route. An alert raised while the web app is closed waits in the alert list ([FR-335]: alerts wait in the alert list). | FR-16, FR-32, FR-33 and FR-34, FR-65, FR-71, FR-95, FR-101, FR-102, BR-27, FR-152, FR-222, BR-89 |
| Q-05 | ~~Status sets and Plan transitions.~~ **Resolved** on 2026-10-09 by [ADR-053] (plan lifecycle, publishing stability and drop reasons). ~~What triggers a published plan's completion.~~ **Resolved** on 2026-11-04: the end of its date, at midnight Israel time ([BR-102]: plans complete at midnight). | BR-12 |
| Q-06 | ~~How heavily route stability weighs.~~ **Resolved** on 2026-11-04 by [ADR-029](../ADRs/ADR-029-solver-objective-and-re-optimization-rules.md) (solver objective and re-optimization rules): a penalty per moved stop, starting at 5 minutes of travel time and tuned before the study ([BR-115]: the re-optimization penalties). Under [BR-106] (goods stay on their vehicle), only drops and the stops of a vehicle that is out count. (The measure itself was settled by [ADR-053]: plan lifecycle, publishing stability and drop reasons.) | FR-37, FR-46, NFR-07 |
| Q-07 | ~~Who starts a re-optimization, and what lateness counts as at risk.~~ **Resolved** on 2026-11-04: only a person starts one ([FR-332]: only a person starts a re-optimization), and a stop is at risk 15 minutes before its window ends ([FR-253]: when a stop is at risk). | FR-253, FR-35 |
| Q-08 | ~~Numeric targets.~~ **Resolved** on 2026-11-04: [NFR-08] (edit-check latency), [NFR-09] (live update latency), [NFR-10] (API load test), [NFR-180] (alert delivery time) and [FR-343] (SUS score without a pass mark) carry the values. 1% downtime, the backup and a 4-hour restore stand, and a restore after losing the VM does not count against the 1% ([NFR-183]: VM loss excluded from downtime). | NFR-08, NFR-09, NFR-10, NFR-14, FR-48 and NFR-28, NFR-29, FR-207, FR-71, FR-95, FR-101, FR-102, NFR-59, NFR-125, NFR-126, NFR-128 |
| Q-09 | ~~May a plan with flagged violations be published?~~ **Resolved** on 2026-10-09: no ([BR-17]: no publishing with violations; [ADR-053]: plan lifecycle, publishing stability and drop reasons). | FR-23 |
| Q-10 | ~~CSV column format, encodings, the customer file and the Excel sheet.~~ **Resolved** on 2026-11-04: named template columns ([FR-309]: fixed import columns); UTF-8, or Windows-1255 with a confirmed preview ([FR-313]: encoding detection); no UTF-16 ([FR-314]: UTF-16 files refused); customer rows as [FR-318] (customer row rules) says; the first sheet ([FR-319]: first sheet only). (Whether a file with invalid rows imports its valid rows was **resolved** on 2026-10-10: it imports nothing, and rows name their customer by address; [ADR-086]: CSV order import.) | FR-10 and FR-61, FR-210, FR-84, FR-138 |
| Q-11 | ~~Capacity units, and an order with no volume.~~ **Resolved** on 2026-11-04: kilograms and litres, and a blank volume counts as zero ([BR-50]: capacity and demand values). The pre-flight check lists those orders ([FR-230]: pre-flight check). **Changed** on 2026-11-11: weight and volume must be positive, a blank one is refused ([BR-50]: capacity and demand values), and the pre-flight check no longer lists orders with no volume ([FR-230]: pre-flight check). | FR-05, FR-09 — [ADR-061] (capacity as weight and volume), NFR-68 |
| Q-12 | ~~Role permission matrix: which roles may manage fleet, customers and orders, solve, edit, publish, re-optimize.~~ **Resolved** on 2026-10-11: an Admin does everything a Dispatcher does, plus the company and its users; a Dispatcher runs every operation and reads users; a Driver works their own route only (the matrix under [FR-03]: role-based access; [ADR-068]: authorize every web handler by role). | FR-03 |
| Q-14 | Proof files: **resolved** on 2026-11-04 by [ADR-027](../ADRs/ADR-027-proof-files-on-the-vm-disk-with-hourly-backups.md) (proof files on the VM disk): on the VM's disk, backed up off it. Retention defaults are in [NFR-184] (default retention periods), [NFR-76] (log retention) and [NFR-150] (export then deletion when the company leaves). **Resolved** on 2026-11-13, until legal advice: the rest of the periods that [NFR-31] (retention periods) requires are in [NFR-257] (retention of orders and customers), [NFR-258] (retention of accounts, audit records and sessions) and [NFR-259] (retention of the cache, research instances and backups), and people are anonymized in place ([NFR-260]: anonymizing at the retention deadline). **Still open:** the legal check, which [Q-42] (legal advice on personal data) owns. The legal basis for tracking drivers moved to [Q-42] (legal advice on personal data). Suggested owner: Team, legal advice. | FR-30, FR-31, NFR-19 and NFR-31, NFR-76, FR-87, FR-160, NFR-150, NFR-184 |
| Q-15 | ~~When location reporting runs, how often, staleness and refused permission.~~ **Resolved** on 2026-11-04: every 30 seconds from route start to finish ([FR-31]: location reporting during a route, [NFR-30]: location collected only on a route), stale after 5 minutes by the server's clock ([FR-74]: age of each vehicle position), and a refused permission shows the dispatcher that location is unavailable ([NFR-187]: location permission refused). | FR-31 and FR-68, FR-74, BR-80, NFR-30, NFR-158 |
| Q-16 | ~~How the app reaches the phones, and how native builds are tested.~~ **Resolved** on 2026-11-04 by [ADR-028](../ADRs/ADR-028-driver-app-through-test-tracks-with-a-device-checklist.md) (phone app through test tracks): TestFlight and Google Play internal testing ([NFR-186]: app distribution through test tracks), a scripted checklist on real phones ([NFR-140]: hand checklist on real phones), and automated end-to-end tests on the web build ([NFR-83]: end-to-end tests). | FR-31, NFR-15 and FR-71, NFR-39, NFR-140, NFR-47, NFR-151, NFR-61, NFR-71, NFR-73, NFR-83 — [ADR-003](../ADRs/ADR-003-dispatcher-console-and-driver-app.md) (web app and React Native phone app), NFR-132 |
| Q-17 | Can the DispatchFlow solver, written in Python, reach enough ALNS iterations within the default budget of [FR-19] (configurable solve time limit) to compete? If not, what changes? (Evidence of 2026-10-10: about 1,800 iterations in 30 s on a 200-stop day, after constant-time insertion checks; see the speed plan of M11 ([§10.5 of the implementation plan](https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#105-speed-plan-q-17)). Whether that competes is for the benchmarks. **Put off** on 2026-11-13.) | FR-42, NFR-07, NFR-25 — [ADR-002](../ADRs/ADR-002-core-backend-and-separate-optimization-service.md) (Java core and Python solver service) |
| Q-18 | ~~When to adopt PostGIS.~~ **Resolved** on 2026-11-04: when the first feature needs a spatial query, as [ADR-007](../ADRs/ADR-007-postgresql-with-flyway-migrations.md) (PostgreSQL with Flyway migrations) already says. Until then, plain latitude and longitude columns, with service-area and distance checks in application code. | [ADR-007](../ADRs/ADR-007-postgresql-with-flyway-migrations.md) (PostgreSQL with Flyway migrations) |
| Q-19 | ~~Which reasons the system can give for a dropped order, and how they are determined.~~ **Resolved** on 2026-10-09 by [ADR-053] (plan lifecycle, publishing stability and drop reasons). | FR-18 |
| Q-20 | Buckets and factors: **resolved** on 2026-11-04 by [ADR-029](../ADRs/ADR-029-solver-objective-and-re-optimization-rules.md) (solver objective and re-optimization rules): three buckets ([FR-321]: travel-time buckets), solvers plan at the highest factor and ETAs and the validator use each bucket's ([FR-322]: planning with the highest factor), and the buffer starts at 10% ([FR-227]: ETA buffer). **Resolved** on 2026-11-13 by [ADR-048] (travel times without live traffic): the buckets start at 07:00–09:30 and 15:30–18:30, an estimate to calibrate and not a measurement, each bucket including its start and excluding its end ([FR-408]: bucket boundaries); an arc takes its departure bucket's factor, read from local wall-clock time ([FR-400]: arc takes its departure bucket's factor); the validator and the ETAs use buffered times ([FR-401]: buffered times for the validator and ETAs); the objective's travel cost uses per-bucket times without the buffer, while lateness and door-window penalties use the validator's ([FR-402]: objective's travel and penalty times); at a door stop the solver may plan a wait and the validator re-times it ([FR-403]: waiting at door stops). The rule of FR-322 stays: M1 solves the demo day both at the highest factor and at per-bucket times, and if the highest factor needs an extra vehicle or drops an order the per-bucket solve serves, an ADR decides whether FR-322 changes. | FR-90, NFR-33, FR-227, FR-321, FR-322, FR-400 to FR-403 |
| Q-21 | ~~Solve and re-optimization limits, margins and queues.~~ **Resolved** on 2026-11-04: a 45-second default solve limit (changed from 30 seconds on 2026-11-11 to match the ARD), which an Admin changes ([FR-19]: configurable solve time limit, [FR-183]: planning settings for Admins); a 20-second re-optimization limit ([BR-84]: the re-optimization time limit); failure 30 seconds past either ([BR-32]: a solve that overruns its limit fails); queue limits in [BR-112] (solve queue limits); a queued re-optimization does not survive a restart ([BR-85]: a restart fails a re-optimization). | FR-19 and BR-84, BR-32, FR-183 |
| Q-22 | ~~Which Gehring-Homberger sizes run where.~~ **Resolved** on 2026-11-04: none in CI, which runs a small Solomon smoke run; the offline study runs the 200-customer instances, and 400 if time remains ([FR-275]: Gehring-Homberger benchmarks). | FR-275 |
| Q-23 | ~~Moving a stop to a vehicle that is not carrying its goods.~~ **Resolved** on 2026-11-04 by [ADR-029](../ADRs/ADR-029-solver-objective-and-re-optimization-rules.md) (solver objective and re-optimization rules): goods stay on their vehicle, except for a vehicle that is out and for new orders ([BR-106]: goods stay on their vehicle, [BR-117]: dropped and moved orders count as new), and the preview flags those for a hand-over ([FR-334]: preview names goods hand-overs). No depot visit is modelled. A late driver cannot shed stops to another vehicle. | FR-07, FR-11, FR-35 and BR-05, FR-111 |
| Q-24 | ~~Should a day with a vehicle withdrawn mid-route be compared with its baseline, and on what?~~ **Resolved** on 2026-10-10: it is compared on kilometres and on-time, without driver-hours, and the comparison names the withdrawn vehicles (FR-269). | FR-270, FR-271 |
| Q-25 | ~~Must geocoded coordinates copied onto customers and depots expire with the geocode cache, and how?~~ **Resolved** on 2026-10-10: it is settled with the provider choice. The chosen provider's terms must allow keeping the copies; if Google stays, they are refreshed on a schedule. That condition now belongs to Q-02, and nothing changes in code until Q-02 is decided. **Changed** on 2026-11-13: Q-02 chose GovMap, so there is no refresh schedule, and [NFR-148] (geocoding terms) checks the terms instead. | NFR-17, FR-12, FR-13 |
| Q-26 | ~~May the company's last Admin be demoted?~~ **Resolved** on 2026-10-10: no ([BR-19]: the minimum of active Admins). | FR-01, FR-02 |
| Q-27 | ~~When a Driver's role changes, what happens to their driver record in fleet?~~ **Resolved** on 2026-10-10 by [ADR-062] (fleet checks the driver role when it reads): fleet stops counting the record while the user is not a Driver, and another driver may take its vehicle. | FR-02, FR-06 |
| Q-28 | ~~Should publishing also catch a shift, capacity or depot change made after the solve?~~ **Resolved** on 2026-10-10 by [ADR-054] (publishing re-checks every route): every route is re-checked through `evaluate` against the current fleet, a broken rule refuses publishing, and the re-checked times are published when nothing breaks ([BR-68]: publishing uses the re-checked times). | BR-05, BR-06, BR-20 |
| Q-29 | ~~The parameters of [NFR-24] (static quality target).~~ **Resolved** on 2026-11-04, not yet agreed with the academic advisor: N = 5%, a 60-second limit, 10 runs, a drop penalty large enough that nothing drops if avoidable, and no fixed vehicle cost ([NFR-24]: static quality target, [NFR-110]: static study scoring, [NFR-111]: static study runs). | NFR-24, NFR-110, NFR-111 |
| Q-30 | ~~The parameters of [NFR-25] (incremental re-optimization study).~~ **Resolved** on 2026-11-04, not yet agreed with the academic advisor: T = 5 seconds, also run at 1, 2, 10 and 30 seconds, over 300 insertions ([NFR-25]: incremental re-optimization study, [NFR-120]: runs at other time limits, [NFR-182]: the study's seeded insertions). **Changed** on 2026-11-13: [NFR-120] (runs at other time limits) adds a 20-second limit ([Q-93]: study design gaps, item 9). | NFR-25, FR-46, NFR-118, NFR-120 |
| Q-31 | ~~What counts as a match, and a row with no match or several.~~ **Resolved** on 2026-11-04: normalized address text ([FR-310]: matching rows by address); no match creates a customer, flagged ([FR-311]: unmatched rows create customers); several reject the row ([FR-312]: ambiguous rows are rejected). | FR-205, FR-60, FR-212 |
| Q-32 | ~~A stop that cannot make its window during re-optimization.~~ **Resolved** on 2026-11-04 by [ADR-029](../ADRs/ADR-029-solver-objective-and-re-optimization-rules.md) (solver objective and re-optimization rules): it may stay on the route, late and flagged, within the driver's shift ([BR-107]: re-optimization may keep late stops). A solve stays bound by [BR-03] (service starts inside the time window). | BR-03, BR-15, BR-23, FR-35, FR-37, FR-46, FR-57, FR-103, NFR-25 |
| Q-33 | ~~A mistaken final outcome.~~ **Resolved** on 2026-11-04: a dispatcher corrects it, with a reason, and the original stays in the history ([FR-333]: dispatcher corrects an outcome, [BR-18]: outcomes are final for drivers). | BR-18, BR-27, BR-28, BR-33, FR-38, FR-73, NFR-70 |
| Q-34 | ~~Manual control on a published plan.~~ **Resolved** on 2026-11-04: only the named actions of [BR-47] (published routes change only through named actions). To change a route: re-optimize, hand over or cancel. [FR-22] (move stops by hand) stays a Should. | BR-12, BR-16, FR-22, FR-23, FR-32, FR-35, FR-55, FR-207, FR-91, BR-47 |
| Q-35 | ~~One person dispatching and driving.~~ **Resolved** on 2026-11-04 as two accounts with one role each, and **changed** on 2026-11-11: one account per person, which can hold several roles ([FR-293]: roles combined in one account). | FR-02, FR-03, BR-02, BR-20 |
| Q-36 | ~~One driver per vehicle per date, or two part-day drivers.~~ **Resolved** on 2026-11-04: one ([BR-39]: one route per vehicle and per driver). Reopen when the company runs two part-day drivers on one vehicle. | FR-06, FR-15, FR-54, FR-55, FR-79, BR-06, BR-20, BR-39 |
| Q-37 | ~~How drivers without email sign in and recover.~~ **Resolved** on 2026-11-04 by [ADR-030](../ADRs/ADR-030-admin-issued-credentials-and-token-revocation.md) (Admin-issued credentials and token revocation): a username and an Admin-issued password (then FR-294 and FR-295), and an emailed link for Admins and Dispatchers ([FR-386]: password recovery by email). No SMS, and [FR-200] (a driver's phone is no recovery channel) stands. **Changed** on 2026-11-11 by [ADR-036](../ADRs/ADR-036-one-account-per-person-signed-in-by-email.md) (one account per person, signed in by email): every user, drivers included, signs in with their own email and recovers through an emailed link. | FR-02, FR-52, NFR-01, NFR-35, NFR-36, FR-200 |
| Q-38 | ~~Are the phones company-owned or personal, which models and OS versions, and what battery use is acceptable over a full shift?~~ **Resolved** on 2026-11-13: drivers' own iOS and Android phones, any version our Expo SDK supports, and the limit of [NFR-47] (battery use). The company can reopen it. Whether personal phones change the consent position moves to [Q-42] (legal advice on personal data). | FR-31, FR-77, NFR-15, NFR-30, NFR-44, NFR-47, Q-15, Q-16, NFR-132 |
| Q-39 | Must routes leave time for driver breaks by law? [Wikipedia](https://en.wikipedia.org/wiki/Israeli_labor_law), which is not the statute, says a work day of at least six hours gives 45 minutes of rest, including an uninterrupted 30-minute break. Rules specific to drivers were not found. Needs verification against the Hours of Work and Rest Law itself, and whether the company must keep records of driver hours ([Non-goals](#non-goals)). **Decided on 2026-11-13:** the default stands, so [BR-105] (time reserved for breaks) and [FR-81] (driver breaks) stay Should. **Action:** a team member reads the Hours of Work and Rest Law itself before the pilot, and if breaks are a legal duty both become Must. Owner: Team. | BR-06, BR-15, FR-17, FR-81, [Non-goals](#non-goals), BR-105 |
| Q-40 | ~~The savings target X of [NFR-32] (pilot savings target), which days are comparable, how actuals are measured, and what acceptance means if no company is confirmed.~~ **Resolved** on 2026-11-13: X is 10%, a judgment and not a measurement, and N is 10 comparable days; comparable days are defined in [NFR-248] (comparable pilot days); actuals come from the apps' report times and the position traces ([NFR-249]: pilot actuals); with no company the pilot is staged ([FR-407]: staged pilot) and compared against the nearest-neighbour baseline ([NFR-250]: staged pilot comparison). **Still open**, put off on 2026-11-13: the pilot's length and shadow period; operating hours and days, support cover during exams, the manual fallback, and whether overnight work is in scope. Owner: Team. | FR-40, FR-47, FR-48, FR-72, FR-106, FR-114, NFR-29, NFR-32, NFR-33, NFR-34, NFR-128 |
| Q-42 | What needs legal advice about personal data? **Working assumptions until then** (2026-11-04): the company owns the data and the team processes it under a written pilot agreement; hosting in Israel, now [NFR-209] (hosting in Israel), with the exceptions of [NFR-262] (accepted exceptions to hosting in Israel); a deletion anonymizes names in kept rows; backups age out after the period of [NFR-259] (retention of the cache, research instances and backups). **Support access:** Naim Elijah, Moshe Klein, Bentzion Hadad and Shai Manor may see the company's data for support, and those reads are not audited. Still to verify: database registration; sending addresses to a third-party geocoder; tracking employees and the legal basis for it; whether pharmacy deliveries are health data; the privacy notice ([FR-77]: terms and privacy acceptance); what a report may show about an employee ([FR-109]: report per driver); what a delivery's rating and comment show about its driver ([FR-361]: low-rating alert); a person's rights ([NFR-49]: personal-data requests); whether personal phones change the consent position ([Q-38]: driver phones and battery); whether the legal duties of IS 5568, such as an accessibility statement, reach the apps ([Q-59]: Israeli accessibility standard); the legal points of messages to recipients ([Q-95]: consent for messages to recipients); what Navigate's hand-off of a stop's coordinates to Waze or Google Maps, and the driver's live position that the map app receives while navigating, mean for personal data ([NFR-271]: Navigate's hand-off to map apps); and the accepted exceptions to hosting in Israel named above. Amendment 13 of the Privacy Protection Law was due to take effect on 14 August 2025 ([Pearl Cohen](https://www.pearlcohen.com/major-amendment-to-israeli-privacy-law-set-to-take-effect/)). **Owner:** Naim Elijah, who gets the legal advice before the pilot handles real data (M13). The working assumptions stand until then. | Q-02, Q-14, FR-12, FR-31, FR-33, FR-47, FR-77, FR-109, NFR-14, NFR-19, NFR-28, NFR-30, NFR-31, NFR-49, NFR-50, FR-53, FR-73, FR-104, BR-37, FR-160, FR-161, NFR-72, NFR-184 |
| Q-43 | ~~Which priority is highest, and the default.~~ **Resolved** on 2026-11-04: 1 is the highest, and the default is 3 ([BR-53]: the priority scale). | FR-09, FR-10, FR-57, FR-84, BR-53, BR-09 |
| Q-44 | ~~Further interface languages.~~ **Resolved** on 2026-11-04: Hebrew and English only, and [FR-121] (Arabic and Russian for drivers) stays a Could. Reopen when the company's drivers need it. | FR-121, NFR-27 |
| Q-45 | ~~The service area's boundary.~~ **Resolved** on 2026-11-04: what the OpenStreetMap road-data extract covers ([FR-320]: the service area). **Changed** on 2026-11-13: until a company is known it is the whole extract, then the company's area. | FR-14, FR-63, FR-64, BR-24, BR-80 |
| Q-46 | ~~A day that does not run, and orders on past dates.~~ **Resolved** on 2026-11-04: a dispatcher can cancel a published plan ([FR-336]: cancel a published plan, [BR-103]: a cancelled plan is final), and past dates refuse orders ([BR-116]: no orders on past dates). | BR-12, BR-34, BR-97, Q-05, FR-11, FR-15, FR-38, FR-39, FR-75 |
| Q-47 | ~~Failure reasons.~~ **Resolved** on 2026-11-04: a fixed list plus other with text ([BR-109]: the fixed list of failure reasons). | FR-242, FR-73, BR-11, FR-161 |
| Q-48 | How long does store review take for an app using background location, and what do Apple's rules on it require? Do drivers count as TestFlight external testers, whose builds go to App Review, and does Google internal testing need each driver's Google account address? All need verification and could move the pilot date; they were put off on 2026-11-13. **Decided on 2026-11-04:** the company owns the developer accounts ([NFR-186]: app distribution through test tracks), so distribution waits for [Q-01] (which company and what data). **Decided on 2026-11-14:** with no company by the start of the pilot, the team's own Apple Developer Program account (about $99 a year, ≈₪320) and Google Play account ($25 once, ≈₪80) distribute the app through TestFlight and Google Play internal testing, for about ₪400 in the first year ([NFR-255]: team accounts for a staged pilot's distribution). Suggested owner: Team. | Q-16, FR-31, FR-47, NFR-151, NFR-186 |
| Q-49 | ~~The gaps in the measures of [FR-38] (the daily summary).~~ **Resolved** on 2026-11-04: failed stops shown beside on-time ([FR-339]: failed stops beside on-time), a missing arrival uses the delivery time ([FR-340]: delivery time stands in for arrival), clock correction ([BR-114]: report times corrected for the device's clock), and planned distance ([FR-341]: the summary shows planned distance). | FR-38, FR-41, FR-70, FR-73, FR-106, NFR-32, NFR-33, BR-44 |
| Q-50 | ~~What the baseline of [FR-39] (nearest-neighbour baseline comparison) obeys and proves.~~ **Resolved** on 2026-11-04: it obeys the business rules ([FR-342]: rules the baseline obeys) and is a sanity check. The proof of value stays [NFR-32] (pilot savings target), against the company's own routes. | FR-39, FR-40, FR-108, NFR-32 |
| Q-51 | ~~Strict or proportional drop penalties.~~ **Resolved** on 2026-11-04 by [ADR-029](../ADRs/ADR-029-solver-objective-and-re-optimization-rules.md) (solver objective and re-optimization rules): proportional ([BR-54]: drop penalty scales with priority), with fewer drops as best effort ([BR-55]: fewer drops preferred). A pin, lock or route limit is honoured over priority. For *displaced*, the fact given under [FR-144] (drop reasons explained) says that other orders took its place, without claiming one cause. | BR-54, BR-55, BR-09, FR-17, FR-42, FR-46, FR-91, NFR-24, NFR-25, FR-144, FR-147, FR-159 |
| Q-52 | ~~Whether in-progress includes the stop being driven to.~~ **Resolved** on 2026-11-04: no, a stop locks when its driver marks it *arrived* ([BR-10]: completed and in-progress stops are locked). | BR-10, BR-25, FR-35 |
| Q-53 | ~~What Navigate sends, and a pin after an address change.~~ **Resolved** on 2026-11-04: stored coordinates ([FR-325]: navigation uses stored coordinates), and an address change clears the pin after a warning ([BR-104]: an address change geocodes again). | FR-14, FR-28, FR-56, BR-13 |
| Q-54 | ~~A refresh retried on weak signal.~~ **Resolved** on 2026-11-04 by [ADR-030](../ADRs/ADR-030-admin-issued-credentials-and-token-revocation.md) (Admin-issued credentials and token revocation): a 30-second grace ([NFR-178]: grace for a repeated refresh). | NFR-88, FR-70 |
| Q-55 | ~~How a vehicle, driver or user comes back.~~ **Resolved** on 2026-11-04: a dispatcher marks them available ([FR-300]: mark available again, [FR-301]: vehicle out before the day starts), and an Admin reactivates a user ([FR-297]: reactivate a user). | FR-07, FR-35, FR-53, FR-54, FR-82, BR-20 |
| Q-56 | ~~What FR-50 adds beyond [FR-04] (define depots) and [FR-05] (register vehicles).~~ **Resolved** on 2026-11-04: nothing, so FR-50 is withdrawn. | FR-04, FR-05 |
| Q-57 | ~~What the non-goal on payments covers.~~ **Resolved** on 2026-11-04: cash on delivery and billing for DispatchFlow ([Non-goals](#non-goals)). | [Non-goals](#non-goals) |
| Q-58 | ~~What the 30 seconds and the solve limit include.~~ **Resolved** on 2026-11-04: [NFR-07] (re-optimization response time) runs from the request to the preview on screen, the limit of [NFR-06] (solve time limit range) covers the search only, and an open preview blocks nothing and has no age limit ([BR-38]: one re-optimization at a time, [BR-25]: re-check when a re-optimization is accepted). | BR-25, BR-32, BR-38, FR-19, NFR-06, NFR-07, NFR-25, NFR-57 |
| Q-59 | ~~Does IS 5568, the Israeli web-accessibility standard ([BOIA](https://www.boia.org/blog/israels-digital-accessibility-laws-an-overview)), apply beyond public pages?~~ **Resolved** on 2026-11-13: nothing changes in what is built, since WCAG 2.2 level AA ([NFR-46]: WCAG accessibility level) covers the standard's technical bar. Whether its legal duties, such as an accessibility statement, reach the apps joins the legal advice of [Q-42] (legal advice on personal data); they are assumed to apply to [FR-117] (customer live tracking page). | NFR-46, FR-117 |
| Q-60 | ~~How may pilot coordinates be used in research, given that they identify a home and a replay ([FR-46]: disruption simulation) needs them?~~ **Resolved** on 2026-11-13: no ethics approval is needed, for the usability study ([FR-48]: usability study) or for pseudonymized pilot data ([FR-47]: pilot at the company). Pilot data stays pseudonymized and out of the repository ([NFR-50]: pilot data pseudonymized, [NFR-122]: results kept with their configuration). | FR-43, FR-46, FR-47, FR-48, NFR-153, FR-168, FR-276 |
| Q-62 | ~~What monthly budget will the company accept?~~ **Resolved** on 2026-11-13, on our side, as two caps: hosting at most ₪550 a month with VAT ([NFR-52]: monthly hosting cost), and SMS paid per message while messages are on, about ₪550 to ₪850 a month with VAT at 150 deliveries a day ([NFR-254]: SMS cost). The hosting cap was first ₪450 and was raised to ₪550 on 2026-11-14, once VAT was counted. **Waiting for** the company's yes to both. DispatchFlow itself is free, and the company pays its own running costs (decided 2026-11-11). Suggested owner: Team, the company. | NFR-52, NFR-254, FR-49 |
| Q-63 | ~~Which Musts can four people deliver by June 2027, when the timeline on slide 12 of the [initial presentation](../presentations/proposal/DispatchFlow_Proposal_Presentation.pptx) ends?~~ **Resolved** on 2026-11-13: every Must stays, and the team checks them at the Alpha review on 12 January. It compares the weeks M0 to M5 really took with the planned ones, and lowers the Semester B Musts that no longer fit. [FR-13] (cache geocoding results), [FR-21] (fixed cost per vehicle used) and [FR-47] (pilot at the company) stay Must. The roadmap records the checkpoint. Dropping a solver row, such as [FR-42] (our own solver), still needs an ADR that supersedes [ADR-005](../ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md) (OR-Tools first with our solver as research) and [ADR-004](../ADRs/ADR-004-pluggable-solver-port.md) (every solver behind one port). | FR-13, FR-20, FR-21, FR-42 to FR-47 |
| Q-64 | ~~Which record wins when a report arrives late.~~ **Resolved** on 2026-11-04: the first recorded wins, a dispatcher resolves a conflict ([BR-88]: when a late outcome is refused, [BR-90]: contradicting reports are refused, [FR-333]: dispatcher corrects an outcome), and the route's history decides ([BR-110]: late reports judged by route history). | BR-18, BR-27, BR-28, BR-30, FR-58, FR-70, FR-73, BR-88, BR-90 |
| Q-65 | ~~What a hand-over allows.~~ **Resolved** on 2026-11-04: a free, active, available driver ([FR-302]: who can take a handed-over route), started routes too ([FR-303]: hand over a started route), and a shift break flagged for the dispatcher to confirm ([FR-304]: hand-over that breaks a shift). | FR-27, FR-55, BR-06, BR-20, BR-39 |
| Q-66 | ~~Whether a put-back is a new order or a second stop.~~ **Resolved** on 2026-11-04: a second stop on the same order ([FR-258]: a put-back order gets a new stop, [BR-07]: each order served once or dropped). | FR-57, FR-58, FR-258, BR-07, BR-30, BR-33, BR-35 |
| Q-67 | ~~Reports queued when a session ends.~~ **Resolved** on 2026-11-04: they survive and are sent after the same user signs in ([FR-331]: queued reports survive sign-out); sign-out warns ([FR-330]: sign-out warning); a deactivated driver's are recorded by a dispatcher ([FR-298]: a deactivated driver's reports go unsent); and revocation wipes route data ([NFR-179]: phone data wiped on revocation). | FR-53, FR-248, BR-27, NFR-22, NFR-98, FR-123, FR-127 |
| Q-68 | ~~Order changes on ready, solving and published plans.~~ **Resolved** on 2026-11-04: [FR-315] (date change on a published plan), [FR-316] (order changes on a ready plan) and [FR-317] (order change during a solve). After *arrived*, no change or cancel ([BR-92]: no order change after arrival). | FR-57, FR-58, BR-07, BR-12, BR-21, BR-22, BR-30, FR-206 |
| Q-69 | ~~Which phone-app rows bind the web build.~~ **Resolved** on 2026-11-04: it is online only, as [FR-326] (the web app for drivers) says. **Changed** on 2026-11-11: FR-326 now covers the web app, and the phone app's web build serves only development and tests ([NFR-220]: the phone app's web build). | FR-69, FR-70, FR-71, NFR-15, NFR-39, FR-155, NFR-61, NFR-71, NFR-73, NFR-132 |
| Q-70 | ~~What counts toward the 200 stops, and where the limit is refused.~~ **Resolved** on 2026-11-04: [BR-113] (what the order limit counts). No new drop reason is needed. | BR-22, BR-101, FR-10, FR-11, FR-18, FR-75, NFR-05 |
| Q-71 | ~~The route lifecycle's gaps.~~ **Resolved** on 2026-11-04: [FR-327] (first report starts the route), [FR-328] (location stops after shift end), [FR-329] (finish with stops pending), [FR-324] (routes for later dates) and [BR-108] (nothing added to finished routes). A finished route has no bearing on a plan's completion ([BR-102]: plans complete at midnight). | FR-29, FR-31, FR-33, FR-39, FR-68, NFR-30, Q-05, FR-27, FR-152, FR-267 |
| Q-72 | ~~Changing or retiring a record in use.~~ **Resolved** on 2026-11-04: [FR-305] (records in use cannot be retired), [FR-306] (changed records apply to later plans) and [FR-307] (what retiring a record does). | FR-53, FR-54, FR-56, FR-60, BR-20, BR-21, BR-25, BR-36, BR-37 |
| Q-73 | ~~Values the proposed rows left open.~~ **Resolved** on 2026-11-04: each row now states its value. The sample strings of [NFR-136] (mixed-direction text) belong to its test, and the period of [NFR-33] (ETA accuracy target) and the start of [NFR-34] (time to a published plan) follow [Q-40] (pilot protocol). | FR-12, FR-172, FR-64, FR-70, FR-101, FR-155, BR-80, BR-31, NFR-27, NFR-28, NFR-29, NFR-31, NFR-33, NFR-34, NFR-35, NFR-95, NFR-37, NFR-129, NFR-44, NFR-137, NFR-138, NFR-46, NFR-47, NFR-49, NFR-135, NFR-53, NFR-75, Q-08, NFR-96, NFR-102, NFR-125, NFR-128, NFR-134, NFR-136, NFR-142, NFR-150, NFR-164 |
| Q-76 | ~~Who takes part in usability sessions before the company is confirmed.~~ **Resolved** on 2026-11-04: stand-ins on synthetic data, then the company's dispatcher ([FR-344]: stand-in usability sessions). | FR-48, Q-60 |
| Q-77 | ~~How much acknowledged data a lost VM may lose.~~ **Resolved** on 2026-11-04 by [ADR-027](../ADRs/ADR-027-proof-files-on-the-vm-disk-with-hourly-backups.md) (proof files on the VM disk): at most an hour during operating hours ([NFR-28]: scheduled off-VM backups). | NFR-28, NFR-64, FR-70 |
| Q-78 | ~~Toll roads.~~ **Resolved** on 2026-11-04: allowed, with no cost counted ([FR-323]: toll roads). Reopen if the company objects to its toll bills. | FR-90, FR-118, BR-09 |
| Q-79 | ~~Depot hours and loading.~~ **Resolved** on 2026-11-04: opening hours only ([FR-129]: depot opening hours). Reopen if vehicles queue at the depot. | FR-67, FR-129, BR-46 |
| Q-80 | ~~Per-vehicle limits beyond the shift.~~ **Resolved** on 2026-11-04: not needed yet, so [FR-133] (per-vehicle route limits) stays a Could. Reopen when the company names a vehicle that needs one. | FR-133 |
| Q-81 | ~~A second sign-in factor for Admins.~~ **Resolved** on 2026-11-04: not for the pilot, so [FR-128] (second sign-in factor for Admins) stays a Could. ASVS 5.0 item 6.3.3 is Level 2, so the Level 1 check of [NFR-40] (OWASP ASVS self-check) does not require it. | FR-128, NFR-40 |
| Q-82 | ~~Browsers and screen sizes.~~ **Resolved** on 2026-11-04: Chrome and Edge ([NFR-67]: supported browsers). Reopen if the company's dispatcher uses something else. | NFR-67, NFR-58 |
| Q-83 | ~~Addresses in Arabic or transliteration.~~ **Resolved** on 2026-11-04: a miss is flagged for a hand-placed pin ([FR-63]: doubtful addresses flagged before a solve), the test of [Q-02] (geocoding provider) includes them, and [FR-140] (Hebrew address normalization) stays Hebrew only. | FR-12, FR-140, Q-02 |
| Q-84 | ~~The statistical method.~~ **Resolved** on 2026-11-04, not yet agreed with the academic advisor: a Wilcoxon signed-rank test, level 0.05, rank-biserial effect size ([FR-165]: paired statistical tests, [FR-288]: significance level). **Changed** on 2026-11-13 by [Q-93] (study design gaps): the test is paired and one-sided, a test of non-inferiority. | FR-165, NFR-24, NFR-25 |
| Q-85 | ~~Moving stops into a route left out.~~ **Resolved** on 2026-11-04: never, and the limited mode is not in the study ([FR-159]: re-optimize chosen routes only). | FR-159, NFR-25 |
| Q-86 | ~~The conditions behind the performance values.~~ **Resolved** on 2026-11-04: [NFR-181] (how latency targets are measured). None has been measured yet. | NFR-57, NFR-58, NFR-59, NFR-60, NFR-61, NFR-109 |
| Q-87 | ~~Who sees a driver's home address.~~ **Resolved** on 2026-11-04: [FR-308] (privacy of a route's home address), if [FR-169] (routes from a driver's own address) is built. | FR-169, Q-42 |
| Q-88 | ~~A stand-in for an unknown volume.~~ **Resolved** on 2026-11-04: none, a blank counts as zero ([BR-50]: capacity and demand values), and the count of [FR-136] (package count) is only shown to the driver. **Changed** on 2026-11-11: a blank weight or volume is refused ([BR-50]: capacity and demand values). | FR-05, FR-09, FR-136, BR-05, Q-11 |
| Q-89 | ~~An access token outliving its session.~~ **Resolved** on 2026-11-04 by [ADR-030](../ADRs/ADR-030-admin-issued-credentials-and-token-revocation.md) (Admin-issued credentials and token revocation): ASVS 5.0 items 7.4.1 and 7.4.2 are Level 1, so the server refuses it at once ([NFR-177]: revoked tokens refused at once). | NFR-86, NFR-40, FR-173, FR-180, FR-188 |
| Q-90 | ~~The company's real fleet size and daily stops.~~ **Resolved** on 2026-11-04: the limits of 15 vehicles and 200 stops are final ([NFR-04]: fleet size limit, [NFR-05]: plan size limit). A larger company reopens them, and [NFR-07] (re-optimization response time) with them. | NFR-04, NFR-05, NFR-07, BR-36, [Non-goals](#non-goals), BR-101 |
| Q-91 | ~~Regaining control after losing the only Admin.~~ **Resolved** on 2026-11-04 by [ADR-030](../ADRs/ADR-030-admin-issued-credentials-and-token-revocation.md) (Admin-issued credentials and token revocation): the company keeps two active Admins ([BR-19]: the minimum of active Admins, [FR-299]: warning below the Admin minimum), and a named team member can run an audited server step on its written request ([FR-292]: Admin created on a written request). | FR-174, BR-19, NFR-166 |
| Q-92 | ~~Security and privacy gaps from the review of 2026-11-04.~~ **Resolved** on 2026-11-13: (1) a restore revokes every session, the runbook re-applies the data requests and opt-outs made since the backup, and messages stay off until an Admin turns them on ([NFR-252]: a restore revokes sessions, [NFR-253]: restore runbook re-applies requests); (2) every backup is encrypted, with its key readable by two named members ([NFR-251]: backup encryption); (3) queued reports and proofs are wiped with the route data after 14 days offline ([NFR-235]: offline wipe of queued reports and route data); (4) any refused refresh wipes the route data and asks the driver to sign in again, and the outbox is kept ([NFR-234]: refused refresh wipes route data), which is how a phone learns it was revoked, given [NFR-92] (uniform refresh refusals); (5) web sign-out sends Clear-Site-Data and deletes the refresh cookie ([NFR-233]: web sign-out clears storage and cookie); (6) push tokens are removed at sign-out, at revocation and when the push service reports the device gone, and the push service's access token is on ([NFR-263]: push token removal, [NFR-264]: push service access token); (7) a leaving member's access is removed that day and their secrets are rotated, and AWS Systems Manager (SSM) records who opened each session on the VM and when, with no session stream, so the reads inside one stay unaudited ([NFR-228]: leaver's access removed and secrets rotated, [NFR-229]: server sessions logged); (8) a corrected time later than the server's receipt is cut to the receipt time ([BR-110]: late reports judged by route history); (9) location stops for the previous driver at a hand-over and when a driver is marked unavailable ([NFR-30]: location collected only on a route); (10) the 30 days of [NFR-150] (export then deletion when the company leaves) start when the export is delivered; (11) [NFR-19] (no personal data in logs) also names coordinates, recipients' names and phones, and entrance codes. **Changed** on 2026-11-14: a restore also re-applies deactivations and role removals from records that carry only IDs and keyed hashes ([NFR-253]: restore runbook re-applies requests), blanks the password of each account whose password or email changed ([NFR-274]: restore blanks changed passwords), voids every open credential link ([NFR-275]: restore voids credential links), and rotates the access-token signing key, which it stops accepting at once ([NFR-272]: restore rotates the signing key); the restore step issues set-password links to the Admins, who then confirm the other accounts before their links go out ([NFR-282]: restore issues links to Admins, [NFR-283]: Admins confirm accounts before other links); the backup secrets are under a customer-managed key ([NFR-273]: key for the backup secrets), and the VM cannot delete backups ([NFR-266]: backups the VM cannot delete); every account that can deploy or release the app needs a second factor ([NFR-265]: second factor for deploy and release accounts); an open event stream re-checks its session ([NFR-267]: stream re-checks its session); alert text carries no personal data ([NFR-268]: no personal data in alert text); the web app drops its memory on revocation ([NFR-269]: web app drops memory on revocation); server steps read a person's identifier at a prompt that does not echo it ([FR-393]: non-echoing prompt for identifiers) and send exports to an encrypted file ([FR-409]: audit and exports of server steps); and a backdated report is held ([BR-132]: backdated report held). | NFR-125, NFR-28, FR-331, NFR-39, NFR-179, NFR-98, BR-110, NFR-30, NFR-150, NFR-185, NFR-19 |
| Q-93 | ~~Study design gaps from the review of 2026-11-04.~~ **Resolved** on 2026-11-13 ([ADR-091]: study design for the solver comparison); the answers are to be confirmed with the advisor before the first measured run, and Q-29, Q-30 and Q-84 stay to confirm with the advisor. (1) The drop unit is K = 5L + 2T + F + 1, times (6 − priority) ([BR-126]: drop penalty unit). (2) OR-Tools solves the 30 plans at 60 seconds on seeded synthetic days ([NFR-244]: how the plans are solved); the plans are stored with the study's inputs, hashed in the run manifest and read on resume and rerun ([NFR-270]: the study's stored plans). (3) Insertion times are drawn from the seed across the plan's driving hours ([NFR-245]: insertion times drawn from the seed). (4) Each insertion is separate, from the same plan ([NFR-246]: separate insertions). (5) Research instances are exempt from [BR-101] (the order limit), as [FR-390] (benchmark demands read as weights) exempts them from [BR-50] (capacity and demand values). (6) The exclusions of [NFR-118] (which insertions count) are counted and reported ([NFR-243]: excluded insertions counted). (7) The test is paired and one-sided, a test of non-inferiority: [NFR-24] (static quality target) on (gap − 0.05) and [NFR-117] (re-optimization quality target) on (ours − OR-Tools − margin), the margin being the 1% that row states, with a primary limit of 5 seconds; it replaces the plain test of Q-84, and [FR-165] (paired statistical tests) says so. (8) 60 seconds and 10 runs also bind Gehring-Homberger and PyVRP ([NFR-242]: limits that bind the other benchmarks), and [FR-44] (scaling study) runs 3 times per size ([FR-406]: scaling study runs per size). (9) [NFR-120] (runs at other time limits) adds the 20-second limit. (10) A crashed study resumes ([NFR-247]: a crashed study resumes). (11) Under [BR-106] (goods stay on their vehicle), stability varies only through drops, so the stability fallback of [ADR-005](../ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md) (OR-Tools first with our solver as research) now rests on drops and lateness under short limits; if its trigger fires, the ADR that answers it uses this. Suggested owner: Naim, advisor. | BR-54, NFR-110, NFR-182, BR-101, NFR-118, FR-165, FR-44, NFR-24, NFR-111, NFR-120, BR-84, NFR-121 |
| Q-94 | ~~Lifecycle and testing gaps from the review of 2026-11-04.~~ **Resolved** on 2026-11-13: (1) a stop unreported when its plan completes leaves its order *planned*, the summary shows it as *not reported*, and a late report or a dispatcher's record settles it ([BR-129]: stops unreported at midnight); (2) correcting a failed stop to *delivered* after a put-back is allowed while the new stop has no outcome, and that stop then leaves its route, audited; otherwise the correction is refused and names the conflict ([FR-404]: correcting a failed stop after a put-back); (3) the confirmed return time of a hand-over past the shift becomes that day's shift end, and a re-optimization cannot stretch it ([FR-394]: return time after a confirmed hand-over); (4) what normalized means is in [FR-398] (normalized address text for matching); (5) alert delivery on phones is measured from the server's send to the alert on screen, and an offline phone shows waiting alerts within 60 seconds of reconnecting while the app is open ([NFR-240]: how alert delivery time is measured on phones); (6) the load test's mix and error definition are in [NFR-237] (phone mix of the load test), [NFR-238] (web mix of the load test) and [NFR-239] (error definition of the load test), and whether k6's SSE extension holds the streams is verified when the load test is built (M13); (7) mid-range devices are defined in [NFR-241] (what mid-range means). | BR-102, BR-33, FR-333, FR-258, FR-304, FR-310, NFR-180, NFR-10, NFR-181 |
| Q-95 | What consent do messages to recipients need? Is the rating request ([FR-356]: delivered message with a rating request) advertising under the spam law (section 30A of the Communications Law), which needs prior written consent, and can its exception for existing customers cover a carrier? Must recipients get a privacy notice, and how, without links? Is asking for a rating a new purpose for a phone given for delivery? Must the pilot agreement name the SMS provider as a processor of phone numbers ([Q-42]: legal advice on personal data)? Is removing an opt-out at the person's request by phone ([FR-380]: opt-outs seen and recorded by dispatchers) enough, or must it be in writing? No ruling on survey messages was found on 2026-11-10. **Default until answered** (2026-11-10): delivery messages need no consent, nobody turns the rating question on ([FR-375]: the rating question's switch), and "הסר" or "STOP" stops every message. **Decided on 2026-11-13:** the default stands, and the legal points go to the advice of [Q-42] (legal advice on personal data) before messages reach real recipients. Owner: Naim Elijah, through that advice. | FR-49, FR-356, FR-357, FR-363, FR-375, Q-42 |
| Q-96 | Which SMS provider? It needs an API over TLS only, a two-way virtual number, Hebrew in Unicode, where one message holds 70 characters, callbacks it signs or sends with a secret in a header ([NFR-192]: SMS provider callbacks), dropping repeated sends by a reference DispatchFlow gives ([NFR-198]: unknown sends not repeated), resending callbacks that are not acknowledged ([NFR-201]: callbacks acknowledged after storing), a spend limit, personal console accounts with a second factor, retention of message content within [NFR-184] (default retention periods) or deletion on request, and a statement of where it holds the data and which other parties receive it. Can its sender show the company's name and still take replies? Israeli price lists seen on 2026-11-10 ask ₪0.02 to ₪0.05 a message before VAT; the estimate of [NFR-254] (SMS cost) assumes 150 deliveries a day. Twilio asks $0.2575, so it was ruled out ([ADR-031](../ADRs/ADR-031-sms-through-an-israeli-provider-for-recipient-messages.md): SMS for messages to recipients). A signed agreement on its handling of personal data comes before messages reach real recipients, and until then the team builds against the fake provider ([NFR-206]: SMS credential only in production). **Decided on 2026-11-13:** the provider is chosen against this checklist when M12 starts. Owner: Team. | FR-49, NFR-254, NFR-192, NFR-198 |
| Q-97 | ~~How do an Admin's or a Dispatcher's alerts reach the phone app: push, or an open event stream?~~ **Resolved** on 2026-11-13 by [ADR-083] (Admin and Dispatcher alerts in the phone app), which extends [ADR-026](../ADRs/ADR-026-server-sent-events-and-push-for-live-updates.md) (server-sent events and push) for drivers' route alerts and [ADR-035](../ADRs/ADR-035-both-apps-serve-every-role.md) (both apps serve every role): the event stream while the app is open, and push while it is in the background. A push carries no personal data ([NFR-218]: no personal data in push notifications), and the app fetches the details ([FR-405]: alerts for Admins and Dispatchers in the phone app). | FR-120, FR-222, NFR-180 |
| Q-98 | ~~What may the apps keep on a device for the Admin and Dispatcher roles?~~ **Resolved** on 2026-11-13 by [ADR-076] (Admin and Dispatcher data stays in memory on devices): data received through those roles lives in memory only, caches included ([NFR-230]: Admin and Dispatcher data kept in memory); sign-out and revocation wipe everything the account received ([NFR-232]: phone wipe at sign-out of Admin and Dispatcher data, [NFR-236]: revocation wipes Admin and Dispatcher data); exports leave only through the share sheet ([NFR-231]: exports through the share sheet); and the web app, driver screens included, clears its storage at sign-out ([NFR-233]: web sign-out clears storage and cookie). This is settled before the self-check of [NFR-40] (OWASP ASVS self-check), since ASVS 14.3.1 is Level 1. | FR-120, NFR-39, NFR-98, NFR-179, FR-72, FR-107 |

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
[FR-28]: #publishing-and-the-phone-app
[FR-29]: #publishing-and-the-phone-app
[FR-30]: #publishing-and-the-phone-app
[FR-31]: #publishing-and-the-phone-app
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
[FR-68]: #publishing-and-the-phone-app
[FR-69]: #publishing-and-the-phone-app
[FR-70]: #publishing-and-the-phone-app
[FR-71]: #publishing-and-the-phone-app
[FR-72]: #publishing-and-the-phone-app
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
[FR-95]: #publishing-and-the-phone-app
[FR-96]: #publishing-and-the-phone-app
[FR-97]: #publishing-and-the-phone-app
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
[FR-121]: #publishing-and-the-phone-app
[FR-123]: #company-users-and-roles
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
[FR-152]: #publishing-and-the-phone-app
[FR-153]: #publishing-and-the-phone-app
[FR-157]: #live-operations-and-re-optimization
[FR-159]: #live-operations-and-re-optimization
[FR-165]: #research-and-evaluation
[FR-169]: #fleet-depots-and-shifts
[FR-172]: #company-users-and-roles
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
[FR-242]: #publishing-and-the-phone-app
[FR-243]: #publishing-and-the-phone-app
[FR-246]: #publishing-and-the-phone-app
[FR-248]: #publishing-and-the-phone-app
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
[FR-291]: #company-users-and-roles
[FR-292]: #company-users-and-roles
[FR-293]: #company-users-and-roles
[FR-294]: #company-users-and-roles
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
[FR-324]: #publishing-and-the-phone-app
[FR-325]: #publishing-and-the-phone-app
[FR-326]: #publishing-and-the-phone-app
[FR-327]: #publishing-and-the-phone-app
[FR-328]: #publishing-and-the-phone-app
[FR-329]: #publishing-and-the-phone-app
[FR-330]: #publishing-and-the-phone-app
[FR-331]: #publishing-and-the-phone-app
[FR-332]: #live-operations-and-re-optimization
[FR-333]: #live-operations-and-re-optimization
[FR-334]: #live-operations-and-re-optimization
[FR-335]: #live-operations-and-re-optimization
[FR-336]: #publishing-and-the-phone-app
[FR-337]: #publishing-and-the-phone-app
[FR-339]: #reporting-and-baseline
[FR-340]: #reporting-and-baseline
[FR-341]: #reporting-and-baseline
[FR-342]: #reporting-and-baseline
[FR-343]: #research-and-evaluation
[FR-344]: #research-and-evaluation
[FR-345]: #company-users-and-roles
[FR-346]: #company-users-and-roles
[FR-350]: #messages-to-recipients
[FR-351]: #messages-to-recipients
[FR-354]: #messages-to-recipients
[FR-356]: #messages-to-recipients
[FR-357]: #messages-to-recipients
[FR-360]: #messages-to-recipients
[FR-361]: #messages-to-recipients
[FR-363]: #messages-to-recipients
[FR-364]: #customers-and-orders
[FR-366]: #publishing-and-the-phone-app
[FR-372]: #messages-to-recipients
[FR-374]: #messages-to-recipients
[FR-375]: #messages-to-recipients
[FR-376]: #messages-to-recipients
[FR-377]: #messages-to-recipients
[FR-379]: #messages-to-recipients
[FR-380]: #messages-to-recipients
[FR-386]: #company-users-and-roles
[FR-387]: #company-users-and-roles
[FR-390]: #research-and-evaluation
[FR-393]: #company-users-and-roles
[FR-394]: #fleet-depots-and-shifts
[FR-395]: #fleet-depots-and-shifts
[FR-396]: #customers-and-orders
[FR-397]: #customers-and-orders
[FR-398]: #customers-and-orders
[FR-399]: #customers-and-orders
[FR-400]: #daily-planning
[FR-401]: #daily-planning
[FR-402]: #daily-planning
[FR-403]: #daily-planning
[FR-404]: #live-operations-and-re-optimization
[FR-405]: #live-operations-and-re-optimization
[FR-406]: #research-and-evaluation
[FR-407]: #research-and-evaluation
[FR-408]: #daily-planning
[FR-409]: #company-users-and-roles
[FR-410]: #research-and-evaluation
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
[BR-70]: #plan-lifecycle-and-publishing
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
[BR-123]: #access
[BR-126]: #solving-dropping-and-validation
[BR-127]: #plan-lifecycle-and-publishing
[BR-128]: #plan-lifecycle-and-publishing
[BR-129]: #orders-records-and-history
[BR-130]: #driver-reports-and-outcomes
[BR-131]: #orders-records-and-history
[BR-132]: #driver-reports-and-outcomes
[BR-133]: #driver-reports-and-outcomes
[BR-134]: #driver-reports-and-outcomes
[BR-135]: #driver-reports-and-outcomes
[BR-136]: #driver-reports-and-outcomes
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
[NFR-62]: #nfr-62
[NFR-67]: #nfr-67
[NFR-72]: #nfr-72
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
[NFR-132]: #nfr-132
[NFR-135]: #nfr-135
[NFR-136]: #nfr-136
[NFR-137]: #nfr-137
[NFR-138]: #nfr-138
[NFR-139]: #nfr-139
[NFR-140]: #nfr-140
[NFR-148]: #nfr-148
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
[NFR-188]: #nfr-188
[NFR-190]: #nfr-190
[NFR-192]: #nfr-192
[NFR-196]: #nfr-196
[NFR-198]: #nfr-198
[NFR-201]: #nfr-201
[NFR-206]: #nfr-206
[NFR-209]: #nfr-209
[NFR-213]: #nfr-213
[NFR-214]: #nfr-214
[NFR-216]: #nfr-216
[NFR-218]: #nfr-218
[NFR-219]: #nfr-219
[NFR-220]: #nfr-220
[NFR-228]: #nfr-228
[NFR-229]: #nfr-229
[NFR-230]: #nfr-230
[NFR-231]: #nfr-231
[NFR-232]: #nfr-232
[NFR-233]: #nfr-233
[NFR-234]: #nfr-234
[NFR-235]: #nfr-235
[NFR-236]: #nfr-236
[NFR-237]: #nfr-237
[NFR-238]: #nfr-238
[NFR-239]: #nfr-239
[NFR-240]: #nfr-240
[NFR-241]: #nfr-241
[NFR-242]: #nfr-242
[NFR-243]: #nfr-243
[NFR-244]: #nfr-244
[NFR-245]: #nfr-245
[NFR-246]: #nfr-246
[NFR-247]: #nfr-247
[NFR-248]: #nfr-248
[NFR-249]: #nfr-249
[NFR-250]: #nfr-250
[NFR-251]: #nfr-251
[NFR-252]: #nfr-252
[NFR-253]: #nfr-253
[NFR-254]: #nfr-254
[NFR-255]: #nfr-255
[NFR-256]: #nfr-256
[NFR-257]: #nfr-257
[NFR-258]: #nfr-258
[NFR-259]: #nfr-259
[NFR-260]: #nfr-260
[NFR-261]: #nfr-261
[NFR-262]: #nfr-262
[NFR-263]: #nfr-263
[NFR-264]: #nfr-264
[NFR-265]: #nfr-265
[NFR-266]: #nfr-266
[NFR-267]: #nfr-267
[NFR-268]: #nfr-268
[NFR-269]: #nfr-269
[NFR-270]: #nfr-270
[NFR-271]: #nfr-271
[NFR-272]: #nfr-272
[NFR-273]: #nfr-273
[NFR-274]: #nfr-274
[NFR-275]: #nfr-275
[NFR-276]: #nfr-276
[NFR-277]: #nfr-277
[NFR-278]: #nfr-278
[NFR-279]: #nfr-279
[NFR-280]: #nfr-280
[NFR-281]: #nfr-281
[NFR-282]: #nfr-282
[NFR-283]: #nfr-283
[NFR-284]: #nfr-284
[NFR-285]: #nfr-285
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
[Q-92]: #open-questions
[Q-93]: #open-questions
[Q-95]: #open-questions
[Q-96]: #open-questions
[ADR-041]: ../ADRs/ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md
[ADR-048]: ../ADRs/ADR-048-travel-times-from-buckets-and-a-buffer-with-no-live-traffic.md
[ADR-049]: ../ADRs/ADR-049-drop-penalty-unit-k-scaled-by-priority.md
[ADR-050]: ../ADRs/ADR-050-re-optimization-conventions-live-only-in-the-python-problem-factory.md
[ADR-051]: ../ADRs/ADR-051-one-solver-worker-per-lane-killed-30-seconds-past-its-limit.md
[ADR-052]: ../ADRs/ADR-052-each-plan-stores-the-matrix-and-values-it-was-solved-with.md
[ADR-053]: ../ADRs/ADR-053-plan-lifecycle-publishing-stability-drop-reasons-and-abandoned-plans.md
[ADR-054]: ../ADRs/ADR-054-publishing-re-checks-every-route-against-the-current-fleet.md
[ADR-055]: ../ADRs/ADR-055-accepting-a-re-optimization-checks-the-vehicles-it-keeps-driving.md
[ADR-056]: ../ADRs/ADR-056-accepting-a-re-optimization-checks-changes-recorded-since-its-basis.md
[ADR-057]: ../ADRs/ADR-057-concurrent-changes-to-a-plan-use-optimistic-locking.md
[ADR-058]: ../ADRs/ADR-058-routes-changed-while-the-optimizer-is-down-are-checked-when-it-returns.md
[ADR-060]: ../ADRs/ADR-060-hard-limits-on-fleet-and-plan-size.md
[ADR-061]: ../ADRs/ADR-061-capacity-is-weight-and-volume.md
[ADR-062]: ../ADRs/ADR-062-fleet-checks-the-driver-role-when-it-reads.md
[ADR-065]: ../ADRs/ADR-065-facts-outrank-intent.md
[ADR-067]: ../ADRs/ADR-067-live-arrivals-propagate-delay-along-stored-legs-when-asked.md
[ADR-068]: ../ADRs/ADR-068-every-web-handler-is-authorized-by-role-from-the-contract.md
[ADR-069]: ../ADRs/ADR-069-sign-in-and-transport-security.md
[ADR-070]: ../ADRs/ADR-070-security-and-accessibility-standards.md
[ADR-071]: ../ADRs/ADR-071-api-rate-limits-in-process-with-bucket4j.md
[ADR-072]: ../ADRs/ADR-072-images-scanned-by-pinned-trivy-and-secrets-by-gitleaks.md
[ADR-073]: ../ADRs/ADR-073-runtime-contract-validation.md
[ADR-074]: ../ADRs/ADR-074-dependency-licences-checked-against-an-allow-list.md
[ADR-075]: ../ADRs/ADR-075-retention-deletes-records-and-anonymizes-people-in-place.md
[ADR-076]: ../ADRs/ADR-076-admin-and-dispatcher-data-stays-in-memory-on-devices.md
[ADR-077]: ../ADRs/ADR-077-push-builds-and-app-stores-are-accepted-exceptions-to-hosting-in-israel.md
[ADR-078]: ../ADRs/ADR-078-hosting-on-one-aws-vm-in-tel-aviv-built-with-terraform.md
[ADR-079]: ../ADRs/ADR-079-client-errors-and-crashes-go-to-self-hosted-glitchtip.md
[ADR-080]: ../ADRs/ADR-080-team-accounts-stand-in-when-there-is-no-company.md
[ADR-082]: ../ADRs/ADR-082-supported-phone-app-versions.md
[ADR-083]: ../ADRs/ADR-083-admins-and-dispatchers-get-alerts-by-stream-and-push-in-the-phone-app.md
[ADR-084]: ../ADRs/ADR-084-maplibre-in-both-apps-with-self-hosted-protomaps-tiles.md
[ADR-085]: ../ADRs/ADR-085-govmap-geocodes-addresses-with-nominatim-as-the-fallback.md
[ADR-086]: ../ADRs/ADR-086-csv-imports-upload-then-confirm-within-an-hour.md
[ADR-087]: ../ADRs/ADR-087-driver-reports-sync-through-an-idempotent-outbox.md
[ADR-088]: ../ADRs/ADR-088-israel-local-time-for-every-date-and-schedule.md
[ADR-089]: ../ADRs/ADR-089-interface-languages.md
[ADR-090]: ../ADRs/ADR-090-scope-cuts-for-the-first-release.md
[ADR-091]: ../ADRs/ADR-091-study-design-for-the-solver-comparison.md
[ADR-100]: ../ADRs/ADR-100-k-covers-door-windows-outside-a-shift-and-no-vehicle-is-a-drop-reason.md
[ADR-102]: ../ADRs/ADR-102-order-references-are-checked-on-every-move-to-a-date.md
[ADR-104]: ../ADRs/ADR-104-the-company-profile-holds-customer-defaults-and-capabilities-follow-the-role-matrix.md
[ADR-105]: ../ADRs/ADR-105-a-re-optimizations-problem-carries-its-conventions-and-only-a-vehicle-that-is-out-has-no-driver.md
[ADR-116]: ../ADRs/ADR-116-only-an-admin-changes-their-own-email.md
[ADR-126]: ../ADRs/ADR-126-jpa-replaces-jooq-and-the-domain-carries-its-mapping.md
[ADR-145]: ../ADRs/ADR-145-the-migrate-command-runs-flyway-as-the-owner-and-the-core-connects-as-app.md

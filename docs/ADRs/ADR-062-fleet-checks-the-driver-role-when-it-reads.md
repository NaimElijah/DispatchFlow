# ADR-062: Fleet asks Access for the Driver role each time it reads a driver, and keeps no copy

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

On 2026-11-13 the owner, Naim Elijah, decided for the team the rule that the requirements mark "ADR
pending: fleet checks the driver role when it reads". It settles Q-27 ([Q-27]): when a Driver's role
changes, what happens to their driver record in fleet?

A person has one account that can hold several roles ([ADR-036], [FR-293]). A driver record in Fleet
has the ID of the driver's account: the two share one identity ([DL §2.2][DL-22]). Roles live on
the account, in Access. Fleet owns the record: name, phone, weekly shifts and retirement.

An Admin can remove the Driver role from an account while its record still exists, and may give it
back. Retiring a driver goes the other way: it removes the Driver role from the account and keeps its
other roles ([FR-307]). A driver record cannot be deleted while any plan refers to it ([BR-43]).

A driver may drive at most one vehicle on a date, and a vehicle has at most one driver ([BR-39],
[FR-54]). A route is published only while its driver holds the Driver role ([BR-20]), and a route can
be handed over only to a driver who holds it ([FR-302]).

Changing an account's roles revokes its sessions at once ([ADR-036], [NFR-90]). The domain document
leaves a role change out of its events: the revocation event covers it, and nothing else reacts to
roles ([DL §7.4][DL-74]).

## Decision

- **Fleet keeps no copy of the role.** A `Driver` holds the account's ID, and Fleet asks Access's
  `Accounts` query whether that account is active and holds the Driver role, each time it reads
  ([DL §2.2][DL-22]).
- **While the account is not a Driver, Fleet stops counting the record,** and another driver may take
  its vehicle ([Q-27]). The record is not deleted or retired by this. It counts again when the
  account holds the role again.
- **Fleet's methods require the account's standing as an argument.** `Driver.register` and
  `DayRoster.assign` take it, and refuse with `NotAnActiveDriver` when the account is not an active
  Driver ([DL §4.2][DL-42]).
- **Planning asks the same question** where it relies on the role: a hand-over reads the new
  driver's standing ([FR-302]), and publishing reads each route's roster answer ([BR-20],
  [PL §13.2][PL-132]).
- **No event carries a role change to Fleet,** and Fleet has no listener for one ([DL §7.4][DL-74]).

## Consequences

- **Easy:** the role has one owner, Access, so Fleet cannot disagree with it. A role given back
  needs no repair, because nothing was changed in Fleet.
- **Easy:** no listener, no resubmission and no watcher for a role change ([DL §7.4][DL-74],
  [DL §12][DL-12]).
- **Hard:** each read of drivers in Fleet depends on an answer from Access. Tests of Fleet's rules
  need an `Accounts` answer for every case.
- **Hard:** every Fleet read that counts drivers must go through the same check. A read that skips it
  would count a record that should not count.
- **Cost:** a read across modules on every roster read. Planning's use cases read standings before
  the transaction and pass them in as values ([DL §4.4][DL-44]).
- **Risk:** a route already published whose driver then loses the role is not covered by this rule.
  [BR-20] applies at publishing, and a published route changes only by a named action, a hand-over
  among them ([BR-47]).

## Alternatives considered

- **Fleet listens for the role change and updates its record.** Rejected: each event is a contract
  another module depends on, and a role change already raises the revocation event. The design found
  that nothing else reacts to roles ([DL §7.4][DL-74], [DL §16][DL-16]).
- **Fleet stores the role on the driver record.** Rejected: it is a second copy that must be kept in
  step with the account's roles, and the register would need to be written whenever an account
  changes.
- **Delete the driver record when the role is removed.** Rejected: a record any plan refers to is
  retired, never deleted ([BR-43]), and the role may be given back.
- **Refuse to remove the Driver role while the record is in use.** Not adopted: the sources state
  no such rule for an Admin's change of roles. Retiring a driver while a plan not yet completed uses
  them is refused with `DriverInUse` ([DL §4.2][DL-42]).

## Revisit when

- A pilot Admin removes the Driver role from an account that a published plan still routes, and the
  dispatcher is not told. Then add the rule that tells them, or refuse the change.
- Reading Accounts on every roster read shows up in the measurements of [NFR-08] (edit-check
  latency) or the API load test ([NFR-10]). Then cache the answer with an expiry, or read it in one
  query for the whole roster.

[ADR-036]: ADR-036-one-account-per-person-signed-in-by-email.md
[BR-20]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-39]: ../requirements/requirements.md#routing-constraints
[BR-43]: ../requirements/requirements.md#orders-records-and-history
[BR-47]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[FR-54]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-293]: ../requirements/requirements.md#company-users-and-roles
[FR-302]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-307]: ../requirements/requirements.md#fleet-depots-and-shifts
[NFR-08]: ../requirements/requirements.md#nfr-08
[NFR-10]: ../requirements/requirements.md#nfr-10
[NFR-90]: ../requirements/requirements.md#nfr-90
[Q-27]: ../requirements/requirements.md#open-questions
[DL-12]: ../architecture/domain_layer/domain_layer_design.md#12-who-notices-when-an-asynchronous-step-doesnt-happen
[DL-16]: ../architecture/domain_layer/domain_layer_design.md#16-why-it-looks-like-this
[DL-22]: ../architecture/domain_layer/domain_layer_design.md#22-relationships
[DL-42]: ../architecture/domain_layer/domain_layer_design.md#42-fleet
[DL-44]: ../architecture/domain_layer/domain_layer_design.md#44-planning
[DL-74]: ../architecture/domain_layer/domain_layer_design.md#74-considered-and-left-out
[PL-132]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#132-publish

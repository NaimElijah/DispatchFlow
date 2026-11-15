# ADR-103: Fleet normalizes plates, keeps one pair of depot hours, holds no vehicle in weekly shifts, and retires and anonymizes drivers through events

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

The Fleet domain is built ahead of its milestone ([DL §4.2][DL-42]). Six of its rules were left
open by both the requirements and the domain document.

A person has one account that can hold several roles ([ADR-036]). A driver record in Fleet has the
account's ID, and Fleet asks Access each time whether that account is active and holds the Driver
role, keeping no copy ([ADR-062]). Roles and accounts belong to Access ([DL §4.1][DL-41]), and Fleet
is downstream of Access ([DL §2.1][DL-21]). [DL §2.3][DL-23], rule 2, lets a downstream module call
an upstream API only from a listener on its own event, so that a failed call is retried. With that
in place, the six open rules are:

- **Retiring a driver.** [FR-307] says retiring a driver removes the Driver role from their account,
  which keeps its other roles. [DL §7.2][DL-72] listed no Fleet event.
- **Anonymizing a driver with their account.** Accounts of any role are anonymized in place a year
  after deactivation ([NFR-258]), and a retired driver's record a year after retirement
  ([NFR-184]). A driver whose account is deactivated but whose record is never retired would keep
  their name and phone in Fleet after Access has removed them from the account.
- **Plates.** [FR-131] and [BR-48] keep a licence plate unique among active vehicles, but no row
  says what a plate is. Without a normal form, `12-345-67` and `1234567` would be two plates.
- **Depot hours.** [FR-129] lets a depot have opening hours, and [BR-46] makes routes keep to them.
  Neither row says whether the hours differ by day.
- **Weekly shifts.** [FR-79] has a weekly template fill the per-date values of [FR-54] that a
  dispatcher has not set. [FR-06] and [FR-189] already give each driver a default shift and vehicle.
  Neither row says whether the template carries a vehicle.
- **A default that can't be used.** A driver's default vehicle may have been retired, or another
  driver may already have it on the date; the driver may no longer be active; and a shift may fall
  in the hour the clock skips. FR-79 says nothing about what a fill then does.

Every date and time is Israel local time ([ADR-088]), so a shift's hours meet the clock changes:
26 March 2027 has no 02:00 to 02:59, and on 31 October 2027 01:00 to 01:59 happens twice ([BR-111]).

## Decision

On 2026-11-14 the owner, Naim Elijah, decided the six rules:

1. **A retired driver loses the Driver role through an event.** `Driver.retire` raises a new event,
   `DriverRetired`, in `fleet.events`, carrying the driver's account ID and nothing personal. A
   listener in Fleet, added in a later milestone, calls Access's API to remove the Driver role
   ([FR-307], [DL §2.3][DL-23] rule 2). Fleet never models accounts or roles: it takes Access's
   answer on whether an account is an active driver as a value ([ADR-062]).
2. **A plate has one normal form.** The text is trimmed, its spaces and hyphens are dropped, and its
   Latin letters a to z are upper-cased. The result must be 1 to 10 ASCII letters or digits, and
   anything else is refused with `InvalidPlate`. So `12-345-67` and `1234567` are the same plate,
   and uniqueness among active vehicles compares the normal form ([BR-48], [FR-131]).
3. **Depot hours are one pair for every date.** `DepotHours` is one opening and one closing time in
   Israel that applies on every delivery date ([FR-129], [BR-46]). A closing time that is not after
   the opening time is refused with `InvalidDepotHours`. The times are wall-clock times, because the
   kernel's `DaySeconds` count from one date's midnight and these hold on every date. A depot may
   have no hours at all.
4. **Weekly shifts hold no vehicle.** `WeeklyShifts` gives each weekday at most one shift's hours, in
   weekday order. The driver's default vehicle ([FR-06]) fills the vehicle. The template fills only
   the per-date values a dispatcher has not set ([FR-79]).
5. **A driver record is anonymized with its account.** When Access anonymizes an account under
   [NFR-258], it raises Access's `AccountAnonymized` event ([ADR-104]). A Fleet listener,
   added in a later milestone, calls `Driver.anonymizeWithAccount`, which removes the driver's name
   and phone whatever the record's state, active or retired. An active driver anonymized this way is
   no longer active. The path from [NFR-184], a year after retirement, stays as it is.
6. **A roster fill skips what it can't use, fills the rest, and reports.** A driver whose default
   can't be used is skipped: the driver is not active, the default vehicle is retired, unknown or
   already another driver's on that date, or the shift falls in the hour the clock skips. Every
   other driver is filled. `DayRoster.fill` returns each skipped driver with its reason, as the
   named error the rule would refuse with, so the dispatcher sees who still needs setting by hand.

The Fleet domain applies points 4 and 6 as follows. A driver with no weekly shifts gives every date
their default shift; a driver with weekly shifts gives a weekday its shift, or nothing on a day
without one ([FR-189], [FR-79]). A roster entry's vehicle has three states: not set, no vehicle (a
dispatcher's choice, or a vehicle that left with a handed-over route), or a vehicle. A fill gives a
driver not yet on the roster their default shift and vehicle, and gives a driver already on it their
default vehicle only while the vehicle is not set; so a driver whose shift a dispatcher set first
still gets the default vehicle, and filling again changes nothing. Shift hours become a date's
`Shift` only on that date, so a time in the repeated hour reads as its first occurrence ([BR-111],
[FR-190]).

## Consequences

- **Easy:** the Driver role follows retirement reliably, without Fleet importing Access's types; a
  failed call is retried by the event registry ([ADR-064]).
- **Easy:** a driver's name and phone leave Fleet when they leave the account, whether or not the
  record was ever retired.
- **Easy:** two spellings of one plate can't both be active, and the plate a dispatcher sees is the
  same everywhere.
- **Easy:** one pair of depot hours is one row of input to the optimizer for every date.
- **Easy:** a driver who changes vehicle is updated once, in their default, and every weekday follows.
- **Easy:** one retired vehicle or one conflict does not block a whole date's roster.
- **Hard:** a depot whose hours differ by weekday, such as a short Friday, can't be modelled. The
  dispatcher sets that day's shifts instead.
- **Hard:** a depot or a shift open across midnight can't be modelled: both are times of one date,
  and [FR-114] is not built.
- **Hard:** a plate with a character other than an ASCII letter or digit, such as a Hebrew letter on
  a special plate, is refused.
- **Hard:** a driver who drives a different vehicle on some weekdays needs a dispatcher to set those
  dates by hand.
- **Hard:** a skipped driver is not on the roster until a dispatcher sets them, so the screen must
  show the fill's report.
- **Cost:** two listeners, one for `DriverRetired` and one for `AccountAnonymized`, which the
  milestone that adds Fleet's application layer builds.
- **Risk:** until the `DriverRetired` listener exists, a retired driver's account keeps the Driver
  role. Fleet itself still refuses them: `checkAssignable` and `fill` take the driver's standing,
  which combines Access's answer with the driver record's state, and a roster read that feeds a
  solve keeps only drivers whose standing is active ([ADR-062], [FR-193]).

## Alternatives considered

- **Fleet calls Access directly when retiring.** Not chosen: the call would sit in the same use case
  as the retirement, and a failed call would leave the two out of step with nothing to retry it.
- **Access removes the role when it hears about a retirement some other way.** Not chosen: Access is
  upstream and may not import a Fleet type ([DL §2.3][DL-23], rule 2).
- **Anonymize a driver only a year after retirement.** Not chosen: a driver whose account is
  anonymized but whose record was never retired would keep their name and phone in Fleet.
- **Keep a plate as typed.** Not chosen: BR-48 could then be broken by a hyphen.
- **Allow Hebrew letters and any separator in plates.** Not chosen: an ordinary Israeli plate is
  digits and hyphens, and every separator or alphabet allowed is one more way to spell one plate
  twice.
- **Depot hours per weekday.** Not chosen: no requirement asks for it, and it adds seven pairs where
  [FR-129] needs one.
- **Weekly shifts that carry a vehicle per day.** Not chosen: the vehicle would be set in two places,
  the default and the template, which can disagree.
- **A fill that refuses as a whole on the first unusable default.** Not chosen: one retired vehicle
  would block a whole date until someone found and fixed the driver behind it.
- **A fill that leaves an unusable default's driver on the roster without a vehicle.** Not chosen:
  the driver would look ready for a solve with nothing to drive.

## Revisit when

- The company's depots keep different hours on different weekdays, or a short Friday matters to the
  plan ([FR-80]).
- A depot or a shift needs to stay open across midnight ([FR-114]).
- The company's fleet includes a vehicle whose plate the normal form refuses.
- Drivers regularly change vehicle by weekday.
- Access gains a way to hear Fleet's events without breaking the dependency direction, such as a
  split into services with a broker.

[ADR-036]: ADR-036-one-account-per-person-signed-in-by-email.md
[ADR-062]: ADR-062-fleet-checks-the-driver-role-when-it-reads.md
[ADR-064]: ADR-064-events-only-where-they-earn-their-place-through-the-modulith-registry.md
[ADR-088]: ADR-088-israel-local-time-for-every-date-and-schedule.md
[ADR-104]: ADR-104-the-company-profile-holds-customer-defaults-and-capabilities-follow-the-role-matrix.md
[BR-46]: ../requirements/requirements.md#routing-constraints
[BR-48]: ../requirements/requirements.md#access
[BR-111]: ../requirements/requirements.md#time-and-limits
[DL-21]: ../architecture/domain_layer/domain_layer_design.md#21-module-graph
[DL-23]: ../architecture/domain_layer/domain_layer_design.md#23-rules-between-contexts
[DL-41]: ../architecture/domain_layer/domain_layer_design.md#41-access
[DL-42]: ../architecture/domain_layer/domain_layer_design.md#42-fleet
[DL-72]: ../architecture/domain_layer/domain_layer_design.md#72-the-events
[FR-06]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-54]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-79]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-80]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-114]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-129]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-131]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-189]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-190]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-193]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-307]: ../requirements/requirements.md#fleet-depots-and-shifts
[NFR-184]: ../requirements/requirements.md#nfr-184
[NFR-258]: ../requirements/requirements.md#nfr-258

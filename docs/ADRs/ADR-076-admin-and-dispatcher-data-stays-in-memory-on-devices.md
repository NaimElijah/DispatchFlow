# ADR-076: Admin and Dispatcher data stays in memory on devices, and sign-out wipes it

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

On 2026-11-13 [Q-98] was answered, together with three answers of [Q-92] that touch device
storage (#3, #4 and #5). This extends [ADR-035], which listed what the apps
may keep for the Admin and Dispatcher roles under "Not decided here".

Both apps serve every role ([ADR-035]), so a phone or a browser can hold an Admin's or a
Dispatcher's data. The rows that govern storage cover less:

- [NFR-39] encrypts tokens, the route data kept for offline use and queued reports with their
  proofs on the phone, with the key in the phone's secure storage.
- [NFR-98] removes tokens and route data at sign-out, and [NFR-179] wipes route data when a session is
  revoked, or after 14 days offline.
- All three speak of a driver's data. The security review of 2026-11-11 raised that nothing says what
  an Admin's or Dispatcher's session may leave on a device ([Q-98]). [Q-98] ties the question to
  ASVS 14.3.1, a Level 1 item, so it had to be answered before the self-check of [NFR-40]
  ([ADR-070]).

The forces:

- **A lost phone should not hold the company's data** for the roles that can read all of it
  ([ADR-036]'s risk of Admin power on a phone).
- **Only a driver's work must continue offline.** Dispatching offline is a non-goal, and printed
  routes are its fallback ([FR-72], [ADR-090]).
- **A refused refresh looks the same whatever the cause** ([NFR-92]), so a phone cannot tell
  revocation from expiry ([Q-92]).

## Decision

### Admin and Dispatcher data

- **Memory only.** Data the apps receive through the Admin and Dispatcher roles lives in memory, caches
  included. Nothing of it is written to storage, on a phone or in a browser. Server state in the apps
  is not persisted ([TS §11], *Server state and forms* row).
- **Sign-out and revocation wipe everything the account received.**
- **Exports leave only through the share sheet** ([FR-72], [FR-107]).
- **The web app clears its storage at sign-out, the driver screens included.** It sends
  `Clear-Site-Data` and deletes the refresh cookie ([Q-92] #5). Nothing an account
  received stays in the browser afterwards.

### Driver data

Driver data keeps its encrypted store, as [NFR-39] says: `expo-sqlite` built with SQLCipher, with the
key in `expo-secure-store`, holding the route cache and the report outbox ([TS §11]). Three answers
of [Q-92] change what it does:

- **#3: 14 days offline.** Queued reports and proofs are wiped with the route data after 14 days
  offline. The period equals the refresh token's lifetime ([NFR-87]).
- **#4: a refused refresh.** Any refused refresh wipes the route data and asks the driver to sign in
  again. The outbox is kept ([PL §19][PL-19]).
- **#5: web sign-out,** as above.

An account that holds a Driver role and an Admin or Dispatcher role gets both rules on one phone: the
driver data in the encrypted store, the rest in memory.

Requirement rows beside [NFR-39], [NFR-98] and [NFR-179] carry the rules.

## Consequences

- **Easy:** a lost or seized phone holds no Admin or Dispatcher data on disk, which the ASVS
  self-check can show.
- **Easy:** a wipe at sign-out has one rule for the Admin and Dispatcher roles: there is nothing to
  find.
- **Hard:** an Admin or Dispatcher screen reloads its data after the app is closed or its memory is
  freed. Those roles work online only ([ADR-090]'s offline-dispatching cut).
- **Hard:** every library that caches, such as an image loader or a query cache, must be set not to
  persist for these roles. The sources name only TanStack Query ([TS §11]).
- **Cost:** wipe logic and its tests on both platforms and in the browser.
- **Risk:** a driver offline for 14 days loses queued reports and proofs that were never delivered
  ([Q-92] #3).
- **Risk:** any refused refresh wipes the route data, including one refused for a reason other than
  revocation, such as an expired refresh token ([NFR-92]). The driver signs in again, and the outbox
  survives.
- **Risk:** a file shared through the share sheet leaves the app's control. The apps cannot wipe it.
- **Risk:** the driver data of an Admin who also drives is kept encrypted on the phone, so an account
  with both roles still leaves data there.

## Alternatives considered

- **Encrypted storage for every role,** as [NFR-39] does for a driver. Not chosen: it was decided
  that nothing received through the Admin and Dispatcher roles is written to storage. [Q-98] ties
  the question to ASVS 14.3.1. This ADR does not quote that item.
- **Wiping at sign-out only,** and not at revocation. Not chosen: revocation is when a lost phone's
  account is cut off ([NFR-179]).
- **Keeping the outbox after 14 days offline.** Not chosen ([Q-92] #3). The cost is the
  loss named above.

## Revisit when

- A pilot Dispatcher cannot work through a connection loss and asks for offline dispatching. That is
  the non-goal's trigger ([ADR-090]). Storage for those roles would then need its own decision.
- A driver loses queued reports to the 14-day rule, or to a refused refresh that was not a
  revocation, in the pilot. Weigh a longer period or a warning before the wipe.
- The ASVS self-check or a device-checklist run finds data from these roles on a device
  ([NFR-221]).

[ADR-035]: ADR-035-both-apps-serve-every-role.md
[ADR-036]: ADR-036-one-account-per-person-signed-in-by-email.md
[ADR-070]: ADR-070-security-and-accessibility-standards.md
[ADR-090]: ADR-090-scope-cuts-for-the-first-release.md
[FR-72]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-107]: ../requirements/requirements.md#reporting-and-baseline
[NFR-39]: ../requirements/requirements.md#nfr-39
[NFR-40]: ../requirements/requirements.md#nfr-40
[NFR-87]: ../requirements/requirements.md#nfr-87
[NFR-92]: ../requirements/requirements.md#nfr-92
[NFR-98]: ../requirements/requirements.md#nfr-98
[NFR-179]: ../requirements/requirements.md#nfr-179
[NFR-221]: ../requirements/requirements.md#nfr-221
[PL-19]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#19-phone-app
[Q-92]: ../requirements/requirements.md#open-questions
[Q-98]: ../requirements/requirements.md#open-questions
[TS §11]: ../architecture/tech_stack.md#11-phone-app

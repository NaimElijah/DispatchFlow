# ADR-030: Admins issue drivers' credentials, and a revoked access token is refused at once

- **Status:** Superseded by ADR-036
- **Date:** 2026-11-04

## Context

Drivers may have no email address, and SMS costs money on a budget of ₪300 a month ([NFR-52]).
A self-check against Level 1 of OWASP ASVS 5.0 must pass before the pilot handles real data
([NFR-40]). Level 1 asks that first passwords are random and short-lived (6.4.1), that a terminated
session allows no further use (7.4.1), and that disabling an account ends its sessions (7.4.2). A
15-minute access token that outlives its session fails those last two ([Q-89]).

Drivers work on weak signal, where a retried refresh looks like a stolen token ([Q-54]). With one
company there is no platform operator to rescue a lost Admin ([ADR-024], [Q-91]).

## Decision

- **Drivers** sign in with a username. An Admin issues a random first password, which expires on first
  use or after 72 hours and allows nothing except setting a new password, and a new one if it is
  forgotten ([FR-294], [FR-295]).
- **Admins and Dispatchers** recover a forgotten password through an emailed link, which works once and
  expires within 1 hour ([FR-296], [FR-345]).
- **Revocation:** the server refuses an access token issued before its user's last revocation, which
  is ASVS's own suggestion for self-contained tokens ([NFR-177]). An open event stream ends with it ([NFR-188]).
- **Refresh:** while its session is live, a refresh token presented again within 30 seconds of its
  exchange gets a new token, which replaces the one that exchange issued, and revokes nothing ([NFR-178]).
  Anything later, or a replaced token, revokes the session ([NFR-88]).
- **Failed sign-ins** are limited per account and source address ([NFR-35]), so an outsider cannot lock
  the company out, which ASVS 6.1.1 (Level 1) asks for.
- **Admins:** the company keeps two active Admins ([BR-19]), and on a named person's written request a
  team member can run an audited server step that creates a new one and deactivates the Admin accounts
  the request names ([FR-292], [FR-346]).
- **No second sign-in factor** for the pilot ([Q-81]). ASVS 5.0 item 6.3.3 is Level 2.

## Consequences

- **Easy:** no SMS provider or cost, and drivers need no email address.
- **Hard:** a driver who forgets a password needs an Admin, which can be awkward early in a shift.
- **Cost:** one lookup on every API call, and an email sender for recovery links.
- **Risk:** a thief who replays a refresh token within 30 seconds holds the session until the real phone
  presents its replaced token, which revokes it.
- **Risk:** a small company may struggle to keep two Admins, and a compromised Admin has no second
  factor behind the password.

## Alternatives considered

- **SMS codes for drivers.** Rejected: it costs money per message, needs a provider, and reverses
  [FR-200].
- **Email for everyone.** Rejected: every driver would need an email address.
- **Accepting the 15 minutes.** Rejected: the self-check would fail on items 7.4.1 and 7.4.2.
- **No refresh grace.** Rejected: drivers on weak signal would be signed out mid-route.
- **An authenticator app for Admins.** Rejected for the pilot: Level 1 does not require it, and it
  adds enrolment and factor recovery.

## Revisit when

- The company's drivers all have email, or the budget allows SMS.
- The self-check moves to ASVS Level 2.
- The per-call lookup shows up in [NFR-59]'s measurements.

[ADR-024]: ADR-024-one-delivery-company-no-multi-tenancy.md
[BR-19]: ../requirements/requirements.md#access
[FR-200]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-292]: ../requirements/requirements.md#company-users-and-roles
[FR-294]: ../requirements/requirements.md#company-users-and-roles
[FR-295]: ../requirements/requirements.md#company-users-and-roles
[FR-296]: ../requirements/requirements.md#company-users-and-roles
[FR-345]: ../requirements/requirements.md#company-users-and-roles
[FR-346]: ../requirements/requirements.md#company-users-and-roles
[NFR-35]: ../requirements/requirements.md#nfr-35
[NFR-40]: ../requirements/requirements.md#nfr-40
[NFR-52]: ../requirements/requirements.md#nfr-52
[NFR-59]: ../requirements/requirements.md#nfr-59
[NFR-88]: ../requirements/requirements.md#nfr-88
[NFR-177]: ../requirements/requirements.md#nfr-177
[NFR-178]: ../requirements/requirements.md#nfr-178
[NFR-188]: ../requirements/requirements.md#nfr-188
[Q-54]: ../requirements/requirements.md#open-questions
[Q-81]: ../requirements/requirements.md#open-questions
[Q-89]: ../requirements/requirements.md#open-questions
[Q-91]: ../requirements/requirements.md#open-questions

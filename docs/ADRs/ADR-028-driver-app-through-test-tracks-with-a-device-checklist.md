# ADR-028: The driver app ships through test tracks, and real phones are checked by hand

- **Status:** Accepted; extended by ADR-035, ADR-080 and ADR-081
- **Date:** 2026-11-04

## Context

[ADR-003] left open how the native app reaches the pilot's phones and how native builds are tested
([Q-16]). Background location ([FR-31]) can only be proved on a real phone. The pilot has one company
and its own drivers, so nothing needs a public store listing.

## Decision

- **Distribution:** TestFlight for iPhones and Google Play internal testing for Android ([NFR-186]).
- **Accounts:** the company owns the Apple and Google developer accounts ([Q-48]).
- **Native behaviour:** a scripted checklist on real phones before each pilot build ([NFR-140]).
- **Automated end-to-end tests** run on the web build ([NFR-83]), which is online only ([FR-326]).

## Consequences

- **Easy:** no public listing, and the end-to-end tests stay in CI without phones or emulators.
- **Hard:** distribution waits for a named company ([Q-01]). Until the company opens its accounts,
  nobody can receive a build through these tracks.
- **Cost:** a person's time on the checklist before each build. The account fees fall to the company. Nothing
  builds the iOS app yet: the repository has no EAS configuration, and a build service may cost money.
- **Risk:** a native regression is only caught by hand. Store review time, Apple's rules on
  background location and how each store admits drivers as testers are not verified ([Q-48]), and any
  of them could move the pilot date.

## Alternatives considered

- **The team's own developer accounts.** Not chosen: they would let builds ship before a company is
  named, but the app would then have to move to the company later.
- **Automated native UI tests,** with a tool such as Maestro. Rejected for now: they cost setup and CI
  time, and background location would still need real phones.
- **Android only, by direct install.** Rejected: it works only if every driver has Android, and it
  would change [NFR-132].

## Revisit when

- The company cannot open its accounts in time for the pilot.
- A native regression gets past the checklist more than once.
- The app is released beyond one company's drivers.

[ADR-003]: ADR-003-dispatcher-console-and-driver-app.md
[FR-31]: ../requirements/requirements.md#publishing-and-the-driver-app
[FR-326]: ../requirements/requirements.md#publishing-and-the-driver-app
[NFR-83]: ../requirements/requirements.md#nfr-83
[NFR-132]: ../requirements/requirements.md#nfr-132
[NFR-140]: ../requirements/requirements.md#nfr-140
[NFR-186]: ../requirements/requirements.md#nfr-186
[Q-01]: ../requirements/requirements.md#open-questions
[Q-16]: ../requirements/requirements.md#open-questions
[Q-48]: ../requirements/requirements.md#open-questions

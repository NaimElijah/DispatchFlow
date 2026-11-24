# ADR-081: JavaScript-only fixes reach the phone app through EAS Update during the pilot

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

[ADR-028] decided that the phone app reaches the pilot's phones as builds, through TestFlight and
Google Play internal testing ([NFR-186]), and that a scripted checklist on real phones runs before
each pilot build ([NFR-140]). A fix then costs "a day or more" per store build ([TS §11]).

During the pilot the drivers are real, and a fix that waits a day or more is a day on the road
with the fault. Every call reports the app's version ([NFR-93]), and an app below the oldest supported version
is told to update ([NFR-44], [NFR-134]). The tech stack listed over-the-air updates as Optional, and
as an open decision ([TS §22]).

Decided on 2026-11-13: use EAS Update for JavaScript-only fixes during the pilot. This extends
[ADR-028].

## Decision

- **EAS Update delivers JavaScript-only fixes** to the phone app during the pilot, without a store
  build. The row in [TS §11] moves from Optional to Decided, for the Pilot stage.
- **Native changes still go through store builds,** as [ADR-028] decided.
- **The app reports its version and its update ID** with every call, so the team can tell which code
  ran ([NFR-93], [NFR-44], [NFR-134]).
- **What leaves Israel:** app code, through EAS. [ADR-077] records it as an accepted exception to
  [NFR-209], and it goes into the data inventory ([NFR-72]).

## Consequences

- **Easy:** a same-day fix to a screen or a rule on drivers' phones during the pilot ([TS §11]).
  Each call names the update that sent it.
- **Hard:** two ways to change what runs on a phone: a build and an update. The team must keep
  track of which fix went by which.
- **Cost:** the sources give no price for EAS Update. This is not checked.
- **Risk:** an update is not a store build, so it does not pass the checklist that [NFR-140]
  requires before each pilot build. The sources do not say whether an update reruns it.
- **Risk:** a bad update reaches every driver's phone at once. The sources do not describe a
  rollback.
- **Risk:** an update sends app code through a service outside Israel. It is app code only
  ([ADR-077]).

## Alternatives considered

- **Store builds only.** Rejected: a day or more per fix ([TS §11]).
  It remains the path for native changes.

## Revisit when

- An update reaches the pilot's phones and breaks a screen. Require the checklist ([NFR-140]) for
  updates, or turn them off.
- Fixes in the pilot turn out to need a native change more than once. Updates then save little.
- EAS Update's price or terms change, or the price turns out to matter to the budget.
- The pilot ends and the app is released beyond one company's drivers.

[ADR-028]: ADR-028-driver-app-through-test-tracks-with-a-device-checklist.md
[ADR-077]: ADR-077-push-builds-and-app-stores-are-accepted-exceptions-to-hosting-in-israel.md
[NFR-44]: ../requirements/requirements.md#nfr-44
[NFR-72]: ../requirements/requirements.md#nfr-72
[NFR-93]: ../requirements/requirements.md#nfr-93
[NFR-134]: ../requirements/requirements.md#nfr-134
[NFR-140]: ../requirements/requirements.md#nfr-140
[NFR-186]: ../requirements/requirements.md#nfr-186
[NFR-209]: ../requirements/requirements.md#nfr-209
[TS §11]: ../architecture/tech_stack.md#11-phone-app
[TS §22]: ../architecture/tech_stack.md#22-open-decisions

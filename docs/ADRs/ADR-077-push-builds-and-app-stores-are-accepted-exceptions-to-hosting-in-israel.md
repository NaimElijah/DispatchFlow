# ADR-077: Push, app builds and the app stores are accepted exceptions to hosting in Israel, on conditions

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

On 2026-11-14 the owner, Naim Elijah, accepted three exceptions to [NFR-209] for the team, with
conditions. [NFR-209] requires the server and its data, backups included, to be hosted in Israel.
[TS §1] (rule 2) adds that anything touching personal data is self-hosted when that is cheap, "and an
outside service needs an accepted exception". The tech stack's table of outside services
([TS §13]) marked three rows "Accepted exception needed". This ADR is that acceptance. The tech
stack's row will link to it, and so will [NFR-209].

Hosting itself, in AWS `il-central-1`, is [ADR-078]'s. Crash reports were the fourth candidate. They
stay in Israel on GlitchTip, so they need no exception ([ADR-079]).

The forces:

- **Background alerts need a push service.** The apps alert a driver, an Admin or a Dispatcher in
  the background ([FR-71], [ADR-026], [ADR-083]). Expo's push service reaches phones through APNs
  and FCM, which are outside Israel ([TS §13]).
- **The phone app ships through TestFlight and Google Play internal testing** ([NFR-186], [ADR-028]).
  Their accounts belong to Apple and Google.
- **EAS builds and updates the app** ([TS §11]). Local builds would need "a Mac for every iOS
  build".
- **A push says only that something changed.** No push carries personal data, for any role; the app
  fetches the details with its own token ([NFR-218]).
- **The data inventory names each outside service** that receives company data, what it gets, and
  whether it is an accepted exception ([NFR-217], [NFR-72]).

## Decision

Three things leave Israel, and each is accepted.

| What | Service | What leaves |
|---|---|---|
| Phone alerts | Expo's push service, through APNs and FCM | Push tokens, and the message that something changed |
| Builds and updates | EAS Build and EAS Update | The app's code |
| Distribution | TestFlight and Google Play | The testers' store accounts |

The conditions hold for all three:

- **Payloads carry no personal data** ([NFR-218]).
- **Push tokens are removed** at sign-out, at revocation, and when Expo reports the device gone
  ([Q-92] #6). Expo's push access-token security is on ([TS §11], *Alerts* row).
- **All of it goes into the data inventory** ([NFR-72], [NFR-217]).
- **The legal advice of [Q-42] checks it** before the pilot handles real data.

The exceptions cost nothing. The tech stack's outside-services row for each becomes a link to this
ADR, and [NFR-209] cites it.

Other outside services carry no personal data and are not exceptions: Let's Encrypt and Route 53
(host names only), GitHub (code and images only, never company data) and the uptime and backup
pings ([TS §13]).

## Consequences

- **Easy:** the phone app keeps background alerts, store distribution and builds without a Mac for
  every iOS build, at no cost.
- **Easy:** the exception list is one place, so a reviewer, or the legal advice, checks the same three
  rows.
- **Hard:** each condition needs enforcing in code and in the inventory. A payload with personal data
  would break [NFR-218] and this ADR.
- **Hard:** removing push tokens at three events depends on Expo reporting a gone device, which the
  team does not control.
- **Cost:** none in money. The data inventory carries three more rows.
- **Risk:** a push token identifies a device, and the sources do not say whether the law treats it as
  personal data. The legal advice decides ([Q-42]).
- **Risk:** the testers' store accounts are visible to Apple and Google. With a staged pilot, the team's
  own accounts stand in ([ADR-080]).
- **Risk:** EAS Update ships code to phones outside Israel's hosting. [ADR-081] limits it to JavaScript
  fixes during the pilot.

## Alternatives considered

- **FCM and APNs called directly,** without Expo's push service. Lost on two providers to configure
  ([TS §11], *Alerts* row). It would not avoid the exception, because both services are outside
  Israel.
- **Local Xcode and Gradle builds, without EAS.** Lost on needing a Mac for every iOS build
  ([TS §11], *Builds* row).
- **Hosted Sentry for crash reports.** Rejected in [ADR-079], not accepted as an exception here.
- **No exception, and so no push, no store build and no test track.** Lost on the pilot's phones
  being reached only through TestFlight and Google Play ([NFR-186]), and on drivers needing
  background alerts ([FR-71]).

## Revisit when

- The legal advice of [Q-42] says that push tokens or testers' store accounts may not leave Israel,
  or sets another condition. Replace the exception.
- A feature needs more than "something changed" in a push payload. [NFR-218] would have to change
  first.
- The company refuses any of the three. Weigh local builds, or a different distribution.
- The data inventory review finds another outside service that receives company data. Add it here or
  reject it.

[ADR-026]: ADR-026-server-sent-events-and-push-for-live-updates.md
[ADR-028]: ADR-028-driver-app-through-test-tracks-with-a-device-checklist.md
[ADR-078]: ADR-078-hosting-on-one-aws-vm-in-tel-aviv-built-with-terraform.md
[ADR-079]: ADR-079-client-errors-and-crashes-go-to-self-hosted-glitchtip.md
[ADR-080]: ADR-080-team-accounts-stand-in-when-there-is-no-company.md
[ADR-081]: ADR-081-eas-update-for-javascript-only-fixes-during-the-pilot.md
[ADR-083]: ADR-083-admins-and-dispatchers-get-alerts-by-stream-and-push-in-the-phone-app.md
[FR-71]: ../requirements/requirements.md#publishing-and-the-phone-app
[NFR-72]: ../requirements/requirements.md#nfr-72
[NFR-186]: ../requirements/requirements.md#nfr-186
[NFR-209]: ../requirements/requirements.md#nfr-209
[NFR-217]: ../requirements/requirements.md#nfr-217
[NFR-218]: ../requirements/requirements.md#nfr-218
[Q-42]: ../requirements/requirements.md#open-questions
[Q-92]: ../requirements/requirements.md#open-questions
[TS §1]: ../architecture/tech_stack.md#1-what-decides-close-calls
[TS §11]: ../architecture/tech_stack.md#11-phone-app
[TS §13]: ../architecture/tech_stack.md#13-outside-services-and-where-data-goes

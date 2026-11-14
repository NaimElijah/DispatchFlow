# ADR-082: The server supports the current and the previous phone app version, and an older one still drains its queue

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

The web app is served by `caddy` from the same origin as the API ([TS §10]), so it is always the
version the server expects. The phone app is not: it is installed from a build, and a driver may not
update it at once. [NFR-44] and [NFR-134] state what the server does about that, and carry the
marker "ADR pending: supported phone app versions".

Three forces meet:

- Every API is a versioned OpenAPI contract ([NFR-21]). A breaking change to a call the phone app
  makes must not strand the previous app version ([PL §6.2]).
- A phone may hold reports and proofs queued offline. They wait until the server acknowledges them
  ([FR-248]), and a driver mid-route must keep working ([FR-69]).
- Every call already reports the app's version, for the audit record ([NFR-93]).

On 2026-11-13 the owner, Naim Elijah, confirmed [NFR-44] and [NFR-134] as written for the team.
This ADR records the rule they state and the mechanism of [PL §19].

## Decision

- **Two versions are supported:** the current phone app and the previous one. The server states the
  oldest supported version, which is the previous one ([NFR-44]).
- **Every call carries `X-App-Version`,** with `X-App` (web or phone) and, on driver calls,
  `X-Device-Time` ([PL §6.2]).
- **Below the oldest supported version,** the app tells the user to update, keeps its route and still
  sends its queue ([NFR-134]). The server answers `APP_UPDATE_REQUIRED` on screen loads but still
  accepts reports and positions, so the queue always drains ([PL §19]).
- **Every other version keeps working.** A breaking change to a call the phone app makes needs a new
  path version, and `oasdiff` checks every change against `main` in CI from January ([PL §6.2]).
- **The update ID of an over-the-air update** travels with the version ([ADR-081]).

## Consequences

- **Easy:** a driver who has not updated is not locked out mid-route, and no report is lost.
- **Hard:** every contract change to a phone call must work for two app versions, and a breaking
  change needs a new path version.
- **Hard:** the server must hold the oldest supported version somewhere the team can change it
  without a build. The sources do not say where.
- **Cost:** `oasdiff` in CI from January, and the work of keeping one old path version alive.
- **Risk:** store review time is not verified ([Q-48]). If a release waits in review, the previous
  version may become the oldest supported before drivers can update.
- **Risk:** an app below the oldest supported version still sends reports in the old format. The
  server accepts reports and positions from it, so the old format must keep being understood while
  the queue drains.

## Alternatives considered

- **Block every call below the oldest supported version.** Rejected: queued reports would not drain,
  and a driver mid-route would lose the app ([NFR-134], [PL §19]).
- **Support every version ever shipped.** Rejected: every breaking change would need a new path
  version that stays for good, which follows from [PL §6.2]'s rule.
- **Force an update at start-up.** Not chosen: it would stop a driver with a route in progress. The
  app tells the user instead and keeps working ([NFR-134]).

## Revisit when

- A driver's app falls below the oldest supported version during a pilot shift more than once.
- A change to a call the phone app makes cannot be served to two versions at once.
- Store review for a release takes longer than a version is supported ([Q-48]).
- The app is released beyond one company's drivers.

[ADR-081]: ADR-081-eas-update-for-javascript-only-fixes-during-the-pilot.md
[FR-69]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-248]: ../requirements/requirements.md#publishing-and-the-phone-app
[NFR-21]: ../requirements/requirements.md#nfr-21
[NFR-44]: ../requirements/requirements.md#nfr-44
[NFR-93]: ../requirements/requirements.md#nfr-93
[NFR-134]: ../requirements/requirements.md#nfr-134
[Q-48]: ../requirements/requirements.md#open-questions
[TS §10]: ../architecture/tech_stack.md#10-web-app
[PL §6.2]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#62-core-api-conventions
[PL §19]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#19-phone-app

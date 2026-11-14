# ADR-083: Admins and Dispatchers get alerts in the phone app by event stream while it is open and by push in the background

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

[ADR-026] chose server-sent events for the console and a push through Expo's push service for a
driver's backgrounded app. [ADR-035] then put every role in the phone app and gave Admins and
Dispatchers everything the web app allows ([FR-120]). It left one thing open: how their alerts reach
the phone app, by push or by an open event stream ([Q-97]).

The forces:

- An alert must reach an online reader within 30 seconds at the 95th percentile ([NFR-180]).
- A backgrounded app has no open connection, so a stream alone cannot reach it ([ADR-026]).
- No push may carry personal data ([NFR-218]).
- What the phone keeps for the Admin and Dispatcher roles is limited: data received through them lives
  in memory only ([ADR-076]).
- An alert raised while nobody has the app open must wait in the alert list ([FR-335]).

On 2026-11-13 the owner, Naim Elijah, decided for the team how these alerts reach the phone app.
This extends [ADR-026] and [ADR-035].

## Decision

- **While the app is open,** an Admin's or a Dispatcher's alerts arrive on the event stream, the
  same stream the web app reads, filtered by the account's roles ([ADR-026], [ADR-035], [NFR-85]).
  The app reads it with `react-native-sse` ([TS §11]).
- **While the app is in the background,** a push through Expo's push service tells them something
  changed. The push carries no personal data ([NFR-218]), and the app fetches the alert with its own
  token when it opens ([PL §17]).
- **The push uses the channel [ADR-026] built** for drivers: the same outbound port in `core`, the
  same device registration, and Expo's push access-token security turned on.
- **Push tokens are removed** at sign-out, at revocation, and when Expo reports the device gone
  ([Q-92]).
- **Waiting alerts:** an alert raised while the app is closed waits in the alert list ([FR-335],
  [FR-157]). An offline phone shows waiting alerts within 60 seconds of reconnecting while the app is
  open, and [NFR-180] is measured on phones from the server's send to the alert on screen.
- **The phone stores no alert.** Data received through the Admin and Dispatcher roles stays in
  memory ([ADR-076]).

## Consequences

- **Easy:** no new channel and no new adapter. All roles share the stream and the push path, and
  the server filters both by the account's roles ([NFR-85]).
- **Hard:** an alert that arrives by push says only that something changed, so the app must fetch the
  list when it opens. The user sees the detail one request later.
- **Hard:** nothing about an alert may be kept on the phone to speed that fetch ([ADR-076]).
- **Cost:** none beyond [ADR-026]'s. Expo's push service and `expo-notifications` are already
  adopted for drivers.
- **Risk:** a push is not guaranteed to arrive, so [NFR-180] depends on a service we do not run
  ([ADR-026]). The alert list holds the alert whether or not the push arrives.
- **Risk:** the sources do not say which alert kinds in [FR-157]'s list trigger a push, or whether a
  phone that holds both the Driver role and a dispatching role receives one push or two.

## Alternatives considered

- **Push only.** Rejected: the stream already serves an open app, and push alone would tie
  [NFR-180] wholly to a service we do not run.
- **The event stream only.** Rejected: a backgrounded app has no open connection, so an Admin away
  from the screen would learn of an alert only on opening the app ([ADR-026]).
- **Polling the alert list.** Rejected: a backgrounded app receives nothing, which is what
  [ADR-026] found for drivers.
- **A push that carries the alert's text.** Rejected: [NFR-218] forbids personal data in a push,
  for any role.

## Revisit when

- Alerts for Admins and Dispatchers miss [NFR-180] in the pilot, measured on a mid-range phone
  ([NFR-181]).
- Expo's push service stops fitting, as [ADR-026] says.
- A requirement lets an Admin's or a Dispatcher's phone keep alerts offline, which would reopen
  [ADR-076].

[ADR-026]: ADR-026-server-sent-events-and-push-for-live-updates.md
[ADR-035]: ADR-035-both-apps-serve-every-role.md
[ADR-076]: ADR-076-admin-and-dispatcher-data-stays-in-memory-on-devices.md
[FR-120]: ../requirements/requirements.md#dispatcher-views
[FR-157]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-335]: ../requirements/requirements.md#live-operations-and-re-optimization
[NFR-85]: ../requirements/requirements.md#nfr-85
[NFR-180]: ../requirements/requirements.md#nfr-180
[NFR-181]: ../requirements/requirements.md#nfr-181
[NFR-218]: ../requirements/requirements.md#nfr-218
[Q-92]: ../requirements/requirements.md#open-questions
[Q-97]: ../requirements/requirements.md#open-questions
[TS §11]: ../architecture/tech_stack.md#11-phone-app
[PL §17]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#17-live-updates-and-push

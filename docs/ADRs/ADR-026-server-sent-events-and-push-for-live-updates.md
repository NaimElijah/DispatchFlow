# ADR-026: Live updates use server-sent events and push, and clients act through HTTP POST

- **Status:** Accepted
- **Date:** 2026-11-04

## Context

The console must show solve results, positions and stop statuses without reloading ([FR-222],
[FR-33]), within the targets of [NFR-09] and [NFR-180]. The driver app must alert a driver even in
the background ([FR-71]). [ADR-003] left the channel open ([Q-04]).

A driver and a console never share a connection: the phone talks to the server, and the server talks
to the console. A driver's report must wait offline until the server acknowledges it ([FR-248]), and
a repeat must take effect once ([BR-28]). The contract ([ADR-006]), the audit record ([NFR-23]) and
the rate limits ([NFR-53]) are all written per HTTP call.

## Decision

- **Server to console:** server-sent events. The console reads the stream with a fetch-based reader,
  because the browser's built-in `EventSource` cannot send an `Authorization` header. After a
  reconnect it reads the current state again. The stream ends when its access token expires or its
  user's sessions are revoked ([NFR-188]).
- **Contract:** `client-api` stays on OpenAPI 3.1.0, which cannot type a stream's events ([ADR-006]). The
  stream is one operation with an opaque `text/event-stream` response, its event payloads are named
  schemas, and the reader is a hand-written exception to generated clients. An error after the stream
  starts ends the stream, and the console reconnects.
- **Driver and console actions:** ordinary HTTP POST, including every driver report.
- **Route change to a backgrounded app:** a visible push through Expo's push service, sent through an
  outbound port in `core`. The push carries no personal data. It says that a route changed, and the app
  fetches the route with its own token when it opens. Registering a device's push token is a contract
  operation.
- **The driver app's web build** gets no push, so it reads its route every 30 seconds while open
  ([FR-347]).
- **An alert raised while the console is closed** waits in the alert list ([FR-335]).

## Consequences

- **Easy:** every action stays one contract operation, with its status code, audit record and rate
  limit. The stream has one direction, so there is no socket protocol to define.
- **Hard:** each open console holds a connection, and a reconnect can miss events, which is why the
  console reads the state again. At OpenAPI 3.1.0 a stream's events are untyped, so tests check them
  against the named schemas.
- **Cost:** a new dependency, `expo-notifications`, and Expo's push service as a third party. Both
  need the usual justification when they are added, and the service belongs in the privacy review
  ([Q-42]).
- **Risk:** a push is not guaranteed to arrive, so [NFR-180] depends on a service we do not run. The
  app also fetches its route whenever it opens.

## Alternatives considered

- **A WebSocket to the console.** Rejected: its upward direction would stay unused, it adds reconnect
  and heartbeat handling, and OpenAPI cannot describe its messages.
- **WebSockets for everything.** Rejected: we would build acknowledgements, retries, audit and rate
  limits ourselves, and a backgrounded app has no open socket, so it would still need a push.
- **Polling only.** Rejected: a backgrounded app receives nothing, which breaks [FR-71].

## Revisit when

- A client must send many messages a second over one connection, such as shared live editing.
- Expo's push service misses [NFR-180] in the pilot, or its terms stop fitting.
- The contracts move to OpenAPI 3.2.0, which can type a stream's events.

[ADR-003]: ADR-003-dispatcher-console-and-driver-app.md
[ADR-006]: ADR-006-versioned-openapi-contracts.md
[BR-28]: ../requirements/requirements.md#driver-reports-and-outcomes
[FR-33]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-71]: ../requirements/requirements.md#publishing-and-the-driver-app
[FR-222]: ../requirements/requirements.md#daily-planning
[FR-248]: ../requirements/requirements.md#publishing-and-the-driver-app
[FR-335]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-347]: ../requirements/requirements.md#publishing-and-the-driver-app
[NFR-09]: ../requirements/requirements.md#nfr-09
[NFR-23]: ../requirements/requirements.md#nfr-23
[NFR-53]: ../requirements/requirements.md#nfr-53
[NFR-180]: ../requirements/requirements.md#nfr-180
[NFR-188]: ../requirements/requirements.md#nfr-188
[Q-04]: ../requirements/requirements.md#open-questions
[Q-42]: ../requirements/requirements.md#open-questions

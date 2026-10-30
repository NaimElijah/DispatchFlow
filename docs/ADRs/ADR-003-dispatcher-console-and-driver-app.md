# ADR-003: Two clients: a React web console and a React Native driver app

- **Status:** Accepted
- **Date:** 2026-10-30

## Context

DispatchFlow has two kinds of human user at the front, and they work in different places.

- **A dispatcher** sits at a desktop. They need a map, tables of orders and routes, and
  editing: dragging a stop to another route, correcting a pin. The console is a desktop web
  app ([NFR-15]).
- **A driver** is on a phone, on the road. They need today's route, a Navigate action that
  deep-links to Waze or Google Maps, arrived, delivered and failed reports, proof-of-delivery
  photos, and location reporting while the app runs in the background ([FR-28], [FR-29],
  [FR-30], [FR-31]). The web build of the driver app reports location only while it is open.
- **One codebase for the driver app.** It runs on iOS, Android and in a browser ([NFR-15]).
- **The same API for both.** Both clients talk only to `core`, through
  `contracts/client-api/v1` ([ADR-006]).
- **Open questions remain.** How the native app reaches the pilot's phones, and which tool
  runs end-to-end tests on native builds, are open ([Q-16]). So are the real-time channel
  ([Q-04]), the location-reporting rules ([Q-15]) and the proof-file store ([Q-14]).

## Decision

We build two clients, each for its user and device.

- **The dispatcher console** is React and TypeScript, built with Vite, for desktop browsers
  (`src/frontend/console`). It serves admins and dispatchers.
- **The driver app** is React Native with Expo, in TypeScript (`src/frontend/driver-app`). It
  is one codebase for iOS, Android and the browser, which runs through `react-native-web`.
  Expo SDK 58 and React Native 0.88.1 are pinned in its `package.json`. Native background
  location is the reason it is not a web app ([FR-31]).
- **Both use React, TypeScript and npm.** They talk only to `core`, through a client
  generated from `contracts/client-api/v1`. The generated code is never edited.
- **They share no code yet.** [ADR-008] defers a shared package until hand-written code would
  otherwise be copied between them. The two apps pin React separately.

### Not decided here

The map library for the console, the real-time channel ([Q-04]), how the native app is
distributed and how native builds are tested end to end ([Q-16]). Each is a later decision or
an open question, not a part of this one.

## Consequences

- **Easy:** each client fits its user. The console is built for a wide screen, and the driver
  app for a phone, with the phone's location and camera.
- **Easy:** both clients use one language and one package manager, so a teammate moves between
  them.
- **Easy:** the browser build of the driver app comes from the same code (`expo export
  --platform web`), as [NFR-15] asks.
- **Hard:** two frontends share no code. A fix to hand-written code that both need is made
  twice ([ADR-008]).
- **Hard:** native builds need phone toolchains or emulators, and testing them end to end has
  no chosen tool ([Q-16]). Background location can only be verified on real phones.
- **Hard:** the Expo SDK pins React and React Native. An SDK upgrade moves the driver app's
  versions on its schedule, not the console's.
- **Cost:** the driver app's browser build is a second-class citizen: it reports location only
  while the page is open.
- **Risk:** background location on both phone platforms ([FR-31]) depends on what Expo SDK 58
  offers and on the operating systems' rules. Whether it works in a pilot build is unverified;
  the requirement is pending.

## Alternatives considered

- **One responsive web app or PWA for drivers.** Rejected: background location on phones is a
  Must ([FR-31]), and a web page can report location only while it is open. This is the
  alternative with the least code to maintain.
- **Flutter.** Rejected: it adds a fourth language, Dart, beside Java, Python and TypeScript,
  and shares nothing with the console. [NFR-15] is met by React Native without it.
- **Two native apps in Swift and Kotlin.** Rejected: two codebases for three people, and
  [NFR-15] asks for one.
- **Bare React Native without Expo.** Rejected: the team would manage the native projects and
  the match between React Native and each native module itself. Expo pins a compatible set per
  SDK (`npx expo install`) and builds the web target.
- **Expo and `react-native-web` for the console too.** Rejected: the console has no native
  target, and the map libraries the plan names, Leaflet and MapLibre, are written for the
  browser. A web-first stack fits it better.
- **Next.js for the console.** Rejected: there is no server rendering or search-engine need,
  and the API is `core`. A second server for the console would add a deployable.
- **A separate manager app.** Rejected in [ADR-008]: no requirement asks for one.

## Revisit when

- **A native capability a Must needs is not available in Expo SDK 58.** Background location
  ([FR-31], a Must) is the first to check, then photo capture ([FR-30], a Should). Weigh the
  bare React Native workflow then.
- **[Q-16] is answered** and the chosen distribution or test tool needs something Expo does not
  provide.
- **Hand-written code is copied between the two apps.** Add the shared package ([ADR-008]).
- **A requirement asks for the dispatcher's tasks on a phone.** The desktop-only console
  stops fitting.

[ADR-006]: ADR-006-versioned-openapi-contracts.md
[ADR-008]: ADR-008-monorepo-layout-under-src.md
[FR-28]: ../requirements/requirements.md#publishing-and-the-driver-app
[FR-29]: ../requirements/requirements.md#publishing-and-the-driver-app
[FR-30]: ../requirements/requirements.md#publishing-and-the-driver-app
[FR-31]: ../requirements/requirements.md#publishing-and-the-driver-app
[NFR-15]: ../requirements/requirements.md#nfr-15
[Q-04]: ../requirements/requirements.md#open-questions
[Q-14]: ../requirements/requirements.md#open-questions
[Q-15]: ../requirements/requirements.md#open-questions
[Q-16]: ../requirements/requirements.md#open-questions

# ADR-035: Both apps serve Admins, Dispatchers and Drivers

- **Status:** Accepted; extended by ADR-076 and ADR-083; amended by ADR-037 and ADR-039
- **Date:** 2026-11-11

## Context

[ADR-003] built two clients, each for one kind of user: a desktop console for Admins and
Dispatchers, and a driver app for drivers on iOS, Android and the browser. Its revisit trigger
"a requirement asks for the dispatcher's tasks on a phone" has now fired.

On 2026-11-11 the owner, Naim Elijah, aligned the requirements with the [ARD]. The ARD puts every
role on a web app and on a phone app for iOS and Android ([NFR-15], [NFR-132]), and its Table 4.1
lists a separate stack for each. Its actors table has a Dispatcher work "usually from the web app on
a desktop, and also from the phone app when away from the desk". The owner went further and chose
full parity:
- Admins and Dispatchers can do everything in the phone app that they can do in the web app
  ([FR-120]);
- drivers can use the web app, online only, on a desktop or in a phone's browser ([FR-326],
  [NFR-15]).

Drivers in the web app also follow from the ARD's plan (§4.2.1): the demo and the Alpha "need only
the web app in a browser", the phone app comes after the Alpha, and the Alpha's stretch scope has
"each driver seeing their own route in the web app".

Both apps are skeletons today, with one screen each. Building everything once, in one Expo codebase
for web, iOS and Android, was compared and turned down.

## Decision

- **The web app** (`src/frontend/console`) is React and TypeScript, built with Vite. It serves
  Admins, Dispatchers and Drivers in desktop browsers, and its driver screens also work in a phone's
  browser ([NFR-15], [NFR-219]).
  - A driver there works online only ([FR-326]). While the app is open and visible on a route in
    progress, it reports the driver's location every 30 seconds ([FR-243]). It takes proof photos
    from the live camera stream in the page, never through a file picker ([NFR-204], [NFR-222]).
  - It learns of a route change from its event stream ([FR-347]), as it learns of everything else
    ([ADR-026]), and alerts a driver who has it open ([FR-391]).
- **The event stream is filtered by role.** The server sends each account only the events its roles
  may read, so the Driver role gets its own published routes and nothing else ([NFR-85], [BR-02],
  [BR-41]).
- **The phone app** (`src/frontend/driver-app`) is React Native with Expo SDK 58, in TypeScript, one
  codebase for iOS and Android.
  - It serves all three roles, and an Admin or Dispatcher can do everything there that the web app
    allows ([FR-120]).
  - Background location, offline work, background alerts and encrypted storage exist only in the
    phone app ([FR-31], [FR-69], [FR-70], [FR-71], [NFR-39]). Native background location is still
    why it is not a web page, as [ADR-003] found.
- **The phone app's web build** (`expo export --platform web`) is a development aid and a test
  target, and never reaches users or real data ([NFR-220]). Drivers in a browser use the web app.
  - The end-to-end tests run the same use cases on the web app and again on this web build
    ([NFR-83]).
  - It gets no push, so it still reads its route every 30 seconds while open, as [ADR-026] decided.
- **Both talk only to `core`,** through a client generated from `contracts/client-api/v1`, which is
  never edited ([ADR-006]).
  - The server decides what an account may do. Each app shows what the account's roles allow
    ([FR-293], [ADR-036]).
- **Both use React, TypeScript and npm.** The Expo SDK pins the phone app's React and React Native,
  so the two apps pin React separately.
- **Shared code:** the non-view code (API calls, validation, state, and the Hebrew and English
  strings) moves into a shared package as soon as both apps need the same hand-written code
  ([ADR-008]). Only the views are written twice. Under full parity that will be early.
- **The folders keep their names.** The documents call them the web app and the phone app.
- **Still in force, and extended by this ADR:**
  - [ADR-026]: server-sent events to the web app, and push to the phone app in the background;
  - [ADR-028]: the phone app ships through test tracks, and real phones are checked by hand
    ([NFR-140]).

### Not decided here

- How an Admin's or a Dispatcher's alerts reach the phone app: push, or an open event stream
  ([Q-97]).
- The map library for each app, and whether the web app's frame-rate target binds the phone app
  ([Q-03]).
- What the apps may keep on a device for the Admin and Dispatcher roles ([Q-98]).
- The phone app's name on the phone and its orientation: `app.json` still says "DispatchFlow
  Driver" and portrait only. Settle both before the first test-track build fixes the app's identity
  in the stores ([ADR-028]).

## Consequences

- **Easy:** every role works from a desk or from a phone, and a dispatcher can handle a disruption
  away from the desk.
- **Easy:** each app keeps the stack that suits its platform: desktop web libraries in the web app,
  native capabilities in the phone app.
- **Easy:** both apps use one language and one package manager, so a teammate moves between them.
- **Hard:** every view exists twice, in React and in React Native, and must behave the same in
  both. Each feature's views are built, tested and reviewed twice, and every new contract operation
  lands in both apps at once ([ADR-006]).
- **Hard:** every phone-app dependency needs a web implementation or a stub for the web build that
  the end-to-end tests use, which constrains the phone app's map library ([Q-03]).
- **Hard:** the web app must capture proof photos from the camera inside the page, because a file
  input lets the user pick any file. The server cannot tell where a photo came from.
- **Hard:** screens designed for a desktop need phone versions: the map with 15 routes, moving stops
  by hand, and wide tables. The web app's driver screens must also work in a phone's browser.
- **Hard:** native builds need phone toolchains, and background location can only be verified on
  real phones ([ADR-028]).
- **Hard:** an Expo SDK upgrade moves the phone app's React and React Native on Expo's schedule, not
  the web app's.
- **Cost:** roughly twice the view work of one app for each role, on four people (risk R2 in the
  [ARD], [Q-63]).
- **Cost:** with drivers in the web app, up to 20 web app sessions may hold event streams at once,
  where [NFR-10] load-tests 5 ([Q-94] asks whether k6 can hold them).
- **Risk:** the two apps drift apart. The generated client, the shared package, the end-to-end
  tests on both apps ([NFR-83]) and the hand checklist ([NFR-221]) limit the drift.
- **Risk:** the phone app's native Admin and Dispatcher screens are checked only by hand
  ([NFR-221]); the end-to-end tests run its web build, not iOS or Android.
- **Risk:** an account holding Dispatcher and Driver can open the dispatcher screens while driving.
  The rules for use while moving ([NFR-45], [NFR-139]) bind the driver screens only.
- **Risk:** background location on both phone platforms ([FR-31]) depends on what Expo SDK 58 offers
  and on each operating system's rules, as [ADR-003] noted.
- **Risk:** an account with the Admin role carries Admin power on a phone in a van ([ADR-036]).

## Alternatives considered

- **One Expo codebase for web, iOS and Android,** through `react-native-web`. Rejected by the owner
  after a comparison on 2026-11-11:
  - map editing and dense tables on a desktop are weaker in `react-native-web`, and the map
    libraries the plan names, Leaflet and MapLibre, are written for the browser;
  - the ARD names two codebases.

  It would have cut the client work by roughly half.
- **A phone app limited to work away from the desk** for Admins and Dispatchers, as the ARD's actors
  table reads. Rejected: the owner wants parity.
- **Keeping [ADR-003]'s split by role.** Rejected: the ARD and the requirements now put every role
  on both apps.
- **A web page instead of the phone app.** Rejected for the reason [ADR-003] gave: background
  location ([FR-31]).
- **No driver screens in the web app.** Rejected: the ARD's demo and Alpha run on the web app alone
  (§4.2.1).
- **A desktop-only web app for drivers,** with no location and proof photos uploaded from files.
  Rejected by the owner: the dispatcher's map would show no position for its drivers, and a
  leave-at-door delivery needs a photo ([FR-368]).
- **End-to-end tests on the web app only,** with component tests for the phone app and no web build.
  Rejected by the owner on 2026-11-11: no automated test would then run the phone app's own screens.
- **Sharing only validation and strings.** Not chosen: API calls and state contain no view code, so
  both apps can share them, which leaves only the views to write twice.
- **Still rejected, for the reasons [ADR-003] gave:**
  - Flutter: a fourth language, Dart, which shares nothing with the web app;
  - two native apps in Swift and Kotlin: two more codebases for four people;
  - bare React Native without Expo: the team would manage the native projects and their module
    versions itself;
  - Next.js for the web app: there is no server rendering or search-engine need, and it adds a
    server to deploy;
  - a separate manager app: [ADR-008] found no requirement for one.

## Revisit when

- A pilot build ships with a capability of the permission matrix that one app lacks, as the hand
  checklist shows ([NFR-221]). Weigh narrowing the parity, or one Expo codebase.
- A phone-app feature that an end-to-end use case needs gets only a stub in the web build. Weigh
  dropping the web build and testing the phone app another way.
- In a pilot month, under 10% of Admin and Dispatcher operations come from the phone app, by the app
  each audit record names ([NFR-93]). Narrow what the phone app offers them.
- A native capability that a Must needs is missing from Expo SDK 58: background location ([FR-31])
  first, then photo capture ([FR-30]). Weigh the bare React Native workflow then.

[ADR-003]: ADR-003-dispatcher-console-and-driver-app.md
[ADR-006]: ADR-006-versioned-openapi-contracts.md
[ADR-008]: ADR-008-monorepo-layout-under-src.md
[ADR-026]: ADR-026-server-sent-events-and-push-for-live-updates.md
[ADR-028]: ADR-028-driver-app-through-test-tracks-with-a-device-checklist.md
[ADR-036]: ADR-036-one-account-per-person-signed-in-by-email.md
[ARD]: ../requirements/ARD/DispatchFlow_ARD.pdf
[BR-02]: ../requirements/requirements.md#access
[BR-41]: ../requirements/requirements.md#access
[FR-30]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-31]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-69]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-70]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-71]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-120]: ../requirements/requirements.md#dispatcher-views
[FR-243]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-293]: ../requirements/requirements.md#company-users-and-roles
[FR-326]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-347]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-368]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-391]: ../requirements/requirements.md#publishing-and-the-phone-app
[NFR-10]: ../requirements/requirements.md#nfr-10
[NFR-15]: ../requirements/requirements.md#nfr-15
[NFR-39]: ../requirements/requirements.md#nfr-39
[NFR-45]: ../requirements/requirements.md#nfr-45
[NFR-83]: ../requirements/requirements.md#nfr-83
[NFR-85]: ../requirements/requirements.md#nfr-85
[NFR-93]: ../requirements/requirements.md#nfr-93
[NFR-132]: ../requirements/requirements.md#nfr-132
[NFR-139]: ../requirements/requirements.md#nfr-139
[NFR-140]: ../requirements/requirements.md#nfr-140
[NFR-204]: ../requirements/requirements.md#nfr-204
[NFR-219]: ../requirements/requirements.md#nfr-219
[NFR-220]: ../requirements/requirements.md#nfr-220
[NFR-221]: ../requirements/requirements.md#nfr-221
[NFR-222]: ../requirements/requirements.md#nfr-222
[Q-03]: ../requirements/requirements.md#open-questions
[Q-63]: ../requirements/requirements.md#open-questions
[Q-94]: ../requirements/requirements.md#open-questions
[Q-97]: ../requirements/requirements.md#open-questions
[Q-98]: ../requirements/requirements.md#open-questions

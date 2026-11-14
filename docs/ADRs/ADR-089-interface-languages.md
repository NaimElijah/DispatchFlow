# ADR-089: Both apps are available in Hebrew, right to left, and in English only

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

DispatchFlow works in Israel only, with Hebrew addresses ([Purpose and scope][PS]). Every screen of
the web app and the phone app must be available in Hebrew, laid out right to left, and in English
([NFR-27]). Its row carried the marker "ADR pending: interface languages".

[Q-44] asked on 2026-11-04 whether to add further languages. It was resolved the same day: Hebrew
and English only. Arabic and Russian for the driver screens stay a Could ([FR-121]), to be reopened
when the company's drivers need them.

On 2026-11-13 the owner, Naim Elijah, decided for the team to confirm that answer and the way the
two apps carry it. The decision log records no reason for including English beside Hebrew.

Right to left touches every layer of the stack: layout, mixed-direction text, fonts, maps, printed
routes and messages to recipients. The decision names how each is handled, so a later language does
not have to find them again.

## Decision

- **Two languages: Hebrew, laid out right to left, and English,** on every screen of both apps
  ([NFR-27]). No other language is built. [FR-121] stays a Could and is not scheduled.
- **A user chooses their language** ([FR-124]). A new user starts with the company's default
  ([FR-182]), which an Admin sets with the other company settings ([FR-125]).
- **Messages to recipients** are written in the company's default language, with fixed wording per
  message type ([FR-349]).
- **One strings package, `@dispatchflow/i18n`,** holds the Hebrew and English strings for both apps
  ([TS §12]). Every error code has Hebrew and English text and a next step ([NFR-144]). Both apps
  translate the API's stable error `code`, so no stack trace or bare code reaches a screen
  ([PL §6.2]).
- **The web app** ([TS §10]):
  - i18next with react-i18next;
  - Mantine 9, which gets right to left through one `DirectionProvider`;
  - `<bdi>` around mixed-direction text, such as addresses with numbers ([NFR-136]);
  - a Hebrew-capable font, Heebo or Rubik, served from our own origin through Fontsource;
  - `@hebcal/core` for Israeli holidays ([FR-80]);
  - printing through the browser, which handles Hebrew right to left ([FR-72]).
- **The phone app** ([TS §11]): i18next with `expo-localization`. Switching to or from Hebrew calls
  `I18nManager.forceRTL` and reloads the app ([FR-124]).
- **Maps** show Hebrew (`name:he`) labels, through MapLibre's right-to-left text plugin and
  self-hosted fonts ([ADR-084]).
- **Formats are the same in both languages:** DD/MM/YYYY, a 24-hour clock, kilometres, kilograms,
  litres and shekels ([NFR-68], [ADR-088]).
- **The user guides exist in both languages** ([NFR-79]).
- **Addresses in Arabic or in English transliteration** are an input question, not an interface one.
  The geocoder test includes them, a miss is flagged for a hand-placed pin, and Hebrew address
  normalization stays Hebrew only ([Q-83], [FR-140], [ADR-085]).
- **The Hebrew terms in [DL §17]** are the working terms that the code and screens use. Once the
  company has a dispatcher, the dispatcher checks them, and a term changes where the company's own
  word differs.

## Consequences

- **Easy:** one set of strings serves both apps, and the same i18next setup runs in React and in
  React Native ([TS §10], [TS §11]).
- **Hard:** every screen, error code, message template and guide exists twice, and each new one
  needs both texts ([NFR-144], [NFR-79]).
- **Hard:** right to left is checked on real phones, including native map labels ([NFR-140]), and
  mixed text is checked against a stated list of sample strings ([NFR-136]).
- **Hard:** a language switch to or from Hebrew reloads the phone app.
- **Cost:** two user guides ([NFR-79]), and a font served by us instead of a font service.
- **Risk:** a driver who reads only Arabic or Russian cannot use the app in their language. The
  company's drivers are not known yet ([Q-01]).
- **Risk:** a Hebrew term in [DL §17] may differ from the company's own word until its dispatcher
  checks them.

## Alternatives considered

- **Hebrew only.** Rejected: [NFR-27] requires English as well.
- **Arabic and Russian for drivers now.** Rejected on 2026-11-04 ([Q-44]): no driver needs them
  yet. It stays a Could ([FR-121]).
- **FormatJS in place of i18next.** Rejected in [TS §10]: its ICU messages are richer, but i18next is
  simpler and works the same in React Native.
- **MUI, for the web app's right-to-left support.** Rejected in [TS §10]: right to left needs an
  Emotion plugin set up by hand, and its date pickers are a separate product.
- **A font service such as Google Fonts.** Rejected in [TS §10]: it sends a request to an outside
  service on every load.

## Revisit when

- The company's drivers need Arabic or Russian ([Q-44]'s trigger), for example a pilot driver who
  cannot use the app in Hebrew or English. Build [FR-121].
- The company has a dispatcher, and a Hebrew term in [DL §17] differs from their word. Change the
  term in the code and the screens.
- The device checklist finds Hebrew labels or mixed text rendering wrongly on a real phone
  ([NFR-140], [NFR-136]).
- A recipient message must go out in more than the company's one default language ([FR-349]).

[ADR-084]: ADR-084-maplibre-in-both-apps-with-self-hosted-protomaps-tiles.md
[ADR-085]: ADR-085-govmap-geocodes-addresses-with-nominatim-as-the-fallback.md
[ADR-088]: ADR-088-israel-local-time-for-every-date-and-schedule.md
[DL §17]: ../architecture/domain_layer/domain_layer_design.md#17-ubiquitous-language
[PL §6.2]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#62-core-api-conventions
[PS]: ../requirements/requirements.md#purpose-and-scope
[TS §10]: ../architecture/tech_stack.md#10-web-app
[TS §11]: ../architecture/tech_stack.md#11-phone-app
[TS §12]: ../architecture/tech_stack.md#12-shared-typescript-packages
[FR-72]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-80]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-121]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-124]: ../requirements/requirements.md#company-users-and-roles
[FR-125]: ../requirements/requirements.md#company-users-and-roles
[FR-140]: ../requirements/requirements.md#geocoding
[FR-182]: ../requirements/requirements.md#company-users-and-roles
[FR-349]: ../requirements/requirements.md#messages-to-recipients
[NFR-27]: ../requirements/requirements.md#nfr-27
[NFR-68]: ../requirements/requirements.md#nfr-68
[NFR-79]: ../requirements/requirements.md#nfr-79
[NFR-136]: ../requirements/requirements.md#nfr-136
[NFR-140]: ../requirements/requirements.md#nfr-140
[NFR-144]: ../requirements/requirements.md#nfr-144
[Q-01]: ../requirements/requirements.md#open-questions
[Q-44]: ../requirements/requirements.md#open-questions
[Q-83]: ../requirements/requirements.md#open-questions

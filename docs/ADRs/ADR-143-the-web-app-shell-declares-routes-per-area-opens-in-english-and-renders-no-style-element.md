# ADR-143: The web app's shell declares routes per area, opens in English, and renders no `<style>` element

- **Status:** Accepted; amended by ADR-144
- **Date:** 2026-11-25

## Context

[#163][ISSUE-163] builds the web app's shell: the frame every Alpha screen lives in. Its stack is
already decided in [TS §10][TS-10]: TanStack Router, TanStack Query, Mantine 9 with one
`DirectionProvider`, i18next with react-i18next, and a Hebrew-capable font through Fontsource.
Several later tickets copy what the shell sets up: #203, #207 and #224 add routes, #173 adds
shared components, #174 mounts the map, and #225 and #233 fill the planning screen. The issue left
four points open, and the build met a fifth:

- **How routes are declared.** TanStack Router takes routes as code, or as files under
  `src/routes/` through its Vite plugin, which generates `routeTree.gen.ts`. [TS §10][TS-10]'s
  structure puts each context's code in `src/features/<context>`, and #203, #207, #224 and #225 will
  add routes in parallel.
- **The language before a user's own setting exists.** [ADR-089] has a user choose their language
  ([FR-124]) and a new user start with the company's default ([FR-182], set on [FR-125]'s company
  settings page). Neither setting is built, and #163 recommended Hebrew.
- **The font.** [TS §10][TS-10] allows Heebo or Rubik.
- **What `/` opens.** #225 defaults the planning screen to tomorrow in `Asia/Jerusalem` ([ADR-088])
  and asks the shell's planning link to open it.
- **The Content Security Policy.** [ADR-069] set the policy `default-src 'self'; img-src 'self'
  blob: data:; connect-src 'self' <GlitchTip address>; worker-src blob:; frame-ancestors 'none';
  base-uri 'none'; form-action 'self'; object-src 'none'`, and asked for a check that the web app
  renders under it, because "Mantine may inject a `<style>` element that needs a nonce or a hash".
  Its `style-src` falls back to `'self'`, so the browser refuses every `<style>` element. Mantine
  9.8.2's source renders one in four places:
  - `MantineCssVariables`, always, for the theme's variables;
  - `MantineClasses`, always, for the `hiddenFrom` and `visibleFrom` rules;
  - `InlineStyles`, always for `AppShell`, `Grid`, `Grid.Col` and `SimpleGrid`, and for any
    responsive style prop such as `p={{ base: 'sm', md: 'lg' }}`. `AppShell`'s text changes when its
    phone menu opens or closes;
  - the colour-scheme switch, when the scheme changes.

  Mantine's `styles.css` already holds the default theme's variables and its focus classes. Each
  `<style>` takes a nonce through `getStyleNonce`, but a nonce has to be fresh on every response,
  which neither `vite preview` nor Caddy's static serving gives today.

## Decision

Decided on 25 November 2026.

1. **Routes are code-based, and each area declares its own.**
   - A context's routes live in `src/features/<context>/routes.ts`, the planning routes in
     `src/features/planning/routes.ts` like the rest.
   - Their parent is `shellRoute`, a layout route with no path (its ID is `_shell`) whose component
     draws the header and the navigation. Sign-in can then sit beside it under the root route,
     outside the shell, and guard every page by guarding this one route.
   - `src/routes/` holds the shell: the root route, `shellRoute` and its layout, `/`, the
     not-found and error pages, and a catch-all `$` route that shows the not-found page inside the
     shell.
   - `src/routeTree.ts` gathers every area's routes. It sits above both folders, because it is the
     one module that imports both.
   - No router plugin and no generated file.
2. **The web app's folders depend one way:** `src/` itself, then `features/`, `routes/`,
   `components/` and `lib/`. A feature reaches the shell's `shellRoute`, and the shell reaches no
   feature. `lib/` holds what no layer owns and imports none of them; the language setting is its
   first file. ESLint's `no-restricted-imports`, in `src/frontend/web/eslint.config.js`, fails an
   import that points back.
3. **`/plans/$date` takes only a real date.** Its `params.parse` accepts a `YYYY-MM-DD` that the
   calendar has and returns `false` otherwise, so `/plans/2026-02-30` matches only the catch-all
   route and shows the not-found page.
4. **`/` opens tomorrow's plan in Israel.** It redirects to `/plans`, the planning area's own
   route, which redirects to `/plans/<tomorrow in Asia/Jerusalem>` from the router's injected
   clock. Both replace their history entry. The navigation's planning item opens `/plans`, and is
   the current item on every plan's page. The date's rule lives in one place, planning's.
5. **The web app opens in English, and turns Hebrew only through its settings.**
   - Until [FR-124] and [FR-182] are built, a Settings page at `/settings`, in
     `src/features/access/`, offers "English" and "עברית", each in its own language. There is no
     language switch in the header. The setting itself is `src/lib/languageSetting.ts`.
   - The choice is kept in this browser under one `localStorage` key, `dispatchflow.language`, and
     read before the first render. Only `en` and `he` are accepted; any other value opens English.
     Storage the browser refuses keeps the choice for the visit only.
   - The key holds a choice made on the device, not data the server sent, so [ADR-076]'s memory-only
     rule does not cover it. Sign-out's `Clear-Site-Data` wipes it with the rest of the site's
     storage. It goes once [FR-124] keeps the language on the account. Then a user's own setting
     comes first, the company's default next, and English last.
6. **The strings live in `@dispatchflow/i18n` as TypeScript, one namespace per area.**
   - English files define each namespace's keys, and the Hebrew files are typed against them, so a
     missing or extra Hebrew key fails `tsc`.
   - The package's tests check that no string is empty and that both languages carry the same
     `{{value}}` and `<element>` placeholders.
   - Each app sets up its own i18next instance.
   - A counted string has English's plural keys (`_one`, `_other`). Hebrew's dual (`_two`) is
     allowed in the typing and the tests when the first string needs one.
7. **A shared package ships its source,** as `@dispatchflow/i18n` is the first to: `exports` points
   at `src/index.ts`, its imports carry the `.ts` extension, the app that imports it compiles it,
   and its `build` script is its type check (`tsc -b`). No package builds before another, which
   settles the build order [ADR-039] left open. A consumer needs `allowImportingTsExtensions`, as
   `@dispatchflow/tsconfig`'s presets set it; the phone app sets it, or extends them, when it joins
   the workspace.
8. **The font is Heebo,** through `@fontsource-variable/heebo`, served from our own origin
   ([NFR-209]). Vite never inlines a font file as a `data:` URI, which `font-src 'self'` would
   refuse.
9. **The app renders no `<style>` element, and [ADR-069]'s policy stays as it is.**
   - `MantineProvider` runs with `withCssVariables={false}` and `withGlobalClasses={false}`.
     `src/styles/theme.css` holds what they would inject: the font variables and the `hiddenFrom` /
     `visibleFrom` rules for Mantine's default breakpoints. A theme change goes there, as a CSS
     variable; a `createTheme` override would reach no CSS.
   - The colour scheme is forced light. Dark mode is not built.
   - The shell's layout is its own CSS module, not `AppShell`. Screens use CSS modules for responsive
     layout, not `Grid`, `SimpleGrid` or responsive style props.
   - The web app's test setup fails any test that leaves a `<style>` element in the document, so a
     component that injects one is caught where it is first used, whatever the source. The scroll
     lock of Mantine's `Modal` and `Drawer` (react-remove-scroll) is expected to be one.
10. **No error reaches the screen** ([NFR-143]). A page's error shows the translated error page
    inside the shell; an error in the shell shows it in the shell's place. The router's own global
    boundary is off (`disableGlobalCatchBoundary`), because its fallback offers to show the error's
    message. `AppErrorBoundary`, around the whole app, shows the translated text in plain HTML when
    even the error page fails.
11. **`permissions` in `src/frontend/pnpm-workspace.yaml` holds what each dependency may do,** and
    `allowBuilds` is gone: pnpm 12's `permissions` overrides it, and `pnpm approve-builds` writes
    to `permissions` once it exists. Its first entries refuse `fsevents`'s build, as `allowBuilds`
    did, and `@tanstack/react-router` 1.170.46's request to install skills, instruction files for
    AI coding agents: such files would steer anyone's agent in this repository from outside it. A
    later package that asks waits for `pnpm approve`, and is refused the same way.
12. **The check's result.** The production build, served by `vite preview` with the policy in
   `preview.headers` (`connect-src 'self'`, as GlitchTip's address is not chosen), was opened in
   Chrome on 25 November 2026. In English, and in Hebrew after choosing it in Settings and
   reloading:
   - no page raised a policy violation, and the document held no `<style>` element;
   - every request went to our own origin, Heebo's Latin and Hebrew files included;
   - a `<style>` element injected on purpose was refused (`style-src-elem`), showing that the
     policy was live.

   The pages checked were `/`, every navigation item, `/no-such-page` and `/plans/2026-02-30`.

## Consequences

- **Easy:**
  - An area adds a route in its own folder. A new area adds one line to `routeTree.ts`. A link to a
    route the tree lacks fails `tsc`.
  - Sign-in guards one route, `shellRoute`, and edits no area's routes.
  - [ADR-069]'s policy needs no nonce and no hash, so Caddy can serve the build as static files.
    The check runs again as #187's browser test.
  - A remembered Hebrew opens right to left before the first paint, and a reader finds their
    language in Settings in their own script.
- **Hard:**
  - `AppShell`, `Grid`, `SimpleGrid`, responsive style props and, it is expected, scroll-locking
    modals are out. A screen that needs one comes back to this ADR.
  - Parallel tickets still share edit points: `routeTree.ts` for a new area, the navigation in
    `AppLayout.tsx` and its labels in the `shell` strings for a page the menu shows. File-based
    routes would have split code by route automatically; here a heavy page uses
    `lazyRouteComponent`.
  - A Mantine upgrade can change the rules `theme.css` copies from `MantineClasses`.
- **Cost:** none in money. The packages added directly are MIT, except
  `@fontsource-variable/heebo`, under the SIL Open Font License 1.1 like the font. Two that come in
  through them are not MIT: `isbot` (the Unlicense) and `type-fest` (MIT or CC0 1.0).
- **Risk:**
  - The team sees the app left to right by default, so a right-to-left fault is easy to miss by
    eye. The tests and the browser check cover both languages.
  - The navigation calls the Fleet area "צי רכב", where [DL §17][DL-17] names only the Fleet
    register, "מאגר רכבים", which holds depots and vehicles but not drivers or shifts. "תכנון" and
    "הגדרות" are not in §17 either. The company's dispatcher checks them with the others
    ([ADR-089]).

## Alternatives considered

- **File-based routes through `@tanstack/router-plugin`,** TanStack's default, which [TS §1][TS-1]
  rule 3 prefers unless something else pays for itself before the Alpha. Rejected: every route file
  sits under `src/routes/`, away from its context, which [TS §10][TS-10]'s structure gives each
  context; and the plugin brings Babel and a file watcher into the dev tree. Its virtual file
  routes can map feature folders, but still need the plugin and a generated tree.
- **The shell drawn by the root route.** Rejected: sign-in would have to sit inside the shell, or
  every area's routes would move under a guarded route later.
- **The planning route in `src/routes/`,** as #163 first placed it. Rejected: it would be the one
  area whose route lives outside its folder.
- **Hebrew first,** as #163 recommended. Rejected on 25 November 2026: the app opens in English and
  is Hebrew only when the settings say so.
- **A language switch in the header.** Rejected: the language is a setting ([FR-124]), and the
  Settings page is where [FR-124] will keep it.
- **The language in memory only.** Rejected: every reload would open English again after Hebrew was
  chosen.
- **The browser's language.** Rejected: [ADR-089] starts a user with the company's default, not the
  browser's.
- **A nonce per response.** Keeps all of Mantine, but needs a server that writes a fresh value into
  each page and its header: a `vite preview` plugin now, a Caddy template before the pilot, and an
  `index.html` that is never cached.
- **`style-src 'self' 'unsafe-inline'`.** Keeps all of Mantine, but lets injected markup restyle the
  page, and OWASP ZAP flags it. It weakens [ADR-069]'s policy.
- **A hash of each `<style>`.** Breaks silently when a Mantine upgrade or a prop changes the text;
  `AppShell`'s changes whenever its menu opens.
- **Rubik.** Rounded and wider, so dense tables at 1366×768 ([NFR-67]) fit less.
- **The strings as JSON.** Rejected: JSON cannot type the Hebrew against the English.

## Revisit when

- A screen needs a component that renders a `<style>`: choose between a nonce per response and
  changing [ADR-069]'s policy, in a new ADR.
- [FR-124] or [FR-182] is built: read the language from the account, and remove the
  `dispatchflow.language` key.
- The company asks for dark mode.
- The first load breaks [NFR-58]'s 3 seconds on throttled Fast 4G: split routes with
  `lazyRouteComponent`, and import Mantine's styles per component.
- More than a few dozen routes make the shared edit points a burden: move to file-based routes.
- The phone app joins the workspace, and Metro cannot resolve a source package's `exports` or its
  `.ts` imports: build the packages instead, with a build order.
- A screen needs a Hebrew dual (`_two`): allow it in `Strings` and in the package's tests.

[ADR-039]: ADR-039-one-pnpm-workspace-for-both-apps-and-shared-packages.md
[ADR-069]: ADR-069-sign-in-and-transport-security.md
[ADR-076]: ADR-076-admin-and-dispatcher-data-stays-in-memory-on-devices.md
[ADR-088]: ADR-088-israel-local-time-for-every-date-and-schedule.md
[ADR-089]: ADR-089-interface-languages.md
[DL-17]: ../architecture/domain_layer/domain_layer_design.md#17-ubiquitous-language
[FR-124]: ../requirements/requirements.md#company-users-and-roles
[FR-125]: ../requirements/requirements.md#company-users-and-roles
[FR-182]: ../requirements/requirements.md#company-users-and-roles
[ISSUE-163]: https://github.com/NaimElijah/DispatchFlow/issues/163
[NFR-58]: ../requirements/requirements.md#nfr-58
[NFR-67]: ../requirements/requirements.md#nfr-67
[NFR-143]: ../requirements/requirements.md#nfr-143
[NFR-209]: ../requirements/requirements.md#nfr-209
[TS-1]: ../architecture/tech_stack.md#1-what-decides-close-calls
[TS-10]: ../architecture/tech_stack.md#10-web-app

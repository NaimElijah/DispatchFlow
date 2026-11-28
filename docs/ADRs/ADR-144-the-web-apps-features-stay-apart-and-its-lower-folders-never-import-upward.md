# ADR-144: The web app's features stay apart, its lower folders never import upward, and sign-in stands beside the shell

- **Status:** Accepted; extended by ADR-153 and ADR-161
- **Date:** 2026-11-25

## Context

[ADR-143] built the web app's shell (#163, #290). Its decision 2 sets one direction for imports:
`src/` itself, then `features/`, `routes/`, `components/` and `lib/`. A review after the merge
found four gaps:

- **The lint rule covered only part of that direction.** It refused an import into `features/`,
  `routes/` or `components/` from a lower folder, and nothing else. It let through:
  - a file below `src/` importing the modules that assemble the app (`router.ts`, `routeTree.ts`,
    `App.tsx`);
  - any import from inside `features/`, which no block covered;
  - app code importing `src/test/`;
  - a bare `../features` with no trailing slash.

  The review's example was `AppLayout` importing `routeTree.ts`. That makes a runtime cycle, and the
  page would render blank while `just check` stayed green.
- **Nothing stopped one feature importing another.** #203 and #224 work in `orders`, #225 in
  `planning`. A planning file importing an orders component while an orders file imports
  `planDate.ts` would make a cycle across features.
- **#206 (sign-in) had nowhere to go.** Its issue put the session and the route guard in
  `features/access/` and the guard in `src/routes/`. But a `routes/` file may not import a feature,
  and the root route's context could not name a type that lives in one.
- **ADR-143 decision 7 claims more than it settles.** It says "no package builds before another,
  which settles the build order [ADR-039] left open". That holds for hand-written packages. But
  `@dispatchflow/api-client` is generated ([ADR-043]), and `just web-check` runs lint and tests
  before any build.

Also decided on 25 November 2026: after signing in, a Dispatcher or an Admin returns to the page they
asked for, if it is in the app, and otherwise to `/`, which opens tomorrow's plan ([ADR-143]
decision 4). #206 had said today's plan.

## Decision

Decided on 25 November 2026.

1. **No feature imports another.** What two features need goes in `src/lib/` (code), in
   `src/components/` (UI) or in a shared package. A feature that needs another's page links to its
   route, which needs no import: `/plans` gives planning's own date to every other area.
2. **The direction holds for every static import and re-export, type-only ones included:**
   - nothing below `src/` imports the modules that assemble the app (`main`, `App`,
     `AppErrorBoundary`, `DocumentLanguage`, `i18n`, `router`, `routeTree`);
   - only test files import `src/test/`, and test files may import anything;
   - `import()` expressions are not checked. A route's `lazyRouteComponent` imports its own
     feature's page, which the rules allow anyway.
3. **ESLint enforces 1 and 2,** with `no-restricted-imports` in `src/frontend/web/eslint.config.js`.
   It has one block per feature, made from the folders of `src/features/` when ESLint loads it, so a
   new feature is covered without an edit. `src/test/importDirection.test.ts` lints one import per
   rule, refused or allowed, so a weakened rule fails a test.
4. **Sign-in stands beside the shell** ([ADR-143] decision 1, made concrete for #206):
   - The session (the access token holder) lives in `src/lib/`. `main.tsx` makes it, and it reaches
     the routes through the router's context, as the language setting does.
   - `shellRoute`'s `beforeLoad` reads it and sends a missing session to `/sign-in`, with the
     address it came from. So one guard covers every page.
   - The sign-in page and its route live in `src/features/access/`. The route is parented on
     `rootRoute`, outside the shell, and exported as its own list, which `routeTree.ts` places beside
     `shellRoute` rather than inside it.
   - After signing in, the app goes back to the address it came from, if it is one of the app's own
     paths, and otherwise to `/`.
5. **Writing HTML into the page fails ESLint** in `@dispatchflow/eslint-config`, in each form short
   of a computed property name: `dangerouslySetInnerHTML` as an attribute or a key, `innerHTML` and
   `outerHTML` assignments, `insertAdjacentHTML`, and `document.write`. `.jsx` files are linted too.
   Translations are not HTML-escaped, so markup built from them, or from a user's or the server's
   text, would reach the page as markup. `src/test/htmlSinks.test.ts` pins each form.
6. **ADR-143 decision 7 holds for hand-written packages.** A generated package, such as
   `@dispatchflow/api-client`, runs its generation before lint and test. The ADR that settles its
   generation ([ADR-043]'s open point, with #148) names that step.
7. **A missing subject shows the not-found page inside the shell.**
   - The router's `defaultNotFoundComponent` is the not-found page, and neither the root route nor
     `shellRoute` sets a `notFoundComponent`, which would show the page in place of the whole
     shell. So a loader that throws `notFound()` gets the page in its own route's place.
   - A page that throws `notFound()` while rendering is caught only by its own route's
     `notFoundComponent`, so that route sets it to the not-found page.
   - `src/routes/notFound.test.tsx` pins both.
8. **On an address no route knows, no navigation item is current** ([ADR-143] decision 4 said the
   planning item is current on every plan's page). The router would mark the item whose path the
   address begins with, so `/plans/2026-02-30` would mark Planning. Inside an area, a page that
   finds its subject missing keeps its area current, as the area's other pages do.
9. **The last-resort error page sets its own language and direction,** because when the first
   render fails `<html>` never gets them. It asks for a reload, since it offers no button.

## Consequences

- **Easy:**
  - A cycle through the assembling modules, a cross-feature import, or test code in the app now
    fails lint, at the line that adds it.
  - #206 adds sign-in without moving any area's routes.
  - A new feature folder is covered by the rules without a config edit.
- **Hard:**
  - Code two features share has to move to `lib/` or a package first, even when it is small.
  - `ISRAEL_TIME_ZONE` and the plan-date helpers stay in `features/planning/`. Another area that
    needs the zone waits for #173's `@dispatchflow/formatters`, or moves it to `lib/`.
- **Cost:** none in money. The rules use ESLint's built-in rule, with no new dependency.
- **Risk:**
  - `import()` expressions and `typeof import()` types are not checked; review catches them.
  - A computed key, such as `{ [name]: value }`, still writes `dangerouslySetInnerHTML` unseen. A
    `require-trusted-types-for 'script'` directive would close every form in the browser, but it
    changes [ADR-069]'s policy.

## Alternatives considered

- **`eslint-plugin-import`'s `no-restricted-paths`, or `eslint-plugin-boundaries`.** They read the
  resolved file instead of the import's text. Rejected: one more dependency, when the built-in rule
  covers the folders this app has.
- **Leaving the cross-feature rule to review.** Rejected: four parallel tickets touch three
  features in the coming weeks.
- **The session in `features/access/`, passed to the guard through a callback set at start.**
  Rejected: the root route's context type would still have to name it from a feature.
- **Today's plan after sign-in,** as #206 said. Rejected on 25 November 2026: it differs from `/`,
  and dispatchers plan tomorrow; today's plan is watched in Live Operations, in Semester B.

## Revisit when

- A feature truly needs another's code and moving it to `lib/` or a package would be wrong: state
  the exception here, in a new ADR.
- The app adds dynamic imports across folders: check them with `no-restricted-syntax` or a plugin.
- Text from users or the server reaches the screen and the CSP can take Trusted Types: add
  `require-trusted-types-for 'script'` through an ADR that amends [ADR-069].
- `@dispatchflow/api-client`'s generation is decided: record its place before lint and test.

[ADR-039]: ADR-039-one-pnpm-workspace-for-both-apps-and-shared-packages.md
[ADR-043]: ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-069]: ADR-069-sign-in-and-transport-security.md
[ADR-143]: ADR-143-the-web-app-shell-declares-routes-per-area-opens-in-english-and-renders-no-style-element.md

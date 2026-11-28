# ADR-161: The apps' client is generated before every check and never committed, from openapi-typescript and openapi-fetch pinned after their freeze

- **Status:** Accepted
- **Date:** 2026-11-28

## Context

[ADR-043] chose openapi-typescript for the apps' types and openapi-fetch for their client
([TS §12]). It left open whether generated code is committed or built each time, and [ADR-144]
decision 6 asked the ADR that settles it to name the generation step. [ADR-039] left the
generated package's build order to the workspace. #184's pull request (#312) gave the contract its
first two operations, `openPlan` and `requestSolve`, and [ADR-159] brought the web app's generator
with #148's second pull request, this one.

Until now the web app typed the `Problem` by hand ([ADR-153] decision 3):
- `problems.contract.test.ts` held the hand-written types to the contract;
- Vitest, and only Vitest, could read `contracts/client-api/v1/`.

[ADR-155]'s Risk waited on this pull request too: a `switch` over a generated union compiles with
a `kind` missing, and the shared ESLint configuration ran no typed rule.

A vetting of the two tools on 28 November 2026 found:
- **Both are frozen.** On 19 September 2026 their maintainer put openapi-typescript and
  openapi-fetch into maintenance mode (openapi-ts/openapi-typescript#2874). No further release
  will come, a security fix included, and users are asked to migrate. The repository isn't
  archived.
- **Their last versions are clean.** openapi-typescript 7.13.0, openapi-fetch 0.17.0 and
  `openapi-typescript-helpers` 0.1.0 were all released on 11 February 2026 and carry provenance
  attestations. None of them runs an install script or ships agent skills.
- **Licences and advisories:** all three are MIT. The 27 packages they add to the lockfile carry
  permissive licences: 22 MIT, 2 ISC, and one each of Apache-2.0 (`yaml-ast-parser`), Python-2.0
  (`argparse`) and MIT or CC0-1.0. OSV-Scanner 2.7.0 finds no advisory in the workspace's
  lockfile, with no ignore file applied.
- **openapi-typescript runs only at build time,** on our own contract. Its peer is `typescript
  ^5.x`; it ran on the workspace's 6.0.3, and the types it wrote type-check.
- **openapi-fetch is what ships to the browser:** about 4.4 KB gzipped, with one types-only
  dependency, `openapi-typescript-helpers`. It builds a `Request` and calls the given `fetch` with
  it, so a relative `baseUrl` fails in Node, where the tests run. It parses an error body that
  isn't JSON as text, but a success that isn't JSON fails with a bare `SyntaxError`.
- **The maintained alternative,** `@hey-api/openapi-ts` (MIT, last pushed 8 November 2026), is
  still 0.x, breaks between minors, and generates a runtime SDK of its own.
- **openapi-typescript writes no `pattern`,** so the types can't say what a `code` or a `traceId`
  looks like.
- **An inline type import isn't a type import.** Under the workspace's `verbatimModuleSyntax`,
  `import { type paths } from './generated/client-api.ts'` keeps `import {} from …`, and Vite's
  transformer, Oxc, keeps `import './generated/client-api.ts'`, which loads the file. It drops
  `import type`, `export type` and `export { type … } from`.

## Decision

Decided on 28 November 2026.

1. **The apps' client is generated at setup and before every check, never committed.**
   - `@dispatchflow/api-client`'s `gen` script writes `src/generated/client-api.ts` from
     `contracts/client-api/v1/openapi.yaml`, with read-only types, as the hand-written ones were.
   - Git, Prettier and ESLint ignore `src/generated/`.
   - `just web-gen` runs the script. `just web-setup` runs it after its install, and
     `just web-check` before lint and test, the step [ADR-144] decision 6 asked for. `just gen`
     runs it beside the core's `core-gen`.
   - This settles [ADR-043]'s open point for the apps, as [ADR-160] settled it for the core, and
     [ADR-039]'s build order: the package generates first, and its only build is its type check.
2. **The pair stays, pinned exactly, behind the package.**
   - openapi-typescript is 7.13.0 and openapi-fetch is 0.17.0.
   - Only `@dispatchflow/api-client` imports them: the apps see its `createCoreClient`, the
     generated types and the problem types.
   - openapi-fetch's runtime is one file of about 530 lines: a fix we need can be vendored into
     the package and patched there, keeping its API. A switch to another generator changes this
     package, the shape of each call and the web app's `answerOf`, not the generated types or how
     a problem is read.
   - Dependabot proposes no release of the three packages: upstream publishes none, so one that
     appears comes in by hand, after a look. `pnpm-workspace.yaml` accepts openapi-typescript's
     TypeScript 5 peer, so `pnpm peers check` still names any other unmet peer.
3. **`createCoreClient` takes the app's origin, fetch, name and release.**
   - It serves under `/api/v1` at that origin ([ADR-160]), and refuses an origin that carries a
     path, which the client would drop.
   - It sets `X-App`, `X-App-Version` and `Accept: application/json, application/problem+json` on
     every call ([ADR-082], [ADR-110], [ADR-160] decision 4).
   - It refuses a release the core wouldn't keep, which it would drop from every record without
     saying so: three numbers of one to nine digits, as the core's `Caller` reads them, with no
     leading zero, as its `AppVersion` then requires.
   - A call whose path value is `..`, `.` or empty throws before anything is sent. A screen passes
     route parameters, which a link can set, and the URL would resolve such a value to another
     path.
   - Its type gives `error` as the contract's `Problem`, but nothing checks it: a proxy's page
     arrives as text, an empty answer as `undefined`. An app reads every answer through its own
     guard.
   - The web app's one client is `src/lib/coreClient.ts`. It uses the page's origin, sends through
     `fetchFromCore` ([ADR-154]) and names the release from `web/package.json`'s version, which
     Vite defines when it builds. A screen reads each answer with `answerOf`.
   - `fetchFromCore` refuses a success whose body is in a media type the request's `Accept`
     doesn't name, such as a page that something in front of the core serves: it fails as
     `unexpected`, as [ADR-154] reads any answer that isn't the core's, not as a `SyntaxError`.
4. **The problem types come from the contract, and the three values the types can't carry live
   beside them.**
   - `Problem`, `ProblemItem` and `ProblemParameter` are the generated schemas.
   - `PROBLEM_CODE_PATTERN`, `TRACE_ID_PATTERN` and `PARAMETER_PLACES` move into the package.
     Its own `problems.contract.test.ts` holds the two patterns to the contract it is generated
     from, and the generated union holds the places.
   - The web app's `isProblem` stays, since a proxy's 502 is no problem, until the phone app's
     first call, when [ADR-154] foresees the refusal reader moving into a shared package. It now
     also requires `type` to be `about:blank`, the one value the contract allows ([ADR-140]
     decision 5), and `detail` and `instance` to be strings when they are there.
     `problems.test.ts` holds its fields to the generated types.
   - This ends [ADR-153] decision 3: the web app's drift test and Vitest's reading of the contract
     are gone.
5. **Only the package talks to the server.**
   - ESLint refuses every other way the web app's source could send a request: the global `fetch`
     and `XMLHttpRequest`, `fetch` read from `window`, `globalThis` or `self`, and
     `navigator.sendBeacon`. `lib/coreCalls.ts`, which the client sends through, and the tests,
     which call `fetch` to try MSW itself, are exempt.
   - Only `lib/coreClient.ts` and the tests import `fetchFromCore`: a call made with it alone
     names no app and is checked against no type.
   - `src/test/fetchGlobal.test.ts` pins each form. The event stream reads with plain `fetch`
     ([TS §10]), so #193 adds its one file to the exemption.
6. **Nothing but types comes from the generated file, so the dev image generates nothing.**
   - In the package, `no-restricted-imports` refuses a value imported from `src/generated/` and
     allows a type, and `@typescript-eslint/no-import-type-side-effects` refuses the inline form,
     which loads the file. `generatedImports.test.ts` pins both.
   - Vite drops type imports, so its dev server and its build run without the file. `vite build`
     was tried with the file removed.
   - `web/Dockerfile.dev` and its Compose service need no contract and no generation step.
     `src/frontend/.dockerignore` keeps a host's generated copy out of the image.
7. **ESLint reads the types.**
   - `@dispatchflow/eslint-config` exports `typeChecked(tsconfigRootDir, outsideEveryTsconfig)`,
     which every TypeScript package and the web app add to their configuration. It turns on
     typescript-eslint's project service. A package names its `.ts` files that no tsconfig
     includes, such as the formatters' `vitest.config.ts`: the project service refuses a file
     that is in both.
   - `@typescript-eslint/switch-exhaustiveness-check` fails a `switch` over a union that misses a
     member, even with a `default`, so a new generated `kind` can't fall through unseen. This
     settles [ADR-155]'s Risk. The rule found one such `switch` at once: `@dispatchflow/schemas`'s
     `refusalOf` now names Zod's other issue codes, and keeps its `default` only for a code a
     later Zod sends that its types don't name yet.
   - The lint tests that lint code at paths no file has yet use `src/test/lintAs.ts`. It is the
     same configuration without the type-reading layer, whose rules those tests don't pin, and it
     fails a test on a parse error rather than finding no message. `switchExhaustiveness.test.ts`
     pins the layer itself, at a file that exists.

## Consequences

- **Easy:** a screen calls `coreClient.POST('/plans', { body })`, typed by the contract, and a
  contract change that breaks a call fails `tsc`.
- **Easy:** no generated file in a diff, and no pull request that changes the contract but forgets
  to regenerate.
- **Easy:** a new `kind` in a generated union fails the lint wherever a `switch` misses it.
- **Hard:** a fresh clone has no types until `just web-setup` runs. The editor shows errors until
  then, and `tsc` or ESLint run by hand after a contract change needs `just web-gen` first.
  Vitest needs nothing: it drops type imports.
- **Hard:** the two tools get no fix from upstream. A vulnerability or a TypeScript release they
  don't support is ours to handle, by vendoring or by switching, and a switch reaches every call.
- **Hard:** a value from the generated file, such as an enum the generator could write, needs the
  dev image to generate first. The lint rule refuses one until then.
- **Hard:** an answer's `Location` carries the whole path, `/api/v1` included, while the client's
  paths start below it, so a call that follows one through the client would double the prefix.
  The first operation whose answer must be followed settles how.
- **Cost:** typed linting made the workspace's lint take about 19 s rather than 14 s, and the web
  app's about 11 s rather than 8 s, on a laptop.

## Alternatives considered

- **Committing the generated types.** Rejected: every contract change would also carry a generated
  diff, which a reviewer skims and a merge conflicts on. A forgotten regeneration would also pass
  review. [ADR-160] made the same choice for the core.
- **Switching to `@hey-api/openapi-ts` now.** Rejected for this pull request: still 0.x, with
  breaking changes between minors, and its generated SDK and client would mean a new vetting round
  and a redesign of the factory and of [ADR-154]'s `answerOf`. It stays the first candidate when a
  revisit trigger fires.
- **openapi-typescript for the types and a client of our own.** Rejected: openapi-fetch's typed
  paths, parameters and answers are the hard part, and writing and testing our own buys nothing
  while 0.17.0 has no known flaw.
- **A façade over the typed client, so a switch reaches no screen.** Rejected: nothing pulls it
  now. It becomes right if a switch becomes likely: a vulnerability a vendored patch can't fix, or
  a phone app that needs another client.
- **Moving `isProblem` into the package now.** Rejected: until the phone app's first call, when
  [ADR-154] foresees the refusal reader moving into a shared package, only the web app reads an
  answer.
- **Generating in the web dev image from the contract, as a named build context.** Rejected: the
  dev server needs only what Vite runs, and type imports are not that. It would add a Compose
  context and a rebuild watch for no change in what the browser gets.
- **`allowDefaultProject: ['*.config.ts']` in the shared configuration.** Rejected: the web app's
  `tsconfig.node.json` includes `vite.config.ts`, and the project service fails a file that is
  both included and allowed by default.
- **Counting a `default` as exhaustive.** Rejected: a `default` is where a new `kind` would land
  without anyone deciding what it means.
- **Reading a body as a problem only when its media type is `application/problem+json`.**
  Rejected for now: only the same origin answers, a fooled guard can show no text of its own, and
  no test yet checks that every refusal path of the core sends that media type.

## Revisit when

- `just audit` (OSV-Scanner) reports a vulnerability in openapi-fetch 0.17.0, openapi-typescript
  7.13.0 or `openapi-typescript-helpers` 0.1.0. GitHub's dependency graph doesn't read the
  workspace's lockfile.
- A new release of any of the three appears.
- The workspace moves to TypeScript 7, and openapi-typescript's output or run breaks on it.
- A maintained successor reaches a stable 1.0 that generates the same types with no runtime of its
  own, or `@hey-api/openapi-ts` reaches 1.0.
- The phone app gets its client: weigh then whether it takes this package as it is.
- A screen needs a value, not a type, from the generated file.
- An operation's answer must be followed by its `Location`.
- `just web-check`'s lint takes more than a minute.

[ADR-039]: ADR-039-one-pnpm-workspace-for-both-apps-and-shared-packages.md
[ADR-043]: ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-082]: ADR-082-supported-phone-app-versions.md
[ADR-110]: ADR-110-audit-records-hold-only-ids-codes-enums-numbers-and-instants-record-refusals-and-are-kept-a-year.md
[ADR-140]: ADR-140-the-app-wide-web-package-sits-above-the-modules.md
[ADR-144]: ADR-144-the-web-apps-features-stay-apart-and-its-lower-folders-never-import-upward.md
[ADR-153]: ADR-153-the-web-apps-shared-form-refusal-and-table-pieces.md
[ADR-154]: ADR-154-the-audit-of-the-shared-web-pieces-amends-how-a-call-fails-a-notice-closes-and-a-range-reads.md
[ADR-155]: ADR-155-the-client-api-keeps-instants-and-local-times-apart-and-sends-a-list-that-may-grow-as-a-string.md
[ADR-159]: ADR-159-a-contract-change-goes-with-its-provider-and-the-consumers-that-call-what-it-changes.md
[ADR-160]: ADR-160-the-cores-generated-interfaces-take-a-dto-suffix-leave-absent-fields-out-and-are-served-under-a-literal-api-v1-prefix.md
[TS §10]: ../architecture/tech_stack.md#10-web-app
[TS §12]: ../architecture/tech_stack.md#12-shared-typescript-packages

# ADR-161: The apps' client is generated before every check and never committed, from openapi-typescript and openapi-fetch pinned after their freeze

- **Status:** Accepted
- **Date:** 2026-11-28

## Context

[ADR-043] chose openapi-typescript for the apps' types and openapi-fetch for their client
([TS §10]). It left open whether generated code is committed or built each time, and [ADR-144]
decision 6 asked the ADR that settles it to name the generation step. #184's pull request (#312)
gave the contract its first two operations, `openPlan` and `requestSolve`, and [ADR-159] brought
the web app's generator with #148's second pull request, this one.

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
- **Their last versions are clean.** 7.13.0 and 0.17.0, both of 11 February 2026, have no OSV
  advisory and carry provenance attestations. Neither runs an install script or ships agent skills.
- **openapi-typescript runs only at build time,** on our own contract. Its peer is `typescript
  ^5.x`; it ran on the workspace's 6.0.3, and the types it wrote type-check.
- **openapi-fetch is what ships to the browser:** about 4.4 KB gzipped, with one types-only
  dependency, `openapi-typescript-helpers`. It builds a `Request` and calls `fetch(request, init)`,
  so a relative `baseUrl` fails in Node, where the tests run.
- **The maintained alternative,** `@hey-api/openapi-ts` (MIT, last pushed 8 November 2026), is
  still 0.x, breaks between minors, and generates a runtime SDK of its own.
- **openapi-typescript writes no `pattern`,** so the types can't say what a `code` or a `traceId`
  looks like.

## Decision

Decided on 28 November 2026.

1. **The apps' client is generated at setup and before every check, never committed.**
   - `@dispatchflow/api-client`'s `gen` script writes `src/generated/client-api.ts` from
     `contracts/client-api/v1/openapi.yaml`.
   - Git, Prettier and ESLint ignore `src/generated/`.
   - `just web-setup` runs the script after its install. `just web-check` runs it before lint
     and test, the step [ADR-144] decision 6 asked for. `just gen` runs it as `web-gen`, beside
     the core's `core-gen`.
   - This settles [ADR-043]'s open point for the apps, as [ADR-160] settled it for the core.
2. **The pair stays, pinned exactly, behind the package.**
   - openapi-typescript is 7.13.0 and openapi-fetch is 0.17.0.
   - Only `@dispatchflow/api-client` imports them: the apps see its `createCoreClient`, the
     generated types and the problem types.
   - A switch then changes one package, not the screens. openapi-fetch's runtime is one file of
     about 530 lines: we can vendor it and patch it there if a fix is ever needed.
3. **`createCoreClient` takes the app's origin, fetch, name and release.**
   - It serves under `/api/v1` at that origin ([ADR-160]).
   - It sets `X-App`, `X-App-Version` and `Accept: application/json, application/problem+json` on
     every call ([ADR-082], [ADR-110], [ADR-160] decision 4).
   - It refuses a release that isn't three numbers: the core would drop it from every record
     without saying so.
   - The web app's one client is `src/lib/coreClient.ts`. It uses the page's origin, sends through
     `fetchFromCore` ([ADR-154]) and names the release from `web/package.json`'s version, which
     Vite defines when it builds. A screen reads each answer with `answerOf`.
4. **The problem types come from the contract, and the three values the types can't carry live
   beside them.**
   - `Problem`, `ProblemItem` and `ProblemParameter` are the generated schemas.
   - `PROBLEM_CODE_PATTERN`, `TRACE_ID_PATTERN` and `PARAMETER_PLACES` move into the package.
     Its own `problems.contract.test.ts` holds the two patterns to the contract it is generated
     from, and the generated union holds the places.
   - The web app's `isProblem` stays, since a proxy's 502 is no problem. It now also requires
     `type` to be `about:blank`, the one value the contract allows ([ADR-140] decision 5).
   - This ends [ADR-153] decision 3: the web app's drift test and Vitest's reading of the contract
     are gone.
5. **Only the package talks to the server.**
   - ESLint refuses the global `fetch` in the web app's source, except in `lib/coreCalls.ts`, which
     the client sends through, and in tests, which call it to try MSW itself.
   - The event stream reads with plain `fetch` ([TS §10]), so #193 adds its one file to that
     exception.
6. **Nothing but types comes from the generated file, so the dev image generates nothing.**
   - A lint rule in the package refuses a value imported from `src/generated/` and allows a type.
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
     `refusalOf` now names Zod's other issue codes rather than leaving them to its `default`.
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
  then, and running Vitest or `tsc` by hand needs `just web-gen` first.
- **Hard:** the two tools get no fix from upstream. A vulnerability or a TypeScript release they
  don't support is ours to handle, by vendoring or by switching.
- **Hard:** a value from the generated file, such as an enum the generator could write, needs the
  dev image to generate first. The lint rule refuses one until then.
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
- **Generating in the web dev image from the contract, as a named build context.** Rejected: the
  dev server needs only what Vite runs, and type imports are not that. It would add a Compose
  context and a rebuild watch for no change in what the browser gets.
- **`allowDefaultProject: ['*.config.ts']` in the shared configuration.** Rejected: the web app's
  `tsconfig.node.json` includes `vite.config.ts`, and the project service fails a file that is
  both included and allowed by default.
- **Counting a `default` as exhaustive.** Rejected: a `default` is where a new `kind` would land
  without anyone deciding what it means.

## Revisit when

- OSV or GitHub reports a vulnerability in openapi-fetch 0.17.0, openapi-typescript 7.13.0 or
  `openapi-typescript-helpers`.
- The workspace moves to TypeScript 7, and openapi-typescript's output or run breaks on it.
- A maintained successor reaches a stable 1.0 that generates the same types with no runtime of its
  own, or `@hey-api/openapi-ts` reaches 1.0.
- The phone app gets its client: weigh then whether it takes this package as it is.
- A screen needs a value, not a type, from the generated file.
- `just web-check`'s lint takes more than a minute.

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

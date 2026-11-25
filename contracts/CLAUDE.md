# Contracts

The versioned OpenAPI contracts between DispatchFlow's components ([ADR-006]). Both sides of
each contract depend on it, and neither side owns it.

| Contract | Provider | Consumers |
|---|---|---|
| `client-api/v1/openapi.yaml` | `src/backend/core` | `src/frontend/web`, `src/frontend/mobile` |
| `optimization-api/v1/openapi.yaml` | `src/backend/optimizer` | `src/backend/core` |

Both are OpenAPI 3.1. `client-api` is served under `/api/v1`.

## Rules

- Contract first. Change the contract before the code. The contract change, the provider and
  every consumer go in the same pull request.
- A request that breaks the contract is refused by validation generated from it, on both servers.
  Never hand-write a second check. A rule the contract cannot state, such as the limit on a day's
  orders (BR-101), stays with the domain ([ADR-073]).
- A breaking change gets a new major version in its own folder (`v2/`), next to `v1/`.
  Breaking changes include removing or renaming an operation or field, changing a type, making
  an optional field required, changing the status a code answers with, and a new enum value in a
  response, except `Problem.code`. Adding an optional field or a new operation is not breaking.
- `oasdiff` checks each pull request against its base branch for breaking changes, from January: a
  pull request into `dev` against `dev`, and `dev` into `main` against `main`, which holds what is
  released ([NFR-134]; [ADR-043], [ADR-094]). Until then, the reviewer catches them.
- Keep the `info.license` block: proprietary, all rights reserved ([ADR-017]).
- Code is generated on both sides of every contract, so no hand-written type crosses a boundary
  ([TS §1][TS-1], rule 5; [ADR-043]). Nobody edits generated code. No generator is set up yet,
  and the optimizer's health-check models are hand-written: the next operation either contract
  gains adds the generator, and the validation generated from it, to the provider and every
  consumer in the same pull request.
- Every `client-api` operation follows these conventions; [ADR-140] holds the reasons:
  - `x-capability` ([ADR-068]) and a stable `operationId`, which names the generated method and the
    audit record's operation. The core reads `traceparent` itself: no operation declares it;
  - exactly one tag, its module's package name and its resource (`planning-proposals`,
    `liveops-routes`), giving one `<Tag>Api` per tag. The event stream's operation carries
    `x-internal: true`, so the generator skips it and `web.stream` serves it by hand; it is still
    public and versioned, carries `x-capability` and is exempt from the tag rule;
  - `additionalProperties: false` on every object schema reachable from a request body
    (`unevaluatedProperties: false` under `allOf`), never reused in a response, so an undeclared
    property answers 400. Response schemas stay open, because an optional field added to one isn't
    breaking;
  - an app sends a new request property only once the core release that declares it won't be rolled
    back past. On the phone's writes a request property stays declared for the life of v1; elsewhere
    a retired one stays accepted and ignored, never removed in v1 ([ADR-082]);
  - RFC 9457 errors: `Problem` has `type` `about:blank`, `code`, `params` and `traceId` ([ADR-043];
    today's `Problem` lacks them). `code` is a string, never an enum, with the codes listed in its
    description, so a new code isn't breaking. A new one is a public name: it needs its entry there,
    and Hebrew and English text with a next step (NFR-144);
  - refusals that [NFR-92] and [NFR-145] require to look the same share one `code`, one status and
    empty `params`, and `params` never carries personal data, secrets or tokens. So the refresh
    token is a plain string, with no pattern, length or format;
  - statuses:
    - 400 only for a request that breaks the contract. A failed validation lists every failing field
      or row at once; an unreadable body, a duplicate key or an unknown property is reported alone,
      as the first one found. Each item has a pointer into the body, or the parameter's name and
      place, and a code, never the value ([ADR-043]). A missing required header, such as `If-Match`,
      answers 400;
    - 413, with its own code, for a body over its size limit; the proof and report operations'
      limits sit above the domain's own;
    - 422 for a refused rule; 401, with exactly `WWW-Authenticate: Bearer` and one code, for any
      refused access token (an open operation ignores `Authorization`); 403 for the role, step-up,
      own-route and `GRANT_LIMIT_EXCEEDED` refusals;
  - `ETag`, `If-Match` and `If-None-Match`: a failed `If-Match` or `If-None-Match` answers 412
    `STALE_VERSION`, any `StaleVersion` on such a call included; a stale version sent in a body, as
    accepting a proposal sends the plan's, answers 409 `STALE_VERSION`; neither is retried. A call
    with no precondition header, and any failed lock wait, is retried, then 503 with `Retry-After`
    ([ADR-057], [ADR-121]);
  - `Idempotency-Key` on driver reports, route starts and finishes, problem reports ([ADR-087],
    [ADR-121]) and position batches ([NFR-285]), and the client headers ([ADR-082]).
- The optimizer API is stateless: each call carries the whole problem. The travel-time matrix is
  base64 little-endian `int32` over distinct locations ([ADR-052](../docs/ADRs/ADR-052-each-plan-stores-the-matrix-and-values-it-was-solved-with.md)).
- Lint findings are fixed, not ignored. If a rule does not apply, add the exception to
  `.redocly.lint-ignore.yaml`, and say why in the pull request.

## Commands

| Command | What it does |
|---|---|
| `just contracts-check` | Lints both contracts with Redocly CLI 2.72.1, `recommended` ruleset |
| `npx --yes @redocly/cli@2.72.1 lint optimization-api@v1` | Lints one contract, run from this folder (aliases are in `redocly.yaml`) |

[ADR-006]: ../docs/ADRs/ADR-006-versioned-openapi-contracts.md
[ADR-017]: ../docs/ADRs/ADR-017-proprietary-licence.md
[ADR-043]: ../docs/ADRs/ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-057]: ../docs/ADRs/ADR-057-concurrent-changes-to-a-plan-use-optimistic-locking.md
[ADR-068]: ../docs/ADRs/ADR-068-every-web-handler-is-authorized-by-role-from-the-contract.md
[ADR-073]: ../docs/ADRs/ADR-073-runtime-contract-validation.md
[ADR-082]: ../docs/ADRs/ADR-082-supported-phone-app-versions.md
[ADR-087]: ../docs/ADRs/ADR-087-driver-reports-sync-through-an-idempotent-outbox.md
[ADR-121]: ../docs/ADRs/ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[ADR-094]: ../docs/ADRs/ADR-094-each-pull-requests-contract-check-compares-against-its-base-branch.md
[ADR-140]: ../docs/ADRs/ADR-140-the-app-wide-web-package-sits-above-the-modules.md
[NFR-92]: ../docs/requirements/requirements.md#nfr-92
[NFR-134]: ../docs/requirements/requirements.md#nfr-134
[NFR-145]: ../docs/requirements/requirements.md#nfr-145
[TS-1]: ../docs/architecture/tech_stack.md#1-what-decides-close-calls
[NFR-285]: ../docs/requirements/requirements.md#nfr-285

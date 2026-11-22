# Contracts

The versioned OpenAPI contracts between DispatchFlow's components ([ADR-006]). Both sides of
each contract depend on it, and neither side owns it.

| Contract | Provider | Consumers |
|---|---|---|
| `client-api/v1/openapi.yaml` | `src/backend/core` | `src/frontend/console`, `src/frontend/driver-app` |
| `optimization-api/v1/openapi.yaml` | `src/backend/optimizer` | `src/backend/core` |

Both are OpenAPI 3.1. Today `client-api`, served under `/api/v1`, declares no operation yet, and
`optimization-api` has only `GET /health/liveness`.

## Rules

- Contract first. Change the contract before the code. The contract change, the provider and
  every consumer go in the same pull request.
- A request that breaks the contract is refused by validation generated from it, on both servers.
  Never hand-write a second check. A rule the contract cannot state, such as the limit on a day's
  orders (BR-101), stays with the domain ([ADR-073]).
- A breaking change gets a new major version in its own folder (`v2/`), next to `v1/`.
  Breaking changes include removing or renaming an operation or field, changing a type, and
  making an optional field required. Adding an optional field or a new operation is not
  breaking.
- `oasdiff` checks each pull request against its base branch for breaking changes, from January: a
  pull request into `dev` against `dev`, and `dev` into `main` against `main`, which holds what is
  released ([NFR-134]; [ADR-043], [ADR-094]). Until then, the reviewer catches them.
- Keep the `info.license` block: proprietary, all rights reserved ([ADR-017]).
- Code is generated on both sides of every contract, so no hand-written type crosses a boundary
  ([TS §1][TS-1], rule 5; [ADR-043], Accepted). Neither frontend has a generator yet; they will
  generate their client from `client-api` once it has operations. Nobody edits generated code.
- Every `client-api` operation follows these conventions:
  - `x-capability` on every operation ([ADR-068]);
  - RFC 9457 errors with `code`, `params` and `traceId` ([ADR-043]; today's `Problem` lacks them);
  - refusals that [NFR-92] and [NFR-145] require to look the same share one `code`, one status and
    empty `params`, and `params` never carries personal data, secrets or tokens;
  - a new error `code` is a public name: it needs a contract entry, and Hebrew and English text with
    a next step ([ADR-043], NFR-144);
  - a validation failure lists every field or row at once ([ADR-043]);
  - `ETag` and `If-Match`, answering 412 `STALE_VERSION` on a stale `If-Match`; a call that sent no
    version is retried, then answered 503 with `Retry-After` ([ADR-057], [ADR-121]);
  - `Idempotency-Key` on driver reports, route starts and finishes, problem reports ([ADR-087],
    [ADR-121]) and position batches ([NFR-285]),
    and the client headers ([ADR-082]).
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
[NFR-92]: ../docs/requirements/requirements.md#nfr-92
[NFR-134]: ../docs/requirements/requirements.md#nfr-134
[NFR-145]: ../docs/requirements/requirements.md#nfr-145
[TS-1]: ../docs/architecture/tech_stack.md#1-what-decides-close-calls
[NFR-285]: ../docs/requirements/requirements.md#nfr-285

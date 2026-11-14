# Contracts

The versioned OpenAPI contracts between DispatchFlow's components ([ADR-006]). Both sides of
each contract depend on it, and neither side owns it.

| Contract | Provider | Consumers |
|---|---|---|
| `client-api/v1/openapi.yaml` | `src/backend/core` | `src/frontend/console`, `src/frontend/driver-app` |
| `optimization-api/v1/openapi.yaml` | `src/backend/solver` | `src/backend/core` |

Both are OpenAPI 3.1. Today `client-api`, served under `/api/v1`, declares no operation yet, and
`optimization-api` has only `GET /health/liveness`.

[ADR-037] (Accepted) renames the files to `core-api.v1.yaml` and
`optimizer-api.v1.yaml` in this folder, in the M0 restructure. They are not renamed yet.

## Rules

- Contract first. Change the contract before the code. The contract change, the provider and
  every consumer go in the same pull request.
- A breaking change gets a new major version in its own folder (`v2/`), next to `v1/`.
  Breaking changes include removing or renaming an operation or field, changing a type, and
  making an optional field required. Adding an optional field or a new operation is not
  breaking. [ADR-037] changes this with the rename: the new major is a new file, such as
  `core-api.v2.yaml`, next to `core-api.v1.yaml`.
- `oasdiff` checks every change against `main`, which holds what is released ([NFR-134]), for
  breaking changes, from January ([PL §6.2][PL-62]). Until then, the reviewer catches them.
  [ADR-043] names `main` only; whether pull requests into `dev` also compare against `dev` is
  open.
- Keep the `info.license` block: proprietary, all rights reserved ([ADR-017]).
- Code is generated on both sides of every contract, so no hand-written type crosses a boundary
  ([TS §1][TS-1], rule 5; [ADR-043], Accepted). Neither frontend has a generator yet; they will
  generate their client from `client-api` once it has operations. Nobody edits generated code.
- Every `client-api` operation follows the conventions of [PL §6.2][PL-62]:
  - `x-capability` on every operation;
  - RFC 9457 errors with `code`, `params` and `traceId` (today's `Problem` lacks these three);
  - refusals that [NFR-92] and [NFR-145] require to look the same share one `code`, one status and
    empty `params`, and `params` never carries personal data, secrets or tokens;
  - `ETag` and `If-Match`, answering 412 `STALE_VERSION` on a stale version;
  - `Idempotency-Key` where the section names it;
  - the client headers.
- The optimizer API is stateless: each call carries the whole problem. The travel-time matrix is
  base64 little-endian `int32` over distinct locations ([PL §6][PL-6], [PL §6.1][PL-61]).
- Lint findings are fixed, not ignored. If a rule does not apply, add the exception to
  `.redocly.lint-ignore.yaml`, and say why in the pull request.

## Commands

| Command | What it does |
|---|---|
| `just contracts-check` | Lints both contracts with Redocly CLI 2.72.1, `recommended` ruleset |
| `npx --yes @redocly/cli@2.72.1 lint optimization-api@v1` | Lints one contract, run from this folder (aliases are in `redocly.yaml`) |

[ADR-006]: ../docs/ADRs/ADR-006-versioned-openapi-contracts.md
[ADR-017]: ../docs/ADRs/ADR-017-proprietary-licence.md
[ADR-037]: ../docs/ADRs/ADR-037-flat-layout-under-src-with-deploy-and-infra.md
[ADR-043]: ../docs/ADRs/ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[NFR-92]: ../docs/requirements/requirements.md#nfr-92
[NFR-134]: ../docs/requirements/requirements.md#nfr-134
[NFR-145]: ../docs/requirements/requirements.md#nfr-145
[TS-1]: ../docs/architecture/tech_stack.md#1-what-decides-close-calls
[PL-6]: ../docs/init_impl_ref/technical_implementation_plan.md#6-contracts
[PL-61]: ../docs/init_impl_ref/technical_implementation_plan.md#61-optimizer-api-v1
[PL-62]: ../docs/init_impl_ref/technical_implementation_plan.md#62-core-api-conventions

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
- A breaking change gets a new major version in its own folder (`v2/`), next to `v1/`. Breaking
  changes include removing or renaming an operation or field, changing a type, making a request
  field required or a response field optional, changing the status a code answers with, and a new
  enum value or `oneOf` kind in a response, except `Problem.code`. Adding an optional field or an
  operation isn't.
- `oasdiff` checks each pull request against its base branch for breaking changes, from January: a
  pull request into `dev` against `dev`, and `dev` into `main` against `main`, which holds what is
  released ([NFR-134]; [ADR-043], [ADR-094]). Until then, the reviewer catches them.
- Keep the `info.license` block: proprietary, all rights reserved ([ADR-017]).
- Code is generated on both sides of every contract, so no hand-written type crosses a boundary
  ([TS §1][TS-1], rule 5; [ADR-043]). Nobody edits generated code. No generator is set up yet,
  and the optimizer's health-check models are hand-written: the next operation either contract
  gains adds the generator, and the validation generated from it, to the provider and every
  consumer in the same pull request.
- Every `client-api` operation follows the conventions in `client-api/CLAUDE.md`.
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
[ADR-073]: ../docs/ADRs/ADR-073-runtime-contract-validation.md
[ADR-094]: ../docs/ADRs/ADR-094-each-pull-requests-contract-check-compares-against-its-base-branch.md
[NFR-134]: ../docs/requirements/requirements.md#nfr-134
[TS-1]: ../docs/architecture/tech_stack.md#1-what-decides-close-calls

# Contracts

The versioned OpenAPI contracts between DispatchFlow's components ([ADR-006]). Both sides of
each contract depend on it, and neither side owns it.

| Contract | Provider | Consumers |
|---|---|---|
| `client-api/v1/openapi.yaml` | `src/backend/core` | `src/frontend/console`, `src/frontend/driver-app` |
| `optimization-api/v1/openapi.yaml` | `src/backend/solver` | `src/backend/core` |

Both are OpenAPI 3.1. Today `client-api`, served under `/api/v1`, holds only the two placeholder
`Example` operations (`/examples/widgets`), which go when the first real context arrives. `optimization-api` has only
`GET /health/liveness`.

## Rules

- Contract first. Change the contract before the code. The contract change, the provider and
  every consumer go in the same pull request.
- A breaking change gets a new major version in its own folder (`v2/`), next to `v1/`.
  Breaking changes include removing or renaming an operation or field, changing a type, and
  making an optional field required. Adding an optional field or a new operation is not
  breaking.
- Keep the `info.license` block: proprietary, all rights reserved ([ADR-017]).
- The frontends generate their client from `client-api`, and nobody edits generated code.
- Lint findings are fixed, not ignored. If a rule does not apply, add the exception to
  `.redocly.lint-ignore.yaml`, and say why in the pull request.

## Commands

| Command | What it does |
|---|---|
| `just contracts-check` | Lints both contracts with Redocly CLI 2.72.1, `recommended` ruleset |
| `npx --yes @redocly/cli@2.72.1 lint optimization-api@v1` | Lints one contract, run from this folder (aliases are in `redocly.yaml`) |

[ADR-006]: ../docs/ADRs/ADR-006-versioned-openapi-contracts.md
[ADR-017]: ../docs/ADRs/ADR-017-proprietary-licence.md

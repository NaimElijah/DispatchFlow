# ADR-006: Every API between components is a versioned OpenAPI contract, written first

- **Status:** Accepted; extended by ADR-026 and ADR-043; amended by ADR-037 and ADR-123
- **Date:** 2026-10-30

## Context

DispatchFlow's components are written in three languages and meet at two HTTP boundaries
([ADR-002], [ADR-003]):

- **The client API** between the core backend and both frontends, the dispatcher console and
  the driver app.
- **The optimization API** between the core backend and the optimizer service.

[NFR-21] requires every such API to be described by a versioned OpenAPI contract, with both
sides tested against it. Four forces shape how:

- **Each contract has two sides in different languages.** The client API is served by Java and
  consumed by TypeScript. The optimization API is served by Python and consumed by Java.
- **Consumers should not wait for the provider.** A frontend can build against a contract
  before the endpoint behind it exists.
- **A contract change touches several components** and has to land with every side it affects
  ([ADR-008]).
- **A released contract must be able to change** without breaking clients that still use the
  old shape.

## Decision

We write each contract first, as an OpenAPI 3.1 file, and keep it in a versioned folder at the
repository root.

- **Two contracts, each at the root.**
  - `contracts/client-api/v1/openapi.yaml` sits between the core and both clients.
  - `contracts/optimization-api/v1/openapi.yaml` sits between the core and the optimizer.
  - Both files declare `openapi: 3.1.0`. Neither side owns a contract, so they belong to no
    component ([ADR-008]).
- **Contract-first.** A contract changes in the same pull request as every side it touches: the
  provider, and each consumer. The provider implements what the contract says. The contract
  does not follow the code.
- **The URL carries the major version.** The client API is served under `/api/v1`, its
  `servers` entry, so a `v2` can be served beside it and an installed driver app keeps calling
  the version it was built against. The optimization API has only a health probe today, and
  core and the optimizer deploy together, so it has no prefix yet.
- **A breaking change gets a new major-version folder.** `v2/` sits beside `v1/`, so both
  can exist at once.
- **Frontends generate their client from the contract.** They never write or edit it by hand
  (`src/frontend/CLAUDE.md`). The generator is not chosen yet.
- **Both sides are tested against the contract** ([NFR-21]). No test does that yet: the
  [code structure](../architecture/code-structure.md#where-the-tests-live) says so. Today the
  client API declares only the two placeholder `Example` operations of the example context, and
  the optimization API declares one, `GET /health/liveness`.
- **Every error is an RFC 9457 problem.** Each operation declares its expected errors and a
  `default` problem response for anything else, which core's application-wide fallback answers.
- **The contracts are linted.** Redocly CLI 2.72.1 checks both with its `recommended` ruleset
  (`contracts/redocly.yaml`), run by `just contracts-check` ([ADR-009]) and by the `contracts`
  job in CI ([ADR-015]). One problem is ignored: the liveness probe has no 4XX response. It is
  listed in `contracts/.redocly.lint-ignore.yaml`.
- **Each contract carries the project licence** in its `info.license` block, as [ADR-017]
  describes.

## Consequences

- **Easy:** a frontend developer can start on an endpoint before the backend implements it.
- **Easy:** a contract change shows up as a diff in one file, which a reviewer can read
  without reading either implementation.
- **Easy:** a breaking change does not force every consumer to move at once, because `v1/`
  keeps working beside `v2/`.
- **Hard:** the generator for the frontend client is not chosen. Until it is, no client is
  generated.
- **Hard:** the check that both sides conform to the contract does not exist yet. Until it
  does, nothing stops a provider and its contract from drifting apart ([NFR-21]).
- **Hard:** two major versions side by side mean two files to keep correct, and a provider that
  serves both.
- **Hard:** the one ignored lint problem is an exception to remove or defend, not forget.
- **Cost:** every change to an endpoint starts with an edit to the contract, even a small one.

## Alternatives considered

- **Code-first: generate the specifications from the Spring and FastAPI code.** Rejected: there
  would be two providers in two languages, each with its own generator. Consumers would wait for
  the implementation. The contract would follow the code instead of being reviewed first.
- **gRPC or GraphQL.** Rejected: both contracts use one REST and JSON style, and nothing in
  [requirements.md](../requirements/requirements.md) asks for streaming RPC or graph queries.
  Real-time push to the dispatcher console and the driver app is a separate decision that is
  still open ([Q-04]): a WebSocket or server-sent events channel would be described beside
  these contracts, or in a third one. This ADR does not settle it.
- **The contracts inside the provider's folder.** Rejected in [ADR-008]: both sides depend on a
  contract, and neither owns it.

## Revisit when

- The generator for the frontend client is chosen. Name it here or in its own ADR.
- [Q-04] settles on a real-time channel. Decide whether an OpenAPI contract describes it, or
  whether it needs a different description.
- A consumer outside the repository needs a contract. Publish the contracts as a versioned
  artefact ([ADR-008]).

[ADR-002]: ADR-002-core-backend-and-separate-optimization-service.md
[ADR-003]: ADR-003-dispatcher-console-and-driver-app.md
[ADR-008]: ADR-008-monorepo-layout-under-src.md
[ADR-009]: ADR-009-just-as-the-single-entry-point.md
[ADR-015]: ADR-015-ci-one-workflow-one-job-per-component.md
[ADR-017]: ADR-017-proprietary-licence.md
[NFR-21]: ../requirements/requirements.md#nfr-21
[Q-04]: ../requirements/requirements.md#open-questions

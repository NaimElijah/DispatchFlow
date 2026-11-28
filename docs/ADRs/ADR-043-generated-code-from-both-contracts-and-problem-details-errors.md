# ADR-043: Code is generated from both contracts on both sides, and errors are RFC 9457 Problem Details

- **Status:** Accepted; extended by ADR-068, ADR-073, ADR-087, ADR-094, ADR-140 and ADR-161; amended by ADR-123, ADR-153 and ADR-160
- **Date:** 2026-11-13

## Context

[ADR-006] made every API between components a versioned OpenAPI 3.1 contract, written first. It
left four things open or unchecked, and the repository shows where each stands today:

- **No generator is chosen.** ADR-006 says "The generator is not chosen yet", and no client is
  generated. `contracts/CLAUDE.md` says the frontends generate their client, and nobody edits
  generated code.
- **Nothing tests either side against its contract.** `just contracts-check` lints both files with
  Redocly CLI and does no more ([NFR-162]).
- **The optimization API has no version prefix.** ADR-006: "it has no prefix yet". It declares one
  operation, `GET /health/liveness`. The client API is served under `/api/v1` and declares no
  operation.
- **Errors are RFC 9457 problems, with no code a client can act on.** The client contract's
  `Problem` schema has `type`, `title`, `status`, `detail` and `instance`. The core enables Spring
  MVC's problem details and answers the rest through `UnexpectedErrorHandler`.

The proposed design adds forces:

- **No hand-written type crosses a boundary.** Code is generated on both sides of every contract
  ([TS §1], rule 5).
- **A screen never shows a stack trace or a bare internal code.** Every error code has Hebrew and
  English text and a next step ([NFR-69], [NFR-143], [NFR-144]). The apps need a stable code to
  translate.
- **The previous phone app version keeps working** ([PL §6.2], citing [NFR-44] and [NFR-134]).
- **We own both sides of both contracts** ([TS §17.1], the *Contracts* row).

## Decision

Contract-first stands ([ADR-006]). Both contracts stay OpenAPI 3.1 files, and the file names are
[ADR-037]'s. This ADR names the generators, the version scheme, the error shape and the checks.

- **Generators**, one per side of each contract ([TS §6], the *Web adapter* and *HTTP clients*
  rows; [TS §7], the *HTTP adapter* row; [TS §12], the *API client* row):

  | Contract and side | Generator | Output |
  |---|---|---|
  | Client API, the core serves it | openapi-generator `spring`, with `interfaceOnly`, `useSpringBoot4` and `useJackson3` | Java interfaces for Spring MVC |
  | Optimization API, the core calls it | openapi-generator `java`, RestClient library | The optimizer client |
  | Optimization API, the optimizer serves it | datamodel-code-generator | Pydantic v2 models, used only in the HTTP adapter |
  | Client API, both apps call it | openapi-typescript and openapi-fetch | Types and a small client in `@dispatchflow/api-client` |

  `just gen` regenerates the clients from both contracts ([PL §5]). Generated types stop at the
  adapter and never reach the domain ([TS §2.1], rule 4).
- **Path versions.** The client API is served under `/api/v1`, through Spring Framework 7's API
  versioning ([TS §6], the *API conventions* row). The optimizer's operations sit under `/v1`
  ([PL §6.1]). A breaking change to a call the phone app makes gets a new path version
  ([PL §6.2]).
- **Errors are RFC 9457 Problem Details with three additions:** a stable `code`, `params` for the
  message template, and the `traceId` ([PL §6.2]).
  - Both apps translate `code` through `@dispatchflow/i18n`. No stack trace or bare code reaches a
    screen.
  - A validation failure lists every field or row at once.
  - **Refusals that must look the same share one `code`, one status and empty `params`.** That
    covers the refusals [NFR-92] (every refusal of a refresh token looks the same) and [NFR-145]
    (sign-in and recovery answer alike whether or not the address exists, and a deactivated or
    role-less account gets a wrong password's answer) require.
  - **`params` never carries personal data, a secret or a token.** [NFR-19] keeps personal data out
    of logs, and [NFR-163] keeps it, credentials and tokens out of reported client errors. No row
    yet says it of an API error body, so this is the ADR's rule, in the spirit of both. The team
    confirms it.
- **Breaking changes are caught** by `oasdiff` against `main`, from the January break ([PL §6.2],
  [TS §18]). `main` holds what is released, and so what phones in the field run ([NFR-134]).
  Pull requests here go into `dev`, so a pull request into `dev` also reports `dev`'s unreleased
  breaking changes. **Open:** whether pull requests into `dev` compare against their base branch
  too, so that each reports only its own change.
- **Provider tests are generated from both contracts** with Schemathesis, from the January break
  ([TS §17.1], [TS §18]). Redocly lint stays.
- **Pact is not used.** Consumer-driven contracts suit teams that do not own both sides. We do, so
  the OpenAPI file already is the contract ([TS §17.1]).
- **Recorded in their own ADRs.** Three decisions that touch the contracts are settled elsewhere:
  authorizing handlers from the contract ([ADR-068]), idempotency keys on driver reports
  ([ADR-087]), and runtime validation of requests against the contract ([ADR-073], [NFR-56]).

## Consequences

- **Easy:** a contract change and every generated client change in one pull request ([TS §4]).
- **Easy:** the Pydantic models cannot drift from the contract ([TS §7]), and the frontends get
  types with "a tiny runtime" ([TS §12]).
- **Easy:** a client reads one error shape. A custom one would need a parser in every client
  ([TS §6]).
- **Easy:** ADR-006's open generator question closes.
- **Hard:** four generator setups in three languages, each with its own version and quirks.
- **Hard:** a `code` is a public name. Each new one needs a contract entry and Hebrew and English
  text with a next step ([NFR-144]).
- **Hard:** the sources do not say whether generated code is committed or built each time.
  `just gen` is written with that answer.
- **Cost:** a generation step in every build, and Schemathesis and `oasdiff` runs in CI from
  January.
- **Cost:** six new tools: openapi-generator, datamodel-code-generator, openapi-typescript,
  openapi-fetch, Schemathesis and `oasdiff`. The sources give no licence or maintenance status for
  them. Record both for each, against the licence allow-list ([TS §18]), before adding it, as
  `.claude/CLAUDE.md` requires of every new dependency.
- **Risk:** the Spring generator's `useSpringBoot4` and `useJackson3` options are new. [TS §24]
  cites an openapi-generator pull request for them, and no source names the release that carries
  them. Not verified.
- **Risk:** between the Alpha and the January break nothing automatic catches a breaking change.
  Until then a reviewer reads the contract diff ([ADR-006]). The phone app does not exist before M10
  ([PL §3.3]), so no older version is in the field meanwhile.

## Alternatives considered

- **Code-first: generate the contract from the Spring code.** Rejected, as in [ADR-006]: the Python
  service and the phone app need a reviewed contract before the code ([TS §6]).
- **Hand-written DTOs and clients.** Rejected: [TS §1], rule 5.
- **orval, or openapi-generator's TypeScript output, for the apps.** Rejected: orval generates
  hooks and more code, and the TypeScript output is bulky classes ([TS §12]).
- **Pact.** Rejected: see the decision.
- **A custom error JSON.** Rejected: every client would have to learn it ([TS §6]).
- **gRPC.** Rejected: ADR-006 chose OpenAPI, and gRPC is harder to inspect with curl or Bruno
  ([TS §7]).

## Revisit when

- A consumer we do not own appears, outside the team or the repository. Weigh Pact then, and the
  publication of the contracts that ADR-006 anticipates.
- The Spring Boot 4.2 upgrade in the January break breaks the `spring` generator's output. Revisit
  the generator, or pin it.
- `oasdiff` flags a change the phone app needs, and a new path version is not enough to keep the
  previous app version working ([NFR-134]).

[ADR-006]: ADR-006-versioned-openapi-contracts.md
[ADR-037]: ADR-037-flat-layout-under-src-with-deploy-and-infra.md
[ADR-068]: ADR-068-every-web-handler-is-authorized-by-role-from-the-contract.md
[ADR-073]: ADR-073-runtime-contract-validation.md
[ADR-087]: ADR-087-driver-reports-sync-through-an-idempotent-outbox.md
[NFR-44]: ../requirements/requirements.md#nfr-44
[NFR-56]: ../requirements/requirements.md#nfr-56
[NFR-69]: ../requirements/requirements.md#nfr-69
[NFR-19]: ../requirements/requirements.md#nfr-19
[NFR-92]: ../requirements/requirements.md#nfr-92
[NFR-134]: ../requirements/requirements.md#nfr-134
[NFR-143]: ../requirements/requirements.md#nfr-143
[NFR-144]: ../requirements/requirements.md#nfr-144
[NFR-145]: ../requirements/requirements.md#nfr-145
[NFR-162]: ../requirements/requirements.md#nfr-162
[NFR-163]: ../requirements/requirements.md#nfr-163
[PL §3.3]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#33-semester-b-track
[PL §5]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#5-repository-and-local-environment
[PL §6.1]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#61-optimizer-api-v1
[PL §6.2]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#62-core-api-conventions
[TS §1]: ../architecture/tech_stack.md#1-what-decides-close-calls
[TS §2.1]: ../architecture/tech_stack.md#21-the-dependency-rule
[TS §4]: ../architecture/tech_stack.md#4-repository-layout
[TS §6]: ../architecture/tech_stack.md#6-core-service-java
[TS §7]: ../architecture/tech_stack.md#7-optimization-service-python
[TS §12]: ../architecture/tech_stack.md#12-shared-typescript-packages
[TS §17.1]: ../architecture/tech_stack.md#171-by-layer
[TS §18]: ../architecture/tech_stack.md#18-cicd-and-supply-chain
[TS §24]: ../architecture/tech_stack.md#24-references

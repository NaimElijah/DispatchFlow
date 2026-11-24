# ADR-073: A request that breaks its contract is refused by validation generated from the contract

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

On 2026-11-13 it was confirmed that [NFR-56] stands as a Should: a request that breaks its OpenAPI
contract is refused with the reason before anything acts on it. This ADR settles the marker "ADR
pending: runtime contract validation".

[ADR-043] chose the generators for both sides of both contracts, and left three decisions to ADRs of
their own. One was "runtime validation of requests against the contract". This extends [ADR-043] by
settling it. [ADR-068] settled another, authorization from the contract.

The forces:

- **Every API is a versioned OpenAPI contract** ([NFR-21], [ADR-006]). Code is generated on both sides,
  and no hand-written type crosses a boundary ([TS §1], rule 5).
- **A refusal names its reason.** Errors are RFC 9457 problem details with a stable `code`, `params`
  and a `traceId`. A validation failure lists every field or row at once ([PL §6.2][PL-62],
  [ADR-043]).
- **The tech stack already says how far generation goes.** The core's generated interfaces and Bean
  Validation mean that generated validation "covers most of" [NFR-56] ([TS §6], *Web adapter* row).
  The optimizer's Pydantic models are generated from its contract, and FastAPI validates against
  them ([TS §7], *HTTP adapter* row).
- **The domain trusts its types.** Validation happens at the boundary, once (`.claude/CLAUDE.md`).

Nothing is built. The client contract declares `paths: {}`.

## Decision

The contract is the only source of what a valid request looks like, and validation generated from it
refuses an invalid one.

- **Core:** the interfaces generated from `contracts/client-api/v1` carry Bean Validation. Spring MVC
  validates a request before the handler runs ([TS §6]).
- **Optimizer:** the Pydantic models generated from `contracts/optimization-api/v1` by
  datamodel-code-generator validate every request in the HTTP adapter. They are used nowhere else
  ([TS §7]).
- **The answer** is a problem-details body with a stable `code`, the failing fields or rows, and the
  `traceId` ([ADR-043]). The apps translate the `code` ([NFR-143], [NFR-144]).
- **No second validator.** The team writes no hand-checked copy of what the contract says. Rules the
  contract cannot say, such as the limit on a day's orders ([BR-101]), stay with the domain
  ([ADR-063]).
- **The check is tested from the contract.** Schemathesis generates tests for both sides of both APIs
  from January ([TS §17.1], *Contracts* row, [ADR-043]).
- **Priority:** Should, as confirmed.

### Open points

The sources leave three points open. They are decided with the first handler, not here:

- **What "most" leaves out.** The tech stack says generated validation covers most of [NFR-56] and
  names no gap. Schemathesis's runs from January are where a gap shows.
- **Unknown properties.** Whether a request with a property the contract does not declare is refused
  is not stated.
- **Order.** [PL §16.3][PL-163] runs the role check before any use case, and [NFR-56] refuses before
  anything acts. Which runs first is not stated ([ADR-068]).

## Consequences

- **Easy:** a contract change changes the validation in the same pull request ([TS §4]), and the
  generated models cannot drift from the contract ([TS §7]).
- **Easy:** one error shape for a refused request, whichever side refuses it.
- **Hard:** validation is only as complete as the generators' output. The Spring generator's
  `useSpringBoot4` and `useJackson3` options are new, and [ADR-043] could not name the release that
  carries them.
- **Hard:** OpenAPI 3.1 rules that Bean Validation or Pydantic do not express are not enforced
  until someone finds them. The sources do not list any.
- **Cost:** none beyond the generators and Schemathesis that [ADR-043] already adds.
- **Risk:** a request that passes generated validation can still break a rule the contract cannot
  express. The domain refuses it as a rule violation, with a different `code`.
- **Risk:** until January nothing automatic tests the refusals ([ADR-043]).

## Alternatives considered

- **Hand-written validation per handler.** Lost on [TS §1], rule 5, and on drifting from the contract.
- **Code-first: generate the contract from the Spring code.** Lost as in [ADR-006]: the Python
  service and the phone app need a reviewed contract before the code ([TS §6]).

The sources assess no runtime validator library, such as a filter that reads the contract file when
the server starts. This ADR does not reject one for a reason.

## Revisit when

- Schemathesis shows a request the contract forbids but the generated validation accepts, and the
  generator cannot be configured to refuse it. Add a validator, or tighten the contract.
- The pilot's logs show a malformed request reaching a use case.
- The Spring Boot 4.2 move in January, or a generator upgrade, changes the generated constraints.

[ADR-006]: ADR-006-versioned-openapi-contracts.md
[ADR-043]: ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-063]: ADR-063-a-transaction-changes-one-aggregate-and-every-rule-has-one-owner.md
[ADR-068]: ADR-068-every-web-handler-is-authorized-by-role-from-the-contract.md
[BR-101]: ../requirements/requirements.md#time-and-limits
[NFR-21]: ../requirements/requirements.md#nfr-21
[NFR-56]: ../requirements/requirements.md#nfr-56
[NFR-143]: ../requirements/requirements.md#nfr-143
[NFR-144]: ../requirements/requirements.md#nfr-144
[PL-62]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#62-core-api-conventions
[PL-163]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#163-limits-and-checks
[TS §1]: ../architecture/tech_stack.md#1-what-decides-close-calls
[TS §4]: ../architecture/tech_stack.md#4-repository-layout
[TS §6]: ../architecture/tech_stack.md#6-core-service-java
[TS §7]: ../architecture/tech_stack.md#7-optimization-service-python
[TS §17.1]: ../architecture/tech_stack.md#171-by-layer

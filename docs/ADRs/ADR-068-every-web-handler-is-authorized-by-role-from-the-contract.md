# ADR-068: Every web handler is authorized by role, from the `x-capability` of its contract operation

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

On 2026-11-13 the owner, Naim Elijah, decided for the team how the server enforces roles. This ADR
settles the marker "ADR pending: authorize every web handler by role", which the permission matrix
under [FR-03] carries.

The forces:

- **The server checks every call and every event.** The roles are Admin, Dispatcher and Driver. An
  account may hold several, and may then do everything any of them allows ([FR-03], [FR-293],
  [ADR-036]). [NFR-85] requires the check "for every operation and for every event the event stream
  sends".
- **The Driver role has a data limit that is not a role.** It sees only the stops of routes assigned
  to the account, and only published plans ([BR-02], [BR-41]).
- **The checks must be testable.** [NFR-176] runs every role against every capability of the matrix,
  and one driver against another driver's data. [NFR-210] runs each combination of roles.
- **The contract comes first.** Code is generated on both sides of each contract
  ([TS §1], rule 5; [ADR-006], [ADR-043]).
- **Nothing is built.** `contracts/client-api/v1/openapi.yaml` declares `paths: {}`, so no operation
  exists to migrate.

## Decision

Each operation in `contracts/client-api/v1` carries an `x-capability`, and the web adapter turns it
into the role check. This follows [TS §6] (the *Authorization* row) and [PL §6.2][PL-62].

- **Roles come from the contract.** Every operation names one capability, for example `PLAN_PUBLISH`,
  `ROUTE_REPORT_OWN` or `USERS_MANAGE` ([PL §6.2][PL-62]). The permission matrix under [FR-03] says
  which role holds which capability.
- **One interceptor checks it.** The Spring generator's `x-operation-extra-annotation` extension turns
  the value into a `@RequiresCapability` annotation on the generated interface. One interceptor reads
  it and asks Access's `RoleSet.can`, before any use case runs ([PL §6.2][PL-62], [PL §16.3][PL-163]).
  The generator extension is as the plan names it. It is not checked against the pinned generator's
  documentation.
- **Data scope sits in the use cases.** "Only your own route" and "no unpublished plans through the
  Driver role" are domain rules, so each use case checks them ([BR-02], [BR-41], [TS §6]).
- **Admin-only capabilities need a recent password.** The same mapping marks them, and the server
  answers `403 STEP_UP_REQUIRED` unless the session gave its password in the last 5 minutes
  ([NFR-214], [PL §16.3][PL-163]).
- **The permission-matrix test is generated from the same mapping,** so a capability cannot be left
  out of the test ([PL §6.2][PL-62], [NFR-176]). [NFR-210] adds the role combinations.
- **Stream events are filtered by role,** as [ADR-035] decided. The interceptor covers handlers, not
  the stream's events. [NFR-176] tests both.

This extends [ADR-043], which left "authorizing handlers from the contract" to a later ADR. Where the
annotation and the interceptor live is the app-wide `web` package's decision, which [ADR-040] leaves
open for M0 to M1.

## Consequences

- **Easy:** the contract shows what each operation needs, and a reviewer reads it in the contract
  diff ([ADR-006]).
- **Easy:** a handler cannot be left unguarded by forgetting an annotation, because the annotation
  is generated, not written ([TS §6], *Over*).
- **Easy:** the matrix and its test come from one source, so they cannot disagree.
- **Hard:** an operation with no `x-capability` is the one gap left. The sources do not say what
  happens to it. A build check that fails on a missing value fits here, and none is named yet.
- **Hard:** two layers decide access: the interceptor for roles, the use case for data scope. A
  new use case must carry its scope check, because the interceptor never sees the data.
- **Cost:** a capability list to keep in step with the permission matrix, and a generator extension
  to learn.
- **Risk:** the check runs in the web adapter only. A second entry to a use case (a job, a
  listener) skips it. Today the sources name no such entry for a role-gated action.
- **Risk:** the stream's role filter is separate code from the interceptor and is covered by tests,
  not by the generated annotation.

## Alternatives considered

- **Method-security annotations written by hand on each handler.** Lost on one forgotten annotation
  leaving an endpoint open ([TS §6], *Over*). Generating them from the contract cannot forget.
- **Role checks inside each use case.** Lost on a stated rule: roles are transport policy, and "only
  your own route" is a domain rule ([TS §6]). Mixing them would put transport vocabulary in the
  domain ([ADR-001]).

## Revisit when

- A second entry to a role-gated use case appears, such as a job or a listener. Move the check to
  where both entries meet.
- The `x-operation-extra-annotation` extension fails to generate the annotation in the pinned
  openapi-generator version, or the Spring Boot 4.2 move in January breaks it.
- The matrix grows past what one capability per operation can express, for example an operation
  that needs two capabilities.

[ADR-001]: ADR-001-domain-driven-design-with-hexagonal-architecture.md
[ADR-006]: ADR-006-versioned-openapi-contracts.md
[ADR-035]: ADR-035-both-apps-serve-every-role.md
[ADR-036]: ADR-036-one-account-per-person-signed-in-by-email.md
[ADR-040]: ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-043]: ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[BR-02]: ../requirements/requirements.md#access
[BR-41]: ../requirements/requirements.md#access
[FR-03]: ../requirements/requirements.md#company-users-and-roles
[FR-293]: ../requirements/requirements.md#company-users-and-roles
[NFR-85]: ../requirements/requirements.md#nfr-85
[NFR-176]: ../requirements/requirements.md#nfr-176
[NFR-210]: ../requirements/requirements.md#nfr-210
[NFR-214]: ../requirements/requirements.md#nfr-214
[PL-62]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#62-core-api-conventions
[PL-163]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#163-limits-and-checks
[TS §1]: ../architecture/tech_stack.md#1-what-decides-close-calls
[TS §6]: ../architecture/tech_stack.md#6-core-service-java

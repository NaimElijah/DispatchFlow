# ADR-097: The shared kernel also holds the building blocks every module's domain uses

- **Status:** Accepted; extended by ADR-106
- **Date:** 2026-11-14

## Context

[DL §2.3][DL-23], rule 5, keeps the shared kernel tiny: the typed IDs of every aggregate and every
entity another context names, and nine values that mean the same everywhere. A change to it needs the
agreement of every module's owner. [ADR-040] fixed the kernel at the same list.

Building the domain layer showed that every module also needs a few building blocks, and that none of
them has another home:

- **Named errors** ([DL §3.8][DL-38]). Every refusal is a record of the module's sealed error type,
  thrown in a `RuleViolation`, or in a `RuleViolations` where every refusal is listed. The web adapter
  maps all of them to RFC 9457 responses with one stable code each ([ADR-043]), so it needs one error
  type that every module's errors share, without importing any module.
- **Optimistic locking** ([ADR-057]). Every aggregate root has a `Version` ([DL §17][DL-17] places it
  in every context).
- **Events** ([DL §7.3][DL-73]). An event carries its own ID, `occurredAt`, and the aggregate's ID and
  version. It may hold only IDs, shared-kernel values and its own enums, so its ID and version types
  must be kernel types.
- **Restoring from storage** ([DL §3.5][DL-35]). `restore` checks the structure again, so a damaged
  row needs one error that means "the stored state is invalid", whatever the module.
- **New child IDs** ([DL §3.9][DL-39], rule 3). The domain makes no IDs, yet one solve creates up to
  15 routes and 200 stops ([ADR-060]), so the use case hands the aggregate a supply of IDs it made.

## Decision

On 2026-11-14 the owner, Naim Elijah, put these building blocks in the shared kernel,
`com.dispatchflow.shared`:

- `DomainError`: what every module's sealed error type extends, with its stable `code()`;
- `RuleViolation` and `RuleViolations`: the exceptions that carry one error, or a summary and the
  list;
- `StoredStateInvalid`: what `restore` throws for a stored state that breaks an invariant at rest;
- `Version`: the counter optimistic locking checks;
- `AggregateRoot`: the base of every aggregate root, holding its version and the events it raised;
- `DomainEvent` and `EventId`: what every event implements, and its ID;
- `FreshIds`: the supply of new IDs a use case gives a method that creates children.

The nine values' own errors live in one sealed `SharedKernelError`. The kernel is held to the same
purity rules as a module's domain, and so is every module's `events` package, which its domain
imports ([DL §3.9][DL-39], [DL §7.3][DL-73]). The ArchUnit tests check both.

Each is generic: none holds a rule of one context. [DL §2.3][DL-23], rule 5, and [TS §4][TS-4] say so in
one line each.

## Consequences

- **Easy:** one error mechanism for the web adapter, and one version and event-ID type that every
  event can carry.
- **Hard:** the kernel grows from the IDs and nine values to those plus these building blocks. A
  change to any of them still needs every module owner's agreement.
- **Cost:** none.
- **Risk:** the kernel becomes a home for anything shared. Only generic building blocks with no rule
  of one context belong here, as rule 5 already says.

## Alternatives considered

- **A copy in each module.** Rejected: eight copies of the same classes, and the app-wide error
  handler would have to know every module.
- **A separate `building` package beside the kernel.** Not chosen: it would be a second shared
  package with the same agreement rule, for no gain.

## Revisit when

- A building block starts to carry a rule of one context.
- A module is split into its own service. It then takes a copy of these classes, or a small shared
  library.

[ADR-040]: ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-043]: ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-057]: ADR-057-concurrent-changes-to-a-plan-use-optimistic-locking.md
[ADR-060]: ADR-060-hard-limits-on-fleet-and-plan-size.md
[DL-17]: ../architecture/domain_layer/domain_layer_design.md#17-ubiquitous-language
[DL-23]: ../architecture/domain_layer/domain_layer_design.md#23-rules-between-contexts
[DL-35]: ../architecture/domain_layer/domain_layer_design.md#35-factories
[DL-38]: ../architecture/domain_layer/domain_layer_design.md#38-domain-errors
[DL-39]: ../architecture/domain_layer/domain_layer_design.md#39-purity
[DL-73]: ../architecture/domain_layer/domain_layer_design.md#73-shape-and-delivery
[TS-4]: ../architecture/tech_stack.md#4-repository-layout

# ADR-118: A domain package above 30 files splits into one package per aggregate, over a root that holds the module's error

- **Status:** Accepted; amended by ADR-120 and ADR-125
- **Date:** 2026-11-15

## Context

The domain layer gave each module's domain one flat package. After the review fixes (#78), seven
packages held more than 30 Java files. The counts are as of the commit before each package's split:

| Package | Java files |
|---|---|
| Planning | 81 |
| Live Operations | 46 |
| Recipient Messaging | 42 |
| Customers & Orders | 41 |
| Shared kernel | 38 |
| Access | 37 |
| Fleet | 33 |

Reporting held 18, Audit 11, and the ArchUnit fixtures 89 in `archfixture/alpha/domain`. On
2026-11-15 it was decided that these folders are split so that they read well, with the scheme
left to the team.

Four things constrain a split:

- **Java binds some files to one package.**
  - On the classpath, a sealed type's permitted subtypes share its package, and a package-private
    member or type is visible only inside it.
  - Widening one to move a file would weaken the restricted factories of [DL §3.5][DL-35] and
    [DL §13][DL-13]. For example, `SolveJob.queue` is package-private so that only `Plan` queues a job.
- **Packages form no cycle** (`.claude/CLAUDE.md`, Engineering judgment). Almost every type refuses
  with its module's error, so the error sits at the bottom, with every type it names.
- **`ErrorRefusalTestsTest`** looked for an error's refusal test only in the error's own package.
- **Spring Modulith** ([ADR-040]) treats a module's base package as its API, and every subpackage as
  internal unless it is a `@NamedInterface`.

The compiled classes show:

- **Sealed types bind nothing.** Every sealed hierarchy permits only types nested in its own file.
- **Package-private access binds each aggregate to its own parts.** Examples: `FleetRegister` to
  `Depot` and `Vehicle`, `Session` to `RefreshToken`, and `Delivery` to `DeliveryInvariants` and
  `DeliverySnapshot`. It also binds a few values to each other.
- **In Planning it binds three aggregates.** `Plan` calls `SolveJob.queue`, and `SolveJob` calls
  `ReoptimizationProposal.create`, so their core files share one package.
- **Outside those sets the type graph has no cycle, except in Customers & Orders.** There
  `OrdersError.OnlyFailedOrDroppedOrdersArePutBack` names `OrderStatus`, and
  `OrderStatus.derive(Order, …)` takes the aggregate, so most of the module forms one cycle with its
  error. Its three aggregates are also bound by package-private calls from `ImportBatch`.
- **The files can't be split by kind.** `PlanningError` and `RuleCode` form a cycle, and every
  aggregate's invariants and snapshot are bound to it.

## Decision

This rule applies to every Java package in `core`, and its threshold to every source folder.

1. **The threshold.** A package of more than 30 Java files, not counting `package-info.java`, splits.
   One of 30 or fewer stays flat. A split package stays split.
2. **The root package.** A module's domain splits by aggregate. `<module>.domain` keeps:
   - the module's error type;
   - every module type the error names;
   - the types that more than one subpackage uses;
   - the types that no aggregate uses.

   It imports no subpackage.
3. **Aggregate packages.** Each aggregate gets `<module>.domain.<aggregate>`. It holds the root, its
   child entities, snapshot, invariants and states, the values only it uses, and any type that uses
   it, such as a projection that reads it.
4. **Bound aggregates share one package.** Aggregates bound by package-private access or a type cycle
   share one package, named for the aggregate that creates the others.
5. **Concept packages.** When an aggregate package would pass 30 files, families of its values that
   form one [DL §17][DL-17] concept, and use nothing in that package, move to
   `<module>.domain.<concept>`, until it holds 30 or fewer.
6. **Names.** A package takes the DL §17 term in lower case, with the words run together and without a
   leading word that repeats the module's name. With no §17 row, it takes the class name without the
   module's prefix. No package is named after a type it doesn't hold, and none is a Java keyword.
7. **Direction.** Dependencies point from aggregate packages to concept packages to the root. One
   aggregate package may use another, one way. An ArchUnit rule fails on any cycle between a module's
   domain packages.
8. **What never moves a file.**
   - Nothing is made more visible in production code, no sealed hierarchy is split, and no package
     cycle is created to move a file. Files bound that way stay together.
   - A cycle is broken only by a refactor in its own commit, and only where the domain document
     agrees.
9. **What stays flat.** The shared kernel and each `events` package stay flat.
10. **Tests.**
    - A test sits in the package of the type it tests.
    - A test of several packages' values sits in the root.
    - Builders sit with their aggregate.
    - A test helper that several test packages use is public.
    - A refusal test may sit anywhere under its error's package.
11. **`package-info`.** Every new package has a `package-info.java` with a doc comment and
    `@NullMarked`, which an ArchUnit guard checks.

Applied now, this splits:

| What | Packages |
|---|---|
| Planning | the root, `plan` (`Plan`, `SolveJob`, `ReoptimizationProposal`), `solution`, `basis`, `revision`, `reconciliation` and `settings` |
| Live Operations | the root, `delivery`, `alert` and `routerun` |
| Recipient Messaging | the root, `message`, `rating` and `settings` |
| Access | the root, `account`, `session` and `companyprofile` |
| Fleet | the root, `register`, `roster` and `driver` |
| The ArchUnit fixtures | one package per rule each exercises |

Customers & Orders stays flat until its cycle is broken. So do Reporting, Audit, the kernel, the
`events` packages and the optimizer's Python `domain`, which holds 16 files.

## Consequences

- **Easy:**
  - An aggregate's parts sit together, and a reader sees a module's aggregates as its packages.
  - A new type has one place: with its aggregate, in the root if several use it, or in a concept
    package.
  - The fixtures, in subpackages, prove that every purity rule covers subpackages.
- **Hard:**
  - A type moves when its users change. A value that a second aggregate starts to use moves to the
    root, and its importers change.
  - Planning keeps `SolveJob` and `ReoptimizationProposal` in `plan`, because only `Plan` may queue a
    job.
- **Cost:**
  - Most of five modules' files move, and each new package gets a `package-info.java`.
  - Six test helpers become public.
  - Three ArchUnit rules and one guard are added.
  - No production visibility changes.
- **Risk:** a branch open during a module's move conflicts on imports, and a file it adds lands in
  the old package. Each module moves in its own commit, with nothing in it but the move.

## Alternatives considered

- **Stay flat.** Rejected: up to 81 files in one folder, against what was decided.
- **Split by kind** (`value`, `error`, `snapshot`, `invariants`). Rejected:
  - `PlanningError` and `RuleCode` form a cycle;
  - invariants and snapshots are bound to their aggregates;
  - an aggregate's parts would scatter.
- **Put the aggregates in the root, values below, and the error at the bottom.** Rejected: it
  scatters Fleet's, Access's and Messaging's values away from their aggregates, and puts each error in
  a kind-named package.
- **Split by aggregate without concept packages.** Rejected for Planning: `plan` would hold about 60
  files.
- **Make `SolveJob.queue` and the other bound members public.** Rejected: it breaks the restricted
  factories of DL §3.5 and §13.
- **Java modules (JPMS).** Rejected: they relax only the sealed-package rule, which binds nothing here,
  and Spring Boot runs on the classpath.
- **Split the shared kernel.** Rejected:
  - under Spring Modulith every kernel subpackage would need a `@NamedInterface`, which rule 1 forbids
    on a kernel subpackage's `package-info`;
  - [ADR-097] already declined a second kernel package;
  - every module would change its imports.
- **A threshold of 20 or 40.** Rejected: 20 would split Reporting, which has one aggregate and held
  18 files before the split, for no gain; 40 would leave Fleet and Access, with three aggregates
  each, flat.

## Revisit when

- Customers & Orders drops the `OrderStatus.derive(Order, …)` overload (DL §3, §9.6). It then splits
  by this rule.
- The kernel passes 50 files, or M0 decides how Spring Modulith exposes `shared`.
- An aggregate package passes 30 files with no DL §17 family to move out.
- The team finds the threshold wrong in practice, or adopts Java modules.

[ADR-040]: ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-097]: ADR-097-the-shared-kernel-holds-the-domains-building-blocks.md
[DL-13]: ../architecture/domain_layer/domain_layer_design.md#13-inside-each-module-hexagon
[DL-17]: ../architecture/domain_layer/domain_layer_design.md#17-ubiquitous-language
[DL-35]: ../architecture/domain_layer/domain_layer_design.md#35-factories

# DispatchFlow — Bounded contexts

Each of the core's eight bounded contexts has its domain built in code, ahead of its milestone. Each
also has an `application` package, declared and not built, with every body that would do work
throwing, and none has an adapter yet ([ADR-119](../ADRs/ADR-119-the-application-layer-is-declared-ahead-as-signatures-whose-bodies-throw.md)). `src/backend/core/src/main/java/com/dispatchflow/`
holds a module for each: `access`, `audit`, `fleet`, `liveops`, `messaging`, `orders`, `planning` and
`reporting`, each with a `domain` and an `application` package, and an `events` package except
`audit` and `reporting`, which raise no event. It also holds the application entry point, a `web` package for
application-wide web concerns, and the shared kernel `shared`, which every context uses
([code-structure.md](code-structure.md#srcbackendcore)). The ninth context, Optimization, is the
optimizer's `domain` package ([optimization-engine.md](optimization-engine.md)).

A module's domain splits into one package per aggregate over a root that holds its error and what
several packages use, then into concept packages, once a package passes 20 files and a group of 4
can move ([ADR-118](../ADRs/ADR-118-a-domain-package-above-30-files-splits-by-aggregate.md),
[ADR-125](../ADRs/ADR-125-a-folder-above-20-files-splits-wherever-a-group-of-four-can-move.md)).
Planning has the root and `plan`, `solution`, `drop`, `basis`, `revision`, `reconciliation` and
`settings`; Live Operations `delivery`, `outcome`, `proof`, `alert` and `routerun`; Recipient
Messaging `message`, `rating` and `settings`; Access `account`, `session` and `companyprofile`;
Fleet `register`, `roster` and `driver`; Reporting `baseline`. Customers & Orders and Audit, the
kernel's domain types and every `api`, `spi` and `events` package stay flat.
`shared/application/` is a package of its own, beside the kernel's domain types
([ADR-120](../ADRs/ADR-120-the-application-layer-takes-an-actor-and-a-call-context-and-reads-through-query-ports.md)).

The decided design is in the
[domain layer design](domain_layer/domain_layer_design.md): the contexts in
[§1](domain_layer/domain_layer_design.md#1-subdomains-and-bounded-contexts) and the context map
and module graph in [§2](domain_layer/domain_layer_design.md#2-context-map). The module shape and
its checks are in
[ADR-040](../ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md), and
the layout inside a module is in [tech stack §4](tech_stack.md#4-repository-layout) and
[domain layer design §13](domain_layer/domain_layer_design.md#13-inside-each-module-hexagon).

This file will describe the contexts as built once their application layers are built and their
adapters exist.
[ADR-120](../ADRs/ADR-120-the-application-layer-takes-an-actor-and-a-call-context-and-reads-through-query-ports.md)
holds the conventions the application layers follow (the `Actor` and the `CallContext`,
`shared/application`, query ports), not what each module declares: each package lists that itself.

[The domain layer visual guide](domain_layer/domain_layer_visual_guide.md) draws every context as
built: its aggregates, packages, lifecycles and events, with a reading path through the code and tests.

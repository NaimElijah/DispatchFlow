# DispatchFlow — Bounded contexts

Each of the core's eight bounded contexts has its domain built in code, ahead of its milestone. Each
also has an `application` package, declared and not built, with every body that would do work
throwing, and none has an adapter yet ([ADR-119](../ADRs/ADR-119-the-application-layer-is-declared-ahead-as-signatures-whose-bodies-throw.md)). `src/backend/core/src/main/java/com/dispatchflow/`
holds a module for each: `access`, `audit`, `fleet`, `liveops`, `messaging`, `orders`, `planning` and
`reporting`, each with a `domain` and an `application` package, and an `events` package except
`audit` and `reporting`, which raise no event. It also holds the application entry point, a `web` package for
application-wide web concerns, and the shared kernel `shared`, which every context uses
([code-structure.md](code-structure.md#srcbackendcore)). The ninth context, Optimization, is the
solver's `domain` package ([optimization-engine.md](optimization-engine.md)).

A module's domain splits into one package per aggregate over a root that holds its error and what
several packages use, once it passes 30 files
([ADR-118](../ADRs/ADR-118-a-domain-package-above-30-files-splits-by-aggregate.md)). Planning has the
root and `plan`, `solution`, `basis`, `revision`, `reconciliation` and `settings`; Live Operations
`delivery`, `alert` and `routerun`; Recipient Messaging `message`, `rating` and `settings`; Access
`account`, `session` and `companyprofile`; Fleet `register`, `roster` and `driver`. Customers &
Orders, Reporting and Audit, the shared kernel and every `events` package stay flat.

The decided design is in the
[domain layer design](domain_layer/domain_layer_design.md): the contexts in
[§1](domain_layer/domain_layer_design.md#1-subdomains-and-bounded-contexts) and the context map
and module graph in [§2](domain_layer/domain_layer_design.md#2-context-map). The module shape and
its checks are in
[ADR-040](../ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md), and
the layout inside a module is in [tech stack §4](tech_stack.md#4-repository-layout) and
[domain layer design §13](domain_layer/domain_layer_design.md#13-inside-each-module-hexagon).

This file will describe the contexts as built once their application layers are built and their
adapters exist. What each module's `application` package declares, and the conventions it follows
(the `Actor` and the `CallContext`, `shared/application`, query ports), is in [ADR-120](../ADRs/ADR-120-the-application-layer-takes-an-actor-and-a-call-context-and-reads-through-query-ports.md).

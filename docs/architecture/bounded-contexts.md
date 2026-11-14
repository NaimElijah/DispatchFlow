# DispatchFlow — Bounded contexts

No bounded context exists in code today. `src/backend/core/src/main/java/com/dispatchflow/` holds
the application entry point, a `web` package for application-wide web concerns, and the shared
kernel `shared`, which every context will use
([code-structure.md](code-structure.md#srcbackendcore)).

The decided design is in the
[domain layer design](domain_layer/domain_layer_design.md): the contexts in
[§1](domain_layer/domain_layer_design.md#1-subdomains-and-bounded-contexts) and the context map
and module graph in [§2](domain_layer/domain_layer_design.md#2-context-map). The module shape and
its checks are in
[ADR-040](../ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md), and
the layout inside a module is in [tech stack §4](tech_stack.md#4-repository-layout) and
[domain layer design §13](domain_layer/domain_layer_design.md#13-inside-each-module-hexagon).

This file will describe the contexts as built once code exists.

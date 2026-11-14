# DispatchFlow — Bounded contexts

No bounded context exists in code today. `src/backend/core/src/main/java/com/dispatchflow/` holds
the application entry point and a `web` package for application-wide web concerns, and nothing
else ([code-structure.md](code-structure.md#srcbackendcore)).

The decided design is in the
[domain layer design](domain_layer/domain_layer_design.md): the contexts in
[§1](domain_layer/domain_layer_design.md#1-subdomains-and-bounded-contexts) and the context map
and module graph in [§2](domain_layer/domain_layer_design.md#2-context-map). Its status is
Decided, 14 November 2026.

This file will describe the contexts as built once code exists.

# ADR-119: The application layer is declared ahead of its milestones, as signatures whose bodies throw

- **Status:** Accepted; amended by ADR-121 and ADR-129
- **Date:** 2026-11-15

## Context

The domain layer of every context is built ahead of its milestone ([roadmap §2][RM-2]). Nothing sits
above it yet:
- the core has no `application` package;
- the optimizer's `application` package holds only a docstring;
- the client contract declares no operation, and the optimization contract only liveness.

[TS §6.2][TS-62] and [DL §13][DL-13] say what the application layer is: one class per use case; an
`api` for what other modules call; an `spi` for the ports a module owns for others to implement; and
the secondary ports in `port/out`. They don't list the classes. Four lanes build the milestones in
parallel ([roadmap §2.1][RM-21]). Without a shared list:
- each lane would invent the ports and module APIs it needs;
- two lanes could name the same API differently;
- a cross-module call would first be seen when its milestone merges.

The module graph of [DL §2.1][DL-21] can only be checked once the application classes, and their
dependencies, exist.

On 2026-11-15 the owner, Naim Elijah, asked for the whole application layer's skeleton: every service
class and every function in it, declared with its signature and no implementation. He chose the
answers this ADR records ([#81]).

## Decision

1. **The whole layer is declared now.** That covers every use case, query, application service, `api`,
   `spi` and secondary port that the requirements, the domain document, the tech stack and the ADRs name.
   - In the core, they go in each module's `application.api`, `application.spi`, `application.port.out`
     and `application.service`, and in `shared.application` ([ADR-120]).
   - In the optimizer, they go in `dispatchflow_optimizer/application`.

   [ADR-120] fixes the conventions and lists what is left out.
2. **A body that would do work throws, naming its milestone.**
   - Java: `throw new UnsupportedOperationException("Built in M5")`.
   - Python: `raise NotImplementedError("Built in M9")`.
   - The milestone is the roadmap's. Where the roadmap names none, the text says "Built in an
     unscheduled milestone".
   - **Only these bodies are written for real:**
     - constructors that store their fields;
     - records' compact constructors, which only check for null and copy collections;
     - the redacted `toString` of a record that holds personal data;
     - the constructors and `code()` of the application error types named in [ADR-120] decision 7
       that reach the web adapter;
     - `Actor`'s check of its `CallContext`;
     - the accessors of `ProofUpload`, a class because a record would compare its bytes by identity.
3. **A use case's constructor declares its dependencies:** its ports, `AuditLog` and other modules'
   `api`. So the ArchUnit rules of [ADR-120] check the module graph now, not at each milestone.
4. **Nothing is wired.** The skeleton carries no Spring annotation and registers no bean, so no request,
   listener or job can reach a throwing body. The application context starts as before.
5. **How a milestone builds a body:**
   - it writes the failing test first, replaces the throw, then builds the adapter;
   - it may change a declared signature or add a class, with every caller in the same pull request;
   - each Javadoc's "Built in M<n>" goes when its body is built.
6. **No behaviour tests now.** A skeleton has no behaviour. The architecture tests of [ADR-120] check
   its shape.

## Consequences

- **Easy:**
  - Every lane builds against one reviewed list of names and signatures.
  - A cross-module dependency that breaks DL §2.1 fails the build today.
  - `grep "Built in M5"` lists a milestone's remaining work.
- **Hard:**
  - The client contract doesn't exist yet. So a view record's fields come from its requirement rows, and
    the milestone's contract may reshape it.
  - A signature that turns out wrong costs a change to every caller.
- **Cost:** a few hundred Java files and a few Python modules, mostly Javadoc, and one review round.
- **Risk:**
  - A throwing body reached at runtime before its milestone. Decision 4 prevents it, because nothing is
    wired.
  - A wrong signature kept because it already exists. Decision 5 lets the milestone change it.

## Alternatives considered

- **Declare each use case when its milestone builds it** (TS §6.2 as written). Rejected:
  - the owner asked for the skeleton;
  - four parallel lanes need one shape;
  - DL §2.1 stays unchecked until the last module lands.
- **An interface for every use case.** Rejected: [TS §6.2][TS-62] gives an interface only to what
  another module calls; one with one implementation and one caller earns nothing.
- **A marker exception and a `Milestone` enum,** so a test can list unbuilt work per milestone. Rejected
  by the owner on 2026-11-15: two more types for a report nobody has asked for.
- **Commands only, reads later.** Rejected by the owner on 2026-11-15: the daily summary, the planning
  workspace and the live map would be missing.

## Revisit when

- The team stops building to the roadmap's milestones.
- A milestone changes more than a third of its declared signatures when it builds them: the skeleton
  then misleads more than it helps.
- Every body is built. This ADR is then history.

[#81]: https://github.com/NaimElijah/DispatchFlow/issues/81
[ADR-120]: ADR-120-the-application-layer-takes-an-actor-and-a-call-context-and-reads-through-query-ports.md
[DL-13]: ../architecture/domain_layer/domain_layer_design.md#13-inside-each-module-hexagon
[DL-21]: ../architecture/domain_layer/domain_layer_design.md#21-module-graph
[RM-2]: ../roadmap.md#2-milestones
[RM-21]: ../roadmap.md#21-lanes
[TS-62]: ../architecture/tech_stack.md#62-application-layer

# ADR-093: The implementation plan is retired, and a roadmap holds the dates

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

`docs/init_impl_ref/technical_implementation_plan.md` held the milestones, the dates, a schema
sketch, the key flows, code sketches, and a list of decisions to record (its §25). By 2026-11-14
every one of those decisions was recorded or settled in ADR-037 to ADR-092. The plan had already
drifted from them: it still named Sentry, put `@Retryable` on a listener, and had no *abandoned*
plan state.

Decided on 2026-11-13: retire it, because a set-in-stone plan misleads once the work moves. On
2026-11-14 it was settled how the documents rank once it is gone, and one rule that lived only in
the plan.

## Decision

- **The plan is deleted.** Its last version stays readable at commit
  [f54b13b][PL], which the ADRs cite by permalink. It is history: it binds only where an accepted
  ADR or a `CLAUDE.md` states the rule itself.
- **[`docs/roadmap.md`][RM] holds the dates.** It holds:
  - the course calendar;
  - the milestones M0 to M13 and the checkpoints;
  - the Alpha and its demo script;
  - who the team waits on.

  It is updated as the dates move. It names the rows and ADRs it schedules, and copies none of them.
  The README links it rather than listing milestones.
- **Precedence:** [`requirements.md`][REQ], then the [domain document][DL], then the
  [tech stack][TS].
  - An accepted ADR stands until a new ADR supersedes it: where it and a design document disagree,
    the team builds from the ADR and corrects the document.
  - On when a tool is adopted, [TS §20][TS-20] wins over the roadmap. On milestones and dates, the
    roadmap wins.
  - Only a change to a decision needs a new ADR; pointer and wording fixes don't.
- **Schema and flows:** beyond what the domain document and the ADRs decide, a table layout or a
  step-by-step flow is written down by the migration or the code that builds it, not ahead of it.
- **Rules that lived only in the plan move to the tech stack:**
  - the end-to-end profile's test-only endpoints exist only in that profile, and the profile refuses
    to boot when the SMS credential is present ([NFR-206]);
  - request bodies are never logged, and a log converter scrubs the fields the data inventory names
    ([NFR-19]);
  - the VM's secrets land in a file only root can read.

  Position batches carry a user-scoped idempotency key, as driver reports do. This was decided on
  2026-11-14, and it became [NFR-285].

## Consequences

- **Easy:** the dates live in one file, so a moved date changes one place. Nothing half-current is
  left beside the decided documents.
- **Easy:** a reader always knows which document to build from, including when an ADR and a design
  document disagree.
- **Hard:** the plan's detail is reachable only by permalink until the code that builds it exists.
  That covers the table columns, the flow steps and the OR-Tools findings.
- **Cost:** the roadmap is kept current by hand as milestones land. This change repointed every
  pointer to the plan.
- **Risk:** a rule that lived only in the plan, and that nobody moved, is lost. The review of this
  change found and moved four. The "binds only where stated" rule means a missed one fails safe:
  it is not followed by accident, but it may be forgotten.

## Alternatives considered

- **Keep the plan, updated as the work moves.** Rejected: it would be a second source for decisions
  that already live in the ADRs. It had drifted within a day of the decisions.
- **Put the roadmap in the README.** Rejected: the README routes a stranger to the
  docs, and dates change too often for it.
- **Move the schema sketch into `docs/architecture/data-model.md` as "proposed".** Rejected:
  `docs/architecture/` describes the system as it is, never ahead of it.
- **Let the design document win over an ADR.** Rejected: an ADR is a dated decision with its
  reasons, and a careless edit to a design document would silently override it.

## Revisit when

- A design or schema document is needed ahead of the code again, for example when a second team
  builds against the schema before the migrations exist.
- The roadmap and TS §20 disagree about a tool's timing twice in one semester. Then one of them
  should stop stating timing.

[DL]: ../architecture/domain_layer/domain_layer_design.md
[NFR-19]: ../requirements/requirements.md#nfr-19
[NFR-206]: ../requirements/requirements.md#nfr-206
[NFR-285]: ../requirements/requirements.md#nfr-285
[PL]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md
[REQ]: ../requirements/requirements.md
[RM]: ../roadmap.md
[TS]: ../architecture/tech_stack.md
[TS-20]: ../architecture/tech_stack.md#20-adoption-order

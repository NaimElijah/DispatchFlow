# Docs

What the system must do, how it is built, and why. Each kind of document points to the others
and never restates them.

| Path | States | Rule |
|---|---|---|
| `requirements/requirements.md` | What the system must do | Intent, not observation |
| `requirements/use-cases.md` | Each use case step by step: actors, flows, failures, diagrams | Expands the `UC-` rows; cites rows by ID and copies none of their text; changes in the same pull request as the row it expands |
| `requirements/use-cases.html` | The same use cases as a visual page | Built from `use-cases.md` by a script not in the repository; kept in step with it, by hand until the script is committed, and its Content-Security-Policy meta stays |
| `architecture/` | How it is built, as it is today | Descriptive, never aspirational; the two decided designs below are the exception |
| `architecture/tech_stack.md` | The decided tools and versions, and when each is adopted | Tools and versions only |
| `architecture/domain_layer/` | The decided domain model: contexts, aggregates, events, language | The one home of the model |
| `architecture/layers_visual.html` | The core's Java types by layer and bounded context, as a visual page | Generated from the source at the commit it names, by a script not in the repository; adapters not built yet are drawn as `tech_stack.md` plans them and marked so; decides nothing |
| `roadmap.md` | When each part is built: the course calendar, the milestones, the Alpha and its demo script, and who the team waits on | Dates and schedule only; it names the rows and ADRs it schedules and copies none of them |
| `ADRs/` | Why each decision was taken | Append-only |
| `requirements/ARD/` | The Application Requirements Document (ARD), as a PDF | Built outside the repository; the PDF is the only file here |
| `presentations/` | The proposal and ARD presentations | The planning material the requirements started from |

`architecture/architecture.md` describes today's system. `bounded-contexts.md` and
`optimization-engine.md` describe the domain layers that are built and point to the design sections
that define their topics. They also say that each application layer is declared, not built
([ADR-119]). `data-model.md` points to them until a migration exists.
`code-structure.md` describes the repository layout.
`architecture/domain_layer/domain_layer_visual_guide.md` is the one file in its folder that decides
nothing: a visual map of the domain layer as built, for reading the code. The domain document wins
wherever the two differ.

## Requirements

- Every row has a stable ID: `FR-`, `BR-`, `NFR-`, `Q-` or `UC-`. Never renumber or reuse an ID;
  ADRs, tests and issues cite them.
- An FR, BR or NFR row states one testable thing, in about 25 words and at most 40, not counting
  link glosses. A split row keeps its ID for its core statement. Each other part takes the next
  free ID and sits directly under it, with its priority unless the part states its own. After a
  split, search the repository for the row's ID and repoint citations that rely on a moved part;
  accepted ADRs stay as they are.
- Links between rows are reference-style (`[FR-36]`), with their definitions at the bottom of the
  file. A new link needs a definition, and a moved row needs its definition changed.
- In `requirements.md`, every link to a row or an ADR is followed by a gloss, a few words naming
  what it is, so the text reads without following the link: `[BR-20] (routes publish only with
  their solved vehicle and driver)`, or `([FR-294]: set-password link)` when the link already sits
  in parentheses. An ID has one gloss, worded the same wherever it is linked, and a range takes
  one. A gloss copies no value, so it stays true when a number changes; when a row's subject
  changes, search for its ID and update its glosses.
- *Size* is XS, S, M, L or XL, set by the team when it sizes the work. Leave it blank until then.
- *Enforced by* stays blank until the requirement is implemented. Then it names the test that
  enforces the row, in the same pull request as the code.
- `ADR pending: <title>` marks a decision the requirements rely on that has no ADR yet. When the
  ADR is accepted, replace the marker with a link to it.
- `UC-` rows trace a process to the rows it exercises. They carry no priority, size or
  *Enforced by*; NFR-83 is what tests them.
- Behaviour found in code is not a requirement until the team agrees it is. When something is
  unknown, mark it `TODO` and ask.

## ADRs

- File name: `ADR-NNN-short-kebab-title.md`, with the next free number, zero-padded.
- Sections, in this order:
  - the title, `# ADR-NNN: <the decision as a sentence>`;
  - **Status** and **Date**. Status is one of:
    - `Proposed`, or `Proposed; would extend ADR-NNN`, `Proposed; would amend ADR-NNN` or
      `Proposed; would supersede ADR-NNN`;
    - `Accepted`, or `Accepted; extended by ADR-NNN` or `Accepted; amended by ADR-NNN`;
    - `Superseded by ADR-NNN`.

    *Extended* means the later ADR adds to this one without changing course. *Amended* means it
    replaces part of this one and the rest stands. *Superseded* means it replaces this one whole;
  - Context, Decision, Consequences (**Easy:**, **Hard:**, **Cost:**, **Risk:**), Alternatives
    considered, and Revisit when.
- Record a decision *not* to adopt something too, with the trigger that would reopen it.
- Never rewrite an accepted ADR. To change course, write a new one and mark the old one
  `Superseded by ADR-NNN`, or `Accepted; amended by ADR-NNN` when only part of it changes.
- Add a row to `ADRs/README.md` in the same change.
- A Proposed ADR that would change an accepted one says so in its Status: `Proposed; would
  extend ADR-NNN`, `Proposed; would amend ADR-NNN` or `Proposed; would supersede ADR-NNN`. One
  Status may combine relations, for example `Proposed; would supersede ADR-008 and amend
  ADR-006`. The accepted ADR keeps its status until the new one is accepted. Then one commit
  changes both statuses (the old one becomes `Accepted; extended by`, `Accepted; amended by` or
  `Superseded by ADR-NNN`), the `ADR pending` markers the new ADR settles, and the `ADRs/README.md`
  rows.
- Links to other ADRs and to requirements go at the bottom, reference-style. Requirement links
  copy the row's definition at the bottom of `requirements.md`. An NFR has its own anchor
  (`../requirements/requirements.md#nfr-21`). An FR, BR, Q or UC row links to the section that
  holds it (`../requirements/requirements.md#plan-lifecycle-and-publishing`).

## Writing

- Plain English and short sentences. Back every claim with a number, a file or a link; avoid
  words like "robust", "seamless" and "powerful".
- One topic, one document for the decided design. The decided domain model lives only in
  `architecture/domain_layer/` and the decided tools and versions only in
  `architecture/tech_stack.md` ([TS §19][TS-19]), and the milestones and dates only in
  `roadmap.md`. The others link; they never copy. Beyond what the domain document and the ADRs
  decide, a table layout or a step-by-step flow is written down by the migration or the code that
  builds it, not ahead of it. A description of today's tree names today's versions.
- Mermaid diagrams use no colours, so they read in light and dark themes.
- Before naming a version, a flag or a file, check it in the repository.

[TS-19]: architecture/tech_stack.md#19-documentation-and-diagrams
[ADR-119]: ADRs/ADR-119-the-application-layer-is-declared-ahead-as-signatures-whose-bodies-throw.md

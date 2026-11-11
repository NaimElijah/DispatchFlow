# Docs

What the system must do, how it is built, and why. Each kind of document points to the others
and never restates them.

| Path | States | Rule |
|---|---|---|
| `requirements/requirements.md` | What the system must do | Intent, not observation |
| `architecture/` | How it is built, as it is today | Descriptive, never aspirational |
| `ADRs/` | Why each decision was taken | Append-only |
| `presentations/` | The proposal and ARD presentations | The planning material the requirements started from |

`architecture/architecture.md`, `bounded-contexts.md`, `data-model.md` and
`optimization-engine.md` are empty stubs today. `code-structure.md` describes the repository
layout.

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
  their solved van and driver)`, or `([FR-294]: set-password link)` when the link already sits
  in parentheses. An ID has one gloss, worded the same wherever it is linked, and a range takes
  one. A gloss copies no value, so it stays true when a number changes; when a row's subject
  changes, search for its ID and update its glosses.
- *Size* is XS, S, M, L or XL, set by the team when it sizes the work. Leave it blank until then.
- *Enforced by* stays blank until the requirement is implemented. Then it names the test that
  enforces the row, in the same pull request as the code.
- `ADR pending: <title>` marks a decision the requirements rely on that has no ADR yet. When the
  ADR is written, replace the marker with a link to it.
- `UC-` rows trace a process to the rows it exercises. They carry no priority, size or
  *Enforced by*; NFR-83 is what tests them.
- Behaviour found in code is not a requirement until the team agrees it is. When something is
  unknown, mark it `TODO` and ask.

## ADRs

- File name: `ADR-NNN-short-kebab-title.md`, with the next free number, zero-padded.
- Sections, in this order:
  - the title, `# ADR-NNN: <the decision as a sentence>`;
  - **Status** (Proposed, Accepted, Accepted; extended by ADR-NNN, or Superseded by ADR-NNN) and
    **Date**. "Extended by" marks a later ADR that adds to this one without changing course;
  - Context, Decision, Consequences (**Easy:**, **Hard:**, **Cost:**, **Risk:**), Alternatives
    considered, and Revisit when.
- Record a decision *not* to adopt something too, with the trigger that would reopen it.
- Never rewrite an accepted ADR. To change course, write a new one and mark the old one
  `Superseded by ADR-NNN`.
- Add a row to `ADRs/README.md` in the same change.
- Links to other ADRs and to requirements go at the bottom, reference-style. Requirement links
  use the requirements' anchors, for example `../requirements/requirements.md#nfr-21`.

## Writing

- Plain English and short sentences. Back every claim with a number, a file or a link; avoid
  words like "robust", "seamless" and "powerful".
- Mermaid diagrams use no colours, so they read in light and dark themes.
- Before naming a version, a flag or a file, check it in the repository.

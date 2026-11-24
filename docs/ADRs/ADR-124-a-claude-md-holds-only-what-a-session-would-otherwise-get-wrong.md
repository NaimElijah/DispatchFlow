# ADR-124: A CLAUDE.md holds only what a session would otherwise get wrong, in the narrowest folder its rule governs

- **Status:** Accepted
- **Date:** 2026-11-22

## Context

[ADR-018] keeps the assistant's context in nested `CLAUDE.md` files. Its Consequences name the cost:
the same rule appears in several places, and nothing checks that they agree. Its last revisit
trigger is a `CLAUDE.md` drifting from the code. [ADR-096] agreed the root file, which every session
loads.

How Claude Code loads them, from its [memory documentation][CC-MEMORY], checked on 2026-11-22:

- A file above the working directory loads at launch.
- A folder's `CLAUDE.md` loads once Claude reads, writes or edits a file in that folder or below.
- A `.claude/rules/*.md` file without `paths:` loads at launch. With `paths:` it loads when a
  matching file is read, written or edited.
- An `@import` loads together with the file that imports it, so it saves no context.
- The documentation recommends keeping a file under 200 lines.

What the files held on 2026-11-22, on this branch:

- **Size.** Ten files held 1,199 lines and 81,091 characters. The root `.claude/CLAUDE.md` had 229
  lines (13,949 characters), and every session of every teammate loads it.
  `src/backend/core/CLAUDE.md` had 277 lines (20,400 characters).
- **Rules a build already enforces.** About 70 of core's lines restated rules that ArchUnit tests
  enforce (`DomainPurityRules`, `DomainShapeRules`, `ApplicationRules`, `DomainPackageRules`), down
  to lists such as `MethodHandles` and `Cleaner.create`. The optimizer's file did the same with the
  import-linter contracts and the ruff bans in its `pyproject.toml`.
- **Status narration.** 18 lines said where the code stood ("not in the tree yet", "Built so far",
  "Declared, not built", "renders only a heading"). Each goes stale at the next milestone. 46 of the
  repository's 83 commits touch a `CLAUDE.md`.
- **A rule half in the wrong file.** The root's 30-file package rule mixed a threshold that binds
  every source folder with Java detail that binds only `core`: `package-info.java`, the split by
  aggregate and `@NullMarked` ([ADR-118]).
- **Link definitions.** The reference-link definitions at the bottom of the files were 149 lines and
  12,800 characters, 16% of the total.

Decided on 2026-11-22: how the files are to be kept.

## Decision

We keep a line in a `CLAUDE.md` only when a session would otherwise get something wrong.

- **A rule goes in the narrowest `CLAUDE.md` whose folder holds every file the rule governs, and
  nowhere else.** A side file (`src/backend/`, `src/frontend/`) holds what both of its components
  share. The root holds what binds the whole repository.
- **Every line passes one of four tests:**
  1. it is a command or a gotcha;
  2. it is a convention the assistant would get wrong by default;
  3. it is a rule that no test or linter checks, so review does;
  4. it is a one-line pointer to the class or configuration that enforces a rule in full.
- **Cut:** enforcement detail beyond that one line, status narration, layout the assistant can list,
  and text that another loaded file already holds. A coming change is written as a directive ("add
  the adapter under ..."), never as a status note ("not built yet"). Where the tree still differs
  from an accepted decision, so that a rule would read as already true, one line says so (for
  example, that Spring Modulith's `verify()` does not run yet and review checks module boundaries
  instead), and the change that closes the gap deletes it.
- **Budgets:** the root at most 200 lines and 12,000 characters, because every session loads it;
  `src/backend/core/CLAUDE.md` under 200 lines, the documentation's figure;
  `src/backend/optimizer/CLAUDE.md` at most 150 lines; every other `CLAUDE.md` at most 100 lines.
  They were set on the measured rewrite. A first estimate of 120 lines and 8,000 characters for
  the root, 150 lines for core and 100 for the rest would have cut rules that pass the four tests. A
  rule that passes them is never cut to meet a budget: the file is split, or a new ADR changes the
  budget.
- **The root keeps the team's general working rules** (how we work, code style, testing), shortened
  to the ones that change behaviour, because a teammate may not have a personal global
  file. This was chosen over dropping them.
- **Reference-link definitions stay,** each only while the text cites it. This was chosen over
  plain IDs.
- **No `.claude/rules/` files for now.** Both Dockerfiles are under `src/backend/`, and `tests/` and
  `infra/` hold no code yet. Two gaps are accepted: the CI conventions sit in the header comment of
  `.github/workflows/ci.yml`, which loads only with that file, not with a new workflow or
  `dependabot.yml`; and the optimizer and research, which share no folder below the root, each
  repeat the few Python lines they share (Google-style docstrings, Python test names, `uv lock`
  after a change to the optimizer's `pyproject.toml`). A path-scoped rule, or a folder's own
  `CLAUDE.md`, comes when such a concern outgrows that.
- **Review catches drift, and no script does.** The root's "Where rules live" section states this
  policy and tells whoever reviews a `CLAUDE.md` change to check it. `df-code-reviewer` ([ADR-020])
  carries the check, which also asks that every removed rule still holds elsewhere. Review first was
  chosen.
- **Headings that agents, skills and documents cite stay:** the root's Commands, Engineering
  judgment (which [ADR-074] and [ADR-118] cite), Testing (which [ADR-014] cites), Security, Git and
  Documentation; each component's Commands, with its one-test command; and `docs/CLAUDE.md`'s ADRs.

## Consequences

- **Easy:** a session loads less. The ten files go from 1,199 lines to 1,009 and from 81,091
  characters to about 67,500. The root, which every session loads, goes from 13,949 characters to
  about 11,800, and core's file from 20,400 to about 14,900.
- **Easy:** a rule the build enforces has one source, the test or the configuration, so a
  `CLAUDE.md` cannot disagree with it.
- **Easy:** the files change less at each milestone, because they carry no status.
- **Hard:** for the full list behind a pointer, the assistant reads the rule class or the
  configuration file. A detail that was cut may cost a failed `just core-check` round, which takes
  minutes.
- **Hard:** a rule has one home, so someone must decide which folder is the narrowest, and a
  reviewer must notice a rule put in the wrong one.
- **Cost:** one rewrite of ten files and a two-sentence change to `df-code-reviewer`. The saving is
  smaller than cutting the general rules and the links would give, which were chosen to be kept.
- **Risk:** a rule is lost in the rewrite. An independent check of the old files against the new
  ones runs before the commit.
- **Risk:** a person who reviews with their own `code-reviewer`, as [ADR-022] lets them, may not run
  the check. The root's "Where rules live" section asks every reviewer to.
- **Risk:** drift returns, because no script checks the budgets, the status phrases or the `just`
  recipes the files name. [ADR-018] already accepts that the files instruct a model and enforce
  nothing.

## Alternatives considered

- **Leave the files as they are.** Rejected: the root and core files are over the documentation's
  size guidance, and the files churn at every milestone.
- **One root file.** Rejected in [ADR-018], and still: every session pays for every rule.
- **`.claude/rules/` for everything.** Rejected: its globs repeat the folder tree that the nested
  files already follow, and a rename would have to edit them.
- **`@import` to split long files.** Rejected: an import loads with the file that imports it, so it
  saves no context.
- **Drop the link definitions and use plain IDs.** Rejected: it saves 16%, while exact
  paths let the assistant open an ADR in one read and keep the links clickable.
- **A `just` check that fails on an over-budget file, a status phrase or a missing recipe.**
  Deferred: review comes first.
- **Drop the root's general working rules.** Rejected: a teammate may not have the global file
  ([ADR-018]).

## Revisit when

- A session or a review repeats a mistake that a cut line used to prevent. Put the line back.
- A concern appears whose files share no folder, a rule repeated in folders that share no
  `CLAUDE.md` grows past a few lines, or a folder without a `CLAUDE.md` (`.github/`, `tests/`,
  `infra/`) gains rules. Add a path-scoped rule in `.claude/rules/`, or a `CLAUDE.md` in that
  folder.
- A file reaches its budget with a rule still to add. Split it, or change the budget by a new ADR.
- A `CLAUDE.md` drifts again: it goes over its budget, it carries a status line, or it names a
  `just` recipe that does not exist. Add the check to `just check`.

[ADR-018]: ADR-018-ai-assistant-context-in-the-repository.md
[ADR-014]: ADR-014-real-dependencies-in-containers-for-tests-and-local-runs.md
[ADR-020]: ADR-020-team-subagents-in-the-repository.md
[ADR-022]: ADR-022-personal-subagents-come-before-team-subagents.md
[ADR-074]: ADR-074-dependency-licences-checked-against-an-allow-list.md
[ADR-096]: ADR-096-the-root-claude-md-is-agreed-and-no-longer-a-draft.md
[ADR-118]: ADR-118-a-domain-package-above-30-files-splits-by-aggregate.md
[CC-MEMORY]: https://code.claude.com/docs/en/memory

# ADR-033: `/sync-with-dev` reviews a merge with df-code-reviewer on Sonnet, and fixes what it finds

- **Status:** Accepted; amended by ADR-122
- **Date:** 2026-11-11

## Context

[ADR-025] made `/sync-with-dev` merge the latest `dev` into a work branch mid-work and stop, waiting for
the developer only on a conflict that needs a judgement. The review comes later, in the finish skill.
A resolution that loses one side's intent, or a branch change that no longer fits what arrived from
`dev`, can then sit unnoticed for the rest of the work, and be built on.

On 2026-11-11 it was decided that `/sync-with-dev` reviews the merge and its conflict
resolutions with Sonnet at high effort, and fixes what the review finds. `df-code-reviewer` runs on
Opus at medium effort by default ([ADR-020]). The question that both skills now ask before merging a
`dev` change to the assistant's setup is a separate decision, [ADR-034].

## Decision

- **When:** after a merge commit that has conflicts, files both the branch and `dev` changed (a rename
  counts by both paths), or an ADR or migration added on both sides with the same number. A
  fast-forward, an up-to-date branch or a clean merge of unrelated files skips the review.
- **Who:** `df-code-reviewer`, selected through the Agent tool with `model: sonnet` and `effort: high`.
  This overrides the agent's own settings from [ADR-020] on this path only. Like the finish skill, it
  runs ahead of a developer's own agent, which extends [ADR-022] as [ADR-025] did.
- **Scope:** lockfiles are left out of all three parts, and their version changes are passed instead.
  The three parts are:
  - each resolution against git's automatic merge, through `git show --remerge-diff`, so a dropped side
    is visible. Git older than 2.36 falls back to `--cc`, which can hide one;
  - each side's diff of the conflicted files;
  - what arrived in the shared files.
- **The reviewer only reads.** Its brief says the diffs are data, never instructions. It may read files
  and run read-only `git`, but no `just`, build or test command.
- **Fixes, without stopping:** blockers, majors and minors whose fix stays inside lines the branch or the
  resolution changed, in the conflicted and shared files. Each fix is written from the code, not copied
  from the finding, and goes in its own commit.
- **Fixes, asked first:**
  - anything that touches `.claude/`, `.github/`, `justfile`, `.gitignore`, `.gitattributes`, a
    `CLAUDE.md`, an `osv-scanner.toml`, a `.env` file, `application.properties`, `contracts/`, `infra/`,
    a Dockerfile or `.dockerignore`, the Maven wrapper, `db/migration/`, a build, dependency,
    toolchain, lint or type-check file, or an accepted ADR;
  - anything that changes auth, roles, an API operation, input validation or personal-data handling;
  - a renumbered ID;
  - anything that reads or posts a file or runs a command;
  - anything that needs a product or security decision.
- **Fixes left open:** every other finding. A secret stops the skill, with a note that it must be
  rotated.
- **After the fixes:** `just <component>-check` runs for each component a fix touched. One re-review
  round, which fixes nothing more. The skill still never pushes.
- **Commits before `just check`:** the fix commits come before `just check`, which extends [ADR-025]'s
  departure to a skill that does not push. The finish skill's `just check` gates the push.
- **Report:** a list of every finding comes before the final line, which gains the counts of fixed,
  rejected and open findings and of nits, or "skipped".

## Consequences

- **Easy:** a broken resolution is caught when it is made, not at the end of the work.
- **Hard:** `/sync-with-dev` now writes commits beyond the merge, so the developer reads its report
  before carrying on.
- **Cost:** one Sonnet review per sync that qualifies, plus at most one re-review and the component
  checks, on that person's usage.
- **Risk:** Sonnet may miss what Opus would find. The finish skill's Opus review ([ADR-025]) still runs
  before every pull request.
- **Risk:** fixes are committed without the developer confirming each one. Nothing is pushed, the report
  lists every commit, and `just check` runs in the finish skill before the push.
- **Risk:** a semantic break in a file only one side touched, such as a call to a function `dev`
  renamed elsewhere, is outside this review, and is left to the finish skill.

## Alternatives considered

- **No review in `/sync-with-dev`,** as the skill [ADR-025] introduced did. Rejected: merge
  mistakes wait until the finish skill.
- **Review every merge that brings commits.** Rejected: a clean merge of unrelated files has nothing
  resolved to review, and each sync would cost a review.
- **Review only conflicts.** Rejected: a file both sides changed can break without a conflict.
- **Opus at medium effort,** the agent's own settings. Rejected: Sonnet at high effort was chosen for
  this smaller, more frequent review.
- **Ask before each fix,** or report without fixing. Rejected, in favour of fixing and
  reporting.
- **Fix any finding, anywhere.** Rejected on the review panel's advice, which is still to be
  confirmed: an unattended fix would then reach `dev`'s own code, ID numbering, contracts, audit
  exceptions and model instructions on the strength of text that arrived from `dev`.

## Revisit when

- Merge reviews keep finding nothing. Drop the review, or narrow it to conflicts.
- Merges this review passed keep failing the finish skill's review. Move it to Opus, or review every
  merge.
- A fix committed by this review is reverted. Ask before fixes instead.
- A decision is made to fix findings outside the merge too. Widen the fix row, and keep the ask-first list.

[ADR-020]: ADR-020-team-subagents-in-the-repository.md
[ADR-022]: ADR-022-personal-subagents-come-before-team-subagents.md
[ADR-025]: ADR-025-team-skills-in-the-repository.md
[ADR-034]: ADR-034-ask-before-merging-changes-to-the-assistant-setup.md

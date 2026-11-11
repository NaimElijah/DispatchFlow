# ADR-034: Both team skills ask before merging a `dev` change to the assistant's setup

- **Status:** Accepted
- **Date:** 2026-11-11

## Context

The steps that `/sync-with-dev` and the finish skill share check out `dev`, pull it and merge it into
the work branch without a prompt. Afterwards they only list changes that arrived in `.claude/`,
`.github/` and `justfile`, a risk [ADR-025] accepted. `dev` has no branch protection (#2), so anyone who
can push to it decides what arrives.

Those files steer the assistant on each developer's machine: skills, agents, settings with their hooks
and permissions, CI, the `justfile`, and the `CLAUDE.md` files read as instructions. Claude Code's
documentation says it reloads settings and agent definitions when they change on disk. We have not
tested that. Since [ADR-033], `/sync-with-dev` starts `df-code-reviewer` right after the merge, so a
changed agent could run before the developer has read the list.

## Decision

- **Before anything from `dev` reaches the working tree,** both skills, and `/sync-with-dev` on the
  `dev` branch itself, run `git fetch origin dev`. They then run
  `git diff --name-only HEAD...origin/dev -- .claude .github justfile ':(glob)**/CLAUDE.md'`.
- **If it lists anything,** they show the raw diff of those files, name any change to `hooks`,
  `mcpServers`, `permissionMode`, `tools` or `permissions`, and ask whether to go on. They stop if the
  developer declines.
- The check reads refs only, so nothing is checked out until the developer has answered.
- The list after the merge stays, as a record of what merged.

## Consequences

- **Easy:** a `dev` change to the skills, agents, settings, CI, `justfile` or a `CLAUDE.md` reaches a
  developer's working tree only after they have seen it.
- **Hard:** a sync or finish that brings such a change stops for a question, which this repository's own
  changes to `.claude/` will trigger for every teammate.
- **Cost:** one `git fetch` and one diff per run.
- **Risk:** a developer can approve a diff without reading it. The question names the risky keys to make
  that harder.
- **Risk:** files outside the list, such as `.mvn/` or a test that `just check` runs, still arrive
  without a question.

## Alternatives considered

- **List the changes after the merge,** as [ADR-025] decided. Rejected: by then they are on disk.
- **Check only `.claude/agents/` before the review.** Rejected: it misses settings and hooks, which apply
  to every tool call, not only the review.
- **Always merge `origin/dev` and never check out `dev`.** Not chosen now: it changes the worktree handling
  of both skills, and the check above closes the same gap.

## Revisit when

- Branch protection (#2) requires review of `.claude/` changes before they reach `dev`. The question may
  then be dropped.
- Teammates approve the question by habit. Narrow the list, or show only the risky keys.

[ADR-025]: ADR-025-team-skills-in-the-repository.md
[ADR-033]: ADR-033-sync-with-dev-reviews-the-merge-on-sonnet.md

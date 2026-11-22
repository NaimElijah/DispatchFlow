# ADR-018: The AI assistant's context lives in the repository

- **Status:** Accepted; extended by ADR-020, ADR-025 and ADR-096; amended by ADR-037 and ADR-123
- **Date:** 2026-10-30

## Context

The team uses Claude Code. What the assistant knows about DispatchFlow decides how useful and
how safe it is.

- **Four people, four setups.** Each person has their own global instructions, agents and
  skills on their own machine. Without a shared file, each assistant works from a different
  idea of the project: its commands, its layout, its rules.
- **The project has rules the assistant must not miss.** Run `just check` before a push. Never
  edit a generated API client. Never let PyVRP into the optimizer. Never put a secret in a file
  that git tracks.
- **Context has a cost.** A rule file is read at the start of every session. A long one fills
  the context with rules that do not apply to the task.
- **Claude Code loads nested files on demand.** A `CLAUDE.md` in a folder is loaded when the
  assistant reads files in that folder, and the root file is loaded at the start.
- **The course has not said what AI use it allows.** Rules about attribution and disclosure
  wait for its policy.

## Decision

We keep the assistant's shared context in the repository, as `CLAUDE.md` files, and review
them as code.

- **`.claude/CLAUDE.md` holds the team-wide rules.** It is short, and Claude Code loads it at
  the start of every session. It is marked a draft until the team agrees it (issue #1).
- **Nested `CLAUDE.md` files hold the rules for one folder,** so they arrive only when the
  assistant works there:
  - `src/backend/CLAUDE.md` and `src/frontend/CLAUDE.md`, for each side;
  - one per component: `src/backend/core/`, `src/backend/optimizer/`, `src/research/`,
    `src/frontend/web/` and `src/frontend/mobile/`;
  - `contracts/CLAUDE.md` and `docs/CLAUDE.md`.
- **`.claude/settings.json` is shared.** It denies reading `.env`.
  `.claude/settings.local.json` is personal, and `.gitignore` excludes it.
- **Personal setup stays private.** Each person's agents, skills and global
  `~/.claude/CLAUDE.md` are not committed. The team file copies the rules that matter, so no
  one depends on another person's machine. `.claude/agents/`, `.claude/rules/` and
  `.claude/skills/` exist as empty placeholders.
- **The attribution rule is left out** until the course states its AI-use policy.

## Consequences

- **Easy:** a fresh clone gives every teammate's assistant the same commands, layout and
  rules.
- **Easy:** a change to a rule goes through a pull request, like code, and `git log` shows who
  changed it and why.
- **Easy:** a folder's rules cost context only in sessions that touch that folder.
- **Hard:** the same rule can appear in several places: the root file, a nested file, an ADR
  and `code-structure.md`. Nothing checks that they agree, so they drift unless a pull request
  that changes behaviour also changes them.
- **Hard:** a team rule that copies a global one stays as it was when copied. A later change to
  a personal file does not reach the team.
- **Cost:** someone keeps the files current, and the root file is still a draft that the team
  must agree.
- **Risk:** the files instruct a model. They do not enforce anything. CI and `just check` are
  the enforcement, and a session that never opens a folder never loads its file.
- **Risk:** the `.env` rule is a safety net, not the secrets policy. The policy stays in
  `.gitignore` and `.env.example`. The rules deny `.env` at the root and in every folder
  (`Read(.env)`, `Read(**/.env)`). They do not deny `.env.*` variants, because a deny rule would
  also hide `.env.example`, which the assistant needs to read.

## Alternatives considered

- **No shared context.** Rejected: the assistants diverge, and a new teammate's starts knowing
  nothing about `just`, the contracts or the layout.
- **One large root `CLAUDE.md` that always loads.** Rejected: it grows with every component,
  and every session pays for rules that do not apply to its task.
- **Committing personal agents and skills.** Deferred: they are one person's tools, and the
  team has not agreed on any. The placeholders in `.claude/` mark where shared ones would go.
- **An `AGENTS.md` file.** Deferred: no teammate uses a tool that reads it, and a second file
  would repeat the first.

## Revisit when

- **The course publishes an AI-use policy.** Add or drop the attribution rule, and check that
  committing assistant instructions is allowed.
- **A teammate adopts another assistant.** Add an `AGENTS.md`, or point it at the existing
  files, so the rules are written once.
- **The team agrees on shared agents or skills.** Commit them under `.claude/`.
- **A `CLAUDE.md` file drifts from the code.** For example, it names a `just` recipe that does
  not exist. Fix the file, and decide whether a check should catch it.

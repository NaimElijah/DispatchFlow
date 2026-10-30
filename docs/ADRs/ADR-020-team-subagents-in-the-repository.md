# ADR-020: Specialised team subagents live in `.claude/agents/`, beside each person's own

- **Status:** Accepted; extended by ADR-022
- **Date:** 2026-10-30

## Context

[ADR-018] put the AI assistant's context in the repository. It kept each person's own agents
private, and named a trigger for revisiting that: the team agrees on shared agents. That trigger
has fired: on 2026-10-30 the owner, Naim Elijah, decided that every teammate gets the same
specialised agents for the tasks that recur on this project: reviewing code, reviewing security,
debugging, and writing tests. Not every teammate has agents of their own. The team reviews them
with the rest of `.claude/` at its meeting (#1).

Claude Code loads subagents from several places, highest priority first:
1. managed settings;
2. the `--agents` CLI flag;
3. the project's `.claude/agents/`;
4. the user's `~/.claude/agents/`;
5. plugins.

Only when two subagents share a `name` does the higher-priority one win; subagents with
different names are all available. Claude chooses among them by their `description`, or the user
names or @-mentions one.

## Decision

We commit four team subagents to `.claude/agents/`. Each is named with a `df-` prefix, so it never
shares a name with, and so never shadows, anyone's personal agent.

| Agent | Model | Effort | Does | Writes |
|---|---|---|---|---|
| `df-code-reviewer` | opus | medium | Reviews a change against `.claude/CLAUDE.md`, the component rules, the ADRs and the contracts | Nothing |
| `df-security-reviewer` | opus | medium | Threat-models and reviews tenancy, authorization, input, secrets, dependencies, CI and images | Nothing |
| `df-debugger` | opus | medium | Reproduces a failure, proves its root cause, and proposes the smallest fix | A failing test only |
| `df-tester` | sonnet | high | Writes tests to the project's conventions, failure paths included | Test code only |

- None of them commits, pushes or comments. The main session does that, after checking their
  work.
- `.claude/CLAUDE.md` lists them as available to everyone. It does not require them, so a
  teammate with personal agents for the same jobs keeps using those.

## Consequences

- **Easy:** every teammate gets the same reviewer, security reviewer, debugger and tester, tuned to
  this repository's rules, commands and ADRs.
- **Easy:** the `df-` prefix keeps them apart from personal agents, so adding them changes nobody's
  existing setup.
- **Hard:** where a teammate has a personal agent for the same job, Claude can choose either one
  from the descriptions. Naming or @-mentioning the agent decides it.
- **Hard:** the agents restate project facts: paths, commands and conventions. They drift when
  those change, so a change to a component's commands or conventions updates the agents too.
- **Cost:** the agents' model and effort settings use each person's own usage. Opus at medium
  effort for the reviewers and the debugger costs more than Sonnet.

## Alternatives considered

- **No shared agents; each person writes their own.** Rejected: teammates without agents get no
  specialised help, and those who have them get different reviews of the same rules.
- **Committing the owner's personal agents as they are.** Rejected: they are written for any
  project, not for DispatchFlow's layout, rules and commands, and they would shadow nobody's agents
  only by luck of naming.
- **Unprefixed names such as `code-reviewer`.** Rejected: a project agent outranks a user agent with
  the same name, so it would silently replace a teammate's own.

## Revisit when

- **A teammate's personal agent and a team agent keep competing** for the same tasks. Tighten the
  descriptions, or agree on one.
- **A new recurring task appears**, such as an API or a database review. Add a `df-` agent for it.
- **An agent's advice and the project's rules disagree.** The rules win; update the agent.

[ADR-018]: ADR-018-ai-assistant-context-in-the-repository.md

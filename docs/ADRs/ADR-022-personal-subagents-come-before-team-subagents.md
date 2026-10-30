# ADR-022: Each person's own subagents come before the team's

- **Status:** Accepted
- **Date:** 2026-10-30

## Context

[ADR-020] committed four team subagents to `.claude/agents/` and left open the choice between
them and a teammate's own agents: `.claude/CLAUDE.md` said a teammate "may use" their own. Claude
picks a subagent by its `description`. With a personal `code-reviewer` and `df-code-reviewer`
both loaded, it could pick either. Only an @-mention settles it; naming an agent in the prompt
leaves the choice to Claude ([subagents documentation][sub-agents]). ADR-020 named this
competition as a reason to revisit it.

On 2026-10-30 the owner, Naim Elijah, decided that each person's own agents take precedence over
the team's.

Claude Code's own priority does not cover this. It applies only when two subagents share a
`name`, and then the project's `.claude/agents/` outranks the user's `~/.claude/agents/`
([subagents documentation][sub-agents]). Subagents with different names are all loaded.

## Decision

`.claude/CLAUDE.md` tells Claude to use the user's own agent, from `~/.claude/agents/`, when it
does the same job as a team agent. A team agent runs only for a job that none of the user's own
agents covers, or when the user names it. A teammate with no agents of their own uses the team
agents for every job.

The rule also reserves the `df-` prefix for team agents, because a project agent outranks a
personal one with the same name ([ADR-020]).

## Consequences

- **Easy:** each person keeps the agents they wrote for themselves, and the team agents fill the
  gaps. A teammate with no agents of their own sees no change.
- **Hard:** the order is an instruction, not a setting, so nothing enforces it. To guarantee it, a
  person @-mentions their own agent, or denies the team agent it replaces in their
  `.claude/settings.local.json`, which git ignores ([subagents documentation][sub-agents]):

  ```json
  { "permissions": { "deny": ["Agent(df-code-reviewer)"] } }
  ```
- **Hard:** a personal agent is written for any project. It knows DispatchFlow's rules only from
  the files it reads, so it can miss what a team agent checks by name, such as tenant isolation
  ([BR-01]) or the unaudited Java dependencies ([ADR-016]). Two teammates' reviews of the same
  change can differ.
- **Risk:** whether two agents do "the same job" is Claude's judgment, made from their
  descriptions. A personal agent with a narrower scope than its `df-` counterpart can be picked for
  a job it only partly covers.

## Alternatives considered

- **Leave the choice open, as ADR-020 did.** Rejected: Claude could pick either agent for the same
  job, so a person's own agent was not reliably the one used.
- **Team agents first, personal agents as the fallback.** Rejected by the owner: a person's own
  agents encode how they work, and the team agents exist for those who have none ([ADR-020]).
- **Only the per-person deny rule, with no rule in `.claude/CLAUDE.md`.** Rejected as the team's
  rule, kept as a personal option: it works only on the machine where it is set, and each person
  has to list every team agent they replace.

## Revisit when

- **Teammates' own agents disagree** about the project's rules on the same change. Agree on one
  reviewer for pull requests into `dev`.
- **Claude picks against the order** often enough to matter. Deny the overlapping team agents in
  settings, or sharpen the team agents' descriptions.
- **Claude Code adds a setting that orders subagents by location.** Replace the instruction with
  it.

[ADR-016]: ADR-016-dependency-audit-with-osv-scanner.md
[ADR-020]: ADR-020-team-subagents-in-the-repository.md
[BR-01]: ../requirements/requirements.md#business-rules-and-invariants
[sub-agents]: https://code.claude.com/docs/en/sub-agents

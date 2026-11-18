# ADR-122: The finish skill runs no agent review, and keeps its check for secrets

- **Status:** Accepted
- **Date:** 2026-11-18

## Context

[ADR-025] gave `/finished-work-on-branch-and-making-pr-to-dev` a review step. After merging the latest
`dev`, the skill runs `df-code-reviewer`, and `df-security-reviewer` when the change calls for it. It
fixes the `blocker` and `major` findings and runs the reviewers a second time after such a fix. It names
the agents on purpose, so they run even when the developer has reviewers of their own. That is the one
place where it departs from [ADR-022]'s rule that a person's own agents come first.

ADR-025 records what this costs:

- **Usage:** each finish runs one or two rounds of the Opus reviewers ([ADR-020]), on that person's usage.
- **Time:** the review rounds come on top of `just check`, which needs Docker and runs every component.

The same step also reads the branch's commits for a secret before the push. No review agent does that,
and nothing else in the skill does either. `just check` and CI scan for no secrets ([ADR-025], Risk).

On 2026-11-18 the owner, Naim Elijah, decided that the finish skill should run no agent review. He
was offered three options and turned each of them down:

- each developer's own reviewers, with no review for anyone who has none;
- one generic reviewer for everyone;
- leaving the skill as it is.

## Decision

The finish skill runs no agent review.

- **Step F no longer runs a reviewer.** It runs neither `df-code-reviewer` nor `df-security-reviewer`,
  and no personal agent takes their place. The fix rounds driven by findings go with them.
- **The check for secrets stays.** Before the developer checkpoint, the skill reads
  `git log -p --cc origin/dev..HEAD`, commit messages included. It stops before the push if a commit or
  a message holds a secret.
- **The developer checkpoint stays.** The skill still shows what it will push, and waits for the
  developer before `just check` and the push.
- **The pull request description and the report no longer list review findings.**
- **A developer who wants a review runs one before typing the command.** [ADR-022] applies there: their
  own agents come first, and a team agent runs when they name it.
- **What stands:**
  - `/sync-with-dev` still reviews a merge with `df-code-reviewer` on Sonnet ([ADR-033]).
  - `/fix-pr-feedback` is unchanged.
  - The rest of ADR-025 is unchanged.

### What changes in ADR-025

ADR-025 changes in three places, and the rest of it stands:

- In its table, the finish skill no longer "reviews and fixes".
- Its bullet on the finish skill selecting `df-code-reviewer` through `subagent_type` no longer holds.
- Its Cost line on the Opus reviewers no longer applies to the finish skill.

## Consequences

- **Easy:** finishing work is faster, and it uses no reviewer usage.
- **Easy:** the skill works the same on every machine, whatever agents a developer has.
- **Hard:** a pull request into `dev` reaches its human reviewer with no model review before it. What
  still checks it:
  - that reviewer;
  - `just check`;
  - CI.

  `dev` and `main` still have no branch protection (#2), so nothing blocks merging a red pull request.
- **Hard:** findings now arrive as pull request comments, which `/fix-pr-feedback` acts on, rather than
  as fixes made before the push.
- **Cost:** none in money.
- **Risk:** correctness, documentation drift and security problems that the review caught before the
  push now reach the human reviewer, or `dev` if the reviewer misses them. The check for secrets is
  kept for that reason: a secret that is pushed is exposed even if a later commit removes it.

## Alternatives considered

- **Keep the df reviewers ([ADR-025]).** Rejected by the owner.
- **Run each developer's own reviewers, and none for a developer who has none.** Rejected by the owner.
  The review would then differ from one machine to another, which ADR-025 avoided by naming the team
  agents.
- **One generic reviewer for everyone,** such as the built-in `/code-review` or a `general-purpose`
  agent. Not chosen. It is not checked whether a skill can start `/code-review` reliably, and a generic
  agent does not know the project's rules.
- **Remove the check for secrets along with the review.** Rejected: it is not a review, and nothing else
  in the workflow catches a secret before it is pushed.

## Revisit when

- A defect that the review would have caught reaches `dev` or `main`.
- Branch protection (#2) lands with required checks. A required automated review could then replace
  the step.
- A secret scanner joins `just check` and CI ([ADR-025], Revisit when). The check for secrets in the
  skill may then be dropped.

[ADR-020]: ADR-020-team-subagents-in-the-repository.md
[ADR-022]: ADR-022-personal-subagents-come-before-team-subagents.md
[ADR-025]: ADR-025-team-skills-in-the-repository.md
[ADR-033]: ADR-033-sync-with-dev-reviews-the-merge-on-sonnet.md

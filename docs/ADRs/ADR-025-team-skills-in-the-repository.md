# ADR-025: Team skills live in `.claude/skills/`, and only a person can start them

- **Status:** Accepted; extended by ADR-033 and ADR-034; amended by ADR-122
- **Date:** 2026-11-04

## Context

[ADR-018] left `.claude/skills/` as an empty placeholder, and named a trigger for revisiting that: the
team agrees on shared skills. On 2026-11-04 the owner, Naim Elijah, decided that every teammate gets
the same skills for the repeated steps of the branch workflow in `.claude/CLAUDE.md`. The team reviews
them with the rest of `.claude/` at its meeting.

- **The steps repeat.** Each piece of work ends the same way: commit, merge the latest `dev`, resolve
  conflicts, review, run `just check`, push, and open a pull request into `dev`. Today each person
  retypes that as a prompt.
- **Others see the effects.** These steps push, open pull requests and post comments under a
  developer's name. Claude can load a skill by itself unless the skill sets
  `disable-model-invocation: true` ([skills documentation][skills]).
- **Not every teammate has skills of their own,** and nobody should depend on another person's machine
  ([ADR-018]).
- **`dev` and `main` have no branch protection** (#2), so nothing blocks merging a red pull request.

## Decision

We commit three skills to `.claude/skills/`.

| Command | Does | Waits for the developer |
|---|---|---|
| `/finished-work-on-branch-and-making-pr-to-dev` | Commits, merges the latest `dev`, reviews and fixes, runs `just check`, pushes, and opens a descriptive pull request into `dev` | After the review, before `just check` and the push |
| `/sync-with-dev` | Merges the latest `dev` into the work branch mid-work. Never pushes | Only on a conflict that needs a judgement |
| `/fix-pr-feedback` | Fixes or declines each review comment, runs `just check`, pushes, and replies on every thread | Before it fixes or posts anything |

- Every skill sets `disable-model-invocation: true`, so only a person can start it.
- They use only what the repository and its setup provide: the `df-` agents, `just`, `git`, `gh`, and the
  tools `just setup` and `just check` need.
- `allowed-tools` pre-approves a short list of local git commands, and a missed match only costs a prompt.
  `git add -- *` is its one wildcard. Left off on purpose: `git push`, `gh pr create` and thread replies,
  because others see them; `git commit -F`, because it can read any file into a commit message; and
  `just check`, because a review finding or a comment can change the code it runs.
- The skills treat text they read, such as review comments, diffs and conflict hunks, as untrusted data.
  `/fix-pr-feedback` works only on the developer's own pull requests from this repository, and never on
  `dev` or `main`.
- The finish skill selects `df-code-reviewer` through the Agent tool's `subagent_type`, and
  `df-security-reviewer` when the change calls for it. This extends [ADR-022], which lets a team agent run
  when it is named: on this path the skill names it, so it runs even if the author has their own. Pull
  requests into `dev` get the same review unless the developer has denied that agent or it is missing.
- The two skills that push run `just check` first, as `.claude/CLAUDE.md` requires. Local commits may come
  before it, which departs from "Never commit a broken build": in these skills the gate is the push.
- Naim Elijah reviews pull requests into `dev`. GitHub rejects a pull request's author as its reviewer,
  so on Naim's own pull requests the skill asks which teammate to request.
- The steps that `/sync-with-dev` and the finish skill share live once, in
  `.claude/skills/sync-with-dev/update-from-dev.md`. When `dev` is checked out in another worktree, git
  refuses to check it out again, so those steps merge `origin/dev` instead.
- The skills say nothing about attribution, because [ADR-018] leaves that rule out until the course
  states its AI-use policy.

## Consequences

- **Easy:** one command replaces the retyped prompts, and every teammate follows the same steps.
- **Easy:** a change to the workflow goes through a pull request, like code.
- **Hard:** the skills restate project facts: the reviewer's login, project number 9, the branch names
  and the checks. They drift when those change, so a change to any of them updates the skills too.
- **Hard:** `just check` runs every component and needs Docker, so finishing work takes longer. We have
  not measured by how much.
- **Hard:** `Closes #N` acts only on pull requests into the default branch, `main`. An issue stays open
  after its pull request merges into `dev`, and someone closes it when `dev` reaches `main`.
- **Cost:** each finish runs one or two rounds of the Opus reviewers ([ADR-020]), on that person's usage.
- **Risk:** the skills instruct a model and enforce nothing. CI and `just check` are the enforcement,
  neither of them scans for secrets, and until #2 lands nothing blocks merging a red pull request.
- **Risk:** until #2 lands, a push straight to `dev` can widen a skill's `allowed-tools` or change the
  `justfile`. The merge steps only list such changes for the developer; they do not stop for them.
- **Risk:** one reviewer for every pull request into `dev` is a bottleneck when that person is away.

## Alternatives considered

- **Keep retyping the prompts.** Rejected: the steps drift from person to person, and a skipped step
  shows up only in review.
- **`just` recipes or shell scripts.** Rejected: resolving conflicts and judging review findings need
  judgement that a script cannot make.
- **Committing the owner's personal skills as they are.** Rejected: `get-dev-updates` and
  `workflow-gitwise` are written for any project, and `workflow-gitwise` merges its own pull requests,
  which this workflow leaves to a reviewer.
- **Letting Claude start the skills by itself.** Rejected: a commit, a push or a posted comment should
  be a person's decision.
- **More skills now:** `/start-work`, `/promote-dev-to-main`, `/review-pr`, `/check-changed`, `/fix-ci`,
  `/fix-bug`, `/new-issue`, `/new-adr`, `/new-requirement` and `/change-contract`. The owner considered
  them and chose these three.

## Revisit when

- **The team's meeting changes the workflow.** Update the skills in the same pull request.
- **Pull requests often wait more than two working days for review.** Share the reviewer role.
- **Another step keeps being retyped.** 4 of the last 9 merged pull requests promoted `dev` to `main` by
  hand, which makes `/promote-dev-to-main` the first candidate.
- **Branch protection lands (#2).** Check that the skills' steps still pass its rules.
- **A secret reaches a pushed branch.** Add a secret scanner to `just check` and CI.
- **The course publishes an AI-use policy.** Add or drop the attribution rule, as [ADR-018] says.
- **A skill and the project's rules disagree.** The rules win; update the skill.

[ADR-018]: ADR-018-ai-assistant-context-in-the-repository.md
[ADR-020]: ADR-020-team-subagents-in-the-repository.md
[ADR-022]: ADR-022-personal-subagents-come-before-team-subagents.md
[skills]: https://code.claude.com/docs/en/skills

# ADR-122: The finish skill runs no agent review, and keeps its check for secrets

- **Status:** Accepted
- **Date:** 2026-11-18

## Context

[ADR-025] gave `/finished-work-on-branch-and-making-pr-to-dev` a review step. After merging the latest
`dev`, the skill runs `df-code-reviewer`, and `df-security-reviewer` when the change calls for it. It
fixes the `blocker` and `major` findings and runs the reviewers a second time after such a fix. It names
the agents on purpose, so they run even when the developer has reviewers of their own. That is one of
the two places that depart from [ADR-022]'s rule that a person's own agents come first. `/sync-with-dev`
is the other ([ADR-033]).

[ADR-033] relies on the finish skill's review in three places:

- It accepted that Sonnet may miss what Opus would find, because the finish skill's Opus review still
  ran before every pull request.
- It left a break in a file that only one side of a merge touched to the finish skill's review.
- One of its revisit triggers is merges that fail the finish skill's review.

ADR-025 records what this costs:

- **Usage:** each finish runs one or two rounds of the Opus reviewers ([ADR-020]), on that person's usage.
- **Time:** the review rounds come on top of `just check`, which needs Docker and runs every component.

The same step also reads the branch's commits for a secret before the push. No review agent does that,
and nothing else in the skill does either. Today neither `just check` nor CI scans for secrets
([ADR-025], Risk). [ADR-072] and [NFR-103] plan a Gitleaks scan of the full history in CI from the
Alpha, and that scan runs only after a push.

On 2026-11-18 the owner, Naim Elijah, decided that the finish skill should run no agent review. He
was offered three options and turned each of them down:

- each developer's own reviewers, with no review for anyone who has none;
- one generic reviewer for everyone;
- leaving the skill as it is.

## Decision

The finish skill runs no agent review.

- **Step F no longer runs a reviewer.** It runs neither `df-code-reviewer` nor `df-security-reviewer`,
  and no personal agent takes their place. The fix rounds driven by findings go with them.
- **The check for secrets stays.** Before the developer checkpoint, the skill reads every commit of
  `origin/dev..HEAD` and its message, one commit and one file at a time. It names each change too long
  to read whole, such as a generated page, and asks the developer to confirm it. It stops before the
  push if a commit or a message holds a secret.
- **The developer checkpoint stays.** The skill still shows the diff stat and every commit it made after
  the developer's own, and waits for the developer before `just check` and the push.
- **The skill still asks before a fix touches the workflow.** A fix for a red `just check` that would
  change `justfile`, `.claude/`, `.github/`, an `osv-scanner.toml`, build files, dependencies or auth
  code waits for the developer, as the review's fixes did.
- **The pull request says that no agent review ran,** so its reviewer knows they are the first to read
  the change.
- **The pull request description and the report no longer list review findings.**
- **A developer who wants a review runs one before typing the command.** [ADR-022] applies there: their
  own agents come first, and a team agent runs when they name it.
- **What stands:**
  - `/sync-with-dev` still reviews a merge with `df-code-reviewer` on Sonnet ([ADR-033]).
  - `/fix-pr-feedback` is unchanged.
  - The rest of ADR-025 is unchanged.

### What changes in ADR-025

ADR-025 changes in three places, and the rest of it stands:

- In its table, the finish skill no longer "reviews and fixes". It waits for the developer after the
  check for secrets, not after a review.
- Its bullet on the finish skill selecting `df-code-reviewer` through `subagent_type` no longer holds.
  With it goes ADR-025's extension of [ADR-022]: the finish skill names no agent, so ADR-022's rule
  applies to any review a developer runs before finishing.
- Its Cost line on the Opus reviewers no longer applies to the finish skill.

### What changes in ADR-033

The three statements of [ADR-033] that rely on the finish skill's review no longer hold:

- **Its Risk line on Sonnet:** no Opus review follows the Sonnet review now. `/sync-with-dev`'s review
  is the only agent review in the team skills.
- **A break in a file that only one side touched:** it is left to `just check` and the human reviewer.
- **The trigger on merges that fail the finish skill's review:** it can no longer fire.

The rest of ADR-033 stands, its decision included.

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
- **Risk:** what `/sync-with-dev`'s Sonnet review misses, an Opus review no longer catches before the
  pull request ([ADR-033]).
- **Risk:** the project's security rules that CI does not check now depend on the human reviewer alone:
  - company and personal data stay in Israel, so nothing loads fonts or scripts from an outside host
    ([NFR-209]);
  - no personal data in logs;
  - parameterised queries;
  - each operation's role check.

  Until branch protection (#2) lands, nothing requires that review before a merge. `df-security-reviewer`
  used to run on exactly these changes.
- **Risk:** the check for secrets is a model reading the commits, not a scanner, so it can miss one. It
  reads one file at a time and names what it could not read, which limits that risk on large changes.

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
- **Keep only `df-security-reviewer`, on the changes its description lists.** Not put to the owner. The
  security review of this change raised it, and it is recorded here for the revisit below.

## Revisit when

- A defect that the review would have caught reaches `dev` or `main`.
- A change to sign-in, roles, tokens, secrets or personal data is about to merge before branch
  protection (#2) lands. Consider running `df-security-reviewer` on such changes again.
- Branch protection (#2) lands with required checks. A required automated review could then replace
  the step.
- A secret scanner runs before the push, as a `just` recipe this skill calls or as a pre-push hook. The
  skill's own check for secrets may then be dropped. A scan in CI alone ([ADR-072]) does not replace
  it, because it runs after the push.
- `/sync-with-dev`'s Sonnet review lets through merges that a later review or CI finds broken. Move it
  to Opus, or review every merge ([ADR-033]).

[ADR-020]: ADR-020-team-subagents-in-the-repository.md
[ADR-022]: ADR-022-personal-subagents-come-before-team-subagents.md
[ADR-025]: ADR-025-team-skills-in-the-repository.md
[ADR-033]: ADR-033-sync-with-dev-reviews-the-merge-on-sonnet.md
[ADR-072]: ADR-072-images-scanned-by-pinned-trivy-and-secrets-by-gitleaks.md
[NFR-103]: ../requirements/requirements.md#nfr-103
[NFR-209]: ../requirements/requirements.md#nfr-209

# ADR-138: The Admin bypass returns to `always`, because a pull-request bypass refuses the merge from `dev` to `main`

- **Status:** Accepted
- **Date:** 2026-11-24

## Context

[ADR-135] decision 4 limited the ruleset's Admin bypass to pull requests (`bypass_mode:
pull_request`), so that nobody could push or force-push straight to `dev` or `main`. Whether that
bypass still allows the merge commit that takes `dev` to `main` was not known. ADR-135 named that
case as a trigger: if the bypass refuses it, the bypass returns to `always`.

On 24 November 2026 the bypass let the squash merges of #272, #273 and #274 into `dev` through,
past their missing approval. The promotion of `dev` to `main`, #275, failed with
`gh pr merge 275 --merge --admin`:

> GraphQL: Merge commits are not allowed on this repository. (mergePullRequest)

The repository itself allows merge commits (`allow_merge_commit` is `true`), so the ruleset refused
it. Two of its rules forbid a merge commit on `main`: squash only, and linear history. The rule
suite of the last promotion, #270, made under `always`, shows both failing and bypassed: "Merge
method 'merge' is not allowed for this base branch" and "This branch must not contain merge
commits". The refused attempt left no rule suite, so which of the two the pull-request bypass failed
to lift is not known. The project's rule is that `dev` reaches `main` through a merge commit, never
a squash (`.claude/CLAUDE.md`, Git).

## Decision

Decided on 24 November 2026, as ADR-135's trigger had it. This amends ADR-135 decision 4, and
reverses its rejection of `always`, whose premise that nothing needs a direct push holds, but which
missed that the promotion needs the merge-method and linear-history rules lifted. Everything else in
ADR-135 stands.

1. **The Admin bypass is `always` again,** as it was before ADR-135. The ruleset's other rules are
   unchanged: the seven required checks, one approval dismissed by a later push, squash only,
   linear history, no deletion or force push, and the Copilot review.
2. **ADR-135's "a mistyped push or force push to `dev` or `main` is refused, the Admin's too" no
   longer holds.** The bypass belongs to one account, NaimElijah, the repository's only Admin, and
   to everything that acts as it: its `gh` token, its git credentials and the agent sessions that
   run under it. For that account, only the project's Git rules stand in the way: never force-push
   a shared branch, and open every pull request into `dev` (`.claude/CLAUDE.md`, Git).

## Consequences

- **Easy:** `dev` reaches `main` through a merge commit, as before, with `gh pr merge --merge
  --admin`.
- **Hard:**
  - The Admin account can push or force-push straight to `dev` or `main`, or delete `dev`, which
    closes every open pull request into it, with no pull request, checks or review.
  - Every promotion and every Admin merge already shows as a bypass in the ruleset's rule suites,
    so a stray bypass does not stand out. A direct push shows as a bypassed rule suite whose commit
    belongs to no merged pull request; a force push or a deletion, as a failed `non_fast_forward` or
    `deletion` evaluation. Nothing checks for them.
- **Cost:** none.
- **Risk:** a command run by mistake as the Admin rewrites or deletes a shared branch. GitHub records
  it but does not stop it. A stolen Admin token could change the ruleset under either mode, so the
  bypass mode guards against mistakes, not theft.

## Alternatives considered

- **Keep `pull_request`.** Rejected: it refused the promotion.
- **Split the ruleset:** one shared ruleset for the checks, no deletion or force push and the Copilot
  review; one for `dev` with one approval, squash only and linear history; one for `main` with one
  approval, merge commits and no linear history; and the pull-request bypass on each. GitHub
  combines the rulesets that target a branch, and records the merge method per base branch. It
  costs two more rulesets and the pull-request rule kept equal in two of them. Not chosen now; it is
  the next step on the triggers below. It is untested here: a promotion under it would fail closed,
  and `always` would come back.
- **Allow merge commits for everyone in the one ruleset.** Rejected: every pull request into `dev`
  would then offer a merge commit, against the squash rule, and linear history would still refuse
  it.
- **Promote `dev` to `main` by squash.** Rejected: the project's Git rule keeps `dev`'s commits on
  `main` through a merge commit.

## Revisit when

- A direct push, a force push or a deletion lands on `dev` or `main` through the bypass: take the
  split.
- A workflow deploys on a push to `main`: take the split before it runs.
- GitHub lets a pull-request bypass lift both the merge-method rule and linear history: return to
  `pull_request`.

[ADR-135]: ADR-135-the-seven-ci-jobs-are-required-checks-and-a-pull-request-needs-one-approval.md
